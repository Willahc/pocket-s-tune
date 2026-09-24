package com.kei.pulse.data

import com.kei.pulse.model.CpuPolicyInfo

class CpuPolicyDetector(
    private val fileSystem: SysfsFileSystem = RealSysfsFileSystem(),
    private val privilegedReader: PrivilegedSysfsReader,
    private val policyRoot: String = "/sys/devices/system/cpu/cpufreq",
    private val gpuDetector: GpuFreqDetector? = null,
) {
    fun detectPolicies(): List<CpuPolicyInfo> {
        val cpuPolicies = fileSystem.listPolicyDirectories(policyRoot)
            .sortedBy(::policyIdOrMax)
            .mapNotNull(::parsePolicy)
            .sortedBy { it.id }
        // Append the GPU as a synthetic policy so it appears last in the UI and flows
        // through the same profile/apply pipeline. Null-safe: if no GPU node is found,
        // the app behaves exactly like upstream ClusterTune.
        val gpuPolicy = gpuDetector?.detectAsPolicy()
        return if (gpuPolicy != null) cpuPolicies + gpuPolicy else cpuPolicies
    }

    fun readCurrentMaxValues(policies: List<CpuPolicyInfo>): Map<Int, Int> {
        val rawValues = privilegedReader.readTexts(policies.map { it.scalingMaxPath })
        return policies.mapNotNull { policy ->
            val value = if (policy.isGpu) {
                gpuDetector?.maxKHzForPowerLevel(policy, rawValues[policy.scalingMaxPath])
            } else {
                rawValues[policy.scalingMaxPath]?.trim()?.toIntOrNull()
            }
            value?.let { policy.id to it }
        }.toMap()
    }

    private fun parsePolicy(policyPath: String): CpuPolicyInfo? {
        val policyName = policyPath.substringAfterLast('/')
        val id = policyName.removePrefix("policy").toIntOrNull() ?: return null
        val scalingMaxPath = "$policyPath/scaling_max_freq"
        val timeInStatePath = "$policyPath/stats/time_in_state"
        val values = readTexts(
            listOf(
                "$policyPath/scaling_available_frequencies",
                "$policyPath/affected_cpus",
                "$policyPath/related_cpus",
                "$policyPath/cpuinfo_max_freq",
                scalingMaxPath,
                timeInStatePath,
                "$policyPath/scaling_min_freq",
            ),
        )
        val rawSupported = parseFrequencies(values["$policyPath/scaling_available_frequencies"])
        val cpuIds = parseCpuIds(values["$policyPath/affected_cpus"])
            .ifEmpty { parseCpuIds(values["$policyPath/related_cpus"]) }
            .ifEmpty { listOf(id) }
        val cpuInfoMax = values["$policyPath/cpuinfo_max_freq"]?.toIntOrNull()
        val scalingMax = values[scalingMaxPath]?.toIntOrNull()
        val timeInStateMax = readTimeInStateMax(values[timeInStatePath])
        val minFreq = values["$policyPath/scaling_min_freq"]?.toIntOrNull()
            ?: rawSupported.firstOrNull()
            ?: 0
        val supported = rawSupported.ifEmpty {
            buildFallbackFrequencies(
                minFreq = minFreq,
                maxFreq = maxOfNotNull(cpuInfoMax, scalingMax) ?: 0,
                currentMaxFreq = scalingMax ?: cpuInfoMax ?: 0,
            )
        }
        val selectableMax = supported.lastOrNull() ?: maxOfNotNull(scalingMax, cpuInfoMax) ?: return null
        val observedMax = maxOfNotNull(cpuInfoMax, scalingMax, timeInStateMax, selectableMax) ?: selectableMax
        val currentMax = scalingMax ?: supported.lastOrNull() ?: cpuInfoMax ?: selectableMax

        return CpuPolicyInfo(
            id = id,
            policyPath = policyPath,
            scalingMaxPath = scalingMaxPath,
            currentMaxFreq = currentMax,
            selectableMaxFreq = selectableMax,
            observedMaxFreq = observedMax,
            minFreq = minFreq,
            supportedFrequencies = supported,
            cpuIds = cpuIds,
        )
    }

    internal fun parseFrequencies(raw: String?): List<Int> {
        return raw.orEmpty()
            .split(Regex("\\s+"))
            .mapNotNull { it.toIntOrNull() }
            .distinct()
            .sorted()
    }

    internal fun buildFallbackFrequencies(
        minFreq: Int,
        maxFreq: Int,
        currentMaxFreq: Int,
    ): List<Int> {
        return listOf(minFreq, currentMaxFreq, maxFreq)
            .filter { it > 0 }
            .distinct()
            .sorted()
    }

    internal fun parseCpuIds(raw: String?): List<Int> {
        return raw.orEmpty()
            .split(Regex("\\s+"))
            .mapNotNull { it.toIntOrNull() }
            .distinct()
            .sorted()
    }

    private fun readTimeInStateMax(raw: String?): Int? {
        return raw
            ?.lineSequence()
            ?.mapNotNull { line ->
                line.trim()
                    .split(Regex("\\s+"))
                    .firstOrNull()
                    ?.toIntOrNull()
            }
            ?.maxOrNull()
    }

    private fun maxOfNotNull(vararg values: Int?): Int? {
        return values.filterNotNull().maxOrNull()
    }

    private fun readTexts(paths: List<String>): Map<String, String?> {
        return privilegedReader
            .readTexts(paths)
            .mapValues { (_, value) -> value?.trim()?.takeIf { it.isNotEmpty() } }
    }

    private fun policyIdOrMax(policyPath: String): Int {
        return policyPath.substringAfterLast('/').removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE
    }
}
