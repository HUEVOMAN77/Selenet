package com.fork.ytdl;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

/** Arranca antes que la activity y que el servicio; deja el gancho de fallos y la paleta. */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Crash.install(this);
        // En Android 12 y mas, los envoltorios (tarjetas, superficies, hojas) siguen el color
        // del papel tapiz. La menta de la marca se queda fija en los botones y en los acentos,
        // asi la app se adapta sin dejar de parecer Selenet. En versiones anteriores no pasa
        // nada: queda la paleta propia del tema.
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
