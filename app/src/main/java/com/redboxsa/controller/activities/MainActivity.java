package com.redboxsa.controller.activities;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;

import com.redboxsa.controller.update.UpdateJobService;
import com.redboxsa.controller.update.Updater;

/**
 * Opened at boot and by the locker app. Has no UI: it makes sure the background job exists,
 * asks for a check right now, and gives the screen back to the locker.
 * On a new locker (no uuid yet) it first asks the technician for the phone permission used for the uuid.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_PHONE_STATE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Updater.needsPhonePermission(this)) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_PHONE_STATE);
        } else {
            start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // Granted or not: the uuid falls back to ANDROID_ID when refused.
        start();
    }

    private void start() {
        UpdateJobService.schedule(this);
        UpdateJobService.runNow(this);
        Updater.launchLocker(this);
        finish();
    }
}
