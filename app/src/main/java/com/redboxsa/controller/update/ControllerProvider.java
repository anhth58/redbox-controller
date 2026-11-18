package com.redboxsa.controller.update;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONException;

/**
 * The locker app's line to the controller, called every 5 minutes when the locker pings its server.
 * Only apps signed with the same key may call it (permission SYNC, protectionLevel signature).
 *
 * <pre>
 * Bundle in = new Bundle();
 * in.putString("uuid", uuid);                // locker uuid; used only when this controller has none saved yet
 * in.putString("token", token);            // locker JWT, used as "Authorization: Bearer" on the token APIs
 * in.putLong("time", exactNowMillis);        // exact current time (epoch ms) as the locker knows it from its server
 * in.putString("timezone", "Asia/Riyadh");   // IANA time zone id of the locker
 * in.putString("server", "dev");             // "dev" or "stage" when the locker app is a dev/stage build; else omit
 * Bundle out = getContentResolver().call(Uri.parse("content://com.redboxsa.controllerapp.sync"), "sync", null, in);
 * String status = out.getString("status");   // JSON to forward to the server as is
 * </pre>
 * A controller older than versionCode 8 has no such provider: the call throws, which itself means "controller outdated".
 */
public class ControllerProvider extends ContentProvider {

    static final String PERMISSION = "com.redboxsa.controllerapp.permission.SYNC";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        // Manifest permissions do not cover call(): check it here.
        getContext().enforceCallingPermission(PERMISSION, "Locker app only");
        if (!"sync".equals(method)) return null;

        if (extras != null) {
            Updater.setLockerDeviceId(getContext(), extras.getString("uuid"));
            State.fromLocker(getContext(), extras.getString("token"), extras.getLong("time"), extras.getString("timezone"),
                    extras.getString("server"));
        }
        // The locker calls every 5 minutes: a cheap way to make sure the 30-minute job is still there.
        UpdateJobService.schedule(getContext());
        try {
            // An IPC clock reset is fixed within 5 minutes instead of at the next 30-minute cycle. No network here.
            TimeSync.run(getContext());
        } catch (RuntimeException e) {
            Log.e(Updater.TAG, "Time sync from the locker call failed", e);
        }

        Bundle out = new Bundle();
        try {
            out.putString("status", State.status(getContext()).toString());
        } catch (JSONException e) {
            Log.e(Updater.TAG, "Cannot build status", e);
        }
        return out;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
