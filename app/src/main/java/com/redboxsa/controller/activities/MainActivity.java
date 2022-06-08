package com.redboxsa.controller.activities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.redboxsa.controller.R;
import com.redboxsa.controller.service.MyUpdateService;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;


public class MainActivity extends AppCompatActivity {
    Button btnTakeScreenShot;

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
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startService();
                startApp();
            }
        },10000);

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
        MainActivity.this.startService(i);
//        ContextCompat.startForegroundService(MainActivity.this, i);

//        Intent intent = new Intent();
//        intent.setAction(Intent.ACTION_MAIN);
//        intent.addCategory(Intent.CATEGORY_HOME);
//        this.startActivity(intent);
        startApp();
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
