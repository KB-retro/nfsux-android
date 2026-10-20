# Android ARM64 port

This is the Android host for the Xbox NFS Underground static recompilation.
The target device for bring-up is Retroid Pocket 4 Pro (Android 13, Mali-G77).

## Build

Use JDK 21 or newer, Python 3 with `venv`, and Android SDK platform 37,
NDK 30.0.16248370, and CMake 4.1.2. Gradle 9.8.0 and AGP 9.4.1 are pinned.
Initialize the SDL, glslang and zlog submodules once:

```sh
git submodule update --init --recursive
```

Set `sdk.dir` and `nfsu.xbe` in `local.properties` at the repository root:

```properties
sdk.dir=/path/to/Android/Sdk
nfsu.xbe=/path/to/Need For Speed Underground/default.xbe
```

Build from the repository root, or open the root directory in Android Studio:

```sh
./gradlew assembleDebug
```

Gradle installs Python dependencies in its own virtual environment, runs the
recompilation pipeline, and builds the APK. The default XBE location is
`game/Need For Speed Underground/default.xbe` relative to the repository root.
Override it with `-Pnfsu.xbe=/path/to/default.xbe`; use
`-Pnfsu.python=/path/to/python3` for a different Python interpreter.
Generation tracks its inputs and writes under `android/app/build/`, so `clean` removes
all generated Android sources. No game data is packaged in the APK.

Output: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Experimental menu touch

Front-end menu items accept taps through the original game's FEng mouse input,
without an overlay or manually positioned touch zones. Main Menu -> Options ->
Camera and row selection were checked on Retroid Pocket 4 Pro. Directional
settings arrows and complete touch-only navigation are not implemented yet;
keep the controller available. Pause/results and racing touch controls are not
enabled. See [native menu touch](../docs/native-menu-touch.md) for scope and tests.

## Launcher settings and languages

Once the saved game folder passes validation, the launcher shows **Play**,
**Settings** and **Change game folder** instead of starting at once. Settings
can turn on **Start the game automatically** (1.5 s delay; opening Settings
cancels it). A newly selected folder never starts the game on its own.

Settings apply at the next game start:

- Graphics rows shared with the in-game Options -> Camera screen, written to
  `nfsu1x_options.txt` (frame rate, resolution scale, FXAA, anisotropic
  filtering, car reflections, blur/bloom, square pixels). Unknown keys such as
  the touch sensor mode are kept.
- Runtime switches written to `nfsu1x_launcher.txt` and loaded before
  `nfsu1x_env.txt`, which still overrides them: `RECOMP_WIDESCREEN`
  (picture format: unset = automatic, 1 = 16:9, 0 = original 4:3), `RECOMP_AUDIO_VOLUME` (0-100) and `RECOMP_LANGUAGE`.

With the automatic picture format, GameActivity sets `RECOMP_WIDESCREEN` from
the screen before the native runtime starts: screens narrower than 1.45:1
(4:3, 5:4) get the original 4:3 picture filling the screen, wider ones the
16:9 Hor+ picture. 4:3 also turns off Hor+, the HUD corrections and square
pixels; menu touch follows the same letterbox.

`RECOMP_LANGUAGE` is the dashboard language the emulated kernel reports
through `XC_LANGUAGE` (1 English, 3 German, 4 French, 5 Spanish, 6 Italian;
Android default 5, Spanish, set in android_host.c; 1 elsewhere). The NTSC-U boot code (0x11190) maps only English, French and
German and falls back to English, so `recomp_manual.c` overrides it: any of
the five returns its id in the game's language table (0x255258), and the
game loads `LANGUAGES\LANGUAGE_<NAME>.BIN`. The NTSC-U disc carries all of
them, including Spanish and Italian. Speech stays in English.

The APK runs code recompiled from the NTSC-U `default.xbe` and only starts
that file. When the folder holds another NFSU release (for example the
European PAL disc in Spanish), the launcher names the release instead of the
generic error. Placing the NTSC-U `default.xbe` next to the PAL disc files is
untested: the data formats of the two releases have not been compared.

Settings can also show an **FPS counter** in the top-left corner (presented
Vulkan frames per second, sampled once a second) and export or import the
saves: a `.zip` with the `save/` tree (profiles, career) and
`nfsu1x_options.txt`. Import only accepts those paths and replaces files of
the same name; close the game first.

The launcher, settings and touch-control editor are translated into English,
Russian and Spanish and follow the system language.

## Quick menu

