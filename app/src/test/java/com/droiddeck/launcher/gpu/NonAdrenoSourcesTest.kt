package com.droiddeck.launcher.gpu

import com.droiddeck.launcher.gpu.GpuInfo.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NonAdrenoSourcesTest {
    private val allMali = setOf(Family.MALI_5TH, Family.MALI_VALHALL, Family.MALI_BIFROST, Family.MALI_UNKNOWN)

    private fun mali(model: Int) = GpuInfo("Mali-G$model", model, GpuInfo.maliFamilyOf(model), "", false)
    private val adreno = GpuInfo("Adreno 740", 740, Family.A7XX, "", false)

    private fun pairAsset(key: String, name: String = "$key-Pair.zip") = TurnipReleases.Asset(
        "Non-Adreno", key, name, "https://example.com/releases/download/$key/$name", 1L, false, "Mali", "sha256:0", key, true,
    )

    private fun bundlePair(key: String, families: Set<Family> = allMali) =
        DriverPairs.DriverPair(key, key, families, null, null, pairAsset(key))

    @Test fun theNonAdrenoSourceReadsTheDriverIdFromTheReleaseTag() {
        val pair = TurnipReleases.kindOf("Non-Adreno", "PanVK-Mali-kbase-Pair.zip", "panvk-mali-kbase")!!
        assertEquals("panvk-mali-kbase", pair.pair)
        assertTrue(pair.bundle)
        assertFalse(pair.linux)

        val linux = TurnipReleases.kindOf("Non-Adreno", "PanVK-Mali-kbase-Linux.zip", "panvk-mali-kbase")!!
        assertTrue(linux.linux)
        assertFalse(linux.bundle)

        val android = TurnipReleases.kindOf("Non-Adreno", "PanVK-Mali-G615-Android.zip", "panvk-mali-g615")!!
        assertFalse(android.linux)
        assertFalse(android.bundle)
        assertEquals("panvk-mali-g615", android.pair)
        assertEquals("Mali G615 · PanVK", android.label)

        val radv = TurnipReleases.kindOf("Non-Adreno", "RADV-Xclipse-upstream-Pair.zip", "radv-xclipse-upstream")!!
        assertTrue(radv.bundle)
        assertEquals("radv-xclipse-upstream", radv.pair)
    }

    @Test fun componentsThatAreNotDriverPairsAreLeftAlone() {
        assertNull(TurnipReleases.kindOf("Non-Adreno", "venus.tzst", "venus-proto13-a4d47cf3"))
        assertNull(TurnipReleases.kindOf("Non-Adreno", "venus.tzst.sha256", "venus-proto13-a4d47cf3"))
        assertNull(TurnipReleases.kindOf("Non-Adreno", "gamescope.tzst", "gamescope-3.16.29-p21-nonadreno"))
        assertNull(TurnipReleases.kindOf("Non-Adreno", "PanVK-Mali-kbase-Pair.zip", "not-a-driver"))
        assertNull(TurnipReleases.kindOf("Non-Adreno", "PanVK-Mali-kbase-Wayland.zip", "panvk-mali-kbase"))
    }

    @Test fun theTurnipSourcesStillClassifyAsTheyDid() {
        val banner = TurnipReleases.kindOf("Banners-Turnip", "Turnip-v27.0.0-20261018-Linux.zip", "v27.0.0-20261018")!!
        assertTrue(banner.linux)
        assertEquals("banner", banner.pair)
        assertEquals("Adreno 6xx/7xx", banner.label)
        assertNull(TurnipReleases.kindOf("Banners-Turnip", "Turnip-v27.0.0-20261018-Wayland.zip", "v27.0.0-20261018"))

        val dd = TurnipReleases.kindOf("DroidDeck", "DD-Turnip-v1.2.3.zip", "DD-Turnip-v1.2.3")!!
        assertTrue(dd.bundle)
        assertEquals(DriverPairs.ddTurnip("DD-Turnip-v1.2.3"), dd.pair)
    }

    @Test fun aMaliDeviceIsPointedAtTheDriverItsChipNeedsOnceOneIsListed() {
        val pairs = listOf(bundlePair("panvk-mali-kbase"), bundlePair("panvk-mali-g720"))
        assertEquals("panvk-mali-g720", DriverPairs.recommendedKey(mali(720), pairs))
        assertEquals("panvk-mali-kbase", DriverPairs.recommendedKey(mali(57), pairs))
        assertEquals("panvk-mali-kbase", DriverPairs.recommendedKey(mali(610), pairs))
        // A G52 needs the job-manager build, and that one is not in the list.
        assertNull(DriverPairs.recommendedKey(mali(52), pairs))
    }

    @Test fun nothingIsRecommendedWhileNoPairHasBeenCalledDownloaded() {
        assertNull(DriverPairs.recommendedKey(mali(720), emptyList()))
        assertNull(DriverPairs.recommendedKey(mali(615), emptyList()))
        // Midgard has no published driver at all, so a listed pair does not make it recommended.
        assertNull(DriverPairs.recommendedKey(GpuInfo("Mali-T880", 880, Family.MALI_UNKNOWN, "", false), listOf(bundlePair("panvk-mali-kbase"))))
    }

    @Test fun xclipseGetsRadvAndAnotherNonAdrenoGpuGetsNothing() {
        val radv = bundlePair("radv-xclipse", setOf(Family.NOT_ADRENO))
        val xclipse = GpuInfo("Samsung Xclipse 940", 0, Family.NOT_ADRENO, "", false)
        assertEquals("radv-xclipse", DriverPairs.recommendedKey(xclipse, listOf(radv)))
        assertNull(DriverPairs.recommendedKey(xclipse, emptyList()))
        assertNull(DriverPairs.recommendedKey(GpuInfo("PowerVR GE8320", 0, Family.NOT_ADRENO, "", false), listOf(radv)))
    }

    @Test fun aMaliPairIsCompleteAndSuitsAMaliGpuOnly() {
        val pair = bundlePair("panvk-mali-kbase")
        assertTrue(pair.complete)
        assertTrue(pair.suits(mali(720)))
        assertTrue(pair.suits(mali(52)))
        assertFalse(pair.suits(adreno))
        assertFalse(bundlePair("radv-xclipse", setOf(Family.NOT_ADRENO)).suits(mali(720)))
    }
}
