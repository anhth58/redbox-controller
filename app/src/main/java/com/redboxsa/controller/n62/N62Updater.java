package com.redboxsa.controller.n62;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.redboxsa.controller.update.Http;
import com.redboxsa.controller.update.Installer;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The N62 check, ported from redbox-controller-v2 (HourlyWorker):
 * POST /api/near-pay-version-nexgo?uuid=ANDROID_ID → {"state", "version", "link", "version_nearpay", "link_nearpay"};
 * the payment app and the Nearpay plugin are installed silently (device owner) when missing or older.
 * A link may point to an apk or to a zip holding one.
 */
final class N62Updater {

    private static final String API = "/api/near-pay-version-nexgo";
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private N62Updater() {
    }

    static void runAsync(Context context, Runnable done) {
        if (!BUSY.compareAndSet(false, true)) {
            done.run();
            return;
        }
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                runOnce(app);
            } catch (Throwable t) {
                Log.e(N62.TAG, "Check failed: " + t, t); // the message too: Android hides UnknownHostException traces
            } finally {
                BUSY.set(false);
                done.run();
            }
        });
    }

    private static void runOnce(Context context) throws Exception {
        String server = N62.server(context);
        String url = server + API + "?uuid=" + Uri.encode(N62.uuid(context));
        Log.i(N62.TAG, "Check: " + url);
        JSONObject json = Http.postJson(context, url, null, new JSONObject());
        if (!json.optBoolean("state", false)) {
            Log.i(N62.TAG, "Nothing to update: " + json.optString("msg"));
            return;
        }
        // Each app on its own: a failure in one never skips the other.
        try {
            update(context, N62.PAYMENT_PACKAGE, json.optInt("version", -1), json.optString("link", ""));
        } catch (Exception e) {
            Log.e(N62.TAG, "Update failed for " + N62.PAYMENT_PACKAGE, e);
        }
        String nearpay = N62.nearpayPackage(server);
        try {
            update(context, nearpay, json.optInt("version_nearpay", -1), json.optString("link_nearpay", ""));
        } catch (Exception e) {
            Log.e(N62.TAG, "Update failed for " + nearpay, e);
        }
    }

    private static void update(Context context, String packageName, int remoteVersion, String link)
            throws IOException, InterruptedException {
        if (remoteVersion <= 0 || link.isEmpty()) return;
        int installed = installedVersion(context, packageName);
        Log.i(N62.TAG, packageName + " installed " + installed + ", server " + remoteVersion);
        if (installed >= remoteVersion) return;
        // Same SIM-data rule as the lockers: one fetch of the same file per day, every 2 h while the app is missing.
        if (Http.fetchedRecently(context, link, installed < 0 ? 2 * 60 * 60 * 1000L : Http.REFETCH_AFTER_MS)) {
            Log.i(N62.TAG, packageName + " already fetched recently, not again: " + link);
            return;
        }

        // One file per app: a resumed download must not be dropped by the other app's download.
        File download = new File(context.getFilesDir(), "n62-" + packageName + ".download");
        File extracted = new File(context.getFilesDir(), "n62-extracted.apk");
        try {
            Http.download(context, link, download);
            Http.markFetched(context, link);
            File apk = isApk(download) ? download : extractApk(download, extracted);
            PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
            if (info == null || !packageName.equals(info.packageName)) {
                throw new IOException("Expected " + packageName + " but the download is " + (info == null ? "not an apk" : info.packageName));
            }
            // The server number and the apk may disagree: never reinstall the same version every hour.
            if (info.versionCode <= installed) {
                Log.w(N62.TAG, "Server offers " + remoteVersion + " but the apk is versionCode " + info.versionCode);
                return;
            }
            int status = Installer.install(context, apk);
            Log.i(N62.TAG, packageName + " install status " + status);
            if (status == PackageInstaller.STATUS_SUCCESS && N62.PAYMENT_PACKAGE.equals(packageName)) {
                N62.openPaymentApp(context);
            }
        } finally {
            download.delete();
            extracted.delete();
        }
    }

    /** @return the installed versionCode, or -1 when not installed */
    @SuppressWarnings("deprecation")
    private static int installedVersion(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /** An apk is itself a zip: it has AndroidManifest.xml at its root. */
    private static boolean isApk(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            return zip.getEntry("AndroidManifest.xml") != null;
        }
    }

    private static File extractApk(File zipFile, File target) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".apk")) continue;
                try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
                }
                return target;
            }
        }
        throw new IOException("No apk inside the zip");
    }
}
