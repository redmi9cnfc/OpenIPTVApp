package com.kraftplay.openiptv.parser

import android.util.Xml
import com.kraftplay.openiptv.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

object XmlTvParser {
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    fun parse(inputStream: InputStream): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "programme") {
                val channelId = parser.getAttributeValue(null, "channel")
                val startStr = parser.getAttributeValue(null, "start")
                val stopStr = parser.getAttributeValue(null, "stop")
                
                var title = ""
                var desc = ""

                while (!(parser.next() == XmlPullParser.END_TAG && parser.name == "programme")) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "title" -> title = parser.nextText()
                            "desc" -> desc = parser.nextText()
                        }
                    }
                }

                try {
                    val startDate = dateFormat.parse(startStr)
                    val stopDate = dateFormat.parse(stopStr)
                    if (startDate != null && stopDate != null) {
                        programs.add(EpgProgram(channelId, title, startDate, stopDate, desc))
                    }
                } catch (e: Exception) {
                    // Skip malformed dates
                }
            }
            eventType = parser.next()
        }
        return programs
    }
}
