package com.droiddeck.launcher.session

/**
 * One DXVK install split into the two halves its own PE import graph keeps apart, so that each half
 * can come from a different build. Read off the shipped DLLs: d3d8 imports Direct3DCreate9 from
 * d3d9; d3d10 and d3d10_1 import D3D10CoreCreateDevice from d3d10core and CreateDXGIFactory from
 * dxgi; d3d10core imports D3D11CoreCreateDevice from d3d11; d3d11 imports CreateDXGIFactory1 from
 * dxgi. Nothing is imported across the halves and every crossing that does exist is a documented
 * export, so the DLLs inside one half have to stay from one build while the halves may differ.
 * vkd3d-proton's d3d12 imports neither half, so a DX12 game is untouched by the split.
 *
 * Mali is why the split exists. Its drivers stop at Vulkan 1.1-1.3 without robustness2, DXVK 2.x
 * needs Vulkan 1.3 plus robustness2, 2.7 adds maintenance5 and 3.x adds maintenance6 and
 * load_store_op_none, while the 1.x half still creates a device on Vulkan 1.1. No single build is
 * both the fast D3D9 and the accurate D3D11 on such a driver; one build per half can be.
 */
object DxvkHybrid {
    const val COMP = "dxvk"
    const val RELEASE = "Dxvk-Linux"

    /** The 1.x build Mali users report as the responsive one; the accurate half is picked per driver. */
    const val FAST = "Dxvk-1.7.2-linux.wcp"

    private const val DIR = "files/lib/wine/dxvk"
    val ARCHES = listOf("aarch64-windows", "i386-windows")

    enum class Half(val title: String, val dlls: List<String>) {
        D3D9("D3D8/D3D9", listOf("d3d9.dll", "d3d8.dll")),
        D3D11("DXGI/D3D10/D3D11", listOf("dxgi.dll", "d3d11.dll", "d3d10core.dll", "d3d10.dll", "d3d10_1.dll"));

        companion object {
            fun of(dll: String): Half? = entries.firstOrNull { half -> half.dlls.any { it.equals(dll, ignoreCase = true) } }
        }
    }

    /** A build a half may come from, with what its driver has to offer before it can create a device. */
    data class Build(
        val file: String,
        val apiMinor: Int,
        val robustness2: Boolean = false,
        val maintenance5: Boolean = false,
        val maintenance6: Boolean = false,
    ) {
        fun runsOn(caps: Caps) = caps.bc && caps.apiMinor >= apiMinor &&
            (!robustness2 || caps.robustness2) &&
            (!maintenance5 || caps.maintenance5) &&
            (!maintenance6 || caps.maintenance6 && caps.loadStoreOpNone)
    }

    val BUILDS = listOf(
        Build("Dxvk-1.7.2-linux.wcp", 1),
        Build("dxvk-1.10.3-linux.wcp", 1),
        Build("dxvk-2.3.1-linux.wcp", 3, robustness2 = true),
        Build("dxvk-2.6.2-linux.wcp", 3, robustness2 = true),
        Build("dxvk-2.7.1-linux.wcp", 3, robustness2 = true, maintenance5 = true),
        Build("dxvk-3.1.1-linux.wcp", 3, robustness2 = true, maintenance5 = true, maintenance6 = true),
    )

