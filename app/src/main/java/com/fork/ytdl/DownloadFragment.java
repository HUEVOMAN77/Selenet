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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class DownloadFragment extends Fragment implements DownloadService.Listener {

    private FragmentDownloadBinding binding;
    private final QueueAdapter adapter = new QueueAdapter();
    private static String asked;

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
        } else {
            offerClipboard();
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
        String typed = binding.urlInput.getText() == null
                ? "" : binding.urlInput.getText().toString().trim();
        String url = firstUrl(typed);
        if (url == null) {
            Toast.makeText(requireContext(), R.string.link_invalido, Toast.LENGTH_SHORT).show();
            return;
        }
        DownloadService.enqueue(requireContext(), url, Quality.saved(requireContext()));
        binding.urlInput.setText("");
        Toast.makeText(requireContext(), R.string.agregada, Toast.LENGTH_SHORT).show();
    }

    /** Si hay un link en el portapapeles lo ofrece, pero no descarga nada solo. */
    private void offerClipboard() {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()
                || clipboard.getPrimaryClip().getItemCount() == 0) {
            return;
        }
        CharSequence raw = clipboard.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
        final String url = firstUrl(raw == null ? "" : raw.toString());
        if (url == null || url.equals(asked)) {
            return;
        }
        asked = url;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clipboard_titulo)
                .setMessage(getString(R.string.clipboard_mensaje) + "\n\n" + url)
                .setPositiveButton(R.string.clipboard_pegar, (dialog, which) ->
                        binding.urlInput.setText(url))
                .setNegativeButton(R.string.clipboard_descartar, null)
                .show();
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

    private static final java.util.regex.Pattern HTTP =
            java.util.regex.Pattern.compile("https?://[^\\s\"'<>]+");
    private static final java.util.regex.Pattern BARE =
            java.util.regex.Pattern.compile("(www\\.[^\\s/]+\\.[a-z]{2,}[^\\s]*"
                    + "|[\\w-]+\\.[a-z]{2,}(/[^\\s]*)?)");

    /** Sacar el primer link de un texto, o null si no hay ninguno. */
    static String firstUrl(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        java.util.regex.Matcher matcher = HTTP.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        String trimmed = text.trim();
        return BARE.matcher(trimmed).matches() ? "https://" + trimmed : null;
    }
}
