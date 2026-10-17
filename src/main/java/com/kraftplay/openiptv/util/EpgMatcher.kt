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
        // Normalize channel labels without deleting quality strings from inside words
        // (for example, "HD" should be removed as a token, not as arbitrary letters).
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("""\[[^]]*]"""), " ")
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""\b(ru|pl|de|ua|by|uz|kz)\s*:"""), " ")
            .replace(Regex("""\b(fhd|uhd|4k|8k|hd|sd|hevc|h[ .]?265|50fps|60fps|1080p|720p|576p|vip|premium)\b"""), " ")
            .replace(Regex("""[^a-z0-9а-я]+"""), "")
            .trim()
    }

    fun transliterate(text: String): String {
        val sb = StringBuilder()
        for (ch in text.lowercase()) {
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
        // Lists are indexed in start-time order when EPG is loaded.
        var low = 0
        var high = programs.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val p = programs[mid]
            when {
                now < p.startTime -> high = mid - 1
                now >= p.endTime -> low = mid + 1
                else -> return p
            }
        }
        return null
    }

    fun findProgramForChannel(
        channel: IptvChannel,
        epgDataMap: Map<String, List<EpgProgram>>,
        epgCleanNameMap: Map<String, List<EpgProgram>>,
        epgTranslitNameMap: Map<String, List<EpgProgram>>,
        now: Long
    ): EpgProgram? {
        val idsToTry = listOfNotNull(channel.epgId, channel.tvgName, channel.name)
            .map { it.lowercase().trim() }
            .filter { it.isNotEmpty() }

        for (id in idsToTry) {
            val match = activeProgram(epgDataMap[id], now)
            if (match != null) return match
        }

        val cleanChannelName = cleanName(channel.name)
        if (cleanChannelName.isNotEmpty()) {
            val match = activeProgram(epgCleanNameMap[cleanChannelName], now)
            if (match != null) return match
        }

        val translitChannelName = transliterate(cleanChannelName)
        if (translitChannelName.isNotEmpty()) {
            val match = activeProgram(epgTranslitNameMap[translitChannelName], now)
            if (match != null) return match
        }

        if (cleanChannelName.length >= 3) {
            // Pick the closest plausible name instead of whichever map entry happens
            // to be visited first; this avoids wrong matches for short/shared names.
            val candidates = epgCleanNameMap.keys.asSequence()
                .filter { it.length >= 4 && (it.contains(cleanChannelName) || cleanChannelName.contains(it)) }
                .sortedBy { kotlin.math.abs(it.length - cleanChannelName.length) }
            for (key in candidates) {
                val match = activeProgram(epgCleanNameMap[key], now)
                if (match != null) return match
            }
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
        val id = currentMatch.channelId.lowercase().trim()
        val progs = epgDataMap[id] 
            ?: epgCleanNameMap[cleanName(id)] 
            ?: epgTranslitNameMap[transliterate(cleanName(id))] 
            ?: emptyList()
        return progs.find { it.startTime >= currentMatch.endTime }
    }
}
