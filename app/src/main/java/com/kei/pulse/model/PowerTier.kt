package com.kei.pulse.model

/**
 * The four PULSE performance tiers. On Snapdragon there is no programmable wattage cap
 * (unlike x86 AYANEO/AYASPACE), so each tier is a *power envelope* implemented by capping
 * the CPU clusters and the GPU to a fraction of their real top frequency. CUSTOM is manual.
 *
 * Factors are applied to each policy's true max frequency and snapped to a real OPP, so
 * the tiers stay valid on every device regardless of its actual frequency table.
 *
 * [governorLabel] is the smart-default CPU governor each preset re-asserts on apply (resolved
 * to a real kernel governor via GovernorController.OPTIONS at apply time; null = leave alone /
 * remembered separately for Custom). Labels match GovernorController.OPTIONS exactly.
 */
enum class PowerTier(
    val label: String,
    val tagline: String,
    val cpuFactor: Double,
    val gpuFactor: Double,
    val governorLabel: String?,
    private val legacyLabels: Set<String> = emptySet(),
) {
    MAX("AAA / Máx", "Teto total do SoC", 1.0, 1.0, "Desempenho", setOf("AAA / Max")),
    BALANCED("Equilibrado", "Mais frio em sessões longas", 0.78, 0.70, "Equilibrado", setOf("Balanced")),
    POWER_SAVING("Economia", "Máxima autonomia de bateria", 0.55, 0.45, "Economia", setOf("Power Saving")),
    CUSTOM("Personalizado", "Seus próprios limites", 1.0, 1.0, null, setOf("Custom")),
    ;

    companion object {
        fun fromLabel(label: String?): PowerTier? {
            if (label.isNullOrBlank()) return null
            return entries.firstOrNull { tier ->
                label == tier.label || label in tier.legacyLabels || label == tier.name
            }
        }
    }
}
