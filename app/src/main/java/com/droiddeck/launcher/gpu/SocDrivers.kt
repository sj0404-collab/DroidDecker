package com.droiddeck.launcher.gpu

/**
 * Which non-Adreno chip carries which GPU, and which published guest driver answers it, oldest to
 * newest.
 *
 * Two lookups, in this order, because they fail differently:
 *
 *  - [of] maps `ro.board.platform` to a marketing name and, where the driver maintainers validated a
 *    build against that exact chip, to that driver id. The chip lists come from
 *    `The412Banner/droiddeck-nonadreno-drivers` (`drivers.json`) - Helio G85/G88/G95/G99/G100,
 *    Dimensity 700/810/6020/6080/6100+/1080/1100/1200/1300/8050, Kompanio 1300T, Unisoc T820 and
 *    Exynos 1280/1380 on the job-manager build, Dimensity 7300/8300 on the G615 build, Dimensity
 *    9300/9300+/8400 on the G720 build, Exynos 2200/1480/2400 on RADV.
 *  - [driverForGpu] maps the GPU the device actually reports to a driver by architecture, which is
 *    what decides whether a build can work at all: Midgard (Mali-T), Bifrost G31/G51/G71/G72,
 *    PowerVR and Huawei's Maleoon have no published driver; G52 and G76 are job-manager; G57 and
 *    newer are covered by the kbase build; G615 and G720 have chip-specific builds.
 *
 * A chip-specific build that its own author has not validated yet is deliberately not recommended:
 * the G710 profile is marked planned, so G710 devices fall back to the kbase build, which the
 * maintainers measured on a Pixel 7. Same reasoning for an unlisted Xclipse: the published RADV
 * builds name 920, 530 and 940, and other Xclipse parts get it by architecture, not by measurement.
 *
 * Qualcomm is not here: [SocNames] labels those chips and `DriverPairs` picks their Turnip build.
 */
object SocDrivers {
    const val PANVK_KBASE = "panvk-mali-kbase"
    const val PANVK_JM = "panvk-mali-jm"
    const val PANVK_G615 = "panvk-mali-g615"
    const val PANVK_G720 = "panvk-mali-g720"
    const val RADV_XCLIPSE = "radv-xclipse"

    /** What a platform code means: its name, the GPU documented in it, and a validated driver id. */
    class Soc(val label: String, val gpu: String, val driver: String?)

    internal val SOCS_FOR_TEST: Map<String, Soc> get() = SOCS

