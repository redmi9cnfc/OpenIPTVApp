package com.kraftplay.openiptv.util

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Catch-up URL builder for common M3U templates, Flussonic DVR and Xtream Codes. */
object CatchupUrlBuilder {
    fun build(
        liveUrl: String,
        sourceTemplate: String?,
        mode: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): String? {
        if (liveUrl.isBlank() || endTimeMs <= startTimeMs || startTimeMs > System.currentTimeMillis()) return null
        val startSec = startTimeMs / 1000L
        val endSec = endTimeMs / 1000L
        val duration = ((endTimeMs - startTimeMs) / 1000L).coerceAtLeast(1L)
        val nowSec = System.currentTimeMillis() / 1000L
        val offset = (nowSec - startSec).coerceAtLeast(0L)
        val utcFormat = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val startDate = utcFormat.format(Date(startTimeMs))
        val endDate = utcFormat.format(Date(endTimeMs))
        val selectedMode = mode.trim().lowercase(Locale.ROOT)
        val template = sourceTemplate?.takeIf { it.isNotBlank() }

        if (template != null && selectedMode !in setOf("flussonic", "fs", "flussonic-hls", "flussonic-ts", "xtream", "xc", "xstream-1")) {
            val expanded = template
                .replace("\${start}", startSec.toString(), true)
                .replace("\${end}", endSec.toString(), true)
                .replace("\${duration}", duration.toString(), true)
                .replace("\${offset}", offset.toString(), true)
                .replace("\${utc}", startSec.toString(), true)
                .replace("\${lutc}", endSec.toString(), true)
                .replace("\${timestamp}", startSec.toString(), true)
                .replace("\$start\$", startSec.toString(), true)
                .replace("\$end\$", endSec.toString(), true)
                .replace("\$duration\$", duration.toString(), true)
                .replace("\$utc\$", startSec.toString(), true)
                .replace("\$lutc\$", endSec.toString(), true)
                .replace("\$timestamp\$", startSec.toString(), true)
                .replace("\$offset\$", offset.toString(), true)
                .replace("{start}", startSec.toString())
                .replace("{end}", endSec.toString())
                .replace("{duration}", duration.toString())
                .replace("{utc}", startSec.toString())
                .replace("{lutc}", endSec.toString())
                .replace("{start_date}", startDate)
                .replace("{end_date}", endDate)
            return when {
                expanded.startsWith("http://", true) || expanded.startsWith("https://", true) -> expanded
                expanded.startsWith("?") || expanded.startsWith("&") -> liveUrl + (if (liveUrl.contains('?')) "&" else "?") + expanded.trimStart('?', '&')
                expanded.startsWith("/") -> runCatching { URI(liveUrl).let { rebuild(it, expanded) } }.getOrNull() ?: expanded
                else -> expanded
            }
        }

        // Explicit modes, plus Auto fallback: providers often omit catchup attributes even
        // though their Flussonic DVR endpoint is enabled.
        if (selectedMode == "xtream" || selectedMode == "xc" || selectedMode == "xstream-1") {
            return buildXtream(liveUrl, startSec, duration)
        }
        if (selectedMode == "flussonic" || selectedMode == "auto" || selectedMode == "fs" || selectedMode == "flussonic-hls" || selectedMode == "flussonic-ts") {
            return buildFlussonic(liveUrl, startSec, duration)
        }
        if (selectedMode == "shift" || selectedMode == "append" || selectedMode == "default") {
            val separator = if (liveUrl.contains('?')) "&" else "?"
            return liveUrl + separator + "utc=$startSec&lutc=$endSec"
        }
        return buildFlussonic(liveUrl, startSec, duration)
    }

    /** Build a Flussonic relative rewind window (no EPG required). */
    fun buildRelative(liveUrl: String, mode: String, secondsBack: Long): String? {
        if (liveUrl.isBlank() || secondsBack <= 0) return null
        val selectedMode = mode.trim().lowercase(Locale.ROOT)
        if (selectedMode == "xtream" || selectedMode == "xc" || selectedMode == "xstream-1") {
            // Xtream needs a stream id and credentials, which are parsed from /live/user/pass/id.ext.
            return buildXtream(liveUrl, System.currentTimeMillis() / 1000L - secondsBack, secondsBack)
        }
        if (selectedMode == "append" || selectedMode == "shift" || selectedMode == "default") {
            val now = System.currentTimeMillis() / 1000L
            val separator = if (liveUrl.contains('?')) "&" else "?"
            return liveUrl + separator + "utc=${now - secondsBack}&lutc=$now"
        }
        return buildFlussonicRelative(liveUrl, secondsBack)
    }

    private fun buildFlussonic(liveUrl: String, startSec: Long, duration: Long): String? {
        val uri = runCatching { URI(liveUrl) }.getOrNull() ?: return null
        val path = uri.rawPath.orEmpty()
        if (path.isBlank()) return null
        val ext = when {
            path.endsWith(".m3u8", true) -> ".m3u8"
            path.endsWith(".mpd", true) -> ".mpd"
            path.endsWith(".ts", true) -> ".ts"
            path.endsWith("/mpegts", true) -> ".ts"
            else -> ".m3u8"
        }
        val newPath = if (path.endsWith("/mpegts", true)) {
            path.substringBeforeLast('/') + "/timeshift_abs-$startSec.ts"
        } else {
            val base = path.substringBeforeLast('/').takeIf { it.isNotBlank() && it != path } ?: path.substringBeforeLast('.').trimEnd('/')
            "$base/archive-$startSec-$duration$ext"
        }
        return rebuild(uri, newPath)
    }

    private fun buildFlussonicRelative(liveUrl: String, secondsBack: Long): String? {
        val uri = runCatching { URI(liveUrl) }.getOrNull() ?: return null
        val path = uri.rawPath.orEmpty()
        if (path.isBlank()) return null
        val ext = when {
            path.endsWith(".m3u8", true) -> ".m3u8"
            path.endsWith(".mpd", true) -> ".mpd"
            path.endsWith(".ts", true) || path.endsWith("/mpegts", true) -> ".ts"
            else -> ".m3u8"
        }
        val base = path.substringBeforeLast('/').takeIf { it.isNotBlank() && it != path } ?: path.substringBeforeLast('.').trimEnd('/')
        val newPath = if (path.endsWith("/mpegts", true)) "$base/timeshift_abs-${System.currentTimeMillis() / 1000L - secondsBack}.ts"
            else "$base/timeshift_rel-$secondsBack$ext"
        return rebuild(uri, newPath)
    }

    private fun buildXtream(liveUrl: String, startSec: Long, duration: Long): String? {
        val uri = runCatching { URI(liveUrl) }.getOrNull() ?: return null
        val parts = uri.rawPath.orEmpty().split('/').filter { it.isNotBlank() }
        val liveIndex = parts.indexOfLast { it.equals("live", true) }
        if (liveIndex < 0 || parts.size < liveIndex + 4) return null
        val user = parts[liveIndex + 1]
        val pass = parts[liveIndex + 2]
        val streamFile = parts[liveIndex + 3]
        val streamId = streamFile.substringBeforeLast('.', streamFile)
        val ext = streamFile.substringAfterLast('.', "ts")
        val newPath = "/timeshift/${enc(user)}/${enc(pass)}/$duration/$startSec/${enc(streamId)}.$ext"
        return rebuild(uri, newPath)
    }

    private fun rebuild(uri: URI, newPath: String): String? = runCatching {
        buildString {
            append(uri.scheme).append("://").append(uri.rawAuthority).append(newPath)
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }.getOrNull()

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
