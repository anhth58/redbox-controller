package com.redboxsa.controller.update;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.redboxsa.controller.BuildConfig;
import com.redboxsa.controller.common.UrlCommon;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okio.BufferedSink;
import okio.Okio;

/**
 * One update cycle, run by {@link UpdateJobService} every 30 minutes:
 * 1. locker missing (e.g. a failed earlier update) -> reinstall it from the last verified apk, no network needed;
 * 2. update this controller when the server offers a newer one;
 * 3. update / install the locker app.
 * Cycles run one at a time on a single thread, so two installs never overlap.
 */
public final class Updater {

    public static final String TAG = "Controller";
    static final String LOCKER_PACKAGE = "com.redbox.locker";

    private static final int REINSTALL_ATTEMPTS = 3;
    private static final long REINSTALL_PAUSE_MS = 5_000;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private Updater() {
    }

    /** Starts a cycle unless one is already running; {@code done} runs when it ends either way. */
    static void runAsync(Context context, Runnable done) {
        Context app = context.getApplicationContext();
        if (!BUSY.compareAndSet(false, true)) {
            Log.i(TAG, "Check already running");
            done.run();
            return;
        }
        WORKER.execute(() -> {
            try {
                runOnce(app);
            } catch (Throwable t) {
                Log.e(TAG, "Update cycle failed", t);
            } finally {
                BUSY.set(false);
                done.run();
            }
        });
    }

