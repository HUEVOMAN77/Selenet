package com.fork.ytdl;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Comparator;

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

    /**
     * Alturas que se pueden elegir en Ajustes, de la mas baja a la mas alta.
     * 1080p es el techo real: arriba de eso YouTube entrega video y audio por separado
     * y aca no hay con que juntarlos, porque la app no lleva ffmpeg. Esos formatos se listan
     * marcados como "Requiere PC" y no se descargan.
     */
    public static final int[] HEIGHTS = {144, 240, 360, 480, 720, 1080};

    private static final String PREF = "ajustes";
    private static final String KEY = "calidad";
    private static final String KEY_LABEL = "calidad_label";
    private static final String KEY_AUDIO = "calidad_audio";
    private static final String KEY_AUDIO_LABEL = "calidad_audio_label";

    private Quality() {
    }

    /**
     * Spec para "lo mejor hasta tal altura", siempre con video y audio juntos (sin merge).
     * El fork de youtube-dl de la app no ordena los formatos, asi que "best" es el ultimo
     * de la lista del extractor y no el de mejor calidad: por eso la escala arranca con los
     * itags progresivos de YouTube (17, 36, 18, 59, 22, 37) ordenados del exacto hacia abajo
     * y despues hacia arriba, y deja los filtros de altura como red para los otros sitios.
     */
    public static String spec(int height) {
        return ladder(height)
                + "best[ext=mp4][vcodec!=none][acodec!=none][height<=" + height + "]"
                + "/best[vcodec!=none][acodec!=none][height<=" + height + "]"
                + "/best[ext=mp4]/best";
    }

    /**
     * Spec del modo "Solo audio" hasta tal altura. Como el audio va dentro del MP4
     * progresivo, la unica forma de mejorar el sonido es pedir un progresivo mas grande:
     * 17 trae ~24 kbps, 18 ~96 kbps, 22 ~128 kbps y 37 ~192 kbps. Termina en worstaudio,
     * que es la sena que usa isAudio() para mandarlo por el camino de extraccion.
     */
    public static String audioSpec(int height) {
        return ladder(height)
                + "best[ext=mp4][vcodec!=none][acodec!=none][height<=" + height + "]"
                + "/best[vcodec!=none][acodec!=none][height<=" + height + "]"
                + "/worstaudio[ext=m4a]/bestaudio[ext=m4a]/worstaudio/bestaudio/best";
    }

    /** itags progresivos historicos de YouTube y la altura que representan. */
    private static final int[] CLASSIC_IDS = {17, 36, 18, 59, 22, 37};
    private static final int[] CLASSIC_HEIGHTS = {144, 240, 360, 480, 720, 1080};

    /**
     * Los itags de la escala, del que coincide exacto hacia las alturas mas bajas y por
     * ultimo las mas altas: quedarse corto gasta menos de lo que promise, pasarse no.
     */
    private static String ladder(int height) {
        Integer[] order = new Integer[CLASSIC_IDS.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        final int wanted = height;
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int cost = cost(CLASSIC_HEIGHTS[a], wanted) - cost(CLASSIC_HEIGHTS[b], wanted);
                return cost != 0 ? cost : Integer.compare(CLASSIC_HEIGHTS[a], CLASSIC_HEIGHTS[b]);
            }
        });
        StringBuilder text = new StringBuilder();
        for (int i : order) {
            text.append(CLASSIC_IDS[i]).append('/');
        }
        return text.toString();
    }

    /** Que tan lejos queda un itag de lo pedido; pasar la altura cuesta el triple que no llegar. */
    private static int cost(int have, int wanted) {
        int diff = Math.abs(have - wanted);
        return have > wanted ? diff * 3 + 1 : diff;
    }

    /** La altura de una preset elegida en Ajustes; 0 si el spec es de otra familia. */
    public static int heightOf(String spec) {
        if (spec == null) {
            return 0;
        }
        for (int height : HEIGHTS) {
            if (spec(height).equals(spec)) {
                return height;
            }
        }
        return 0;
    }

    /** Igual que heightOf pero para la escala de "Solo audio". */
    public static int audioHeightOf(String spec) {
        if (spec == null) {
            return 0;
        }
        for (int height : HEIGHTS) {
            if (audioSpec(height).equals(spec)) {
                return height;
            }
        }
        return 0;
    }

    public static String label(Context context, String spec) {
        int height = heightOf(spec);
        if (height > 0) {
            return context.getString(R.string.fmt_altura, height);
        }
        int audio = audioHeightOf(spec);
        if (audio > 0) {
            return context.getString(R.string.fmt_audio_altura, audio);
        }
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

    /**
     * Si el spec es del modo "Solo audio", que se procesa aparte (baja el progresivo y
     * saca la pista). Todos los specs de audio terminan con worstaudio, asi que alcanza
     * con mirar eso; el viejo spec DASH se reconoce por igualdad.
     */
    public static boolean isAudio(String spec) {
        return spec != null && (spec.contains("worstaudio") || AUDIO_DASH.equals(spec));
    }

    /** La ultima calidad elegida; mientras no se elija, mejor automatico. */
    public static String saved(Context context) {
        return prefs(context).getString(KEY, BEST);
    }

    /** La etiqueta visible de esa ultima eleccion. */
    public static String savedLabel(Context context) {
        String text = prefs(context).getString(KEY_LABEL, null);
        return text == null || text.isEmpty() ? label(context, saved(context)) : text;
    }

    /** La calidad elegida para el modo "Solo audio"; por defecto la mas barata. */
    public static String savedAudio(Context context) {
        return prefs(context).getString(KEY_AUDIO, AUDIO);
    }

    public static String savedAudioLabel(Context context) {
        String text = prefs(context).getString(KEY_AUDIO_LABEL, null);
        if (text != null && !text.isEmpty()) {
            return text;
        }
        String spec = savedAudio(context);
        return AUDIO.equals(spec)
                ? context.getString(R.string.ajustes_calidad_audio_auto)
                : label(context, spec);
    }

    /**
     * La calidad guardada pero apta para video: si lo que esta guardado es "Solo audio"
     * se usa la mejor automatica, porque el boton Solo MP4 nunca debe sacar audio.
     */
    public static String savedVideo(Context context) {
        String saved = saved(context);
        return isAudio(saved) ? BEST : saved;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context context, String spec) {
        prefs(context).edit().putString(KEY, spec).apply();
    }

    public static void save(Context context, String spec, String text) {
        prefs(context).edit().putString(KEY, spec).putString(KEY_LABEL, text).apply();
    }

    public static void saveAudio(Context context, String spec, String text) {
        prefs(context).edit()
                .putString(KEY_AUDIO, spec).putString(KEY_AUDIO_LABEL, text).apply();
    }
}
