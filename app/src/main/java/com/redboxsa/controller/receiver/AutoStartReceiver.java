package com.redboxsa.controller.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.redboxsa.controller.BuildConfig;
import com.redboxsa.controller.activities.MainActivity;
import com.redboxsa.controller.n62.N62;
import com.redboxsa.controller.n62.N62JobService;
import com.redboxsa.controller.update.EventLog;
import com.redboxsa.controller.update.UpdateJobService;

public class AutoStartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (N62.isN62()) {
            // Payment terminal (boot or update): only the N62 check, no locker flow, no UI.
            N62JobService.schedule(context);
            N62JobService.runNow(context);
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Same boot path as the old controllers: MainActivity schedules the job and opens the locker.
            context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            // Self-update killed this process; the locker stays in front, only the background work restarts.
            EventLog.add(context, "CONTROLLER_UPDATED", "now " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
            UpdateJobService.schedule(context);
            UpdateJobService.runNow(context);
        }
    }
}
