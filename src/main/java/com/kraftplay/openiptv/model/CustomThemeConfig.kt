package com.kraftplay.openiptv.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class CustomThemeConfig(
    val enabled: Boolean = true,
    val backgroundColorHex: String = "#0A0A12",
    val primaryColorHex: String = "#00FFCC",
    val accentColorHex: String = "#FF007F",
    val surfaceColorHex: String = "#121226",
    val textColorHex: String = "#E0E0FF",
    val topBarColorHex: String = "#181830",
    val cardBorderColorHex: String = "#00FFCC",
    val badgeColorHex: String = "#00FFCC",
    val cardCornerRadiusDp: Int = 22,
    val cardAlpha: Float = 0.92f,
    val glassEffect: Boolean = false
) {
    companion object {
        val DEFAULT = CustomThemeConfig(enabled = false)

        val SAMPLE_CYBERPUNK = """
        {
          "enabled": true,
          "backgroundColorHex": "#0D0221",
          "primaryColorHex": "#00F0FF",
          "accentColorHex": "#FF007F",
          "surfaceColorHex": "#1A0836",
          "textColorHex": "#F6F7F8",
          "topBarColorHex": "#260C4E",
          "cardBorderColorHex": "#00F0FF",
          "badgeColorHex": "#FF007F",
          "cardCornerRadiusDp": 24,
          "cardAlpha": 0.85,
          "glassEffect": true
        }
        """.trimIndent()

        val SAMPLE_GOLD = """
        {
          "enabled": true,
          "backgroundColorHex": "#14100B",
          "primaryColorHex": "#FFD700",
          "accentColorHex": "#FF9900",
          "surfaceColorHex": "#241D13",
          "textColorHex": "#FFF8E7",
          "topBarColorHex": "#302619",
          "cardBorderColorHex": "#FFD700",
          "badgeColorHex": "#FFD700",
          "cardCornerRadiusDp": 20,
          "cardAlpha": 0.95,
          "glassEffect": false
        }
        """.trimIndent()

        val SAMPLE_MONET = """
        {
          "enabled": true,
          "backgroundColorHex": "#0F141C",
          "primaryColorHex": "#A8C7FA",
          "accentColorHex": "#7CACF8",
          "surfaceColorHex": "#1E2430",
          "textColorHex": "#E1E2E6",
          "topBarColorHex": "#283040",
          "cardBorderColorHex": "#A8C7FA",
          "badgeColorHex": "#A8C7FA",
          "cardCornerRadiusDp": 28,
          "cardAlpha": 0.92,
          "glassEffect": true
        }
        """.trimIndent()

        fun generateRandom(): CustomThemeConfig {
            val palettes = listOf(
                // Cyberpunk / Synthwave
                listOf("#0D0221", "#00F0FF", "#FF007F", "#1A0836", "#F6F7F8", "#260C4E", "#00F0FF", "#FF007F"),
                // Gold Luxury
                listOf("#14100B", "#FFD700", "#FF9900", "#241D13", "#FFF8E7", "#302619", "#FFD700", "#FFD700"),
                // Emerald Matrix
                listOf("#061A14", "#00FF87", "#60EFF2", "#0F2B22", "#E8FFF8", "#173E32", "#00FF87", "#00FF87"),
                // Midnight Velvet
                listOf("#0F0C1B", "#B18CFF", "#FF6584", "#1E1833", "#F2ECFF", "#2B2248", "#B18CFF", "#FF6584"),
                // Solar Flare
                listOf("#1C0C0B", "#FF5E36", "#FFAE00", "#2E1513", "#FFEFEA", "#401E1B", "#FF5E36", "#FFAE00"),
                // Nordic Frost
                listOf("#0B151F", "#82C8FF", "#50E3C2", "#152433", "#EBF5FF", "#1F3448", "#82C8FF", "#50E3C2"),
                // Neon Tokyo
                listOf("#12001A", "#FF0055", "#00E5FF", "#220030", "#FFEBF2", "#330048", "#FF0055", "#00E5FF"),
                // Obsidian Blue
                listOf("#0A1118", "#38BDF8", "#818CF8", "#14202C", "#F0F9FF", "#1E2F40", "#38BDF8", "#818CF8")
            )
            val c = palettes[Random.nextInt(palettes.size)]
            val radius = listOf(16, 20, 24, 28).random()
            val alpha = listOf(0.85f, 0.90f, 0.94f).random()
            val glass = Random.nextBoolean()

            return CustomThemeConfig(
                enabled = true,
                backgroundColorHex = c[0],
                primaryColorHex = c[1],
                accentColorHex = c[2],
                surfaceColorHex = c[3],
                textColorHex = c[4],
                topBarColorHex = c[5],
                cardBorderColorHex = c[6],
                badgeColorHex = c[7],
                cardCornerRadiusDp = radius,
                cardAlpha = alpha,
                glassEffect = glass
            )
        }
    }
}
