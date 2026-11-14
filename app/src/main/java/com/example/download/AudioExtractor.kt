package com.example.download

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.data.model.MediaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Extracts the native audio track from a downloaded MP4/video file into a high-definition M4A/AAC
 * audio file using Android's built-in MediaExtractor + MediaMuxer (no external native binaries).
 */
object AudioExtractor {

    suspend fun extractAudioFromVideo(
        context: Context,
        videoFile: File,
        desiredAudioBaseName: String
    ): Result<Pair<SavedFileResult, Long>> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(videoFile.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                extractor.release()
                throw IllegalStateException("No audio track found inside this video file.")
            }

            extractor.selectTrack(audioTrackIndex)
            val tempDir = File(context.cacheDir, "audio_extract").apply { mkdirs() }
            val safeBase = LinkInspector.sanitizeFileName(desiredAudioBaseName.substringBeforeLast('.'))
            val outFile = File(tempDir, "${safeBase}_HD.m4a")
            if (outFile.exists()) outFile.delete()

            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxInputSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(128 * 1024)
            } else {
                256 * 1024
            }
            val buffer = ByteBuffer.allocate(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            val fileLength = outFile.length()
            val saved = MediaStoreSaver.saveToPublicStorage(
                context = context,
                sourceFile = outFile,
                fileName = outFile.name,
                mimeType = "audio/mp4",
                category = MediaCategory.HD_AUDIO
            )
            saved to fileLength
        }
    }
}
