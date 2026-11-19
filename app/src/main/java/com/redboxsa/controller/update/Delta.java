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
 * A bad or missing patch means "no patch": the caller downloads the full apk_url exactly as before.
 */
final class Delta {

    private Delta() {
    }

    /** One per package: the controller and locker patches must not drop each other's partial bytes. */
    static String patchFile(String packageName) {
        return packageName + ".patch";
    }

    /**
     * @return true when {@code target} holds the new apk with the sha256 the server announced
     * @throws IOException the patch download broke (weak network): it resumes next cycle, and the full apk is not
     *                     started now, which would cost far more data
     */
    static boolean build(Context context, JSONObject data, String packageName, File target) throws IOException {
        File installed = installedApk(context, packageName);
        JSONObject match = installed == null ? null : matchingPatch(context, data, packageName, installed);
        if (match == null) return false;

        File patch = new File(context.getFilesDir(), patchFile(packageName));
        try {
            Http.download(context, match.optString("patch_url"), patch);
        } catch (Http.StatusException | IllegalArgumentException e) { // refused by the server, or a malformed patch_url
            EventLog.add(context, "PATCH_FAILED", packageName + ": " + e + "; downloading the full apk");
            return false;
        }
        try {
            if (!Updater.hex(Updater.sha256(patch)).equalsIgnoreCase(match.optString("patch_sha256"))) {
                throw new IOException("patch sha256 mismatch");
            }
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                Patch.patch(readAll(installed), readAll(patch), out);
            }
            if (!Updater.hex(Updater.sha256(target)).equalsIgnoreCase(data.optString("apk_sha256"))) {
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

    /** @return the installed base.apk, or null when it cannot be patched (not installed, split apks) */
    private static File installedApk(Context context, String packageName) {
        try {
            ApplicationInfo app = context.getPackageManager().getApplicationInfo(packageName, 0);
            // Patches are made against base.apk only.
            return app.splitSourceDirs != null && app.splitSourceDirs.length > 0 ? null : new File(app.sourceDir);
        } catch (Exception e) {
            return null;
        }
    }

    /** @return the patch made from the installed apk, or null: then the full apk is downloaded */
    private static JSONObject matchingPatch(Context context, JSONObject data, String packageName, File installed) {
        JSONArray patches = data.optJSONArray("patches");
        if (patches == null || patches.length() == 0 || data.optString("apk_sha256", "").isEmpty()) return null;
        try {
            String installedSha = Updater.hex(Updater.sha256(installed));
            for (int i = 0; i < patches.length(); i++) {
                JSONObject p = patches.optJSONObject(i);
                if (p == null || p.optString("patch_url").isEmpty()
                        || !installedSha.equalsIgnoreCase(p.optString("from_sha256"))) continue;
                if (context.getFilesDir().getUsableSpace() < 2 * data.optLong("apk_size", installed.length())) {
                    throw new IOException("not enough free storage");
                }
                return p;
            }
            return null;
        } catch (Exception e) {
            EventLog.add(context, "PATCH_FAILED", packageName + ": " + e + "; downloading the full apk");
            return null;
        }
    }

    private static byte[] readAll(File file) throws IOException {
        try (BufferedSource source = Okio.buffer(Okio.source(file))) {
            return source.readByteArray();
        }
    }
}
