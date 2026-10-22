package com.fork.ytdl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Cola de descargas; sigue funcionando con la app cerrada gracias al servicio en primer plano. */
public class DownloadService extends Service {

    public static final String ACTION_PAUSE_ALL = "com.fork.ytdl.PAUSE_ALL";
    public static final String ACTION_RESUME = "com.fork.ytdl.RESUME";

    private static final String CHANNEL = "descargas";
    private static final int NOTIF_ID = 4711;
    private static final long[] BACKOFF = {2000, 5000, 10000};
    private static final String PREFS = "cola";
    /** Hasta ahi llega la barra mientras se baja el MP4 temporal del modo audio. */
    private static final int DOWNLOAD_CEILING = 90;

    private static final List<Task> QUEUE = new ArrayList<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final ConcurrentHashMap<String, String> STOPS = new ConcurrentHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Context app;
    private static volatile boolean running;
    private static volatile boolean pauseAll;

    private Thread worker;
    private final Listener notifier = new Listener() {
        @Override
        public void onQueueChanged() {
            if (running) {
                updateNotification();
            }
        }
    };

    public interface Listener {
        void onQueueChanged();
    }

    // ---------------------------------------------------------------- estado

    /** Toda la cola vive en estaticos; hace falta un context para persistir y arrancar el servicio. */
    private static void use(Context context) {
        if (context != null) {
            app = context.getApplicationContext();
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static List<Task> snapshot() {
        synchronized (QUEUE) {
            return new ArrayList<>(QUEUE);
        }
    }

    public static void addListener(Listener listener) {
        synchronized (LISTENERS) {
            if (!LISTENERS.contains(listener)) {
                LISTENERS.add(listener);
            }
        }
    }

    public static void removeListener(Listener listener) {
        synchronized (LISTENERS) {
            LISTENERS.remove(listener);
        }
    }

    // ---------------------------------------------------------------- acciones

    public static void enqueue(Context context, String url, String fmt) {
        enqueue(context, url, fmt, null);
    }

    public static void enqueue(Context context, String url, String fmt, String label) {
        use(context);
        Task task = new Task(url, fmt == null ? "best" : fmt);
        task.label = label;
        synchronized (QUEUE) {
            QUEUE.add(task);
        }
        Logs.append(task, "agregada: " + url);
        persist();
        notifyUi();
        start(ACTION_RESUME);
    }

    public static void pause(Context context, Task task) {
        use(context);
        if (Task.RUNNING.equals(task.state)) {
            requestStop(task, Engine.PAUSE);
            Logs.append(task, "pausando...");
        } else if (task.isLive()) {
            task.state = Task.PAUSED;
            persist();
            notifyUi();
        }
    }

    public static void resume(Context context, Task task) {
        use(context);
        if (Task.PAUSED.equals(task.state) || Task.ERROR.equals(task.state)) {
            task.state = Task.QUEUED;
            task.attempts = 0;
            task.errorKind = null;
            pauseAll = false;
            persist();
            notifyUi();
            start(ACTION_RESUME);
        }
    }

    public static void cancel(Context context, Task task) {
        use(context);
        if (Task.RUNNING.equals(task.state)) {
            requestStop(task, Engine.CANCEL);
            Logs.append(task, "cancelando...");
            return;
        }
        drop(task);
    }

    public static void pauseAll(Context context) {
        use(context);
        pauseAll = true;
        for (Task task : snapshot()) {
            if (Task.RUNNING.equals(task.state)) {
                requestStop(task, Engine.PAUSE);
            } else if (Task.QUEUED.equals(task.state)) {
                task.state = Task.PAUSED;
            }
        }
        persist();
        notifyUi();
    }

    public static void resumeAll(Context context) {
        use(context);
        pauseAll = false;
        for (Task task : snapshot()) {
            if (Task.PAUSED.equals(task.state)) {
                task.state = Task.QUEUED;
            }
        }
        persist();
        notifyUi();
        start(ACTION_RESUME);
    }

    public static int cleanupOrphans(Context context) {
        use(context);
        return cleanupOrphans();
    }

    /** Saca una tarea terminada de la lista guardada (el archivo ya se borro aparte). */
    public static void forget(Context context, Task task) {
        use(context);
        synchronized (QUEUE) {
            QUEUE.remove(task);
        }
        Logs.forget(task);
        persist();
        notifyUi();
    }

    private static void requestStop(Task task, String action) {
        STOPS.put(task.id, action);
        Engine.request(task.id, action);
    }

    /** Borra los .part/.ytdl y los temporales de audio que ya no pertenecen a ninguna tarea. */
    private static int cleanupOrphans() {
        if (app == null) {
            return 0;
        }
        File dir = Storage.engineDir(app);
        List<String> owned = new ArrayList<>();
        for (Task task : snapshot()) {
            if (task.isLive() && task.tmp != null) {
                owned.add(task.tmp);
                // el .ytdl del corte en curso es hermano del .part: sin él no se reanuda
                owned.add(task.tmp + ".ytdl");
            }
            if (task.path != null) {
                owned.add(task.path);
            }
            if (task.fallback != null) {
                owned.add(task.fallback);
            }
        }
        int deleted = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!name.endsWith(".part") && !name.endsWith(".ytdl")) {
                    continue;
                }
                String stem = name.replace(".ytdl", "").replace(".part", "");
                boolean keep = false;
                for (String path : owned) {
                    if (path.contains(stem)) {
                        keep = true;
                    }
                }
                if (!keep && file.delete()) {
                    deleted++;
                    Logs.append(null, "borrado huerfano: " + name);
                }
            }
        }
        // el modo audio escribe su MP4 en la caché: lo que ninguna tarea ya reclama se tira
        int cache = Storage.cleanAudioCache(app, owned);
        if (cache > 0) {
            Logs.append(null, "temporales de audio borrados: " + cache);
        }
        return deleted + cache;
    }

    // ---------------------------------------------------------------- servicio

    @Override
    public void onCreate() {
        super.onCreate();
        use(this);
        Engine.init(this);
        createChannel();
        restoreQueue();
        cleanupOrphans();
        addListener(notifier);
        registerReceiver(controlReceiver, filter());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        try {
            startForeground(NOTIF_ID, buildNotification());
        } catch (Exception e) {
            Logs.append(null, "error startForeground: " + e.getMessage());
        }
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PAUSE_ALL.equals(action)) {
                pauseAll(this);
            } else if (ACTION_RESUME.equals(action)) {
                resumeAll(this);
            }
        }
        ensureWorker();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        pauseAll = true;
        removeListener(notifier);
        for (Task task : snapshot()) {
            if (Task.RUNNING.equals(task.state)) {
                requestStop(task, Engine.PAUSE);
            }
        }
        try {
            unregisterReceiver(controlReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PAUSE_ALL.equals(intent.getAction())) {
                pauseAll(DownloadService.this);
            } else if (ACTION_RESUME.equals(intent.getAction())) {
                resumeAll(DownloadService.this);
            }
        }
    };

    private static IntentFilter filter() {
        IntentFilter intentFilter = new IntentFilter(ACTION_PAUSE_ALL);
        intentFilter.addAction(ACTION_RESUME);
        return intentFilter;
    }

    // ---------------------------------------------------------------- worker

    private void ensureWorker() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        worker = new Thread(this::workerLoop, "ydl-worker");
        worker.start();
    }

    private void workerLoop() {
        Task task;
        while ((task = nextPending()) != null) {
            if (pauseAll) {
                task.state = Task.PAUSED;
                persist();
                notifyUi();
                continue;
            }
            runTask(task);
        }
        finishUp();
    }

    private Task nextPending() {
        synchronized (QUEUE) {
            for (Task task : QUEUE) {
                if (Task.QUEUED.equals(task.state)) {
                    return task;
                }
            }
        }
        return null;
    }

    private void runTask(Task task) {
        STOPS.remove(task.id);
        task.state = Task.RUNNING;
        task.errorKind = null;
        task.errorDetail = null;
        task.percent = 0;
        task.fallback = null;
        Logs.append(task, "iniciando (" + (task.attempts + 1) + ") " + task.url);
        persist();
        notifyUi();

        Sink sink = new Sink(task);
        boolean ok;
        try {
            ok = Quality.isAudio(task.fmt)
                    ? runAudio(task, sink)
                    : Boolean.TRUE.equals(
                            Engine.runResult(task.id, task.url, Storage.engineDir(this),
                                    task.fmt, sink));
        } catch (Exception e) {
            ok = false;
            task.errorKind = "error";
            task.errorDetail = String.valueOf(e.getMessage());
            Logs.append(task, "excepcion: " + e);
        }
        task.stage = null;

        String stop = STOPS.remove(task.id);
        if (stop != null) {
            if (Engine.CANCEL.equals(stop)) {
                drop(task);
                return;
            }
            task.state = Task.PAUSED;
            Logs.append(task, "pausada en " + task.percent + "%");
        } else if (ok) {
            task.state = Task.DONE;
            task.percent = 100;
            task.finishedAt = System.currentTimeMillis();
            task.path = task.path == null ? null : Storage.publish(this, task.path);
            task.bytes = Storage.size(this, task.path);
            // el audio se registra como audio en MediaStore; si no se puede, escaneo normal
            if (!Quality.isAudio(task.fmt)
                    || !Storage.registerAudio(this, fileOf(task.path))) {
                Storage.scan(this, task.path);
            }
            HistoryDb.get(this).put(task);
            Logs.append(task, "terminada: " + Storage.nameOf(task.path));
        } else {
            String kind = task.errorKind == null ? "error" : task.errorKind;
            boolean network = "network".equals(kind);
            if (network && task.attempts < Task.MAX_RETRIES) {
                task.attempts++;
                task.state = Task.QUEUED;
                long wait = BACKOFF[Math.min(task.attempts - 1, BACKOFF.length - 1)];
                Logs.append(task, "corte de red, reintento " + task.attempts + " en "
                        + wait / 1000 + "s");
                sleep(wait);
            } else if (network) {
                task.state = Task.ERROR;
                task.errorKind = "reintentos";
            } else {
                task.state = Task.ERROR;
            }
        }
        Engine.forget(task.id);
        persist();
        notifyUi();
    }

    private static void drop(Task task) {
        synchronized (QUEUE) {
            QUEUE.remove(task);
        }
        deletePartials(task);
        cleanupOrphans();
        Engine.forget(task.id);
        Logs.forget(task);
        persist();
        notifyUi();
    }

    private static void deletePartials(Task task) {
        if (task.tmp != null) {
            File partial = new File(task.tmp);
            if (partial.exists() && partial.delete()) {
                Logs.append(task, "corte borrado: " + partial.getName());
            }
            File meta = new File(partial.getAbsolutePath() + ".ytdl");
            if (meta.exists()) {
                meta.delete();
            }
        }
    }

    private void finishUp() {
        boolean anyLive = false;
        for (Task task : snapshot()) {
            if (task.isLive()) {
                anyLive = true;
            }
        }
        if (!anyLive) {
            stopForeground(true);
            stopSelf();
            running = false;
        }
        updateNotification();
    }

    // ---------------------------------------------------------------- sinks

    private class Sink {
        private final Task task;
        /** Tope de la barra: 100 lo de siempre, 90 mientras se baja el temporal de audio. */
        private int ceiling = 100;
        /** El archivo que dejo el motor; en el modo audio es el MP4 temporal de la caché. */
        private File produced;

        Sink(Task task) {
            this.task = task;
        }

        public void onLog(String msg) {
            Logs.append(task, msg);
        }

        public void onTitle(String title) {
            task.title = title;
            notifyUi();
        }

        public void onProgress(int percent, String speed, String eta,
                               String filename, String tmpfilename) {
            task.percent = percent * ceiling / 100;
            task.speed = speed == null ? "" : speed;
            task.eta = eta == null ? "" : eta;
            // en el modo audio el archivo vive en la caché: todavia no es la ruta de salida
            if (filename != null && ceiling == 100) task.path = filename;
            if (tmpfilename != null) task.tmp = tmpfilename;
            notifyUi();
        }

        public void onDone(String path, long bytes) {
            produced = new File(path);
            task.path = path;
            task.bytes = bytes;
            task.tmp = null;
            if (ceiling < 100) {
                // el temporal vive en la caché: task.tmp lo marca como archivo propio,
                // asi "Limpiar cortadas" no lo borra mientras la extraccion lo esta usando
                task.path = null;
                task.tmp = path;
            }
        }

        public void onError(String kind, String detail) {
            task.errorKind = kind;
            task.errorDetail = detail;
            Logs.append(task, "ERROR (" + kind + "): " + detail);
        }

        public void onStopped(String action, String partial) {
            STOPS.put(task.id, action);
            task.tmp = partial;
        }
    }

    // ---------------------------------------------------------------- notificacion

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, getString(R.string.notif_canal),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, flags());

        String action = pauseAll ? ACTION_RESUME : ACTION_PAUSE_ALL;
        String label = getString(pauseAll ? R.string.notif_start_all : R.string.notif_pause_all);
        PendingIntent control = PendingIntent.getBroadcast(this, pauseAll ? 2 : 1,
                new Intent(action).setPackage(getPackageName()), flags());

        Task active = null;
        Task ended = null;
        boolean live = false;
        for (Task task : snapshot()) {
            if (Task.RUNNING.equals(task.state)) {
                active = task;
            }
            if (task.isLive()) {
                live = true;
            }
            if (Task.DONE.equals(task.state) || Task.ERROR.equals(task.state)) {
                ended = task;
            }
        }
        String title;
        String text;
        if (active != null) {
            title = getString(R.string.notif_titulo);
            text = active.displayName() + " · " + active.percent + "% · " + active.speed
                    + (active.eta.isEmpty() ? "" : " · " + active.eta);
        } else if (ended != null) {
            // la cola ya se vacio: el aviso dice como termino, y se puede cerrar
            boolean fallo = Task.ERROR.equals(ended.state);
            title = getString(fallo ? R.string.notif_titulo_error : R.string.notif_titulo_listo);
            text = fallo ? Errors.reason(this, ended.errorKind, ended.errorDetail)
                    : Storage.nameOf(ended.path);
        } else {
            title = getString(R.string.notif_dejo);
            text = getString(R.string.notif_tap);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                // con la cola vacia el aviso tiene que poder cerrarse
                .setOngoing(live)
                .setContentTitle(title)
                .setContentText(text)
                .setNumber(active == null ? 0 : active.percent)
                .addAction(pauseAll ? android.R.drawable.ic_media_play
                        : android.R.drawable.ic_media_pause, label, control)
                .setContentIntent(content);
        if (active != null && active.percent > 0) {
            builder.setProgress(100, active.percent, false);
        } else {
            // sin descarga en curso no tiene que quedar el spinner dando vueltas
            builder.setProgress(0, 0, false);
        }
        return builder.build();
    }

    private int flags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void updateNotification() {
        // finishUp() llama a stopSelf() y deja colgado un notifyUi() en la cola del main;
        // aca getApplicationContext() ya puede ser null y el post revienta la app.
        Context context = app == null ? getApplicationContext() : app;
        if (context == null) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIF_ID, buildNotification());
            }
        } catch (Exception e) {
            Logs.append(null, "error notificacion: " + e);
        }
    }

    // ---------------------------------------------------------------- persistencia

    private static void notifyUi() {
        MAIN.post(() -> {
            synchronized (LISTENERS) {
                for (Listener listener : new ArrayList<>(LISTENERS)) {
                    listener.onQueueChanged();
                }
            }
        });
    }

    private static void persist() {
        if (app == null) {
            return;
        }
        JSONArray array = new JSONArray();
        synchronized (QUEUE) {
            for (Task task : QUEUE) {
                if (task.isLive()) {
                    array.put(task.toJson());
                }
            }
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("tasks", array.toString()).apply();
    }

    private void restoreQueue() {
        synchronized (QUEUE) {
            if (!QUEUE.isEmpty()) {
                return;
            }
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString("tasks", "[]");
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject json = array.getJSONObject(i);
                    Task task = new Task(json);
                    if (Task.RUNNING.equals(task.state)) {
                        task.state = Task.QUEUED;
                    }
                    if (Task.DONE.equals(task.state)) {
                        continue;   // las terminadas viven en la base del historial
                    }
                    QUEUE.add(task);
                }
            } catch (Exception ignored) {
            }
        }
        notifyUi();
    }

    private static void start(String action) {
        if (app == null) {
            return;
        }
        Intent intent = new Intent(action).setClassName(app, DownloadService.class.getName());
        if (ACTION_RESUME.equals(action)) {
            // El servicio ya puede estar andando; solo hace falta levantar el worker.
            try {
                app.startService(intent);
            } catch (Exception e) {
                Logs.append(null, "error startService: " + e.getMessage());
            }
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (Exception e) {
            Logs.append(null, "error startForegroundService: " + e.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }

    // ---------------------------------------------------------------- modo audio

    /**
     * "Solo audio" sin ffmpeg: baja un MP4 progresivo a la caché (ocupa de 0% a 90% de la
     * barra) y después MediaExtractor/MediaMuxer sacan la pista de audio (90% a 100%).
     * El destino del usuario nunca ve el temporal; si algo falla, el temporal se borra o
     * queda a mano como reserva para "Guardar el MP4 completo".
     */
    private boolean runAudio(Task task, Sink sink) {
        File cache = Storage.audioCache(this);
        task.stage = Task.STAGE_DOWNLOAD;
        sink.ceiling = DOWNLOAD_CEILING;
        notifyUi();

        boolean down;
        try {
            down = Boolean.TRUE.equals(Engine.runResult(task.id, task.url, cache, task.fmt,
                    sink, "audio-" + task.id));
        } catch (Exception e) {
            task.errorKind = "error";
            task.errorDetail = String.valueOf(e.getMessage());
            Logs.append(task, "excepcion en la descarga de audio: " + e);
            return false;
        }
        if (!down || STOPS.containsKey(task.id)) {
            return false;   // el motor ya piso kind/detail, o pidieron cortar
        }
        File source = sink.produced;
        if (source == null || !source.isFile() || source.length() <= 0) {
            task.errorKind = "sin_destino";
            task.errorDetail = "el temporal de audio no quedo en " + cache;
            Logs.append(task, "temporal de audio ausente o vacio");
            return false;
        }

        task.stage = Task.STAGE_EXTRACT;
        task.percent = DOWNLOAD_CEILING;
        task.speed = "";
        task.eta = "";
        notifyUi();
        String base = Storage.safeBase(task.title == null ? source.getName() : task.title);
        File output = Storage.unique(Storage.engineDir(this), base, "m4a");
        // se escribe con .part al lado: si algo sale mal el resto se barre como cortada, y
        // mientras vive es el "tmp" de la tarea, asi "Limpiar cortadas" no lo toca
        File target = new File(output.getAbsolutePath() + ".part");
        task.tmp = target.getAbsolutePath();
        // el MP4 de la caché pasa a ser reserva: si la extraccion falla se puede guardar tal cual
        task.fallback = source.getAbsolutePath();
        try {
            String mime = AudioRemux.extract(source, target, new AudioRemux.Watch() {
                @Override
                public void onStage(int percent) {
                    task.percent = Math.min(99, DOWNLOAD_CEILING + percent / 10);
                    notifyUi();
                }

                @Override
                public boolean cancelled() {
                    return STOPS.containsKey(task.id);
                }
            });
            long size = target.length();
            if (size <= 0) {
                deleteQuietly(target);
                task.errorKind = "audio_vacio";
                task.errorDetail = "el m4a salio de 0 bytes";
                Logs.append(task, "extraccion sin muestras");
                return false;
            }
            if (!target.renameTo(output)) {
                deleteQuietly(target);
                task.errorKind = "sin_destino";
                task.errorDetail = "no se pudo dejar " + output.getName() + " en el destino";
                Logs.append(task, "sin destino para el m4a");
                return false;
            }
            task.path = output.getAbsolutePath();
            task.bytes = size;
            task.tmp = null;
            task.fallback = null;
            deleteQuietly(source);
            Logs.append(task, "audio " + mime + " separado en " + output.getName());
            return true;
        } catch (AudioRemux.Failure failure) {
            deleteQuietly(target);   // el m4a a medio escribir no sirve
            task.errorKind = failure.kind;
            task.errorDetail = failure.getMessage();
            task.tmp = null;
            Logs.append(task, "extraccion (" + failure.kind + "): " + failure.getMessage());
            if (STOPS.containsKey(task.id)) {
                // cortaron de verdad: no queda nada en la caché
                task.fallback = null;
                deleteQuietly(source);
            }
            return false;
        } finally {
            task.stage = null;
        }
    }

    private static void deleteQuietly(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (!file.delete()) {
            Logs.append(null, "no se pudo borrar " + file.getName());
        }
    }

    /** File del resultado final, o null si ya es un documento SAF (ahi MediaStore no aplica). */
    private static java.io.File fileOf(String path) {
        return path == null || path.startsWith("content://") ? null : new File(path);
    }

    /**
     * Acepta la reserva: sube el MP4 temporal que dejo el modo audio al destino del usuario
     * con el nombre del video, para no perder la descarga cuando el audio no se pudo separar.
     */
    public static boolean saveFallback(Context context, Task task) {
        use(context);
        File source = task.fallback == null ? null : new File(task.fallback);
        if (source == null || !source.isFile() || source.length() <= 0) {
            task.fallback = null;
            return false;
        }
        String base = Storage.safeBase(task.title == null ? source.getName() : task.title);
        File staged = Storage.unique(Storage.engineDir(context), base,
                extension(source.getName()));
        if (!Storage.move(source, staged)) {
            return false;   // la reserva sigue en la caché; el que llama avisa del fallo
        }
        task.fallback = null;
        task.path = Storage.publish(context, staged.getAbsolutePath());
        task.bytes = Storage.size(context, task.path);
        task.percent = 100;
        task.state = Task.DONE;
        task.errorKind = null;
        task.errorDetail = null;
        task.finishedAt = System.currentTimeMillis();
        Storage.scan(context, task.path);
        HistoryDb.get(context).put(task);
        Logs.append(task, "reserva guardada: " + Storage.nameOf(task.path));
        synchronized (QUEUE) {
            QUEUE.remove(task);
        }
        Logs.forget(task);
        persist();
        notifyUi();
        return true;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "mp4";
    }
}
