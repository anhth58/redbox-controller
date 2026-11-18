package com.redboxsa.controller.update;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

import com.redboxsa.controller.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What the controller knows between cycles: data handed over by the locker app (token, exact time, time zone)
 * and the outcome of the last cycle. Also builds the status the locker forwards to the server.
 */
final class State {

    /** A cycle should run every 30 minutes; none for this long means the controller is not running. */
    private static final long NOT_RUNNING_AFTER_MS = 2 * 60 * 60 * 1000L;
    /** Locker time older than this is not trusted (the locker sends it every 5 minutes). */
    private static final long LOCKER_TIME_MAX_AGE_MS = 60 * 60 * 1000L;

    private State() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("state", Context.MODE_PRIVATE);
    }

    /** Called by the locker app every 5 minutes (see ControllerProvider). */
    static void fromLocker(Context context, String token, long time, String timeZone) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (token != null && !token.isEmpty()) editor.putString("token", token);
        if (time > 0) {
            editor.putLong("locker_time", time).putLong("locker_time_elapsed", SystemClock.elapsedRealtime());
        }
        if (timeZone != null && !timeZone.isEmpty()) editor.putString("locker_timezone", timeZone);
        editor.apply();
    }

    static String token(Context context) {
        return prefs(context).getString("token", null);
    }

    /** Exact time according to the locker app, or 0 when unknown, stale or from before a reboot. */
    static long lockerNow(Context context) {
        SharedPreferences prefs = prefs(context);
        long time = prefs.getLong("locker_time", 0);
        long age = SystemClock.elapsedRealtime() - prefs.getLong("locker_time_elapsed", 0);
        return time == 0 || age < 0 || age > LOCKER_TIME_MAX_AGE_MS ? 0 : time + age;
    }

    static String lockerTimeZone(Context context) {
        return prefs(context).getString("locker_timezone", null);
    }

    static void setServerTimeZone(Context context, String timeZone) {
        if (timeZone != null && !timeZone.isEmpty()) prefs(context).edit().putString("server_timezone", timeZone).apply();
    }

    static String serverTimeZone(Context context) {
        return prefs(context).getString("server_timezone", null);
    }

    static void setControllerLatest(Context context, int version) {
        prefs(context).edit().putInt("controller_latest", version).apply();
    }

    /** @param error null when the whole cycle went fine */
    static void setLastCheck(Context context, String error) {
        prefs(context).edit()
                .putLong("last_check", System.currentTimeMillis())
                .putLong("last_check_elapsed", SystemClock.elapsedRealtime())
                .putString("last_error", error)
                .apply();
    }

    /** Status for the locker app to forward to the server, so a technician knows what to fix. */
    static JSONObject status(Context context) throws JSONException {
        SharedPreferences prefs = prefs(context);
        boolean deviceOwner = isDeviceOwner(context);
        boolean phonePermission = granted(context, Manifest.permission.READ_PHONE_STATE);
        boolean silentInstall = deviceOwner || granted(context, Manifest.permission.INSTALL_PACKAGES);
        boolean setTime = TimeSync.canSetTime(context);
        int latest = prefs.getInt("controller_latest", 0);
        String lastError = prefs.getString("last_error", null);
        long sinceCheck = SystemClock.elapsedRealtime() - prefs.getLong("last_check_elapsed", -NOT_RUNNING_AFTER_MS);

        JSONArray problems = new JSONArray();
        if (!phonePermission) problems.put("NO_PHONE_PERMISSION");
        // Android 8+ IPCs (Android 11) only install silently as device owner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !deviceOwner) problems.put("NOT_DEVICE_OWNER");
        if (!silentInstall) problems.put("CANNOT_INSTALL");
        if (!setTime) problems.put("CANNOT_SET_TIME");
        if (latest > BuildConfig.VERSION_CODE) problems.put("CONTROLLER_OUTDATED");
        if (lastError != null) problems.put("LAST_CHECK_FAILED");
        if (sinceCheck < 0 || sinceCheck > NOT_RUNNING_AFTER_MS) problems.put("NOT_RUNNING");

        return new JSONObject()
                .put("uuid", Updater.deviceId(context))
                .put("controller_version", BuildConfig.VERSION_CODE)
                .put("controller_latest", latest)
                .put("android_sdk", Build.VERSION.SDK_INT)
                .put("device_owner", deviceOwner)
                .put("silent_install", silentInstall)
                .put("set_time", setTime)
                .put("phone_permission", phonePermission)
                .put("token", token(context) != null)
                .put("last_check", prefs.getLong("last_check", 0))
                .put("last_error", lastError)
                .put("problems", problems);
    }

    static boolean isDeviceOwner(Context context) {
        return context.getSystemService(DevicePolicyManager.class).isDeviceOwnerApp(context.getPackageName());
    }

    private static boolean granted(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }
}
