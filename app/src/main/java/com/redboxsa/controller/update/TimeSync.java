package com.redboxsa.controller.update;

import android.Manifest;
import android.app.AlarmManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.redboxsa.controller.DeviceOwnerReceiver;

import java.util.Arrays;
import java.util.TimeZone;

/**
 * Keeps the IPC clock and time zone right, every cycle (30 minutes).
 * Exact time: the locker app (it gets it from the server every 5 minutes), else the Date header of our own API calls.
 * Time zone: the locker app, else the "timezone" field of the token API.
 * Android 11: set as device owner. Android 6: set as privileged system app (SET_TIME / SET_TIME_ZONE).
 */
final class TimeSync {

    /** Smaller drifts are left alone: not worth fighting the locker's own time handling or NTP. */
    private static final long MAX_DRIFT_MS = 60 * 1000L;
    /** 2025-01-01: anything earlier is a broken value (e.g. seconds sent instead of ms), never a clock to set. */
    private static final long MIN_VALID_TIME_MS = 1735689600000L;

    private TimeSync() {
    }

    static void run(Context context) {
        long lockerNow = State.lockerNow(context);
        long serverNow = Http.serverNow();
        long exactNow = lockerNow != 0 ? lockerNow : serverNow;
        String source = lockerNow != 0 ? "locker" : "server";
        if (lockerNow != 0 && serverNow != 0 && Math.abs(lockerNow - serverNow) > MAX_DRIFT_MS) {
            // A bug on either side must never move the clock: leave it alone until both agree again.
            EventLog.add(context, "TIME_SET_SKIPPED", "locker and server time differ by " + (lockerNow - serverNow) / 1000 + "s");
        } else if (exactNow < MIN_VALID_TIME_MS) {
            Log.i(Updater.TAG, "No exact time available yet");
        } else {
            long drift = System.currentTimeMillis() - exactNow;
            if (Math.abs(drift) > MAX_DRIFT_MS) {
                boolean ok = setTime(context, exactNow);
                EventLog.add(context, ok ? "TIME_SET" : "TIME_SET_FAILED",
                        "drift " + drift / 1000 + "s, source " + source + (ok ? "" : ", no permission to set time"));
            }
        }

        String zone = State.lockerTimeZone(context);
        if (zone == null) zone = State.serverTimeZone(context);
        String current = TimeZone.getDefault().getID();
        if (zone != null && !zone.equals(current)) {
            if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(zone)) {
                EventLog.add(context, "TIMEZONE_SET_FAILED", "unknown time zone " + zone);
            } else {
                // Read before setting: AlarmManager.setTimeZone also changes this process's default zone.
                boolean ok = setTimeZone(context, zone);
                EventLog.add(context, ok ? "TIMEZONE_SET" : "TIMEZONE_SET_FAILED",
                        current + " -> " + zone + (ok ? "" : ", no permission to set time zone"));
            }
        }
    }

    static boolean canSetTime(Context context) {
        return deviceOwnerCanSetTime(context)
                || context.checkSelfPermission(Manifest.permission.SET_TIME) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean deviceOwnerCanSetTime(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && State.isDeviceOwner(context);
    }

    @SuppressWarnings("deprecation")
    private static boolean setTime(Context context, long millis) {
        if (deviceOwnerCanSetTime(context)) {
            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            ComponentName admin = new ComponentName(context, DeviceOwnerReceiver.class);
            // The time is wrong although automatic time may be on (no NTP on the IPC): we take over; setTime refuses otherwise.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setAutoTimeEnabled(admin, false);
            } else {
                dpm.setGlobalSetting(admin, Settings.Global.AUTO_TIME, "0");
            }
            return dpm.setTime(admin, millis);
        }
        try {
            context.getSystemService(AlarmManager.class).setTime(millis);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean setTimeZone(Context context, String zone) {
        if (deviceOwnerCanSetTime(context)) {
            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            ComponentName admin = new ComponentName(context, DeviceOwnerReceiver.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setAutoTimeZoneEnabled(admin, false);
            } else {
                dpm.setGlobalSetting(admin, Settings.Global.AUTO_TIME_ZONE, "0");
            }
            return dpm.setTimeZone(admin, zone);
        }
        try {
            context.getSystemService(AlarmManager.class).setTimeZone(zone);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
