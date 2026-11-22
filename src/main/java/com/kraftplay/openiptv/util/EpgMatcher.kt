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
            .replace(Regex("""\b(ru|pl|de|ua|by|uz|kz|en|fr|it|es|tr|ro|bg|cz|sk|hu)\s*:"""), " ")
            .replace(Regex("""\b(fhd|uhd|4k|8k|hd|sd|hevc|h[ .]?265|50fps|60fps|1080p|720p|576p|vip|premium|orig|50|60|\+1|\+2|\+3|\+4|\+5|\+6|\+7|\+8|\+9)\b"""), " ")
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
        for (i in programs.indices) {
            val p = programs[i]
            if (now >= p.startTime && now < p.endTime) {
                return p
            }
        }
        return null
    }

    fun findProgramForChannel(
        channel: IptvChannel,
        epgDataMap: Map<String, List<EpgProgram>>,
        epgCleanNameMap: Map<String, List<EpgProgram>>,
        epgTranslitNameMap: Map<String, List<EpgProgram>>,
        now: Long = System.currentTimeMillis()
    ): EpgProgram? {
        // 1. Try epgId
        if (!channel.epgId.isNullOrBlank()) {
            val idKey = channel.epgId.lowercase(Locale.ROOT).trim()
            val matchExact = activeProgram(epgDataMap[idKey], now)
            if (matchExact != null) return matchExact

            val cleanId = cleanName(idKey)
            if (cleanId.isNotEmpty()) {
                val matchClean = activeProgram(epgCleanNameMap[cleanId], now)
                if (matchClean != null) return matchClean

                val translitId = transliterate(cleanId)
                if (translitId.isNotEmpty()) {
                    val matchTranslit = activeProgram(epgTranslitNameMap[translitId], now)
                    if (matchTranslit != null) return matchTranslit
                }
            }
        }

        // 2. Try tvgName
        if (!channel.tvgName.isNullOrBlank()) {
            val tvgKey = channel.tvgName.lowercase(Locale.ROOT).trim()
            val matchExact = activeProgram(epgDataMap[tvgKey], now)
            if (matchExact != null) return matchExact

            val cleanTvg = cleanName(tvgKey)
            if (cleanTvg.isNotEmpty()) {
                val matchClean = activeProgram(epgCleanNameMap[cleanTvg], now)
                if (matchClean != null) return matchClean

                val translitTvg = transliterate(cleanTvg)
                if (translitTvg.isNotEmpty()) {
                    val matchTranslit = activeProgram(epgTranslitNameMap[translitTvg], now)
                    if (matchTranslit != null) return matchTranslit
                }
            }
        }

        // 3. Try channel title (name)
        val nameKey = channel.name.lowercase(Locale.ROOT).trim()
        val matchExactName = activeProgram(epgDataMap[nameKey], now)
        if (matchExactName != null) return matchExactName

        val cleanChannelName = cleanName(channel.name)
        if (cleanChannelName.isNotEmpty()) {
            val matchCleanName = activeProgram(epgCleanNameMap[cleanChannelName], now)
            if (matchCleanName != null) return matchCleanName

            val translitChannelName = transliterate(cleanChannelName)
            if (translitChannelName.isNotEmpty()) {
                val matchTranslitName = activeProgram(epgTranslitNameMap[translitChannelName], now)
                if (matchTranslitName != null) return matchTranslitName
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
        now: Long = System.currentTimeMillis()
    ): EpgProgram? {
        val id = currentMatch.channelId.lowercase(Locale.ROOT).trim()
        val progs = epgDataMap[id]
            ?: epgCleanNameMap[cleanName(id)]
            ?: epgTranslitNameMap[transliterate(cleanName(id))]
            ?: emptyList()
        var candidate: EpgProgram? = null
        for (i in progs.indices) {
            val p = progs[i]
            if (p.startTime >= currentMatch.endTime) {
                if (candidate == null || p.startTime < candidate.startTime) {
                    candidate = p
                }
            }
        }
        return candidate
    }
}
