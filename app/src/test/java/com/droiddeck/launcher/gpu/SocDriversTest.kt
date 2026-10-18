package com.droiddeck.launcher.gpu

import com.droiddeck.launcher.session.DxvkHybrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocDriversTest {
    @Test fun midgardBifrostAndPowervrHaveNoPublishedDriver() {
        assertNull(SocDrivers.driverForGpu("Mali-T880 MP12", 880))
        assertNull(SocDrivers.driverForGpu("Mali-T760", 760))
        assertNull(SocDrivers.driverForGpu("Mali-G72 MP18", 72))
        assertNull(SocDrivers.driverForGpu("Mali-G71 MP2", 71))
        assertNull(SocDrivers.driverForGpu("Mali-G51 MP4", 51))
        assertNull(SocDrivers.driverForGpu("Mali-G31 MP2", 31))
        assertNull(SocDrivers.driverForGpu("PowerVR GE8320", 0))
        assertNull(SocDrivers.driverForGpu("Maleoon 910", 0))
        assertNull(SocDrivers.driverForGpu("Adreno 740", 740))
    }

    @Test fun jobManagerMaliGetsTheJobManagerBuild() {
        assertEquals("panvk-mali-jm", SocDrivers.driverForGpu("Mali-G52 MC2", 52))
        assertEquals("panvk-mali-jm", SocDrivers.driverForGpu("Mali-G76 MC4", 76))
    }

    @Test fun valhallAndNewerGetTheKbaseBuildExceptWhereAChipBuildExists() {
        for (gpu in listOf("Mali-G57 MC2" to 57, "Mali-G68 MC4" to 68, "Mali-G77 MC9" to 77,
            "Mali-G78 MP14" to 78, "Mali-G610 MC6" to 610, "Mali-G710 MC10" to 710,
            "Mali-G715 MC7" to 715, "Mali-G725 MC7" to 725, "Immortalis-G925 MC12" to 925)) {
            assertEquals(gpu.first, "panvk-mali-kbase", SocDrivers.driverForGpu(gpu.first, gpu.second))
        }
        assertEquals("panvk-mali-g615", SocDrivers.driverForGpu("Mali-G615 MC2", 615))
        assertEquals("panvk-mali-g720", SocDrivers.driverForGpu("Mali-G720 MC12", 720))
        assertEquals("panvk-mali-g720", SocDrivers.driverForGpu("Immortalis-G720 MC12", 720))
        // Arm calls the premium line Immortalis, and a driver picked by substring would miss it.
        assertEquals("panvk-mali-kbase", SocDrivers.driverForGpu("Immortalis-G715 MC11", 715))
        assertEquals("panvk-mali-kbase", SocDrivers.driverForGpu("Immortalis-G925 MC12", 925))
    }

    @Test fun anUnknownMaliIsRefusedRatherThanGuessed() {
        assertNull(SocDrivers.driverForGpu("Mali-G666", 666))
        assertNull(SocDrivers.driverForGpu("Immortalis-G666", 666))
        assertNull(SocDrivers.driverForGpu("", 0))
    }

    @Test fun aChipTheMaintainersValidatedBeatsTheArchitectureFallback() {
        // Helio G99 and Dimensity 700 are on the job-manager build even though their G57 would
        // otherwise fall back to the kbase build.
        assertEquals("panvk-mali-jm", SocDrivers.driver("Mali-G57 MC2", 57, "mt6789"))
        assertEquals("panvk-mali-jm", SocDrivers.driver("Mali-G57 MC2", 57, "mt6833"))
        assertEquals("panvk-mali-g615", SocDrivers.driver("Mali-G615 MC2", 615, "mt6878"))
        assertEquals("panvk-mali-g720", SocDrivers.driver("Immortalis-G720 MC12", 720, "mt6989"))
        assertEquals("radv-xclipse", SocDrivers.driver("Xclipse 920", 0, "exynos2200"))
        assertEquals("radv-xclipse", SocDrivers.driver("Xclipse 940", 0, "exynos2400"))
        // Dimensity 9000 is a G710, whose own profile is unvalidated, so it falls back to kbase.
        assertEquals("panvk-mali-kbase", SocDrivers.driver("Mali-G710 MC10", 710, "mt6983"))
    }

    @Test fun chipsFromEveryVendorAreNamedOldestToNewest() {
        assertEquals("MediaTek Helio G99/G100", SocDrivers.of("mt6789")?.label)
        assertEquals("MediaTek Dimensity 9300/9300+", SocDrivers.of("MT6989")?.label)
        assertEquals("MediaTek Kompanio 1300T", SocDrivers.of("mt8798")?.label)
        assertEquals("Samsung Exynos 9810", SocDrivers.of("universal9810")?.label)
        assertEquals("Google Tensor G3", SocDrivers.of("zuma")?.label)
        assertEquals("HiSilicon Kirin 9000", SocDrivers.of("kirin9000")?.label)
        assertEquals("Unisoc T820", SocDrivers.of("ums9620")?.label)
        assertEquals("Rockchip RK3588", SocDrivers.of("rk3588")?.label)
        assertNull(SocDrivers.of("sun"))   // Qualcomm belongs to SocNames and DriverPairs
        assertEquals("sun", SocDrivers.label("sun"))
        assertEquals("", SocDrivers.label(""))
    }

    @Test fun everyDriverInTheTableIsOneTheHybridHasAProfileFor() {
        val ids = buildSet {
            SocDrivers.SOCS_FOR_TEST.values.forEach { soc -> soc.driver?.let { add(it) } }
            (0..1000).forEach { model -> SocDrivers.driverForGpu("Mali-G$model", model)?.let { add(it) } }
            add(SocDrivers.driverForGpu("Xclipse 530", 0)!!)
        }
        assertTrue(ids.isNotEmpty())
        for (id in ids) {
            val guest = DxvkHybrid.Guest.of(id)
            assertTrue("$id has no capability profile", guest != null && guest.caps != null)
        }
    }
}
