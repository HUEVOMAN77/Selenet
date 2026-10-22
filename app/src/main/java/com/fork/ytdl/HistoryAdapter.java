package com.fork.ytdl;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fork.ytdl.databinding.ItemHistoryBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

    private static final SimpleDateFormat DAY =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);

    /** Aviso de que la lista cambio afuera del adaptador (borrado de un archivo). */
    public interface Reload {
        void reload();
    }

    private final List<Task> items = new ArrayList<>();
    private Reload reload;

    public void setReload(Reload callback) {
        reload = callback;
    }

    public void submit(List<Task> tasks) {
        items.clear();
        items.addAll(tasks);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ItemHistoryBinding b;

        Holder(ItemHistoryBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(Task task) {
            Context context = b.getRoot().getContext();
            b.histTitle.setText(task.displayName());
            long size = task.bytes > 0 ? task.bytes : Storage.size(context, task.path);
            String when = task.finishedAt > 0 ? DAY.format(new Date(task.finishedAt)) : "";
            String kind = task.qualityLabel(context);
            b.histMeta.setText(kind + " · " + Formatter.formatShortFileSize(context, size)
                    + (when.isEmpty() ? "" : " · " + when));
            b.histDelete.setVisibility(task.path == null ? View.GONE : View.VISIBLE);
            // "Volver a descargar" solo tiene sentido si la app sabe de que link vino
            b.histAgain.setVisibility(
                    task.url == null || task.url.isEmpty() ? View.GONE : View.VISIBLE);
            if (task.url != null && !task.url.isEmpty()) {
                Thumbs.show(b.histThumb, task.url);
            } else {
                Thumbs.showFile(b.histThumb, context, task.path);
            }

            b.histOpen.setOnClickListener(v -> show(context, task, false));
            b.histShare.setOnClickListener(v -> show(context, task, true));
            b.histAgain.setOnClickListener(v -> DownloadService.enqueue(
                    context, task.url, task.fmt, task.label));
            b.histDelete.setOnClickListener(v -> confirmDelete(context, task));
        }

        /** Antes de tirar un archivo se pide confirmacion: puede ser algo que no bajo esta app. */
        private void confirmDelete(Context context, Task task) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.hist_borrado_titulo)
                    .setMessage(context.getString(R.string.hist_borrado_mensaje,
                            Storage.nameOf(task.path)))
                    .setPositiveButton(R.string.borrar, (dialog, which) -> delete(context, task))
                    .setNegativeButton(R.string.clipboard_descartar, null)
                    .show();
        }

        private void delete(Context context, Task task) {
            if (!Storage.delete(context, task.path)) {
                Toast.makeText(context, R.string.hist_borrado_fallo, Toast.LENGTH_LONG).show();
                return;
            }
            HistoryDb.get(context).remove(task.id);
            HistoryDb.get(context).removePath(task.path);
            Toast.makeText(context, R.string.borrado, Toast.LENGTH_SHORT).show();
            if (reload != null) {
                reload.reload();
            }
        }

        private void show(Context context, Task task, boolean share) {
            Uri uri = Storage.shareUri(context, task.path);
            if (uri == null) {
                Toast.makeText(context, R.string.hist_sin_ruta, Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(share ? Intent.ACTION_SEND : Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setType(Storage.typeOf(task.path));
            if (!share) {
                intent.setData(uri);
            } else {
                intent.putExtra(Intent.EXTRA_STREAM, uri);
            }
            try {
                context.startActivity(Intent.createChooser(intent, context.getString(
                        share ? R.string.compartir : R.string.abrir)));
            } catch (ActivityNotFoundException | IllegalArgumentException e) {
                Toast.makeText(context, R.string.sin_app, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
