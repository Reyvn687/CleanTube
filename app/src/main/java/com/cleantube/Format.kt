package com.cleantube

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun formatDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun oneDecimal(d: Double, suffix: String): String =
    "%.1f".format(Locale.US, d).replace('.', ',').removeSuffix(",0") + " " + suffix

fun compact(n: Long): String = when {
    n >= 1_000_000_000L -> oneDecimal(n / 1e9, "mlrd")
    n >= 1_000_000L -> oneDecimal(n / 1e6, "mln")
    n >= 1_000L -> oneDecimal(n / 1e3, "min")
    else -> n.toString()
}

fun formatAgo(iso: String): String {
    if (iso.length < 19) return ""
    return try {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        val t = f.parse(iso.substring(0, 19))?.time ?: return ""
        val s = (System.currentTimeMillis() - t) / 1000
        when {
            s < 60 -> "indicə"
            s < 3600 -> "${s / 60} dəqiqə əvvəl"
            s < 86400 -> "${s / 3600} saat əvvəl"
            s < 604800 -> "${s / 86400} gün əvvəl"
            s < 2592000 -> "${s / 604800} həftə əvvəl"
            s < 31536000 -> "${s / 2592000} ay əvvəl"
            else -> "${s / 31536000} il əvvəl"
        }
    } catch (e: Exception) {
        ""
    }
}

fun VideoItem.metaLine(): String {
    val parts = ArrayList<String>()
    if (channel.isNotBlank()) parts.add(channel)
    if (views >= 0) parts.add("${compact(views)} baxış")
    val ago = formatAgo(published)
    if (ago.isNotEmpty()) parts.add(ago)
    return parts.joinToString(" • ")
}

private val VIDEO_ID_REGEX = Regex(
    "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^\\s]*?[&?])?v=|shorts/|embed/|live/|v/))([A-Za-z0-9_-]{11})"
)

/** Mətndən (link və ya paylaşılan mesaj) YouTube video id-sini çıxarır. */
fun extractVideoId(text: String): String? = VIDEO_ID_REGEX.find(text)?.groupValues?.get(1)
