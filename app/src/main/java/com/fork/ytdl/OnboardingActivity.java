package com.fork.ytdl;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Guia de inicio de 4 pasos.
 *
 * Los cuatro pasos estan inflados de una en activity_onboarding.xml y se cruzan con un
 * fade: el contenido es estatico y lo unico vivo es la tarjeta de cola del paso 1, que se
 * mueve con un Handler para mostrar el ciclo completo (espera, progreso, guardado) antes de
 * que exista una descarga real.
 *
 * El paso 3 guarda la carpeta exactamente igual que SettingsFragment (Storage.saveTree),
 * asi que lo elegido aca ya aparece en Ajustes. Saltear la guia tambien cierra el flag:
 * si volviera a abrirse en cada arranque seria un candado, no una ayuda.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int[] STEP_IDS = {R.id.step_1, R.id.step_2, R.id.step_3, R.id.step_4};

    /** El progreso simulado avanza de a 4 cada 90 ms: ~2,3 s de bajada, 1,4 s de final. */
    private static final long DEMO_FIRST_MS = 500L;
    private static final long DEMO_TICK_MS = 90L;
    private static final long DEMO_HOLD_MS = 1400L;

    private final Handler main = new Handler(Looper.getMainLooper());

    private View[] steps;
    private LinearLayout dots;
    private TextView counter;
    private MaterialButton next;
    private MaterialButton back;
    private LinearProgressIndicator demoProgress;
    private TextView demoState;
    private TextView demoPct;

    /** -1 significa "terminada, esperando para volver a arrancar". */
    private int demoValue;
    private boolean demoRunning;
    private int index = -1;

    private ActivityResultLauncher<Uri> treePicker;

    private final Runnable demoStep = new Runnable() {
        @Override
        public void run() {
            if (!demoRunning) {
                return;
            }
            if (demoValue < 0) {
                demoValue = 0;
                demoProgress.setProgress(0);
                demoState.setText(R.string.estado_en_espera);
                demoPct.setText("");
                main.postDelayed(demoStep, DEMO_FIRST_MS);
                return;
            }
            if (demoValue == 0) {
                demoState.setText(R.string.estado_descargando);
            }
            demoValue = Math.min(100, demoValue + 4);
            demoProgress.setProgress(demoValue);
            if (demoValue >= 100) {
                demoState.setText(R.string.estado_completada);
                demoPct.setText(R.string.guardado);
                demoValue = -1;
                main.postDelayed(demoStep, DEMO_HOLD_MS);
                return;
            }
            demoPct.setText(getString(R.string.pct_corto, demoValue));
            main.postDelayed(this, DEMO_TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_onboarding);

        treePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), this::chooseTree);

        steps = new View[STEP_IDS.length];
        for (int i = 0; i < STEP_IDS.length; i++) {
            steps[i] = findViewById(STEP_IDS[i]);
        }
        counter = findViewById(R.id.onb_counter);
        next = findViewById(R.id.onb_next);
        back = findViewById(R.id.onb_back);
        dots = findViewById(R.id.onb_dots);
        demoProgress = findViewById(R.id.onb_demo_progress);
        demoState = findViewById(R.id.onb_demo_state);
        demoPct = findViewById(R.id.onb_demo_pct);

        buildDots();
        showStep(0, true);

        next.setOnClickListener(v -> {
            if (index == STEP_IDS.length - 1) {
                finishGuide();
            } else {
                showStep(index + 1, true);
            }
        });
        back.setOnClickListener(v -> {
            if (index > 0) {
                showStep(index - 1, false);
            }
        });
        findViewById(R.id.onb_skip).setOnClickListener(v -> finishGuide());
        findViewById(R.id.onb_btn_carpeta).setOnClickListener(v -> treePicker.launch(null));
        findViewById(R.id.onb_btn_luego).setOnClickListener(v -> showStep(index + 1, true));
        refreshDir();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (index == 0) {
            startDemo();
        }
    }

    @Override
    protected void onPause() {
        stopDemo();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (index > 0) {
            showStep(index - 1, false);
            return;
        }
        finishGuide();
    }

    // ------------------------------------------------------------------ pasos

    private void buildDots() {
        int size = dp(8);
        int gap = dp(8);
        for (int i = 0; i < STEP_IDS.length; i++) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.dot_state);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(i == 0 ? 0 : gap);
            dots.addView(dot, lp);
        }
    }

    /** Cruza el paso anterior con el nuevo; forward solo cambia la direccion del deslizamiento. */
    private void showStep(int target, boolean forward) {
        if (target == index || target < 0 || target >= steps.length) {
            return;
        }
        final View out = index >= 0 ? steps[index] : null;
        index = target;

        View in = steps[index];
        in.setAlpha(0f);
        in.setTranslationY(dp(forward ? 14 : -14));
        in.setVisibility(View.VISIBLE);
        in.animate().alpha(1f).translationY(0f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        if (out != null) {
            out.animate().alpha(0f)
                    .setDuration(160)
                    .setInterpolator(new AccelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            out.setVisibility(View.GONE);
                            out.setTranslationY(0f);
                        }
                    })
                    .start();
        }

        counter.setText(getString(R.string.onb_paso, index + 1));
        next.setText(index == STEP_IDS.length - 1
                ? R.string.onb_empezar
                : R.string.onb_siguiente);
        back.setVisibility(index == 0 ? View.INVISIBLE : View.VISIBLE);
        paintDots();

        if (index == 0) {
            startDemo();
        } else {
            stopDemo();
        }
    }

    private void paintDots() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            int color = i == index ? R.color.primary : R.color.outline_variant;
            dots.getChildAt(i).setBackgroundTintList(
                    ContextCompat.getColorStateList(this, color));
        }
    }

    // ---------------------------------------------------------------- demo

    private void startDemo() {
        if (demoRunning) {
            return;
        }
        demoRunning = true;
        demoValue = 0;
        demoProgress.setProgress(0);
        demoState.setText(R.string.estado_en_espera);
        demoPct.setText("");
        main.postDelayed(demoStep, DEMO_FIRST_MS);
    }

    private void stopDemo() {
        demoRunning = false;
        main.removeCallbacks(demoStep);
        demoPct.setText("");
    }

    // ---------------------------------------------------------------- carpeta

    private void chooseTree(Uri uri) {
        if (uri == null) {
            return;
        }
        Storage.saveTree(this, uri);
        refreshDir();
        Toast.makeText(this, getString(R.string.carpeta_elegida, Storage.describe(this)),
                Toast.LENGTH_LONG).show();
    }

    private void refreshDir() {
        TextView label = findViewById(R.id.onb_dir_state);
        if (label == null) {
            return;
        }
        label.setText(Storage.tree(this) != null
                ? getString(R.string.carpeta_elegida, Storage.describe(this))
                : getString(R.string.onb_dir_por_defecto, Storage.describe(this)));
    }

    // ---------------------------------------------------------------- cierre

    private void finishGuide() {
        stopDemo();
        FirstRun.onboardingDone(this);
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
