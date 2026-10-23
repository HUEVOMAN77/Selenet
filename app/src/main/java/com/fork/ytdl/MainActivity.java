package com.fork.ytdl;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.fork.ytdl.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int ASK_STORAGE = 21;

    private ActivityMainBinding binding;
    private String pendingUrl;

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
        if (saved == null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_descargas);
        }
        askStorageIfNeeded();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
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
        if (requestCode == ASK_STORAGE && results.length > 0
                && results[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permiso_saf, Toast.LENGTH_LONG).show();
        }
    }

    /** El link que llego por "Compartir" y todavia no se consumo. */
    public String takePendingUrl() {
        String url = pendingUrl;
        pendingUrl = null;
        return url;
    }

    private String shared(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return null;
        }
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        return text == null ? null : DownloadFragment.firstUrl(text);
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
}
