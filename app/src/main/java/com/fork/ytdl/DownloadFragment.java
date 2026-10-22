package com.fork.ytdl;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadFragment extends Fragment implements DownloadService.Listener {

    private FragmentDownloadBinding binding;
    private final QueueAdapter adapter = new QueueAdapter();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService THREADS = Executors.newSingleThreadExecutor();
    private static String asked;
    private boolean reading;

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

        binding.btnFmtAuto.setChecked(true);

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
        binding.logCard.setVisibility(
                Storage.logVisible(requireContext()) ? View.VISIBLE : View.GONE);
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
        if (reading) {
            return;
        }
        if (binding.btnFmtMp4.isChecked()) {
            enqueue(url, Quality.MP4, Quality.label(requireContext(), Quality.MP4));
            return;
        }
        if (binding.btnFmtMp3.isChecked()) {
            enqueue(url, Quality.AUDIO, Quality.label(requireContext(), Quality.AUDIO));
            return;
        }
        readFormats(url);
    }

    /** Lee los formatos del motor y muestra los que se pueden bajar sin merge. */
    private void readFormats(String url) {
        reading = true;
        Context app = requireContext().getApplicationContext();
        binding.btnAdd.setEnabled(false);
        setStatus(app.getString(R.string.fmt_obteniendo), true);
        THREADS.execute(() -> {
            Formats.Result result;
            try {
                Engine.init(app);
                result = Formats.parse(Engine.formats(url));
            } catch (Exception e) {
                Logs.append(null, "formatos: " + e);
                result = Formats.Result.fail("error");
            }
            final Formats.Result found = result;
            main.post(() -> {
                reading = false;
                if (binding == null || !isAdded() || getContext() == null) {
                    return;
                }
                binding.btnAdd.setEnabled(true);
                setStatus("", false);
                if (found.ok) {
                    showQualities(url, found);
                } else {
                    Context context = requireContext();
                    Toast.makeText(context, getString(R.string.fmt_fallo,
                                    Errors.reason(context, found.errorKind, null)),
                            Toast.LENGTH_LONG).show();
                    enqueue(url, Quality.saved(context), Quality.savedLabel(context));
                }
            });
        });
    }

    private void showQualities(String url, Formats.Result result) {
        Context context = requireContext();
        String[] labels = new String[result.options.size()];
        String saved = Quality.saved(context);
        for (int i = 0; i < labels.length; i++) {
            Formats.Option option = result.options.get(i);
            labels[i] = option.label(context) + (option.id.equals(saved) ? "  ✓" : "");
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.fmt_titulo)
                .setMessage(header(context, result))
                .setItems(labels, (dialog, which) -> choose(url, result.options.get(which)))
                .setNegativeButton(R.string.q_usar_ultima,
                        (dialog, which) -> enqueue(url, Quality.saved(context),
                                Quality.savedLabel(context)))
                .show();
    }

    private void choose(String url, Formats.Option option) {
        Context context = requireContext();
        if (option.needsMerge()) {
            Toast.makeText(context, R.string.fmt_requiere_pc_detalle, Toast.LENGTH_LONG).show();
            return;
        }
        enqueue(url, option.id, option.shortLabel(context));
    }

    private void enqueue(String url, String fmt, String label) {
        Context context = requireContext();
        Quality.save(context, fmt, label);
        DownloadService.enqueue(context, url, fmt, label);
        binding.urlInput.setText("");
        Toast.makeText(context, R.string.agregada, Toast.LENGTH_SHORT).show();
    }

    private static String header(Context context, Formats.Result result) {
        StringBuilder text = new StringBuilder();
        if (!result.title.isEmpty()) {
            text.append(result.title);
        }
        if (result.seconds > 0) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(context.getString(R.string.fmt_duracion, clock(result.seconds)));
        }
        return text.toString();
    }

    private static String clock(int total) {
        int minutes = total / 60;
        int seconds = total % 60;
        if (minutes >= 60) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d", minutes / 60,
                    minutes % 60, seconds);
        }
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }

    private void setStatus(String text, boolean visible) {
        if (binding == null) {
            return;
        }
        binding.statusRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.statusText.setText(text);
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
        // se guarda la vista, no el binding: el post corre despues y el binding ya puede
        // estar borrado si la pestaña cambio mientras tanto
        android.widget.ScrollView scroll = binding.logScroll;
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
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
