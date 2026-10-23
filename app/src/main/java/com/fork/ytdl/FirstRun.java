package com.fork.ytdl;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Dueño de las preferencias de apertura: cuanto intro se muestra, si la guia de inicio
 * ya fue vista y si las visitas de la pantalla de Descargas estan hechas.
 *
 * Vive aparte de SettingsFragment porque lo consulta IntroActivity antes de inflar nada.
 * Todo se guarda en el mismo archivo "ajustes" que usa la calidad y la carpeta.
 */
public final class FirstRun {

    public static final String PREFS = "ajustes";

    public static final int INTRO_OFF = 0;
    public static final int INTRO_SHORT = 1;
    public static final int INTRO_FULL = 2;

    private static final String KEY_INTRO = "intro_modo";
    private static final String KEY_ONBOARDING = "guia_vista";
    private static final String KEY_COACH = "visitas_hechas";

    /**
     * El proceso sigue vivo cuando salis de la app y volves al toque. En ese caso el
     * motor de Python ya esta levantado y mostrar 4 segundos de intro es tiempo perdido,
     * asi que el modo Completo se achica a Corto. Es memoria del proceso, no un flag
     * que se escriba: se reinuta solo cuando Android mata la app.
     */
    private static boolean warm;

    private FirstRun() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int introMode(Context context) {
        int mode = prefs(context).getInt(KEY_INTRO, INTRO_FULL);
        if (mode < INTRO_OFF || mode > INTRO_FULL) {
            return INTRO_FULL;
        }
        return mode;
    }

    public static void setIntroMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_INTRO, mode).apply();
    }

    /** Duracion real del intro: 4 s en frio, 2.5 s si el proceso ya estaba caliente. */
    public static boolean fullIntro(Context context) {
        return !warm && introMode(context) == INTRO_FULL;
    }

    public static void markWarm() {
        warm = true;
    }

    /** El intro se salta cuando esta apagado y la guia ya no puede aparecer. */
    public static boolean showIntro(Context context) {
        return introMode(context) != INTRO_OFF;
    }

    public static boolean needsOnboarding(Context context) {
        return !prefs(context).getBoolean(KEY_ONBOARDING, false);
    }

    public static void onboardingDone(Context context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING, true).apply();
    }

    public static void resetOnboarding(Context context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING, false).apply();
    }

    public static boolean needsCoach(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING, false)
                && !prefs(context).getBoolean(KEY_COACH, false);
    }

    public static void coachDone(Context context) {
        prefs(context).edit().putBoolean(KEY_COACH, true).apply();
    }
}
