package com.kraftplay.openiptv.parser

import android.os.Build
import android.util.Xml
import com.kraftplay.openiptv.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.*
import java.util.zip.GZIPInputStream

object XmlTvParser {

    private val utcCalendarThreadLocal = ThreadLocal.withInitial {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { isLenient = false }
    }

    suspend fun parseStreaming(
        inputStream: InputStream,
        isGzipped: Boolean = false,
        cutoffTime: Long = 0L,
        futureLimit: Long = Long.MAX_VALUE,
        sourceUrl: String? = null,
        onChannelNamesParsed: (suspend (Map<String, List<String>>) -> Unit)? = null,
        onBatchReady: suspend (List<EpgProgram>) -> Unit
    ) {
        val bis = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream, 64 * 1024)
        val stream: InputStream = if (isGzipped) {
            try {
                bis.mark(64 * 1024)
                GZIPInputStream(bis)
            } catch (e: Exception) {
                runCatching { bis.reset() }
                bis
            }
        } else {
            bis
        }

        val batch = mutableListOf<EpgProgram>()
        val channelNames = mutableMapOf<String, List<String>>()
        val maxBatchSize = 5000

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            val channelId = parser.getAttributeValue(null, "id")?.trim().orEmpty()
                            val names = mutableListOf<String>()
                            var event = parser.next()
                            while (!(event == XmlPullParser.END_TAG && parser.name == "channel")) {
                                if (event == XmlPullParser.START_TAG) {
                                    if (parser.name == "display-name") {
                                        val name = readText(parser)
                                        if (name.isNotBlank()) names.add(name)
                                    } else {
                                        skip(parser)
                                    }
                                }
                                event = parser.next()
                            }
                            if (channelId.isNotBlank() && names.isNotEmpty()) {
                                channelNames[channelId.lowercase(Locale.ROOT)] = names
                            }
                        }
                        "programme" -> {
                            val channelId = parser.getAttributeValue(null, "channel")?.trim().orEmpty()
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")

                            var title = ""
                            var desc = ""
                            var category = ""
                            var icon = ""

                            var event = parser.next()
                            while (!(event == XmlPullParser.END_TAG && parser.name == "programme")) {
                                if (event == XmlPullParser.START_TAG) {
                                    when (parser.name) {
                                        "title" -> title = readText(parser)
                                        "desc" -> desc = readText(parser)
                                        "category" -> category = readText(parser)
                                        "icon" -> {
                                            icon = parser.getAttributeValue(null, "src") ?: ""
                                            skip(parser)
                                        }
                                        else -> skip(parser)
                                    }
                                }
                                event = parser.next()
                            }

                            val startTime = parseXmlTvDate(startStr)
                            val endTime = parseXmlTvDate(stopStr)

                            if (startTime != null && endTime != null && endTime > cutoffTime && startTime < futureLimit && title.isNotEmpty() && channelId.isNotEmpty()) {
                                batch.add(
                                    EpgProgram(
                                        channelId = channelId,
                                        title = title.trim(),
                                        startTime = startTime,
                                        endTime = endTime,
                                        description = desc.ifBlank { null }?.trim(),
                                        category = category.ifBlank { null }?.trim(),
                                        iconUrl = icon.ifBlank { null }?.trim(),
                                        sourceUrl = sourceUrl
                                    )
                                )
                                if (batch.size >= maxBatchSize) {
                                    onBatchReady(ArrayList(batch))
                                    batch.clear()
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            if (channelNames.isNotEmpty() && onChannelNamesParsed != null) {
                onChannelNamesParsed(HashMap(channelNames))
            }
            if (batch.isNotEmpty()) {
                onBatchReady(ArrayList(batch))
                batch.clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { stream.close() }
        }
    }

    suspend fun parse(inputStream: InputStream, isGzipped: Boolean = false): List<EpgProgram> {
        val result = mutableListOf<EpgProgram>()
        parseStreaming(inputStream, isGzipped) { batch ->
            result.addAll(batch)
        }
        return result
    }

    private fun readText(parser: XmlPullParser): String {
        val text = StringBuilder()
        val startDepth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == startDepth)) {
            if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT || event == XmlPullParser.ENTITY_REF) {
                text.append(parser.text.orEmpty())
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
        return text.toString().trim()
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    fun parseXmlTvDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val clean = dateStr.trim()

        // 1. Standard XMLTV 14-digit or 12-digit format YYYYMMDDHHMMSS or YYYYMMDDHHMM
        val digitsOnly = clean.takeWhile { it.isDigit() }
        if (digitsOnly.length >= 12) {
            try {
                val year = digitsOnly.substring(0, 4).toInt()
                val month = digitsOnly.substring(4, 6).toInt() - 1
                val day = digitsOnly.substring(6, 8).toInt()
                val hour = digitsOnly.substring(8, 10).toInt()
                val minute = digitsOnly.substring(10, 12).toInt()
                val second = if (digitsOnly.length >= 14) digitsOnly.substring(12, 14).toInt() else 0

                val rest = clean.substring(digitsOnly.length).trim()
                val hasExplicitOffset = rest.isNotEmpty() && (rest.startsWith("+") || rest.startsWith("-") || rest.equals("Z", ignoreCase = true))

                val cal = if (hasExplicitOffset) {
                    utcCalendarThreadLocal.get()!!
                } else {
                    Calendar.getInstance()
                }

                cal.clear()
                cal.set(year, month, day, hour, minute, second)
                var millis = cal.timeInMillis

                if (rest.isNotEmpty() && (rest.startsWith("+") || rest.startsWith("-"))) {
                    val sign = if (rest.startsWith("+")) -1 else 1
                    val offsetStr = rest.substring(1).replace(":", "")
                    if (offsetStr.length >= 4) {
                        val offsetHours = offsetStr.substring(0, 2).toIntOrNull() ?: 0
                        val offsetMins = offsetStr.substring(2, 4).toIntOrNull() ?: 0
                        millis += (offsetHours * 3600 + offsetMins * 60) * 1000L * sign
                    }
                }
                return millis
            } catch (e: Exception) { }
        }

        // 2. ISO-8601 or other standard formatted date
        try {
            if (clean.contains("-") || clean.contains("T") || clean.contains(":")) {
                val isoClean = clean.replace(" ", "T")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val javaInstant = java.time.OffsetDateTime.parse(isoClean)
                    return javaInstant.toInstant().toEpochMilli()
                }
            }
        } catch (e: Exception) { }

        // 3. Raw epoch milliseconds or seconds
        val asLong = clean.toLongOrNull()
        if (asLong != null) {
            return if (asLong < 10000000000L) asLong * 1000L else asLong
        }

        return null
    }
}
