package com.fork.ytdl;

import android.content.Context;
import android.text.format.Formatter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Lectura del JSON que devuelve backend.formats(). */
public final class Formats {

    public static final class Option {
        public final String id;
        public final String kind;
        public final String ext;
        public final int height;
        public final int fps;
        public final long size;
        /**
         * Formato de la pista de audio con la que hay que juntar este video suelto; vacio
         * cuando el video ya trae audio o cuando no hay con que juntarlo en el telefono.
         */
        public final String audio;

        Option(String id, String kind, String ext, int height, int fps, long size,
               String audio) {
            this.id = id;
            this.kind = kind;
            this.ext = ext;
            this.height = height;
            this.fps = height == 0 ? 0 : fps;
            this.size = size;
            this.audio = audio == null ? "" : audio;
        }

        public boolean needsMerge() {
            return "merge".equals(kind);
        }

        /** Video suelto que la app puede juntar con su pista de audio, sin ffmpeg. */
        public boolean mergeable() {
            return needsMerge() && !audio.isEmpty();
        }

        public boolean isAudio() {
            return "audio".equals(kind);
        }

        /** Texto corto, para la cola y el historial: "720p mp4 60fps". */
        public String shortLabel(Context context) {
            if (isAudio()) {
                return context.getString(R.string.fmt_solo_audio) + " " + ext;
            }
            if (height > 0) {
                String base = context.getString(R.string.fmt_altura, height) + " " + ext;
                return fps > 0 ? base + " " + fps + "fps" : base;
            }
            return context.getString(R.string.fmt_normal, ext);
        }

        /** Texto de una linea con el tamanho aproximado y como se consigue esa calidad. */
        public String label(Context context) {
            String text = shortLabel(context) + " · "
                    + (size > 0 ? Formatter.formatShortFileSize(context, size)
                    : context.getString(R.string.fmt_sin_medida));
            if (mergeable()) {
                return text + " · " + context.getString(R.string.fmt_se_junta);
            }
            return needsMerge()
                    ? text + " · " + context.getString(R.string.fmt_requiere_pc) : text;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String errorKind;
        public final String title;
        public final int seconds;
        /** Canal o cuenta que subio el video; vacio si el sitio no lo informa. */
        public final String uploader;
        public final long views;
        /** Alta en formato YYYYMMDD del extractor, vacia cuando no viene. */
        public final String date;
        /** Bytes aproximados de la mejor opcion; 0 si el sitio no los dice. */
        public final long size;
        public final List<Option> options;

        private Result(boolean ok, String errorKind, String title, int seconds,
                       String uploader, long views, String date, long size,
                       List<Option> options) {
            this.ok = ok;
            this.errorKind = errorKind;
            this.title = title;
            this.seconds = seconds;
            this.uploader = uploader;
            this.views = views;
            this.date = date;
            this.size = size;
            this.options = options;
        }

        static Result fail(String kind) {
            return new Result(false, kind == null ? "error" : kind, "", 0, "", 0, "", 0,
                    new ArrayList<Option>());
        }
    }

    private Formats() {
    }

    public static Result parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!root.optBoolean("ok", false)) {
                return Result.fail(root.optString("kind", "error"));
            }
            List<Option> options = new ArrayList<>();
            JSONArray rows = root.optJSONArray("formats");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) {
                        continue;
                    }
                    options.add(new Option(row.optString("id"), row.optString("kind"),
                            row.optString("ext"), row.optInt("height"), row.optInt("fps"),
                            row.optLong("size"), row.optString("audio", "")));
                }
            }
            if (options.isEmpty()) {
                return Result.fail("format");
            }
            // el motor devuelve lo mejor primero; la pantalla se muestra al reves,
            // de la resolucion mas baja a la mas alta, y lo que pide PC y el audio suelto
            // quedan al final
            Collections.sort(options, new Comparator<Option>() {
                @Override
                public int compare(Option a, Option b) {
                    int group = group(a) - group(b);
                    return group != 0 ? group : a.height - b.height;
                }

                private int group(Option option) {
                    if (option.isAudio()) {
                        return 2;
                    }
                    // la que se junta en el telefono cuenta como opcion normal
                    return option.needsMerge() && !option.mergeable() ? 1 : 0;
                }
            });
            return new Result(true, null, root.optString("title", ""),
                    root.optInt("duration"), root.optString("uploader", ""),
                    root.optLong("views"), root.optString("date", ""),
                    root.optLong("size"), options);
        } catch (Exception e) {
            return Result.fail("error");
        }
    }
}
