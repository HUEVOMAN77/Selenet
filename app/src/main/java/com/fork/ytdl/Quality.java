package com.fork.ytdl;

import android.content.Context;

/** Mapa entre el spec de formato que entiende youtube-dl y la etiqueta que ve el usuario. */
public final class Quality {

    public static final String BEST = "best";
    public static final String MP4 = "best[ext=mp4]/best";
    public static final String P360 = "18";
    /**
     * Modo "Solo audio": no pide un formato de audio suelto (DASH), sino el mismo MP4
     * progresivo que ya baja bien en modo MP4, empezando por la calidad mas barata porque
     * del archivo solo interesa la pista de audio. Si el cliente no ofrece progresivo,
     * cae a audio puro en m4a (tampoco baja video) y por ultimo a cualquier audio.
     * La extraccion la hace MediaMuxer en Java, sin ffmpeg.
     */
    public static final String AUDIO = "worst[ext=mp4][vcodec!=none][acodec!=none]/18/17"
            + "/worstaudio[ext=m4a]/bestaudio[ext=m4a]/worstaudio/bestaudio/best";
    /** El spec viejo (DASH puro); queda para tareas y preferencias guardadas antes de v9. */
    public static final String AUDIO_DASH =
            "bestaudio[protocol=https]/bestaudio[ext=m4a]/bestaudio";
    public static final String SMALL = "worst";

    private static final String PREF = "ajustes";
    private static final String KEY = "calidad";
    private static final String KEY_LABEL = "calidad_label";

    private Quality() {
    }

    public static String label(Context context, String spec) {
        int res;
        if (MP4.equals(spec)) {
            res = R.string.q_mp4;
        } else if (P360.equals(spec)) {
            res = R.string.q_360;
        } else if (AUDIO.equals(spec) || AUDIO_DASH.equals(spec)) {
            res = R.string.q_audio;
        } else if (SMALL.equals(spec)) {
            res = R.string.q_chica;
        } else {
            res = R.string.q_mejor;
        }
        return context.getString(res);
    }

    /** Si el spec es el modo "Solo audio", que se procesa aparte (baja MP4 y saca el audio). */
    public static boolean isAudio(String spec) {
        return AUDIO.equals(spec) || AUDIO_DASH.equals(spec);
    }

    /** La ultima calidad elegida; mientras no se elija, mejor automatico. */
    public static String saved(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, BEST);
    }

    /** La etiqueta visible de esa ultima eleccion. */
    public static String savedLabel(Context context) {
        String saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY_LABEL, null);
        return saved == null ? label(context, saved(context)) : saved;
    }

    public static void save(Context context, String spec) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, spec).apply();
    }

    public static void save(Context context, String spec, String text) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, spec).putString(KEY_LABEL, text).apply();
    }
}
