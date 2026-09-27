package com.aibot;

import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * DeviceController - controls hardware on the Android device
 * Works on Android 11 ARMv7a
 *
 * Flashlight  → CameraManager (API 23+) or Camera (older)
 * WiFi        → WifiManager (direct on API <29, settings intent on 29+)
 * Bluetooth   → BluetoothAdapter
 * Apps        → Intent launch by package name or name search
 * Mobile data → Settings intent (no root needed on most devices)
 */
public class DeviceController {

    private static final String TAG = "DeviceController";

    private Context       context;
    private CameraManager cameraManager;
    private String        cameraId;
    private boolean       torchOn = false;

    public DeviceController(Context context) {
        this.context = context.getApplicationContext();
        initCamera();
    }

    // ─── FLASHLIGHT ───────────────────────────────────────────────────────────

    private void initCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            try {
                cameraId = cameraManager.getCameraIdList()[0];
            } catch (CameraAccessException | ArrayIndexOutOfBoundsException e) {
                Log.e(TAG, "Camera init error: " + e.getMessage());
            }
        }
    }

    public boolean setFlashlight(boolean on) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cameraManager != null) {
            try {
                cameraManager.setTorchMode(cameraId, on);
                torchOn = on;
                return true;
            } catch (CameraAccessException e) {
                Log.e(TAG, "Flashlight error: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public boolean toggleFlashlight() {
        return setFlashlight(!torchOn);
    }

    public boolean isFlashlightOn() { return torchOn; }

    // ─── WIFI ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    public boolean setWifi(boolean enable) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 and below: direct control
            WifiManager wm = (WifiManager)
                context.getApplicationContext()
                       .getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wm.setWifiEnabled(enable);
                return true;
            }
        } else {
            // Android 10+: open WiFi settings panel
            openWifiSettings();
        }
        return false;
    }

    public void openWifiSettings() {
        Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    public boolean isWifiEnabled() {
        WifiManager wm = (WifiManager)
            context.getApplicationContext()
                   .getSystemService(Context.WIFI_SERVICE);
        return wm != null && wm.isWifiEnabled();
    }

    // ─── BLUETOOTH ────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    public boolean setBluetooth(boolean enable) {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) return false;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (enable) bt.enable();
            else        bt.disable();
            return true;
        } else {
            // Android 13+: open settings
            openBluetoothSettings();
            return false;
        }
    }

    public void openBluetoothSettings() {
        Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    public boolean isBluetoothEnabled() {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        return bt != null && bt.isEnabled();
    }

    // ─── MOBILE DATA ──────────────────────────────────────────────────────────

    public void openMobileDataSettings() {
        Intent i;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            i = new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
        } else {
            i = new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(i);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
        }
    }

    // ─── APP LAUNCHER ─────────────────────────────────────────────────────────

    public boolean openApp(String appNameOrPackage) {
        PackageManager pm = context.getPackageManager();

        // Try as package name first
        try {
            Intent i = pm.getLaunchIntentForPackage(appNameOrPackage);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                return true;
            }
        } catch (Exception ignored) {}

        // Try searching by app name
        String pkg = findPackageByName(appNameOrPackage.toLowerCase());
        if (pkg != null) {
            try {
                Intent i = pm.getLaunchIntentForPackage(pkg);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // Try Play Store search
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://search?q=" + appNameOrPackage));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return true;
        } catch (ActivityNotFoundException ignored) {}

        return false;
    }

    private String findPackageByName(String name) {
        PackageManager pm = context.getPackageManager();
        // Common apps map
        java.util.Map<String, String> common = new java.util.HashMap<>();
        common.put("youtube",   "com.google.android.youtube");
        common.put("whatsapp",  "com.whatsapp");
        common.put("chrome",    "com.android.chrome");
        common.put("camera",    "com.android.camera2");
        common.put("settings",  "com.android.settings");
        common.put("maps",      "com.google.android.apps.maps");
        common.put("gmail",     "com.google.android.gm");
        common.put("facebook",  "com.facebook.katana");
        common.put("instagram", "com.instagram.android");
        common.put("twitter",   "com.twitter.android");
        common.put("telegram",  "org.telegram.messenger");
        common.put("spotify",   "com.spotify.music");
        common.put("netflix",   "com.netflix.mediaclient");
        common.put("calculator","com.android.calculator2");
        common.put("clock",     "com.android.deskclock");
        common.put("gallery",   "com.android.gallery3d");
        common.put("files",     "com.android.documentsui");
        common.put("contacts",  "com.android.contacts");
        common.put("phone",     "com.android.phone");
        common.put("messages",  "com.android.mms");
        common.put("browser",   "com.android.browser");
        common.put("music",     "com.android.music");
        common.put("play",      "com.android.vending");
        common.put("tiktok",    "com.zhiliaoapp.musically");
        common.put("snapchat",  "com.snapchat.android");

        for (java.util.Map.Entry<String, String> entry : common.entrySet()) {
            if (name.contains(entry.getKey())) return entry.getValue();
        }

        // Search installed apps
        try {
            java.util.List<android.content.pm.ApplicationInfo> apps =
                pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (android.content.pm.ApplicationInfo app : apps) {
                String label = pm.getApplicationLabel(app).toString().toLowerCase();
                if (label.contains(name) || name.contains(label)) {
                    return app.packageName;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "App search error: " + e.getMessage());
        }
        return null;
    }

    // ─── SCREEN BRIGHTNESS ────────────────────────────────────────────────────

    public void openBrightnessSettings() {
        Intent i = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    // ─── VOLUME ───────────────────────────────────────────────────────────────

    public void openSoundSettings() {
        Intent i = new Intent(Settings.ACTION_SOUND_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    // ─── BATTERY ──────────────────────────────────────────────────────────────

    public int getBatteryLevel() {
        android.content.IntentFilter filter =
            new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battery = context.registerReceiver(null, filter);
        if (battery == null) return -1;
        int level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
        return (int)((level / (float) scale) * 100);
    }

    public boolean isCharging() {
        android.content.IntentFilter filter =
            new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battery = context.registerReceiver(null, filter);
        if (battery == null) return false;
        int status = battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
               status == android.os.BatteryManager.BATTERY_STATUS_FULL;
    }

    // ─── STATUS SUMMARY ───────────────────────────────────────────────────────

    public String getDeviceStatus() {
        int battery = getBatteryLevel();
        boolean charging = isCharging();
        boolean wifi = isWifiEnabled();
        boolean bt   = isBluetoothEnabled();

        return "📱 Device Status:\n" +
               "🔋 Battery: " + battery + "%" + (charging ? " (charging)" : "") + "\n" +
               "📶 WiFi: "    + (wifi ? "ON" : "OFF") + "\n" +
               "🔵 Bluetooth: " + (bt ? "ON" : "OFF") + "\n" +
               "🔦 Flashlight: " + (torchOn ? "ON" : "OFF");
    }
}
