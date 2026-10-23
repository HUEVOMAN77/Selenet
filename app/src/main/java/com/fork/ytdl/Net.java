package com.fork.ytdl;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

/**
 * Politica de red de la cola. "Solo WiFi" existe porque en un plan de datos una descarga
 * de 1080p se come la bonificacion en dos minutos, y porque con senal floja la bajada
 * corta a mitad: es mejor esperar a tener una red buena que reintentar diez veces.
 *
 * No hay receptor de conexion: el hilo de trabajo pregunta la red cada pocos segundos
 * mientras retiene la tarea, asi que no hace falta que nadie lo despierte.
 */
public final class Net {

    /** Cada cuanto vuelve a mirar la red mientras la cola esta retenida. */
    public static final long POLL_MS = 4000L;

    private static final String PREFS = "ajustes";
    private static final String KEY_WIFI_ONLY = "solo_wifi";
    private static final String KEY_ANNOUNCE = "avisar_terminada";

    private Net() {
    }

    public static boolean wifiOnly(Context context) {
        return prefs(context).getBoolean(KEY_WIFI_ONLY, false);
    }

    public static void setWifiOnly(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, on).apply();
    }

    /**
     * Aviso suelto por cada descarga terminada. Va encendido por defecto porque es lo que
     * uno espera de un descargador: bajar algo con la app cerrada y enterarse. Se apaga
     * para las colas grandes, donde un aviso por video seria ruido.
     */
    public static boolean announce(Context context) {
        return prefs(context).getBoolean(KEY_ANNOUNCE, true);
    }

    public static void setAnnounce(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_ANNOUNCE, on).apply();
    }

    /** Se puede hablar con alguien, por el transporte que sea. */
    public static boolean connected(Context context) {
        ConnectivityManager manager = systemService(context);
        if (manager == null) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = manager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        NetworkInfo info = manager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    /** WiFi o cable. Los datos moviles y el tethering no cuentan. */
    public static boolean wifi(Context context) {
        ConnectivityManager manager = systemService(context);
        if (manager == null) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = network == null
                    ? null : manager.getNetworkCapabilities(network);
            if (capabilities == null) {
                return false;
            }
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        }
        NetworkInfo info = manager.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            return false;
        }
        return info.getType() == ConnectivityManager.TYPE_WIFI
                || info.getType() == ConnectivityManager.TYPE_ETHERNET;
    }

    /**
     * Si la tarea tiene que esperar: con la politica apagada nunca se retiene, y si el
     * telefono no tiene ninguna red tampoco (ahi no hay nada que decidir, hay que avisar).
     */
    public static boolean hold(Context context) {
        if (!wifiOnly(context)) {
            return false;
        }
        return !wifi(context);
    }

    private static ConnectivityManager systemService(Context context) {
        return (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
