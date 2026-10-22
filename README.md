# ydl-android

App Android que ejecuta **youtube-dl sin modificar** dentro del teléfono.

El motor es el de [`HUEVOMAN77/youtube-dl`](https://github.com/HUEVOMAN77/youtube-dl), fork de
`ytdl-org/youtube-dl` en el commit `956b8c5855` (versión `2025.04.07`). No se parcheó ni una línea
del paquete `youtube_dl`: la app lo importa y llama a `YoutubeDL` con su API pública.

## Cómo funciona

- **Chaquopy 17.1** mete un CPython 3.12 (arm64) dentro del APK.
- El wheel de youtube-dl se instala en tiempo de build y termina empaquetado en
  `assets/chaquopy/requirements-common.imy`.
- `app/src/main/python/backend.py` corre la descarga en un hilo de Python y reenvía el progreso
  a la Activity a través de un logger e `progress_hook` estándar de youtube-dl.
- `MainActivity` levanta la UI, recibe links por el intent `ACTION_SEND` (menú Compartir) y
  muestra el log.

## Requisitos de build

| Herramienta | Versión usada |
|---|---|
| JDK | 17 (Temurin 17.0.20+1) |
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| compileSdk / minSdk / targetSdk | 35 / 24 / 28 |
| Python de build | 3.12.15 |
| Chaquopy | 17.1.0 |

`targetSdk` es **28 a propósito**: desde API 29 Android bloquea ejecutar binarios desde el
directorio de datos de la app, y eso impide usar ffmpeg. Bajarlo a 28 lo evita.

## Pasos

```sh
# 1. Generar el wheel desde tu fork de youtube-dl
python -m pip wheel ../youtube-dl --no-deps --no-build-isolation -w app/libs

# 2. Escribir local.properties (no se committea) con tu SDK, Python y wheel
#    sdk.dir=.../Android/Sdk
#    buildPython=.../python.exe
#    ytdlWheel=./libs/youtube_dl-2025.4.7-py2.py3-none-any.whl

# 3. Compilar
JAVA_HOME=/ruta/jdk17 gradle-8.9/bin/gradle assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk (~16 MB, arm64-v8a)
```

## Limitaciones conocidas de la v1

- **Sin ffmpeg embebido**: no hay merge de streams DASH, así que `best` rinde el formato
  progresivo (~360p en YouTube). El modo "solo audio" baja el stream `bestaudio` sin convertir.
- `--update` de youtube-dl no tiene sentido dentro del APK: el código vive en el APK.
- Probado en PC la lógica de `backend.py` de punta a punta; la UI de Android no se verificó en
  dispositivo en esta etapa.
