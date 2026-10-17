package com.kraftplay.openiptv.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomThemeConfig(
    val enabled: Boolean = true,
    val backgroundColorHex: String = "#0A0A12",
    val primaryColorHex: String = "#00FFCC",
    val surfaceColorHex: String = "#121226",
    val textColorHex: String = "#E0E0FF",
    val topBarColorHex: String = "#181830",
    val cardCornerRadiusDp: Int = 20,
    val cardAlpha: Float = 0.9f
) {
    companion object {
        val DEFAULT = CustomThemeConfig(enabled = false)

        val SAMPLE_CYBERPUNK = """
        {
          "enabled": true,
          "backgroundColorHex": "#0D0221",
          "primaryColorHex": "#00F0FF",
          "surfaceColorHex": "#1A0836",
          "textColorHex": "#F6F7F8",
          "topBarColorHex": "#260C4E",
          "cardCornerRadiusDp": 24,
          "cardAlpha": 0.85
        }
        """.trimIndent()

        val SAMPLE_GOLD = """
        {
          "enabled": true,
          "backgroundColorHex": "#14100B",
          "primaryColorHex": "#FFD700",
          "surfaceColorHex": "#241D13",
          "textColorHex": "#FFF8E7",
          "topBarColorHex": "#302619",
          "cardCornerRadiusDp": 18,
          "cardAlpha": 0.95
        }
        """.trimIndent()
    }
}
