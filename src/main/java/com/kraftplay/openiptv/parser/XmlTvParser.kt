package com.kraftplay.openiptv.parser

import android.util.Xml
import com.kraftplay.openiptv.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream

object XmlTvParser {

    suspend fun parseStreaming(
        inputStream: InputStream,
        isGzipped: Boolean = false,
        cutoffTime: Long = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L,
        futureLimit: Long = System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L,
        onChannelNamesParsed: (suspend (Map<String, List<String>>) -> Unit)? = null,
        onBatchReady: suspend (List<EpgProgram>) -> Unit
    ) {
        val stream = if (isGzipped) {
            try { GZIPInputStream(inputStream) } catch (e: Exception) { inputStream }
        } else inputStream

        val batch = mutableListOf<EpgProgram>()
        val channelNames = mutableMapOf<String, List<String>>()
        val maxBatchSize = 1000

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            val channelId = parser.getAttributeValue(null, "id") ?: ""
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
                                channelNames[channelId.trim().lowercase(Locale.ROOT)] = names
                            }
                        }
                        "programme" -> {
                            if (channelNames.isNotEmpty() && onChannelNamesParsed != null) {
                                onChannelNamesParsed(HashMap(channelNames))
                            }
                            val channelId = parser.getAttributeValue(null, "channel") ?: ""
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
                                        channelId = channelId.trim(),
                                        title = title.trim(),
                                        startTime = startTime,
                                        endTime = endTime,
                                        description = if (desc.isBlank()) null else desc.trim(),
                                        category = if (category.isBlank()) null else category.trim(),
                                        iconUrl = if (icon.isBlank()) null else icon.trim()
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
                onChannelNamesParsed(channelNames)
            }
            if (batch.isNotEmpty()) {
                onBatchReady(ArrayList(batch))
                batch.clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { stream.close() } catch (e: Exception) {}
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

        if (clean.length >= 14) {
            try {
                val year = clean.substring(0, 4).toInt()
                val month = clean.substring(4, 6).toInt() - 1
                val day = clean.substring(6, 8).toInt()
                val hour = clean.substring(8, 10).toInt()
                val minute = clean.substring(10, 12).toInt()
                val second = clean.substring(12, 14).toInt()

                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    isLenient = false
                    clear()
                }
                cal.set(year, month, day, hour, minute, second)
                cal.set(Calendar.MILLISECOND, 0)

                var millis = cal.timeInMillis

                val rest = clean.substring(14).trim()
                if (rest.isNotEmpty()) {
                    if (rest.startsWith("+") || rest.startsWith("-")) {
                        val sign = if (rest.startsWith("+")) -1 else 1
                        val offsetStr = rest.substring(1).replace(":", "")
                        if (offsetStr.length >= 4) {
                            val offsetHours = offsetStr.substring(0, 2).toIntOrNull() ?: 0
                            val offsetMins = offsetStr.substring(2, 4).toIntOrNull() ?: 0
                            val totalOffsetMs = (offsetHours * 3600 + offsetMins * 60) * 1000L * sign
                            millis += totalOffsetMs
                        }
                    }
                }
                return millis
            } catch (e: Exception) { }
        }

        val formats = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmmss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ"
        )

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val d = sdf.parse(clean)
                if (d != null) return d.time
            } catch (e: Exception) { }
        }

        return null
    }
}
