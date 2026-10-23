# Registro de cambios de Selenet

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y
[Versionado Semántico](https://semver.org/lang/es/). Las versiones `v6` a `v11` fueron entregas de
pruebas internas y se publicaron con `versionName` del motor; desde la 1.0 se usa `versionCode`
semántico (`10000` para la 1.0) manteniendo la misma clave de firma, de modo que la actualización
se instala encima sin desinstalar.

---

## [Unreleased]

### Documentation

- Reescritura completa del `README.md` como especificación técnica del producto: arquitectura,
  modelo de hilos, especificación de calidad con las escaleras de *itag* y su validación,
  almacenamiento, seguridad y permisos con las compensaciones declaradas, taxonomía de errores,
  composición y verificación del artefacto, matriz de compatibilidad, estado de calidad y
  verificación, configuración persistente, solución de problemas y hoja de ruta.
- Añadidos `docs/RELEASE_NOTES-1.0.md` (texto del release) y este registro de cambios.
- **Corrección de exactitud:** el *modo híbrido con la PC* estaba documentado como una función
  disponible. No lo es: solo existe un interruptor deshabilitado en Ajustes → Avanzado sin código
  asociado. Pasa a documentarse como *fuera de alcance* y como primer elemento de la hoja de ruta.
- Corregido el comentario obsoleto de `Quality.java` que repetía esa misma afirmación.
- Afirmaciones nuevas verificadas contra el repositorio y el APK publicado durante esta pasada
  (rotación de clientes, política de reintentos, digest de firma, composición del artefacto,
  número de extractores, pruebas del backend).

---

## [1.3] — 2026-10-23 · etiqueta `v1.3-pistas.1` · **en prueba**

### Added

- **Unión de pistas en el dispositivo, sin `ffmpeg`.** Las calidades que el sitio manda en dos
  pistas (video suelto sin audio + audio `.m4a`) se bajan las dos a la caché privada y se juntan
  con `MediaExtractor`/`MediaMuxer` en un solo MP4: es el camino de 1080p y de las alturas
  mayores cuando existen en MP4. No decodifica ni recodifica, así que conserva el video y el
  audio exactos que entregó el sitio.
- `Merger.java` (mezclador), `Task.audioFmt`, dos tramos nuevos de la barra de progreso y la
  etiqueta de estado **Uniendo pistas**. El motor ahora marca con `audio` la pista con la que
  juntar cada opción de video suelto, y suma el tamaño de las dos para el aviso de espacio.
- Tipos de error nuevos: `merge` y `no_video`, con su texto en español.

### Changed

- *Fuera del alcance en la 1.0*: las opciones de video suelto con códecs que Android escribe en
  MP4 ya no se marcan como *Requiere PC*; se descargan y se unen. Siguen marcadas las que
  vienen en VP9/AV1 con audio Opus, que no tienen lugar en un MP4.
- La hoja del video informa el tamaño de la opción más grande y no la primera de la lista, que
  es lo que sirve antes de elegir calidad.

### Verificación

- Compilación `debug` y `release` firmada. Probado con un extractor falso: las filas `avc1`+`m4a`
  quedan emparejadas y suman su tamaño; las `vp9`/`opus` no.
- **No probado en un teléfono.** El mezclador depende de que `MediaExtractor` lea el contenedor
  que entregó el sitio (los MP4 fragmentados requieren Android 9 o superior) y de que
  `MediaMuxer` acepte los tiempos tal como vienen; eso solo se confirma instalando.

---

## [1.2] — 2026-10-23 · etiqueta `v1.2-buscar.1`

### Added

- **Buscador dentro de la app.** Botón *Buscar video* en Descargas: una sola petición de
  `ytsearchN:` con `extract_flat` devuelve 15 resultados con duración, canal y tamaño
  aproximado. Toque = elegir calidad; pulsación larga = agregar con la calidad guardada.
- **Ficha previa del video** antes de encolar: canal, vistas, fecha, duración y tamaño, leídos
  del mismo pedido de formatos.
- **Abrir en Selenet.** Filtro `VIEW` en `MainActivity`, limitado a los dominios donde
  descargar tiene sentido, para traer un link desde otro navegador sin copiarlo.
- **Control de espacio.** Aviso cuando lo elegido no entra con 80 MB de margen (con opción de
  bajar igual) y tarea marcada como error cuando el volumen está por debajo de 60 MB, en vez
  de una descarga que se corta a mitad.

---

## [1.1] — 2026-10-23 · etiqueta `1.1-intro.1`

### Added

- Intro animada de 4 segundos (`IntroActivity`, lanzador de la app) y guía de primer uso
  (`OnboardingActivity`) con salto, progreso y paginación.
- Mejoras de interfaz: estado vivo de la cola, badge en la pestaña Descargas y filtro del
  historial.
- Funciones: bajar solo por WiFi, reintentos con multi-enlace por tarea, listas de
  reproducción con confirmación y aviso individual por descarga terminada (con canal
  aparte y permiso `POST_NOTIFICATIONS` pedido en tiempo de ejecución).
- Número de compilación visible en Ajustes, para distinguir un APK de otro al probar.

---

## [1.0] — 2026-10-22 · etiqueta `v1.0` · **primera versión oficial y estable**

### Added

- Nombre oficial **Selenet** en la etiqueta de la aplicación y en el lanzador.
- Carpeta pública por defecto `Movies/Selenet`, con lectura de la anterior
  `Movies/YTDL Fork` para no ocultar descargas previas.
- `LICENSE` (Unlicense) y `NOTICE` con el desglose de terceros, avisos de no afiliación y uso
  responsable.
- `README.md` del proyecto con la historia de continuidad desde `ydl-android` y los créditos al
  creador original.

### Changed

- `versionName` 1.0 y `versionCode` 10000, calculados para quedar por encima de las entregas de
  pruebas y permitir actualizar sin perder cola ni historial.
- Se conserva deliberadamente el identificador `com.fork.ytdl`: cambiarlo habría creado una app
  distinta e instalado dos veces en el mismo teléfono.

### Fixed

- Mensajes de calidad y destino que prometían comportamientos inexistentes en Ajustes.

---

## [11] — 2026-10-22 · etiqueta `v11`

### Added

- **Historial completo:** `Library` consolida la base de datos de la app con la exploración real de
  las carpetas (destino elegido, `Movies/Selenet`, la carpeta del nombre anterior y la carpeta
  privada) y el árbol SAF cuando no hay ruta real; deduplica por ruta y limita a 300 elementos.
- Miniatura local: si una entrada no tiene enlace, se extrae un fotograma del propio archivo con
  `MediaMetadataRetriever` (`Thumbs.showFile`).
- **Carpeta de destino funcional:** selector `OpenDocumentTree`, permiso persistente, resolución
  del árbol a ruta real y validación de escritura antes de guardar la elección, con reversión si la
  carpeta no es utilizable.
- **Escalera de calidad de 144p a 1080p** en Ajustes, con dos configuraciones independientes para
  video y para solo audio, y selector por video ordenado de menor a mayor resolución marcando con
  `✓` la calidad recordada.
- Aviso cuando el archivo del historial no se puede exponer (`hist_sin_ruta`).

### Fixed

- Las calidades bajas elegían formatos por encima del techo pedido: el motor embebido no ordena los
  formatos, así que `best` no significaba «el mejor». Cada preset arranca ahora con los itag
  progresivos clásicos y deja los filtros de altura como red; validado offline.
- `Storage.writable()` podía marcar como no escribible una carpeta con un centinela `.write`
  residual.
- **Abrir** y **Compartir** no funcionaban: `file_paths.xml` solo cubría directorios privados de la
  app. Se añadieron almacenamiento externo, volúmenes montados y caché.
- Borrado de archivos desde el historial con confirmación y limpieza de la base de datos por
  identificador y por ruta.

---

## [10] — 2026-10-22 · etiqueta `v10`

### Added

- Interfaz oscura con degradado azul→verde agua y tarjetas translúcidas sobre un sistema de tokens
  Material 3 completo (71 colores, escala de formas 14/22/30 dp).
- Icono de lanzador propio: adaptativo con capa `monochrome` y cinco densidades para Android 7.x.
- Miniaturas en la cola y en el historial con caché `LruCache` de 6 MiB, reescalado a 240 px,
  dos hilos y lista de claves fallidas.
- Buscador dentro de la cola, insensible a tildes y mayúsculas.

### Changed

- Navegación inferior con indicador activo de Material y portadas de pestaña renovadas.

---

## [9] — 2026-10-22 · etiqueta `v2025.04.07-fork.9`

### Added

- Modo **solo audio** sin `ffmpeg`: el motor baja un MP4 progresivo a la caché privada y
  `MediaExtractor`/`MediaMuxer` generan el `.m4a` definitivo.
- Barra de progreso en dos etapas (`download` 0 %→90 %, `extract` 90 %→100 %) con el estado
  `Extrayendo audio…`.
- Botón **Guardar el MP4 completo** como reserva cuando la extracción falla.
- Alta en `MediaStore` como audio musical con detección de duplicados y repliegue al
  `MediaScanner`; limpieza de la caché de audio respetando las tareas activas.
- Nombres de archivo saneados y sufijos ` (1)`, ` (2)`… en caso de colisión.

### Fixed

- El temporal de audio se descargaba con nombre fijo en la caché, evitando restos por carpeta.
- Un `.m4a` de 0 bytes se reporta como error propio (`audio_vacio`) en lugar de completarse.

---

## [8] — 2026-10-22 · etiqueta `v2025.04.07-fork.8`

### Added

- Rotación de clientes de YouTube (`android_sdkless`, `tv_downgraded`, `tv`, `web_safari`) al
  recibir `HTTP 403`, habilitada por el parche `youtube_player_clients` del motor.
- Captura de fallos no controlados en `fallo.txt` (hasta 128 KiB) con pila, causas encadenadas y
  registro global, visible y copiable desde Ajustes.
- Firma de release propia, fuera del repositorio vía `local.properties`.
- Registro técnico oculto por defecto, con interruptor en Ajustes.

### Fixed

- Clasificación correcta del bloqueo 403 de YouTube, que antes se reportaba como fallo genérico o
  de red.
- Cierre abrupto de la app cuando una descarga fallaba dentro del hilo del motor.
- Rechazo de motores descargados que no traigan el parche de clientes, con mensaje explicativo y
  permanencia del motor del APK.

---

## [7] — 2026-10-22 · etiqueta `v2025.04.07-fork.7`

### Added

- Rediseño completo sobre Material 3, modo oscuro y las tres pantallas renovadas.
- Selector de formatos con lectura real del motor (MP4/MP3) y visualización de la ruta de destino.

### Fixed

- Estabilidad del servicio en primer plano y del backend Python.

---

## [6] — 2026-10-22 · etiqueta `v2025.04.07-fork.1` (publicada como *v6 - ultima*, `versionName` `2025.04.07-fork.6`)

### Added

- Base del proyecto: APK con `youtube-dl` 2025.04.07 embebido mediante Chaquopy y cola persistente
  con servicio en primer plano, estados, pausa, reanudación y reintentos con espera progresiva.
- Pegado desde el portapapeles con confirmación, recepción del menú **Compartir** y carga de
  formatos por video.
- Carpeta elegible por SAF, anuncio a la galería e historial en SQLite.
- Botón **Actualizar motor** con descarga desde GitHub y repliegue al motor del APK.
- Detalle técnico del error en la tarjeta de la tarea y nombre e icono del experimento.

[Unreleased]: https://github.com/HUEVOMAN77/Selenet/commits/main
[1.0]: https://github.com/HUEVOMAN77/Selenet/releases/tag/v1.0
[11]: https://github.com/HUEVOMAN77/Selenet/releases/tag/v11
[10]: https://github.com/HUEVOMAN77/Selenet/releases/tag/v10
[9]: https://github.com/HUEVOMAN77/Selenet/releases/tag/v2025.04.07-fork.9
[8]: https://github.com/HUEVOMAN77/Selenet/releases/tag/v2025.04.07-fork.8
[7]: https://github.com/HUEVOMAN77/Selenet/releases/tag/v2025.04.07-fork.7
[6]: https://github.com/HUEVOMAN77/Selenet/releases/tag/v2025.04.07-fork.1
