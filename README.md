<div align="center">

<img src="assets/branding/nfsux-logo.png" alt="NFSU-X Carrerillas" width="520">

### Recompilación estática de Xbox para Android

La versión original de Xbox (NTSC-U) de *Need for Speed: Underground*,
traducida instrucción a instrucción a C y ejecutada de forma nativa en Android,
sin emulador.

</div>

> **NFSU-X Carrerillas** es un proyecto de fans sin relación con Electronic Arts.
> *Need for Speed* y *Need for Speed: Underground* son marcas de Electronic Arts.
> El repositorio no incluye datos ni ejecutables del juego: necesitas tu propia
> copia original de Xbox (NTSC-U).

## Funciones

- **Traducción al español** del launcher, los ajustes y el editor de controles
  táctiles (también en inglés y ruso; sigue el idioma del sistema).
- **Pantalla de ajustes en el launcher**, sin editar archivos a mano:
  - Gráficos: 30/60 FPS, escala de resolución, FXAA, filtro anisotrópico,
    reflejos, desenfoque/bloom y píxeles cuadrados.
  - Formato de imagen: automático, panorámico 16:9 o clásico 4:3. En
    automático, las pantallas 4:3 (por ejemplo consolas retro Android) usan la
    imagen 4:3 original a pantalla completa, sin franjas negras.
  - Volumen general.
  - Idioma del juego: inglés, español, francés, alemán o italiano. El disco
    USA ya trae los textos de todos; las voces siguen en inglés.
  - Inicio automático opcional.
- **Contador de FPS** en pantalla.
- **Copia de seguridad de partidas**: exporta e importa perfiles, progreso de
  la carrera y opciones en un `.zip`.
- **Jugar / Ajustes / Cambiar carpeta** al abrir la app.
- Aviso claro si la carpeta tiene otra versión del juego (por ejemplo PAL).
- Nombre, logo e icono propios.

- Controles táctiles personalizables en carrera, menús táctiles, vibración y
  compatibilidad con mandos.
- Renderizado Vulkan con panorámico 16:9 o 4:3, reflejos en los coches, mipmaps y FXAA.

Detalles técnicos (en inglés) en [android/README.md](android/README.md).

## Créditos

- Port para Android: [cherepavel](https://github.com/cherepavel) —
  [nfsu1-android](https://github.com/cherepavel/nfsu1-android).
- Basado en el port para Nintendo Switch de
  [antoxa2584x](https://github.com/antoxa2584x) —
  [nfsu1-sw](https://github.com/antoxa2584x/nfsu1-sw).
- Toolkit de recompilación: [xboxrecomp](xboxrecomp/).
- NFSU-X Carrerillas: [KB-retro](https://github.com/KB-retro).

## Instalación

1. Necesitas un móvil o consola Android **ARM64** con **Android 11 o superior**.
2. Descarga la APK (ver [Compilar con GitHub Actions](#compilar-con-github-actions))
   e instálala. Si Android lo pide, permite instalar apps de origen desconocido.
3. Copia en el móvil la carpeta del juego **extraída** de tu disco original de
   Xbox **NTSC-U (USA)**: debe contener `default.xbe`, `CARS`, `FRONTEND`,
   `GLOBAL`, `LANGUAGES`, `MOVIES`, `SOUND` y `TRACKS`. Puede estar en la
   memoria interna, una tarjeta SD o un USB. No se admite la ISO sin extraer.
4. Abre **NFSU-X**, pulsa **Seleccionar carpeta**, elige esa
   carpeta y concede el acceso de lectura.
5. Pulsa **Jugar**. En **Ajustes** puedes cambiar gráficos, sonido, idioma,
   el contador de FPS y hacer copias de seguridad de tus partidas.

La carpeta del juego se lee directamente y no se copia dentro de la app:
mantenla disponible mientras juegas. Las partidas, ajustes y registros se
guardan en `Android/data/org.nfsu.android/files/`.

Al actualizar a una APK nueva, normalmente se instala encima. Si Android no lo
permite (la firma puede cambiar entre compilaciones), exporta antes las
partidas en **Ajustes → Exportar partidas**, desinstala, instala la nueva e
impórtalas.

## Licencia

Copyright (C) 2026 cherepavel. Modificaciones de NFSU-X Carrerillas (C) 2026
KB-retro.

El código de este proyecto se distribuye bajo la **Licencia Pública General de
GNU, versión 3 o (a tu elección) cualquier versión posterior**
(`SPDX-License-Identifier: GPL-3.0-or-later`). Consulta [LICENSE](LICENSE)
para los términos completos. Este software se ofrece sin ninguna garantía.

El código de terceros conserva sus avisos de copyright y licencia. En
particular, consulta [xboxrecomp/LICENSE](xboxrecomp/LICENSE),
[xboxrecomp/NOTICE](xboxrecomp/NOTICE) y los archivos de licencia de los
submódulos SDL y glslang.

La licencia GPL cubre el código del proyecto, no el ejecutable original del
juego, sus datos, logotipos, ilustraciones ni marcas. Estos siguen sujetos a
los derechos de sus respectivos propietarios; mencionarlos aquí no concede
ninguna licencia sobre ellos.
