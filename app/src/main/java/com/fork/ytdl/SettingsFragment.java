package com.fork.ytdl;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.fork.ytdl.databinding.FragmentSettingsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    private static final SimpleDateFormat DAY =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);
    private static final ExecutorService THREADS = Executors.newSingleThreadExecutor();

    private FragmentSettingsBinding binding;
    private ActivityResultLauncher<Uri> treePicker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;

    @Override
    public void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        treePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), this::chooseTree);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        binding.btnUpdateEngine.setOnClickListener(v -> update(false));
        binding.btnUpdateEngine.setOnLongClickListener(v -> {
            confirmReset();
            return true;
        });

        binding.btnPickDir.setOnClickListener(v -> treePicker.launch(null));
        binding.btnPickDir.setOnLongClickListener(v -> {
            Storage.saveTree(requireContext(), null);
            refreshDir();
            Toast.makeText(requireContext(), R.string.carpeta_interna, Toast.LENGTH_SHORT).show();
            return true;
        });

        binding.btnCopyDir.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ytdl-path",
                        Storage.describe(requireContext())));
                Toast.makeText(requireContext(), R.string.ruta_copiada, Toast.LENGTH_SHORT).show();
            }
        });

        refreshDir();
        binding.lblQuality.setText(Quality.savedLabel(requireContext()));
        binding.lblEngineHint.setText(getString(R.string.motor_sugerencia));
        refreshEngine();
    }

    // ------------------------------------------------------------------ motor

    /** Engine.info() levanta Python, asi que se lee en un hilo de fondo. */
    private void refreshEngine() {
        Context app = requireContext().getApplicationContext();
        THREADS.execute(() -> {
            String[] info;
            try {
                Engine.init(app);
                info = Engine.info();
            } catch (Exception e) {
                info = new String[]{"youtube-dl", "?", "apk"};
            }
            final String[] shown = info;
            main.post(() -> {
                if (binding != null) {
                    paintEngine(shown);
                }
            });
        });
    }

    private void paintEngine(String[] info) {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("ajustes", Context.MODE_PRIVATE);
        binding.lblEngineName.setText(info[0]);
        binding.lblEngineVersion.setText(getString(R.string.ajustes_version) + ": " + info[1]
                + " · " + getString("descargado".equals(info[2])
                ? R.string.motor_descargado : R.string.motor_del_apk));
        long when = prefs.getLong("motor_fecha", 0);
        binding.lblEngineUpdated.setText(getString(R.string.ajustes_actualizado) + ": "
                + (when == 0 ? getString(R.string.motor_nunca) : DAY.format(new Date(when))));
    }

    private void update(boolean force) {
        if (busy || binding == null) {
            return;
        }
        busy = true;
        Context app = requireContext().getApplicationContext();
        binding.btnUpdateEngine.setEnabled(false);
        binding.btnUpdateEngine.setText(R.string.motor_bajando);
        THREADS.execute(() -> {
            String json;
            try {
                Engine.init(app);
                String tar = Motor.download(app, (percent, bytes) -> main.post(() -> {
                    if (binding != null) {
                        binding.btnUpdateEngine
                                .setText(getString(R.string.motor_bajando_pct, percent));
                    }
                }));
                json = Engine.install(app, tar, force);
            } catch (Exception e) {
                Logs.append(null, "actualizar motor: " + e);
                JSONObject failure = new JSONObject();
                try {
                    failure.put("ok", false).put("kind", "network")
                            .put("error", String.valueOf(e.getMessage()));
                } catch (Exception ignored) {
                }
                json = failure.toString();
            }
            final String result = json;
            main.post(() -> finished(result));
        });
    }

    private void finished(String json) {
        busy = false;
        if (binding == null) {
            return;
        }
        binding.btnUpdateEngine.setEnabled(true);
        binding.btnUpdateEngine.setText(R.string.ajustes_actualizar);
        Context context = requireContext();
        String kind;
        String version;
        String detail;
        boolean ok;
        try {
            JSONObject result = new JSONObject(json);
            ok = result.optBoolean("ok", false);
            kind = result.optString("kind", "error");
            version = result.optString("version", "");
            detail = result.optString("error", "");
        } catch (Exception e) {
            ok = false;
            kind = "error";
            version = "";
            detail = json;
        }
        if (ok) {
            context.getSharedPreferences("ajustes", Context.MODE_PRIVATE).edit()
                    .putString("motor_version", version)
                    .putLong("motor_fecha", System.currentTimeMillis()).apply();
            Toast.makeText(context, getString(R.string.motor_listo, version),
                    Toast.LENGTH_LONG).show();
        } else if ("igual".equals(kind)) {
            Toast.makeText(context, getString(R.string.motor_igual, version),
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, getString(R.string.motor_fallo,
                    Errors.reason(context, kind, detail)), Toast.LENGTH_LONG).show();
        }
        refreshEngine();
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.motor_reset_titulo)
                .setMessage(R.string.motor_reset_mensaje)
                .setPositiveButton(R.string.motor_reset_ok, (dialog, which) -> reset())
                .setNegativeButton(R.string.clipboard_descartar, null)
                .show();
    }

    private void reset() {
        Context app = requireContext().getApplicationContext();
        THREADS.execute(() -> {
            try {
                Engine.reset(app);
            } catch (Exception e) {
                Logs.append(null, "volver al motor del APK: " + e);
            }
            app.getSharedPreferences("ajustes", Context.MODE_PRIVATE).edit()
                    .remove("motor_version").remove("motor_fecha").apply();
            main.post(() -> {
                if (binding == null) {
                    return;
                }
                Toast.makeText(requireContext(), R.string.motor_reset_ok_toast,
                        Toast.LENGTH_SHORT).show();
                refreshEngine();
            });
        });
    }

    // ---------------------------------------------------------------- carpeta

    private void chooseTree(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Storage.saveTree(requireContext(), uri);
        refreshDir();
        Toast.makeText(requireContext(),
                getString(R.string.carpeta_elegida, Storage.describe(requireContext())),
                Toast.LENGTH_LONG).show();
    }

    private void refreshDir() {
        binding.lblDir.setText(Storage.describe(requireContext()));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
