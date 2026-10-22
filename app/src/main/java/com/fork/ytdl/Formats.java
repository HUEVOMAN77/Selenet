package com.fork.ytdl;

import android.content.Context;
import android.text.format.Formatter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
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

        Option(String id, String kind, String ext, int height, int fps, long size) {
            this.id = id;
            this.kind = kind;
            this.ext = ext;
            this.height = height;
            this.fps = height == 0 ? 0 : fps;
            this.size = size;
        }

        public boolean needsMerge() {
            return "merge".equals(kind);
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

        /** Texto de una linea con el tamanho aproximado y el aviso de merge. */
        public String label(Context context) {
            String text = shortLabel(context) + " · "
                    + (size > 0 ? Formatter.formatShortFileSize(context, size)
                    : context.getString(R.string.fmt_sin_medida));
            return needsMerge()
                    ? text + " · " + context.getString(R.string.fmt_requiere_pc) : text;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String errorKind;
        public final String title;
        public final int seconds;
        public final List<Option> options;

        private Result(boolean ok, String errorKind, String title, int seconds,
                       List<Option> options) {
            this.ok = ok;
            this.errorKind = errorKind;
            this.title = title;
            this.seconds = seconds;
            this.options = options;
        }

        static Result fail(String kind) {
            return new Result(false, kind == null ? "error" : kind, "", 0,
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
                            row.optLong("size")));
                }
            }
            if (options.isEmpty()) {
                return Result.fail("format");
            }
            return new Result(true, null, root.optString("title", ""),
                    root.optInt("duration"), options);
        } catch (Exception e) {
            return Result.fail("error");
        }
    }
}
