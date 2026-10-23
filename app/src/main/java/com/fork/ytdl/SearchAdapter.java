package com.fork.ytdl;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fork.ytdl.databinding.ItemSearchBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fila de resultado de busqueda: miniatura, titulo, canal y vistas. El toque elige el video
 * y la pulsacion larga lo agrega directo con la calidad guardada, sin pasar por el selector.
 */
public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.Holder> {

    public interface Pick {
        void onPick(Search.Item item, boolean direct);
    }

    private final List<Search.Item> items = new ArrayList<>();
    private Pick pick;

    public void setPick(Pick callback) {
        pick = callback;
    }

    public void submit(List<Search.Item> found) {
        items.clear();
        items.addAll(found);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemSearchBinding.inflate(
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
        private final ItemSearchBinding b;

        Holder(ItemSearchBinding binding) {
            super(binding.getRoot());
            b = binding;
            Motion.press(b.getRoot());
        }

        void bind(Search.Item item) {
            Context context = b.getRoot().getContext();
            b.itemTitle.setText(item.title.isEmpty() ? context.getString(R.string.buscar_sin_titulo)
                    : item.title);
            b.itemMeta.setText(meta(context, item));
            b.itemTime.setText(clock(item.seconds));
            b.itemTime.setVisibility(item.seconds > 0 ? View.VISIBLE : View.GONE);
            Thumbs.show(b.itemThumb, item.url);
            b.getRoot().setOnClickListener(v -> send(item, false));
            b.getRoot().setOnLongClickListener(v -> {
                send(item, true);
                return true;
            });
        }

        private void send(Search.Item item, boolean direct) {
            if (pick != null) {
                pick.onPick(item, direct);
            }
        }
    }

    private static String meta(Context context, Search.Item item) {
        StringBuilder out = new StringBuilder();
        if (!item.uploader.isEmpty()) {
            out.append(item.uploader);
        }
        if (item.views > 0) {
            piece(out, context.getString(R.string.buscar_vistas, compact(item.views)));
        }
        if (out.length() == 0) {
            out.append(context.getString(R.string.buscar_sin_datos));
        }
        return out.toString();
    }

    private static void piece(StringBuilder out, String text) {
        if (out.length() > 0) {
            out.append(" · ");
        }
        out.append(text);
    }

    /** "1,2 M" / "340 K": en una pantalla de celular no entran siete digitos. */
    static String compact(long value) {
        if (value >= 1_000_000) {
            return String.format(Locale.US, "%.1f M", value / 1_000_000f);
        }
        if (value >= 1_000) {
            return String.format(Locale.US, "%.0f K", value / 1_000f);
        }
        return String.valueOf(value);
    }

    static String clock(int total) {
        int minutes = total / 60;
        int seconds = total % 60;
        if (minutes >= 60) {
            return String.format(Locale.US, "%d:%02d:%02d", minutes / 60, minutes % 60, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
}
