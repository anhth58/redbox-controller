package com.redboxsa.controller;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.redboxsa.controller.update.Updater;

import org.conscrypt.Conscrypt;

import java.security.Security;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Android 6 platform TLS stops at 1.2; Conscrypt adds 1.3. Android 10+ has 1.3 built in.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1);
            } catch (Throwable t) {
                Log.w(Updater.TAG, "Conscrypt unavailable, using platform TLS", t);
            }
        }
    }
}