    /**
     * What the driver that runs DXVK can do: read off the phone's own driver in the keys VulkanInfo
     * prints, or taken from a [Guest] profile when the guest runs a driver DroidDeck installed.
     */
    data class Caps(
        val apiMinor: Int,
        val bc: Boolean,
        val robustness2: Boolean,
        val maintenance5: Boolean,
        val maintenance6: Boolean,
        val loadStoreOpNone: Boolean,
    ) {
        enum class Block(val label: String) {
            NO_API("the device report carries no Vulkan API version"),
            NO_BC("the driver has no BC textures, which every DXVK build requires"),
        }

        /** Why no DXVK build at all can run here, or null when at least one can. */
        val block: Block?
            get() = when {
                apiMinor == 0 -> Block.NO_API
                !bc -> Block.NO_BC
                else -> null
            }

        fun newest(): Build? = BUILDS.lastOrNull { it.runsOn(this) }

        companion object {
            fun of(report: Map<String, String>): Caps = Caps(
                apiMinor = report["api"]?.split('.')?.getOrNull(1)?.toIntOrNull() ?: 0,
                bc = yes(report["texture_bc"]),
                robustness2 = yes(report["ext.VK_EXT_robustness2"]),
                maintenance5 = yes(report["ext.VK_KHR_maintenance5"]),
                maintenance6 = yes(report["ext.VK_KHR_maintenance6"]),
                loadStoreOpNone = yes(report["ext.VK_KHR_load_store_op_none"]),
            )

            /**
             * The guest's driver decides, not the phone's: a vendor Mali driver reports no BC
             * textures at all, while the PanVK installed beside Proton reports them. A driver that
             * is not profiled keeps [host], which is the conservative floor.
             */
            fun forGuest(guest: Guest?, host: Caps): Caps = when {
                guest == null -> host
                guest.caps != null -> guest.caps
                guest.dropsHostExtras -> host.copy(
                    robustness2 = false, maintenance5 = false, maintenance6 = false, loadStoreOpNone = false,
                )

                else -> host
            }

            private fun yes(value: String?) = value.equals("yes", ignoreCase = true)
        }
    }

    /**
     * The drivers DroidDeck installs beside Proton, with what their published builds report for
     * themselves - the phone's own Vulkan report says nothing about them. Taken from the driver
     * table and release notes of The412Banner/droiddeck-nonadreno-drivers and from zenithblue-oss's
     * PanVK-kbase releases: BC1 RGB and BC7 at 8 bpp, robustness2 (robustBufferAccess2 plus
     * nullDescriptor) proven on v9 and newer, no maintenance5 anywhere, so the accurate half of a
     * hybrid stops at 2.6.2 on every one of them.
     */
    enum class Guest(val ids: List<String>, val caps: Caps?, val dropsHostExtras: Boolean = false) {
        /** Mali v9 to v13 on Arm's kbase driver. */
        PANVK_KBASE(listOf("panvk-mali-kbase", "panvk-kbase"), guestCaps(robustness2 = true)),

        /** The per-chip CSF builds share that Mesa base and carry the same fixes. */
        PANVK_G720(listOf("panvk-mali-g720"), guestCaps(robustness2 = true)),
        PANVK_G615(listOf("panvk-mali-g615"), guestCaps(robustness2 = true)),

        /** Its author marks the g610-v10-csf profile as planned and not yet validated. */
        PANVK_G710(listOf("panvk-mali-g710"), guestCaps(robustness2 = false)),

        /** Older Mali on the job manager: BC yes, nullDescriptor still experimental on v9. */
        PANVK_JM(listOf("panvk-mali-jm"), guestCaps(robustness2 = false)),

        /** Handheld pairs whose Linux half is the kbase build. */
        PANVK_FAUX123(listOf("panvk-faux123"), guestCaps(robustness2 = true)),

        /** The Mali bridge emulates BCn beside the system driver; robustness2 is not established. */
        VKBRIDGE(listOf("vkbridge", "mali-bridge", "bridge"), guestCaps(robustness2 = false)),

        /** Venus forwards the phone's own driver, and on most Malis offers no robustness2. */
        VENUS(listOf("venus"), null, dropsHostExtras = true),

        /** Samsung's Exynos GPUs, through RADV. */
        RADV_XCLIPSE(listOf("radv-xclipse", "radv"), guestCaps(robustness2 = true)),

        /** Turnip for Adreno: its builds differ, so the phone's own report stays the floor. */
        TURNIP(listOf("freedreno", "turnip"), null);

        companion object {
            /** A driver id, an installed bundle's tag, or the ICD manifest the guest carries. */
            fun of(id: String): Guest? {
                val key = id.trim().lowercase()
                if (key.isEmpty()) return null
                values().firstOrNull { g -> g.ids.any { key == it || key.startsWith("$it-") || key.startsWith("$it:") } }
                    ?.let { return it }
                return when {
                    "venus" in key -> VENUS
                    "bridge" in key -> VKBRIDGE
                    "panfrost" in key || "panvk" in key -> when {
                        "jm" in key -> PANVK_JM
                        "g720" in key -> PANVK_G720
                        "g615" in key -> PANVK_G615
                        "g710" in key || "g610" in key -> PANVK_G710
                        "faux123" in key -> PANVK_FAUX123
                        else -> PANVK_KBASE
                    }

                    "xclipse" in key || "radv" in key -> RADV_XCLIPSE
                    "freedreno" in key || "turnip" in key -> TURNIP
                    else -> null
                }
            }
        }
    }

