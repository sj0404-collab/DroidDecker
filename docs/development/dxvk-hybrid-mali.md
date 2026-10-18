# Hybrid DXVK (two builds, one install)

A DXVK install can be built from **two** downloaded DXVK packages at once: one build answers
D3D8/D3D9, the other answers DXGI/D3D10/D3D11. Each half is taken whole, so nothing inside a half
is mixed. Both directions are offered, and the hybrid is an ordinary stored package: it swaps into a
Proton, follows one game's profile, and restores to the Proton's original like any other package.

Mali is the reason this exists. See [Mali](#mali) for what a Mali device can and cannot do.

## Why exactly these two halves

Read off the shipped DLLs (`d3d9.dll`, `d3d11.dll`, `dxgi.dll`, `d3d10core.dll`, `d3d10.dll`,
`d3d10_1.dll`, `d3d8.dll` in DXVK 1.7.2, 1.10.3, 2.7.1 and 3.1.1, both PE architectures). Every
import that crosses a DLL is a documented export, and none of them crosses between the two halves:

| DLL | Imports from another DXVK DLL |
| --- | --- |
| `d3d9.dll` | nothing (Vulkan only) |
| `d3d8.dll` | `d3d9.dll`: `Direct3DCreate9` |
| `dxgi.dll` | nothing |
| `d3d11.dll` | `dxgi.dll`: `CreateDXGIFactory1` |
| `d3d10core.dll` | `d3d11.dll`: `D3D11CoreCreateDevice` |
| `d3d10.dll`, `d3d10_1.dll` | `d3d10core.dll`: `D3D10CoreCreateDevice`, `dxgi.dll`: `CreateDXGIFactory`, `d3dcompiler_43.dll` (Wine's) |

So the halves are:

- **D3D8/D3D9** — `d3d9.dll`, `d3d8.dll`
- **DXGI/D3D10/D3D11** — `dxgi.dll`, `d3d11.dll`, `d3d10core.dll`, `d3d10.dll`, `d3d10_1.dll`

`d3d8` follows `d3d9` because it calls into it; `d3d10`, `d3d10_1` and `d3d10core` follow `d3d11`
and `dxgi` for the same reason. Splitting inside a half would put one build's COM objects into
another build's implementation, which is not covered by any export contract.

vkd3d-proton's `d3d12.dll` imports neither half (only `KERNEL32.dll` and `msvcrt.dll`), so a DX12
game is untouched by the split: DX12 keeps coming from the VKD3D-Proton package.

A DLL that is in neither half (`d3d12.dll`, `ntdll.dll`, anything new) stops the build with the
DLL's name in the message rather than being dropped silently.

## What a build needs before it can create a device

From DXVK's own sources (`src/dxvk/dxvk_instance.h`, `dxvk_adapter.cpp`, `dxvk_device_info.cpp`,
`src/d3d9/d3d9_device.cpp`, `src/d3d11/d3d11_device.cpp`):

| Build | Vulkan | Also required |
| --- | --- | --- |
| 1.7.2, 1.10.3 | 1.1 | `textureCompressionBC`; robustness2 and null descriptors are used when present |
| 2.3.1 … 2.6.2 | 1.3 | `textureCompressionBC`, `VK_EXT_robustness2` (`robustBufferAccess2` **and** `nullDescriptor`), `synchronization2`, `dynamicRendering` |
| 2.7.1 | 1.3 | the above plus `VK_KHR_maintenance5` |
| 3.1.1 | 1.3 | the above plus `VK_KHR_maintenance6` and `VK_KHR_load_store_op_none` |

`textureCompressionBC` is required by **both** halves in **every** build, so a driver without BC
textures runs no DXVK at all and the hybrid is refused with that reason.

`DxvkHybrid.Caps` reads the phone's own Vulkan report (`VulkanInfo`, which now also probes
`VK_KHR_maintenance5`, `VK_KHR_maintenance6`, `VK_KHR_load_store_op_none` and
`VK_EXT_graphics_pipeline_library`) and picks the newest build that can create a device there.
`DxvkHybrid.forDriver` pairs it with the fast 1.x build:

- D3D8/D3D9 from `Dxvk-1.7.2-linux.wcp`
- DXGI/D3D10/D3D11 from the newest build the driver can create (3.1.1, else 2.7.1, else 2.6.2, else
  2.3.1, else 1.10.3)

and `reversed` flips the two. On a driver that only reaches Vulkan 1.1-1.2, or 1.3 without
robustness2, the pair is 1.7.2 + 1.10.3 — still a hybrid, still two halves with different strengths.

