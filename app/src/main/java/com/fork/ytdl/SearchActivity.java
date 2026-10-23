package com.fork.ytdl;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fork.ytdl.databinding.ActivitySearchBinding;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Busqueda dentro de la app: se escribe un tema y se elige el video, sin salir a copiar
 * links al navegador. Los resultados vienen del extractor con `ytsearch`, en un solo pedido.
 */
public class SearchActivity extends AppCompatActivity implements SearchAdapter.Pick {

    /** Lo que devuelve la pantalla: el link elegido y si se agrega sin preguntar calidad. */
    public static final String EXTRA_URL = "com.fork.ytdl.BUSQUEDA_URL";
    public static final String EXTRA_TITLE = "com.fork.ytdl.BUSQUEDA_TITULO";
    public static final String EXTRA_DIRECT = "com.fork.ytdl.BUSQUEDA_DIRECTO";

    private static final ExecutorService THREADS = Executors.newSingleThreadExecutor();

    private ActivitySearchBinding binding;
    private final SearchAdapter adapter = new SearchAdapter();
    private boolean busy;
    /** Numero de busqueda en curso: si se lanza otra antes de que vuelva la primera, la
     *  respuesta vieja se descarta en lugar de pintar resultados de otra pantalla. */
    private int generation;

    @Override
    protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.searchList.setLayoutManager(new LinearLayoutManager(this));
        binding.searchList.setAdapter(adapter);
        adapter.setPick(this);

        binding.searchBack.setOnClickListener(v -> finish());
        binding.searchInput.setOnEditorActionListener((text, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                run(text == null ? "" : text.toString());
                return true;
            }
            return false;
        });
        binding.searchInput.requestFocus();
        keyboard(true);
    }

    @Override
    protected void onDestroy() {
        adapter.setPick(null);
        super.onDestroy();
    }

    private void run(String query) {
        final String text = query == null ? "" : query.trim();
        if (text.isEmpty() || busy) {
            return;
        }
        final int mine = ++generation;
        busy = true;
        keyboard(false);
        binding.searchProgress.setVisibility(View.VISIBLE);
        binding.searchStatus.setText(R.string.buscando);
        final Context app = getApplicationContext();
        THREADS.execute(() -> {
            Search.Result found;
            try {
                Engine.init(app);
                found = Search.parse(Engine.search(text, 15));
            } catch (Exception e) {
                Logs.append(null, "busqueda: " + e);
                found = Search.parse(null);
            }
            final Search.Result result = found;
            runOnUiThread(() -> paint(mine, text, result));
        });
    }

    private void paint(int mine, String query, Search.Result result) {
        if (binding == null || mine != generation) {
            return;
        }
        busy = false;
        binding.searchProgress.setVisibility(View.GONE);
        List<Search.Item> items = result.items;
        adapter.submit(items);
        if (!result.ok) {
            binding.searchStatus.setText(Errors.reason(this, result.errorKind, null));
            return;
        }
        binding.searchStatus.setText(getString(R.string.buscar_resultados, items.size(), query));
    }

    @Override
    public void onPick(@NonNull Search.Item item, boolean direct) {
        Intent data = new Intent()
                .putExtra(EXTRA_URL, item.url)
                .putExtra(EXTRA_TITLE, item.title)
                .putExtra(EXTRA_DIRECT, direct);
        setResult(RESULT_OK, data);
        finish();
    }

    private void keyboard(boolean show) {
        InputMethodManager ime = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (ime == null) {
            return;
        }
        if (show) {
            ime.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT);
        } else {
            ime.hideSoftInputFromWindow(binding.searchInput.getWindowToken(), 0);
        }
    }
}
