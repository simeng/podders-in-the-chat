package com.poddersinthechat.app.ui.components

/**
 * Pure formatting helpers shared by the settings / download UI.
 * Kept free of Compose/Android references so they are unit-testable.
 */

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else "%.1f ${units[unit]}".format(value)
}

/** 24-hour clock label, e.g. 13 -> "13:00". */
internal fun describeHour(hour: Int): String = "%02d:00".format(hour)

/** 0 = never auto-delete (keep until removed manually). */
internal fun retentionLabel(days: Int): String = when (days) {
    0 -> "Never"
    30 -> "1 month"
    else -> "${days} days"
}
