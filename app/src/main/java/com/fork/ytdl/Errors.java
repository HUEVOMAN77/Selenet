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
                // el detalle trae cuanto queda libre; sin ese dato, el aviso generico
                return (detail == null || detail.isEmpty())
                        ? context.getString(R.string.err_espacio)
                        : context.getString(R.string.err_espacio_full, detail);
            case "format":
                return context.getString(R.string.err_formato);
            case "reintentos":
                return context.getString(R.string.err_reintentos);
            case "sin_clientes":
                return context.getString(R.string.err_sin_clientes);
            case "no_audio":
                return context.getString(R.string.err_sin_audio);
            case "no_video":
                return context.getString(R.string.err_sin_video);
            case "merge":
                return context.getString(R.string.err_union);
            case "audio_vacio":
                return context.getString(R.string.err_audio_vacio);
            case "extraccion":
                return context.getString(R.string.err_extraccion);
            case "sin_destino":
                return context.getString(R.string.err_sin_destino);
            case "cancel":
                return context.getString(R.string.err_cancelado);
            default:
                return context.getString(R.string.err_generico, detail == null ? safe : detail);
        }
    }
}
