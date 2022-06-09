package com.redboxsa.controller.service;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import com.redboxsa.controller.R;
import com.redboxsa.controller.activities.MainActivity;
import com.redboxsa.controller.common.UrlCommon;
import com.redboxsa.controller.common.Utils;
import com.redboxsa.controller.volley.listeners.ResponseListener;
import com.redboxsa.controller.volley.network.ApiResponse.ApiResponse;
import com.redboxsa.controller.volley.network.JsonRequest.JsonObjectReq;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class MyUpdateService extends IntentService {
    private String mUUID;
    private final static int VERSION = 5;

    public MyUpdateService() {
        super(MyUpdateService.class.getSimpleName());
    }

    public String getUUID() {
        String uuid;
        SharedPreferences prefs = this.getSharedPreferences(
                "FILE", Context.MODE_PRIVATE);
        uuid = prefs.getString("uuid", null);
        if (uuid != null && !uuid.isEmpty()) {
            Log.d("UUID 1", uuid);
            return uuid;
        }
        TelephonyManager tManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    Activity#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for Activity#requestPermissions for more details.
                return null;
            }
        }
        uuid = tManager.getDeviceId();
        ;
        if (uuid == null || uuid.isEmpty()) {
            uuid = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        Log.d("UUID 2", uuid);
        if (uuid != null) {
            SharedPreferences prefs2 = this.getSharedPreferences(
                    "FILE", Context.MODE_PRIVATE);
            Log.d("save uuid 1", uuid);
            prefs2.edit().putString("uuid", uuid).apply();
            ;
        }
        return uuid;
    }

    private boolean isPackageInstalled(String packageName, PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("OnCreate", "create service");

        if (!Utils.serviceStarted) {
            Utils.serviceStarted = true;
        }

        if (mUUID == null) {
            mUUID = getUUID();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Controller App")
                .setContentText("Controller app is working")
                .setContentIntent(pendingIntent).build();
        startForeground(1337, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("onDestroy", "onDestroy service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("#onHandleIntent", "check for update");

        selfUpgrade();
        // After doing useful things...
        scheduleNextUpdate();
        // Do useful things.
    }

    private void checkForUpdate() {
        String uuid = getUUID();
        JsonObjectReq.makeGetRequest(this, UrlCommon.CHECK_FOR_UPDATE + "?uuid=" + uuid, null, null, new ResponseListener() {
            @Override
            public void onRequestCompleted(ApiResponse result) {
                try {
                    JSONObject response = result.get_jsonObject();
                    boolean state = response.getBoolean("state");
                    if (state) {
                        JSONObject jsonObject = response.getJSONObject("data");
                        int currentVersion = jsonObject.getInt("current_version");
                        int newVersion = jsonObject.getInt("new_version");
                        boolean approvedApk = jsonObject.getBoolean("approved_apk");
                        boolean autoUpdate = jsonObject.getBoolean("auto_update");
                        String url = jsonObject.getString("apk_url");
                        PackageManager pm = getPackageManager();
                        if ((currentVersion != newVersion && (autoUpdate || approvedApk)) || !isPackageInstalled("com.redbox.locker", pm)) {
                            downloadApkFile(newVersion, url);
                        }
                        if(jsonObject.optBoolean("start_app")){
                            selfUpgrade();
                            startApp();
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onRequestError(ApiResponse error) {

            }
        });
    }

    private void selfUpgrade() {
        String uuid = getUUID();
        JsonObjectReq.makeGetRequest(this, UrlCommon.CHECK_FOR_SELF_UPDATE + "?current_version=" + VERSION + "&uuid=" + uuid, null, null, new ResponseListener() {
            @Override
            public void onRequestCompleted(ApiResponse result) {
                try {
                    JSONObject response = result.get_jsonObject();
                    boolean state = response.getBoolean("state");
                    if (state) {
                        JSONObject jsonObject = response.getJSONObject("data");
                        Log.d("Current version", VERSION + "");
                        int newVersion = jsonObject.getInt("new_version");
                        boolean approvedApk = jsonObject.getBoolean("approved_apk");
                        boolean autoUpdate = jsonObject.getBoolean("auto_update");
                        String url = jsonObject.getString("apk_url");
                        PackageManager pm = getPackageManager();
                        if ((VERSION < newVersion && (autoUpdate || approvedApk))) {
                            downloadSelf(newVersion, url);
                        }
                    }
                    checkForUpdate();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onRequestError(ApiResponse error) {
                checkForUpdate();
            }
        });
    }

    private void downloadApkFile(final int version, String url) {
        //get destination to update file and set Uri
        //TODO: First I wanted to store my update .apk file on internal storage for my app but apparently android does not allow you to open and install
        //aplication with existing package from there. So for me, alternative solution is Download directory in external storage. If there is better
        //solution, please inform us in comment
        String destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            File[] externalCacheDirs = this.getExternalCacheDirs();
            for (File file : externalCacheDirs) {
                if (Environment.isExternalStorageRemovable(file)) {
                    // Path is in format /storage.../Android....
                    // Get everything before /Android
                    destination = file.getPath().split("/Android")[0] + "/";
                    break;
                }
            }
        }
        String fileName = "LockerApp.apk";
        destination += fileName;
        final Uri uri = Uri.parse("file://" + destination);

        //Delete update file if exists
        File file = new File(destination);
        if (file.exists())
            //file.delete() - test this, I think sometimes it doesnt work
            file.delete();

        //get url of app on server
        //final String url = "https://redbox-aws-s3-bucket.s3.amazonaws.com/redbox_2019_app_locker.apk";
        Log.d("start download", url);
        Log.d("path", uri.getPath());

        //set downloadmanager
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription("Download new version");
        request.setTitle("Download new version");

        //set destination
        request.setDestinationUri(uri);

        // get download service and enqueue file
        final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        final long downloadId = manager.enqueue(request);

        //set BroadcastReceiver to install app when .apk is downloaded
        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                Log.d("#onReceive", "Complete download");
//                Intent install = new Intent(Intent.ACTION_VIEW);
//                install.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                install.setDataAndType(uri,
//                        manager.getMimeTypeForDownloadedFile(downloadId));
//                startActivity(install);
//
//                unregisterReceiver(this);
//
                unregisterReceiver(this);
                installApk(uri.getPath());
                startApp();
            }
        };
        //register receiver for when .apk download is compete
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void downloadSelf(final int version, String url) {
        //get destination to update file and set Uri
        //TODO: First I wanted to store my update .apk file on internal storage for my app but apparently android does not allow you to open and install
        //aplication with existing package from there. So for me, alternative solution is Download directory in external storage. If there is better
        //solution, please inform us in comment
        String destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            File[] externalCacheDirs = this.getExternalCacheDirs();
            for (File file : externalCacheDirs) {
                if (Environment.isExternalStorageRemovable(file)) {
                    // Path is in format /storage.../Android....
                    // Get everything before /Android
                    destination = file.getPath().split("/Android")[0] + "/";
                    break;
                }
            }
        }
        String fileName = "ControllerApp.apk";
        destination += fileName;
        final Uri uri = Uri.parse("file://" + destination);

        //Delete update file if exists
        File file = new File(destination);
        if (file.exists())
            //file.delete() - test this, I think sometimes it doesnt work
            file.delete();

        //get url of app on server
        //final String url = "https://redbox-aws-s3-bucket.s3.amazonaws.com/redbox_2019_app_locker.apk";
        Log.d("start download crl", url);
        Log.d("path crl", uri.getPath());

        //set downloadmanager
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription("Download new version");
        request.setTitle("Download new version");

        //set destination
        request.setDestinationUri(uri);

        // get download service and enqueue file
        final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        final long downloadId = manager.enqueue(request);

        //set BroadcastReceiver to install app when .apk is downloaded
        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                Log.d("#onReceive", "Complete download crl");
//                Intent install = new Intent(Intent.ACTION_VIEW);
//                install.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                install.setDataAndType(uri,
//                        manager.getMimeTypeForDownloadedFile(downloadId));
//                startActivity(install);
//
//                unregisterReceiver(this);
//
                unregisterReceiver(this);
                installApkWithoutUninstall(uri.getPath());
//                startSelf();
            }
        };
        //register receiver for when .apk download is compete
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public boolean uninstallPackage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            String appPackage = "com.redbox.locker";
            Intent intent = new Intent(getApplicationContext(),
                    getApplicationContext().getClass()); //getActivity() is undefined!
            PendingIntent sender = PendingIntent.getActivity(this, 0, intent, 0);
            PackageInstaller mPackageInstaller =
                    this.getPackageManager().getPackageInstaller();
            mPackageInstaller.uninstall(appPackage, sender.getIntentSender());
        } else {
            final String commandUninstall = "pm uninstall com.redbox.locker";
            Process procUninstall = null;
            try {
                procUninstall = Runtime.getRuntime().exec(commandUninstall);
                procUninstall.waitFor();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        System.err.println("old sdk");
        return false;
    }

    private void installApkWithoutUninstall(String filename) {
        File file = new File(filename);
        if (file.exists()) {
            try {
                Log.d("#installApk", "start install");
                final String command = "pm install -r " + file.getAbsolutePath();
                Process proc = Runtime.getRuntime().exec(command);
                proc.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void installApk(String filename) {
        File file = new File(filename);
        if (file.exists()) {
            try {
                Log.d("#installApk", "start uninstall");
                uninstallPackage();
                Log.d("#installApk", "start install");
                final String command = "pm install -r " + file.getAbsolutePath();
                Process proc = Runtime.getRuntime().exec(command);
                proc.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startApp() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.redbox.locker");
        if (launchIntent != null) {
            startActivity(launchIntent);//null pointer check in case package name was not found
        }
    }

    private void startSelf() {
        Log.d("START", "startSelf");
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.redboxsa.controllerapp");
        if (launchIntent != null) {
            Log.d("START", "startSelf");
            startActivity(launchIntent);//null pointer check in case package name was not found
        }
    }

    private void stopApp() {
        try {
            Log.d("#stopApp", "start");
            final String command = "am force-stop com.redbox.locker";
            Process proc = Runtime.getRuntime().exec(command);
            proc.waitFor();
            Log.d("#stopApp", "done");
        } catch (Exception e) {
            e.printStackTrace();
        }
        ActivityManager am = (ActivityManager) getSystemService(Activity.ACTIVITY_SERVICE);
        if (am != null) {
            am.killBackgroundProcesses("com.redbox.locker");
        }
    }

    private void scheduleNextUpdate() {
        Intent intent = new Intent(this, this.getClass());
        PendingIntent pendingIntent =
                PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // The update frequency should often be user configurable.  This is not.

        long currentTimeMillis = System.currentTimeMillis();
        long nextUpdateTimeMillis = currentTimeMillis + 30 * DateUtils.MINUTE_IN_MILLIS;
        Log.d("Schedule next update", nextUpdateTimeMillis + "");

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        //Log.d("next fire", nextUpdateTimeMillis+ "");
        alarmManager.set(AlarmManager.RTC, nextUpdateTimeMillis, pendingIntent);
    }
}
