package com.fork.ytdl;

import android.content.Context;

import org.json.JSONObject;

import java.util.UUID;

public class Task {

    public static final String QUEUED = "queued";
    public static final String RUNNING = "running";
    public static final String PAUSED = "paused";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    public static final int MAX_RETRIES = 3;

    public final String id;
    public final String url;
    public String title;
    public String fmt;
    public String label;
    public String state = QUEUED;
    public int percent;
    public String speed = "";
    public String eta = "";
    public String path;
    public String tmp;
    public String errorKind;
    public String errorDetail;
    public int attempts;
    public long bytes;
    public long finishedAt;

    public Task(String url, String fmt) {
        this.id = UUID.randomUUID().toString();
        this.url = url;
        this.fmt = fmt;
    }

    public Task(JSONObject json) {
        this.id = json.optString("id", UUID.randomUUID().toString());
        this.url = json.optString("url", "");
        this.title = json.optString("title", null);
        this.fmt = json.optString("fmt", "best");
        this.label = json.optString("label", null);
        this.state = json.optString("state", QUEUED);
        this.path = json.optString("path", null);
        this.tmp = json.optString("tmp", null);
        this.attempts = json.optInt("attempts", 0);
        this.errorKind = json.optString("errorKind", null);
        this.errorDetail = json.optString("errorDetail", null);
        this.bytes = json.optLong("bytes", 0);
        this.finishedAt = json.optLong("finishedAt", 0);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id).put("url", url).put("fmt", fmt).put("state", state)
                    .put("attempts", attempts).put("bytes", bytes);
            if (title != null) json.put("title", title);
            if (label != null) json.put("label", label);
            if (path != null) json.put("path", path);
            if (tmp != null) json.put("tmp", tmp);
            if (errorKind != null) json.put("errorKind", errorKind);
            if (errorDetail != null) json.put("errorDetail", errorDetail);
            if (finishedAt > 0) json.put("finishedAt", finishedAt);
        } catch (Exception ignored) {
        }
        return json;
    }

    /** Como se muestra la calidad elegida; si no hay etiqueta, la que corresponda al spec. */
    public String qualityLabel(Context context) {
        if (label != null && !label.isEmpty()) {
            return label;
        }
        return Quality.label(context, fmt);
    }

    public boolean isLive() {
        return QUEUED.equals(state) || RUNNING.equals(state) || PAUSED.equals(state);
    }

    public String displayName() {
        if (title != null && !title.isEmpty()) {
            return title;
        }
        return url;
    }
}
