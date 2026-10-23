package com.fork.ytdl;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Los dos brillos de la marca, moviendose detras del contenido.
 *
 * El degradado fijo de `bg_app` ya da profundidad; esto es lo mismo pero vivo: un halo menta
 * arriba a la derecha y uno azul abajo a la izquierda, que derivan lentamente como si la
 * pantalla respirara. Se pinta con dos shaders que solo transforman su matriz, asi el costo
 * por cuadro es una invalidacion, no dos degradados nuevos.
 *
 * Va a 11 cuadros por segundo porque un movimiento lento no necesita mas, y se detiene solo
 * cuando la vista deja de estar visible. Con las animaciones del sistema apagadas queda
 * quieta, en la posicion de reposo.
 */
public class AmbientGlow extends View {

    /** Paso del reloj de la animacion: 90 ms, once cuadros por segundo. */
    private static final long STEP_MS = 90;
    /** Lo que tarda una vuelta completa de los halos. */
    private static final long LOOP_MS = 26000;

    private final Paint mint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blue = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long phase;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            phase = (phase + STEP_MS) % LOOP_MS;
            invalidate();
            postDelayed(this, STEP_MS);
        }
    };

    public AmbientGlow(Context context) {
        this(context, null);
    }

    public AmbientGlow(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float radius = Math.max(width, height) * 0.62f;
        mint.setShader(gradient(radius, 0x2E3EE8B0));
        blue.setShader(gradient(radius, 0x2A4C8DFF));
    }

    private static RadialGradient gradient(float radius, int color) {
        return new RadialGradient(0f, 0f, radius, color, 0x00000000, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0 || mint.getShader() == null) {
            return;
        }
        double angle = 2 * Math.PI * phase / LOOP_MS;
        // con angle = 0 los halos quedan donde los pone el degradado estatico de la ventana
        halo(canvas, mint, width * (0.86f + 0.06f * (float) Math.cos(angle)),
                height * (0.10f + 0.05f * (float) Math.sin(angle)));
        halo(canvas, blue, width * (0.10f + 0.05f * (float) Math.sin(angle * 0.7)),
                height * (0.80f + 0.06f * (float) Math.cos(angle * 0.7)));
    }

    /** Pinta un halo con el centro en (x, y): el shader esta centrado en el origen. */
    private void halo(Canvas canvas, Paint paint, float x, float y) {
        canvas.save();
        canvas.translate(x, y);
        canvas.drawPaint(paint);
        canvas.restore();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible && !Motion.reduced()) {
            start();
        } else {
            stop();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void start() {
        if (running) {
            return;
        }
        running = true;
        postDelayed(tick, STEP_MS);
    }

    private void stop() {
        running = false;
        removeCallbacks(tick);
    }
}
