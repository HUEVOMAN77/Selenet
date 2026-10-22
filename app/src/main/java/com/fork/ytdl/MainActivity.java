package com.fork.ytdl;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.fork.ytdl.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private String pendingUrl;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
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
        return text == null || text.trim().isEmpty() ? null : text.trim();
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
}