    public static void launchLocker(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(LOCKER_PACKAGE);
        if (intent == null) {
            Log.w(TAG, "Locker not installed, nothing to open");
            return;
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static void runOnce(Context context) throws InterruptedException {
        // Leftovers of a cycle killed mid-way (reboot, power loss). Never locker.apk / locker-backup.apk: recovery copies.
        for (String name : new String[]{Delta.PATCH_FILE, "controller.apk", "locker-download.apk"}) {
            new File(context.getFilesDir(), name).delete();
            new File(context.getFilesDir(), name + ".part").delete();
        }
        File savedLocker = new File(context.getFilesDir(), "locker.apk");
        if (!isInstalled(context, LOCKER_PACKAGE) && savedLocker.exists()) {
            EventLog.add(context, "LOCKER_MISSING", "reinstalling the last locker that installed fine");
            reinstallLocker(context, savedLocker);
        }

        String uuid = deviceId(context);
        Log.i(TAG, "Check: controller " + BuildConfig.VERSION_CODE + ", uuid " + uuid);
        String error = null;
        // Each step catches everything: a failure in one never skips the locker update.
        // New features go after updateLocker(), in their own try/catch.
        try {
            updateSelf(context, uuid);
        } catch (Exception e) {
            Log.e(TAG, "Controller update check failed", e);
            error = "controller: " + e;
        }
        try {
            updateLocker(context, uuid, savedLocker);
        } catch (Exception e) {
            Log.e(TAG, "Locker update check failed", e);
            error = (error == null ? "" : error + "; ") + "locker: " + e;
        }
        try {
            TimeSync.run(context);
        } catch (Exception e) {
            Log.e(TAG, "Time sync failed", e);
        }
        State.setLastCheck(context, error);
        try {
            EventLog.upload(context, uuid, State.token(context));
        } catch (Exception e) {
            Log.w(TAG, "Event log upload failed, kept for next cycle: " + e);
        }
    }

    /**
     * Token API first (locker token); the old token-less API when the locker has not handed over a token yet,
     * or the token API fails or is not deployed yet.
     */
    private static JSONObject versionApi(Context context, String tokenUrl, String oldUrl, String query) throws IOException, JSONException {
        String token = State.token(context);
        if (token != null) {
            try {
                JSONObject response = Http.getJson(context, tokenUrl + query, token);
                if (response.optBoolean("state")) return response;
                Log.w(TAG, "Token API answered state=false, using the old API");
            } catch (IOException | JSONException e) {
                Log.w(TAG, "Token API failed, using the old API: " + e);
            }
        }
        return Http.getJson(context, oldUrl + query, null);
    }

    private static void updateSelf(Context context, String uuid) throws IOException, JSONException, InterruptedException {
        JSONObject data = data(versionApi(context, UrlCommon.CONTROLLER_VERSION, UrlCommon.CHECK_FOR_SELF_UPDATE,
                "?current_version=" + BuildConfig.VERSION_CODE + "&uuid=" + Uri.encode(uuid)));
        if (data == null) return;
        State.setControllerLatest(context, data.optInt("new_version"));
        State.setServerTimeZone(context, data.optString("timezone", null));
        if (data.optInt("new_version") <= BuildConfig.VERSION_CODE || !approved(data)) return;

        File apk = new File(context.getFilesDir(), "controller.apk");
        try {
            if (!Delta.build(context, data, context.getPackageName(), apk)) Http.download(context, data.getString("apk_url"), apk);
            PackageInfo info = verify(context, apk, context.getPackageName());
            // Guards against an endless download/install loop when the server number and the apk disagree.
            if (info.versionCode <= BuildConfig.VERSION_CODE) {
                EventLog.add(context, "CONTROLLER_UPDATE_SKIPPED", "server offers " + data.optInt("new_version")
                        + " but the apk is versionCode " + info.versionCode);
                return;
            }
            EventLog.add(context, "CONTROLLER_UPDATE", BuildConfig.VERSION_CODE + " -> " + info.versionCode);
            // On success Android kills this process here; AutoStartReceiver (MY_PACKAGE_REPLACED) restarts the job.
            Installer.install(context, apk);
            EventLog.add(context, "CONTROLLER_UPDATE_FAILED", "install refused, see INSTALL_FAILED");
        } catch (IOException e) {
            EventLog.add(context, "CONTROLLER_UPDATE_FAILED", String.valueOf(e.getMessage()));
            throw e;
        } finally {
            apk.delete();
        }
    }

    @SuppressWarnings("deprecation")
    private static void updateLocker(Context context, String uuid, File savedLocker) throws IOException, JSONException, InterruptedException {
        JSONObject data = data(versionApi(context, UrlCommon.LOCKER_VERSION, UrlCommon.CHECK_FOR_UPDATE,
                "?uuid=" + Uri.encode(uuid)));
        if (data == null) return;
        State.setServerTimeZone(context, data.optString("timezone", null));

        PackageInfo installed = installedInfo(context);
        boolean newVersion = data.optInt("current_version") != data.optInt("new_version") && approved(data);
        if (installed == null || newVersion) {
            File download = new File(context.getFilesDir(), "locker-download.apk");
            try {
                if (installed == null || !Delta.build(context, data, LOCKER_PACKAGE, download)) {
                    Http.download(context, data.getString("apk_url"), download);
                }
                PackageInfo apk = verify(context, download, LOCKER_PACKAGE);
                if (installed != null && sameAsInstalled(context, download)) {
                    // Server still reports an old version: reinstalling would only kill the running locker.
                    EventLog.add(context, "LOCKER_UPDATE_SKIPPED", "apk " + apk.versionName + " is already installed");
                } else {
                    EventLog.add(context, "LOCKER_UPDATE", (installed == null ? "not installed" : versionOf(installed))
                            + " -> " + versionOf(apk));
                    if (installLocker(context, download, apk) && !download.renameTo(savedLocker)) {
                        // Only an apk that installed fine on this device becomes the recovery copy.
                        Log.w(TAG, "Cannot save " + savedLocker);
                    }
                }
            } catch (IOException e) {
                EventLog.add(context, "LOCKER_UPDATE_FAILED", String.valueOf(e.getMessage()));
                throw e;
            } finally {
                download.delete();
            }
        }
        if (data.optBoolean("start_app")) launchLocker(context);
    }

    @SuppressWarnings("deprecation")
    private static String versionOf(PackageInfo info) {
        return info.versionName + " (" + info.versionCode + ")";
    }

    /** Recovery path: the locker is missing, so a plain install is all it takes. */
    private static void reinstallLocker(Context context, File apk) throws InterruptedException {
        if (installWithRetries(context, apk)) {
            EventLog.add(context, "LOCKER_REINSTALLED", "from the saved apk");
            launchLocker(context);
        } else {
            EventLog.add(context, "LOCKER_STILL_MISSING", "retrying from the saved apk next cycle");
        }
    }

    /**
     * Replaces the locker in place. Removes it first only for a real downgrade (Android refuses an older versionCode),
     * after copying the running apk aside so the locker can always be put back if the new one does not install.
     */
    @SuppressWarnings("deprecation")
    private static boolean installLocker(Context context, File apk, PackageInfo apkInfo) throws InterruptedException, IOException {
        int status = install(context, apk);
        PackageInfo current = installedInfo(context);
        if (status != PackageInstaller.STATUS_SUCCESS && current != null && apkInfo.versionCode < current.versionCode) {
            File backup = new File(context.getFilesDir(), "locker-backup.apk");
            try {
                copy(new File(current.applicationInfo.sourceDir), backup);
                EventLog.add(context, "LOCKER_DOWNGRADE", versionOf(current) + " -> " + versionOf(apkInfo) + ": uninstalling first");
                Installer.uninstall(context, LOCKER_PACKAGE);
                if (installWithRetries(context, apk)) {
                    status = PackageInstaller.STATUS_SUCCESS;
                } else if (installWithRetries(context, backup)) {
                    EventLog.add(context, "LOCKER_RESTORED", "new locker would not install; " + versionOf(current) + " put back");
                } else {
                    EventLog.add(context, "LOCKER_STILL_MISSING", "could not restore " + versionOf(current) + "; retrying next cycle");
                }
            } finally {
                backup.delete();
            }
        }
        if (status == PackageInstaller.STATUS_SUCCESS) EventLog.add(context, "LOCKER_INSTALLED", versionOf(apkInfo));
        // Installing replaces the running locker (or brings back the old one): put it on screen again.
        launchLocker(context);
        return status == PackageInstaller.STATUS_SUCCESS;
    }

    private static boolean installWithRetries(Context context, File apk) throws InterruptedException {
        for (int attempt = 1; attempt <= REINSTALL_ATTEMPTS; attempt++) {
            if (attempt > 1) Thread.sleep(REINSTALL_PAUSE_MS);
            if (install(context, apk) == PackageInstaller.STATUS_SUCCESS) return true;
        }
        return false;
    }

    private static PackageInfo installedInfo(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(LOCKER_PACKAGE, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static void copy(File from, File to) throws IOException {
        try (BufferedSink sink = Okio.buffer(Okio.sink(to))) {
            sink.writeAll(Okio.source(from));
        }
    }

    private static int install(Context context, File apk) throws InterruptedException {
        try {
            return Installer.install(context, apk);
        } catch (IOException e) {
            Log.e(TAG, "Install failed: " + apk, e);
            return PackageInstaller.STATUS_FAILURE;
        }
    }

    /** Rejects truncated files and foreign apks before anything is installed or removed. */
    @SuppressWarnings("deprecation")
    private static PackageInfo verify(Context context, File apk, String expectedPackage) throws IOException {
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
        if (archive == null) throw new IOException("Not a valid apk: " + apk);
        if (!expectedPackage.equals(archive.packageName)) {
            throw new IOException("Expected " + expectedPackage + " but apk is " + archive.packageName);
        }
        // Locker and controller share one signing key, so this app's own signature is the reference.
        PackageInfo self;
        try {
            self = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
        if (!Arrays.equals(self.signatures, archive.signatures)) {
            throw new IOException("Signing key of " + archive.packageName + " apk does not match");
        }
        return archive;
    }

    private static boolean sameAsInstalled(Context context, File apk) {
        try {
            File installed = new File(context.getPackageManager().getApplicationInfo(LOCKER_PACKAGE, 0).sourceDir);
            return Arrays.equals(sha256(installed), sha256(apk));
        } catch (Exception e) {
            return false;
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(String.format("%02x", b));
        return s.toString();
    }

    static byte[] sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) != -1; ) digest.update(buffer, 0, n);
        }
        return digest.digest();
    }

    private static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static JSONObject data(JSONObject response) {
        return response.optBoolean("state") ? response.optJSONObject("data") : null;
    }

    private static boolean approved(JSONObject data) {
        return data.optBoolean("auto_update") || data.optBoolean("approved_apk");
    }

    /** True only on a new locker: no uuid saved yet and the phone permission (IMEI) not granted. */
    public static boolean needsPhonePermission(Context context) {
        return savedDeviceId(context) == null
                && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED;
    }

    private static SharedPreferences prefs(Context context) {
        // Same file and key as the old controllers, so the server keeps recognising every locker.
        return context.getSharedPreferences("FILE", Context.MODE_PRIVATE);
    }

    private static String savedDeviceId(Context context) {
        String id = prefs(context).getString("uuid", null);
        return id == null || id.isEmpty() ? null : id;
    }

    @SuppressLint({"HardwareIds", "MissingPermission"})
    static String deviceId(Context context) {
        String id = savedDeviceId(context);
        if (id != null) return id;
        // Requested by MainActivity on a new locker; ANDROID_ID when refused or without telephony.
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                id = context.getSystemService(TelephonyManager.class).getDeviceId();
            } catch (RuntimeException ignored) {
                // No telephony or no access to identifiers: fall back to ANDROID_ID.
            }
        }
        if (id == null || id.isEmpty()) {
            id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        prefs(context).edit().putString("uuid", id).apply();
        return id;
    }
}
