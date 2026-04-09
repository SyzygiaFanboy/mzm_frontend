package com.example.myapplication.network;

import android.os.Build;

/**
 * Central place for backend server address.
 *
 * - Emulator: prefer adb reverse (127.0.0.1), fallback to 10.0.2.2.
 * - Real device: use LAN_HOST (edit if your PC IP changes).
 */
public final class ServerConfig {
    private ServerConfig() {}

    // Your PC LAN address for real devices on the same Wi‑Fi.
    // If you only use emulator, you can ignore this.
    private static final String LAN_HOST = "192.168.31.83";
    private static final int PORT = 8080;
    private static final String CONTEXT = "houduan2";

    /** If you run `adb reverse tcp:8080 tcp:8080`, emulator can use localhost. */
    private static final boolean EMULATOR_USE_ADB_REVERSE = true;

    public static String baseUrl() {
        return "http://" + host() + ":" + PORT + "/";
    }

    public static String appBaseUrl() {
        return "http://" + host() + ":" + PORT + "/" + CONTEXT + "/";
    }

    public static String songUploadUrl() {
        return appBaseUrl() + "SongServlet";
    }

    public static String playStreamUrl(String songIdEncoded) {
        // Must include context path so backend filter (/play/*) runs inside this webapp.
        return appBaseUrl() + "play/stream?songId=" + songIdEncoded;
    }

    private static String host() {
        if (isEmulator()) {
            // Prefer adb reverse to avoid emulator networking/firewall surprises.
            return EMULATOR_USE_ADB_REVERSE ? "127.0.0.1" : "10.0.2.2";
        }
        return LAN_HOST;
    }

    private static boolean isEmulator() {
        final String fp = Build.FINGERPRINT;
        final String model = Build.MODEL;
        final String brand = Build.BRAND;
        final String device = Build.DEVICE;
        final String product = Build.PRODUCT;

        return (fp != null && (fp.startsWith("generic") || fp.contains("emulator") || fp.contains("sdk_gphone")))
                || (model != null && (model.contains("Emulator") || model.contains("Android SDK built for x86")))
                || (brand != null && brand.startsWith("generic"))
                || (device != null && device.startsWith("generic"))
                || (product != null && product.contains("sdk"));
    }
}

