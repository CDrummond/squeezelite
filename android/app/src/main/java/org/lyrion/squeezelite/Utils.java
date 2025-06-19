/*
 *  Squeezelite Android
 *
 *  (c) Craig Drummond 2025 <craig.p.drummond@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.lyrion.squeezelite;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;


public class Utils {
    public static final String LOG_TAG = "SqueezeliteUI";

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        return null!=info && info.isConnected();
    }

    static public boolean notificationAllowed(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            debug("Check if notif permission granted");
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                debug("No notif permission");
                return false;
            }
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            debug("Notifs are disabled");
            return false;
        }
        if (channelId!=null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = mgr.getNotificationChannel(channelId);
            if (null!=channel) {
                debug("Channel " + channelId + " importance " + channel.getImportance());
                return channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
            }
        }
        debug("Notifs are allowed");
        return true;
    }

    public static boolean isEmpty(String str) {
        return null==str || str.isEmpty();
    }

    public static int toInt(String str, int def) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static String logPrefix() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        if (null!=st && st.length>4) {
            // Remove org.lyrion.squeezelite.
            return "["+st[4].getClassName().substring(23)+"."+st[4].getMethodName()+"] ";
        }
        return "";
    }

    public static void verbose(String message) {
        if (BuildConfig.DEBUG) {
            Log.v(LOG_TAG, logPrefix() + message);
        }
    }

    public static void debug(String message) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, logPrefix() + message);
        }
    }

    public static void info(String message) {
        if (BuildConfig.DEBUG) {
            Log.i(LOG_TAG, logPrefix() + message);
        }
    }

    public static void warn(String message) {
        if (BuildConfig.DEBUG) {
            Log.w(LOG_TAG, logPrefix() + message);
        }
    }

    public static void warn(String message, Throwable t) {
        if (BuildConfig.DEBUG) {
            Log.w(LOG_TAG, logPrefix() + message, t);
        }
    }

    public static void error(String message) {
        if (BuildConfig.DEBUG) {
            Log.e(LOG_TAG, logPrefix() + message);
        }
    }

    public static void error(String message, Throwable t) {
        if (BuildConfig.DEBUG) {
            Log.e(LOG_TAG, logPrefix() + message, t);
        }
    }
}
