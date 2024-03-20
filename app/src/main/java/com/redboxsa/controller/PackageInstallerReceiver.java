package com.redboxsa.controller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

public class PackageInstallerReceiver extends BroadcastReceiver {

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceive(Context context, Intent intent) {
//        String action = intent.getAction();
//        if (action != null && action.equals("ACTION_SESSION_COMMITTED")) {
//            int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
//            if (sessionId != -1) {
//                PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
//                PackageInstaller.SessionInfo sessionInfo = packageInstaller.getSessionInfo(sessionId);
//                if (sessionInfo != null) {
//                    Log.d("PackageInstallReceiver", "Installation progress: " + sessionInfo.getProgress());
//                }
//            }
//        }
        Log.d("install","done");
        startApp(context);
    }

    private void startApp(Context context) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.redbox.locker");
        if (launchIntent != null) {
            context.startActivity(launchIntent);//null pointer check in case package name was not found
        }
    }
}
