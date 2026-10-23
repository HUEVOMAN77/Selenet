package com.fork.ytdl;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fork.ytdl.databinding.FragmentHistoryBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryFragment extends Fragment
        implements DownloadService.Listener, HistoryAdapter.Reload {

    private static final ExecutorService THREADS = Executors.newSingleThreadExecutor();

    private FragmentHistoryBinding binding;
    private final HistoryAdapter adapter = new HistoryAdapter();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean loading;
    private boolean pending;

    private static final int F_ALL = 0;
    private static final int F_VIDEO = 1;
    private static final int F_AUDIO = 2;

    /** Lo que hay en disco, sin filtrar, con el tamano ya medido para poder sumar. */
    private List<Task> all = new ArrayList<>();
    private long[] bytes = new long[0];
    private int filter = F_ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        binding.historyList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.historyList.setAdapter(adapter);
        adapter.setReload(this);
        binding.histFilterGroup.check(R.id.hist_filter_all);
        binding.histFilterGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            filter = checkedId == R.id.hist_filter_video ? F_VIDEO
                    : checkedId == R.id.hist_filter_audio ? F_AUDIO : F_ALL;
            apply();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        DownloadService.addListener(this);
        refresh();
    }

    @Override
    public void onPause() {
        DownloadService.removeListener(this);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onQueueChanged() {
        if (binding != null) {
            refresh();
        }
    }

    @Override
    public void reload() {
        if (binding != null) {
            refresh();
        }
    }

    /**
     * Recorrer carpetas y abrir videos cuesta, asi que se hace afuera del hilo de la interfaz.
     * Si llegan cambios mientras se esta leyendo, se anotan y se vuelve al terminar.
     */
    private void refresh() {
        if (binding == null) {
            return;
        }
        if (loading) {
            pending = true;
            return;
        }
        loading = true;
        pending = false;
        binding.historyEmpty.setVisibility(View.VISIBLE);
        binding.historyEmptyText.setText(R.string.hist_contando);
        Context app = requireContext().getApplicationContext();
        THREADS.execute(() -> {
            final List<Task> found = Library.all(app);
            // Los tamafios se miden aca, en el hilo de fondo: sobre una carpeta por SAF
            // preguntar el tamaño es lento y en el hilo de la interfaz cortaria el scroll.
            final long[] sizes = new long[found.size()];
            for (int i = 0; i < found.size(); i++) {
                Task task = found.get(i);
                sizes[i] = task.bytes > 0 ? task.bytes
                        : task.path == null ? 0 : Storage.size(app, task.path);
            }
            main.post(() -> {
                loading = false;
                if (binding == null || !isAdded()) {
                    return;
                }
                all = found;
                bytes = sizes;
                apply();
                if (pending) {
                    pending = false;
                    refresh();
                }
            });
        });
    }

    /** Aplica el filtro de tipo y pinta la bajada con el recuento real. */
    private void apply() {
        List<Task> shown = new ArrayList<>();
        long total = 0;
        long shownTotal = 0;
        for (int i = 0; i < all.size(); i++) {
            Task task = all.get(i);
            total += bytes[i];
            boolean audio = task.path != null && Storage.isAudioName(task.path);
            if (filter == F_VIDEO && audio || filter == F_AUDIO && !audio) {
                continue;
            }
            shown.add(task);
            shownTotal += bytes[i];
        }
        adapter.submit(shown);
        boolean empty = shown.isEmpty();
        binding.historyEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.historyEmptyText.setText(all.isEmpty()
                ? R.string.historial_vacio : R.string.hist_filtro_vacio);
        if (all.isEmpty()) {
            binding.historySubtitle.setText(R.string.portada_historial_sub);
        } else if (filter == F_ALL) {
            binding.historySubtitle.setText(getString(R.string.hist_resumen, all.size(),
                    android.text.format.Formatter.formatShortFileSize(requireContext(), total)));
        } else {
            binding.historySubtitle.setText(getString(R.string.hist_resumen_filtro,
                    shown.size(), all.size(),
                    android.text.format.Formatter.formatShortFileSize(requireContext(), shownTotal)));
        }
    }
}
