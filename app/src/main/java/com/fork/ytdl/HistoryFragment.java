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
            main.post(() -> {
                loading = false;
                if (binding == null || !isAdded()) {
                    return;
                }
                adapter.submit(found);
                boolean empty = adapter.getItemCount() == 0;
                binding.historyEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.historyEmptyText.setText(R.string.historial_vacio);
                if (pending) {
                    pending = false;
                    refresh();
                }
            });
        });
    }
}
