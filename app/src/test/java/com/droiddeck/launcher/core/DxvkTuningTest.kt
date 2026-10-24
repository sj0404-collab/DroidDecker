package com.droiddeck.launcher.core

import com.droiddeck.launcher.gpu.GpuInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DxvkTuningTest {
    private val eightGib = 8L shl 30

    private fun mali(model: Int) = GpuInfo("Mali-G$model", model, GpuInfo.maliFamilyOf(model), "", false)
    private val adreno = GpuInfo("Adreno 740", 740, GpuInfo.familyOf(true, 740), "", false)

    @Test fun everyOptionIsOneEveryDxvkBuildInTheHybridReads() {
        val keys = DxvkTuning.maliOptions(eightGib, 8).split("; ").map { it.substringBefore(" = ") }
        assertEquals(
            listOf(
                "d3d9.maxFrameLatency", "dxgi.maxFrameLatency", "dxvk.numCompilerThreads",
                "dxgi.maxDeviceMemory", "dxgi.maxSharedMemory", "d3d9.maxAvailableMemory",
            ),
            keys,
        )
    }

    @Test fun bothHalvesQueueOneFrameSoTouchDoesNotSwim() {
        val options = DxvkTuning.maliOptions(eightGib, 8)
        assertTrue(options.contains("d3d9.maxFrameLatency = 1"))
        assertTrue(options.contains("dxgi.maxFrameLatency = 1"))
    }

    @Test fun compilerThreadsLeaveTheGameThreadCoresToRunOn() {
        assertEquals(1, DxvkTuning.compilerThreads(0))
        assertEquals(1, DxvkTuning.compilerThreads(2))
        assertEquals(2, DxvkTuning.compilerThreads(4))
        assertEquals(2, DxvkTuning.compilerThreads(8))
    }

    @Test fun videoMemoryIsAQuarterOfUnifiedRamWithinSaneBounds() {
        assertEquals(2048, DxvkTuning.videoMemoryMib(eightGib))
        assertEquals(1024, DxvkTuning.videoMemoryMib(4L shl 30))
        assertEquals(4096, DxvkTuning.videoMemoryMib(16L shl 30))
        assertEquals(1024, DxvkTuning.videoMemoryMib(1L shl 30))
        assertEquals(2048, DxvkTuning.maliOptions(eightGib, 8).let { it.substringAfter("dxgi.maxDeviceMemory = ").substringBefore(";").toInt() })
    }

    @Test fun onlyMaliGetsTheProfile() {
        assertEquals("", DxvkTuning.profile(adreno, eightGib, 8))
        assertTrue(DxvkTuning.profile(mali(720), eightGib, 8).startsWith("d3d9.maxFrameLatency = 1"))
        assertTrue(DxvkTuning.profile(mali(57), eightGib, 8).isNotEmpty())
    }

    @Test fun nothingThatChangesThePictureIsSetByDefault() {
        val options = DxvkTuning.maliOptions(eightGib, 8)
        for (key in listOf("maxTessFactor", "relaxedBarriers", "tearFree", "samplerLodBias", "allowFse", "numBackBuffers", "hud")) {
            assertFalse(key, options.contains(key))
        }
    }
}