Back (button or gesture) or Select + Start on a controller opens an in-game
quick menu (`QuickMenuDialog`) instead of leaving the game; "Exit game" in it
does that after a confirmation. It lists the rows of Options -> Camera plus
FSR Sharpness through JNI (`nativeOption*` in `recomp_manual.c`: same
`s_rows`, applied at once and saved to `nfsu1x_options.txt`), the FPS counter
and fast cores (`nativeSetFastCores`, pinning or unpinning the game and
renderer threads live). Controller: up/down choose a row, left/right or A
change it. Language and picture format need a restart and stay in the
launcher settings.

## FSR 1.0 upscaling

Options -> Camera "Anti-Aliasing" is Off / FXAA / FSR 1.0 (`fxaa=0..2`); FSR
sharpness (Soft / Normal / Strong = 0.5 / 0.2 / 0 stops, `fsr_sharpness=0..2`)
is in the quick menu and launcher only, since Camera has no free row handler
slot left in a usable place.

FSR replaces the FXAA present pass with AMD FidelityFX Super Resolution 1
(MIT), ported from `ffx_fsr1.h` into two GLSL passes in `nv2a_vk.c`: EASU
upscales the surface into an intermediate image of the presented rectangle's
size, RCAS sharpens it into the swapchain. It runs only when the surface is
smaller than that rectangle; otherwise FXAA is used. `RECOMP_VK_FSR=1` turns
it on at boot, `=0` forces it off; `RECOMP_VK_FSR_SHARPNESS` sets the start
strength. FSR 2/3 need motion vectors and depth the title does not produce.
The shaders pass glslangValidator (Vulkan 1.1) and a NumPy mirror of them;
not yet run on a device.

## Performance hints and -O2

On Android 13+ the game and renderer threads share an APerformanceHint session
(`src/android_perf_hint.c`): each presented frame reports its interval against
the 30 or 60 FPS target, so power-saving CPU profiles raise clocks when frames
run late. Older Android versions skip it; `RECOMP_PERF_HINT=0` turns it off.
The `[perfhint]` line in `logs/performance.log` says whether a session started.
`RECOMP_FAST_CORES=1` (launcher: "Use only the fast CPU cores", off by
default) pins the game and renderer threads to the cores whose maximum
frequency is at least 80% of the fastest, read from sysfs; `[cores]` lines in
`logs/performance.log` show the result. Meant for power-saving profiles that
move the game onto little cores. Going to the background also replaces every
thread's affinity with the little cores and returning does not restore it;
about twice a second the renderer checks its own mask and, when it changed,
gives every thread in `/proc/self/task` its mask back (fast cores for the
game and renderer threads when enabled, all cores otherwise).
The CI build compiles the lifted code with `-Pnfsu.genOpt=-O2`, the level used
for the device measurements in `docs/performance-counters.md`.

## Experimental 60 FPS

In the in-game **Options -> Camera** menu, set **Frame Rate** to **60 FPS**.
The Android default is **60 FPS** (30 FPS elsewhere); the choice is saved in `nfsu1x_options.txt`
alongside the other port options and applies at the next race frame.
This changes NFSU1's D3D presentation interval from two vblanks to one,
while keeping the kernel clock and simulation timing unchanged. Actual frame
rate depends on the device and rendering settings. Race speed, timers,
animations and audio at 60 FPS still require device validation. If GPU flips
stop for 10 seconds after startup, the diagnostic watchdog writes up to two
guest-state snapshots into `nfsu1.log` without terminating the game. Loading
screens and background pauses can also trigger these snapshots.

## Play

1. Install the APK on an ARM64 Android device (Android 11 or newer).
2. Keep the extracted disc in an ordinary folder on internal storage, an SD
   card or a USB drive. The folder must contain `default.xbe`, `CARS`,
   `FRONTEND`, `GLOBAL`, `LANGUAGES`, `MOVIES`, `SOUND` and `TRACKS`.
3. Tap **Выбрать папку игры**, choose this folder and grant read access.
4. The game starts automatically after the folder passes validation. Later app
   launches validate the saved folder and open the game directly. If the folder
   is missing or unreadable, the folder-selection menu appears.
   SDL reads the built-in or connected controller.

The app does not import/copy the selected game folder. SAF resolves document
IDs and opens seekable read-only descriptors; regular native read/seek then
works directly on the original files. Case-insensitive metadata lookup matches
Xbox paths. Providers that return non-seekable streams are unsupported. Keep
the selected drive connected while playing. Saves, settings and shader caches
live under the app's external files directory, separately from the disc data.

## Graphics

