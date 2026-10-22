package com.fork.ytdl;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Lo que ya esta bajado, sin importar quien lo bajo.
 * Junta la base de la app (que tiene el link y por eso la miniatura de YouTube) con los
 * archivos que hay en la carpeta elegida, en Movies/Selenet (y en Movies/YTDL Fork,
 * que es la de las versiones anteriores), y en la carpeta interna de la app.
 * Se llama siempre desde un hilo de fondo: recorrer carpetas cuesta.
 */
public final class Library {

    /** Techo de filas mostradas y de archivos leidos por carpeta, para no colgar la pantalla. */
    private static final int LIMIT = 300;

    private Library() {
    }

    public static List<Task> all(Context context) {
        LinkedHashMap<String, Task> found = new LinkedHashMap<>();
        fromDatabase(context, found);
        for (File dir : Storage.searchDirs(context)) {
            fromFolder(context, dir, found);
        }
        fromSafTree(context, found);
        List<Task> out = new ArrayList<>(found.values());
        Collections.sort(out, (a, b) -> Long.compare(b.finishedAt, a.finishedAt));
        return out.size() > LIMIT ? new ArrayList<>(out.subList(0, LIMIT)) : out;
    }

    /** Descargas terminadas por la app que todavia tienen el archivo. */
    private static void fromDatabase(Context context, LinkedHashMap<String, Task> found) {
        for (Task task : HistoryDb.get(context).all()) {
            if (task.path == null || !Storage.exists(context, task.path)) {
                continue;
            }
            String key = key(task.path);
            if (!found.containsKey(key)) {
                found.put(key, task);
            }
        }
    }

    /** Archivos de una carpeta, sin entrar en subcarpetas ni tocar los temporales. */
    private static void fromFolder(Context context, File dir, LinkedHashMap<String, Task> found) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (found.size() >= LIMIT) {
                return;
            }
            if (file.isDirectory() || !Storage.isMedia(file.getName())) {
                continue;
            }
            put(found, entry(context, file.getAbsolutePath(),
                    Storage.nameOf(file.getName()), file.length(), file.lastModified()));
        }
    }

    /**
     * Si la carpeta elegida es de una SD (tree SAF sin ruta real) se lista por DocumentsContract.
     * Con almacenamiento interno no hace falta: ahi se escribe con rutas reales.
     */
    private static void fromSafTree(Context context, LinkedHashMap<String, Task> found) {
        Uri tree = Storage.tree(context);
        if (tree == null || Storage.chosenDir(context) != null || found.size() >= LIMIT) {
            return;
        }
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                    DocumentsContract.getTreeDocumentId(tree));
            String[] columns = new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED};
            try (Cursor cursor = context.getContentResolver()
                    .query(children, columns, null, null, null)) {
                if (cursor == null) {
                    return;
                }
                while (cursor.moveToNext() && found.size() < LIMIT) {
                    String name = cursor.getString(1);
                    if (!Storage.isMedia(name)) {
                        continue;
                    }
                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                    put(found, entry(context, doc.toString(), Storage.nameOf(name),
                            cursor.getLong(2), cursor.getLong(3)));
                }
            }
        } catch (Exception e) {
            Logs.append(null, "historial de la carpeta SAF: " + e);
        }
    }

    private static Task entry(Context context, String path, String title,
                              long bytes, long modified) {
        boolean audio = Storage.isAudioName(path);
        JSONObject json = new JSONObject();
        try {
            json.put("id", "archivo:" + path)
                    .put("url", "")
                    .put("title", title)
                    .put("fmt", audio ? Quality.AUDIO : Quality.MP4)
                    .put("label", context.getString(
                            audio ? R.string.hist_audio : R.string.hist_video))
                    .put("state", Task.DONE)
                    .put("path", path)
                    .put("bytes", bytes)
                    .put("finishedAt", modified > 0 ? modified : System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        return new Task(json);
    }

    private static void put(LinkedHashMap<String, Task> found, Task task) {
        String key = key(task.path);
        if (!found.containsKey(key)) {
            found.put(key, task);
        }
    }

    /** Un archivo y su documento SAF son la misma descarga: se comparan por la ruta real. */
    private static String key(String path) {
        String real = Storage.realPath(path);
        return real == null ? path : real;
    }
}
