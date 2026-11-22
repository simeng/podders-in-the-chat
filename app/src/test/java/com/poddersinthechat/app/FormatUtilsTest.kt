package com.poddersinthechat.app

import com.poddersinthechat.app.ui.components.describeHour
import com.poddersinthechat.app.ui.components.formatBytes
import com.poddersinthechat.app.ui.components.retentionLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun `formatBytes formats common sizes`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("10.0 MB", formatBytes(10L * 1024 * 1024))
        assertEquals("2.5 GB", formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `describeHour uses 24h labels`() {
        assertEquals("00:00", describeHour(0))
        assertEquals("06:00", describeHour(6))
        assertEquals("11:00", describeHour(11))
        assertEquals("12:00", describeHour(12))
        assertEquals("18:00", describeHour(18))
        assertEquals("21:00", describeHour(21))
    }

    @Test
    fun `retentionLabel handles never and month`() {
        assertEquals("Never", retentionLabel(0))
        assertEquals("3 days", retentionLabel(3))
        assertEquals("7 days", retentionLabel(7))
        assertEquals("1 month", retentionLabel(30))
    }
}
