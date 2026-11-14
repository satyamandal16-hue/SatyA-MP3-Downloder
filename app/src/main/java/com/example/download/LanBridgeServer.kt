package com.example.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaCategory
import com.example.ui.components.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections

data class LanBridgeState(
    val isRunning: Boolean = false,
    val url: String = "",
    val port: Int = 8765,
    val requestsServed: Int = 0,
    val error: String? = null
)

/**
 * Lightweight embedded HTTP server allowing PC (Windows/Mac/Linux) browsers on the same Wi-Fi
 * network to push download links to SatyA Mobile AND download completed MP3/Video/Files to PC.
 */
class LanBridgeServer(
    private val context: Context,
    private val dao: DownloadDao,
    private val onEnqueueFromPc: suspend (url: String, category: MediaCategory) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _state = MutableStateFlow(LanBridgeState())
    val state: StateFlow<LanBridgeState> = _state.asStateFlow()

    fun start(port: Int = 8765) {
        if (_state.value.isRunning) return
        acceptJob = scope.launch {
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                val ip = getLocalIpv4Address() ?: "127.0.0.1"
                _state.value = LanBridgeState(
                    isRunning = true,
                    url = "http://$ip:$port",
                    port = port
                )
                while (isActive && !ss.isClosed) {
                    val client = ss.accept()
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                if (_state.value.isRunning || serverSocket != null) {
                    _state.value = _state.value.copy(
                        isRunning = false,
                        error = e.message ?: "Bridge stopped"
                    )
                }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        _state.value = LanBridgeState(isRunning = false, port = _state.value.port)
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { s ->
            val input = BufferedInputStream(s.getInputStream())
            val out = BufferedOutputStream(s.getOutputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            // Drain headers and capture Content-Length
            var contentLength = 0
            while (true) {
                val header = readLine(input) ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            if (method == "POST" && path.startsWith("/enqueue")) {
                val bodyBytes = ByteArray(contentLength.coerceIn(0, 16384))
                var readTotal = 0
                while (readTotal < bodyBytes.size) {
                    val r = input.read(bodyBytes, readTotal, bodyBytes.size - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                val formBody = String(bodyBytes, 0, readTotal, Charsets.UTF_8)
                val params = parseFormParams(formBody)
                val url = params["url"]?.trim().orEmpty()
                val mode = params["mode"] ?: "AUTO"
                if (url.isNotBlank()) {
                    val cat = when (mode) {
                        "MP3" -> MediaCategory.HD_AUDIO
                        "VIDEO" -> MediaCategory.HD_VIDEO
                        else -> LinkInspector.classifyMedia(url, "")
                    }
                    onEnqueueFromPc(url, cat)
                }
                val redirect = "HTTP/1.1 303 See Other\r\nLocation: /\r\nConnection: close\r\n\r\n"
                out.write(redirect.toByteArray(Charsets.UTF_8))
                out.flush()
                _state.value = _state.value.copy(requestsServed = _state.value.requestsServed + 1)
                return
            }

            if (method == "GET" && path.startsWith("/file/")) {
                val id = path.removePrefix("/file/").substringBefore("?").toLongOrNull()
                val item = id?.let { dao.getDownloadById(it) }
                val file = item?.localFilePath?.let { File(it) }
                if (item != null && file != null && file.exists()) {
                    val headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: ${item.mimeType}\r\n" +
                        "Content-Length: ${file.length()}\r\n" +
                        "Content-Disposition: attachment; filename=\"${item.fileName}\"\r\n" +
                        "Connection: close\r\n\r\n"
                    out.write(headers.toByteArray(Charsets.UTF_8))
                    FileInputStream(file).use { fis ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val r = fis.read(buf)
                            if (r <= 0) break
                            out.write(buf, 0, r)
                        }
                    }
                    out.flush()
                    _state.value = _state.value.copy(requestsServed = _state.value.requestsServed + 1)
                    return
                }
            }

            // Serve HTML console
            val downloads = dao.observeAllDownloads().first()
            val html = buildHtmlConsole(downloads)
            val bytes = html.toByteArray(Charsets.UTF_8)
            val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
            out.write(headers.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
            _state.value = _state.value.copy(requestsServed = _state.value.requestsServed + 1)
        }
    }

    private fun buildHtmlConsole(downloads: List<DownloadEntity>): String {
        val rows = downloads.joinToString("\n") { item ->
            val sizeLabel = Formatters.formatBytes(item.totalBytes)
            val downloadBtn = if (item.status == DownloadStatus.COMPLETED && item.localFilePath != null) {
                "<a class='btn' href='/file/${item.id}'>Save to PC</a>"
            } else {
                "<span class='tag'>${item.status.name}</span>"
            }
            """
            <div class='card'>
              <div>
                <div class='title'>${escapeHtml(item.fileName)}</div>
                <div class='meta'>${item.category.displayName} • ${item.qualityLabel} • $sizeLabel</div>
              </div>
              <div>$downloadBtn</div>
            </div>
            """.trimIndent()
        }.ifBlank { "<div class='empty'>No downloads yet. Paste a link above from your PC!</div>" }

        return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width,initial-scale=1"/>
          <title>SatyA PC ↔ Mobile Bridge</title>
          <style>
            body{margin:0;background:#0B101B;color:#F1F5F9;font-family:system-ui,-apple-system,sans-serif;padding:24px;}
            .wrap{max-width:760px;margin:0 auto;}
            h1{margin:0 0 6px;font-size:24px;color:#00E5FF;}
            .sub{color:#94A3B8;font-size:14px;margin-bottom:20px;}
            form{background:#121927;border:1px solid #2B3954;border-radius:14px;padding:16px;margin-bottom:20px;display:flex;gap:10px;flex-wrap:wrap;}
            input,select{background:#192235;color:#F1F5F9;border:1px solid #2B3954;border-radius:10px;padding:10px 12px;font-size:14px;}
            input{flex:1;min-width:220px;}
            button,.btn{background:#00E5FF;color:#0B101B;border:none;border-radius:10px;padding:10px 16px;font-weight:700;cursor:pointer;text-decoration:none;font-size:13px;}
            .card{background:#121927;border:1px solid #2B3954;border-radius:12px;padding:14px 16px;margin-bottom:10px;display:flex;justify-content:space-between;align-items:center;gap:12px;}
            .title{font-weight:600;font-size:15px;word-break:break-all;}
            .meta{font-size:12px;color:#94A3B8;margin-top:4px;}
            .tag{background:#192235;color:#F59E0B;padding:6px 10px;border-radius:8px;font-size:12px;font-weight:600;}
            .empty{color:#94A3B8;padding:24px;text-align:center;background:#121927;border-radius:12px;}
          </style>
        </head>
        <body>
          <div class='wrap'>
            <h1>SatyA PC ↔ Mobile Bridge</h1>
            <div class='sub'>Open Edition • Push HD MP3 / Video links to your phone or pull completed files to PC</div>
            <form method='POST' action='/enqueue'>
              <input name='url' placeholder='Paste MP3, MP4, or File URL here...' required/>
              <select name='mode'>
                <option value='MP3'>HD MP3 Audio</option>
                <option value='VIDEO'>HD Video</option>
                <option value='AUTO'>Auto Detect</option>
              </select>
              <button type='submit'>Send to Phone</button>
            </form>
            $rows
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun parseFormParams(body: String): Map<String, String> {
        return body.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                k to v
            }
        }.toMap()
    }

    private fun escapeHtml(raw: String): String =
        raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        var c = input.read()
        if (c == -1) return null
        while (c != -1) {
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
            c = input.read()
        }
        return sb.toString()
    }

    private fun getLocalIpv4Address(): String? {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            val isWifiOrEth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            var fallback: String? = null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (isWifiOrEth && (intf.name.startsWith("wlan") || intf.name.startsWith("eth"))) {
                            return host
                        }
                        if (fallback == null) fallback = host
                    }
                }
            }
            fallback
        }.getOrNull()
    }
}
