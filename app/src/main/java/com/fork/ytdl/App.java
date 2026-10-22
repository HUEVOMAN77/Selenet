package com.fork.ytdl;

import android.app.Application;

/** Arranca antes que la activity y que el servicio; solo deja el gancho de fallos puesto. */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Crash.install(this);
    }
}
