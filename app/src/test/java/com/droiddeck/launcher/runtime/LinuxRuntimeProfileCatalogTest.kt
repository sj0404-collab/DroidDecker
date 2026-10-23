package com.droiddeck.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LinuxRuntimeProfileCatalogTest {
    private val url = "https://github.com/sj0404-collab/DroidDecker/releases/download/linuxfs-lite-r1/linuxfs.tar.zst"
    private val sha256 = "a".repeat(64)

    @Test
    fun liteVersionIsNamespacedSoSwitchingProfilesTriggersInstall() {
        val release = LinuxRuntimeInstaller.releaseFromCatalogFields(
            "r1", url, sha256, 605348116, LinuxRuntimeInstaller.PROFILE_LITE,
        )
        assertEquals("lite-r1", release.version)
        assertEquals(605348116L, release.size)
    }

    @Test
    fun fullVersionKeepsItsUpstreamName() {
        val release = LinuxRuntimeInstaller.releaseFromCatalogFields(
            "r9", url, sha256, 790907606, LinuxRuntimeInstaller.PROFILE_FULL,
        )
        assertEquals("r9", release.version)
    }

    @Test
    fun catalogRejectsUntrustedUrlsAndMalformedDigests() {
        val invalidEntries = listOf(
            Triple("r1", "https://example.invalid/runtime.tar.zst", sha256),
            Triple("r1", url, "not-a-sha256"),
            Triple("../r1", url, sha256),
        )
        for ((version, downloadUrl, digest) in invalidEntries) {
            try {
                LinuxRuntimeInstaller.releaseFromCatalogFields(
                    version, downloadUrl, digest, 1, LinuxRuntimeInstaller.PROFILE_LITE,
                )
                fail("invalid catalog entry should be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
