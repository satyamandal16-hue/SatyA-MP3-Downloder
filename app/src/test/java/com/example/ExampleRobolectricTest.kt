package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MediaCategory
import com.example.download.LinkInspector
import com.example.ui.components.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches SatyA`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SatyA", appName)
    }

    @Test
    fun `link inspector classifies mp3 and mp4 accurately`() {
        assertEquals(MediaCategory.HD_AUDIO, LinkInspector.classifyMedia("track_320.mp3", "audio/mpeg"))
        assertEquals(MediaCategory.HD_VIDEO, LinkInspector.classifyMedia("movie_4k.mp4", "video/mp4"))
        assertEquals(MediaCategory.ARCHIVE, LinkInspector.classifyMedia("bundle.zip", "application/zip"))
        assertTrue(LinkInspector.isInterestingMediaOrFileUrl("https://cdn.example.com/song.mp3?token=abc"))
    }

    @Test
    fun `link inspector extracts content disposition filename`() {
        val name = LinkInspector.extractFileName(
            url = "https://cdn.example.com/download?id=99",
            contentDisposition = "attachment; filename=\"SatyA_Mix_320kbps.mp3\"",
            mimeType = "audio/mpeg"
        )
        assertEquals("SatyA_Mix_320kbps.mp3", name)
    }

    @Test
    fun `formatters render readable sizes and speeds`() {
        assertEquals("1.50 MB", Formatters.formatBytes(1572864L))
        assertEquals("2.00 MB/s", Formatters.formatSpeed(2L * 1024L * 1024L))
        assertEquals("10s left", Formatters.formatEta(2000L, 1000L, 100L))
    }
}