## Which driver answers, and what it can do

The capabilities above belong to the driver that runs DXVK, and in the guest that is not the phone's
own Vulkan HAL: DroidDeck installs a driver beside Proton and points `VK_ICD_FILENAMES` at its
manifest. Reading the phone's report instead is what makes a Mali device look hopeless - the vendor
driver has no BC textures at all, while the PanVK installed beside Proton has them.

`DxvkHybrid.Guest` profiles what the published builds report for themselves (from
`The412Banner/droiddeck-nonadreno-drivers`' driver table and release notes, and zenithblue-oss's
PanVK-kbase releases), and `Caps.forGuest(guest, host)` answers with the profile:

| Guest driver | Vulkan | BC | robustness2 | Accurate half of the hybrid |
| --- | --- | --- | --- | --- |
| `panvk-mali-kbase`, `panvk-mali-g720`, `panvk-mali-g615`, `panvk-faux123-*` | 1.3 | yes (BC1 RGB and BC7 at 8 bpp, `PANVK_BC_AFRC=0` for exact) | yes on v9 and newer | 2.6.2 |
| `panvk-mali-g710` | 1.3 | yes | not established (its author marks the profile planned and not yet validated) | 1.10.3 |
| `panvk-mali-jm` | 1.3 | yes | not established (nullDescriptor is experimental on v9) | 1.10.3 |
| the Mali bridge (`vkbridge`) | 1.3 | yes (emulated beside the system driver) | not established | 1.10.3 |
| Venus | the host's | the host's | no on most Malis | 1.10.3, or nothing where the host has no BC |
| Turnip (Adreno) | the host's report stays the floor | | | as before |
| anything not profiled | the phone's own report | | | as before |

No published build reports `VK_KHR_maintenance5`, so the accurate half stops at 2.6.2 on all of them:
a hybrid on Mali is 1.7.2 answering D3D8/D3D9 and 2.6.2 answering DXGI/D3D10/D3D11 where PanVK gives
robustness2, and 1.7.2 + 1.10.3 where it does not.

Which driver the guest has is read at runtime, not asked for: the bundle the user picked
(`DriverBundle.active`), else the ICD manifest on disk (`LinuxRuntime.vulkanIcd`). The manifest is
chosen by GPU as well - `pickIcd` prefers `panfrost` on Mali and `radv` on Xclipse instead of always
taking `freedreno`, which is Turnip's, and falls back to Venus and then to the first manifest.

## Which chip gets which driver

`SocDrivers` answers two questions, oldest chip to newest: what a `ro.board.platform` code means
(`mt6789` is a Helio G99, not a name anybody recognises), and which published guest driver can talk
to the GPU inside it. 88 platform codes are carried: MediaTek Helio and Dimensity plus Kompanio,
Samsung Exynos under both spellings its devices report, Google Tensor, HiSilicon Kirin, Unisoc and
Rockchip. Qualcomm is deliberately absent - `SocNames` labels those chips and `DriverPairs` picks
their Turnip build.

The driver is chosen in two steps, because they fail differently:

1. **The chip.** Where the driver maintainers validated a build against an exact chip, that build
   wins: Helio G85/G88/G95/G99/G100, Dimensity 700/810/6020/6080/6100+/1080/1100/1200/1300/8050,
   Kompanio 1300T, Unisoc T820 and Exynos 1280/1330/1380 get `panvk-mali-jm`; Dimensity 7300/8300
   get `panvk-mali-g615`; Dimensity 9300/9300+ get `panvk-mali-g720`; Exynos 2200/1480/2400 get
   `radv-xclipse`.
2. **The GPU.** Otherwise the reported GPU decides by architecture: Midgard (Mali-T), Bifrost
   G31/G51/G71/G72, PowerVR and Huawei's Maleoon have **no published driver**, which is an answer and
   not a gap - `null` rather than a guess, because installing a build that cannot talk to the GPU
   produces a black screen and no explanation. G52 and G76 are job-manager; G57, G68, G77, G78, G610,
   G710, G715, G725 and G925 are covered by `panvk-mali-kbase`; G615 and G720 have their own builds.

A build whose own author has not validated it is not recommended: the `panvk-mali-g710` profile is
marked planned, so a Dimensity 9000 or a Tensor G2 falls back to `panvk-mali-kbase`, which the
maintainers measured on a Pixel 7. Same for an unlisted Xclipse - the published RADV builds name
920, 530 and 940, and other Xclipse parts get one by architecture rather than by measurement.

