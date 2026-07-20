package com.kraftplay.openiptv.parser

import com.kraftplay.openiptv.model.EpgProgram
import com.kraftplay.openiptv.model.IptvChannel
import java.util.Locale

data class EpgParseResult(
    val channelAliases: Map<String, Set<String>>,
    val programs: List<EpgProgram>
)

object EpgMatcher {
    fun normalizeKey(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun channelLookupKeys(channel: IptvChannel): List<String> =
        listOfNotNull(channel.epgId, channel.tvgName, channel.name)
            .filter { it.isNotBlank() }
            .map(::normalizeKey)
            .distinct()

    fun buildProgramIndex(
        programs: List<EpgProgram>,
        channelAliases: Map<String, Set<String>>
    ): Map<String, List<EpgProgram>> {
        val index = mutableMapOf<String, MutableList<EpgProgram>>()

        fun addProgram(key: String, program: EpgProgram) {
            if (key.isBlank()) return
            index.getOrPut(key) { mutableListOf() }.add(program)
        }

        for (program in programs) {
            val rawChannelId = program.channelId.trim()
            val keys = linkedSetOf<String>()

            if (rawChannelId.isNotEmpty()) {
                keys += normalizeKey(rawChannelId)
                channelAliases[rawChannelId]?.let { keys.addAll(it) }
            }

            for (key in keys) {
                addProgram(key, program)
            }
        }

        return index.mapValues { (_, items) ->
            items.distinctBy { "${it.channelId}_${it.startTime}" }.sortedBy { it.startTime }
        }
    }

    fun programsForChannel(
        channel: IptvChannel,
        programIndex: Map<String, List<EpgProgram>>
    ): List<EpgProgram> {
        val seen = linkedSetOf<String>()
        val result = mutableListOf<EpgProgram>()

        for (key in channelLookupKeys(channel)) {
            programIndex[key]?.forEach { program ->
                val id = "${program.channelId}_${program.startTime}"
                if (seen.add(id)) {
                    result.add(program)
                }
            }
        }

        return result.sortedBy { it.startTime }
    }

    fun currentAndNext(
        channel: IptvChannel,
        programIndex: Map<String, List<EpgProgram>>,
        now: Long = System.currentTimeMillis()
    ): Pair<EpgProgram?, EpgProgram?> {
        val schedule = programsForChannel(channel, programIndex)
        val current = schedule.find { it.startTime <= now && it.endTime > now }
        val next = if (current != null) {
            schedule.filter { it.startTime >= current.endTime }.minByOrNull { it.startTime }
        } else {
            schedule.filter { it.startTime > now }.minByOrNull { it.startTime }
        }
        return current to next
    }
}
