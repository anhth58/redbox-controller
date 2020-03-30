package com.redboxsa.controller.common;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static android.content.Context.ACTIVITY_SERVICE;

public class Utils {
    public static boolean serviceStarted = false;
    public static int dpToPx(Context context, int dp) {
        Resources r = context.getResources();
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics()));
    }

    public static boolean isInteger(String s) {
        return isInteger(s, 10);
    }

    public static boolean isInteger(String s, int radix) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (i == 0 && s.charAt(i) == '-') {
                if (s.length() == 1) return false;
                else continue;
            }
            if (Character.digit(s.charAt(i), radix) < 0) return false;
        }
        return true;
    }

    public static boolean isBoolean(String s) {
        if (s != null && s.length() == 4) {
            return s.equals("true") || s.equals("false");
        }

        return false;
    }

    public static boolean isExist(String s) {
        return !(s == null || s.equals(""));
    }

    public static String checksumPass(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashInBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));

            // bytes to hex
            StringBuilder sb = new StringBuilder();
            for (byte b : hashInBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return password;
    }

    public static String formatStartTimeCamera(Date date, int time) {
        int hour = time / 100;
        int minutes = time - hour * 100;
        date.setMinutes(minutes);
        date.setHours(hour);
        date.setSeconds(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    public static String formatEndTimeCamera(Date date, int time) {
        int hour = time / 100;
        int minutes = time - hour * 100;
        date.setMinutes(minutes);
        date.setHours(hour);
        date.setSeconds(59);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    public static String formatResultDateCamera(Date date, String time) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(date) + " " + time;
    }

    public static void updateLanguage(String lang, Context context) {
        Locale myLocale = new Locale(lang);
        Resources res = context.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        Configuration conf = res.getConfiguration();
        conf.locale = myLocale;
        res.updateConfiguration(conf, dm);
    }

    public static String convertNumberDecimal(int number) {
        if (number < 10) {
            return "0" + number;
        } else {
            return String.valueOf(number);
        }
    }

    public static String getEnglishNumbers(String Numbers) {
        String ArabicNumber = Numbers;
        try {

            ArabicNumber = ArabicNumber.replace('٠', '0');
            ArabicNumber = ArabicNumber.replace('١', '1');
            ArabicNumber = ArabicNumber.replace('٢', '2');
            ArabicNumber = ArabicNumber.replace('٣', '3');
            ArabicNumber = ArabicNumber.replace('٤', '4');
            ArabicNumber = ArabicNumber.replace('٥', '5');
            ArabicNumber = ArabicNumber.replace('٦', '6');
            ArabicNumber = ArabicNumber.replace('٧', '7');
            ArabicNumber = ArabicNumber.replace('٨', '8');
            ArabicNumber = ArabicNumber.replace('٩', '9');
        } catch (Exception e) {

        }

        return ArabicNumber;
    }

    public static void saveVersionNumber(Context context, int version) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("version", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("version_number", version);
        editor.apply();
    }

    public static int getVersionNumber(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("version", Context.MODE_PRIVATE);
        return sharedPreferences.getInt("version_number", 0);
    }

    private boolean isUpdateServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            Log.d("service name", service.service.getClassName());
            if ("MyUpdateService".equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
