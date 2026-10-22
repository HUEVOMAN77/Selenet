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

    /** Borra los .part/.ytdl que no pertenecen a una descarga activa o pausada. */
    private static int cleanupOrphans() {
        if (app == null) {
            return 0;
        }
        File dir = Storage.engineDir(app);
        List<String> owned = new ArrayList<>();
        for (Task task : snapshot()) {
            if (task.isLive() && task.tmp != null) {
                owned.add(task.tmp);
            }
        }
        int deleted = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
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
        return deleted;
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
        Logs.append(task, "iniciando (" + (task.attempts + 1) + ") " + task.url);
        persist();
        notifyUi();

        Sink sink = new Sink(task);
        boolean ok;
        try {
            ok = Boolean.TRUE.equals(
                    Engine.runResult(task.id, task.url, Storage.engineDir(this), task.fmt, sink));
        } catch (Exception e) {
            ok = false;
            task.errorKind = "error";
            task.errorDetail = String.valueOf(e.getMessage());
            Logs.append(task, "excepcion: " + e);
        }

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
            Storage.scan(this, task.path);
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
            task.percent = percent;
            task.speed = speed == null ? "" : speed;
            task.eta = eta == null ? "" : eta;
            if (filename != null) task.path = filename;
            if (tmpfilename != null) task.tmp = tmpfilename;
            notifyUi();
        }

        public void onDone(String path, long bytes) {
            task.path = path;
            task.bytes = bytes;
            task.tmp = null;
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
        for (Task task : snapshot()) {
            if (Task.RUNNING.equals(task.state)) {
                active = task;
            }
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setContentTitle(active == null ? getString(R.string.notif_dejo)
                        : getString(R.string.notif_titulo))
                .setContentText(active == null ? getString(R.string.notif_tap)
                        : active.displayName() + " · " + active.percent + "% · " + active.speed
                        + (active.eta.isEmpty() ? "" : " · " + active.eta))
                .setNumber(active == null ? 0 : active.percent)
                .addAction(android.R.drawable.ic_media_pause, label, control)
                .setContentIntent(content);
        if (active != null && active.percent > 0) {
            builder.setProgress(100, active.percent, false);
        } else {
            builder.setProgress(0, 0, true);
        }
        return builder.build();
    }

    private int flags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIF_ID, buildNotification());
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
}
