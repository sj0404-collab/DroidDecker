package com.droiddeck.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LinuxRuntimeProfileCatalogTest {
    private val valid = """{"version":"r1","url":"https://github.com/sj0404-collab/DroidDecker/releases/download/linuxfs-lite-r1/linuxfs.tar.zst","sha256":"${"a".repeat(64)}","size":605348116}"""

    @Test
    fun liteVersionIsNamespacedSoSwitchingProfilesTriggersInstall() {
        val release = LinuxRuntimeInstaller.parseReleaseJson(valid, LinuxRuntimeInstaller.PROFILE_LITE)
        assertEquals("lite-r1", release.version)
        assertEquals(605348116L, release.size)
    }

    @Test
    fun fullVersionKeepsItsUpstreamName() {
        val full = valid.replace("r1", "r9")
        val release = LinuxRuntimeInstaller.parseReleaseJson(full, LinuxRuntimeInstaller.PROFILE_FULL)
        assertEquals("r9", release.version)
    }

    @Test
    fun catalogRejectsUntrustedUrlsAndMalformedDigests() {
        val badUrl = valid.replace("https://github.com/sj0404-collab/DroidDecker", "https://example.invalid")
        val badHash = valid.replace("a".repeat(64), "not-a-sha256")
        for (body in listOf(badUrl, badHash)) {
            try {
                LinuxRuntimeInstaller.parseReleaseJson(body, LinuxRuntimeInstaller.PROFILE_LITE)
                fail("invalid catalog entry should be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
