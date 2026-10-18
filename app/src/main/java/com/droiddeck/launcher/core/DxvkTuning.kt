package com.droiddeck.launcher.core

import android.app.ActivityManager
import android.content.Context
import com.droiddeck.launcher.gpu.GpuInfo

/**
 * The DXVK options a Mali device starts from.
 *
 * Every key here is read by 1.7.2, 1.10.3, 2.7.1 and 3.1.1 alike, because a hybrid answers one half
 * with an old build and the other with a new one, and both read the same DXVK_CONFIG. Keys only some
 * versions know are left out rather than relied upon: `dxgi.tearFree` and `dxgi.numBackBuffers` stop
 * at 1.x, `dxvk.allowFse` starts at 2.7.1, and `d3d9.samplerLodBias` starts at 2.7.1.
 *
 * What is tuned is latency and stalls, not the picture: one queued frame per swapchain, fewer shader
 * compiler threads than cores, and an honest amount of "video" memory on a phone whose memory is
 * unified. Options that change what is drawn - `d3d11.maxTessFactor`, `d3d11.relaxedBarriers` - are
 * left to the per-game configuration, where a player can choose them for one game.
 *
 * These are defaults, and they are laid down first: a raw DXVK_CONFIG the player wrote and the
 * texture filtering choices in the UI both come after them, so both win.
 */
object DxvkTuning {
    /** The profile for [gpu], or nothing when this is not a GPU the profile is written for. */
    fun profile(gpu: GpuInfo, totalRamBytes: Long, cores: Int): String =
        if (gpu.mali) maliOptions(totalRamBytes, cores) else ""

    fun profile(context: Context): String =
        profile(GpuInfo.detect(), totalRamBytes(context), Runtime.getRuntime().availableProcessors())

    fun maliOptions(totalRamBytes: Long, cores: Int): String {
        val memory = videoMemoryMib(totalRamBytes)
        return listOf(
            // The phone's compositor already queues a frame of its own, and a second queued frame in
            // DXVK is what makes touch input feel like it is swimming behind the picture.
            "d3d9.maxFrameLatency = 1",
            "dxgi.maxFrameLatency = 1",
            // Compiling shaders on every core starves the game thread and heats the phone out of its
            // boost clock; both read as a stutter that has nothing to do with the GPU.
            "dxvk.numCompilerThreads = ${compilerThreads(cores)}",
            // Memory is unified, so "video memory" is a share of RAM. A generous guess lets one game
            // push the phone into swap, which arrives as a freeze and then as the kernel killing it.
            "dxgi.maxDeviceMemory = $memory",
            "dxgi.maxSharedMemory = $memory",
            "d3d9.maxAvailableMemory = $memory",
        ).joinToString("; ")
    }

    /** Two threads where there are cores to spare, one where there are not. */
    fun compilerThreads(cores: Int): Int = if (cores >= 4) 2 else 1

    /** A quarter of unified RAM, between 1 and 4 GiB: enough for textures, not enough to swap. */
    fun videoMemoryMib(totalRamBytes: Long): Int = (totalRamBytes / MIB / 4).toInt().coerceIn(1024, 4096)

    private fun totalRamBytes(context: Context): Long = ActivityManager.MemoryInfo().let { info ->
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        info.totalMem
    }

    private const val MIB = 1024L * 1024L
}
