package com.fork.ytdl;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.fork.ytdl.databinding.ItemHistoryBinding;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

    private static final SimpleDateFormat DAY =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);

    private final List<Task> items = new ArrayList<>();

    public void submit(List<Task> tasks) {
        items.clear();
        for (Task task : tasks) {
            if (task.path != null && new File(task.path).exists()) {
                items.add(task);
            }
        }
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
            File file = new File(task.path);
            b.histTitle.setText(task.displayName());
            String size = Formatter.formatShortFileSize(context, file.length());
            String when = task.finishedAt > 0 ? DAY.format(new Date(task.finishedAt)) : "";
            b.histMeta.setText(size + (when.isEmpty() ? "" : " · " + when));

            b.histOpen.setOnClickListener(v -> open(context, file, false));
            b.histShare.setOnClickListener(v -> open(context, file, true));
            b.histAgain.setOnClickListener(v -> DownloadService.enqueue(
                    context, task.url, Quality.saved(context)));
            b.histDelete.setOnClickListener(v -> {
                if (file.delete()) {
                    DownloadService.forget(context, task);
                    Toast.makeText(context, context.getString(R.string.borrado), Toast.LENGTH_SHORT).show();
                    submit(HistoryFragment.load(context));
                }
            });
        }

        private void open(Context context, File file, boolean share) {
            Uri uri;
            try {
                uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".archivos", file);
            } catch (Exception e) {
                Toast.makeText(context, context.getString(R.string.sin_app), Toast.LENGTH_SHORT).show();
                return;
            }
            String mime = file.getName().toLowerCase(Locale.US).matches(".*\\.(m4a|mp3|aac)$")
                    ? "audio/*" : "video/*";
            Intent intent = new Intent(share ? Intent.ACTION_SEND : Intent.ACTION_VIEW)
                    .setType(mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (share) {
                intent.putExtra(Intent.EXTRA_STREAM, uri);
            } else {
                intent.setDataAndType(uri, mime);
            }
            try {
                context.startActivity(Intent.createChooser(intent,
                        context.getString(share ? R.string.compartir : R.string.abrir)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(context, context.getString(R.string.sin_app), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
