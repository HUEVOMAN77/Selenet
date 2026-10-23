package com.fork.ytdl;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Destino de los videos: la carpeta elegida por SAF (si la hay) o Movies/Selenet.
 * Las rutas que arrancan con content:// son documentos SAF; el resto son archivos reales.
 */
public final class Storage {

    private static final String PREFS = "ajustes";
    private static final String KEY_TREE = "carpeta";
    private static final String KEY_DIR = "carpeta_real";
    private static final String KEY_LOG = "log_visible";
    private static final String ALBUM = "Selenet";
    /** La carpeta pública de las versiones previas al cambio de nombre: no se borra,
     * solo se sigue leyendo para que el Historial no pierda lo que ya estaba ahí. */
    private static final String ALBUM_VIEJO = "YTDL Fork";

    private Storage() {
    }

    // ---------------------------------------------------------------- registro tecnico

    /** El log del motor esta oculto por defecto; se habilita desde Ajustes. */
    public static boolean logVisible(Context context) {
        return prefs(context).getBoolean(KEY_LOG, false);
    }

    public static void setLogVisible(Context context, boolean visible) {
        prefs(context).edit().putBoolean(KEY_LOG, visible).apply();
    }

    // ---------------------------------------------------------------- carpeta elegida

    public static Uri tree(Context context) {
        String raw = prefs(context).getString(KEY_TREE, null);
        return raw == null ? null : Uri.parse(raw);
    }

