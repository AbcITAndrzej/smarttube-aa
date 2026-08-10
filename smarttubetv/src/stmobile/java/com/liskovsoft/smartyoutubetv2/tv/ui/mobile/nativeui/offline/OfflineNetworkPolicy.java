package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

/** Shared network gate for Stage 7 passive saves and Stage 8 explicit playlist downloads. */
final class OfflineNetworkPolicy {
    private OfflineNetworkPolicy() {}

    static boolean isAllowed(Context context, boolean wifiOnly) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= 23) {
            Network network = manager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null
                    || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return false;
            }
            if (!wifiOnly) return true;
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        }
        //noinspection deprecation
        NetworkInfo info = manager.getActiveNetworkInfo();
        //noinspection deprecation
        if (info == null || !info.isConnected()) return false;
        if (!wifiOnly) return true;
        //noinspection deprecation
        int type = info.getType();
        return type == ConnectivityManager.TYPE_WIFI || type == ConnectivityManager.TYPE_ETHERNET;
    }
}
