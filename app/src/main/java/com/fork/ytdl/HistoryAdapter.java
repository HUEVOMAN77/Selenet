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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

    private static final SimpleDateFormat DAY =
            new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private final List<Task> items = new ArrayList<>();

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
            b.histMeta.setText(Formatter.formatShortFileSize(context, size)
                    + (when.isEmpty() ? "" : " · " + when));
            b.histDelete.setVisibility(task.path == null ? View.GONE : View.VISIBLE);

            b.histOpen.setOnClickListener(v -> show(context, task, false));
            b.histShare.setOnClickListener(v -> show(context, task, true));
            b.histAgain.setOnClickListener(v -> DownloadService.enqueue(
                    context, task.url, Quality.saved(context)));
            b.histDelete.setOnClickListener(v -> {
                Storage.delete(context, task.path);
                HistoryDb.get(context).remove(task.id);
                Toast.makeText(context, R.string.borrado, Toast.LENGTH_SHORT).show();
                submit(HistoryFragment.load(context));
            });
        }

        private void show(Context context, Task task, boolean share) {
            Uri uri = Storage.shareUri(context, task.path);
            Intent intent = new Intent(share ? Intent.ACTION_SEND : Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setType("video/*");
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
