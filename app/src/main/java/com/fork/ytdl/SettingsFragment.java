package com.fork.ytdl;

import android.content.Context;
import android.content.Intent;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

        binding.cardQuality.setOnClickListener(v -> pickQuality());

        refreshDir();
        paintQuality();
        binding.lblEngineHint.setText(getString(R.string.motor_sugerencia));
        binding.switchLog.setChecked(Storage.logVisible(requireContext()));
        binding.switchLog.setOnCheckedChangeListener((button, on) ->
                Storage.setLogVisible(requireContext(), on));
        binding.switchWifi.setChecked(Net.wifiOnly(requireContext()));
        binding.switchWifi.setOnCheckedChangeListener((button, on) ->
                Net.setWifiOnly(requireContext(), on));
        binding.switchAvisos.setChecked(Net.announce(requireContext()));
        binding.switchAvisos.setOnCheckedChangeListener((button, on) ->
                Net.setAnnounce(requireContext(), on));
        binding.btnCrashCopy.setOnClickListener(v -> copyCrash());
        binding.btnCrashClear.setOnClickListener(v -> {
            Crash.clear(requireContext());
            paintCrash();
            Toast.makeText(requireContext(), R.string.fallo_borrado, Toast.LENGTH_SHORT).show();
        });
        paintIntro();
        binding.cardIntro.setOnClickListener(v -> pickIntro());
        binding.btnTutorial.setOnClickListener(v -> reopenGuide());
        paintCrash();
        refreshEngine();
    }

    // ------------------------------------------------------------------ apertura

    private void paintIntro() {
        binding.lblIntroState.setText(getString(R.string.intro_modo_actual,
                introName(requireContext(), FirstRun.introMode(requireContext()))));
    }

    private static String introName(Context context, int mode) {
        return context.getString(mode == FirstRun.INTRO_SHORT
                ? R.string.intro_modo_corto
                : mode == FirstRun.INTRO_OFF
                ? R.string.intro_modo_off
                : R.string.intro_modo_completo);
    }

    private void pickIntro() {
        Context context = requireContext();
        String[] names = {
                introName(context, FirstRun.INTRO_OFF),
                introName(context, FirstRun.INTRO_SHORT),
                introName(context, FirstRun.INTRO_FULL),
        };
        final Context app = context.getApplicationContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.intro_modo_titulo)
                .setSingleChoiceItems(names, FirstRun.introMode(context), (dialog, which) -> {
                    FirstRun.setIntroMode(app, which);
                    if (binding != null) {
                        paintIntro();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.clipboard_descartar, null)
                .show();
    }

    /** Vuelve a pasar la guia de 4 pasos; el intro en si no se repite aca. */
    private void reopenGuide() {
        FirstRun.resetOnboarding(requireContext());
        startActivity(new Intent(requireContext(), OnboardingActivity.class));
    }

    @Override
    public void onResume() {
        super.onResume();
        // si la app se cerro mal mientras esta pantalla no estaba abierta, ahi se ve
        paintCrash();
    }

    /** La tarjeta de fallos solo aparece cuando hay un stack guardado. */
    private void paintCrash() {
        if (binding == null) {
            return;
        }
        boolean hay = Crash.exists(requireContext());
        binding.crashCard.setVisibility(hay ? View.VISIBLE : View.GONE);
        if (hay) {
            binding.crashDetail.setText(Crash.read(requireContext()));
        }
    }

    private void copyCrash() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                "ytdl-fallo", Crash.read(requireContext())));
        Toast.makeText(requireContext(), R.string.fallo_copiado, Toast.LENGTH_SHORT).show();
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
        Uri before = Storage.tree(requireContext());
        Storage.saveTree(requireContext(), uri);
        String raw = Storage.chosenRaw(requireContext());
        if (raw != null && Storage.chosenDir(requireContext()) == null) {
            // la ruta existe pero el telefono no deja escribir ahi: se vuelve a la anterior
            Storage.saveTree(requireContext(), before);
            refreshDir();
            Toast.makeText(requireContext(), R.string.ajustes_carpeta_fallo,
                    Toast.LENGTH_LONG).show();
            return;
        }
        refreshDir();
        Toast.makeText(requireContext(),
                getString(R.string.carpeta_elegida, Storage.describe(requireContext())),
                Toast.LENGTH_LONG).show();
    }

    private void refreshDir() {
        binding.lblDir.setText(Storage.describe(requireContext()));
    }

    // ---------------------------------------------------------------- calidad

    /** Primero se elige el modo (video o audio) y despues la resolucion, de la mas baja a la mas alta. */
    private void pickQuality() {
        final Context context = requireContext();
        String[] rows = new String[]{
                getString(R.string.ajustes_calidad_fila, getString(R.string.hist_video),
                        Quality.savedLabel(context)),
                getString(R.string.ajustes_calidad_fila, getString(R.string.fmt_solo_audio),
                        Quality.savedAudioLabel(context))};
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.ajustes_calidad_titulo)
                .setItems(rows, (dialog, which) -> {
                    if (which == 0) {
                        pickVideoQuality();
                    } else {
                        pickAudioQuality();
                    }
                })
                .show();
    }

    private void pickVideoQuality() {
        final Context context = requireContext();
        List<String> specs = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int height : Quality.HEIGHTS) {
            specs.add(Quality.spec(height));
            labels.add(getString(R.string.fmt_altura, height));
        }
        specs.add(Quality.BEST);
        labels.add(getString(R.string.q_mejor));
        choose(R.string.ajustes_calidad_video_titulo, R.string.ajustes_calidad_video_nota,
                specs, labels, specs.indexOf(Quality.saved(context)), true);
    }

    private void pickAudioQuality() {
        final Context context = requireContext();
        List<String> specs = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int height : Quality.HEIGHTS) {
            specs.add(Quality.audioSpec(height));
            labels.add(getString(R.string.fmt_altura, height));
        }
        specs.add(Quality.AUDIO);
        labels.add(getString(R.string.ajustes_calidad_audio_auto));
        choose(R.string.ajustes_calidad_audio_titulo, R.string.ajustes_calidad_audio_nota,
                specs, labels, specs.indexOf(Quality.savedAudio(context)), false);
    }

    private void choose(int titleRes, int noteRes, final List<String> specs,
                        List<String> labels, int checked, final boolean forVideo) {
        Context context = requireContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(titleRes)
                .setMessage(noteRes)
                .setSingleChoiceItems(labels.toArray(new String[0]), Math.max(checked, -1),
                        (dialog, which) -> {
                            Context ctx = requireContext();
                            String spec = specs.get(which);
                            String label = labels.get(which);
                            if (forVideo) {
                                Quality.save(ctx, spec, label);
                            } else {
                                Quality.saveAudio(ctx, spec, label);
                            }
                            paintQuality();
                            dialog.dismiss();
                        })
                .setNegativeButton(R.string.clipboard_descartar, null)
                .show();
    }

    private void paintQuality() {
        Context context = requireContext();
        binding.lblQuality.setText(getString(R.string.ajustes_calidad_valor,
                Quality.savedLabel(context), Quality.savedAudioLabel(context)));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
