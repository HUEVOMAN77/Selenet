package com.fork.ytdl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Lectura del JSON que devuelve backend.search(): resultados de una busqueda por texto. */
public final class Search {

    public static final class Item {
        public final String title;
        public final String url;
        public final int seconds;
        public final String uploader;
        public final long views;

        Item(String title, String url, int seconds, String uploader, long views) {
            this.title = title;
            this.url = url;
            this.seconds = seconds;
            this.uploader = uploader;
            this.views = views;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String errorKind;
        public final List<Item> items;

        private Result(boolean ok, String errorKind, List<Item> items) {
            this.ok = ok;
            this.errorKind = errorKind;
            this.items = items;
        }

        static Result fail(String kind) {
            return new Result(false, kind == null ? "error" : kind, new ArrayList<Item>());
        }
    }

    private Search() {
    }

    public static Result parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!root.optBoolean("ok", false)) {
                return Result.fail(root.optString("kind", "error"));
            }
            List<Item> items = new ArrayList<>();
            JSONArray rows = root.optJSONArray("items");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    String url = row == null ? null : row.optString("url", "");
                    if (url == null || url.isEmpty()) {
                        continue;
                    }
                    items.add(new Item(row.optString("title", ""), url,
                            row.optInt("duration"), row.optString("uploader", ""),
                            row.optLong("views")));
                }
            }
            if (items.isEmpty()) {
                return Result.fail("unavailable");
            }
            return new Result(true, null, items);
        } catch (Exception e) {
            return Result.fail("error");
        }
    }
}
