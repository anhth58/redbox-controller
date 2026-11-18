package com.redboxsa.controller.n62;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import com.redboxsa.controller.R;

/**
 * N62 screen (from redbox-controller-v2): the uuid for registering the terminal, the device owner state,
 * then the payment app after 15 seconds. Also makes sure the hourly check is scheduled.
 */
public class N62Activity extends Activity {

    private static final long OPEN_PAYMENT_APP_AFTER_MS = 15_000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_n62);

        String uuid = N62.uuid(this);
        Log.i(N62.TAG, "uuid " + uuid);
        ((TextView) findViewById(R.id.uuid)).setText("UUID: " + uuid);
        boolean deviceOwner = getSystemService(DevicePolicyManager.class).isDeviceOwnerApp(getPackageName());
        ((TextView) findViewById(R.id.deviceOwnerState))
                .setText(deviceOwner ? "Device owner is active" : "Device owner is not active");

        N62JobService.schedule(this);
        N62JobService.runNow(this);

        Context app = getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> N62.openPaymentApp(app), OPEN_PAYMENT_APP_AFTER_MS);
    }
}
