package com.fork.ytdl;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

/**
 * Visitas (coach marks) sobre la pantalla de Descargas: oscurece todo salvo el control
 * que se esta explicando y apoya un bubble encima.
 *
 * Esta hecho a mano con un View que recorta el hueco en el canvas, en lugar de una
 * libreria: son tres anclas fijas del propio layout y no vale la pena arrastrar un
 * dependency mas al APK. El overlay se agrega al content de la activity, asi que tapa
 * la barra de navegacion y consume los toques: nadie puede tocar a ciegas.
 *
 * La responsabilidad del ciclo (cuando mostrar, cuando marcar como vistas) queda afuera,
 * en DownloadFragment; aca solo se pinta y se avanza.
 */
public final class CoachMarks {

    /** Color del velo: negro casi opaco, para que el recorte se note sin tapar la app. */
    private static final int SCRIM = 0xE6040C12;

    public static final class Step {
        final View anchor;
        final int title;
        final int body;

        public Step(View anchor, int titleRes, int bodyRes) {
            this.anchor = anchor;
            this.title = titleRes;
            this.body = bodyRes;
        }
    }

    private final Activity host;
    private final Step[] steps;
    private final Runnable onFinish;
    private final FrameLayout content;
    private final int margin;
    private final int gap;

    private FrameLayout overlay;
    private Spotlight spot;
    private View bubble;
    private int index = -1;
    private boolean closed;

    private CoachMarks(Activity host, Step[] steps, Runnable onFinish) {
        this.host = host;
        this.steps = steps;
        this.onFinish = onFinish;
        this.content = (FrameLayout) host.findViewById(android.R.id.content);
        this.margin = dp(16);
        this.gap = dp(12);
    }

    /** Devuelve el handle para que el dueno lo cierre si el fragmento se destruye antes. */
    public static CoachMarks show(Activity host, Step[] steps, Runnable onFinish) {
        CoachMarks marks = new CoachMarks(host, steps, onFinish);
        marks.open();
        return marks;
    }

    private void open() {
        overlay = new FrameLayout(host);
        overlay.setClickable(true);
        spot = new Spotlight(host);
        overlay.addView(spot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bubble = LayoutInflater.from(host).inflate(R.layout.view_coach, overlay, false);
        ((MaterialButton) bubble.findViewById(R.id.coach_skip))
                .setOnClickListener(v -> close());
        ((MaterialButton) bubble.findViewById(R.id.coach_next))
                .setOnClickListener(v -> next());
        overlay.addView(bubble, new FrameLayout.LayoutParams(dp(300),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        overlay.setAlpha(0f);
        content.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        index = 0;
        paint(index);
        overlay.animate().alpha(1f).setDuration(220).start();
    }

    private void next() {
        if (index + 1 >= steps.length) {
            close();
            return;
        }
        show(index + 1);
    }

    /** Cruza el fade del bubble con el cambio de ancla para que no salte de lugar. */
    private void show(int target) {
        Step step = steps[target];
        if (step.anchor == null || !step.anchor.isShown()) {
            // El ancla no esta (oculta o todavia sin medir): se saltea el paso.
            if (target + 1 < steps.length) {
                show(target + 1);
            } else {
                close();
            }
            return;
        }
        index = target;
        overlay.animate().alpha(0f).setDuration(130).withEndAction(new Runnable() {
            @Override
            public void run() {
                paint(target);
                overlay.animate().alpha(1f).setDuration(200).start();
            }
        }).start();
    }

    private void paint(int target) {
        Step step = steps[target];
        ((TextView) bubble.findViewById(R.id.coach_title)).setText(step.title);
        ((TextView) bubble.findViewById(R.id.coach_body)).setText(step.body);
        ((MaterialButton) bubble.findViewById(R.id.coach_next)).setText(
                target == steps.length - 1 ? R.string.coach_listo : R.string.coach_next);

        int[] anchorPos = new int[2];
        int[] rootPos = new int[2];
        step.anchor.getLocationInWindow(anchorPos);
        content.getLocationInWindow(rootPos);
        int left = anchorPos[0] - rootPos[0];
        int top = anchorPos[1] - rootPos[1];
        int width = step.anchor.getWidth();
        int height = step.anchor.getHeight();
        spot.setTarget(left, top, left + width, top + height);

        int bubbleWidth = Math.min(dp(300), content.getWidth() - margin * 2);
        bubble.measure(View.MeasureSpec.makeMeasureSpec(bubbleWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int bubbleHeight = bubble.getMeasuredHeight();

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bubble.getLayoutParams();
        lp.width = bubbleWidth;
        lp.leftMargin = clamp(left, margin, content.getWidth() - margin - bubbleWidth);
        int below = top + height + gap;
        if (below + bubbleHeight + margin < content.getHeight()) {
            lp.topMargin = below;
        } else {
            // No entra debajo: se va arriba del ancla; si tampoco, queda encima a la fuerza.
            int above = top - gap - bubbleHeight;
            lp.topMargin = Math.max(margin, above);
        }
        bubble.setLayoutParams(lp);
    }

    private int clamp(int value, int min, int max) {
        return max < min ? min : Math.max(min, Math.min(value, max));
    }

    /** Cierra y avisa: el dueno marca "vistas" aca para que no vuelvan solas. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (overlay != null) {
            content.removeView(overlay);
        }
        if (onFinish != null) {
            onFinish.run();
        }
    }

    private int dp(int value) {
        return Math.round(value * host.getResources().getDisplayMetrics().density);
    }

    /**
     * El velo con el hueco: se pinta el scrim sobre una capa aparte y se borra el rectangulo
     * redondeado del ancla con CLEAR. La luz no es una vista detras (no hay otra), es literal
     * un agujero, asi que lo que se ve debajo es el control real y sigue funcionando.
     */
    private static final class Spotlight extends View {

        private final Paint scrim = new Paint();
        private final Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float radius;

        Spotlight(Context context) {
            super(context);
            scrim.setColor(SCRIM);
            cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(2f * context.getResources().getDisplayMetrics().density);
            ring.setColor(ContextCompat.getColor(context, R.color.primary));
            ring.setAlpha(200);
            radius = 14f * context.getResources().getDisplayMetrics().density;
        }

        void setTarget(int left, int top, int right, int bottom) {
            float pad = 6f * getResources().getDisplayMetrics().density;
            rect.set(left - pad, top - pad, right + pad, bottom + pad);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (rect.isEmpty()) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), scrim);
                return;
            }
            int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrim);
            canvas.drawRoundRect(rect, radius, radius, cut);
            canvas.restoreToCount(layer);
            canvas.drawRoundRect(rect, radius, radius, ring);
        }
    }
}
