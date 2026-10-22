package com.avdbuddy

import kotlin.test.*
import org.junit.jupiter.api.Test

class DeviceNamesTest {
    @Test fun `name guidance rejects ambiguous duplicate names on Mac`() {
        assertNotNull(deviceNameError("pixel_10", listOf("Pixel_10")))
        assertNull(deviceNameError("Pixel_11", listOf("Pixel_10")))
    }
    @Test fun `name guidance prevents invalid paths and explains blank input`() {
        listOf("", "  ", "Pixel 10", "../Pixel", "Pixel/10").forEach { assertNotNull(deviceNameError(it, emptyList()),it) }
        assertNull(deviceNameError(" Pixel_10-Pro.2 ",emptyList()))
    }
}
