package com.redboxsa.controller.update;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Silent install/uninstall through PackageInstaller, the same path on both IPCs:
 * Android 6 = privileged system app holding INSTALL_PACKAGES, Android 11 = device owner.
 * Calls block until Android reports the result, so the caller can react (retry, open the locker).
 */
final class Installer {

    private static final String ACTION_RESULT = "com.redboxsa.controllerapp.PACKAGE_RESULT";
    private static final long RESULT_TIMEOUT_MINUTES = 5;

    private Installer() {
    }

    /** @return a {@link PackageInstaller} STATUS_* code. Installing this app itself never returns: Android kills the process. */
    static int install(Context context, File apk) throws IOException, InterruptedException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        int sessionId = installer.createSession(
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
                session.fsync(out);
            } catch (IOException e) {
                session.abandon();
                throw e;
            }
            return awaitResult(context, session::commit);
        }
    }

    /** @return a {@link PackageInstaller} STATUS_* code */
    static int uninstall(Context context, String packageName) throws InterruptedException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        return awaitResult(context, sender -> installer.uninstall(packageName, sender));
    }

    private interface Request {
        void start(IntentSender resultSender) throws IOException;
    }

    private static int awaitResult(Context context, Request request) throws InterruptedException {
        BlockingQueue<Intent> results = new ArrayBlockingQueue<>(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                results.offer(intent);
            }
        };
        context.registerReceiver(receiver, new IntentFilter(ACTION_RESULT));
        try {
            Intent callback = new Intent(ACTION_RESULT).setPackage(context.getPackageName());
            request.start(PendingIntent.getBroadcast(context, 0, callback, PendingIntent.FLAG_UPDATE_CURRENT).getIntentSender());
            Intent result = results.poll(RESULT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (result == null) {
                Log.w(Updater.TAG, "No result from PackageInstaller after " + RESULT_TIMEOUT_MINUTES + " min");
                return PackageInstaller.STATUS_FAILURE;
            }
            int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            // Never start the confirm dialog: no popup on the locker screen.
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Log.e(Updater.TAG, "Not allowed to install silently (not privileged / not device owner)");
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                Log.e(Updater.TAG, "PackageInstaller status " + status + ": " + result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
            return status;
        } catch (IOException e) {
            Log.e(Updater.TAG, "PackageInstaller request failed", e);
            return PackageInstaller.STATUS_FAILURE;
        } finally {
            context.unregisterReceiver(receiver);
        }
    }
}
