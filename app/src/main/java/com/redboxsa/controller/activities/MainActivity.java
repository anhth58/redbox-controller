package com.redboxsa.controller.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.redboxsa.controller.n62.N62;
import com.redboxsa.controller.n62.N62Activity;
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
        if (N62.isN62()) {
            // Payment terminal: its own screen and flow (n62 package); nothing below runs there.
            startActivity(new Intent(this, N62Activity.class));
            finish();
            return;
        }
        UpdateJobService.schedule(this);
        UpdateJobService.runNow(this);
        Updater.launchLocker(this);
        finish();
    }
}
