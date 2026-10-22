package com.fork.ytdl;

import android.content.Context;
import android.os.Bundle;
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

public class HistoryFragment extends Fragment implements DownloadService.Listener {

    private FragmentHistoryBinding binding;
    private final HistoryAdapter adapter = new HistoryAdapter();

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

    private void refresh() {
        List<Task> done = load(requireContext());
        adapter.submit(done);
        binding.historyEmpty.setVisibility(
                adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    /** Terminadas y todavia con archivo disponible, leidas de la base. */
    static List<Task> load(Context context) {
        List<Task> done = new ArrayList<>();
        for (Task task : HistoryDb.get(context).all()) {
            if (Storage.exists(context, task.path)) {
                done.add(task);
            }
        }
        return done;
    }
}
