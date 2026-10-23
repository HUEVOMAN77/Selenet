package com.fork.ytdl;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import androidx.core.widget.NestedScrollView;

import com.fork.ytdl.databinding.ItemFormatBinding;
import com.fork.ytdl.databinding.SheetFormatsBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.function.Predicate;

/**
 * Hoja modal de calidades. Antes era un cuadro de texto con una lista simple; aqui cada opcion
 * es una fila con su numero grande, su tamanho y como se arma, para que se entienda de una
 * mirada cual conviene en el telefono.
 */
public final class FormatSheet {

    /** Alturas de la hoja: la fila medida en item_format.xml y lo que se deja ver. */
    private static final int ROW_DP = 72;
    private static final float MAX_SHARE = 0.46f;

    public interface Listener {
        /** Opcion elegida y que el telefono puede bajar tal cual. */
        void onPick(Formats.Option option);

        /** Opcion que viene en dos pistas que el telefono no puede juntar. */
        void onBlocked(Formats.Option option);

        /** Se prefiere la calidad que ya esta guardada en Ajustes. */
        void onUseSaved();
    }

    private FormatSheet() {
    }

    /**
     * Muestra la hoja. `note` es la linea de datos del video (canal, duracion, vistas) que ya
     * arma DownloadFragment, y `remembered` marca la opcion que coincide con la calidad guardada.
     */
    public static void show(Context context, Formats.Result result, String note,
                            Predicate<Formats.Option> remembered, Listener listener) {
        SheetFormatsBinding sheet = SheetFormatsBinding.inflate(LayoutInflater.from(context));
        BottomSheetDialog dialog = new BottomSheetDialog(context);

        sheet.sheetTitle.setText(result.title.isEmpty()
                ? context.getString(R.string.fmt_titulo) : result.title);
        sheet.sheetNote.setText(note);
        sheet.sheetNote.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);

        int dp72 = dp(context, ROW_DP);
        for (int i = 0; i < result.options.size(); i++) {
            Formats.Option option = result.options.get(i);
            ItemFormatBinding row = ItemFormatBinding.inflate(LayoutInflater.from(context),
                    sheet.sheetRows, false);
            bind(context, row, option, remembered.test(option));
            row.getRoot().setOnClickListener(v -> {
                if (option.needsMerge() && !option.mergeable()) {
                    // la hoja se queda abierta: hay que poder elegir otra
                    listener.onBlocked(option);
                    return;
                }
                dialog.dismiss();
                listener.onPick(option);
            });
            sheet.sheetRows.addView(row.getRoot());
        }
        sheet.sheetRows.setLayoutAnimation(
                AnimationUtils.loadLayoutAnimation(context, R.anim.list_enter));

        // la hoja no debe crecer mas de media pantalla: si hay muchas opciones, se rasca
        int wanted = dp72 * result.options.size();
        int cap = (int) (context.getResources().getDisplayMetrics().heightPixels * MAX_SHARE);
        if (wanted > cap) {
            NestedScrollView scroll = sheet.sheetScroll;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                    scroll.getLayoutParams();
            params.height = cap;
            params.weight = 0f;
            scroll.setLayoutParams(params);
        }

        sheet.sheetSaved.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onUseSaved();
        });

        dialog.setContentView(sheet.getRoot());
        dialog.show();
    }

    private static void bind(Context context, ItemFormatBinding row, Formats.Option option,
                             boolean remembered) {
        row.formatBadge.setText(badge(context, option));
        row.formatTitle.setText(option.shortLabel(context));
        row.formatNote.setText(footnote(context, option));
        row.formatMark.setVisibility(remembered ? View.VISIBLE : View.INVISIBLE);
        if (option.needsMerge() && !option.mergeable()) {
            row.getRoot().setAlpha(0.45f);
        }
    }

    /** Lo que entra en la pastilla: la altura, o el contenedor cuando no hay una. */
    private static String badge(Context context, Formats.Option option) {
        if (option.height > 0) {
            return context.getString(R.string.fmt_altura, option.height);
        }
        return option.ext.isEmpty() ? "?" : option.ext.toUpperCase(java.util.Locale.US);
    }

    /** Tamanho aproximado y si el telefono arma solo la pista. */
    private static String footnote(Context context, Formats.Option option) {
        StringBuilder out = new StringBuilder(
                option.size > 0 ? android.text.format.Formatter.formatShortFileSize(context,
                        option.size)
                        : context.getString(R.string.fmt_sin_medida));
        if (option.mergeable()) {
            out.append("  \u00b7  ").append(context.getString(R.string.fmt_se_junta));
        } else if (option.needsMerge()) {
            out.append("  \u00b7  ").append(context.getString(R.string.fmt_requiere_pc));
        }
        return out.toString();
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }
}
