package com.fork.ytdl;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.provider.Settings;
import android.transition.Transition;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.platform.MaterialSharedAxis;

/**
 * Utilidades de movimiento. Todo lo bonito de la app pasa por aqui para respetar dos cosas:
 * que el usuario pudo desactivar las animaciones del sistema, y que una interfaz no debe
 * temblar cuando apenas se toca.
 */
public final class Motion {

    /** Cuanto se hunde una tarjeta al tocarla. 0.985 es apenas perceptible, y alcanza. */
    private static final float PRESS_SCALE = 0.985f;

    private Motion() {
    }

    /** Verdadero si el sistema tiene las animaciones de objetos apagadas. */
    public static boolean reduced() {
        return ValueAnimator.getDurationScale() == 0f;
    }

    /**
     * Igual que lo anterior pero mirando tambien la escala de las transiciones, que es la que
     * usan los movimientos de pantalla: se puede dejar una sin apagar la otra.
     */
    public static boolean reduced(Context context) {
        if (reduced()) {
            return true;
        }
        return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f;
    }

    /**
     * Hunde la vista mientras se la mantiene tocada y la suelta al terminar. No consume el
     * evento: el click largo y el ripple del fondo siguen funcionando igual que antes.
     */
    public static void press(final View view) {
        view.setOnTouchListener((target, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!reduced()) {
                        target.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
                                .setDuration(90).start();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    target.animate().scaleX(1f).scaleY(1f).setDuration(150)
                            .start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Aparicion suave de un elemento que todavia no se mostro (estado vacio, avisos). */
    public static void appear(View view, long duration) {
        if (reduced() || view.getAlpha() > 0f && view.getVisibility() == View.VISIBLE) {
            return;
        }
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(duration).start();
    }

    /** Un pequeno salto de escala, para marcar que algo acaba de pasar (agregada, lista). */
    public static void pop(View view) {
        if (reduced() || view == null) {
            return;
        }
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.animate().scaleX(1.04f).scaleY(1.04f).setDuration(110)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(160)
                        .start())
                .start();
    }

    /**
     * Deja un fragmento listo para el fundido a traves de Material 3, el movimiento que la
     * guia reserva para destinos paralelos (las tres pestanas de abajo). Se le ponen los dos
     * lados: el que entra aparece desde la superficie y el que se va se apaga contra ella.
     */
    public static void fadeThrough(Fragment fragment) {
        MaterialFadeThrough enter = new MaterialFadeThrough();
        MaterialFadeThrough exit = new MaterialFadeThrough();
        fragment.setEnterTransition(enter);
        fragment.setReenterTransition(enter);
        fragment.setExitTransition(exit);
        fragment.setReturnTransition(exit);
    }

    /**
     * Eje compartido en Z para pasar de una pantalla a otra: la que abre empuja y la que la
     * recibe viene de atras, con profundidad, en lugar del fundido plano de siempre.
     *
     * @param origin true en la pantalla que lanza (baja al abrir), false en la lanzada (sube).
     */
    public static void push(Activity activity, boolean origin) {
        Window window = activity.getWindow();
        if (origin) {
            window.setExitTransition(axis(true));
            window.setReenterTransition(axis(false));
        } else {
            window.setEnterTransition(axis(true));
            window.setReturnTransition(axis(false));
        }
    }

    /**
     * Animador de filas ajustado: entradas y salidas cortas y, sobre todo, sin la animacion de
     * cambio, que es la que hace parpadear toda la lista cada vez que la cola se refresca con
     * el progreso de una descarga.
     */
    public static void calm(RecyclerView list) {
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        animator.setAddDuration(190);
        animator.setRemoveDuration(160);
        animator.setMoveDuration(190);
        animator.setChangeDuration(0);
        list.setItemAnimator(animator);
    }

    private static Transition axis(boolean forward) {
        return new MaterialSharedAxis(MaterialSharedAxis.Z, forward);
    }
}
