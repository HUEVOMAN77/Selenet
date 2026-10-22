package com.fork.ytdl;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fork.ytdl.databinding.ItemQueueBinding;

import java.util.ArrayList;
import java.util.List;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.Holder> {

    private final List<Task> items = new ArrayList<>();

    public void submit(List<Task> tasks) {
        items.clear();
        for (Task task : tasks) {
            if (!Task.DONE.equals(task.state)) {
                items.add(task);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemQueueBinding.inflate(
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
        private final ItemQueueBinding b;
        private Task task;

        Holder(ItemQueueBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(Task task) {
            this.task = task;
            Context context = b.getRoot().getContext();
            b.itemTitle.setText(task.displayName());
            b.itemSubtitle.setText(task.url + " · " + Quality.label(context, task.fmt));
            b.itemProgress.setProgress(Math.max(0, Math.min(100, task.percent)));
            b.itemState.setText(stateLabel(context, task));
            b.itemState.setTextColor(stateColor(context, task));

            String action;
            if (Task.PAUSED.equals(task.state)) {
                action = context.getString(R.string.btn_reanudar);
            } else if (Task.ERROR.equals(task.state)) {
                action = context.getString(R.string.btn_reintentar);
            } else {
                action = context.getString(R.string.btn_pausar);
            }
            b.itemAction.setText(action);
            b.itemCancel.setVisibility(
                    Task.QUEUED.equals(task.state) && task.percent == 0
                            ? android.view.View.GONE : android.view.View.VISIBLE);

            b.itemAction.setOnClickListener(v -> act());
            b.itemCancel.setOnClickListener(v -> DownloadService.cancel(context, task));
        }

        private void act() {
            Context context = b.getRoot().getContext();
            if (Task.PAUSED.equals(task.state) || Task.ERROR.equals(task.state)) {
                DownloadService.resume(context, task);
            } else {
                DownloadService.pause(context, task);
            }
        }
    }

    private static String stateLabel(Context context, Task task) {
        if (Task.DONE.equals(task.state)) {
            return context.getString(R.string.estado_completada);
        }
        if (Task.RUNNING.equals(task.state)) {
            return context.getString(R.string.estado_descargando) + " " + task.percent + "%";
        }
        if (Task.PAUSED.equals(task.state)) {
            return context.getString(R.string.estado_pausada) + " " + task.percent + "%";
        }
        if (Task.ERROR.equals(task.state)) {
            return Errors.message(context, task);
        }
        return context.getString(R.string.estado_en_espera);
    }

    private static int stateColor(Context context, Task task) {
        int color;
        if (Task.ERROR.equals(task.state)) {
            color = R.color.bad;
        } else if (Task.RUNNING.equals(task.state)) {
            color = R.color.brand;
        } else if (Task.PAUSED.equals(task.state)) {
            color = R.color.warn;
        } else {
            color = R.color.muted;
        }
        return context.getColor(color);
    }
}
