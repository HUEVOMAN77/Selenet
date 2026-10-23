package com.fork.ytdl;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.util.List;

public final class Engine {

    public static final String PAUSE = "pause";
    public static final String CANCEL = "cancel";

    private static PyObject backend;
    private static boolean routed;

    private Engine() {
    }

    public static File motorDir(Context context) {
        return new File(context.getFilesDir(), "motor");
    }

    public static File activeDir(Context context) {
        return new File(motorDir(context), "activo");
    }

    public static synchronized void init(Context context) {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context.getApplicationContext()));
        }
        if (backend == null) {
            backend = Python.getInstance().getModule("backend");
        }
        if (!routed) {
            routed = true;
            File activo = activeDir(context);
            Object dir = activo.isDirectory() ? activo.getAbsolutePath() : null;
            try {
                backend.callAttr("use", dir);
            } catch (Exception e) {
                Logs.append(null, "motor externo: " + e);
            }
        }
    }

    public static String[] info() {
        try {
            List<PyObject> parts = backend.callAttr("engine_info").asList();
            String[] out = new String[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                out[i] = parts.get(i).toString();
            }
            return out;
        } catch (Exception e) {
            return new String[]{"?", "?", "?"};
        }
    }

    public static boolean runResult(String taskId, String url, File outDir, String fmt,
                                    Object sink) {
        return runResult(taskId, url, outDir, fmt, sink, null);
    }

    /**
     * Descarga una URL. `prefix`, cuando existe, fija el nombre del archivo de salida
     * en lugar del titulo; lo usa el modo audio para escribir el MP4 temporal en cache.
     */
    public static boolean runResult(String taskId, String url, File outDir, String fmt,
                                    Object sink, String prefix) {
        if (backend == null) {
            throw new IllegalStateException("motor sin iniciar");
        }
        // null en Java llega a Python como None, que es el valor por defecto de prefix.
        return backend.callAttr("run", taskId, url, outDir.getAbsolutePath(), fmt, sink, prefix)
                .toBoolean();
    }

    /** JSON con los formatos leibles de una URL; lo llama un hilo de fondo. */
    public static String formats(String url) {
        if (backend == null) {
            throw new IllegalStateException("motor sin iniciar");
        }
        return backend.callAttr("formats", url).toString();
    }

    /** JSON con los videos de una lista; tampoco se llama desde la interfaz. */
    public static String playlist(String url) {
        if (backend == null) {
            throw new IllegalStateException("motor sin iniciar");
        }
        return backend.callAttr("playlist", url).toString();
    }

    /** JSON con los resultados de una busqueda por texto. */
    public static String search(String query, int limit) {
        if (backend == null) {
            throw new IllegalStateException("motor sin iniciar");
        }
        return backend.callAttr("search", query, limit).toString();
    }

    public static void request(String taskId, String action) {
        if (backend == null) {
            return;
        }
        backend.callAttr("request", taskId, action);
    }

    public static void forget(String taskId) {
        if (backend != null) {
            backend.callAttr("forget", taskId);
        }
    }

    /** Instala el motor bajado; devuelve el JSON que produce backend.install(). */
    public static String install(Context context, String tarPath, boolean force) {
        init(context);
        Object dir = motorDir(context).getAbsolutePath();
        return backend.callAttr("install", tarPath, dir, force).toString();
    }

    /** Vuelve al motor del APK y borra lo descargado. */
    public static String reset(Context context) {
        init(context);
        String version = backend.callAttr("reset").toString();
        deleteTree(activeDir(context));
        return version;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (!file.delete()) {
            Logs.append(null, "no se pudo borrar " + file.getName());
        }
    }
}
