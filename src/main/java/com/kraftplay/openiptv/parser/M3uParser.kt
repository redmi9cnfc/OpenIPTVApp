package com.kraftplay.openiptv.parser

import com.kraftplay.openiptv.model.IptvChannel
import java.io.InputStream

object M3uParser {
    data class ParseResult(
        val channels: List<IptvChannel>,
        val epgUrls: List<String>
    )

    fun parseWithEpg(inputStream: InputStream): ParseResult {
        val channels = ArrayList<IptvChannel>()
        val epgUrls = LinkedHashSet<String>()
        val reader = inputStream.bufferedReader()
        
        var currentName = ""
        var currentLogo = ""
        var currentCategory = ""
        var currentEpgId = ""
        var currentTvgName = ""
        var currentCatchup: String? = null
        var currentCatchupSource: String? = null
        var currentCatchupDays: Int? = null

        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmedLine = line.trim().replace("\uFEFF", "")
                if (trimmedLine.isEmpty()) return@forEach

                val lowerLine = trimmedLine.lowercase()
                when {
                    lowerLine.startsWith("#extm3u") -> {
                        extractAttribute(trimmedLine, "url-tvg=")?.let { addEpgUrls(it, epgUrls) }
                        extractAttribute(trimmedLine, "x-tvg-url=")?.let { addEpgUrls(it, epgUrls) }
                    }
                    lowerLine.startsWith("#extinf:") || lowerLine.startsWith("#extinf ") -> {
                        currentName = trimmedLine.substringAfterLast(",").trim()
                        currentLogo = extractAttribute(trimmedLine, "tvg-logo=").orEmpty()
                        currentCategory = extractAttribute(trimmedLine, "group-title=").orEmpty()
                        currentEpgId = extractAttribute(trimmedLine, "tvg-id=").orEmpty()
                        currentTvgName = extractAttribute(trimmedLine, "tvg-name=").orEmpty()
                        currentCatchup = extractAttribute(trimmedLine, "catchup=")
                        currentCatchupSource = extractAttribute(trimmedLine, "catchup-source=")
                        currentCatchupDays = extractAttribute(trimmedLine, "catchup-days=")?.toIntOrNull()
                    }
                    lowerLine.startsWith("#extgrp:") -> {
                        val grp = trimmedLine.substringAfter(":").trim()
                        if (grp.isNotBlank()) currentCategory = grp
                    }
                    !trimmedLine.startsWith("#") -> {
                        val streamUrl = trimmedLine
                        if (streamUrl.isNotBlank()) {
                            val name = if (currentName.isNotBlank()) currentName else "Channel ${channels.size + 1}"
                            channels.add(
                                IptvChannel(
                                    name = name,
                                    url = streamUrl,
                                    logoUrl = if (currentLogo.isBlank()) null else currentLogo,
                                    category = if (currentCategory.isBlank()) "Other" else currentCategory,
                                    epgId = if (currentEpgId.isBlank()) null else currentEpgId,
                                    tvgName = if (currentTvgName.isBlank()) null else currentTvgName,
                                    catchup = currentCatchup,
                                    catchupSource = currentCatchupSource,
                                    catchupDays = currentCatchupDays
                                )
                            )
                        }
                        currentName = ""
                        currentLogo = ""
                        currentCategory = ""
                        currentEpgId = ""
                        currentTvgName = ""
                        currentCatchup = null
                        currentCatchupSource = null
                        currentCatchupDays = null
                    }
                }
            }
        }
        return ParseResult(channels, epgUrls.toList())
    }

    private fun addEpgUrls(rawUrls: String, destination: MutableSet<String>) {
        rawUrls.split(",", ";").forEach { u ->
            val cleanUrl = u.trim()
            if (cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true)) {
                destination.add(cleanUrl)
            }
        }
    }

    private fun extractAttribute(line: String, key: String): String? {
        val idx = line.indexOf(key, ignoreCase = true)
        if (idx == -1) return null
        val startValue = idx + key.length
        if (startValue >= line.length) return null
        val quote = line[startValue]
        return if (quote == '"' || quote == '\'') {
            val endValue = line.indexOf(quote, startValue + 1)
            if (endValue != -1) line.substring(startValue + 1, endValue) else null
        } else {
            val endValue = line.indexOfAny(charArrayOf(' ', ',', '>'), startValue)
            if (endValue != -1) line.substring(startValue, endValue) else line.substring(startValue)
        }
    }

    fun parse(inputStream: InputStream): List<IptvChannel> {
        return parseWithEpg(inputStream).channels
    }
}
