package com.kei.pulse.model

enum class AutoTdpPreset(
    val label: String,
    val tagline: String,
    val fpsTarget: Int,
    val tdpLimitW: Int,
    val bias: AutoTdpBias,
    val aggressivePark: Boolean,
) {
    RETRO("Retro", "5W / 60fps", 60, 5, AutoTdpBias.EFFICIENT, true),
    EMU_BALANCED("Emulação", "8W / 60fps", 60, 8, AutoTdpBias.EFFICIENT, true),
    PS3_COOL("PS3", "10W / 60fps", 60, 10, AutoTdpBias.BALANCED, true),
    PC_GAMEHUB("PC/GameHub", "12W / 60fps", 60, 12, AutoTdpBias.BALANCED, true),
    SMOOTH("Suave", "13W / 60fps", 60, 13, AutoTdpBias.SMOOTH, false),
}
