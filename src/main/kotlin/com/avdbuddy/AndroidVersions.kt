package com.avdbuddy

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Android platform version names and their distinct SDK API levels:
// https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels
private val androidReleases = mapOf(
    1 to "Android 1.0",
    2 to "Android 1.1",
    3 to "Android 1.5 Cupcake",
    4 to "Android 1.6 Donut",
    5 to "Android 2.0 Eclair",
    6 to "Android 2.0.1 Eclair",
    7 to "Android 2.1 Eclair",
    8 to "Android 2.2 Froyo",
    9 to "Android 2.3 Gingerbread",
    10 to "Android 2.3.3 Gingerbread",
    11 to "Android 3.0 Honeycomb",
    12 to "Android 3.1 Honeycomb",
    13 to "Android 3.2 Honeycomb",
    14 to "Android 4.0 Ice Cream Sandwich",
    15 to "Android 4.0.3 Ice Cream Sandwich",
    16 to "Android 4.1 Jelly Bean",
    17 to "Android 4.2 Jelly Bean",
    18 to "Android 4.3 Jelly Bean",
    19 to "Android 4.4 KitKat",
    20 to "Android 4.4W KitKat",
    21 to "Android 5.0 Lollipop",
    22 to "Android 5.1 Lollipop",
    23 to "Android 6.0 Marshmallow",
    24 to "Android 7.0 Nougat",
    25 to "Android 7.1 Nougat",
    26 to "Android 8.0 Oreo",
    27 to "Android 8.1 Oreo",
    28 to "Android 9 Pie",
    29 to "Android 10",
    30 to "Android 11",
    31 to "Android 12",
    32 to "Android 12L",
    33 to "Android 13",
    34 to "Android 14",
    35 to "Android 15",
    36 to "Android 16",
    37 to "Android 17",
)

fun androidFamilyTitle(identifier: String): String {
    val api = identifier.takeWhile(Char::isDigit).toIntOrNull()
    if (api != null) return androidReleases[api] ?: "Android Preview"
    return when {
        identifier.startsWith("canary", ignoreCase = true) -> "Android Canary"
        identifier == "—" -> "Android"
        else -> "Android Preview"
    }
}

fun androidApiSubtitle(identifier: String): String {
    if (identifier.firstOrNull()?.isDigit() == true) return "API $identifier"
    val date = Regex("canary-(\\d{8})", RegexOption.IGNORE_CASE).matchEntire(identifier)?.groupValues?.get(1)
    if (date != null) {
        val formatted = runCatching {
            LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
                .format(DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH))
        }.getOrNull()
        if (formatted != null) return "Preview · $formatted"
    }
    return if (identifier.equals("CANARY", ignoreCase = true)) "Latest preview" else "Preview · $identifier"
}

/** Ascending version order; callers reverse it for newest-first release lists. */
val androidReleaseOrder: Comparator<String> = Comparator { first, second ->
    val pattern = Regex("^(\\d+(?:\\.\\d+)*)(?:-([a-zA-Z]+)(\\d*))?$")
    val a = pattern.matchEntire(first)
    val b = pattern.matchEntire(second)
    if (a == null || b == null) return@Comparator first.compareTo(second)
    val av = a.groupValues[1].split('.').map(String::toInt)
    val bv = b.groupValues[1].split('.').map(String::toInt)
    for (index in 0 until maxOf(av.size, bv.size)) {
        val result = (av.getOrNull(index) ?: 0).compareTo(bv.getOrNull(index) ?: 0)
        if (result != 0) return@Comparator result
    }
    fun phase(value: String) = when(value.lowercase()) {
        "" -> 4
        "rc" -> 3
        "beta" -> 2
        "alpha" -> 1
        else -> 0
    }
    val phaseOrder = phase(a.groupValues[2]).compareTo(phase(b.groupValues[2]))
    if (phaseOrder != 0) return@Comparator phaseOrder
    val iteration = (a.groupValues[3].toIntOrNull() ?: 0).compareTo(b.groupValues[3].toIntOrNull() ?: 0)
    if (iteration != 0) iteration else first.compareTo(second)
}
