package com.fork.ytdl;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Mini grafica de velocidad de la fila que esta bajando.
 *
 * Guarda las ultimas 40 muestras que le manda el motor y las dibuja como un area rellena con
 * la linea de arriba mas marcada. Se escala sola contra su propio maximo, asi que una bajada
 * pareja se ve como una meseta y una que se corta se ve como un diente: es la unica forma de
 * notar que la red esta aflojando sin mirar numeros.
 */
public class Sparkline extends View {

    private static final int SAMPLES = 40;
    private static final float MIN_RATE = 1024f;   // 1 KB/s, para que una bajada lenta no salga plana

    private final float[] values = new float[SAMPLES];
    private final Path line = new Path();
    private final Path area = new Path();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String tracked;
    private int count;

    public Sparkline(Context context) {
        this(context, null);
    }

    public Sparkline(Context context, AttributeSet attrs) {
        super(context, attrs);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.6f));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xFF3EE8B0);
        fill.setStyle(Paint.Style.FILL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * Agrega una muestra de bytes por segundo de la tarea `id`. Si llega otra tarea se olvida
     * la serie: las curvas no tienen sentido de una descarga a la otra.
     */
    public void track(String id, double rate) {
        if (!id.equals(tracked)) {
            tracked = id;
            count = 0;
        }
        if (count == SAMPLES) {
            System.arraycopy(values, 1, values, 0, SAMPLES - 1);
            count--;
        }
        values[count++] = (float) Math.max(0, rate);
        invalidate();
    }

    /** Olvida la serie: se llama cuando la fila deja de estar activa. */
    public void clear() {
        tracked = null;
        count = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // el area se desvanece hacia abajo y la linea queda entera: mismo degradado, dos cortes
        fill.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{0x553EE8B0, 0x0D3EE8B0}, new float[]{0f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        if (count < 2 || width <= 0 || height <= 0) {
            return;
        }
        float peak = MIN_RATE;
        for (int i = 0; i < count; i++) {
            peak = Math.max(peak, values[i]);
        }
        float step = width / (count - 1);
        line.reset();
        area.reset();
        for (int i = 0; i < count; i++) {
            float x = i * step;
            // se deja un pixel de aire arriba y abajo para que la linea no se corte
            float y = height - 1 - (values[i] / peak) * (height - 2);
            if (i == 0) {
                line.moveTo(x, y);
                area.moveTo(x, height);
                area.lineTo(x, y);
            } else {
                line.lineTo(x, y);
                area.lineTo(x, y);
            }
        }
        area.lineTo(width, height);
        area.close();
        canvas.drawPath(area, fill);
        canvas.drawPath(line, stroke);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
