package com.fork.ytdl;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fork.ytdl.databinding.ItemQueueBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.Holder> {

    private final List<Task> items = new ArrayList<>();
    private String query = "";

    /** Texto del buscador de la cola; vacio muestra todo. */
    public void setQuery(String text) {
        query = text == null ? "" : text.trim().toLowerCase(java.util.Locale.US);
    }

    public boolean hasQuery() {
        return !query.isEmpty();
    }

    public void submit(List<Task> tasks) {
        items.clear();
        for (Task task : tasks) {
            if (!Task.DONE.equals(task.state) && matches(task)) {
                items.add(task);
            }
        }
        notifyDataSetChanged();
    }

    /** Filtra por titulo o por link, sin tildes ni mayusculas, para que "audio" alcance. */
    private boolean matches(Task task) {
        if (query.isEmpty()) {
            return true;
        }
        return normalize(task.displayName()).contains(query)
                || normalize(task.url).contains(query)
                || normalize(task.label).contains(query);
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String flat = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return flat.toLowerCase(java.util.Locale.US);
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
            b.itemSubtitle.setText(task.qualityLabel(context));
            b.itemProgress.setProgress(Math.max(0, Math.min(100, task.percent)));
            b.itemState.setText(stateLabel(context, task));
            b.itemState.setTextColor(stateColor(context, task));
            b.itemDot.setBackgroundTintList(ColorStateList.valueOf(stateColor(context, task)));
            Thumbs.show(b.itemThumb, task.url);

            boolean failed = Task.ERROR.equals(task.state);
            b.itemError.setVisibility(failed ? android.view.View.VISIBLE
                    : android.view.View.GONE);
            if (failed) {
                b.itemErrorText.setText(Errors.message(context, task));
            }

            String action;
            if (Task.PAUSED.equals(task.state)) {
                action = context.getString(R.string.btn_reanudar);
            } else if (Task.ERROR.equals(task.state)) {
                // si quedo el MP4 de reserva, el boton ofrece guardarlo en vez de reintentar
                action = context.getString(task.fallback == null
                        ? R.string.btn_reintentar : R.string.btn_guardar_mp4_corto);
            } else {
                action = context.getString(R.string.btn_pausar);
            }
            b.itemAction.setText(action);
            b.itemAction.setIconResource(actionIcon(task));
            b.itemCancel.setVisibility(
                    Task.QUEUED.equals(task.state) && task.percent == 0
                            ? android.view.View.GONE : android.view.View.VISIBLE);

            b.itemAction.setOnClickListener(v -> act());
            b.itemCancel.setOnClickListener(v -> DownloadService.cancel(context, task));
            b.getRoot().setOnLongClickListener(v -> {
                showError(context);
                return true;
            });
        }

        /** Guarda la reserva (el MP4 que bajo el modo audio) y avisa del resultado. */
        private void saveFallback(Context context) {
            if (DownloadService.saveFallback(context, task)) {
                Toast.makeText(context, R.string.mp4_guardado, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, R.string.err_sin_destino, Toast.LENGTH_LONG).show();
            }
        }

        /** Detalle tecnico del fallo, para copiar o pegar en un foro. */
        private void showError(Context context) {
            if (!Task.ERROR.equals(task.state)) {
                String log = Logs.forTask(task);
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.err_detalle_titulo)
                        .setMessage(log.isEmpty() ? context.getString(R.string.sin_log) : log)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            StringBuilder text = new StringBuilder(Errors.message(context, task));
            if (task.errorDetail != null && !task.errorDetail.isEmpty()) {
                text.append("\n\n").append(context.getString(R.string.err_detalle))
                        .append(": ").append(task.errorDetail);
            }
            if (task.attempts > 0) {
                text.append("\n").append(context.getString(R.string.err_intentos,
                        task.attempts));
            }
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.err_detalle_titulo)
                    .setMessage(text.toString())
                    .setNeutralButton(R.string.btn_copiar_detalle, (d, which) -> {
                        ClipboardManager clipboard = (ClipboardManager)
                                context.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(
                                ClipData.newPlainText("ytdl", text.toString()));
                        Toast.makeText(context, R.string.log_copiado, Toast.LENGTH_SHORT).show();
                    });
            if (task.fallback != null) {
                dialog.setPositiveButton(R.string.btn_guardar_mp4,
                        (d, which) -> saveFallback(context));
            } else {
                dialog.setPositiveButton(android.R.string.ok, null);
            }
            dialog.show();
        }

        private void act() {
            Context context = b.getRoot().getContext();
            if (Task.ERROR.equals(task.state) && task.fallback != null) {
                saveFallback(context);
                return;
            }
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
            String stage = Task.STAGE_EXTRACT.equals(task.stage)
                    ? context.getString(R.string.estado_extrayendo)
                    : context.getString(R.string.estado_descargando);
            return stage + " " + task.percent + "%";
        }
        if (Task.PAUSED.equals(task.state)) {
            return context.getString(R.string.estado_pausada) + " " + task.percent + "%";
        }
        if (Task.ERROR.equals(task.state)) {
            // el motivo completo se lee en la caja roja de la propia fila
            return context.getString(R.string.estado_error);
        }
        return context.getString(R.string.estado_en_espera);
    }

    /** Color fuerte del estado: texto, puntito y barra de progreso. */
    private static int stateColor(Context context, Task task) {
        return context.getColor(stateColorRes(task));
    }

    private static int stateColorRes(Task task) {
        if (Task.ERROR.equals(task.state)) {
            return R.color.state_error_fg;
        }
        if (Task.RUNNING.equals(task.state)) {
            return R.color.state_active_fg;
        }
        if (Task.PAUSED.equals(task.state)) {
            return R.color.state_warn_fg;
        }
        if (Task.DONE.equals(task.state)) {
            return R.color.state_done_fg;
        }
        return R.color.state_idle_fg;
    }

    private static int actionIcon(Task task) {
        if (Task.ERROR.equals(task.state) && task.fallback != null) {
            return R.drawable.ic_download;
        }
        if (Task.PAUSED.equals(task.state) || Task.ERROR.equals(task.state)) {
            return R.drawable.ic_refresh;
        }
        return R.drawable.ic_state_paused;
    }
}
