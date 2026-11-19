package com.redboxsa.controller.update;

import android.Manifest;
import android.app.AlarmManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.redboxsa.controller.DeviceOwnerReceiver;

import java.util.Arrays;
import java.util.TimeZone;

/**
 * Keeps the IPC clock and time zone right, at every locker sync (5 minutes) and every cycle (30 minutes).
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

    /** Synchronized: the 30-minute cycle and the locker's sync call may run it at the same time. */
    static synchronized void run(Context context) {
        long lockerNow = State.lockerNow(context);
        long serverNow = Http.serverNow();
        long exactNow = lockerNow != 0 ? lockerNow : serverNow;
        String source = lockerNow != 0 ? "locker" : "server";
        if (lockerNow != 0 && serverNow != 0 && Math.abs(lockerNow - serverNow) > MAX_DRIFT_MS) {
            // A bug on either side must never move the clock: leave it alone until both agree again.
            report(context, "time", "TIME_SET_SKIPPED", "locker and server time differ by " + (lockerNow - serverNow) / 1000 + "s");
        } else if (exactNow < MIN_VALID_TIME_MS) {
            Log.i(Updater.TAG, "No exact time available yet");
        } else {
            long drift = System.currentTimeMillis() - exactNow;
            if (Math.abs(drift) <= MAX_DRIFT_MS) {
                report(context, "time", null, null);
            } else if (setTime(context, exactNow)) {
                report(context, "time", null, null);
                EventLog.add(context, "TIME_SET", "drift " + drift / 1000 + "s, source " + source);
            } else {
                report(context, "time", "TIME_SET_FAILED", "drift " + drift / 1000 + "s, source " + source + ", no permission to set time");
            }
        }

        // The zone is only touched when it is surely right: a real zone id of this device, and the server does not say otherwise.
        // Anything else leaves the zone and its automatic setting exactly as they are.
        String lockerZone = State.lockerTimeZone(context);
        String serverZone = State.serverTimeZone(context);
        String zone = lockerZone != null ? lockerZone : serverZone;
        String current = TimeZone.getDefault().getID();
        if (zone == null || zone.equals(current)) {
            report(context, "zone", null, null);
        } else if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(zone)) {
            report(context, "zone", "TIMEZONE_SET_FAILED", "unknown time zone " + zone + ", left " + current);
        } else if (lockerZone != null && serverZone != null && !lockerZone.equals(serverZone)) {
            report(context, "zone", "TIMEZONE_SET_SKIPPED", "locker says " + lockerZone + ", server says " + serverZone + ", left " + current);
        } else if (setTimeZone(context, zone)) {
            // Read before setting: AlarmManager.setTimeZone also changes this process's default zone.
            report(context, "zone", null, null);
            EventLog.add(context, "TIMEZONE_SET", current + " -> " + zone);
        } else {
            report(context, "zone", "TIMEZONE_SET_FAILED", current + " -> " + zone + ", no permission to set time zone");
        }
    }

    /**
     * Logs a lasting problem once instead of every 5 minutes (the locker's sync), so it cannot push real events out of
     * the 500-line log. {@code type} null = the problem is over; the next one is logged again.
     */
    private static void report(Context context, String area, String type, String message) {
        SharedPreferences prefs = context.getSharedPreferences("time_sync", Context.MODE_PRIVATE);
        String last = prefs.getString(area, null);
        if (type == null ? last == null : type.equals(last)) return;
        prefs.edit().putString(area, type).apply();
        if (type != null) EventLog.add(context, type, message);
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
            // setTime refuses while automatic time is on: switch it off for the call, then back on so NTP stays as a fallback.
            boolean autoTime = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AUTO_TIME, 0) == 1;
            setAutoTime(dpm, admin, false);
            boolean ok = dpm.setTime(admin, millis);
            if (autoTime) setAutoTime(dpm, admin, true);
            return ok;
        }
        try {
            context.getSystemService(AlarmManager.class).setTime(millis);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static void setAutoTime(DevicePolicyManager dpm, ComponentName admin, boolean on) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dpm.setAutoTimeEnabled(admin, on);
        } else {
            dpm.setGlobalSetting(admin, Settings.Global.AUTO_TIME, on ? "1" : "0");
        }
    }

    @SuppressWarnings("deprecation")
    private static void setAutoTimeZone(DevicePolicyManager dpm, ComponentName admin, boolean on) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dpm.setAutoTimeZoneEnabled(admin, on);
        } else {
            dpm.setGlobalSetting(admin, Settings.Global.AUTO_TIME_ZONE, on ? "1" : "0");
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean setTimeZone(Context context, String zone) {
        if (deviceOwnerCanSetTime(context)) {
            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            ComponentName admin = new ComponentName(context, DeviceOwnerReceiver.class);
            // setTimeZone refuses while automatic time zone is on: off for the call only, then back as it was (also on failure).
            boolean autoZone = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 0) == 1;
            setAutoTimeZone(dpm, admin, false);
            boolean ok = dpm.setTimeZone(admin, zone);
            if (autoZone) setAutoTimeZone(dpm, admin, true);
            return ok;
        }
        try {
            context.getSystemService(AlarmManager.class).setTimeZone(zone);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
