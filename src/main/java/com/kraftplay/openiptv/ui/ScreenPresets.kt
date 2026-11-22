package com.kraftplay.openiptv.ui

/** Roadmap screen presets. Car values follow parked car-app quality minimums (UX-1/2/3). */
internal object ScreenPresets {
    fun targetSize(isAuto: Boolean): Float = if (isAuto) 64f else 48f
    fun iconSize(isAuto: Boolean): Float = if (isAuto) 32f else 24f
    fun gap(isAuto: Boolean): Float = if (isAuto) 24f else 8f
    fun minTextSp(isAuto: Boolean): Float = if (isAuto) 24f else 14f
}
