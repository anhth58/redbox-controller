package com.redboxsa.controller;

import static java.lang.System.in;

import android.app.PendingIntent;
import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.redboxsa.controller.activities.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class DeviceOwnerReceiver extends DeviceAdminReceiver {
    public static final String ACTION_CUSTOM_ACTION = "com.example.ACTION_CUSTOM_ACTION";

    public static final String ACTION_INSTALL_COMPLETE = "com.redboxsa.controllerapp.INSTALL_COMPLETE";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d("Device owner enabled", "enable");

    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "Device owner disabled", Toast.LENGTH_SHORT).show();
        Log.d("Device owner disabled", "disabled");
    }



    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);
        Log.d("onProfile", "Complete");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        Log.d("DeviceOwnerReceiver", "onReceive");

        if(intent == null || intent.getAction() == null || intent.getAction().isEmpty()){
            return;
        }
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(intent.getAction()).build();
        Log.d("url", intent.getAction());
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Handle failure
                Log.d("download", "fail");
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                Log.d("download", "success");
                // Assuming you have permission to write to this directory
                File apkFile = new File(context.getExternalFilesDir(null), "downloaded_app.apk");
                try (BufferedSink sink = Okio.buffer(Okio.sink(apkFile))) {
                    sink.writeAll(response.body().source());
                } catch (Exception e) {
                    // Handle exceptions
                    Log.d("writeAll", e.toString());
                }
                Log.d("file", apkFile.getAbsolutePath());
                installPackage(context,apkFile.getAbsolutePath());
            }
        });
    }



    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void installPackage(Context context, String apkFilePath) {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);

        try {
            // Create a new session
            int sessionId = packageInstaller.createSession(params);

            // Open the session for writing
            PackageInstaller.Session session = packageInstaller.openSession(sessionId);
            OutputStream out = session.openWrite("package", 0, -1);

            // Copy APK file to session
            InputStream in = new FileInputStream(new File(apkFilePath));
            byte[] buffer = new byte[65536];
            int c;
            while ((c = in.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }

            // Close the session
            session.fsync(out);
            in.close();
            out.close();

            // Commit the session (start installation)
            Log.d("start","start install");
            session.commit(getStatusReceiver(context));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private IntentSender getStatusReceiver(Context context) {
        Intent intent = new Intent(context, PackageInstallerReceiver.class);
        intent.setAction("ACTION_SESSION_COMMITTED");
        return PendingIntent.getBroadcast(context, 0, intent, 0).getIntentSender();
    }

}