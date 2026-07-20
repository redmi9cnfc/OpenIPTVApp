package com.kraftplay.openiptv.parser

import android.util.Xml
import com.kraftplay.openiptv.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream

object XmlTvParser {
    private val dateFormats = listOf(
        "yyyyMMddHHmmss Z",
        "yyyyMMddHHmmss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    fun parse(inputStream: InputStream, isGzipped: Boolean = false): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        val stream = if (isGzipped) {
            try { GZIPInputStream(inputStream) } catch (e: Exception) { inputStream }
        } else inputStream
        
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "programme") {
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
                                "title" -> title = parser.nextText()
                                "desc" -> desc = parser.nextText()
                                "category" -> category = parser.nextText()
                                "icon" -> icon = parser.getAttributeValue(null, "src") ?: ""
                                else -> skip(parser)
                            }
                        }
                        event = parser.next()
                    }

                    val startTime = parseDate(startStr)
                    val endTime = parseDate(stopStr)

                    if (startTime != null && endTime != null && title.isNotEmpty()) {
                        programs.add(EpgProgram(
                            channelId = channelId,
                            title = title,
                            startTime = startTime,
                            endTime = endTime,
                            description = desc,
                            category = category,
                            iconUrl = icon
                        ))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { stream.close() } catch (e: Exception) {}
        }
        return programs
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

    private fun parseDate(dateStr: String?): Long? {
        if (dateStr == null) return null
        val cleanDate = dateStr.trim()
        for (format in dateFormats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                return sdf.parse(cleanDate)?.time
            } catch (e: Exception) { }
        }
        return null
    }
}
