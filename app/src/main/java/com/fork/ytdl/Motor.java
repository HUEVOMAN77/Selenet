package com.fork.ytdl;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Baja el codigo del motor desde el master de ytdl-org/youtube-dl. */
public final class Motor {

    public static final String SOURCE
            = "https://codeload.github.com/ytdl-org/youtube-dl/tar.gz/refs/heads/master";

    public interface Progress {
        void on(int percent, long downloaded);
    }

    private Motor() {
    }

    /** Descarga el tarball al almacenamiento interno y devuelve su ruta. */
    public static String download(Context context, Progress progress) throws IOException {
        File dir = Engine.motorDir(context);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("no se pudo crear la carpeta del motor");
        }
        File out = new File(dir, "motor.tar.gz");
        HttpURLConnection conn = (HttpURLConnection) new URL(SOURCE).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("el servidor respondio " + code);
            }
            long total = conn.getContentLengthLong();
            try (InputStream in = conn.getInputStream();
                 FileOutputStream file = new FileOutputStream(out)) {
                byte[] buffer = new byte[32768];
                long done = 0;
                int last = -1;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    file.write(buffer, 0, read);
                    done += read;
                    int percent = total > 0 ? (int) (done * 100 / total) : -1;
                    if (progress != null && percent != last) {
                        last = percent;
                        progress.on(percent, done);
                    }
                }
                if (done < 4096) {
                    throw new IOException("la descarga salio vacia (" + done + " bytes)");
                }
            }
        } finally {
            conn.disconnect();
        }
        return out.getAbsolutePath();
    }
}
