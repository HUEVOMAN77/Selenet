package com.fork.ytdl;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.fork.ytdl.databinding.ActivityIntroBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Puerta de entrada de Selenet: el intro y, de ahi, la guia de inicio o la app.
 *
 * El guion tiene tres partes:
 *  - 0,00-1,28 s  la marca se dibuja trazo por trazo y despues se rellena con un rebote.
 *                 Lo hace solo el animated-vector (anim_intro_mark.xml).
 *  - 0,90-1,31 s  la flecha cae y entra en la bandeja: el gesto de la descarga real.
 *  - 1,30-4,00 s  el nombre letra por letra, el filete, la bajada, la barra con sus tres
 *                 tabs y, al final, el degradado de la app entrando por debajo.
 *
 * La marca no se puede escalar (las duraciones de un animated-vector solo se tocan desde
 * API 26 y este APK apunta a 28 con minSdk 24), asi que lo unico que se achica es la parte
 * final: en frio el intro cierra en ~4 s y si el proceso ya estaba levantado en ~2,5 s,
 * sin recortar ninguna beat.
 *
 * Mientras se ve el intro, el motor de Python levanta en un hilo aparte. El arranque de
 * Chaquopy es lo mas caro del arranque en frio; superpuesto con la animacion sale gratis.
 */
public class IntroActivity extends AppCompatActivity {

    /** Hasta aca manda el AVD; lo que sigue es escalable. */
    private static final long SCRIPT_START = 1300L;
    private static final long TAIL_FULL_MS = 4000L - SCRIPT_START;
    private static final long TAIL_WARM_MS = 2500L - SCRIPT_START;

    private static final Interpolator LINEAR = new LinearInterpolator();

