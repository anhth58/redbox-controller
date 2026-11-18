package com.redboxsa.controller.n62;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.redboxsa.controller.common.UrlCommon;

/**
 * Nexgo N62 payment terminal: a separate flow from the lockers (Android 6 / 11), merged from redbox-controller-v2.
 * On an N62 only this package runs: it keeps the payment app and the Nearpay plugin up to date.
 * The locker flow (update package) never runs on an N62, and this package never runs anywhere else.
 */
public final class N62 {

    static final String TAG = "ControllerN62";
    static final String PAYMENT_PACKAGE = "com.redboxsa.paymentapp";

    private N62() {
    }

    public static boolean isN62() {
        return "N62".equalsIgnoreCase(Build.MODEL);
    }

    /** The N62 terminals are registered by ANDROID_ID (shown on N62Activity). */
    static String uuid(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    static String server(Context context) {
        return UrlCommon.server(uuid(context), null);
    }

    /** Production terminals run the live Nearpay plugin, dev and stage the sandbox one. */
    static String nearpayPackage(String server) {
        return UrlCommon.PRODUCTION.equals(server) ? "io.nearpay.softpos" : "io.nearpay.nearpay.sandbox";
    }

    static void openPaymentApp(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(PAYMENT_PACKAGE);
        if (intent == null) {
            Log.w(TAG, "Payment app not installed, nothing to open");
            return;
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
