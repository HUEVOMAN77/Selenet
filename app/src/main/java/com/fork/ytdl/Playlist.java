package com.fork.ytdl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Lectura del JSON que devuelve backend.playlist(): los links de una lista de videos. */
public final class Playlist {

    public static final class Item {
        public final String title;
        public final String url;

        Item(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String title;
        /** Videos que tiene la lista, incluido lo que se corto por el techo del motor. */
        public final int total;
        /** Techo del motor; si total > max la lista entra recortada. */
        public final int max;
        public final List<Item> items;

        private Result(boolean ok, String title, int total, int max, List<Item> items) {
            this.ok = ok;
            this.title = title;
            this.total = total;
            this.max = max;
            this.items = items;
        }

        static Result fail() {
            return new Result(false, "", 0, 0, new ArrayList<Item>());
        }

        /** Verdadero si el motor tuvo que cortar la lista. */
        public boolean trimmed() {
            return total > max;
        }
    }

    private Playlist() {
    }

    public static Result parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!root.optBoolean("ok", false)) {
                return Result.fail();
            }
            List<Item> items = new ArrayList<>();
            JSONArray rows = root.optJSONArray("entries");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    String url = row == null ? null : row.optString("url", "");
                    if (url == null || url.isEmpty()) {
                        continue;
                    }
                    items.add(new Item(row.optString("title", ""), url));
                }
            }
            // sin miembros utilizables no hay nada que ofrecer, aunque total diga otro numero
            if (items.size() < 2) {
                return Result.fail();
            }
            return new Result(true, root.optString("title", ""),
                    root.optInt("total", items.size()), root.optInt("max", items.size()),
                    items);
        } catch (Exception e) {
            return Result.fail();
        }
    }
}