    private val SOCS: Map<String, Soc> = mapOf(
        // MediaTek Helio, Midgard and PowerVR first: no driver has been published for either.
        "mt6735" to Soc("MediaTek MT6735", "Mali-T720", null),
        "mt6737" to Soc("MediaTek MT6737", "Mali-T720", null),
        "mt6750" to Soc("MediaTek MT6750", "Mali-T860", null),
        "mt6753" to Soc("MediaTek MT6753", "Mali-T720", null),
        "mt6755" to Soc("MediaTek Helio P10", "Mali-T860", null),
        "mt6757" to Soc("MediaTek Helio P20", "Mali-T880", null),
        "mt6762" to Soc("MediaTek Helio P22/G25", "PowerVR GE8320", null),
        "mt6763" to Soc("MediaTek Helio P23", "Mali-G71", null),
        "mt6765" to Soc("MediaTek Helio P35/G35/G36", "PowerVR GE8320", null),
        "mt6771" to Soc("MediaTek Helio P60/P70", "Mali-G72", null),
        "mt6779" to Soc("MediaTek Helio P90/P95", "PowerVR GM9446", null),
        // Bifrost and Valhall: the job-manager build is the one validated on these chips.
        "mt6768" to Soc("MediaTek Helio G70/G80/G85", "Mali-G52", PANVK_JM),
        "mt6785" to Soc("MediaTek Helio G90/G90T/G95", "Mali-G76", PANVK_JM),
        "mt6781" to Soc("MediaTek Helio G96", "Mali-G57", null),
        "mt6789" to Soc("MediaTek Helio G99/G100", "Mali-G57", PANVK_JM),
        "mt8781" to Soc("MediaTek Helio G99 (tablet)", "Mali-G57", PANVK_JM),
        // Dimensity, oldest to newest.
        "mt6833" to Soc("MediaTek Dimensity 700/810/6020/6080", "Mali-G57", PANVK_JM),
        "mt6835" to Soc("MediaTek Dimensity 6100+/6300", "Mali-G57", PANVK_JM),
        "mt6853" to Soc("MediaTek Dimensity 720/800U", "Mali-G57", PANVK_JM),
        "mt6855" to Soc("MediaTek Dimensity 930/7020", "Mali-G68", PANVK_JM),
        "mt6873" to Soc("MediaTek Dimensity 800/800U", "Mali-G57", PANVK_JM),
        "mt6875" to Soc("MediaTek Dimensity 820", "Mali-G57", PANVK_JM),
        "mt6877" to Soc("MediaTek Dimensity 900/920/1080/7050", "Mali-G68", PANVK_JM),
        "mt6878" to Soc("MediaTek Dimensity 7300/7400", "Mali-G615", PANVK_G615),
        "mt6879" to Soc("MediaTek Dimensity 1050", "Mali-G610", null),
        "mt6883" to Soc("MediaTek Dimensity 1000+/8050", "Mali-G77", PANVK_JM),
        "mt6885" to Soc("MediaTek Dimensity 1000/1000L", "Mali-G77", null),
        "mt6889" to Soc("MediaTek Dimensity 1000+", "Mali-G77", null),
        "mt6891" to Soc("MediaTek Dimensity 1100", "Mali-G77", PANVK_JM),
        "mt6893" to Soc("MediaTek Dimensity 1200/1300", "Mali-G77", PANVK_JM),
        "mt6895" to Soc("MediaTek Dimensity 8000/8100", "Mali-G610", null),
        "mt6896" to Soc("MediaTek Dimensity 8200", "Mali-G610", null),
        "mt6897" to Soc("MediaTek Dimensity 8300/8350", "Mali-G615", PANVK_G615),
        "mt6983" to Soc("MediaTek Dimensity 9000/9000+", "Mali-G710", null),
        "mt6985" to Soc("MediaTek Dimensity 9200/9200+", "Immortalis-G715", null),
        "mt6989" to Soc("MediaTek Dimensity 9300/9300+", "Immortalis-G720", PANVK_G720),
        "mt6991" to Soc("MediaTek Dimensity 9400", "Immortalis-G925", null),
        // Kompanio, the tablet line.
        "mt8195" to Soc("MediaTek Kompanio 1200", "Mali-G57", null),
        "mt8798" to Soc("MediaTek Kompanio 1300T", "Mali-G77", PANVK_JM),
        // Samsung Exynos, under both platform spellings the devices report.
        "exynos7420" to Soc("Samsung Exynos 7420", "Mali-T760", null),
        "exynos7870" to Soc("Samsung Exynos 7870", "Mali-T830", null),
        "exynos7880" to Soc("Samsung Exynos 7880", "Mali-T830", null),
        "exynos8890" to Soc("Samsung Exynos 8890", "Mali-T880", null),
        "universal8895" to Soc("Samsung Exynos 8895", "Mali-G71", null),
        "exynos8895" to Soc("Samsung Exynos 8895", "Mali-G71", null),
        "universal9610" to Soc("Samsung Exynos 9610", "Mali-G72", null),
        "exynos9610" to Soc("Samsung Exynos 9610", "Mali-G72", null),
        "universal9810" to Soc("Samsung Exynos 9810", "Mali-G72", null),
        "exynos9810" to Soc("Samsung Exynos 9810", "Mali-G72", null),
        "exynos9820" to Soc("Samsung Exynos 9820", "Mali-G76", null),
        "exynos9825" to Soc("Samsung Exynos 9825", "Mali-G76", null),
        "exynos9830" to Soc("Samsung Exynos 990", "Mali-G77", null),
        "exynos990" to Soc("Samsung Exynos 990", "Mali-G77", null),
        "exynos2100" to Soc("Samsung Exynos 2100", "Mali-G78", null),
        "exynos2200" to Soc("Samsung Exynos 2200", "Xclipse 920", RADV_XCLIPSE),
        "exynos1280" to Soc("Samsung Exynos 1280", "Mali-G68", PANVK_JM),
        "exynos1330" to Soc("Samsung Exynos 1330", "Mali-G68", PANVK_JM),
        "exynos1380" to Soc("Samsung Exynos 1380", "Mali-G68", PANVK_JM),
        "exynos1480" to Soc("Samsung Exynos 1480", "Xclipse 530", RADV_XCLIPSE),
        "exynos1580" to Soc("Samsung Exynos 1580", "Xclipse 540", null),
        "exynos2400" to Soc("Samsung Exynos 2400", "Xclipse 940", RADV_XCLIPSE),
        "exynos2500" to Soc("Samsung Exynos 2500", "Xclipse 950", null),
        // Google Tensor.
        "gs101" to Soc("Google Tensor", "Mali-G78", null),
        "gs201" to Soc("Google Tensor G2", "Mali-G710", null),
        "zuma" to Soc("Google Tensor G3", "Mali-G715", null),
        // HiSilicon Kirin, including the ones that are not Mali at all.
        "kirin659" to Soc("HiSilicon Kirin 659", "Mali-T830", null),
        "kirin710" to Soc("HiSilicon Kirin 710", "Mali-G51", null),
        "kirin810" to Soc("HiSilicon Kirin 810", "Mali-G52", null),
        "kirin820" to Soc("HiSilicon Kirin 820", "Mali-G57", null),
        "kirin960" to Soc("HiSilicon Kirin 960", "Mali-G71", null),
        "kirin970" to Soc("HiSilicon Kirin 970", "Mali-G72", null),
        "kirin980" to Soc("HiSilicon Kirin 980", "Mali-G76", null),
        "kirin990" to Soc("HiSilicon Kirin 990", "Mali-G76", null),
        "kirin9000" to Soc("HiSilicon Kirin 9000", "Mali-G78", null),
        "kirin9000s" to Soc("HiSilicon Kirin 9000S", "Maleoon 910", null),
        "kirin9010" to Soc("HiSilicon Kirin 9010", "Maleoon 910", null),
        // Unisoc.
        "ums312" to Soc("Unisoc T310", "PowerVR GE8300", null),
        "ums512" to Soc("Unisoc T610/T618", "Mali-G57", null),
        "ums9230" to Soc("Unisoc T606/T612/T616", "Mali-G57", null),
        "udx710" to Soc("Unisoc T760/T770", "Mali-G57", null),
        "ums9620" to Soc("Unisoc T820", "Mali-G57", PANVK_JM),
        // Rockchip, the handheld and TV-box line.
        "rk3326" to Soc("Rockchip RK3326", "Mali-G31", null),
        "rk3399" to Soc("Rockchip RK3399", "Mali-T860", null),
        "rk3528" to Soc("Rockchip RK3528", "Mali-450", null),
        "rk3566" to Soc("Rockchip RK3566", "Mali-G52", null),
        "rk3568" to Soc("Rockchip RK3568", "Mali-G52", null),
        "rk3576" to Soc("Rockchip RK3576", "Mali-G52", null),
        "rk3588" to Soc("Rockchip RK3588", "Mali-G610", null),
    )