`GpuInfo.soc` now carries the chip name on non-Adreno devices too (it was empty), and
`GpuInfo.recommendedDriver()` returns the id - which `DxvkHybrid.Guest.of` turns into the capability
profile the hybrid is built from. A test walks every driver id the table can produce and fails if any
of them has no profile.

## The DXVK options a Mali device starts from

The guest reads one `DXVK_CONFIG` for both halves of a hybrid, so a tuning profile may only use keys
every build in the pair understands. Of the four versions DroidDeck ships (1.7.2, 1.10.3, 2.7.1,
3.1.1) only 25 options are common to all; `dxgi.tearFree` and `dxgi.numBackBuffers` stop at 1.x,
`dxvk.allowFse` starts at 2.7.1, and `d3d9.samplerLodBias` starts at 2.7.1, so none of them are used.

`DxvkTuning.maliOptions` emits six, all about latency and stalls rather than the picture:

| Option | Value | Why on Mali |
| --- | --- | --- |
| `d3d9.maxFrameLatency`, `dxgi.maxFrameLatency` | 1 | the phone's compositor already queues a frame; a second one in DXVK is what makes touch feel behind the picture |
| `dxvk.numCompilerThreads` | 2 (1 below four cores) | compiling shaders on every core starves the game thread and heats the phone out of its boost clock, which reads as stutter |
| `dxgi.maxDeviceMemory`, `dxgi.maxSharedMemory`, `d3d9.maxAvailableMemory` | a quarter of RAM, 1024-4096 MiB | memory is unified; a generous guess lets one game push the phone into swap, which arrives as a freeze |

Left out on purpose: `d3d11.maxTessFactor` and `d3d11.relaxedBarriers` change what is drawn or trade
correctness for speed, so they belong in the per-game configuration, where a player picks them for one
game, and `dxvk.hud` is a diagnostic rather than a default.

Precedence follows the existing texture-filtering mechanism and is set in
`tools/linuxfs/overlay/usr/local/bin/droiddeck-game-env: apply_config`: the profile is laid down
first, then the player's own `DXVK_CONFIG`, then the texture filtering choices from the UI - and DXVK
keeps the last value for a key, so both of those override the profile. Per game, `dxvkConfigGames`
still wins over `dxvkConfig`, profile included.

## Mali

