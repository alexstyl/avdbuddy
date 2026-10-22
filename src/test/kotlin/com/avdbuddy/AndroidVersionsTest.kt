package com.avdbuddy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AndroidVersionsTest {
    @Test fun `release names are mapped from API levels rather than guessed from their numbers`() {
        assertEquals("Android 2.3.3 Gingerbread", androidFamilyTitle("10"))
        assertEquals("Android 3.0 Honeycomb", androidFamilyTitle("11"))
        assertEquals("Android 4.1 Jelly Bean", androidFamilyTitle("16"))
        assertEquals("Android 5.0 Lollipop", androidFamilyTitle("21"))
        assertEquals("Android 10", androidFamilyTitle("29"))
        assertEquals("Android 12L", androidFamilyTitle("32"))
        assertEquals("Android 17", androidFamilyTitle("37"))
    }
    @Test fun `extension releases keep the Android version while the subtitle distinguishes the API`() {
        assertEquals("Android 16", androidFamilyTitle("36.1"))
        assertEquals("API 36.1", androidApiSubtitle("36.1"))
        assertEquals("Android 17", androidFamilyTitle("37.1"))
    }
    @Test fun `canary and unknown SDK identifiers never invent Android version numbers`() {
        assertEquals("Android Canary", androidFamilyTitle("canary-20261007"))
        assertEquals("Preview · Oct 7, 2026", androidApiSubtitle("canary-20261007"))
        assertEquals("Latest preview", androidApiSubtitle("CANARY"))
        assertEquals("Android Preview", androidFamilyTitle("99"))
        assertEquals("API 99", androidApiSubtitle("99"))
    }
    @Test fun `release sorting keeps previews below their final release and above older APIs`() {
        val versions=listOf("37.1", "37.2-beta1", "37.0", "37.2-beta2", "37.2", "37.2-beta3")
        assertEquals(listOf("37.2", "37.2-beta3", "37.2-beta2", "37.2-beta1", "37.1", "37.0"),
            versions.sortedWith(androidReleaseOrder.reversed()))
    }
    @Test fun `release components and preview iterations sort numerically`() {
        val versions=listOf("37.2-beta2", "37.10", "37.2-alpha3", "37.2-beta10", "37.2-rc1", "37.2")
        assertEquals(listOf("37.10", "37.2", "37.2-rc1", "37.2-beta10", "37.2-beta2", "37.2-alpha3"),
            versions.sortedWith(androidReleaseOrder.reversed()))
    }
}
