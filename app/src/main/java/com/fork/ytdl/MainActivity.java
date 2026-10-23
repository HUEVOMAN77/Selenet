package com.fork.ytdl;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.fork.ytdl.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DownloadService.Listener {

    private static final int ASK_STORAGE = 21;
    private static final int ASK_NOTIFY = 22;

    /** El aviso de "terminada" trae la ruta del archivo por aqui, no por un intent suelto. */
    public static final String EXTRA_OPEN = "com.fork.ytdl.ABRIR";
    public static final String EXTRA_OPEN_TYPE = "com.fork.ytdl.ABRIR_TIPO";

    private ActivityMainBinding binding;
    private String pendingUrl;
    private Uri pendingOpen;
    private String pendingOpenType;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // A partir de aca el proceso ya estuvo abierto: el intro se achica la proxima vez.
        FirstRun.markWarm();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });

        pendingUrl = shared(getIntent());
        takeOpen(getIntent());
        if (saved == null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_descargas);
        } else {
            // la activity se recreo (giro, proceso muerto): el link de "Compartir" y el
            // aviso pendiente seguirian en el intent y volverian a entrar solos
            pendingUrl = null;
            pendingOpen = null;
        }
        askStorageIfNeeded();
        askNotifyIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        DownloadService.addListener(this);
        paintBadge();
        consumeOpen();
    }

    @Override
    protected void onPause() {
        DownloadService.removeListener(this);
        super.onPause();
    }

    @Override
    public void onQueueChanged() {
        if (binding != null) {
            paintBadge();
        }
    }

    /**
     * El numero de la pestana Descargas. Sirve para ver si queda cola o se detuvo sin
     * estar mirando esa pantalla; desaparece solo cuando la cola se vacia.
     */
    private void paintBadge() {
        int active = 0;
        for (Task task : DownloadService.snapshot()) {
            if (Task.RUNNING.equals(task.state) || Task.QUEUED.equals(task.state)
                    || Task.PAUSED.equals(task.state)) {
                active++;
            }
        }
        if (active == 0) {
            binding.bottomNav.removeBadge(R.id.nav_descargas);
            return;
        }
        com.google.android.material.badge.BadgeDrawable badge =
                binding.bottomNav.getOrCreateBadge(R.id.nav_descargas);
        badge.setNumber(active);
        badge.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
        badge.setBadgeTextColor(ContextCompat.getColor(this, R.color.bg_top));
        badge.setVisible(true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // hay que guardarlo, no abrirlo ya: desde el fondo del sistema solo se puede
        // arrancar otra interfaz cuando la nuestra acaba de volver a la pantalla
        takeOpen(intent);
        String url = shared(intent);
        if (url != null) {
            pendingUrl = url;
            binding.bottomNav.setSelectedItemId(R.id.nav_descargas);
            show(R.id.nav_descargas);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean denied = results.length > 0 && results[0] != PackageManager.PERMISSION_GRANTED;
        if (denied && requestCode == ASK_STORAGE) {
            Toast.makeText(this, R.string.permiso_saf, Toast.LENGTH_LONG).show();
        } else if (denied && requestCode == ASK_NOTIFY) {
            Toast.makeText(this, R.string.notif_sin_permiso, Toast.LENGTH_LONG).show();
        }
    }

    /** El link que llego por "Compartir" y todavia no se consumo. */
    public String takePendingUrl() {
        String url = pendingUrl;
        pendingUrl = null;
        return url;
    }

    private String shared(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // "Abrir en Selenet" desde otro navegador: el link viene limpio, sin texto al lado
            Uri data = intent.getData();
            return data == null ? null : data.toString();
        }
        if (!Intent.ACTION_SEND.equals(intent.getAction())) {
            return null;
        }
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        return text == null ? null : DownloadFragment.firstUrl(text);
    }

    /** Guarda el archivo que trae el aviso, si es que trae alguno. */
    private void takeOpen(Intent intent) {
        Uri uri = intent == null ? null : intent.getParcelableExtra(EXTRA_OPEN);
        if (uri != null) {
            pendingOpen = uri;
            pendingOpenType = intent.getStringExtra(EXTRA_OPEN_TYPE);
        }
    }

    /**
     * Abre lo que pidio el aviso. Se hace aca y no en onNewIntent porque Android 10+
     * bloquea el arranque de interfaces desde el fondo; con la app ya en pantalla
     * el reproductor entra sin problema.
     */
    private void consumeOpen() {
        Uri uri = pendingOpen;
        if (uri == null) {
            return;
        }
        pendingOpen = null;
        String type = pendingOpenType == null ? "*/*" : pendingOpenType;
        pendingOpenType = null;
        Intent open = new Intent(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setDataAndType(uri, type);
        try {
            startActivity(Intent.createChooser(open, getString(R.string.abrir)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.sin_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void show(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_historial) {
            fragment = new HistoryFragment();
        } else if (itemId == R.id.nav_ajustes) {
            fragment = new SettingsFragment();
        } else {
            fragment = new DownloadFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_host, fragment)
                .commit();
    }

    /** Con targetSdk 28 la carpeta publica de videos necesita este permiso runtime. */
    private void askStorageIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Storage.tree(this) != null
                || Storage.sharedDir() != null) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            List<String> needed = new ArrayList<>();
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            requestPermissions(needed.toArray(new String[0]), ASK_STORAGE);
        }
    }

    /**
     * Android 13+ no muestra ningun aviso sin este permiso, ni el de la cola. Con targetSdk
     * 28 el sistema no lo pide solo al instalar, entonces lo pide la app una vez apenas
     * se abre. Si se niega, las descargas siguen bajando: solo no se ven los avisos.
     */
    private void askNotifyIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, ASK_NOTIFY);
        }
    }
}
