package com.droiddeck.launcher.gpu

import android.content.Context
import android.os.Build
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.FileUtils
import java.io.File

data class GpuInfo(
    val name: String,
    val model: Int,
    val family: Family,
    val soc: String,
    val oneUi8Gen2: Boolean,
    val kgslName: String = "",
    val modelSource: String = "",
) {
    enum class Family(val label: String) {
        A8XX("Adreno 8xx"),
        A7XX_LOW("Adreno 710/720/722"),
        A7XX("Adreno 7xx"),
        A6XX("Adreno 6xx"),
        ADRENO_UNKNOWN("Adreno"),
        MALI_5TH("Mali G720/G725/G925"),
        MALI_VALHALL("Mali Valhall (G57-G715)"),
        MALI_BIFROST("Mali Bifrost (G31-G76)"),
        MALI_UNKNOWN("Mali"),
        NOT_ADRENO("Not an Adreno GPU");

        fun label(context: Context): String = if (this == NOT_ADRENO) context.getString(R.string.gpuinfo_not_adreno) else label
    }

    enum class Support { TESTED, UNTESTED, UNSUPPORTED }

    val mali: Boolean get() = family in MALI

    val support: Support
        get() = when {
            family == Family.NOT_ADRENO -> Support.UNSUPPORTED
            mali -> Support.UNSUPPORTED
            family == Family.A6XX && model == 650 -> Support.TESTED
            family == Family.A8XX -> Support.TESTED
            family == Family.A7XX && model >= 725 -> Support.TESTED
            else -> Support.UNTESTED
        }

    val supportText: String
        get() = when {
            mali -> "Not supported yet: the guest driver is Turnip for Adreno, and Mali needs a PanVK " +
                "build with BCn decode; DXVK then runs as a hybrid of two builds"
            else -> when (support) {
                Support.TESTED -> "Supported"
                Support.UNTESTED -> if (family == Family.A7XX_LOW) "Experimental: its drivers are test builds"
                    else "Outside tested hardware (Adreno 650, 725 and newer): it may not run"
                Support.UNSUPPORTED -> "Not supported: DroidDeck needs an Adreno (Snapdragon) GPU"
            }
        }

    fun supportText(context: Context): String = when {
        mali -> context.getString(R.string.gpuinfo_unsupported_mali)
        else -> context.getString(when (support) {
            Support.TESTED -> R.string.gpuinfo_supported
            Support.UNTESTED -> if (family == Family.A7XX_LOW) R.string.gpuinfo_experimental else R.string.gpuinfo_below_tested
            Support.UNSUPPORTED -> R.string.gpuinfo_unsupported
        })
    }

    fun displayName(context: Context): String = if (name == UNNAMED) context.getString(R.string.gpu_this_gpu) else name

    companion object {
        private const val UNNAMED = "this GPU"
        private const val ADRENO_HAL = "/vendor/lib64/hw/vulkan.adreno.so"
        private const val MALI_HAL = "/vendor/lib64/hw/vulkan.mali.so"

        val MALI = setOf(Family.MALI_5TH, Family.MALI_VALHALL, Family.MALI_BIFROST, Family.MALI_UNKNOWN)

        private val FIFTH_GEN = setOf(720, 725, 925)
        private val VALHALL = setOf(57, 68, 77, 78, 610, 615, 710, 715)
        private val BIFROST = setOf(31, 51, 52, 71, 72, 76)
        private val MALI_NAME = Regex("""(?i)(?:mali|immortalis)[\s-]*g?[\s-]*(\d{2,4})""")

        fun detect(): GpuInfo {
            val adreno = File("/sys/class/kgsl/kgsl-3d0").exists() || File(ADRENO_HAL).exists()
            val vkDevice = if (adreno) "" else VulkanInfo.cachedOrNull()?.get("device").orEmpty()
            val mali = !adreno && (isMaliName(vkDevice) || File(MALI_HAL).exists() ||
                systemProperty("ro.hardware.vulkan").contains("mali", ignoreCase = true))
            val raw = listOf("/sys/class/kgsl/kgsl-3d0/gpu_model", "/sys/class/kgsl/kgsl-3d0/gpu_chipid")
                .firstNotNullOfOrNull { FileUtils.readString(File(it))?.trim()?.takeIf(String::isNotEmpty) }
            val soc = if (adreno) SocNames.label() else ""
            val fromKernel = raw?.let { threeDigits(it) } ?: 0
            val fromVulkan = if (fromKernel > 0 || !adreno) 0 else VulkanInfo.cachedOrNull()?.get("device")?.let { threeDigits(it) } ?: 0
            val fromPlatform = if (fromKernel > 0 || fromVulkan > 0 || !adreno) 0 else platformModel()
            val maliModel = if (mali) maliModelOf(vkDevice) else 0
            val model = if (mali) maliModel else maxOf(fromKernel, fromVulkan, fromPlatform)
            val source = when {
                mali -> if (maliModel > 0) "vulkan" else ""
                fromKernel > 0 -> "kernel"
                fromVulkan > 0 -> "vulkan"
                fromPlatform > 0 -> "platform"
                else -> ""
            }
            val family = if (mali) maliFamilyOf(maliModel) else familyOf(adreno, model)
            val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            return GpuInfo(
                name = when {
                    mali -> vkDevice.ifEmpty { "Mali" }
                    !adreno -> Build.HARDWARE.ifBlank { UNNAMED }
                    model > 0 -> "Adreno $model"
                    else -> "Adreno"
                },
                model = model, family = family, soc = soc,
                oneUi8Gen2 = samsung && !mali && model == 740,
                kgslName = raw.orEmpty(), modelSource = source,
            )
        }

        internal fun threeDigits(text: String): Int = Regex("""(\d{3})""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        internal fun isMaliName(name: String): Boolean = name.contains("mali", ignoreCase = true) ||
            name.contains("immortalis", ignoreCase = true)

        internal fun maliModelOf(name: String): Int = MALI_NAME.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        internal fun maliFamilyOf(model: Int): Family = when (model) {
            0 -> Family.MALI_UNKNOWN
            in FIFTH_GEN -> Family.MALI_5TH
            in VALHALL -> Family.MALI_VALHALL
            in BIFROST -> Family.MALI_BIFROST
            else -> Family.MALI_UNKNOWN
        }

        private val PLATFORMS = mapOf(
            "msmnile" to 640, "kona" to 650, "lahaina" to 660, "taro" to 730, "cape" to 730,
            "kalama" to 740, "pineapple" to 750, "sun" to 830,
        )

        internal fun platformModel(platform: String = systemProperty("ro.board.platform")): Int =
            PLATFORMS[platform.lowercase()] ?: PLATFORMS[systemProperty("ro.vendor.qti.soc_name").lowercase()] ?: 0

        fun systemProperty(name: String): String = try {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as? String ?: ""
        } catch (_: Exception) {
            ""
        }

        internal fun familyOf(adreno: Boolean, model: Int): Family = when {
            !adreno -> Family.NOT_ADRENO
            model == 0 -> Family.ADRENO_UNKNOWN
            model >= 800 -> Family.A8XX
            model in listOf(710, 720, 722) -> Family.A7XX_LOW
            model >= 700 -> Family.A7XX
            model >= 600 -> Family.A6XX
            else -> Family.ADRENO_UNKNOWN
        }
    }
}
