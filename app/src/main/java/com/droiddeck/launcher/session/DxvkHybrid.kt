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

    /** What the phone's own Vulkan driver reports, in the keys VulkanInfo prints. */
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

            private fun yes(value: String?) = value.equals("yes", ignoreCase = true)
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
