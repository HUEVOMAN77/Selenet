package com.fork.ytdl;

import android.content.Context;

/** Traduce los tipos de error del motor a mensajes en espanol. */
public final class Errors {

    private Errors() {
    }

    public static String message(Context context, Task task) {
        return reason(context, task.errorKind, task.errorDetail);
    }

    /** Texto en espanol para un tipo de error del motor. */
    public static String reason(Context context, String kind, String detail) {
        String safe = kind == null ? "error" : kind;
        switch (safe) {
            case "private":
                return context.getString(R.string.err_privado);
            case "unavailable":
                return context.getString(R.string.err_no_disponible);
            case "unsupported":
                return context.getString(R.string.err_sitio);
            case "network":
                return context.getString(R.string.err_red);
            case "forbidden":
                return context.getString(R.string.err_bloqueado);
            case "space":
                return context.getString(R.string.err_espacio);
            case "format":
                return context.getString(R.string.err_formato);
            case "reintentos":
                return context.getString(R.string.err_reintentos);
            default:
                return context.getString(R.string.err_generico, detail == null ? safe : detail);
        }
    }
}
