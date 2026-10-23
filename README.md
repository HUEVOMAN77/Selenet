# Selenet

**Descarga de video y audio directamente en el dispositivo, con el motor `youtube-dl` embebido.**

| | |
|---|---|
| **Producto** | Selenet (anteriormente `ydl-android`) |
| **Versión publicada** | 1.0 — primera versión oficial y estable |
| **Identificador de paquete** | `com.fork.ytdl` |
| `versionCode` / `versionName` | 10000 / 1.0 |
| **Plataforma** | Android 7.0 (API 24) o superior, `arm64-v8a` |
| **Tipo de aplicación** | `application` nativa, Java 17, sin dependencias de servidor propio |
| **Motor de extracción** | `youtube-dl` 2025.04.07 (fork parcheado) sobre CPython 3.12 embebido |
| **Licencia** | Unlicense (dominio público), misma del motor — ver `LICENSE` y `NOTICE` |
| **Artefacto** | `Selenet-1.0.apk` · 20.485.673 bytes · SHA-256 `0935d068…b05e` |
| **Estado del proyecto** | En mantenimiento activo · sin fines comerciales · no afiliado a YouTube |

---

## Índice

1. [Resumen ejecutivo](#1-resumen-ejecutivo)
2. [Origen y continuidad del proyecto](#2-origen-y-continuidad-del-proyecto)
3. [Alcance de la versión 1.0](#3-alcance-de-la-versión-10)
4. [Funcionalidades](#4-funcionalidades)
5. [Arquitectura técnica](#5-arquitectura-técnica)
6. [Especificación de calidad y formatos](#6-especificación-de-calidad-y-formatos)
7. [Historial e indexación de archivos](#7-historial-e-indexación-de-archivos)
8. [Especificación de almacenamiento](#8-especificación-de-almacenamiento)
9. [Seguridad, privacidad y permisos](#9-seguridad-privacidad-y-permisos)
10. [Fiabilidad y manejo de errores](#10-fiabilidad-y-manejo-de-errores)
11. [Interfaz y sistema de diseño](#11-interfaz-y-sistema-de-diseño)
12. [Artefacto de publicación](#12-artefacto-de-publicación)
13. [Matriz de compatibilidad](#13-matriz-de-compatibilidad)
14. [Calidad y verificación](#14-calidad-y-verificación)
15. [Compilación reproducible](#15-compilación-reproducible)
16. [Configuración persistente](#16-configuración-persistente)
17. [Solución de problemas](#17-solución-de-problemas)
18. [Hoja de ruta](#18-hoja-de-ruta)
19. [Créditos, licencia y gobernanza](#19-créditos-licencia-y-gobernanza)
20. [Documentación relacionada](#20-documentación-relacionada)

---

## 1. Resumen ejecutivo

Selenet es una aplicación Android que ejecuta el motor de `youtube-dl` **dentro del teléfono** y
permite descargar video o audio en una carpeta elegible por el usuario, sin pasar por servidores
intermedios, sin root, sin servicios de terceros y sin `ffmpeg`. El enlace se resuelve en el
dispositivo, el archivo se escribe en el almacenamiento del dispositivo y el resultado queda
indexado para cualquier otra app de la galería o el reproductor.

La aplicación está orientada a tres casos de uso concretos: **guardar contenido para verlo u
oír sin conexión**, **obtener solo la pista de audio en `.m4a`** y **controlar resolución, destino
y reintento de cada descarga**. Para eso expone una cola persistente que sobrevive al cierre de la
interfaz, un selector de calidad determinista de 144p a 1080p, un historial que lee los archivos
reales del sistema de archivos (no solo la memoria de la app) y un canal de actualización del motor
para que los cambios de los sitios se puedan resolver sin recompilar la app.

**Qué no es Selenet:** no es un servicio de streaming, ni un conversor web, ni una herramienta de
descarga masiva o automatizada, ni un cliente no oficial de YouTube. No reproduce, edita ni
transmite archivos que no haya descargado el propio usuario, y no almacena cuentas, cookies ni
sesiones de inicio de sesión.

La 1.0 es la primera versión que el proyecto considera **oficial**: las anteriores (`v6` a `v11`)
fueron entregas de pruebas internas. Todas las versiones comparten la misma clave de firma, de
modo que la actualización se instala encima de la anterior conservando cola e historial.

---

## 2. Origen y continuidad del proyecto

El repositorio nació como **`ydl-android`**, un experimento de **HUEVOMAN77** que ya tenía en
marcha lo más difícil del problema: CPython dentro del APK mediante Chaquopy, el wheel de
`youtube-dl` empaquetado, un servicio en primer plano con cola persistente y un selector de
formatos básico. El proyecto quedó detenido en una etapa de pruebas: la base funcionaba, pero le
faltaba todo lo necesario para usarla a diario.

Selenet es la **continuidad oficial** de ese trabajo. Se conservó la arquitectura y la forma de
descargar original —de ahí que el identificador de paquete siga siendo `com.fork.ytdl`, elección
deliberada para no crear una app distinta ni perder la base instalada— y se reconstruyeron por
dentro el motor de selección de calidad, el almacenamiento, el historial, la interfaz y el manejo
de errores.

| Hito | Contenido | Estado |
|---|---|---|
| `v6` – `v8` | Pruebas del motor embebido, firma real, cola y correcciones de `HTTP 403` | Entregas internas |
| `v9` | Modo **solo audio (`.m4a`)** sin `ffmpeg`: extracción con `MediaExtractor`/`MediaMuxer` | Entregas internas |
| `v10` | Identidad visual nueva: tema Material 3, degradado azul→verde agua e icono adaptativo con variante monocroma | Entregas internas |
| `v11` | Historial con los archivos reales del dispositivo, carpeta de destino funcional y escalera de calidad de 144p a 1080p | Entregas internas |
| **1.0** | Nombre oficial **Selenet**, documentación técnica completa, revisión de exactitud y estabilización | **Oficial y estable** |

---

## 3. Alcance de la versión 1.0

### 3.1 Dentro del alcance

| Área | Capacidad entregada | Estado |
|---|---|---|
| Ingesta de enlaces | Pegado manual, botón **Compartir** de cualquier app, oferta desde el portapapeles con confirmación | Operativo |
| Cola | Persistente, secuencial, con pausa, reanudación, reintento y cancelación | Operativo |
| Ejecución en segundo plano | Servicio en primer plano con canal de notificación y acciones | Operativo |
| Video | 144p · 240p · 360p · 480p · 720p · 1080p · Mejor (automático), en un solo archivo | Operativo |
| Audio | `.m4a` real extraído en el dispositivo, seis niveles ligados a la resolución | Operativo |
| Destino | Carpeta elegida por el usuario con permiso persistente; por defecto `Movies/Selenet` | Operativo |
| Historial | Unión de base de datos y exploración del sistema de archivos, con miniatura, tamaño y fecha | Operativo |
| Operaciones sobre archivos | Abrir, compartir, volver a descargar, borrar con confirmación | Operativo |
| Motor | Actualización de `youtube-dl` desde GitHub sin recompilar, con verificación de compatibilidad y marcha atrás | Operativo |
| Diagnóstico | Registro técnico del motor opcional y captura de fallos en disco | Operativo |
| Interfaz | Material 3 oscuro, tres pestañas, icono adaptativo con modo monocroma | Operativo |

### 3.2 Fuera del alcance en la 1.0

| Elemento | Situación real | Motivo |
|---|---|---|
| **DASH de video y audio separados** | Se descargan y se unen en el dispositivo (1080p y las alturas mayores cuando vienen en MP4); se marcan como *Requiere PC* solo las de VP9/AV1 con audio Opus | La unión la hace `MediaExtractor`/`MediaMuxer` con pistas AVC/HEVC + AAC; VP9 y AV1 no tienen lugar en un MP4 |
| **Modo híbrido con la PC** | **No implementado.** El único rastro es un interruptor **deshabilitado** en Ajustes → Avanzado, que no tiene código asociado | Marcador de posición de una función planificada (ver [hoja de ruta](#18-hoja-de-ruta)); no debe presentarse como disponible |
| Listas de reproducción | No soportado (`noplaylist: true` en el motor) | Mantener una descarga por tarea, cola previsible |
| Descarga concurrente | No: un único hilo de trabajo | Evita saturar el enlace y simplifica la reanudación |
| Subtítulos, metadatos ID3, portadas | No soportado | Fuera del objetivo de la 1.0 |
| Contenido con DRM | No soportado | El motor no lo maneja |
| `armeabi-v7a` / `x86` | No compilado (`abiFilters arm64-v8a`) | Reducir tamaño y superficie de prueba |
| Distribución en Google Play | No publicada | Distribución por release con APK firmado |

---

## 4. Funcionalidades

### 4.1 Ingesta del enlace

Tres caminos, todos validados antes de encolar:

- Campo de texto con pegado manual.
- Menú **Compartir** de Android: la activity principal declara `android.intent.action.SEND` con
  `text/plain` y modo de lanzamiento `singleTask`, de modo que compartir dos veces no abre dos
  instancias.
- **Oferta desde el portapapeles.** Al abrir la pantalla de descargas, si hay texto con aspecto de
  enlace, se muestra un diálogo con el enlace completo y las opciones **Pegar** / **Descartar**.
  La app nunca vuelca el portapapeles en la cola sin confirmación explícita.

Un enlace que no parece una URL se rechaza con `Eso no parece un link.` y no se agrega nada.

### 4.2 Cola y ejecución

| Propiedad | Valor |
|---|---|
| Modelo de ejecución | Un hilo de trabajo (`ydl-worker`), procesamiento secuencial |
| Estados de tarea | `queued` · `running` · `paused` · `done` · `error` |
| Etapas de una tarea | `download` (0 %→90 %) · `extract` (90 %→100 %, solo modo audio) |
| Persistencia de la cola | `SharedPreferences` `cola`, clave `tasks` (JSON) |
| Servicio | `DownloadService`, `exported="false"`, `foregroundServiceType="dataSync"` |
| Notificación | Canal `descargas`, identificador `4711`, acciones *Pausar todo* / *Reanudar todo* |
| Reintentos de red | 3 intentos con espera progresiva de 2 s, 5 s y 10 s |
| Techo de progreso del motor | 90 % mientras descarga; el tramo final se reserva a la extracción de audio |
| Búsqueda en cola | Filtra por título o enlace, insensible a tildes y mayúsculas (`Normalizer` NFD) |

La barra de progreso es honesta en el modo audio: durante la extracción muestra
`Extrayendo audio…` en lugar de fingir una descarga que ya terminó.

### 4.3 Modos de descarga

| Modo | Comportamiento |
|---|---|
| **Todos** | Lee los formatos reales del video y los lista de menor a mayor, marcando con `✓` la calidad recordada de Ajustes |
| **Solo MP4** | Usa la calidad de video guardada en Ajustes; si allí hubiera una calidad de audio, cae a *Mejor (automático)* para no devolver nunca un archivo sin video |
| **Solo audio** | Usa la calidad de audio guardada en Ajustes y ejecuta el pipeline de extracción `.m4a` |

### 4.4 Sin `ffmpeg`: extracción de audio y unión de pistas

1. El motor baja el MP4 progresivo elegido a la caché privada `cache/audio` del dispositivo.
2. `MediaExtractor` selecciona la pista de audio y `MediaMuxer` la escribe como `.m4a` real en
   `destino/nombre.m4a.part`.
3. Al terminar se renombra al nombre definitivo (con sufijo ` (1)`, ` (2)`… si está tomado) y se
   registra en `MediaStore` como audio musical.
4. Si la extracción falla, la descarga del MP4 temporal queda disponible y aparece el botón
   **Guardar el MP4 completo**, con el audio ya incluido.

El archivo temporal nunca se ve en la carpeta del usuario. La limpieza de la caché respeta las
tareas activas.

La unión de pistas usa la misma caché y las mismas APIs, pero con los dos archivos que entrega
el sitio cuando la calidad no viene en un solo contenedor:

1. El motor baja la pista de video suelto (`137`, `313`… sin audio) a `cache/audio`; la barra
   llega hasta el 60 %.
2. Después baja la pista de audio `.m4a` emparejada; hasta el 70 %.
3. `MediaExtractor` lee las muestras de cada pista y `MediaMuxer` las escribe interleadas por
   marca de tiempo en `destino/nombre.mp4.part`; de ahí al 100 % y se renombra al definitivo.
4. No se decodifica ni se recodifica nada: el video y el audio son los bytes exactos del sitio.

Si alguna pista no se puede leer o el contenedor no la acepta, la tarea queda con el motivo
(`merge` o `no_video`) y se borran los dos temporales. Las opciones que el teléfono no puede
unir (VP9/AV1 con Opus) siguen listadas como *Requiere PC* y no se inician. La unión depende de
que el reproductor de Android lea el MP4 que entregó el sitio: los MP4 fragmentados requieren
Android 9 o superior.

### 4.5 Historial, destino, motor y diagnóstico

Se describen en detalle en las secciones [7](#7-historial-e-indexación-de-archivos),
[8](#8-especificación-de-almacenamiento) y [10](#10-fiabilidad-y-manejo-de-errores), porque son
especificaciones, no listas de funciones.

### 4.6 Interfaz

Ver [sistema de diseño](#11-interfaz-y-sistema-de-diseño).

---

## 5. Arquitectura técnica

### 5.1 Diagrama de componentes

```
                        ┌──────────────────────────────────────────────┐
   Intent SEND ──────▶  │  MainActivity  ·  navegación inferior        │
   Portapapeles ─────▶  │  DownloadFragment │ HistoryFragment │ Settings│
                        └───────────┬──────────────────┬───────────────┘
                                    │ encolar          │ leer historial
                                    ▼                  ▼
                        ┌───────────────┐   ┌──────────────────┐
                        │ DownloadSvc   │   │ Library ─┬─► HistoryDb (SQLite)
                        │ (Foreground)  │◀──┤          ├─► exploración de carpetas
                        │ 1 hilo        │   │          └─► árbol SAF elegido
                        └───┬───────┬───┘   └──────────────────┘
                            │       │
              JNI/Chaquopy  │       │  MediaExtractor/MediaMuxer
                            ▼       ▼
                    ┌───────────┐ ┌────────────┐   ┌───────────────┐
                    │ backend.py│ │ AudioRemux │   │   Storage     │
                    │ youtube-dl│ └────────────┘   │ rutas · SAF · │
                    │ 2025.04.07│                  │ MediaStore ·  │
                    └─────┬─────┘                  │ FileProvider  │
                          │ HTTP(S)                └───────┬───────┘
                          ▼                                ▼
                    sitios de origen                 carpeta del usuario
```

### 5.2 Reparto del código

| Capa | Componentes | Líneas |
|---|---|---|
| Interfaz Android (Java) | 21 clases: `MainActivity`, 3 fragmentos, 2 adaptadores, `Task`, `Quality`, `Formats`, `Errors`, `Logs`, `Crash`, `App` | 4.287 |
| Servicios de datos y sistema (Java) | `DownloadService`, `Storage`, `Library`, `HistoryDb`, `Thumbs`, `Engine`, `Motor`, `AudioRemux` | incluidas arriba |
| Backend Python | `backend.py` (extracción, selección de formato, clasificación de errores, carga de motor externo) | 598 |
| Pruebas Python | `test_backend.py` (17 casos) | 253 |
| Recursos | 6 layouts, 27 drawables, 71 colores, 154 cadenas, 5 densidades de icono, `file_paths.xml` | 54 archivos |
| Parche del motor | `app/patches/youtube-player-clients.patch` | 30 |

El proyecto es **Java puro**: no hay Kotlin ni coroutines. La concurrencia se resuelve con
`Thread`, `ExecutorService` y `Handler(Looper.getMainLooper())` para publicar en la interfaz.

### 5.3 Modelo de hilos

| Componente | Ejecutor | Uso |
|---|---|---|
| `DownloadService` | 1 hilo `ydl-worker` | Cola secuencial; notifica a los tres fragmentos por el hilo principal |
| `DownloadFragment` / `HistoryFragment` / `SettingsFragment` | 1 `newSingleThreadExecutor` cada uno | Lectura de formatos, exploración de archivos y actualización del motor |
| `Thumbs` | `newFixedThreadPool(2)` | Descarga y decodificación de miniaturas |
| `backend.py` | Hilo de Python dentro del servicio | Ejecuta el motor y reenvía progreso por `progress_hooks` |

### 5.4 Persistencia

| Almacén | Contenido |
|---|---|
| `SharedPreferences` `cola` | Cola serializada en JSON |
| `SharedPreferences` `ajustes` | Calidad de video y audio, etiquetas, carpeta elegida, ruta real, visibilidad del registro |
| SQLite `historial` v2, tabla `descargas` | `id`, `url`, `title`, `fmt`, `label`, `path`, `bytes`, `finished_at` |
| Archivos privados | `fallo.txt` (hasta 128 KiB), `cache/audio`, carpeta del motor descargado |
| Almacenamiento público | Los medios descargados, anunciados a `MediaStore`/`MediaScanner` |

---

## 6. Especificación de calidad y formatos

### 6.1 El problema de fondo

El fork de `youtube-dl` que usa la app **no ordena los formatos**: en `YoutubeDL.py` sigue en pie
el `# TODO Central sorting goes here`, por lo que `best` equivale al **último** elemento
de la lista del extractor y `worst` al primero, sin relación con la calidad real. Cualquier escalera
que dependa de `best`/`worst` a secas es indefinida.

### 6.2 Solución: escalera determinista

Cada preset de resolución empieza con los *itag* progresivos históricos de YouTube ordenados por
coste de desviación respecto de lo pedido —quedarse corto cuesta menos que pasarse (pasar el techo
multiplica el error por 3 y suma 1)— y termina con filtros que imponen el **techo de altura** como
red para el resto de los sitios. Los predicados `vcodec!=none` y `acodec!=none` garantizan que el
formato traiga video **y** audio en el mismo archivo, es decir, que nunca hará falta mezclar.

Presets de **video** (prefijo de la escalera, calculado con `Quality.ladder`):

| Preset | Escalera de itag | Cola de filtros |
|---|---|---|
| 144p | `17/36/18/59/22/37/` | `best[ext=mp4][vcodec!=none][acodec!=none][height<=144]` → `best[vcodec!=none][acodec!=none][height<=144]` → `best[ext=mp4]` → `best` |
| 240p | `36/17/18/59/22/37/` | idéntica con `height<=240` |
| 360p | `18/36/17/59/22/37/` | idéntica con `height<=360` |
| 480p | `59/18/36/17/22/37/` | idéntica con `height<=480` |
| 720p | `22/59/18/36/17/37/` | idéntica con `height<=720` |
| 1080p | `37/22/59/18/36/17/` | idéntica con `height<=1080` |
| Mejor (automático) | — | `best` (última opción del extractor, sin techo) |

Presets de **solo audio**: misma escalera, y la cola sustituye los dos últimos términos por
`worstaudio[ext=m4a]` → `bestaudio[ext=m4a]` → `worstaudio` → `bestaudio` → `best`. El marcador
`Quality.isAudio` se apoya en la presencia de `worstaudio`, que identifica el pipeline de extracción.

### 6.3 Referencia de formatos

| Preset | *itag* prioritario | Resolución | Contenedor | Video | Audio nominal |
|---|---|---|---|---|---|
| 144p | 17 | 176×144 | 3GP | mp4v | AAC ~24 kbps |
| 240p | 36 | 320×240 | 3GP | mp4v | AAC variable |
| 360p | 18 | 640×360 | MP4 | H.264 | AAC ~96 kbps |
| 480p | 59 | 854×480 | MP4 | H.264 | AAC ~128 kbps |
| 720p | 22 | 1280×720 | MP4 | H.264 | AAC ~192 kbps |
| 1080p | 37 | 1920×1080 | MP4 | H.264 | AAC ~192 kbps |

Datos tomados de la tabla `_formats` del propio motor empaquetado. En el modo **solo audio** el
sonido sale del mismo archivo progresivo, por eso subir la resolución también sube el bitrate
audio: es la única palanca disponible sin `ffmpeg`.

### 6.4 Garantias y comportamiento verificado

- **Techo real:** ningún formato elegido supera la altura pedida cuando existe alternativa dentro
  del techo; la verificación offline resuelve 144→17, 240→36, 360→18, 480→59, 720→22, 1080→37.
- **Sin mezcla:** todo preset exige video y audio juntos. Arriba de 1080p la app marca
  *Requiere PC* y no inicia la descarga.
- **Sitios distintos de YouTube:** cuando no hay itag clásico, la escalera cae a los filtros de
  altura; se validó con un conjunto de formatos moderno que la selección queda en 18 o 22 de forma
  determinista según el techo pedido.
- **Selector validado offline:** cada spec se compiló con `YoutubeDL.build_format_selector` contra
  listas de formatos sintéticas antes de publicarse.

### 6.5 Selector por video

En el modo **Todos**, `backend.pick` devuelve hasta 6 filas de video —primero los progresivos, uno
por altura, ordenados de mayor a menor, y después las alturas que solo existen con pistas
separadas— más 3 filas de audio. `Formats` reordena la lista en la interfaz: video → requiere
mezcla → audio y, dentro de cada grupo, de menor a mayor resolución. Las filas que necesitan
mezcla se rotulan *Requiere PC* y, al tocarlas, la app explica que esa calidad viene sin audio y
no se puede descargar aquí.

---

## 7. Historial e indexación de archivos

El historial **no es un registro de la app**, es una vista consolidada de lo que existe en el
dispositivo.

| Paso | Detalle |
|---|---|
| 1. Base de datos | Filas de `historial/descargas` cuyo archivo sigue existiendo |
| 2. Exploración | Carpetas de búsqueda en orden: destino elegido, `Movies/Selenet`, `Movies/YTDL Fork` (legado, solo lectura) y `Android/data/…/downloads` |
| 3. Árbol SAF | Se recorre el documento raíz elegido únicamente cuando la ruta real no está disponible |
| 4. Deduplicación | Por `Storage.realPath`; la fila de la base gana y la entrada escaneada se descarta |
| 5. Orden y tope | Por fecha de finalización descendente, máximo **300** elementos (`Library.LIMIT`) |

Las entradas solo-visitas-archivos reciben un identificador sintético `archivo:<ruta>`, campo
`url` vacío y estado *completada*; en ellas se oculta **Volver a descargar** porque no hay enlace
del que partir.

**Miniaturas.** Con enlace se toma `i.ytimg.com/vi/<id>/mqdefault.jpg`; sin enlace se extrae un
fotograma local del propio archivo con `MediaMetadataRetriever`. Caché `LruCache` de 6 MiB
dimensionada por `getByteCount()`, reescalado a 240 px de lado, 2 hilos, tiempos de espera de
6 s (conexión) y 8 s (lectura), tope de 2 MiB por imagen y lista de 200 claves fallidas para no
insistir sobre un archivo roto. Las pistas de audio usan un icono dedicado.

**Operaciones:** abrir, compartir, volver a descargar, borrar (con diálogo de confirmación que
nombra el archivo y advierte de que no es recuperable) y copiar la ruta. Si `FileProvider` no
puede exponer el archivo, se informa en lugar de fallar en silencio.

---

## 8. Especificación de almacenamiento

### 8.1 Selección del destino

| Condición | Dónde escribe el motor |
|---|---|
| Carpeta elegida con ruta real escribible | Esa carpeta, directamente |
| Carpeta elegida sin ruta real (SD u otro proveedor) | `files/staging` y publicación por `DocumentsContract.createDocument` |
| Sin carpeta elegida y `Movies/Selenet` escribible | `Movies/Selenet` |
| Ninguna anterior | `Android/data/com.fork.ytdl/files/downloads` → `files/downloads` |

`Storage.writable()` lo comprueba creando y borrando un archivo centinela `.write`; si la carpeta
deja de ser escribible, la app **vuelve al destino por defecto** en vez de fallar en cada descarga.
`chosenDir()` lo verifica en cada uso, no solo al elegir.

### 8.2 Elección por el usuario

- Selector del sistema `OpenDocumentTree` (SAF) con `takePersistableUriPermission`, de modo que la
  preferencia sobrevive al reinicio.
- Si el árbol pertenece al almacenamiento primario, `DocumentsContract.getTreeDocumentId` y el
  prefijo `primary:` se convierten a ruta real: escribir derecho es más fiable que copiar después.
- Antes de guardar la elección se valida la escritura; si falla, la interfaz revierte al valor
  anterior y avisa.
- Mantener pulsado el botón restablece la carpeta de la app.

### 8.3 Nombre y colisiones

| Regla | Comportamiento |
|---|---|
| Caracteres inválidos | `/ \ : * ? " < > |` y controles se sustituyen por `_` |
| Puntos iniciales / finales | Se recortan; si el nombre queda vacío se usa `audio` |
| Longitud | Base limitada a 100 caracteres |
| Colisiones | Primer nombre libre: `base.ext`, `base (1).ext`, … hasta `(999)` |
| Escrito a medias | Se genera con sufijo `.part` y se renombra al final; **Limpiar cortes** barre los huérfanos |
| Cambio de volumen | `renameTo` con copia de verificación de longitud y borrado del origen |

### 8.4 Indexación multimedia

- Cada archivo publicado se anuncia a `MediaScannerConnection` con su MIME real.
- El `.m4a` del modo audio se inserta además en `MediaStore.Audio` (`IS_MUSIC`, título, ruta),
  evitando duplicados con una consulta previa por `_data`; si el *insert* no es posible, se
  degrada al escáner.
- Extensiones reconocidas — video: `mp4`, `m4v`, `mkv`, `webm`, `mov`, `avi`, `3gp`;
  audio: `m4a`, `mp3`, `aac`, `ogg`, `opus`, `wav`, `flac`.

### 8.5 Exposición a otras apps

`FileProvider` con autoridad `com.fork.ytdl.archivos`, `exported="false"` y
`grantUriPermissions="true"`. Los paths declarados cubren almacenamiento externo, volúmenes
montados, archivos externos e internos de la app y caché, que es lo que hace funcionar **Abrir** y
**Compartir** también con carpetas elegidas por SAF y tarjetas SD. Las rutas reales nunca se
exponen a otras aplicaciones: se entregan URIs temporales.

---

## 9. Seguridad, privacidad y permisos

### 9.1 Permisos declarados

| Permiso | Para qué | Nota |
|---|---|---|
| `INTERNET` | Que el motor consulte los sitios y baje los archivos | Único imprescindible |
| `WAKE_LOCK` | Que el CPU no duerma durante una descarga larga | |
| `FOREGROUND_SERVICE` | Que la cola siga funcionando con la interfaz cerrada | Servicio `dataSync`, no exportado |
| `WRITE_EXTERNAL_STORAGE` | Escribir en `Movies/Selenet` y registrar en `MediaStore` | Innecesario si el destino se elige por SAF |

Se hereda además `com.fork.ytdl.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, generado por AndroidX.
**No se pide** acceso a la ubicación, contactos, cámara, micrófono ni a archivos ajenos al destino.

### 9.2 Contactos de red salientes

| Destino | Cuándo | Contenido enviado |
|---|---|---|
| Sitio del enlace | En cada extracción y descarga | La URL y lo que el propio extractor necesite |
| `codeload.github.com` | Solo al pulsar **Actualizar motor** | Solicitud del tarball de `youtube-dl` |
| `i.ytimg.com` | Al pintar una miniatura con enlace conocido | Identificador del video |

No hay telemetría, ni análisis de uso, ni publicidad, ni SDKs de terceros en las dependencias
(compile-time: AndroidX AppCompat/Fragment/RecyclerView y Material Components). La app no envía
los enlaces a ningún servidor del proyecto, porque no existe ninguno.

### 9.3 Decisiones con coste de seguridad, declaradas

| Decisión | Motivo | Consecuencia y mitigación |
|---|---|---|
| `targetSdk = 28` | API 29+ restringe la ejecución de binarios desde el directorio de datos y obliga a migrar a almacenamiento por alcance; la app depende de CPython embebido y de escribir rutas reales | La app queda exenta de las reglas de *scoped storage* y de los tipos de servicio en primer plano de API 34+. Es una decisión de compatibilidad, no un descuido: **debe revisarse** antes de aspirar a Google Play |
| `usesCleartextTraffic="true"` | Con API 28 el texto claro está bloqueado por defecto y parte de las URLs de medios de algunos sitios siguen servidas por HTTP | Riesgo real de interceptación en redes hostiles. Se acepta para no romper descargas; la trazabilidad queda en el registro técnico. La mitigación futura es un `network_security_config` por dominio |
| `requestLegacyExternalStorage="true"` | Coherente con el punto anterior | Sin efecto en API < 29 |
| `allowBackup="true"` | Conservar cola, ajustes e historial al reinstalar | El respaldo del sistema incluye datos privados de la app; conviene desactivarlo si se añaden credenciales |
| Actualización del motor sin firma de código | Permite arreglar cambios de los sitios sin recompilar | La integridad depende de TLS con GitHub y de la revisión del paquete. La app **rechaza** motores que no traigan el parche de clientes y siempre conserva el del APK como reserva |
| Sin *certificate pinning* | El motor consume cientos de dominios distintos | Se confía en el almacén de certificados del sistema y en `cacert.pem` de Python para el canal del motor |

### 9.4 Datos y credenciales

- No hay cuentas, tokens, cookies ni sesiones: el código no utiliza el parámetro de cookies del
  motor, así que el contenido restringido por inicio de sesión se rechaza con un mensaje claro.
- El portapapeles se lee solo en la pantalla de descargas y solo para ofrecer un pegado
  confirmado por el usuario.
- El registro técnico y `fallo.txt` se guardan en el almacenamiento privado; el registro está
  **oculto por defecto** y hay que activarlo en Ajustes.
- La firma de release se mantiene fuera del repositorio, en un `local.properties` ignorado por git.

### 9.5 Integridad de la distribución

El único canal de actualización de la app es GitHub Releases, con el SHA-256 publicado en la
misma página del release. La clave de firma es la misma desde las versiones de prueba, lo que
impide que un tercero publique una actualización suplantando la app instalada sin esa clave.

---

## 10. Fiabilidad y manejo de errores

### 10.1 Política de reintento

| Nivel | Parámetro | Valor |
|---|---|---|
| Motor | `retries` | 0 (el reintento lo decide Java, no el motor) |
| Motor | `fragment_retries` | 3 en la primera intentación, 1 en las siguientes |
| Motor | `socket_timeout` | 30 s (20 s al listar formatos) |
| Motor | `continuedl` | Activado; se desactiva al cambiar de cliente, porque el corte previo no sirve |
| Motor | `concurrent_fragment_downloads` | 2 |
| Java | `Task.MAX_RETRIES` | 3 para fallos de red |
| Java | Espera | 2 s → 5 s → 10 s |
| Específico de YouTube | Rotación de clientes | `android_sdkless` → `tv_downgraded` → `tv` → `web_safari`, reintentando ante `HTTP 403` |

### 10.2 Taxonomía de errores

| Código interno | Mensaje al usuario | Acción recomendada |
|---|---|---|
| `private` | El video es privado o pide inicio de sesión | No hay solución desde la app |
| `unavailable` | No está disponible (borrado, restringido por región o edad) | Probar otro enlace |
| `unsupported` | Ese sitio no lo soporta el motor | Actualizar el motor |
| `network` | Sin conexión o se cortó; se reintenta solo | Esperar |
| `forbidden` | YouTube bloqueó la descarga (403) | Cambiar de calidad, esperar unos minutos o actualizar el motor |
| `space` | Almacenamiento lleno | Liberar espacio |
| `format` | Ningún formato descargable sin mezcla | Elegir otra calidad o usar el modo PC |
| `reintentos` | Se agotaron los 3 reintentos | Reintentar a mano |
| `sin_clientes` | El motor activo no sabe rotar clientes de YouTube | Volver al motor del APK |
| `no_audio` | Sin pista de audio compatible para `.m4a` | Usar el modo MP4 |
| `audio_vacio` | El `.m4a` quedó en 0 bytes | Usar el modo MP4 |
| `extraccion` | Bajó el video pero no se pudo separar el audio | **Guardar el MP4 completo** |
| `sin_destino` | No se pudo escribir en el destino | Revisar la carpeta en Ajustes |
| `cancel` | Descarga cancelada | — |
| otros | `Fallo: <detalle>` con copia del detalle técnico | — |

Toda tarea conserva su detalle técnico y el número de intentos, y puede copiarse desde la propia
tarjeta de error.

### 10.3 Observabilidad

- `Logs` mantiene un búfer por tarea y uno global, recortado a 400 líneas, con marca horaria.
- El registro en pantalla es opcional y vive detrás de un interruptor en Ajustes.
- `Crash` encadena el manejador de fallos del sistema: antes de morir escribe pila, causas
  encadenadas y registro global en `fallo.txt` (tope 128 KiB). Ajustes muestra
  *La app se cerró mal* con botones para copiar o borrar ese detalle.
- El botón **Limpiar cortes** elimina los `.part` huérfanos sin tocar las tareas activas.

---

## 11. Interfaz y sistema de diseño

Construida sobre `Theme.Material3.Dark.NoActionBar` con asignación explícita de los roles de
color de Material 3: primario, secundario, terciario, error, superficie en cinco niveles de
contenedor, variantes, colores inversos, `outline` y `scrim`. **71 tokens** en `colors.xml`,
**259 cadenas** en `strings.xml`, **17 layouts** y **39 drawables** vectoriales.

| Rol | Valor |
|---|---|
| Semilla / identidad | Verde agua `#19E3A5`, primario `#3EE8B0` |
| Secundario | Azul `#4C8DFF` |
| Acento informativo | Ámbar `#FFC95B` |
| Fondo | Degradado nocturno `#06131F` → `#0A3546` → `#0B6A58` |
| Superficie | `#0A1E2C` con contenedores translúcidos y filete de vidrio `#33FFFFFF` |
| Estados | Activo verde, completo verde claro, aviso ámbar, error rojo suave, inactivo azul grisáceo |

Escala de formas: 14 dp en componentes pequeños, 22 dp en medianos y 30 dp en tarjetas y en la
hoja inferior. Barra de estado transparente con la app dibujando detrás de ella, para que el
degradado llegue continuo hasta arriba; la de navegación conserva el color de la barra inferior.
Navegación inferior de tres destinos con indicador activo; control de modo por
`MaterialButtonToggleGroup`; selección de calidad en una **hoja modal** (`FormatSheet`, una fila
por opción con la resolución, el tamaño y si el teléfono la arma sola) y carpeta en diálogo de
Material.

**Capa de color y movimiento (1.4).** `DynamicColors.applyToActivitiesIfAvailable()` lleva la
paleta del fondo de pantalla a las superficies en Android 12 y superiores, sin mover el menta de
los botones de acción: las tarjetas usan `?attr/colorSurfaceContainer*` y solo las cargas
mantienen el color de marca. Detrás de todo, `AmbientGlow` dibuja dos halos que respiran a unos
11 fps y se pausan solos cuando la vista deja de estar visible. El movimiento está centralizado
en `Motion`: fundido a través de Material 3 entre pestañas, eje compartido en Z entre
`MainActivity` y `SearchActivity`, hundido de la fila al tocarla y entrada escalonada de las
listas; `Motion.calm()` apaga la animación de cambio de los `RecyclerView`, que era lo que hacía
parpadear la cola con cada refresco de progreso. Todo se salta cuando el sistema tiene las
animaciones desactivadas (`ValueAnimator.getDurationScale()` y
`TRANSITION_ANIMATION_SCALE`). En la fila activa, `Sparkline` dibuja las últimas 40 muestras de
la tasa real de bajada junto al texto *velocidad · quedan*; los números usan ancho tabular para
no desplazarse, y el aviso del motor se pinta como máximo dos veces por segundo.

Los avisos de agregar a la cola son barras de Material con **Deshacer** (un enlace, varios o una
lista entera), apoyadas en `DownloadService.enqueue()`, que devuelve la tarea, y
`DownloadService.withdraw()`, que la saca de la cola y corta el motor si ya había empezado.

El lanzamiento del registro técnico está oculto por defecto: la interfaz normal no muestra
consolas ni códigos.

**Icono:** adaptativo, con fondo completo pensado para el recorte circular, primer plano neutro y
capa `monochrome` para los temas monocromos de Android, más cinco densidades PNG para Android 7.x,
donde no existen los *adaptive icons*.

**Accesibilidad:** los controles primarios son botones y tarjetas de área amplia; 17 atributos
`contentDescription` cubren los elementos gráficos de los layouts de la app. No se ha documentado una
auditoría formal de accesibilidad (ver [lagunas de verificación](#143-lagunas-de-verificación)).

---

## 12. Artefacto de publicación

### 12.1 Identidad

| Campo | Valor |
|---|---|
| Archivo | `Selenet-1.0.apk` |
| Paquete | `com.fork.ytdl` |
| `versionCode` / `versionName` | 10000 / 1.0 |
| Etiqueta visible | `Selenet` |
| `minSdkVersion` / `targetSdkVersion` / `compileSdkVersion` | 24 / 28 / 35 |
| ABI | `arm64-v8a` (única) |
| Tamaño | 20.485.673 bytes (19,54 MiB) |
| Contenido descomprimido | 34,57 MiB en 965 entradas |

### 12.2 Composición

| Componente | Tamaño descomprimido | Detalle |
|---|---|---|
| `classes.dex` | 8,22 MiB | Código de la app y de AndroidX/Material. Sin ofuscación ni reducción (`minifyEnabled false`) |
| `lib/arm64-v8a` | 12,97 MiB | `libpython3.12.so` 6,39 · `libcrypto_python.so` 4,33 · `libsqlite3_python.so` 1,16 · `libssl_python.so` 0,92 · `libchaquopy_java.so` 0,15 |
| `assets/chaquopy` | 11,39 MiB | `stdlib-common.imy` 4,13 · `requirements-common.imy` 3,37 (el motor) · `stdlib-arm64-v8a.imy` 1,43 · nativos de arranque 1,60 · `bootstrap.imy` 0,57 · `cacert.pem` 0,23 · `app.imy` (backend) 0,02 |
| `resources.arsc` | 1,04 MiB | Tabla de recursos compilada |
| `res/` | 0,91 MiB | 875 entradas: layouts, drawables, iconos en 5 densidades |
| Otros | ~0,03 MiB | Metadatos de *builtins* de Kotlin que arrastra una dependencia (el código de la app no usa Kotlin) |

Cerca del **70 % del contenido descomprimido** es el runtime de Python y su biblioteca estándar, no
el código de Selenet. Es el coste de tener `youtube-dl` corriendo en el dispositivo.

### 12.3 Verificación del artefacto

| Digest | Valor |
|---|---|
| SHA-256 | `0935d0683c37a611c775dbe69e8fc52e78e5594f5a40df8aa8d408ffba12b05e` |
| MD5 | `0ca9f799b6a0d321f42cd618acdc949b` |

```sh
sha256sum Selenet-1.0.apk
apksigner verify --print-certs Selenet-1.0.apk
```

**Firma:** APK Signature Scheme **v2** (v1 desactivado por ser innecesario con `minSdk` 24).
Certificado `CN=HUEVOMAN77, OU=ydl-android, O=HUEVOMAN77, L=El Salvador, C=SV`
· huella SHA-256 `072384533e77b5deaa8b884c21376cced3edd1cfbc52ca0cd66b0198c8d7f5e7`
· clave pública `475149e9fdfd892e415eef39e20fa6eef015c631e4c694c3e36e47176ff4c6a6`.

### 12.4 Procedencia

El APK publicado se compiló desde el estado de código de `08c5c5c` («Selenet: nombre oficial de la
app y version 1.0»). El árbol `app/` y los archivos Gradle son **idénticos** en el etiquetado
`v1.0` (`e0ea3b9`) y en la cabecera posterior a esta documentación, con una única excepción: un
comentario de javadoc en `Quality.java` que repetía una afirmación obsoleta. Los comentarios no
afectan al bytecode, así que el artefacto publicado sigue correspondiendo a ese código. El resto de
los cambios posteriores afectaron solo a documentación (`README.md`, `LICENSE`, `NOTICE`, `docs/`).

```sh
git diff --stat 08c5c5c HEAD -- app/ build.gradle settings.gradle gradle.properties
```

---

## 13. Matriz de compatibilidad

| Sistema | Comportamiento |
|---|---|
| Android 7.0 – 7.1 (API 24–25) | Compatibilidad mínima. Sin icono adaptativo: se usan los PNG de densidad |
| Android 8.0 – 10 (API 26–29) | Pleno. Canal de notificación y foreground type operativos |
| Android 11 – 13 (API 30–33) | Pleno. Al escribir rutas reales con `targetSdk` 28 se conserva el acceso heredado; el destino por SAF cubre dispositivos con políticas restrictivas |
| Android 14 – 15 (API 34–35) | Instalación y uso esperados sin cambios relevantes por la diana 28. Sin validación propia en laboratorio (ver §14) |
| Android < 7.0 | No instalable |
| `armeabi-v7a`, `x86`, emulador x86 | No soportado: el APK solo lleva nativos `arm64-v8a` |
| Android Go / RAM limitada | Sin requisito declarado; la caché de miniaturas es fija (6 MiB), no depende de la densidad de pantalla |
| Tarjeta SD o almacenamiento portátil | Escaneo y publicación por SAF; no se garantizan rutas reales ni escritura directa |
| Google Play | No distribuido; la diana 28 no cumple los requisitos actuales de publicación |

---

## 14. Calidad y verificación

### 14.1 Automatizada

| Comprobación | Resultado |
|---|---|
| Pruebas del backend Python (`test_backend.py`) | **17 casos, todos correctos** (`python3 -m unittest app/src/main/python/test_backend.py`) |
| Compilación `assembleRelease` con Chaquopy y empaquetado del wheel | Correcta |
| Validación offline de los seis *specs* de calidad y del de audio | Determinista y dentro del techo pedido |
| `aapt2 dump badging` del artefacto | Paquete, versión, SDKs, ABI, permiso y etiqueta `Selenet` confirmados |
| `apksigner verify` | V2 válido con el certificado esperado |
| `zipalign -c -v 4` | Alineación verificada |
| Inspección del ZIP | Parche `youtube_player_clients` presente en el motor empaquetado; `file_paths.xml` y recursos corregidos |

### 14.2 Revisiones de exactitud de esta versión

Durante la documentación se auditaron las afirmaciones publicadas frente al código. Correcciones
aplicadas:

- El **modo híbrido con la PC** estaba descrito como una función disponible. No lo está: solo
  existe un interruptor deshabilitado sin implementación. Queda documentado como *fuera de alcance*
  y como elemento de la hoja de ruta.
- Un comentario interno de `Quality.java` repite esa misma afirmación obsoleta; se corrigió.
- Se verificó y documentó el techo real de 1080p, la rotación de clientes de YouTube y el
  comportamiento ante formatos que requieren mezcla.

### 14.3 Lagunas de verificación

- **Sin granja de dispositivos.** No se ejecutó una batería de pruebas en equipos reales con
  distintas versiones de Android ni con distintos fabricantes.
- **Sin instrumentadas ni de UI** (no hay pruebas Espresso ni de robustez).
- `lintVitalRelease` se excluye explícitamente del empaquetado (`-x lintVitalRelease`), por lo que
  no hay un informe de Lint limpio que respalde la publicación.
- Sin CI/CD: el proceso de compilación y firma está documentado pero es manual.
- Sin prueba de restauración del respaldo (`allowBackup`) ni de actualización desde cada versión de
  prueba.

### 14.4 Afirmaciones sin garantía explícita

Se mantienen tres afirmaciones deliberadamente sin garantías fuertes, y así se comunican:
instalación sobre versiones previas en todos los dispositivos, comportamiento en Android 14/15 y
rendimiento en tarjetas SD de baja velocidad.

---

## 15. Compilación reproducible

### 15.1 Requisitos

| Herramienta | Versión empleada en la 1.0 |
|---|---|
| JDK | 21 |
| Gradle | 8.10 (sin *wrapper*) |
| Android Gradle Plugin | 8.7.3 |
| SDK de compilación / mínimo / diana | 35 / 24 / 28 |
| Python del equipo de compilación | 3.12 |
| Chaquopy | 17.1.0 |
| Motor empaquetado | `youtube_dl-2025.4.7-py2.py3-none-any.whl` |
| Dependencias de interfaz | AppCompat 1.7.1 · Fragment 1.8.5 · RecyclerView 1.3.2 · Material 1.12.0 |

### 15.2 Pasos

```sh
# 1. Aplicar el parche al fork del motor y generar el wheel
cd ../youtube-dl && git apply ../Selenet/app/patches/youtube-player-clients.patch
python -m pip wheel . --no-deps --no-build-isolation -w ../Selenet/app/libs

# 2. Crear local.properties (ignorada por git)
#    sdk.dir=/ruta/Android/Sdk
#    buildPython=/usr/bin/python3
#    ytdlWheel=/ruta/youtube_dl-2025.4.7-py2.py3-none-any.whl

# 3. Compilar
gradle :app:assembleDebug
gradle :app:assembleRelease -x lintVitalRelease    # firmado, arm64-v8a
```

### 15.3 Firma

`assembleRelease` usa la clave de debug salvo que `local.properties` aporte las cuatro claves
propias:

```
keystoreFile=/ruta/selenet-release.jks
keystoreStorePassword=…
keystoreAlias=selenet
keystoreKeyPassword=…
```

El almacén **nunca** se comitea: es lo que autoriza las actualizaciones. Si se pierde, la única
manera de instalar una versión nueva es desinstalar la app, con la consiguiente pérdida de cola e
historial locales.

### 15.4 Notas de reproducibilidad

La compilación no está sellada contra el entorno: fechas de `MANIFEST.MF`, metadatos de Chaquopy y
el orden del `DEX` pueden variar entre máquinas, por lo que dos compilaciones del mismo commit no
garantizan el mismo hash. La verificación debe hacerse sobre el digest publicado del release, no
sobre una recompilación local.

---

## 16. Configuración persistente

| Archivo | Clave | Valor por defecto | Significado |
|---|---|---|---|
| `ajustes` | `calidad` | `best` | Spec de formato para video |
| `ajustes` | `calidad_label` | — | Etiqueta visible de la calidad de video |
| `ajustes` | `calidad_audio` | escalera «la más barata» | Spec de formato para solo audio |
| `ajustes` | `calidad_audio_label` | *Automática (la más barata)* | Etiqueta visible del audio |
| `ajustes` | `carpeta` | sin valor | URI del árbol SAF elegido |
| `ajustes` | `carpeta_real` | sin valor | Ruta resuelta del árbol, cuando existe |
| `ajustes` | `log_visible` | `false` | Mostrar el registro técnico |
| `cola` | `tasks` | `[]` | Cola serializada |
| `historial` (SQLite v2) | tabla `descargas` | — | `id`, `url`, `title`, `fmt`, `label`, `path`, `bytes`, `finished_at` |

La migración de la base de datos de la versión 1 a la 2 añade la columna `label`; ninguna versión
publicada requiere borrar datos.

---

## 17. Solución de problemas

| Síntoma | Causa probable | Acción |
|---|---|---|
| `HTTP 403` repetido | Cambio en los clientes de YouTube | Cambiar de calidad, esperar unos minutos y **Actualizar motor**; si el motor descargado no trae el parche, volver al del APK |
| La descarga no arranca | Destino no escribible o sin espacio | Revisar Ajustes → Carpeta de destino; activar el registro técnico |
| El audio sale con poco nivel | Resolución elegida baja | En modo audio, subir la calidad elegida: el bitrate está ligado a la resolución |
| Una calidad no se puede elegir | Formato de video y audio separados | Quedarse en 1080p o por debajo; no hay mezclador en el dispositivo |
| El archivo no aparece en la galería | Medios sin escanear | Volver al destino por defecto, o revisar con **Abrir**/**Compartir** desde el historial |
| **Abrir** o **Compartir** no hacen nada | El destino no es exponible por `FileProvider` (SD sin rutas reales) | Elegir de nuevo la carpeta, o abrirla desde el gestor de archivos; el aviso aparece al tocar |
| El historial muestra menos de lo esperado | Se superó el tope de 300 elementos o falta permiso persistente tras reinstalar | Reactivar la carpeta en Ajustes y actualizar el registro |
| La app se cerró de golpe | Fallo no capturado por la interfaz | Ajustes → *La app se cerró mal* → **Copiar detalle** |
| Actualización falla o instala otra app | Instalación sobre firma distinta | Verificar la huella del certificado (§12.3) y descargar del release oficial |

---

## 18. Hoja de ruta

| Prioridad | Elemento | Alcance |
|---|---|---|
| 1 | **Modo híbrido con la PC** | Convertir el interruptor deshabilitado en una función real: descubrir un equipo en la red local, entregar el enlace y recibir el archivo ya mezclado, para desbloquear 1440p/2160p y cualquier formato DASH. Requiere protocolo, autenticación y documentación de red |
| 2 | **Mezcla en el dispositivo sin `ffmpeg`** | **Implementada** para AVC/HEVC + AAC (1080p y más). Queda pendiente confirmar en dispositivos reales y cubrir VP9/AV1, que no entran en un MP4 |
| 3 | **Migración de `targetSdk`** | Plan de salida de API 28 hacia una diana publicable, con almacenamiento por alcance y tipos de servicio en primer plano actuales; incluye revisar `usesCleartextTraffic` con una configuración de red por dominio |
| 4 | **CI y calidad** | Pipeline con compilación, pruebas del backend, Lint sin exclusiones y artefactos firmados en cada `tag` |
| 5 | **Verificación en dispositivo** | Batería mínima de instalaciones reales (Android 8, 11, 13, 15), SD y restauración de copias de seguridad |
| 6 | **Accesibilidad** | Auditoría de contraste, tamaños táctiles, lectura de pantalla y modo de texto grande |
| 7 | **Ampliación de alcance** | Listas de reproducción con confirmación, subtítulos cuando existan y múltiples descargas simultáneas |
| 8 | **Distribución** | Decisión informada entre Play Store (obliga al punto 3) u OTA controlada con comprobación de digest |

---

## 19. Créditos, licencia y gobernanza

### 19.1 Créditos

| Proyecto | Autoría | Licencia | Aporta |
|---|---|---|---|
| [youtube-dl](https://github.com/ytdl-org/youtube-dl) | **ric920609** y los contribuidores de `ytdl-org` | Unlicense | El motor de extracción y descarga: todo el trabajo pesado |
| [HUEVOMAN77/youtube-dl](https://github.com/HUEVOMAN77/youtube-dl) | HUEVOMAN77 | Unlicense | Fork sobre `956b8c5855` (2025.04.07) con el parche de clientes de YouTube |
| [ydl-android](https://github.com/HUEVOMAN77/Selenet) | **HUEVOMAN77** | Unlicense | Base original del proyecto, que Selenet continúa y revive |
| [Chaquopy](https://chaquo.com/chaquopy/) | Chaquo | Apache 2.0 | CPython 3.12 dentro del APK |
| [AndroidX / Jetpack](https://developer.android.com/jetpack) | Google | Apache 2.0 | AppCompat, Fragment, RecyclerView, FileProvider |
| [Material Components](https://github.com/material-components/material-components-android) | Google | Apache 2.0 | Sistema de interfaz Material 3 e iconografía Material Icons |
| SQLite | dominio público | dominio público | Persistencia del historial |

Selenet se publica bajo **Unlicense** —la misma licencia del motor— para no añadir restricciones
sobre software que ya era de dominio público: está en `LICENSE`. El desglose por componente y el
aviso de uso están en `NOTICE`.

### 19.2 Aviso de no afiliación y uso responsable

Selenet no está afiliado, patrocinado ni aprobado por YouTube, Google ni por ningún sitio que la
app pueda soportar. Es una herramienta técnica: descargue únicamente contenido que esté autorizado
a descargar, respetando los términos de servicio del sitio y los derechos de sus autores. El
proyecto no se hace responsable del uso que se haga de él y no aloja ni distribuye contenido.

### 19.3 Cómo contribuir

- Rama principal: `main`. Ramas de trabajo con nombre descriptivo.
- Mensajes de commit en español, imperativos y centrados en el *porqué*.
- Cada versión estable va etiquetada (`v1.0`) y publicada como release con su APK firmado.
- Antes de tocar la selección de calidad, validar los *specs* offline como se describe en §6.4:
  es la parte más fácil de romper en silencio.
- Cambios en `app/patches/` exigen regenerar el wheel y comprobar que el motor descargado sigue
  siendo aceptado por la app.

---

## 20. Documentación relacionada

| Documento | Contenido |
|---|---|
| [`docs/RELEASE_NOTES-1.0.md`](docs/RELEASE_NOTES-1.0.md) | Ficha de la versión 1.0: novedades, especificación resumida, verificación y notas de actualización. Es el texto del release |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | Historial completo desde las versiones de prueba |
| [`NOTICE`](NOTICE) | Dependencias, licencias y avisos legales |
| [`LICENSE`](LICENSE) | Texto de la Unlicense |
| [`app/patches/`](app/patches) | Parche aplicable al motor |

**Distribución:** [GitHub Releases](https://github.com/HUEVOMAN77/Selenet/releases) ·
**Repositorio:** https://github.com/HUEVOMAN77/Selenet