    private ActivityIntroBinding binding;
    private Interpolator easeOut;
    private AnimatorSet script;
    private boolean delivered;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!FirstRun.showIntro(this)) {
            // Sin intro: igual hay que encaminar la guia el primer arranque.
            deliver();
            return;
        }
        setContentView(R.layout.activity_intro);
        binding = ActivityIntroBinding.bind(findViewById(R.id.intro_root));
        easeOut = AnimationUtils.loadInterpolator(this, R.interpolator.intro_ease_out);

        warmUpMotor();
        buildScript(FirstRun.fullIntro(this));

        // Tocar cualquier parte lo saltea: un intro no puede ser un candado.
        binding.introRoot.setOnClickListener(v -> endNow());
        binding.introSkip.setOnClickListener(v -> endNow());
    }

    @Override
    public void onBackPressed() {
        endNow();
    }

    @Override
    protected void onDestroy() {
        if (script != null) {
            script.cancel();
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ guion

    private void buildScript(boolean full) {
        final long tail = full ? TAIL_FULL_MS : TAIL_WARM_MS;
        List<Animator> beats = new ArrayList<>();

        AnimatedVectorDrawable mark =
                (AnimatedVectorDrawable) ContextCompat.getDrawable(this, R.drawable.anim_intro_mark);
        if (mark != null) {
            binding.introMark.setImageDrawable(mark);
            mark.start();
        }

        // La flecha: aparece arriba, cae con aceleracion y se desvanece en la bandeja.
        View arrow = binding.introFalling;
        arrow.setTranslationY(dp(-46));
        beats.add(alpha(arrow, 0f, 1f, at(900, tail), 120, LINEAR));
        beats.add(move(arrow, dp(-46), dp(48), at(900, tail), 400,
                new AccelerateInterpolator(1.4f)));
        beats.add(alpha(arrow, 1f, 0f, at(1190, tail), 140, LINEAR));

        // "SELENET" letra por letra, subiendo desde abajo.
        for (TextView letter : wordmark()) {
            beats.add(alpha(letter, 0f, 1f, at(1360, tail), 220, LINEAR));
            beats.add(move(letter, dp(26), 0f, at(1360, tail), 300, easeOut));
        }

        // Filete que crece desde la izquierda y bajada debajo.
        binding.introAccent.setPivotX(0f);
        beats.add(alpha(binding.introAccent, 0f, 1f, at(1900, tail), 160, LINEAR));
        beats.add(scaleX(binding.introAccent, 0f, 1f, at(1900, tail), 360, easeOut));
        beats.add(alpha(binding.introTagline, 0f, 1f, at(2060, tail), 300, LINEAR));
        beats.add(move(binding.introTagline, dp(14), 0f, at(2060, tail), 300, easeOut));

        // El aviso de "tocá para saltar" temprano, para que nadie se quede esperando.
        beats.add(alpha(binding.introSkip, 0f, 0.75f, at(600, tail), 320, LINEAR));

        // La silueta de la barra y sus tres destinos, escalonados.
        binding.introNavGhost.setPivotY(0f);
        beats.add(alpha(binding.introNavGhost, 0f, 1f, at(2450, tail), 260, LINEAR));
        beats.add(scaleX(binding.introNavGhost, 0.6f, 1f, at(2450, tail), 420, easeOut));
        @IdRes int[] tabs = {R.id.intro_tab_1, R.id.intro_tab_2, R.id.intro_tab_3};
        for (int i = 0; i < tabs.length; i++) {
            View tab = findViewById(tabs[i]);
            long delay = at(2650 + i * 120L, tail);
            beats.add(alpha(tab, 0f, 1f, delay, 260, LINEAR));
            beats.add(move(tab, dp(18), 0f, delay, 320, easeOut));
        }

        // El fondo real de la app entra por ultimo: es el pase visual a MainActivity.
        beats.add(alpha(binding.introBg, 0f, 1f, at(3300, tail), 680, LINEAR));

        script = new AnimatorSet();
        script.playTogether(beats);
        script.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                deliver();
            }
        });
        script.start();
    }

    /**
     * Traduce la posicion de una beat al tiempo real. Las beats anteriores a SCRIPT_START
     * son fijas (van pegadas al AVD); el resto se comprime segun la cola elegida.
     */
    private long at(long baseMs, long tail) {
        if (baseMs <= SCRIPT_START) {
            return baseMs;
        }
        float k = (float) tail / (float) TAIL_FULL_MS;
        return SCRIPT_START + Math.round((baseMs - SCRIPT_START) * k);
    }

    /** Arma el nombre con las letras de app_name, para no hardcodear la marca. */
    private List<TextView> wordmark() {
        List<TextView> letters = new ArrayList<>();
        String word = getString(R.string.app_name).toUpperCase(Locale.ROOT);
        LinearLayout host = binding.introWordmark;
        host.removeAllViews();
        for (int i = 0; i < word.length(); i++) {
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(word.charAt(i)));
            tv.setTextAppearance(this, R.style.Text_Ytdl_IntroWord);
            tv.setAlpha(0f);
            tv.setTranslationY(dp(26));
            // A la ultima letra no hay que darle espacio posterior: si no, el nombre sale
            // corrido a la izquierda dentro de su propio contenedor.
            tv.setLetterSpacing(i == word.length() - 1 ? 0f : 0.22f);
            host.addView(tv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            letters.add(tv);
        }
        return letters;
    }

    // ---------------------------------------------------------------- helpers

    private ObjectAnimator alpha(View v, float from, float to, long delay, long dur,
                                Interpolator iv) {
        return prop(v, "alpha", from, to, delay, dur, iv);
    }

    private ObjectAnimator move(View v, float from, float to, long delay, long dur,
                                Interpolator iv) {
        return prop(v, "translationY", from, to, delay, dur, iv);
    }

    private ObjectAnimator scaleX(View v, float from, float to, long delay, long dur,
                                  Interpolator iv) {
        return prop(v, "scaleX", from, to, delay, dur, iv);
    }

    private ObjectAnimator prop(View v, String name, float from, float to, long delay,
                                long dur, Interpolator iv) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, name, from, to);
        a.setStartDelay(delay);
        a.setDuration(dur);
        a.setInterpolator(iv);
        return a;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    // ---------------------------------------------------------------- arranque

    /**
     * Prende Chaquopy debajo del intro. Si falla no rompe nada: el servicio vuelve a
     * intentar Engine.init() cuando arranca la primera descarga.
     */
    private void warmUpMotor() {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Engine.init(app);
                } catch (Throwable t) {
                    Logs.append(null, "warmup: " + t);
                }
            }
        }, "ydl-warmup").start();
    }

    /** Salteo manual: cancel deja cada vista en su valor final y despues se entrega. */
    private void endNow() {
        if (script != null) {
            script.cancel();
        }
        deliver();
    }

    private void deliver() {
        if (delivered) {
            return;
        }
        delivered = true;
        FirstRun.markWarm();
        Class<?> next = FirstRun.needsOnboarding(this)
                ? OnboardingActivity.class
                : MainActivity.class;
        startActivity(new Intent(this, next));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
