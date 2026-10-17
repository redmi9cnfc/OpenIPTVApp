package com.kraftplay.openiptv.parser

import com.kraftplay.openiptv.model.IptvChannel
import java.io.InputStream

object M3uParser {
    data class ParseResult(
        val channels: List<IptvChannel>,
        val epgUrls: List<String>
    )

    fun parseWithEpg(inputStream: InputStream): ParseResult {
        val channels = mutableListOf<IptvChannel>()
        val epgUrls = mutableListOf<String>()
        val reader = inputStream.bufferedReader()
        var currentName = ""
        var currentLogo = ""
        var currentCategory = ""
        var currentEpgId = ""
        var currentTvgName = ""
        var currentCatchup: String? = null
        var currentCatchupSource: String? = null
        var currentCatchupDays: Int? = null

        val logoRegex = """tvg-logo=["']?([^"'\s>]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
        val categoryRegex = """group-title=["']?([^"'\r\n,]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
        val epgIdRegex = """tvg-id=["']?([^"'\s,]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
        val tvgNameRegex = """tvg-name=["']?([^"'\r\n,]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
        val catchupRegex = """catchup=["']?([^"'\s>]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
        val catchupSourceRegex = """catchup-source=["']([^"']*)["']""".toRegex(RegexOption.IGNORE_CASE)
        val catchupDaysRegex = """catchup-days=["']?(\d+)["']?""".toRegex(RegexOption.IGNORE_CASE)
        val urlTvgRegex = """(?:url-tvg|x-tvg-url)=["']?([^"'\r\n>]+)["']?""".toRegex(RegexOption.IGNORE_CASE)

        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmedLine = line.trim().replace("\uFEFF", "")
                val lowerLine = trimmedLine.lowercase()
                when {
                    lowerLine.startsWith("#extm3u") -> {
                        urlTvgRegex.find(trimmedLine)?.groupValues?.get(1)?.let { rawUrls ->
                            rawUrls.split(",", ";").forEach { u ->
                                val cleanUrl = u.trim()
                                if (cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true)) {
                                    epgUrls.add(cleanUrl)
                                }
                            }
                        }
                    }
                    lowerLine.startsWith("#extinf:") || lowerLine.startsWith("#extinf ") -> {
                        currentName = trimmedLine.substringAfterLast(",").trim()
                        currentLogo = ""
                        currentCategory = ""
                        currentEpgId = ""
                        currentTvgName = ""
                        currentCatchup = null
                        currentCatchupSource = null
                        currentCatchupDays = null
                        
                        logoRegex.find(trimmedLine)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank()) currentLogo = it
                        }
                        categoryRegex.find(trimmedLine)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank()) currentCategory = it
                        }
                        epgIdRegex.find(trimmedLine)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank()) currentEpgId = it
                        }
                        tvgNameRegex.find(trimmedLine)?.groupValues?.get(1)?.let {
                            if (it.isNotBlank()) currentTvgName = it
                        }
                        currentCatchup = catchupRegex.find(trimmedLine)?.groupValues?.get(1)
                        currentCatchupSource = catchupSourceRegex.find(trimmedLine)?.groupValues?.get(1)
                        currentCatchupDays = catchupDaysRegex.find(trimmedLine)?.groupValues?.get(1)?.toIntOrNull()
                    }
                    lowerLine.startsWith("#extgrp:") -> {
                        val grp = trimmedLine.substringAfter(":").trim()
                        if (grp.isNotBlank()) currentCategory = grp
                    }
                    trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#") -> {
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
        return ParseResult(channels, epgUrls.distinct())
    }

    fun parse(inputStream: InputStream): List<IptvChannel> {
        return parseWithEpg(inputStream).channels
    }
}
