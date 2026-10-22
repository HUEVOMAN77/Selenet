package com.fork.ytdl;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fork.ytdl.databinding.FragmentDownloadBinding;

import java.util.List;

public class DownloadFragment extends Fragment implements DownloadService.Listener {

    private FragmentDownloadBinding binding;
    private final QueueAdapter adapter = new QueueAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        binding = FragmentDownloadBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        binding.queueList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.queueList.setAdapter(adapter);

        binding.btnAdd.setOnClickListener(v -> add());
        binding.btnClearPart.setOnClickListener(v -> clearParts());
        binding.btnCopyLog.setOnClickListener(v -> copyLog());

        MainActivity activity = (MainActivity) requireActivity();
        String pending = activity.takePendingUrl();
        if (pending != null) {
            binding.urlInput.setText(pending);
        }
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

    private void add() {
        String url = binding.urlInput.getText() == null
                ? "" : binding.urlInput.getText().toString().trim();
        if (!looksLikeUrl(url)) {
            Toast.makeText(requireContext(), R.string.link_invalido, Toast.LENGTH_SHORT).show();
            return;
        }
        DownloadService.enqueue(requireContext(), url, Quality.saved(requireContext()));
        binding.urlInput.setText("");
        Toast.makeText(requireContext(), R.string.agregada, Toast.LENGTH_SHORT).show();
    }

    private void clearParts() {
        int deleted = DownloadService.cleanupOrphans(requireContext());
        Toast.makeText(requireContext(),
                getString(R.string.part_borrados, deleted), Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void copyLog() {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("ytdl-log", Logs.global()));
        Toast.makeText(requireContext(), R.string.log_copiado, Toast.LENGTH_SHORT).show();
    }

    private void refresh() {
        List<Task> tasks = DownloadService.snapshot();
        adapter.submit(tasks);
        boolean empty = adapter.getItemCount() == 0;
        binding.queueEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.logText.setText(Logs.global());
        binding.logScroll.post(() -> binding.logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private static boolean looksLikeUrl(String text) {
        if (text.length() < 5 || text.indexOf('.') < 0 || text.indexOf(' ') >= 0) {
            return false;
        }
        return text.startsWith("http") || text.contains("www.") || text.contains("://");
    }
}
