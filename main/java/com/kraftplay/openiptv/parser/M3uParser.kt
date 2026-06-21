package com.kraftplay.openiptv.parser

import com.kraftplay.openiptv.model.IptvChannel
import java.io.InputStream

object M3uParser {
    fun parse(inputStream: InputStream): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val reader = inputStream.bufferedReader()
        var currentName = ""
        var currentLogo = ""
        var currentCategory = ""
        var currentEpgId = ""

        val logoRegex = """tvg-logo="([^"]*)"""".toRegex()
        val categoryRegex = """group-title="([^"]*)"""".toRegex()
        val epgIdRegex = """tvg-id="([^"]*)"""".toRegex()

        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmedLine = line.trim()
                when {
                    trimmedLine.startsWith("#EXTINF:") -> {
                        currentName = trimmedLine.substringAfterLast(",").trim()
                        if (currentName.isEmpty()) currentName = "Unknown Channel"
                        
                        currentLogo = logoRegex.find(trimmedLine)?.groupValues?.get(1) ?: ""
                        currentCategory = categoryRegex.find(trimmedLine)?.groupValues?.get(1) ?: ""
                        currentEpgId = epgIdRegex.find(trimmedLine)?.groupValues?.get(1) ?: ""
                    }
                    trimmedLine.startsWith("#EXTGRP:") -> {
                        currentCategory = trimmedLine.substringAfter(":").trim()
                    }
                    trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#") -> {
                        channels.add(IptvChannel(currentName, trimmedLine, currentLogo, currentCategory, currentEpgId))
                        currentName = ""
                        currentLogo = ""
                        currentEpgId = ""
                    }
                }
            }
        }
        return channels
    }
}
