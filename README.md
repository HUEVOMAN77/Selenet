# ydl-android

App Android que ejecuta **youtube-dl** dentro del teléfono.

El motor es el de [`HUEVOMAN77/youtube-dl`](https://github.com/HUEVOMAN77/youtube-dl), fork de
`ytdl-org/youtube-dl` en el commit `956b8c5855` (versión `2025.04.07`).

> **El fork lleva un parche chico en el extractor** (`app/patches/youtube-player-clients.patch`).
> Agrega el parámetro `youtube_player_clients` a `YoutubeDL`, que es lo único que permite cambiar de
> cliente de YouTube y reintentar cuando el sitio responde `HTTP 403`. Sin ese parche la app vuelve a
> elegir siempre un solo cliente y el modo "solo audio" se corta con 403. Por eso `backend.py` rechaza
> instalar un motor que no lo tenga (Ajustes → Actualizar motor).

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
# 1. Aplicar el parche del cliente al fork y generar el wheel
cd ../youtube-dl && git apply ../ydl-android/app/patches/youtube-player-clients.patch
python -m pip wheel . --no-deps --no-build-isolation -w ../ydl-android/app/libs

# 2. Escribir local.properties (no se committea) con tu SDK, Python y wheel
#    sdk.dir=.../Android/Sdk
#    buildPython=.../python.exe
#    ytdlWheel=./libs/youtube_dl-2025.4.7-py2.py3-none-any.whl

# 3. Compilar
JAVA_HOME=/ruta/jdk17 gradle-8.9/bin/gradle assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk (~16 MB, arm64-v8a)
```

## Firma de release

`assembleRelease` firma con `signingConfigs.debug` salvo que `local.properties` tenga las cuatro
claves de la firma propia; en ese caso usa `signingConfigs.fork`:

```
keystoreFile=/ruta/ytdl-fork-release.jks
keystoreStorePassword=...
keystoreAlias=ytdl-fork
keystoreKeyPassword=...
```

La clave **no se committea**: es lo que autoriza las actualizaciones. Si se pierde, la única forma
de instalar una versión nueva es desinstalar la app (se borra el historial).

```sh
keytool -genkeypair -v -keystore ytdl-fork-release.jks -alias ytdl-fork \
  -keyalg RSA -keysize 2048 -validity 10950 -dname "CN=..., OU=..., O=..., C=..."
```

## Limitaciones conocidas de la v1

- **Sin ffmpeg embebido**: no hay merge de streams DASH, así que `best` rinde el formato
  progresivo (~360p en YouTube). El modo "solo audio" baja el stream `bestaudio` sin convertir.
- `--update` de youtube-dl no tiene sentido dentro del APK: el código vive en el APK.
- Probado en PC la lógica de `backend.py` de punta a punta; la UI de Android no se verificó en
  dispositivo en esta etapa.
