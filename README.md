# Selenet

**Bajador de videos para Android con youtube-dl adentro del teléfono.**
Versión oficial **1.0** — primera release estable del proyecto.

Selenet es el nombre oficial de lo que empezó como `ydl-android`: una app que ya estaba
andando, con el motor embebido y la cola funcionando, pero frenada en una etapa de pruebas.
Le dimos continuidad y la revivimos desde adentro: misma base, misma forma de descargar,
y encima todo lo que le faltaba para usarla todos los días. De ese trabajo salió esta 1.0,
que es la primera versión que consideramos **oficial y estable**.

---

## Qué hace

### Descarga
- Pegás un link (o lo compartís desde cualquier app con el menú **Compartir**) y va directo a la cola.
- Tres modos: **Todos** (elige la calidad de ese video), **Solo MP4** y **Solo audio (m4a)**.
- Cola con reanudar, pausar, reintentar y cancelar; sigue bajando con la app cerrada (servicio en primer plano).
- Reintentos automáticos ante cortes de red, con espera progresiva.
- Buscador dentro de la cola, que ignora tildes y mayúsculas.

### Calidad, de la más baja a la más alta
- Escalera elegible desde **Ajustes**: 144p · 240p · 360p · 480p · 720p · 1080p · Mejor (automático).
- Dos escaleras independientes: una para el **video** y otra para el **solo audio**.
- En el modo solo audio la calidad del sonido sube con la resolución elegida, porque el audio sale
  del mismo MP4 progresivo (itag 17 ≈ 24 kbps, 18 ≈ 96 kbps, 22 ≈ 128 kbps, 37 ≈ 192 kbps).
- Cada preset prueba primero el itag progresivo clásico de esa altura y después usa el techo de
  altura como red: **nunca se pasa de lo que pediste y nunca baja dos pistas para juntarlas**.

### Solo audio sin ffmpeg
- Baja el MP4 progresivo a una caché interna y `MediaExtractor`/`MediaMuxer` separan la pista de
  audio en un `.m4a` real, con el título del video como nombre y `(1)`, `(2)`… si está tomado.
- Se registra como audio en MediaStore para que aparezca en cualquier reproductor.
- Si la extracción falla, la barra no miente (Descargando… / Extrayendo audio…) y queda el botón
  **Guardar el MP4 completo** con lo que ya bajó.

### Carpeta de destino
- **Ajustes → Elegir carpeta** resuelve la ruta real de la carpeta que selecciones y escribe ahí
  derecho, video y audio. Se comprueba que se pueda escribir antes de guardarla; si no se puede,
  vuelve a la anterior en vez de fallar en cada descarga.
- Por defecto: `Movies/Selenet` (las versiones anteriores escribían en `Movies/YTDL Fork`, que
  sigue leyéndose en el historial para que no pierdas nada).
- Mantené pulsado el botón para volver a la carpeta de la app.

### Historial
- Muestra **todo lo que ya está bajado**, no solo lo que la app recuerda: junta la base de datos
  con los archivos reales de la carpeta elegida, `Movies/Selenet`, la carpeta vieja y la interna.
- Miniatura de YouTube si hay link, y si no, un fotograma del propio archivo (con caché LRU,
  sin librerías de imágenes y sin permisos de almacenamiento extra).
- Abrir, compartir, volver a descargar y borrar (con confirmación).

### Interfaz
- Tema oscuro con degradado azul→verde agua, Material 3, tarjetas de vidrio, indicador de progreso
  y navegación inferior.
- Icono propio (adaptativo, con variante monócroma para el tema monocromo de Android).
- **Registro técnico** oculto por defecto; se enciende desde Ajustes si hace falta ver qué hizo
  el motor. Si la app se cierra mal, el detalle queda guardado y se puede copiar.

### Motor actualizable
- **Ajustes → Actualizar motor** baja el código de youtube-dl desde GitHub y lo usa en lugar del
  que viene en el APK. Mantené pulsado para volver al del APK. Si el motor nuevo no trae el parche
  de clientes, la app lo rechaza y avisa por qué.

### Modo híbrido con la PC
- Para las calidades que vienen con video y audio por separado (arriba de 1080p), el link lo
  puede procesar tu PC por red desde la misma app.

---

## Instalación

Descargá `Selenet-1.0.apk` de la [página de releases](../../releases) y abrilo en el teléfono.
Android va a pedir permitir instalar desde fuentes desconocidas la primera vez.
No necesita root, ni Play Store, ni permisos de almacenamiento adicionales.

Todas las releases de este proyecto se firman con la misma clave, así que podés actualizar
encima de una versión anterior sin desinstalar (se conserva el historial y la cola).

---

## Cómo funciona

- **Chaquopy 17.1** mete un CPython 3.12 (arm64-v8a) dentro del APK; no hay binarios ffmpeg.
- El wheel de youtube-dl se instala al compilar y termina empaquetado en
  `assets/chaquopy/app.imy`, junto con `backend.py`.
- `backend.py` corre la descarga en un hilo de Python y reenvía el progreso por el
  `progress_hook` estándar de youtube-dl.