    /** What a `ro.board.platform` code means, or null when the table does not carry it. */
    fun of(platform: String): Soc? = SOCS[platform.trim().lowercase()]

    /** The chip's name for the UI; an unknown code is shown as itself rather than dropped. */
    fun label(platform: String): String {
        val key = platform.trim().lowercase()
        return if (key.isEmpty()) "" else of(key)?.label ?: key
    }

    /**
     * The driver to install: the one validated against this chip when there is one, else the one the
     * reported GPU's architecture allows. Null means nobody has published a driver for it, which is
     * an answer, not a gap - guessing one installs a build that cannot talk to the GPU.
     */
    fun driver(gpuName: String, gpuModel: Int, platform: String): String? =
        of(platform)?.driver ?: driverForGpu(gpuName, gpuModel)

    /** The driver a GPU model can run on, by architecture, oldest to newest. */
    fun driverForGpu(name: String, model: Int): String? {
        if ("xclipse" in name.lowercase()) return RADV_XCLIPSE
        // Arm names the premium line "Immortalis-G925" rather than "Mali-G925", so this asks the
        // same test the rest of the app uses instead of looking for one substring.
        if (!GpuInfo.isMaliName(name)) return null   // Adreno, PowerVR, Maleoon
        return when (model) {
            // Bifrost on the job manager: G52 and G76. The kbase build starts at Valhall v9.
            52, 76 -> PANVK_JM
            // Valhall v9, then CSF v10 to v13, all covered by the kbase build.
            57, 68, 77, 78, 610, 710, 715, 725, 925 -> PANVK_KBASE
            615 -> PANVK_G615
            720 -> PANVK_G720
            // Midgard (Mali-T), Bifrost G31/G51/G71/G72, and any Mali this table does not know.
            else -> null
        }
    }
}
