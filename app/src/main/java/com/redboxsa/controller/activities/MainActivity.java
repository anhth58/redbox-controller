package com.redboxsa.controller.activities;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.redboxsa.controller.DeviceOwnerReceiver;
import com.redboxsa.controller.R;
import com.redboxsa.controller.service.MyUpdateService;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;


public class MainActivity extends AppCompatActivity {
    Button btnTakeScreenShot;
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        initView();
//        try {
//            Process p = Runtime.getRuntime().exec("su");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        stopService();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startService();
                }
            },10000);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startApp();
                }
            },60000);

            ComponentName adminComponent = new ComponentName(MainActivity.this, DeviceOwnerReceiver.class);

            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

            if (devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(MainActivity.this.getPackageName())) {
                // Your app is already a device owner.
            } else {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Your explanation message");
                startActivityForResult(intent, 1);
            }
        }else {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startService();
                    startApp();
                }
            },10000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(handler != null){
            handler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                // Device admin is enabled
                // You can proceed to configure the app as a device owner
            } else {
                // Device admin was not enabled
                // Handle accordingly
            }
        }
    }
    private void takeScreenshot() {
        Date now = new Date();
        android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);

        try {
            // image naming and path  to include sd card  appending name you choose for file
            String mPath = Environment.getExternalStorageDirectory().toString() + "/" + now + ".jpg";

            // create bitmap screen capture
            View v1 = getWindow().getDecorView().getRootView();
            v1.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
            v1.setDrawingCacheEnabled(false);

            File imageFile = new File(mPath);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();

            openScreenshot(imageFile);
        } catch (Throwable e) {
            // Several error may come out with file handling or DOM
            e.printStackTrace();
        }
    }

    private void openScreenshot(File imageFile) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        Uri uri = Uri.fromFile(imageFile);
        intent.setDataAndType(uri, "image/*");
        startActivity(intent);
    }

    public void stopService() {
        Intent intent = new Intent(MainActivity.this, MyUpdateService.class);
        PendingIntent pendingIntent =
                PendingIntent.getService(MainActivity.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager) MainActivity.this.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }

    public void startService() {
        // Construct our Intent specifying the Service
        Intent i = new Intent(MainActivity.this, MyUpdateService.class);
        // Start the service
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
//            ContextCompat.startForegroundService(this, i);
//        }else {
//            MainActivity.this.startService(i);
//        }
        MainActivity.this.startService(i);

//        ContextCompat.startForegroundService(MainActivity.this, i);

//        Intent intent = new Intent();
//        intent.setAction(Intent.ACTION_MAIN);
//        intent.addCategory(Intent.CATEGORY_HOME);
//        this.startActivity(intent);
//        startApp();
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermission();
    }

    String[] PERMISSIONS = {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
//            android.Manifest.permission.ACCESS_FINE_LOCATION,
//            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.READ_PHONE_STATE,
//            android.Manifest.permission.BLUETOOTH_ADMIN,
//            android.Manifest.permission.BLUETOOTH,
//            android.Manifest.permission.CAMERA
    };
    int PERMISSION_ALL = 100;

    protected void checkPermission() {
        if (!hasPermissions(PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
    }

    private boolean hasPermissions(String... permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void startApp() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.redbox.locker");
        if (launchIntent != null) {
            startActivity(launchIntent);//null pointer check in case package name was not found
        }
    }
}
