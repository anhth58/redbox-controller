package com.redboxsa.controller.activities;

import android.app.Activity;
import android.os.Bundle;

import com.redboxsa.controller.update.UpdateJobService;
import com.redboxsa.controller.update.Updater;

/**
 * Opened at boot and by the locker app. Has no UI and never shows a popup: it makes sure the background job exists,
 * asks for a check right now, and gives the screen back to the locker.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UpdateJobService.schedule(this);
        UpdateJobService.runNow(this);
        Updater.launchLocker(this);
        finish();
    }
}
