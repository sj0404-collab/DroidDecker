package com.droiddeck.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class GuestIcdTest {
    private val manifests = arrayOf(
        File("freedreno_icd.json"), File("panfrost_icd.aarch64.json"), File("radv_icd.aarch64.json"),
    )

    @Test fun maliPicksPanvkAndNeverTurnip() {
        assertEquals("panfrost_icd.aarch64.json", LinuxRuntime.pickIcd(manifests, "mali")?.name)
        assertEquals("panfrost_icd.aarch64.json", LinuxRuntime.pickIcd(arrayOf(File("freedreno_icd.json"), File("panfrost_icd.aarch64.json")), "mali")?.name)
    }

    @Test fun adrenoKeepsTurnip() {
        assertEquals("freedreno_icd.json", LinuxRuntime.pickIcd(manifests, "")?.name)
    }

    @Test fun xclipsePicksRadvThenVenus() {
        assertEquals("radv_icd.aarch64.json", LinuxRuntime.pickIcd(manifests, "xclipse")?.name)
        assertEquals("venus_icd.json", LinuxRuntime.pickIcd(arrayOf(File("freedreno_icd.json"), File("venus_icd.json")), "xclipse")?.name)
    }

    @Test fun maliFallsBackToVenusWhenNoPanvkIsInstalled() {
        assertEquals("venus_icd.json", LinuxRuntime.pickIcd(arrayOf(File("freedreno_icd.json"), File("venus_icd.json")), "mali")?.name)
    }

    @Test fun aDriverNobodyNamedFallsBackToTheFirstManifest() {
        assertEquals("freedreno_icd.json", LinuxRuntime.pickIcd(manifests, "powervr")?.name)
    }

    @Test fun noManifestsMeansNoIcd() {
        assertNull(LinuxRuntime.pickIcd(emptyArray(), "mali"))
        assertNull(LinuxRuntime.pickIcd(null, ""))
    }
}
