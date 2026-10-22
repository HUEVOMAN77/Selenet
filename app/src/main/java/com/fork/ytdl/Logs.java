package com.fork.ytdl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Buffer en memoria del log del motor; alimenta la vista de log y el boton "Copiar log". */
public final class Logs {

    private static final int MAX_LINES = 400;
    private static final LinkedHashMap<String, StringBuilder> BY_TASK = new LinkedHashMap<>();
    private static final StringBuilder GLOBAL = new StringBuilder();
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    private Logs() {
    }

    public static synchronized void append(Task task, String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            return;
        }
        String line = TIME.format(new Date()) + "  " + msg.trim() + "\n";
        GLOBAL.append(line);
        trim(GLOBAL);
        if (task != null) {
            StringBuilder buffer = BY_TASK.get(task.id);
            if (buffer == null) {
                buffer = new StringBuilder();
                BY_TASK.put(task.id, buffer);
            }
            buffer.append(line);
            trim(buffer);
        }
    }

    public static synchronized String global() {
        return GLOBAL.toString();
    }

    public static synchronized String forTask(Task task) {
        if (task == null) {
            return "";
        }
        StringBuilder buffer = BY_TASK.get(task.id);
        return buffer == null ? "" : buffer.toString();
    }

    public static synchronized void forget(Task task) {
        if (task != null) {
            BY_TASK.remove(task.id);
        }
    }

    public static synchronized void clear() {
        BY_TASK.clear();
        GLOBAL.setLength(0);
    }

    private static void trim(StringBuilder buffer) {
        int overflow = buffer.length() - MAX_LINES * 200;
        if (overflow > 0) {
            buffer.delete(0, overflow);
        }
    }
}
