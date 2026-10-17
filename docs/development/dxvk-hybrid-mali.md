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
packages: it reads the driver's capabilities, builds the hybrid for them and swaps it into every
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
| Mali families, model and name | `gpu/GpuInfo.kt` |
| The extra extension probes | `cpp/deviceinfo/vkinfo.c` |
| The two buttons | `ui/ComponentsPage.kt`, wired in `MainActivity.kt` |
| The launch hook | `session/SessionService.kt`, beside `ensureForcedPackages` |
| Tests | `test/.../session/DxvkHybridTest.kt`, `test/.../gpu/GpuDetectionTest.kt` |
| Test of the rows landing in the guest | `tools/tests/test_game_environment.py` |

The composed package uses the same layout as a downloaded one (`profile.json` with `type`
`DXVK-Linux`, then `files/lib/wine/dxvk/<arch>/<dll>`), so nothing downstream needed to change:
install, fingerprinting, the launch table, the guest wrapper, originals and per-game profiles all
already work per file.
