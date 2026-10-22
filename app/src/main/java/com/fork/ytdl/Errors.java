package com.fork.ytdl;

import android.content.Context;

/** Traduce los tipos de error del motor a mensajes en espanol. */
public final class Errors {

    private Errors() {
    }

    public static String message(Context context, Task task) {
        String kind = task.errorKind == null ? "error" : task.errorKind;
        switch (kind) {
            case "private":
                return context.getString(R.string.err_privado);
            case "unavailable":
                return context.getString(R.string.err_no_disponible);
            case "unsupported":
                return context.getString(R.string.err_sitio);
            case "network":
                return context.getString(R.string.err_red);
            case "space":
                return context.getString(R.string.err_espacio);
            case "format":
                return context.getString(R.string.err_formato);
            case "reintentos":
                return context.getString(R.string.err_reintentos);
            default:
                String detail = task.errorDetail == null ? kind : task.errorDetail;
                return context.getString(R.string.err_generico, detail);
        }
    }
}