    /**
     * Guarda la carpeta elegida. Si el tree es del almacenamiento interno se guarda tambien
     * su ruta real: escribir derecho ahi es mucho mas confiable que ir copiando el archivo
     * por SAF despues de bajarlo. Con una SD o un proveedor raro queda solo el tree.
     */
    public static void saveTree(Context context, Uri uri) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (uri == null) {
            editor.remove(KEY_TREE).remove(KEY_DIR).apply();
            return;
        }
        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        String real = treeRealPath(uri);
        editor.putString(KEY_TREE, uri.toString());
        if (real != null) {
            editor.putString(KEY_DIR, real);
        } else {
            editor.remove(KEY_DIR);
        }
        editor.apply();
    }

    /** true si se puede crear y borrar un archivo dentro de esa carpeta. */
    public static boolean writable(File dir) {
        if (dir == null) {
            return false;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            return false;
        }
        if (!dir.isDirectory()) {
            return false;
        }
        File probe = new File(dir, ".write");
        probe.delete();
        try {
            return probe.createNewFile();
        } catch (Exception e) {
            return false;
        } finally {
            if (probe.exists() && !probe.delete()) {
                Logs.append(null, "no se pudo borrar la prueba de escritura");
            }
        }
    }

    /** La ruta real guardada, sin comprobar si todavia se puede escribir ahi. */
    public static String chosenRaw(Context context) {
        return prefs(context).getString(KEY_DIR, null);
    }

    /**
     * La carpeta real que eligio el usuario, o null si no hay ninguna o si el tree es de una
     * SD. Se comprueba la escritura cada vez: si la carpeta se borro a mano, la app vuelve a
     * la de siempre en vez de fallar en cada descarga.
     */
    public static File chosenDir(Context context) {
        String raw = prefs(context).getString(KEY_DIR, null);
        if (raw == null) {
            return null;
        }
        File dir = new File(raw);
        return writable(dir) ? dir : null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- carpetas

    /** Carpeta publica de videos; null si no se puede escribir ahi. */
    public static File sharedDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), ALBUM);
        return writable(dir) ? dir : null;
    }

    /** La carpeta Movies/YTDL Fork de las versiones anteriores; null si ya no existe. */
    public static File legacySharedDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), ALBUM_VIEJO);
        return dir.isDirectory() ? dir : null;
    }

    /** Donde escribe el motor: siempre una ruta real. */
    public static File engineDir(Context context) {
        File chosen = chosenDir(context);
        if (chosen != null) {
            return chosen;
        }
        if (tree(context) != null) {
            File staging = new File(context.getApplicationContext().getFilesDir(), "staging");
            if (!staging.exists()) {
                staging.mkdirs();
            }
            return staging;
        }
        File shared = sharedDir();
        if (shared != null) {
            return shared;
        }
        File dir = new File(context.getExternalFilesDir(null), "downloads");
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File(context.getFilesDir(), "downloads");
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Bytes libres en el volumen donde la app escribe primero. Todas las descargas se escriben
     * en `engineDir` y despues se publican al destino del usuario, asi que esta es la medida
     * que importa antes de empezar. Devuelve 0 cuando el sistema no lo puede informar: los
     * que lo consultan tratan 0 como "no se sabe" y no bloquean nada.
     */
    public static long freeBytes(Context context) {
        File dir = engineDir(context);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getUsableSpace();
    }

    /** Las carpetas que se recorren al armar el historial: la elegida, las públicas y la de la app. */
    public static List<File> searchDirs(Context context) {
        List<File> dirs = new ArrayList<>();
        File chosen = chosenDir(context);
        if (chosen != null) {
            dirs.add(chosen);
        }
        File shared = sharedDir();
        File app = new File(context.getExternalFilesDir(null), "downloads");
        for (File candidate : new File[]{shared, legacySharedDir(), app}) {
            if (candidate != null && candidate.isDirectory() && !dirs.contains(candidate)) {
                dirs.add(candidate);
            }
        }
        return dirs;
    }

    public static String describe(Context context) {
        File chosen = chosenDir(context);
        if (chosen != null) {
            return chosen.getAbsolutePath();
        }
        Uri uri = tree(context);
        if (uri != null) {
            return uri.toString();
        }
        File shared = sharedDir();
        if (shared != null) {
            return shared.getAbsolutePath();
        }
        return new File(context.getExternalFilesDir(null), "downloads").getAbsolutePath();
    }

    // ---------------------------------------------------------------- publicacion

    /** Mueve lo que bajo el motor a su destino final y devuelve la ruta definitiva. */
    public static String publish(Context context, String stagedPath) {
        Uri tree = tree(context);
        File staged = new File(stagedPath);
        // con carpeta real el archivo ya esta en el destino; solo queda el tree SAF (SD)
        if (tree == null || chosenDir(context) != null || !staged.exists()) {
            return stagedPath;
        }
        try {
            Uri created = DocumentsContract.createDocument(context.getContentResolver(), tree,
                    mimeOf(staged.getName()), staged.getName());
            if (created == null) {
                return stagedPath;
            }
            transfer(new FileInputStream(staged),
                    context.getContentResolver().openOutputStream(created));
            return staged.delete() ? created.toString() : stagedPath;
        } catch (Exception e) {
            Logs.append(null, "no se pudo copiar a la carpeta elegida: " + e);
            return stagedPath;
        }
    }

    private static void transfer(InputStream in, OutputStream out) throws Exception {
        if (out == null) {
            in.close();
            throw new IllegalStateException("el destino no admite escritura");
        }
        byte[] buffer = new byte[64 * 1024];
        try {
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            in.close();
            out.close();
        }
    }

    // ---------------------------------------------------------------- nombre y caché de audio

    private static final java.util.regex.Pattern INVALID =
            java.util.regex.Pattern.compile("[/\\\\:*?\"<>|\\p{Cntrl}]");

    /** Carpeta privada de caché para el MP4 temporal del modo audio; nunca el destino del usuario. */
    public static File audioCache(Context context) {
        File dir = new File(context.getApplicationContext().getCacheDir(), "audio");
        if (!dir.exists() && !dir.mkdirs()) {
            return context.getCacheDir();
        }
        return dir;
    }

    /** Nombre limpio: sin / \\ : * ? " &lt; &gt; | ni controles, y cortado a 100 caracteres. */
    public static String safeBase(String raw) {
        String text = raw == null ? "" : raw.trim();
        text = INVALID.matcher(text).replaceAll("_");
        while (text.startsWith(".")) {
            text = text.substring(1);
        }
        while (text.endsWith(".") || text.endsWith(" ")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty()) {
            text = "audio";
        }
        return text.length() > 100 ? text.substring(0, 100) : text;
    }

    /**
     * Pasa un archivo de una carpeta a otra y deja el origen borrado. `renameTo` no funciona
     * entre volumenes (la caché interna y el almacenamiento publico son distintos), asi que
     * si el rename falla se copia y despues se tira el original.
     */
    public static boolean move(File from, File to) {
        if (from == null || !from.isFile() || from.equals(to)) {
            return false;
        }
        if (to.getParentFile() != null && !to.getParentFile().exists()) {
            to.getParentFile().mkdirs();
        }
        if (from.renameTo(to)) {
            return true;
        }
        try {
            transfer(new FileInputStream(from), new java.io.FileOutputStream(to));
        } catch (Exception e) {
            Logs.append(null, "no se pudo mover " + from.getName() + ": " + e);
            if (to.exists()) {
                to.delete();
            }
            return false;
        }
        if (!to.isFile() || to.length() != from.length()) {
            to.delete();
            return false;
        }
        return from.delete();
    }

    /** El primer nombre libre del par base.ext; si está tomado prueba "base (1).ext", etc. */
    public static File unique(File dir, String base, String ext) {
        File file = new File(dir, base + "." + ext);
        for (int n = 1; file.exists() && n < 1000; n++) {
            file = new File(dir, base + " (" + n + ")." + ext);
        }
        return file;
    }

    /**
     * Alta el archivo como audio en MediaStore para que aparezca en reproductores y galería.
     * No pide permisos nuevos: con targetSdk 28 y WRITE_EXTERNAL_STORAGE ya declarado, el insert
     * funciona en Android 6 a 13. Si el insert no se puede, usa al MediaScanner con el MIME real.
     */
    public static boolean registerAudio(Context context, File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        if (alreadyInStore(context, file.getAbsolutePath())) {
            return true;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.TITLE, base);
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name));
        values.put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath());
        values.put(MediaStore.Audio.Media.IS_MUSIC, true);
        try {
            if (context.getContentResolver()
                    .insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) != null) {
                return true;
            }
        } catch (Exception e) {
            Logs.append(null, "MediaStore audio: " + e);
        }
        scan(context, file.getAbsolutePath());
        return false;
    }

    /** true si MediaScanner ya anotó esa ruta (evita filas duplicadas). */
    private static boolean alreadyInStore(Context context, String path) {
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[]{"_id"},
                MediaStore.Audio.Media.DATA + "=?", new String[]{path}, null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Borra lo que dejó el modo audio en la caché, salvo lo que una tarea todavía necesita. */
    public static int cleanAudioCache(Context context, java.util.Collection<String> keep) {
        File[] files = audioCache(context).listFiles();
        if (files == null) {
            return 0;
        }
        int deleted = 0;
        for (File file : files) {
            if (keep != null && keep.contains(file.getAbsolutePath())) {
                continue;
            }
            if (file.delete()) {
                deleted++;
            }
        }
        return deleted;
    }

    // ---------------------------------------------------------------- operaciones

    public static boolean exists(Context context, String path) {
        if (path == null) {
            return false;
        }
        if (isDoc(path)) {
            try (Cursor cursor = context.getContentResolver()
                    .query(Uri.parse(path), null, null, null, null)) {
                return cursor != null && cursor.moveToFirst();
            } catch (Exception e) {
                return false;
            }
        }
        return new File(path).exists();
    }

    public static long size(Context context, String path) {
        if (!isDoc(path)) {
            return new File(path).length();
        }
        try (Cursor cursor = context.getContentResolver().query(Uri.parse(path),
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public static boolean delete(Context context, String path) {
        if (isDoc(path)) {
            try {
                return DocumentsContract.deleteDocument(context.getContentResolver(),
                        Uri.parse(path));
            } catch (Exception e) {
                return false;
            }
        }
        return new File(path).delete();
    }

    /** Uri listo para abrir o compartir: directo si es SAF, por FileProvider si es archivo. */
    public static Uri shareUri(Context context, String path) {
        if (isDoc(path)) {
            return Uri.parse(path);
        }
        try {
            return FileProvider.getUriForFile(context, context.getPackageName() + ".archivos",
                    new File(path));
        } catch (IllegalArgumentException e) {
            // la ruta esta fuera de lo que el FileProvider puede exponer
            Logs.append(null, "no se puede compartir " + path + ": " + e.getMessage());
            return null;
        }
    }

    public static String nameOf(String path) {
        String raw = isDoc(path) ? Uri.parse(path).getLastPathSegment() : path;
        if (raw == null) {
            return "";
        }
        int slash = raw.lastIndexOf('/');
        String name = slash >= 0 ? raw.substring(slash + 1) : raw;
        try {
            name = URLDecoder.decode(name, "UTF-8");
        } catch (Exception ignored) {
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Avisa a la galeria cuando el archivo es una ruta real visible. */
    public static void scan(Context context, String path) {
        String real = realPath(path);
        if (real == null || isDoc(path)) {
            return;
        }
        MediaScannerConnection.scanFile(context.getApplicationContext(),
                new String[]{real}, new String[]{mimeOf(real)}, null);
    }

    /** Ruta real de un documento del almacenamiento primario, o null si no se puede saber. */
    public static String realPath(String path) {
        if (!isDoc(path)) {
            return path;
        }
        String doc = Uri.parse(path).getLastPathSegment();
        if (doc == null) {
            return null;
        }
        try {
            doc = URLDecoder.decode(doc, "UTF-8");
        } catch (Exception e) {
            return null;
        }
        int colon = doc.indexOf(':');
        if (colon < 0 || doc.indexOf('/', colon) < 0) {
            return null;
        }
        return new File(Environment.getExternalStorageDirectory(), doc.substring(colon + 1))
                .getAbsolutePath();
    }

    private static String treeRealPath(Uri tree) {
        String doc = DocumentsContract.getTreeDocumentId(tree);
        if (doc == null || !doc.startsWith("primary:")) {
            return null;
        }
        return new File(Environment.getExternalStorageDirectory(), doc.substring("primary:".length()))
                .getAbsolutePath();
    }

    /** Extensiones que el historial reconoce como una descarga. */
    private static final String[] VIDEO_EXT = {"mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp"};
    private static final String[] AUDIO_EXT = {"m4a", "mp3", "aac", "ogg", "opus", "wav", "flac"};

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.US);
    }

    public static boolean isAudioName(String name) {
        return name != null && contains(AUDIO_EXT, extOf(name));
    }

    /** Video o audio: lo que el historial muestra; lo demas (.part, .ytdl, fotos) queda fuera. */
    public static boolean isMedia(String name) {
        if (name == null || name.startsWith(".")) {
            return false;
        }
        String ext = extOf(name);
        return contains(VIDEO_EXT, ext) || contains(AUDIO_EXT, ext);
    }

    private static boolean contains(String[] values, String value) {
        for (String known : values) {
            if (known.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static String mimeOf(String name) {        String lower = name.toLowerCase(Locale.US);
        if (lower.matches(".*\\.(m4a|mp3|aac|wav|flac)$")) {
            return "audio/mp4";
        }
        if (lower.matches(".*\\.(ogg|opus)$")) {
            return "audio/ogg";
        }
        if (lower.matches(".*\\.(webm|mkv)$")) {
            return "video/webm";
        }
        return "video/mp4";
    }

    /** Tipo generico para elegir app de reproduccion, segun la terminacion de la ruta. */
    public static String typeOf(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.US);
        return lower.matches(".*\\.(m4a|mp3|aac|ogg|opus|wav|flac)(\\?.*)?$")
                ? "audio/*" : "video/*";
    }

    private static boolean isDoc(String path) {
        return path.startsWith("content://");
    }
}
