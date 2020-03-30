package com.redboxsa.controller.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.redboxsa.controller.activities.MainActivity;
import com.redboxsa.controller.service.MyUpdateService;

public class AutoStartReceiver extends BroadcastReceiver
{
    public void onReceive(Context context, Intent arg1)
    {
        // Construct our Intent specifying the Service
        // Start the service
        if (Intent.ACTION_BOOT_COMPLETED.equals(arg1.getAction())) {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            Log.i("Autostart", "started");
        }
    }
}