    /**
     * Which build answers which half. [overlay] lets a DLL its own half lacks be borrowed from the
     * other build, which crosses a build boundary inside a documented export: DXVK's d3d8 over the
     * other build's d3d9, or DXVK's d3d10 over the other build's d3d10core. Without it the DLL is
     * left out and Wine's own builtin serves that API on top of the installed half.
     */
    data class Spec(val d3d9: String, val d3d11: String, val overlay: Boolean = false) {
        init {
            require(d3d9 != d3d11) { "a hybrid mixes two builds" }
        }

        val reversed: Spec get() = Spec(d3d11, d3d9, overlay)
        val sources: List<String> get() = listOf(d3d9, d3d11)
        val file: String get() = "dxvk-hybrid-d3d9-${tag(d3d9)}+d3d11-${tag(d3d11)}${if (overlay) "+overlay" else ""}.wcp"
        val version: String get() = "${tag(d3d9)} ${Half.D3D9.title} + ${tag(d3d11)} ${Half.D3D11.title}"
        val description: String
            get() = "Hybrid DXVK · ${Half.D3D9.title} from ${tag(d3d9)} · ${Half.D3D11.title} from ${tag(d3d11)}" +
                if (overlay) " · a DLL its half lacks is borrowed from the other build" else ""

        fun ownerOf(half: Half): String = if (half == Half.D3D9) d3d9 else d3d11
    }

    /** [taken] maps a package-relative path to the build it comes from. */
    data class Plan(
        val taken: Map<String, String>,
        val borrowed: List<String>,
        val absent: List<String>,
        val foreign: List<String>,
    )

    fun plan(spec: Spec, contents: (String) -> Set<String>): Plan {
        val have = spec.sources.associateWith { contents(it) }
        val taken = LinkedHashMap<String, String>()
        val borrowed = mutableListOf<String>()
        val absent = mutableListOf<String>()
        val foreign = LinkedHashSet<String>()
        for (arch in ARCHES) {
            val prefix = "$DIR/$arch/"
            val dlls = have.values.flatMap { set ->
                set.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
            }.distinct().sorted()
            for (dll in dlls) {
                val half = Half.of(dll)
                if (half == null) {
                    foreign += dll
                    continue
                }
                val owner = spec.ownerOf(half)
                val other = spec.ownerOf(if (half == Half.D3D9) Half.D3D11 else Half.D3D9)
                val rel = prefix + dll
                when {
                    rel in have.getValue(owner) -> taken[rel] = owner
                    spec.overlay && rel in have.getValue(other) -> {
                        taken[rel] = other
                        borrowed += rel
                    }

                    else -> absent += rel
                }
            }
        }
        return Plan(taken, borrowed, absent, foreign.toList())
    }

    /** Mali: the fast 1.x half answers D3D9, the newest build the driver can create answers D3D11. */
    fun forDriver(caps: Caps, reversed: Boolean = false): Spec? {
        if (caps.block != null) return null
        val accurate = caps.newest()?.file ?: return null
        if (accurate == FAST) return null
        val spec = Spec(FAST, accurate)
        return if (reversed) spec.reversed else spec
    }

    /** "Dxvk-1.7.2-linux.wcp" reads as "1.7.2", "dxvk-gplasync-2.7.1-1-linux.wcp" as "gplasync-2.7.1-1". */
    internal fun tag(file: String): String {
        val base = file.removeSuffix(".wcp").removeSuffix("-linux")
        val cut = base.indexOf('-')
        return if (cut in 0 until base.lastIndex) base.substring(cut + 1) else base
    }
}

/**
 * Vulkan 1.3 with BC textures and nothing beyond: what the published non-Adreno builds are proven
 * to have. maintenance5 and later stay off, so the accurate half stops at 2.6.2.
 */
private fun guestCaps(robustness2: Boolean) = DxvkHybrid.Caps(
    apiMinor = 3, bc = true, robustness2 = robustness2,
    maintenance5 = false, maintenance6 = false, loadStoreOpNone = false,
)
