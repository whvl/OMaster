package com.silas.omaster.util

/**
 * 格式化带符号的数字
 * 例如: 5 -> "+5", -3 -> "-3", 0 -> "0"
 */
fun Int.formatSigned(): String = if (this > 0) "+$this" else "$this"

/**
 * 格式化百分比
 * 例如: 0.75f -> "75%"
 */
fun Float.formatPercent(): String = "${(this * 100).toInt()}%"

/**
 * 格式化滤镜强度
 * 例如: ("复古", 80) -> "复古 80%"
 * 注意: 即使 intensity 为 100，也保留强度信息，确保 parseFilterWithIntensity 能正确解析
 */
fun formatFilterWithIntensity(filter: String, intensity: Int): String {
    return if (filter == "标准") {
        filter
    } else {
        "$filter ${intensity}%"
    }
}
