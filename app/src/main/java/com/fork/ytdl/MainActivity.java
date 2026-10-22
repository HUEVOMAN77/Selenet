package com.fork.ytdl;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;

public class MainActivity extends Activity {

    private static final String[] FORMAT_LABELS = {
            "mejor (auto)",
            "mp4 progresivo",
            "360p (formato 18)",
            "solo audio",
            "peor (ahorro)"
    };

    private static final String[] FORMAT_SPECS = {
            "best",
            "best[ext=mp4]/best",
            "18",
            "bestaudio",
            "worst"
    };

    private EditText urlBox;
    private Spinner formatBox;
    private TextView logView;
    private ScrollView scroller;
    private final StringBuilder logText = new StringBuilder();

    public void log(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                logText.append(msg).append('\n');
                logView.setText(logText.toString());
                scroller.post(new Runnable() {
                    public void run() {
                        scroller.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        buildUi();
        receiveShared(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        receiveShared(intent);
    }

    private void receiveShared(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared != null && !shared.trim().isEmpty()) {
            urlBox.setText(shared.trim());
            log("link recibido por Compartir");
        }
    }

    private File outDir() {
        File dir = new File(getExternalFilesDir(null), "downloads");
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File(getFilesDir(), "downloads");
            dir.mkdirs();
        }
        return dir;
    }

    private void startDownload() {
        final String url = urlBox.getText().toString().trim();
        if (url.isEmpty()) {
            log("pegá un link primero");
            return;
        }
        String spec = FORMAT_SPECS[formatBox.getSelectedItemPosition()];
        File dir = outDir();
        log("---- " + url);
        log("destino: " + dir.getAbsolutePath());
        PyObject backend = Python.getInstance().getModule("backend");
        backend.callAttr("start", url, dir.getAbsolutePath(), spec, this);
    }

    private void buildUi() {
        int pad = (int) (12 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        urlBox = new EditText(this);
        urlBox.setHint("URL del video");
        urlBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlBox.setSingleLine(true);
        root.addView(urlBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        formatBox = new Spinner(this);
        formatBox.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, FORMAT_LABELS));
        LinearLayout.LayoutParams spinParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(formatBox, spinParams);

        Button go = new Button(this);
        go.setText("DESCARGAR");
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startDownload();
            }
        });
        row.addView(go);

        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        scroller = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextIsSelectable(true);
        logView.setTextSize(11f);
        scroller.addView(logView);
        root.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView footer = new TextView(this);
        footer.setTextSize(11f);
        footer.setText("youtube-dl 2025.04.07 · motor sin modificar");
        root.addView(footer);

        setContentView(root);
    }
}
