package com.droiddeck.launcher.session

import android.content.Context
import com.droiddeck.launcher.core.Hashes
import com.droiddeck.launcher.gpu.GpuInfo
import com.droiddeck.launcher.runtime.LinuxRuntime
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DxvkHybridTest {
    private val modern = "dxvk-2.7.1-linux.wcp"
    private lateinit var context: Context

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "components").deleteRecursively()
        LinuxRuntime.rootDir(context).deleteRecursively()
    }

    @Test fun theTwoHalvesAreTheOnesTheImportGraphKeepsApart() {
        assertEquals(DxvkHybrid.Half.D3D9, DxvkHybrid.Half.of("d3d9.dll"))
        assertEquals(DxvkHybrid.Half.D3D9, DxvkHybrid.Half.of("D3D8.DLL"))
        assertEquals(DxvkHybrid.Half.D3D11, DxvkHybrid.Half.of("dxgi.dll"))
        assertEquals(DxvkHybrid.Half.D3D11, DxvkHybrid.Half.of("d3d11.dll"))
        assertEquals(DxvkHybrid.Half.D3D11, DxvkHybrid.Half.of("d3d10core.dll"))
        assertEquals(DxvkHybrid.Half.D3D11, DxvkHybrid.Half.of("d3d10_1.dll"))
        assertNull(DxvkHybrid.Half.of("d3d12.dll"))
        assertNull(DxvkHybrid.Half.of("ntdll.dll"))
        assertEquals(
            listOf("d3d9.dll", "d3d8.dll", "dxgi.dll", "d3d11.dll", "d3d10core.dll", "d3d10.dll", "d3d10_1.dll").sorted(),
            DxvkHybrid.Half.entries.flatMap { it.dlls }.sorted(),
        )
    }

    @Test fun aBuildIsNamedByItsVersionAndFlavour() {
        assertEquals("1.7.2", DxvkHybrid.tag(DxvkHybrid.FAST))
        assertEquals("2.7.1", DxvkHybrid.tag(modern))
        assertEquals("gplasync-2.7.1-1", DxvkHybrid.tag("dxvk-gplasync-2.7.1-1-linux.wcp"))
        assertEquals("2.4.1-pre-reg", DxvkHybrid.tag("dxvk-2.4.1-pre-reg-linux.wcp"))
        assertEquals("dxvk", DxvkHybrid.tag("dxvk.wcp"))
    }

    @Test fun aSpecNamesItsFileAfterBothHalvesAndFlipsInOnePiece() {
        val spec = DxvkHybrid.Spec(DxvkHybrid.FAST, modern)
        assertEquals("dxvk-hybrid-d3d9-1.7.2+d3d11-2.7.1.wcp", spec.file)
        assertEquals("1.7.2 D3D8/D3D9 + 2.7.1 DXGI/D3D10/D3D11", spec.version)
        assertEquals(DxvkHybrid.Spec(modern, DxvkHybrid.FAST), spec.reversed)
        assertEquals("dxvk-hybrid-d3d9-2.7.1+d3d11-1.7.2.wcp", spec.reversed.file)
        assertEquals(spec, spec.reversed.reversed)
        assertEquals("dxvk-hybrid-d3d9-1.7.2+d3d11-2.7.1+overlay.wcp", spec.copy(overlay = true).file)
        assertThrows(IllegalArgumentException::class.java) { DxvkHybrid.Spec(modern, modern) }
    }

    @Test fun maliVendorDriverWithoutBcRunsNoBuildAtAll() {
        val caps = DxvkHybrid.Caps.of(maliVendorReport)
        assertEquals(DxvkHybrid.Caps.Block.NO_BC, caps.block)
        assertNull(caps.newest())
        assertNull(DxvkHybrid.forDriver(caps))
    }

    @Test fun theNewestBuildTheDriverCanCreateIsTheOnePicked() {
        assertEquals("dxvk-1.10.3-linux.wcp", DxvkHybrid.Caps.of(panvkOld).newest()?.file)
        assertEquals("dxvk-2.6.2-linux.wcp", DxvkHybrid.Caps.of(panvkRobust).newest()?.file)
        assertEquals(modern, DxvkHybrid.Caps.of(panvkMaintenance5).newest()?.file)
        assertEquals("dxvk-3.1.1-linux.wcp", DxvkHybrid.Caps.of(panvkFull).newest()?.file)
        assertNull(DxvkHybrid.Caps.of(emptyMap()).newest())
    }

    @Test fun aDriverWithoutBcOrApiVersionSaysWhy() {
        assertEquals(DxvkHybrid.Caps.Block.NO_API, DxvkHybrid.Caps.of(emptyMap()).block)
        assertEquals(DxvkHybrid.Caps.Block.NO_BC, DxvkHybrid.Caps.of(panvkFull + ("texture_bc" to "no")).block)
        assertNull(DxvkHybrid.Caps.of(panvkFull).block)
    }

    @Test fun maliGetsTheFastHalfOnD3D9AndTheAccurateHalfOnD3D11() {
        val spec = DxvkHybrid.forDriver(DxvkHybrid.Caps.of(panvkMaintenance5))!!
        assertEquals(DxvkHybrid.FAST, spec.d3d9)
        assertEquals(modern, spec.d3d11)
        assertEquals(spec.reversed, DxvkHybrid.forDriver(DxvkHybrid.Caps.of(panvkMaintenance5), reversed = true))
        assertEquals(modern, DxvkHybrid.forDriver(DxvkHybrid.Caps.of(panvkMaintenance5), reversed = true)!!.d3d9)
    }

    @Test fun aWeakDriverStillGetsAHybridOfTwoLegacyBuilds() {
        val spec = DxvkHybrid.forDriver(DxvkHybrid.Caps.of(panvkOld))!!
        assertEquals(DxvkHybrid.FAST, spec.d3d9)
        assertEquals("dxvk-1.10.3-linux.wcp", spec.d3d11)
    }

    @Test fun thePlanTakesEachHalfWholeAndLeavesWhatItsBuildHasNotToWine() {
        val spec = DxvkHybrid.Spec(DxvkHybrid.FAST, modern)
        val plan = DxvkHybrid.plan(spec) { contents(it) }
        for (arch in DxvkHybrid.ARCHES) {
            assertEquals(DxvkHybrid.FAST, plan.taken.getValue("files/lib/wine/dxvk/$arch/d3d9.dll"))
            for (dll in listOf("dxgi.dll", "d3d11.dll", "d3d10core.dll"))
                assertEquals(modern, plan.taken.getValue("files/lib/wine/dxvk/$arch/$dll"))
        }
        assertEquals(8, plan.taken.size)
        assertTrue(plan.borrowed.isEmpty())
        assertEquals(
            DxvkHybrid.ARCHES.flatMap { arch -> listOf("d3d8.dll", "d3d10.dll", "d3d10_1.dll").map { "files/lib/wine/dxvk/$arch/$it" } }.sorted(),
            plan.absent.sorted(),
        )
        assertTrue(plan.foreign.isEmpty())
    }

    @Test fun theFlippedPlanMovesTheOtherHalf() {
        val plan = DxvkHybrid.plan(DxvkHybrid.Spec(DxvkHybrid.FAST, modern).reversed) { contents(it) }
        assertEquals(modern, plan.taken.getValue("files/lib/wine/dxvk/aarch64-windows/d3d9.dll"))
        assertEquals(modern, plan.taken.getValue("files/lib/wine/dxvk/aarch64-windows/d3d8.dll"))
        assertEquals(DxvkHybrid.FAST, plan.taken.getValue("files/lib/wine/dxvk/aarch64-windows/d3d11.dll"))
        assertEquals(DxvkHybrid.FAST, plan.taken.getValue("files/lib/wine/dxvk/i386-windows/dxgi.dll"))
        assertEquals(14, plan.taken.size)
    }

    @Test fun anOverlayBorrowsOnlyWhatItsOwnHalfLacks() {
        val plan = DxvkHybrid.plan(DxvkHybrid.Spec(DxvkHybrid.FAST, modern, overlay = true)) { contents(it) }
        assertEquals(14, plan.taken.size)
        assertEquals(modern, plan.taken.getValue("files/lib/wine/dxvk/aarch64-windows/d3d8.dll"))
        assertEquals(
            DxvkHybrid.ARCHES.flatMap { arch -> listOf("d3d8.dll", "d3d10.dll", "d3d10_1.dll").map { "files/lib/wine/dxvk/$arch/$it" } }.sorted(),
            plan.borrowed.sorted(),
        )
        assertTrue(plan.absent.isEmpty())
    }

    @Test fun aDllOutsideBothHalvesIsRefusedRatherThanDropped() {
        val plan = DxvkHybrid.plan(DxvkHybrid.Spec(DxvkHybrid.FAST, modern)) {
            if (it == modern) contents(it) + "files/lib/wine/dxvk/aarch64-windows/d3d12.dll" else contents(it)
        }
        assertEquals(listOf("d3d12.dll"), plan.foreign)
    }

    @Test fun aBuiltHybridCarriesTheBytesOfTheBuildThatOwnsEachHalf() {
        dxvkPackage(DxvkHybrid.FAST, "1.7.2", legacy = true)
        dxvkPackage(modern, "2.7.1", legacy = false)
        val pkg = ComponentsManager.hybrid(context, DxvkHybrid.Spec(DxvkHybrid.FAST, modern))

        assertEquals("dxvk", pkg.comp)
        assertEquals("1.7.2 D3D8/D3D9 + 2.7.1 DXGI/D3D10/D3D11", pkg.version)
        assertTrue(pkg.file.startsWith("dxvk-hybrid-"))
        val unpacked = unpack(File(packages(), pkg.file))
        assertEquals("d3d9 of 1.7.2", unpacked.getValue("files/lib/wine/dxvk/aarch64-windows/d3d9.dll"))
        assertEquals("d3d9 of 1.7.2", unpacked.getValue("files/lib/wine/dxvk/i386-windows/d3d9.dll"))
        assertEquals("d3d11 of 2.7.1", unpacked.getValue("files/lib/wine/dxvk/aarch64-windows/d3d11.dll"))
        assertEquals("dxgi of 2.7.1", unpacked.getValue("files/lib/wine/dxvk/aarch64-windows/dxgi.dll"))
        assertEquals("d3d10core of 2.7.1", unpacked.getValue("files/lib/wine/dxvk/i386-windows/d3d10core.dll"))
        assertFalse(unpacked.keys.any { it.endsWith("d3d8.dll") || it.endsWith("d3d10.dll") || it.endsWith("d3d10_1.dll") })
        assertEquals(8, unpacked.size)
    }

    @Test fun aHybridIsBuiltOnceAndRebuiltOnDemand() {
        dxvkPackage(DxvkHybrid.FAST, "1.7.2", legacy = true)
        dxvkPackage(modern, "2.7.1", legacy = false)
        val spec = DxvkHybrid.Spec(DxvkHybrid.FAST, modern)
        val first = ComponentsManager.hybrid(context, spec)
        val file = File(packages(), first.file)
        file.setLastModified(1_000_000L)
        assertEquals(1_000_000L, ComponentsManager.hybrid(context, spec).let { File(packages(), it.file).lastModified() })
        assertTrue(ComponentsManager.hybrid(context, spec, rebuild = true).let { File(packages(), it.file).lastModified() } > 1_000_000L)
        assertEquals(first.version, ComponentsManager.hybrid(context, spec).version)
    }

    @Test fun bothDirectionsLiveSideBySideAsStoredPackages() {
        dxvkPackage(DxvkHybrid.FAST, "1.7.2", legacy = true)
        dxvkPackage(modern, "2.7.1", legacy = false)
        val spec = DxvkHybrid.Spec(DxvkHybrid.FAST, modern)
        val one = ComponentsManager.hybrid(context, spec)
        val other = ComponentsManager.hybrid(context, spec.reversed)
        assertNotEquals(one.file, other.file)
        assertTrue(ComponentsManager.packages(context).map { it.file }.containsAll(listOf(one.file, other.file)))
        assertEquals("d3d11 of 1.7.2", unpack(File(packages(), other.file)).getValue("files/lib/wine/dxvk/aarch64-windows/d3d11.dll"))
    }

    @Test fun aHybridSwapsIntoAProtonLikeAnyOtherPackageAndPublishesItsFiles() {
        dxvkPackage(DxvkHybrid.FAST, "1.7.2", legacy = true)
        dxvkPackage(modern, "2.7.1", legacy = false)
        proton("proton-exp")
        val pkg = ComponentsManager.hybrid(context, DxvkHybrid.Spec(DxvkHybrid.FAST, modern))

        ComponentsManager.swap(context, "proton-exp", pkg.file)

        val dir = File(LinuxRuntime.rootDir(context), "root/.local/share/Steam/steamapps/common/proton-exp")
        assertEquals("d3d9 of 1.7.2", File(dir, "files/lib/wine/dxvk/aarch64-windows/d3d9.dll").readText())
        assertEquals("d3d11 of 2.7.1", File(dir, "files/lib/wine/dxvk/aarch64-windows/d3d11.dll").readText())
        assertFalse(File(dir, "files/lib/wine/dxvk/aarch64-windows/d3d10.dll").exists())
        val rows = launchState().readLines().map { it.split('\t') }.filter { it[3] == "dxvk" }
        val d3d9 = rows.single { it[4].endsWith("/aarch64-windows/d3d9.dll") }
        val gone = rows.single { it[4].endsWith("/aarch64-windows/d3d10.dll") }
        assertEquals("d3d9 of 1.7.2", File(LinuxRuntime.rootDir(context), d3d9[6].removePrefix("/")).readText())
        assertEquals(Hashes.sha256(File(dir, "files/lib/wine/dxvk/aarch64-windows/d3d9.dll")), d3d9[5])
        assertEquals("-", gone[5])
        assertEquals("-", gone[6])
    }

    @Test fun aHybridCanFollowOneGameWithoutTouchingTheOthers() {
        dxvkPackage(DxvkHybrid.FAST, "1.7.2", legacy = true)
        dxvkPackage(modern, "2.7.1", legacy = false)
        proton("proton-exp")
        val pkg = ComponentsManager.hybrid(context, DxvkHybrid.Spec(DxvkHybrid.FAST, modern))

        ComponentsManager.setGameComponent(context, "42", "dxvk", pkg.file)

        assertEquals(pkg.file, ComponentsManager.gameComponents(context, "42")["dxvk"])
        val rows = launchState().readLines().map { it.split('\t') }
        assertTrue(rows.any { it[0] == "42" && it[4].endsWith("/aarch64-windows/d3d9.dll") && it[6] != "-" })
        assertTrue(rows.any { it[0] == "*" && it[3] == "dxvk" })
    }

    @Test fun maliPicksTheHybridUpWhileAnotherGpuIsLeftAlone() {
        dxvkPackage(DxvkHybrid.FAST, "1.7.2", legacy = true)
        dxvkPackage(modern, "2.7.1", legacy = false)
        proton("proton-exp")
        val applied = mutableListOf<String>()
        val spec = DxvkHybrid.Spec(DxvkHybrid.FAST, modern)

        val result = ComponentsManager.ensureMaliHybrid(
            context, mali(720), DxvkHybrid.Caps.of(panvkMaintenance5),
            { assertEquals(spec, it); ComponentsManager.hybrid(context, it) },
            { id, file -> applied += "$id $file"; "hybrid in $id" },
        )

        assertEquals("hybrid in proton-exp", result)
        assertEquals(listOf("proton-exp ${spec.file}"), applied)
    }

    @Test fun aGpuThatIsNotMaliAndADriverWithoutBcAreBothLeftAsTheyAre() {
        proton("proton-exp")
        val caps = DxvkHybrid.Caps.of(panvkMaintenance5)
        assertNull(ComponentsManager.ensureMaliHybrid(context, adreno(740), caps, { error("built") }, { _, _ -> error("applied") }))
        assertEquals(
            "DXVK hybrid skipped: the driver has no BC textures, which every DXVK build requires",
            ComponentsManager.ensureMaliHybrid(context, mali(720), DxvkHybrid.Caps.of(maliVendorReport), { error("built") }, { _, _ -> error("applied") }),
        )
    }

    @Test fun aHybridNobodyAskedForDoesNotReplaceAChosenPackage() {
        dxvkPackage(DxvkHybrid.FAST, "1.7.2", legacy = true)
        dxvkPackage(modern, "2.7.1", legacy = false)
        proton("proton-exp")
        ComponentsManager.swap(context, "proton-exp", modern)

        assertNull(ComponentsManager.ensureMaliHybrid(
            context, mali(720), DxvkHybrid.Caps.of(panvkMaintenance5),
            { error("built ${it.file}") }, { _, _ -> error("applied") },
        ))
    }

    @Test fun aDeviceWithoutAVulkanReportIsRefusedRatherThanGuessed() {
        val failure = assertThrows(IllegalStateException::class.java) { ComponentsManager.hybridForDevice(context, false) }
        assertTrue(failure.message, failure.message!!.isNotEmpty())
    }

    @Test fun aPackageWithAnUnknownDllIsNotTurnedIntoAHybrid() {
        dxvkPackage(DxvkHybrid.FAST, "1.7.2", legacy = true, extra = "d3d12.dll")
        dxvkPackage(modern, "2.7.1", legacy = false)

        val failure = assertThrows(IllegalStateException::class.java) {
            ComponentsManager.hybrid(context, DxvkHybrid.Spec(DxvkHybrid.FAST, modern))
        }

        assertTrue(failure.message, failure.message!!.contains("d3d12.dll"))
        assertFalse(File(packages(), DxvkHybrid.Spec(DxvkHybrid.FAST, modern).file).isFile)
    }

    private val maliVendorReport = mapOf(
        "device" to "Mali-G720-Immortalis MC12", "api" to "1.3.247", "texture_bc" to "no",
        "ext.VK_EXT_robustness2" to "no", "ext.VK_KHR_maintenance5" to "no",
        "ext.VK_KHR_maintenance6" to "no", "ext.VK_KHR_load_store_op_none" to "no",
    )

    private val panvkOld = mapOf(
        "api" to "1.2.0", "texture_bc" to "yes", "ext.VK_EXT_robustness2" to "no",
        "ext.VK_KHR_maintenance5" to "no", "ext.VK_KHR_maintenance6" to "no", "ext.VK_KHR_load_store_op_none" to "no",
    )

    private val panvkRobust = panvkOld + ("api" to "1.3.0") + ("ext.VK_EXT_robustness2" to "yes")

    private val panvkMaintenance5 = panvkRobust + ("ext.VK_KHR_maintenance5" to "yes")

    private val panvkFull = panvkMaintenance5 +
        ("ext.VK_KHR_maintenance6" to "yes") + ("ext.VK_KHR_load_store_op_none" to "yes")

    private fun mali(model: Int) = GpuInfo("Mali-G$model", model, GpuInfo.maliFamilyOf(model), "", false)

    private fun adreno(model: Int) = GpuInfo("Adreno $model", model, GpuInfo.familyOf(true, model), "", false)

    private fun packages() = File(context.filesDir, "components/packages").apply { mkdirs() }

    private fun launchState() = File(LinuxRuntime.rootDir(context), "root/.local/share/droiddeck-components/desired.tsv")

    /** The DLL set each build ships: 1.x still has d3d10 and d3d10_1 and has no d3d8, 2.x is the other way round. */
    private fun contents(file: String): Set<String> =
        if (file == DxvkHybrid.FAST) legacyDlls else modernDlls

    private val legacyDlls = DxvkHybrid.ARCHES.flatMap { arch ->
        listOf("d3d9.dll", "dxgi.dll", "d3d11.dll", "d3d10core.dll", "d3d10.dll", "d3d10_1.dll").map { "files/lib/wine/dxvk/$arch/$it" }
    }.toSet()

    private val modernDlls = DxvkHybrid.ARCHES.flatMap { arch ->
        listOf("d3d9.dll", "d3d8.dll", "dxgi.dll", "d3d11.dll", "d3d10core.dll").map { "files/lib/wine/dxvk/$arch/$it" }
    }.toSet()

    private fun dxvkPackage(file: String, version: String, legacy: Boolean, extra: String? = null) {
        val dlls = (if (legacy) legacyDlls else modernDlls) +
            (extra?.let { DxvkHybrid.ARCHES.map { arch -> "files/lib/wine/dxvk/$arch/$it" } } ?: emptyList())
        val rels = dlls.sorted()
        val profile = JSONObject()
            .put("type", "DXVK-Linux")
            .put("versionName", "$version-linux")
            .put("versionCode", 0)
            .put("description", "test $version")
            .put("files", JSONArray(rels.map { JSONObject().put("source", it).put("target", "\${proton}/$it") }))
        val dest = File(packages(), file)
        TarArchiveOutputStream(ZstdOutputStream(dest.outputStream().buffered(), 9)).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            val bytes = profile.toString(2).toByteArray()
            tar.putArchiveEntry(TarArchiveEntry("./profile.json").apply { size = bytes.size.toLong() })
            tar.write(bytes)
            tar.closeArchiveEntry()
            for (rel in rels) {
                val name = rel.substringAfterLast('/')
                val text = "${name.removeSuffix(".dll")} of $version"
                tar.putArchiveEntry(TarArchiveEntry("./$rel").apply { size = text.length.toLong() })
                tar.write(text.toByteArray())
                tar.closeArchiveEntry()
            }
        }
    }

    private fun unpack(wcp: File): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        TarArchiveInputStream(ZstdInputStream(BufferedInputStream(FileInputStream(wcp)))).use { tar ->
            while (true) {
                val e = tar.nextTarEntry ?: break
                if (e.isDirectory) continue
                val rel = e.name.removePrefix("./")
                if (rel.startsWith("files/")) out[rel] = String(tar.readBytes())
            }
        }
        return out
    }

    private fun proton(name: String, version: String = "experimental-11") {
        val dir = File(LinuxRuntime.rootDir(context), "root/.local/share/Steam/steamapps/common/$name")
        File(dir, "files/lib/wine").mkdirs()
        File(dir, "proton").writeText("#!/bin/sh\n")
        File(dir, "version").writeText("1 $version\n")
        for (rel in legacyDlls) File(dir, rel).apply { parentFile!!.mkdirs(); writeText("bundled ${rel.substringAfterLast('/')}") }
    }
}
