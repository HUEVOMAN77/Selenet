package com.fork.ytdl;

import android.content.Context;

/** Mapa entre el spec de formato que entiende youtube-dl y la etiqueta que ve el usuario. */
public final class Quality {

    public static final String BEST = "best";
    public static final String MP4 = "best[ext=mp4]/best";
    public static final String P360 = "18";
    public static final String AUDIO = "bestaudio[ext=m4a]/bestaudio";
    public static final String SMALL = "worst";

    private static final String PREF = "ajustes";
    private static final String KEY = "calidad";

    private Quality() {
    }

    public static String label(Context context, String spec) {
        int res;
        if (MP4.equals(spec)) {
            res = R.string.q_mp4;
        } else if (P360.equals(spec)) {
            res = R.string.q_360;
        } else if (AUDIO.equals(spec)) {
            res = R.string.q_audio;
        } else if (SMALL.equals(spec)) {
            res = R.string.q_chica;
        } else {
            res = R.string.q_mejor;
        }
        return context.getString(res);
    }

    /** La ultima calidad elegida; mientras no se elija, mejor automatico. */
    public static String saved(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, BEST);
    }

    public static void save(Context context, String spec) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, spec).apply();
    }
}
