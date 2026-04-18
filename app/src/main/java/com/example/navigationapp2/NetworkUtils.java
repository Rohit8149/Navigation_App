package com.example.navigationapp2;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

public class NetworkUtils {

    private static final String TAG = "NAV_APP";

    // 🔍 Check if device has an active internet connection
    public static boolean isInternetAvailable(Context context) {

        Log.d(TAG, "🔎 Checking internet availability...");

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            Log.e(TAG, "❌ ConnectivityManager is null");
            return false;
        }

        Network network = cm.getActiveNetwork();
        if (network == null) {
            Log.w(TAG, "⚠️ No active network found");
            return false;
        }

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        if (capabilities == null) {
            Log.w(TAG, "⚠️ NetworkCapabilities is null");
            return false;
        }

        boolean hasInternet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);

        Log.d(TAG, "✅ Internet available: " + hasInternet);
        return hasInternet;
    }
}