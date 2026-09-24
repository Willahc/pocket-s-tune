package com.kei.pulse.data

import com.kei.pulse.model.CpuPolicyInfo
import com.kei.pulse.model.PerformanceProfile
import com.kei.pulse.model.ProfileSource
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledProfileProviderTest {

    private val cq8725sJson = """
        {
          "schemaVersion": 1,
          "socModel": "CQ8725S",
          "profiles": [
            {
              "id": "bundled_cq8725s_small",
              "name": "Small Underclock",
              "maxFrequencies": {
                "0": 2745600,
                "6": 3072000
              }
            },
            {
              "id": "bundled_cq8725s_medium",
              "name": "Medium Underclock",
              "maxFrequencies": {
                "0": 2227200,
                "6": 2246400
              }
            },
            {
              "id": "bundled_cq8725s_large",
              "name": "Large Underclock",
              "maxFrequencies": {
                "0": 1785600,
                "6": 1958400
              }
            }
          ]
        }
    """.trimIndent()

    private val provider = BundledProfileProvider(
        readProfileJson = { socModel ->
            if (socModel == "CQ8725S") {
                cq8725sJson
            } else {
                null
            }
        },
        parseProfiles = { cq8725sProfiles },
        socDetector = fakeSocDetector("CQ8725S"),
    )

    @Test
    fun `returns soc profiles when matching asset exists and policy0 and policy6 are present`() {
        val profiles = provider.createProfiles(
            listOf(
                policy(id = 0, stockMax = 3_532_800, supported = listOf(1_785_600, 2_227_200, 2_745_600, 3_532_800)),
                policy(id = 6, stockMax = 4_320_000, supported = listOf(1_958_400, 2_246_400, 3_072_000, 4_320_000)),
            ),
        )

        assertEquals(listOf("Small Underclock", "Medium Underclock", "Large Underclock"), profiles.map { it.name })
    }

    @Test
    fun `returns empty when required policies are missing`() {
        val profiles = provider.createProfiles(listOf(policy(id = 2, stockMax = 2_500_000, supported = listOf(800_000, 2_500_000))))
        assertTrue(profiles.isEmpty())
    }

    @Test
    fun `returns empty when no matching soc asset exists`() {
        val profiles = BundledProfileProvider(
            readProfileJson = { null },
            parseProfiles = { cq8725sProfiles },
            socDetector = fakeSocDetector("QCS8550"),
        ).createProfiles(
            listOf(
                policy(id = 0, stockMax = 3_532_800, supported = listOf(1_785_600, 2_227_200, 2_745_600, 3_532_800)),
                policy(id = 6, stockMax = 4_320_000, supported = listOf(1_958_400, 2_246_400, 3_072_000, 4_320_000)),
            ),
        )

        assertTrue(profiles.isEmpty())
    }

    @Test
    fun `SG8275 bundled asset profiles survive filtering with CPU and GPU policies`() {
        val rawJson = sg8275AssetFile().readText()
        val policies = sg8275Policies(rawJson, includeGpu = true)

        val profiles = sg8275Provider(rawJson).createProfiles(policies)

        assertEquals(listOf("bundled_sg8275_stock", "bundled_sg8275_stable_860"), profiles.map { it.id })
        assertEquals("Padrão", profiles.first().name)
        assertTrue(profiles.last().name.contains("860MHz"))
        assertTrue(profiles.all { profile ->
            profile.maxFrequencies.keys.containsAll(listOf(0, 3, 7, CpuPolicyInfo.GPU_POLICY_ID))
        })
        assertEquals(860_000, profiles.last().maxFrequencies[CpuPolicyInfo.GPU_POLICY_ID])
    }

    @Test
    fun `SG8275 bundled asset profiles survive filtering when GPU policy is missing`() {
        val rawJson = sg8275AssetFile().readText()
        val policies = sg8275Policies(rawJson, includeGpu = false)

        val profiles = sg8275Provider(rawJson).createProfiles(policies)

        assertEquals(listOf("bundled_sg8275_stock", "bundled_sg8275_stable_860"), profiles.map { it.id })
        assertTrue(profiles.all { profile ->
            profile.maxFrequencies.keys == setOf(0, 3, 7)
        })
    }

    private fun fakeSocDetector(socModel: String) = object : SocDetector() {
        override fun detectSocModel(): String = socModel
    }

    private val cq8725sProfiles = listOf(
        PerformanceProfile(
            id = "bundled_cq8725s_small",
            name = "Small Underclock",
            maxFrequencies = mapOf(0 to 2_745_600, 6 to 3_072_000),
            source = ProfileSource.BUNDLED,
        ),
        PerformanceProfile(
            id = "bundled_cq8725s_medium",
            name = "Medium Underclock",
            maxFrequencies = mapOf(0 to 2_227_200, 6 to 2_246_400),
            source = ProfileSource.BUNDLED,
        ),
        PerformanceProfile(
            id = "bundled_cq8725s_large",
            name = "Large Underclock",
            maxFrequencies = mapOf(0 to 1_785_600, 6 to 1_958_400),
            source = ProfileSource.BUNDLED,
        ),
    )

    private fun policy(id: Int, stockMax: Int, supported: List<Int>) = CpuPolicyInfo(
        id = id,
        policyPath = "/sys/devices/system/cpu/cpufreq/policy$id",
        scalingMaxPath = "/sys/devices/system/cpu/cpufreq/policy$id/scaling_max_freq",
        currentMaxFreq = stockMax,
        selectableMaxFreq = stockMax,
        observedMaxFreq = stockMax,
        minFreq = supported.first(),
        supportedFrequencies = supported,
    )

    private fun sg8275Provider(rawJson: String) = BundledProfileProvider(
        readProfileJson = { socModel -> rawJson.takeIf { socModel == "SG8275" } },
        socDetector = fakeSocDetector("SG8275"),
    )

    private fun sg8275Policies(rawJson: String, includeGpu: Boolean): List<CpuPolicyInfo> {
        val root = Json.parseToJsonElement(rawJson).jsonObject
        val cpuPolicies = root.getValue("cpuClusters").jsonArray.map { element ->
            val cluster = element.jsonObject
            val id = cluster.getValue("id").jsonPrimitive.content.removePrefix("policy").toInt()
            val frequencies = cluster.getValue("availableFrequenciesKhz").jsonArray.map { frequency ->
                frequency.jsonPrimitive.int
            }
            policy(
                id = id,
                stockMax = cluster.getValue("maxFreqKhz").jsonPrimitive.int,
                supported = frequencies,
            )
        }
        val gpuPolicy = if (includeGpu) {
            val frequencies = root
                .getValue("gpu")
                .jsonObject
                .getValue("availableFrequenciesHz")
                .jsonArray
                .map { frequency -> (frequency.jsonPrimitive.long / 1000L).toInt() }
                .sorted()
            policy(
                id = CpuPolicyInfo.GPU_POLICY_ID,
                stockMax = frequencies.last(),
                supported = frequencies,
            ).copy(
                policyPath = "/sys/class/kgsl/kgsl-3d0",
                scalingMaxPath = "/sys/class/kgsl/kgsl-3d0/max_pwrlevel",
                cpuIds = emptyList(),
            )
        } else {
            null
        }
        return cpuPolicies + listOfNotNull(gpuPolicy)
    }

    private fun sg8275AssetFile(): File {
        return listOf(
            File("src/main/assets/bundled_profiles/SG8275.json"),
            File("app/src/main/assets/bundled_profiles/SG8275.json"),
        ).firstOrNull { it.isFile }
            ?: error("SG8275 bundled profile asset not found")
    }
}
