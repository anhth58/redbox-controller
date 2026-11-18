package com.redboxsa.controller.update;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import io.sigpipe.jbsdiff.Patch;
import okio.BufferedSource;
import okio.Okio;

/**
 * Delta update: rebuilds the new apk from the installed one and a bsdiff patch listed by the version API:
 * "apk_sha256", "apk_size", "patches": [{"from_sha256", "patch_url", "patch_sha256", "patch_size"}].
 * Any problem means "no patch": the caller downloads the full apk_url exactly as before.
 */
final class Delta {

    static final String PATCH_FILE = "update.patch";

    private Delta() {
    }

    /** @return true when {@code target} holds the new apk with the sha256 the server announced */
    static boolean build(Context context, JSONObject data, String packageName, File target) {
        JSONArray patches = data.optJSONArray("patches");
        String apkSha = data.optString("apk_sha256", "");
        if (patches == null || patches.length() == 0 || apkSha.isEmpty()) return false;

        File patch = new File(context.getFilesDir(), PATCH_FILE);
        try {
            ApplicationInfo app = context.getPackageManager().getApplicationInfo(packageName, 0);
            // Patches are made against base.apk only.
            if (app.splitSourceDirs != null && app.splitSourceDirs.length > 0) return false;
            File installed = new File(app.sourceDir);
            String installedSha = Updater.hex(Updater.sha256(installed));
            JSONObject match = null;
            for (int i = 0; i < patches.length() && match == null; i++) {
                JSONObject p = patches.optJSONObject(i);
                if (p != null && installedSha.equalsIgnoreCase(p.optString("from_sha256"))) match = p;
            }
            if (match == null) return false;
            long needed = 2 * data.optLong("apk_size", installed.length());
            if (context.getFilesDir().getUsableSpace() < needed) throw new IOException("not enough free storage");

            Http.download(context, match.getString("patch_url"), patch);
            if (!Updater.hex(Updater.sha256(patch)).equalsIgnoreCase(match.optString("patch_sha256"))) {
                throw new IOException("patch sha256 mismatch");
            }
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                Patch.patch(readAll(installed), readAll(patch), out);
            }
            if (!Updater.hex(Updater.sha256(target)).equalsIgnoreCase(apkSha)) {
                throw new IOException("rebuilt apk sha256 mismatch");
            }
            Log.i(Updater.TAG, packageName + ": rebuilt from a " + patch.length() + " byte patch");
            return true;
        } catch (Throwable t) {
            // Includes OutOfMemoryError while patching: the full download still works.
            target.delete();
            EventLog.add(context, "PATCH_FAILED", packageName + ": " + t + "; downloading the full apk");
            return false;
        } finally {
            patch.delete();
        }
    }

    private static byte[] readAll(File file) throws IOException {
        try (BufferedSource source = Okio.buffer(Okio.source(file))) {
            return source.readByteArray();
        }
    }
}