- `DownloadService` es el dueño de la cola: ejecuta, persiste en SQLite, mantiene la notificación
  y expone los cambios a los tres fragmentos (Descargas, Historial, Ajustes).
- Java puro, sin Kotlin ni coroutines: hilos y `Handler` de la UI.

> **El fork del motor lleva un parche chico** (`app/patches/youtube-player-clients.patch`).
> Agrega el parámetro `youtube_player_clients` a `YoutubeDL`, que es lo único que permite cambiar
> de cliente de YouTube y reintentar cuando el sitio responde `HTTP 403`. Sin ese parche la app
> vuelve a elegir siempre un solo cliente y el modo "solo audio" se corta con 403.

---

## Requisitos de build

| Herramienta | Versión usada |
|---|---|
| JDK | 21 |
| Gradle | 8.10 |
| Android Gradle Plugin | 8.7.3 |
| compileSdk / minSdk / targetSdk | 35 / 24 / 28 |
| Python de build | 3.12 |
| Chaquopy | 17.1.0 |

`targetSdk` es **28 a propósito**: con API 29+ Android bloquea ejecutar binarios desde el
directorio de datos de la app y cambia por completo el acceso a carpetas públicas. Con 28 la app
puede escribir derecho en la carpeta que elige el usuario y seguir usando `File`, sin permisos
nuevos ni rescritura al API de SAF.

## Pasos

```sh
# 1. Aplicar el parche del cliente al fork del motor y generar el wheel
cd ../youtube-dl && git apply ../Selenet/app/patches/youtube-player-clients.patch
python -m pip wheel . --no-deps --no-build-isolation -w ../Selenet/app/libs

# 2. Escribir local.properties (no se committea) con tu SDK, Python y wheel
#    sdk.dir=/ruta/Android/Sdk
#    buildPython=/usr/bin/python3
#    ytdlWheel=/ruta/youtube_dl-2025.4.7-py2.py3-none-any.whl

# 3. Compilar
gradle :app:assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease -x lintVitalRelease   # firmado, ~20 MB, arm64-v8a
```

## Firma de release

`assembleRelease` firma con la clave de debug salvo que `local.properties` tenga las cuatro claves
de la firma propia; en ese caso usa esa:

```
keystoreFile=/ruta/selenet-release.jks
keystoreStorePassword=...
keystoreAlias=selenet
keystoreKeyPassword=...
```

La clave **no se committea**: es lo que autoriza las actualizaciones. Si se pierde, la única forma
de instalar una versión nueva es desinstalar la app (y se borra el historial).

---

## Créditos y licencia

Este proyecto es posible gracias a otros proyectos; se dejan acá por respeto y para que quede clara
la procedencia de cada parte:

- **[youtube-dl](https://github.com/ytdl-org/youtube-dl)** — el motor de descarga, creado y
  mantenido por **ric920609** y los contribuidores de `ytdl-org`. Todo el trabajo pesado de
  extraer links y formatos es de ese proyecto. Publicado como *Unlicense* (dominio público).
- **[HUEVOMAN77/youtube-dl](https://github.com/HUEVOMAN77/youtube-dl)** — fork del motor, sobre
  el commit `956b8c5855` (versión `2025.04.07`), con el parche de clientes de YouTube.
- **ydl-android** — el nombre original de este mismo repositorio
  ([HUEVOMAN77/Selenet](https://github.com/HUEVOMAN77/Selenet)), creado por **HUEVOMAN77**.
  Las versiones de prueba quedaron etiquetadas como `v6` a `v11`; Selenet 1.0 es la continuidad
  oficial de ese proyecto.
- **[Chaquopy](https://chaquo.com/chaquopy/)** (Apache 2.0) — mete Python dentro del APK.
- **[Android Jetpack / AndroidX](https://developer.android.com/jetpack)** y
  **[Material Components for Android](https://github.com/material-components/material-components-android)**
  (Apache 2.0) — la interfaz.
- Los íconos de la interfaz vienen de **Material Icons** (Google, Apache 2.0); el ícono del
  lanzador es propio.

El código de Selenet (esta app) se publica bajo la **Unlicense**, la misma licencia del motor,
para no agregar restricciones sobre software que ya era de dominio público: está en `LICENSE`.
El detalle de cada dependencia y el aviso de uso están en `NOTICE`. Cada librería mencionada
arriba sigue bajo su propia licencia.

**Aviso:** Selenet es una herramienta técnica. Bajá solo contenido que puedas bajar: respetá los
términos de servicio del sitio y los derechos del autor.

---

## Limitaciones conocidas

- Sin ffmpeg embebido: las calidades que vienen con video y audio por separado (1440p, 2160p,
  o cualquier DASH) se bajan en la PC con el modo híbrido.
- `--update` de youtube-dl no aplica dentro del APK: el código vive adentro; por eso el botón
  **Actualizar motor** reemplaza el paquete Python completo.
- Solo `arm64-v8a`. Para `armeabi-v7a` o emulador x86 hay que cambiar `abiFilters` y recompilar.
- El techo de calidad es 1080p porque es la última resolución que YouTube entrega con audio
  incluido en el mismo archivo.