Mali hardware has no BC texture unit, and the vendor driver reports no `VK_EXT_robustness2`. A real
report from a Mali-G720 (Dimensity 9300+, driver r44p1, in
[DroidDeck#218](https://github.com/Droid-Deck/DroidDeck/issues/218)):

```
API version: 1.3.247        BC textures: no          VK_EXT_robustness2: no
Geometry shaders: yes       Tessellation: yes        VK_EXT_transform_feedback: yes
VK_KHR_dynamic_rendering: yes                        VK_EXT_descriptor_indexing: yes
VK_EXT_extended_dynamic_state3: no
```

That driver can create a device with DXVK 1.x only, and only where something answers BC textures
for it. So Mali needs two things, and this document covers only the second:

1. **A Vulkan driver in the guest.** DroidDeck's guest driver is Mesa's Turnip for Adreno, and
   `DeviceSupport.adreno()` refuses to install one anywhere else. Mali needs a glibc PanVK build
   against the vendor's `mali_kbase`/CSF kernel interface, or the bionic-driver bridging discussed
   in DroidDeck#165. PanVK gained `textureCompressionBC` through GPU-side BCn decode in Mesa 25.1
   and Vulkan 1.4 on V10+ GPUs in Mesa 25.2, which is what makes DXVK 2.x and 3.x reachable there.
   Until such a driver ships, `GpuInfo` keeps Mali at `Support.UNSUPPORTED` and says so; nothing
   about the hybrid changes that gate.
2. **A DXVK pair that fits the driver.** Which is this feature. Mali reports as
   `Family.MALI_5TH`, `MALI_VALHALL`, `MALI_BIFROST` or `MALI_UNKNOWN` (from
   `/vendor/lib64/hw/vulkan.mali.so`, `ro.hardware.vulkan`, or the Vulkan device name), the model
   number comes out of the Vulkan device name, and the device report now names Mali instead of the
   board.

On Mali, `ComponentsManager.ensureMaliHybrid` runs when Steam starts, next to the existing forced
packages: it reads what the guest's driver can do, builds the hybrid for it and swaps it into every
Proton that is still on its shipped DXVK. A Proton the user already chose a package for is left
alone, and a driver that cannot run a second build logs the reason instead of guessing.

## Using it

**Components → DXVK** has two buttons beside *Import .wcp*:

- **Hybrid DXVK · old D3D9, new D3D11** — the 1.x half answers D3D8/D3D9, the newest build the
  driver can create answers DXGI/D3D10/D3D11.
- **Hybrid DXVK · new D3D9, old D3D11** — the same two builds the other way round.

Both read the phone's Vulkan report to decide what "new" is, download whichever half is not stored
yet, compose the package and swap it into the selected Proton. The package is then in the stored
list, so it can also be picked by hand, per Proton or for one game only (**Games → Game
environment**, or the per-game Components sheet). Removing it is the usual *Restore original* for a
Proton, or *Use Default* for a game.

Package names say what is inside, so both directions can sit in the store at once:

```
dxvk-hybrid-d3d9-1.7.2+d3d11-2.7.1.wcp
dxvk-hybrid-d3d9-2.7.1+d3d11-1.7.2.wcp
dxvk-hybrid-d3d9-1.7.2+d3d11-2.7.1+overlay.wcp
```

### While something is running

The hybrid is written to the Proton as soon as it is asked for, and the launch table
(`~/.local/share/droiddeck-components/desired.tsv`) is published with it. The wrapper re-reads that
table and re-checks every file's SHA-256 before each Proton start, so a game picks the new halves up
on its next launch. A process that is already running keeps the DLLs it has mapped: Windows DLLs
cannot be swapped under a running process, so the change is queued and applied by `applyQueued` when
the Proton is no longer in use. That is the same behaviour every other package has, and it is the
reason a switch takes one restart of the game rather than none.

Because a hybrid is one package, per-game overrides are the way to keep two different splits alive
at the same time: one game on the fast D3D9 half, another on the accurate one, without touching
either install between launches.

### Overlay

`DxvkHybrid.Spec(overlay = true)` borrows a DLL from the other build when its own half's build does
not ship it. That is the one place where a build boundary is crossed inside a half, and it only ever
crosses a documented export:

- DXVK 1.x has no `d3d8.dll`, so an overlay takes 2.x's `d3d8.dll` over 1.x's `d3d9.dll`.
- DXVK 2.x and 3.x dropped `d3d10.dll` and `d3d10_1.dll`, so an overlay takes 1.x's over 2.x's
  `d3d10core.dll`.

Without the overlay those DLLs are left out of the install, the launch table publishes a removal row
for them, and Wine's own builtin serves that API on top of whichever half is installed — which is
the safer default and what the two buttons build. The overlay is for a game that needs DXVK's own
`d3d8` or `d3d10` rather than Wine's.

## Where the code lives

| Piece | File |
| --- | --- |
| Halves, builds, capabilities, spec, plan, naming | `session/DxvkHybrid.kt` |
| Composing the `.wcp`, swapping it, the Mali rule | `session/ComponentsManager.kt` (`hybrid`, `hybridForDevice`, `applyHybrid`, `ensureMaliHybrid`) |
| Mali families, model, name and the ICD it wants | `gpu/GpuInfo.kt` |
| Chip to GPU to driver, oldest to newest | `gpu/SocDrivers.kt` |
| Guest driver profiles | `session/DxvkHybrid.kt` (`Guest`, `Caps.forGuest`) |
| Mali DXVK tuning profile | `core/DxvkTuning.kt`, published by `session/GameEnvironmentStore.kt`, applied by `tools/linuxfs/overlay/usr/local/bin/droiddeck-game-env` |
| Picking the guest's ICD manifest | `runtime/LinuxRuntime.java` (`vulkanIcd`, `pickIcd`) |
| The extra extension probes | `cpp/deviceinfo/vkinfo.c` |
| The two buttons | `ui/ComponentsPage.kt`, wired in `MainActivity.kt` |
| The launch hook | `session/SessionService.kt`, beside `ensureForcedPackages` |
| Tests | `test/.../session/DxvkHybridTest.kt`, `test/.../gpu/GpuDetectionTest.kt`, `test/.../runtime/GuestIcdTest.kt`, `test/.../core/DxvkTuningTest.kt`, `test/.../gpu/SocDriversTest.kt`, `tools/tests/test_game_environment.py` |
| Test of the rows landing in the guest | `tools/tests/test_game_environment.py` |

The composed package uses the same layout as a downloaded one (`profile.json` with `type`
`DXVK-Linux`, then `files/lib/wine/dxvk/<arch>/<dll>`), so nothing downstream needed to change:
install, fingerprinting, the launch table, the guest wrapper, originals and per-game profiles all
already work per file.
