<div align="center">

<img src="assets/branding/nfsux-logo.png" alt="NFSU-X Carrerillas" width="520">

### Recompilación estática de Xbox para Android

Proyecto derivado de [nfsu1-android](https://github.com/cherepavel/nfsu1-android)
de cherepavel: la versión original de Xbox (NTSC-U) de *Need for Speed:
Underground*, traducida instrucción a instrucción a C y ejecutada de forma
nativa en Android, sin emulador.

</div>

> **NFSU-X Carrerillas** es un proyecto de fans sin relación con Electronic Arts.
> *Need for Speed* y *Need for Speed: Underground* son marcas de Electronic Arts.
> El repositorio no incluye datos ni ejecutables del juego: necesitas tu propia
> copia original de Xbox (NTSC-U).

## Novedades de NFSU-X Carrerillas

Además de todo lo que ya hace el port original:

- **Traducción al español** del launcher, los ajustes y el editor de controles
  táctiles (también en inglés y ruso; sigue el idioma del sistema).
- **Pantalla de ajustes en el launcher**, sin editar archivos a mano:
  - Gráficos: 30/60 FPS, escala de resolución, FXAA, filtro anisotrópico,
    reflejos, desenfoque/bloom, píxeles cuadrados y pantalla panorámica 16:9.
  - Volumen general.
  - Idioma del juego (inglés, español, francés, alemán, italiano). El juego
    solo lo usa si su carpeta `LANGUAGES` lo incluye.
  - Inicio automático opcional.
- **Contador de FPS** en pantalla.
- **Copia de seguridad de partidas**: exporta e importa perfiles, progreso de
  la carrera y opciones en un `.zip`.
- **Jugar / Ajustes / Cambiar carpeta** al abrir la app, en lugar de arrancar
  el juego directamente.
- Aviso claro si la carpeta tiene otra versión del juego (por ejemplo PAL).
- Nombre, logo e icono propios.

Detalles técnicos en [android/README.md](android/README.md).

## Créditos

- Port para Android: [cherepavel](https://github.com/cherepavel) —
  [nfsu1-android](https://github.com/cherepavel/nfsu1-android).
- Basado en el port para Nintendo Switch de
  [antoxa2584x](https://github.com/antoxa2584x) —
  [nfsu1-sw](https://github.com/antoxa2584x/nfsu1-sw).
- Toolkit de recompilación: [xboxrecomp](xboxrecomp/).
- NFSU-X Carrerillas: [KB-retro](https://github.com/KB-retro).

## Build for Android

The commands below are for Linux. The APK targets **ARM64**, Android 11 or
newer (API 30+).

### Requirements

- Git, Python 3 with `venv` and `pip`, and JDK 21 or newer.
- Android SDK with platform **37**, NDK **30.0.16248370**, and CMake **4.1.2**.
  Install these through Android Studio's SDK Manager or `sdkmanager`.
- The supported original Xbox **NTSC-U** `default.xbe` from your own game copy.
  Game data is not included in the repository or APK.

Gradle **9.8.0** is downloaded by the wrapper; Android Gradle Plugin **9.4.1**
is configured in the project. Run the build from a Git checkout: the launcher
displays the current commit as its build identifier.

### Prepare and build

From the repository root, initialize the pinned source dependencies once:

```sh
git submodule update --init --recursive
```

Open the repository root in Android Studio and select your JDK, or use the
command line. In `local.properties`, set your SDK and game executable
paths (this file is ignored by Git):

```properties
sdk.dir=/path/to/Android/Sdk
nfsu.xbe=/path/to/Need For Speed Underground/default.xbe
```

If `nfsu.xbe` is omitted, Gradle uses
`game/Need For Speed Underground/default.xbe` relative to the repository root.
Then build:

```sh
./gradlew assembleDebug
```

Gradle creates its own Python virtual environment, installs the pinned generator
dependencies, generates C sources from the XBE, and builds the native libraries
and APK. Python 3 with `venv` must be installed; no manual `pip` or preparation
script is needed. The first build needs network access for dependencies.
Generated sources and analysis files live under `android/app/build/` and are
regenerated when the XBE or generator inputs change. `clean` removes them.
Use `-Pnfsu.xbe=/path/to/default.xbe` to override the executable for one build,
and `-Pnfsu.python=/path/to/python3` to choose a Python interpreter.

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.
To install it on a connected device with USB debugging enabled:

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

On first launch, select the extracted Xbox game folder containing `default.xbe`,
`CARS`, `TRACKS`, and the remaining disc files. Keep that folder available while
playing; it is read directly rather than copied into the app.

See [the Android README](android/README.md) for controls, graphics, and runtime
details.

### GitHub Actions

The [Android APK workflow](.github/workflows/android.yml) builds an ARM64 debug
APK only when started by hand: **Actions → Android APK → Run workflow**. It
uses the root Gradle build and runs `lintDebug`.

Before the first run, add a repository Actions secret named `NFSU_XBE_URL`
under **Settings → Secrets and variables → Actions**. Set it to a private HTTPS
URL that downloads the supported NTSC-U `default.xbe` directly, without a login
page. Supply your own game copy. The executable is downloaded into the
runner's temporary directory and is not uploaded as an artifact.

Download the APK from the successful run's **Artifacts** section. The APK
contains code recompiled from the game, so the artifact is kept for **one
day** only: on a public repository any signed-in GitHub user can download it.
Delete it from the run page once you have it, and do not share it. The APK
uses the runner's debug signing key, which can change between runs; updating
an existing installation may require uninstalling it first. Back up saves
(Settings → Export saves) before uninstalling.

## License

Copyright (C) 2026 cherepavel.

The original code in this project is licensed under the **GNU General Public
License, version 3 or (at your option) any later version**
(`SPDX-License-Identifier: GPL-3.0-or-later`). See [LICENSE](LICENSE) for the
full terms. This software is provided without warranty.

Third-party code retains its existing copyright and license notices. In
particular, see [xboxrecomp/LICENSE](xboxrecomp/LICENSE),
[xboxrecomp/NOTICE](xboxrecomp/NOTICE), and the license files in the SDL and
glslang submodules.

The GPL grant covers the project's code, not the original game's executable,
game data, logos, artwork, or trademarks. Those remain subject to their
respective owners' rights; inclusion or reference here does not grant a license
to them.
