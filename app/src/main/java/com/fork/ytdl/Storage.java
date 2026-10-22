package com.fork.ytdl;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.Locale;

/**
 * Destino de los videos: la carpeta elegida por SAF (si la hay) o Movies/YTDL Fork.
 * Las rutas que arrancan con content:// son documentos SAF; el resto son archivos reales.
 */
public final class Storage {

    private static final String PREFS = "ajustes";
    private static final String KEY_TREE = "carpeta";
    private static final String ALBUM = "YTDL Fork";

    private Storage() {
    }

    // ---------------------------------------------------------------- carpeta elegida

    public static Uri tree(Context context) {
        String raw = prefs(context).getString(KEY_TREE, null);
        return raw == null ? null : Uri.parse(raw);
    }

    public static void saveTree(Context context, Uri uri) {
        try {
            if (uri != null) {
                context.getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
        } catch (Exception ignored) {
        }
        prefs(context).edit().putString(KEY_TREE, uri == null ? null : uri.toString()).apply();
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
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        File probe = new File(dir, ".write");
        try {
            return probe.createNewFile() ? dir : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (probe.exists() && !probe.delete()) {
                Logs.append(null, "no se pudo borrar la prueba de escritura");
            }
        }
    }

    /** Donde escribe el motor: siempre una ruta real. */
    public static File engineDir(Context context) {
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

    public static String describe(Context context) {
        Uri uri = tree(context);
        if (uri != null) {
            String real = treeRealPath(uri);
            return real != null ? real : uri.toString();
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
        if (tree == null || !staged.exists()) {
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
        return isDoc(path) ? Uri.parse(path)
                : FileProvider.getUriForFile(context, context.getPackageName() + ".archivos",
                        new File(path));
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

    private static String mimeOf(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.matches(".*\\.(m4a|mp3|aac|ogg|opus)$")) {
            return "audio/mp4";
        }
        return "video/mp4";
    }

    private static boolean isDoc(String path) {
        return path.startsWith("content://");
    }
}
