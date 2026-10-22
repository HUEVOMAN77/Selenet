# Selenet 1.0 — Nota de versión oficial

**Fecha de publicación:** 2026-10-22 · **Etiqueta:** `v1.0` · **Artefacto:** `Selenet-1.0.apk`

Primera versión **oficial y estable** del proyecto. Selenet es el nombre definitivo de lo que
hasta ahora era `ydl-android`: una aplicación de Android que ejecuta el motor de `youtube-dl`
**dentro del teléfono** y descarga video o audio en la carpeta que el usuario elige. Sin root, sin
servidores intermedios, sin `ffmpeg`, sin permisos de ubicación, contactos o micrófono. Requiere
Android 7.0 o superior en dispositivos `arm64-v8a`.

Documentación técnica completa:
[`README.md`](https://github.com/HUEVOMAN77/Selenet/blob/main/README.md) ·
Historial: [`docs/CHANGELOG.md`](https://github.com/HUEVOMAN77/Selenet/blob/main/docs/CHANGELOG.md)

---

## Qué incorpora esta versión

| Área | Entregado |
|---|---|
| Identidad | Nombre oficial **Selenet** en el lanzador y en Ajustes; `versionName` 1.0 con `versionCode` 10000 |
| Calidad | Escalera selectable de **144p a 1080p**, con dos configuraciones independientes (video y solo audio) en Ajustes |
| Solo audio | `.m4a` real generado en el dispositivo con `MediaExtractor`/`MediaMuxer`, registrado en la biblioteca multimedia |
| Historial | Muestra **todos los archivos ya descargados**, no solo los que la app recuerda: base de datos + exploración de las carpetas reales, deduplicados y ordenados por fecha |
| Carpeta de destino | Selector funcional del sistema (SAF) con permiso persistente, verificación de escritura y marcha atrás automática si la carpeta deja de ser utilizable |
| Abrir y compartir | Corregido: `FileProvider` ahora expone también el almacenamiento externo y los volúmenes montados |
| Interfaz | Tema oscuro Material 3 con degradado azul→verde agua, navegación inferior, icono adaptativo propio con variante monocroma |
| Motor | Actualización de `youtube-dl` desde GitHub sin recompilar la app, con rechazo de motores incompatibles y vuelta al motor del APK |
| Diagnóstico | Registro técnico del motor oculto por defecto y captura del detalle de fallos en disco |
| Legal | Licencia Unlicense, `NOTICE` con el desglose de terceros y créditos al proyecto original |

---

## Correcciones funcionales respecto de las versiones de prueba

- Las calidades bajas (144p, 240p, 360p) seleccionaban formatos que **no respetaban el techo
  pedido**, porque el motor embebido no ordena los formatos. Ahora cada preset empieza con los
  *itag* progresivos deterministas y termina con filtros de altura: se validó offline que 144→17,
  240→36, 360→18, 480→59, 720→22 y 1080→37.
- Un destino no escribible dejaba todas las descargas en error; ahora se detecta y se revierte.
- Una prueba de escritura mal limpiada podía hacer aparecer como inválida una carpeta correcta.
- El historial solo mostraba lo que la app había descargado: los archivos ya presentes en la
  carpeta eran invisibles.
- **Abrir** y **Compartir** no funcionaban porque el proveedor de archivos solo cubría
  directorios privados de la app.

---

## Especificación de la versión

| Campo | Valor |
|---|---|
| Paquete | `com.fork.ytdl` (se conservó a propósito para no crear una app distinta) |
| `versionCode` / `versionName` | 10000 / 1.0 |
| Etiqueta | `Selenet` |
| SDK mínimo / diana / compilación | 24 / 28 / 35 |
| ABI | `arm64-v8a` |
| Motor | `youtube-dl` 2025.04.07 (fork con parche de clientes) sobre CPython 3.12 |
| Runtime | Chaquopy 17.1.0 |
| Interfaz | AndroidX + Material Components 1.12.0 |
| Código | Java 17 (sin Kotlin ni coroutines) |
| Tamaño del APK | 20.485.673 bytes (19,54 MiB); contenido descomprimido 34,57 MiB |
| Licencia | Unlicense |

### Calidad soportada

| Resolución | *itag* prioritario | Contenedor | Video | Audio nominal |
|---|---|---|---|---|
| 144p | 17 | 3GP | mp4v | AAC ~24 kbps |
| 240p | 36 | 3GP | mp4v | AAC variable |
| 360p | 18 | MP4 | H.264 | AAC ~96 kbps |
| 480p | 59 | MP4 | H.264 | AAC ~128 kbps |
| 720p | 22 | MP4 | H.264 | AAC ~192 kbps |
| 1080p | 37 | MP4 | H.264 | AAC ~192 kbps |

Todos los presets garantizan **video y audio en el mismo archivo**, sin necesidad de mezclar.
En el modo solo audio el bitrate depende de la resolución elegida, porque el sonido sale del mismo
MP4 progresivo.

---

## Instalación y actualización

1. Descargar `Selenet-1.0.apk` de esta página del release.
2. Abrirlo en el teléfono y permitir instalar desde esta fuente (solo la primera vez).
3. Se puede instalar **encima** de una versión anterior sin desinstalar: la firma es la misma, por
   lo que se conservan cola, ajustes e historial.

Carpeta de destino por defecto: `Movies/Selenet`. Las descargas anteriores en `Movies/YTDL Fork`
siguen apareciendo en el historial (solo lectura; esa carpeta no se borra ni se toca).

---

## Verificación del artefacto

| Digest | Valor |
|---|---|
| SHA-256 | `0935d0683c37a611c775dbe69e8fc52e78e5594f5a40df8aa8d408ffba12b05e` |
| MD5 | `0ca9f799b6a0d321f42cd618acdc949b` |

```sh
sha256sum Selenet-1.0.apk
apksigner verify --print-certs Selenet-1.0.apk
```

Firmado con **APK Signature Scheme v2**, certificado
`CN=HUEVOMAN77, OU=ydl-android, O=HUEVOMAN77, L=El Salvador, C=SV` ·
huella `072384533e77b5deaa8b884c21376cced3edd1cfbc52ca0cd66b0198c8d7f5e7`.
El APK publicado se compiló desde el estado de código de `08c5c5c`; bajo `app/` solo cambió un
comentario de javadoc sin efecto en el bytecode (el resto de los cambios posteriores es
documentación).

Comprobaciones ejecutadas: `assembleRelease` correcta, 17 pruebas del backend Python en verde,
validación offline de los seis *specs* de calidad, `aapt2 dump badging` (paquete, SDKs, ABI,
etiqueta *Selenet*), `apksigner verify` y `zipalign -c -v 4`.

---

## Limitaciones conocidas

- **Por encima de 1080p no hay descarga posible en el dispositivo**: esos formatos vienen con video
  y audio en pistas separadas y requieren un mezclador. La app los lista marcados como
  *Requiere PC* y avisa al seleccionarlos, pero no los descarga.
- **El «modo híbrido con la PC» no está implementado en la 1.0.** Existe únicamente un interruptor
  **deshabilitado** en Ajustes → Avanzado, sin código asociado: es un marcador de posición de una
  función planificada. Cualquier texto que lo presente como disponible es incorrecto.
- Sin listas de reproducción, sin subtítulos, sin DRM y sin descarga concurrente (la cola es
  secuencial por diseño).
- Distribución solo para `arm64-v8a`; no hay artefacto para `armeabi-v7a` ni emulador x86.
- `targetSdk` 28 y tráfico de texto claro habilitado por necesidad técnica; su revisión está en la
  hoja de ruta y su justificación en el README.
- Sin validación propia en una granja de dispositivos ni en Android 14/15; sin CI y con
  `lintVitalRelease` excluido del empaquetado.

---

## Créditos y licencia

El motor de extracción es de [ytdl-org/youtube-dl](https://github.com/ytdl-org/youtube-dl),
creado y mantenido por **ric920609** y los contribuidores de `ytdl-org` (Unlicense), usado desde el
fork [HUEVOMAN77/youtube-dl](https://github.com/HUEVOMAN77/youtube-dl) con el parche de clientes de
YouTube. La base de la aplicación es el proyecto
[ydl-android](https://github.com/HUEVOMAN77/Selenet) de **HUEVOMAN77**, que Selenet continúa y
revive. Python dentro del APK gracias a [Chaquopy](https://chaquo.com/chaquopy/) (Apache 2.0);
interfaz con AndroidX y Material Components (Apache 2.0).

Selenet se publica como **Unlicense** —la misma licencia del motor— para no añadir restricciones
sobre software que ya era de dominio público. Licencia en `LICENSE`, desglose de terceros en
`NOTICE`.

**Aviso:** Selenet es una herramienta técnica, no está afiliada ni aprobada por YouTube ni por
ningún sitio soportado. Descargue solo contenido que esté autorizado a descargar, respetando los
términos de servicio del sitio y los derechos de sus autores.
