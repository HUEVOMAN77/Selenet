package com.fork.ytdl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Miniaturas de la cola y del historial sin ninguna libreria:
 * busca el id de YouTube en el link, baja i.ytimg.com y lo muestra en el ImageView.
 * Lo que no sea YouTube queda con el fondo degradado; nunca falla la fila por una imagen.
 */
public final class Thumbs {

    /** watch?v=, youtu.be/, shorts, embed y live comparten los 11 caracteres del id. */
    private static final Pattern ID = Pattern.compile(
            "(?:v=|youtu\\.be/|/embed/|/shorts/|/live/|/v/)([A-Za-z0-9_-]{11})");

    /** Cuadrado de la miniatura: se recorta con centerCrop, asi que 240px sobran. */
    private static final int SIDE = 240;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    /** Ids que ya se intentaron y no dieron imagen, para no reintentar en cada redraw. */
    private static final Set<String> FAILED = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 200;
                }
            }));

    private Thumbs() {
    }

    /** Pone la miniatura del video en la imagen; si no se puede, deja el placeholder. */
    public static void show(ImageView view, String url) {
        String key = videoId(url);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (key == null) {
            view.setTag(null);
            view.setImageResource(R.drawable.thumb_placeholder);
            return;
        }
        Bitmap cached = CACHE.get(key);
        view.setTag(key);
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(R.drawable.thumb_placeholder);
        if (FAILED.contains(key)) {
            return;
        }
        POOL.execute(() -> load(key, view, null, null));
    }

    /**
     * Miniatura de un archivo que ya esta en la carpeta: un cuadro del propio video,
     * leido con MediaMetadataRetriever. El audio no tiene cuadro y queda con el fondo.
     */
    public static void showFile(ImageView view, android.content.Context context, String path) {
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (path == null || Storage.isAudioName(path)) {
            view.setTag(null);
            view.setImageResource(R.drawable.thumb_audio);
            return;
        }
        String key = "archivo:" + path;
        Bitmap cached = CACHE.get(key);
        view.setTag(key);
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(R.drawable.thumb_placeholder);
        if (FAILED.contains(key)) {
            return;
        }
        final android.content.Context app = context.getApplicationContext();
        POOL.execute(() -> load(key, view, app, path));
    }

    private static void load(String key, ImageView view,
                             android.content.Context context, String path) {
        Bitmap bitmap = context == null ? fetch(key) : frame(context, path);
        if (bitmap == null) {
            FAILED.add(key);
            return;
        }
        CACHE.put(key, bitmap);
        final Bitmap found = bitmap;
        MAIN.post(() -> {
            // la fila pudo reciclarse con otro video mientras bajaba la imagen
            if (key.equals(view.getTag())) {
                view.setImageBitmap(found);
            }
        });
    }

    /** Un cuadro del archivo, achicado a 240 px para no llenar la memoria con fotogramas. */
    private static Bitmap frame(android.content.Context context, String path) {
        android.media.MediaMetadataRetriever retriever =
                new android.media.MediaMetadataRetriever();
        Bitmap raw = null;
        Bitmap small = null;
        try {
            if (path.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(path));
            } else {
                retriever.setDataSource(path);
            }
            raw = retriever.getFrameAtTime(0,
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            small = shrink(raw);
            return small;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
            // si shrink devolvio el mismo bitmap no se puede tirar aca
            if (raw != null && raw != small && !raw.isRecycled()) {
                raw.recycle();
            }
        }
    }

    private static Bitmap shrink(Bitmap source) {
        if (source == null) {
            return null;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0 || width <= SIDE) {
            return source;
        }
        float ratio = SIDE / (float) width;
        Bitmap small = Bitmap.createScaledBitmap(source, SIDE,
                Math.max(1, Math.round(height * ratio)), true);
        return small == source ? source : small;
    }

    /** Descarga y decodifica la miniatura; null en cualquier problema de red o formato. */
    private static Bitmap fetch(String key) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)
                    new URL("https://i.ytimg.com/vi/" + key + "/mqdefault.jpg").openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (connection.getResponseCode() != 200) {
                return null;
            }
            byte[] raw = read(connection.getInputStream());
            int sample = sampleSize(raw);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
        } catch (Exception e) {
            Logs.append(null, "miniatura " + key + ": " + e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(64 * 1024);
        byte[] buffer = new byte[8 * 1024];
        int n;
        int total = 0;
        while ((n = in.read(buffer)) > 0) {
            total += n;
            // una miniatura real pesa pocos KB; el techo evita colgar la memoria
            if (total > 2 * 1024 * 1024) {
                break;
            }
            out.write(buffer, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    /** mqdefault mide 320x180: con 240 de lado alcanza una sola reducción por mitad. */
    private static int sampleSize(byte[] raw) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
        int width = bounds.outWidth;
        int sample = 1;
        while (width / (sample * 2) >= SIDE) {
            sample *= 2;
        }
        return sample;
    }

    static String videoId(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = ID.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }
}
