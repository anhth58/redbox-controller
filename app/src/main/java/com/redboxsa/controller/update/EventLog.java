package com.redboxsa.controller.update;

import android.content.Context;
import android.util.Log;

import com.redboxsa.controller.BuildConfig;
import com.redboxsa.controller.common.UrlCommon;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Events worth explaining later (updates, restores, time changes and why they failed), kept on disk
 * and uploaded to the server with the locker token. One JSON object per line:
 * {"time": epoch ms, "type": "LOCKER_UPDATE", "message": "...", "controller_version": 8}
 */
public final class EventLog {

    /** Oldest lines are dropped beyond this, e.g. while there is no token or no network. */
    private static final int MAX_LINES = 500;

    private EventLog() {
    }

    public static synchronized void add(Context context, String type, String message) {
        Log.i(Updater.TAG, type + ": " + message);
        try {
            List<String> lines = read(context);
            // The same failure every 30 minutes is logged once until something else happens.
            if (!lines.isEmpty() && isSame(lines.get(lines.size() - 1), type, message)) return;
            lines.add(new JSONObject()
                    .put("time", System.currentTimeMillis())
                    .put("type", type)
                    .put("message", message)
                    .put("controller_version", BuildConfig.VERSION_CODE)
                    .toString());
            write(context, lines.subList(Math.max(0, lines.size() - MAX_LINES), lines.size()));
        } catch (IOException | JSONException e) {
            Log.e(Updater.TAG, "Cannot write event log", e);
        }
    }

    /** Sends everything logged so far; removes it only once the server accepted it. */
    static void upload(Context context, String uuid, String token) throws IOException, JSONException {
        List<String> sent;
        synchronized (EventLog.class) {
            sent = read(context);
        }
        if (sent.isEmpty() || token == null) return;

        JSONArray logs = new JSONArray();
        for (String line : sent) {
            try {
                logs.put(new JSONObject(line));
            } catch (JSONException e) {
                // A line cut by a power loss: drop it rather than block every later upload.
            }
        }
        JSONObject response = Http.postJson(context, UrlCommon.CONTROLLER_LOGS, token, new JSONObject()
                .put("uuid", uuid)
                .put("controller_version", BuildConfig.VERSION_CODE)
                .put("logs", logs));
        if (!response.optBoolean("state", true)) throw new IOException("Server refused the logs: " + response);

        synchronized (EventLog.class) {
            List<String> lines = read(context);
            // Keep what was added during the upload.
            write(context, lines.subList(Math.min(sent.size(), lines.size()), lines.size()));
        }
    }

    private static boolean isSame(String line, String type, String message) {
        try {
            JSONObject last = new JSONObject(line);
            return type.equals(last.optString("type")) && message.equals(last.optString("message"));
        } catch (JSONException e) {
            return false;
        }
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), "events.log");
    }

    private static List<String> read(Context context) throws IOException {
        List<String> lines = new ArrayList<>();
        File file = file(context);
        if (!file.exists()) return lines;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (!line.isEmpty()) lines.add(line);
            }
        }
        return lines;
    }

    private static void write(Context context, List<String> lines) throws IOException {
        try (Writer writer = new FileWriter(file(context))) {
            for (String line : lines) writer.write(line + "\n");
        }
    }
}
