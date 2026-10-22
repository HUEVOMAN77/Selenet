package com.fork.ytdl;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * El log del motor vive en memoria, asi que cuando la app se cierra de golpe se pierde el
 * stack junto con el proceso. Esto lo escribe en un archivo antes de morir y lo deja visible
 * en Ajustes para poder copiarlo.
 */
public final class Crash {

    private static final String FILE = "fallo.txt";
    private static final int MAX_BYTES = 128 * 1024;
    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private Crash() {
    }

    /** Se llama una sola vez desde Application; encadena con el manejador de sistema. */
    public static void install(Context context) {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                save(context.getApplicationContext(), thread, error);
            } catch (Throwable ignored) {
                // si no hay como escribir, que el sistema cierre como siempre
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private static void save(Context context, Thread thread, Throwable error) throws Exception {
        StringWriter buffer = new StringWriter();
        PrintWriter print = new PrintWriter(buffer);
        print.println(STAMP.format(new Date()) + "  hilo: " + thread.getName());
        error.printStackTrace(print);
        Throwable cause = error.getCause();
        while (cause != null) {
            print.println("--- causa ---");
            cause.printStackTrace(print);
            cause = cause.getCause();
        }
        print.println("--- log ---");
        print.println(Logs.global());
        print.flush();

        File file = new File(context.getFilesDir(), FILE);
        FileOutputStream out = new FileOutputStream(file);
        try {
            byte[] raw = buffer.toString().getBytes("UTF-8");
            out.write(raw, 0, Math.min(raw.length, MAX_BYTES));
        } finally {
            out.close();
        }
    }

    public static boolean exists(Context context) {
        return new File(context.getFilesDir(), FILE).isFile();
    }

    /** Contenido guardado, o cadena vacia si nunca fallo nada. */
    public static String read(Context context) {
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        try (FileInputStream in = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(in, "UTF-8")) {
            char[] chunk = new char[4096];
            int read;
            while ((read = reader.read(chunk)) != -1) {
                text.append(chunk, 0, read);
            }
        } catch (Exception ignored) {
            return "";
        }
        return text.toString();
    }

    public static void clear(Context context) {
        new File(context.getFilesDir(), FILE).delete();
    }
}
