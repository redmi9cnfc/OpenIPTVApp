package com.kraftplay.openiptv.util

import com.kraftplay.openiptv.model.EpgProgram
import com.kraftplay.openiptv.model.IptvChannel
import java.util.Locale

object EpgMatcher {

    private val TRANSLIT_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    fun cleanName(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("""\[[^]]*]"""), " ")
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""\b(ru|pl|de|ua|by|uz|kz)\s*:"""), " ")
            .replace(Regex("""\b(fhd|uhd|4k|8k|hd|sd|hevc|h[ .]?265|50fps|60fps|1080p|720p|576p|vip|premium)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), "")
            .trim()
    }

    fun transliterate(text: String): String {
        val sb = StringBuilder()
        for (ch in text.lowercase(Locale.ROOT)) {
            val replacement = TRANSLIT_MAP[ch]
            if (replacement != null) {
                sb.append(replacement)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun activeProgram(programs: List<EpgProgram>?, now: Long): EpgProgram? {
        if (programs.isNullOrEmpty()) return null
        return programs.find { now >= it.startTime && now < it.endTime }
    }

    fun findProgramForChannel(
        channel: IptvChannel,
        epgDataMap: Map<String, List<EpgProgram>>,
        epgCleanNameMap: Map<String, List<EpgProgram>>,
        epgTranslitNameMap: Map<String, List<EpgProgram>>,
        now: Long
    ): EpgProgram? {
        if (!channel.epgId.isNullOrBlank()) {
            val match = activeProgram(epgDataMap[channel.epgId.lowercase(Locale.ROOT).trim()], now)
            if (match != null) return match
        }
        if (!channel.tvgName.isNullOrBlank()) {
            val tvgKey = channel.tvgName.lowercase(Locale.ROOT).trim()
            val match = activeProgram(epgDataMap[tvgKey], now)
                ?: activeProgram(epgCleanNameMap[cleanName(tvgKey)], now)
            if (match != null) return match
        }
        val nameLower = channel.name.lowercase(Locale.ROOT).trim()
        val matchExact = activeProgram(epgDataMap[nameLower], now)
        if (matchExact != null) return matchExact

        val cleanChannelName = cleanName(channel.name)
        if (cleanChannelName.isNotEmpty()) {
            val matchClean = activeProgram(epgCleanNameMap[cleanChannelName], now)
            if (matchClean != null) return matchClean
        }

        val translitChannelName = transliterate(cleanChannelName)
        if (translitChannelName.isNotEmpty()) {
            val matchTranslit = activeProgram(epgTranslitNameMap[translitChannelName], now)
            if (matchTranslit != null) return matchTranslit
        }

        return null
    }

    fun findNextProgramForChannel(
        channel: IptvChannel,
        currentMatch: EpgProgram,
        epgDataMap: Map<String, List<EpgProgram>>,
        epgCleanNameMap: Map<String, List<EpgProgram>>,
        epgTranslitNameMap: Map<String, List<EpgProgram>>,
        now: Long
    ): EpgProgram? {
        val id = currentMatch.channelId.lowercase(Locale.ROOT).trim()
        val progs = epgDataMap[id]
            ?: epgCleanNameMap[cleanName(id)]
            ?: epgTranslitNameMap[transliterate(cleanName(id))]
            ?: emptyList()
        return progs.find { it.startTime >= currentMatch.endTime }
    }
}
