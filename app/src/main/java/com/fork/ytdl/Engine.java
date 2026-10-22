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

    private Engine() {
    }

    public static synchronized void init(Context context) {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context.getApplicationContext()));
        }
        if (backend == null) {
            backend = Python.getInstance().getModule("backend");
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
        if (backend == null) {
            throw new IllegalStateException("motor sin iniciar");
        }
        return backend.callAttr("run", taskId, url, outDir.getAbsolutePath(), fmt, sink)
                .toBoolean();
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
}