The Android Vulkan renderer targets **Vulkan 1.1**, using ordinary render
passes/framebuffers, per-frame descriptor pools and pipelines containing vertex,
rasterizer, depth and stencil state. It does not require dynamic rendering,
push descriptors or extended dynamic state. The existing desktop/Switch Vulkan
1.3 path is retained. Cube maps, mipmaps and FXAA use the same shader and texture
implementation. Mobile BC/DXT support is optional; the existing CPU decode path
is used when the driver lacks it.

The Android baseline is commit `9d94d5c`, with 3D/HUD aspect corrections
retained. 3D rendering keeps its widescreen correction. Only the racing HUD
keeps proportional text, icons and gauges, with objects and the minimap moved
towards the screen edges. All menus retain the original 4:3 layout stretched
across the widescreen output, including text, buttons and background pieces.
Menu draws in racing state (pause, rewards and results) undo the HUD projection
for every quad equally. Loading screens retain their original projection.
`RECOMP_HORPLUS=0` disables the corrections. The guest draw checks in
`tests/ui_aspect.c` cover coordinates, source restoration, menu stretching,
HUD scope restoration and projection selection. They do not establish visual
correctness or race stability on a device. The revised menu behavior still
needs device validation.

The Android host owns the SDL window and services events on SDL's main thread,
independently of guest flips. GameActivity is the sole owner of fullscreen policy, using WindowInsetsController
and a decor layout that does not shrink for system bars. SDL does not request
fullscreen window style.
Java surface callbacks publish retained native-window references, a window
lifetime generation and its dimensions. The Vulkan renderer consumes snapshots;
window replacement recreates the surface and swapchain, while size changes only
rebuild the swapchain. It waits when no active window is available. Presentation
semaphores belong to swapchain images. GameActivity is a singleTask because the
SDL/native runtime is process-global; launcher re-entry returns to that instance.
The acquisition timeout only bounds a pause racing a window snapshot. Screen
lock/unlock, launcher re-entry and app switching still require device validation.

## Diagnostics and validation

Native logs are in `logs/` under
`/sdcard/Android/data/org.nfsu.android/files/`: `all.log` and separate input,
graphics, audio, file, runtime, performance, kernel and system logs. The directory
stays in place across launches. zlog appends and rotates individual files by
size, naming archives with the rotation date and number. The root `nfsu1.log`
is retained as a compatibility copy. Optional `nfsu1x_env.txt` lives in the
root runtime directory and is read at startup. Logging uses zlog with
TRACE/DEBUG/INFO/NOTICE/WARN/ERROR/FATAL levels and per-category thresholds. See
[Android logging](../docs/android-logging.md) and
[example settings](nfsu1x_env.example.txt). For example:

```sh
adb logcat -s NFSU SDL SDL/APP AndroidRuntime
adb pull /sdcard/Android/data/org.nfsu.android/files/nfsu1.log
adb pull /sdcard/Android/data/org.nfsu.android/files/logs
```

The game has a separate process, so its native shutdown does not kill the
launcher. A successful APK build or installation does not establish a working
race, correct rendering, sound, saves or performance. Device validation is
required, including selecting a folder on removable storage and reopening the
app with its persisted permission. Touchscreen controls are not implemented.

## Verified on 2026-10-14

The APK was built, signed, installed and run on the user's Retroid Pocket 4 Pro.
The actual driver reports Mali-G77 MC9 / Vulkan 1.1.177. The selected game tree
was on the removable volume `FEDE-A117`, under `Games/Need For Speed Underground`.
The selection survived APK updates; XBE and game assets were read through SAF.

Verified by device logs/screenshots: movies, upright fullscreen output, main
menu, profile creation and a Quick Race with the car, track and HUD visible.
The user confirmed controller input, driving and sound. `PAVEL.nfs` was created
under the app's `save/UserData/45410047/` tree. Reloading that profile after a
fresh restart and Career mode have not been checked. FPS, prolonged stability
and 16 KB-page device operation have not been measured.

One stop on the title screen after skipping movies was reported; it did not
reproduce during the subsequent menu/race run. Its cause remains unresolved.
This first debug APK retains input diagnostics and one non-terminating guest
snapshot at 25 seconds. The message "no exit after 25s" is a snapshot label,
not a detected hang or a forced shutdown.

Checks passed: `assembleDebug`, `lintDebug`, APK signing verification and
`zipalign -c -P 16 4`. A POSIX platform-memory regression covers no-access
reservations, occupied-address refusal and carving a high window from a 4 GB
reservation:

```sh
cc -std=gnu11 -O1 -I xboxrecomp/src tests/platform_memory.c \
  xboxrecomp/src/platform/win32_compat.c -pthread -lm -o /tmp/nfsu-memory-test
/tmp/nfsu-memory-test
```

Local evidence is in the ignored `build/validation/` directory.
