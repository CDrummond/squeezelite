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

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.preference.PreferenceManager;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class Prefs {
    public static final String INITIAL_KEY = "initial";
    public static final String SERVER_KEY = "server";
    public static final String PLAYER_NAME_KEY = "player_name";
    public static final String PLAYER_MAC_KEY = "mac";
    public static final String START_SERVICE_KEY = "start_service";
    public static final String USE_WAKE_LOCK_KEY = "use_wake_lock";
    public static final String VOLUME_CONTROL_KEY = "volume_control";
    public static final String CONNECTION_LOST_TIMEOUT_KEY = "connection_lost_timeout";
    public static final String INITIAL_CONNECTION_TIMEOUT_KEY = "initial_connection_timeout";
    public static final String OUTPUT_LIB_KEY = "output_lib";

    public static final String VOLUME_CONTROL_SEPARATE = "separate";
    public static final String VOLUME_CONTROL_DEVICE = "device";
    public static final String VOLUME_CONTROL_SYNCHRONIZED = "synchronized";
    public static final String OUTPUT_LIB_OPENSLES = "opensles";
    public static final String OUTPUT_LIB_AAUDIO = "aaudio";
    public static final String DEFAULT_PLAYER_NAME = "Squeezelite";
    public static final String DEFAULT_PLAYER_MAC = "01:02:03:04:05:06";
    public static final String DEFAULT_CONNECTION_LOST_TIMEOUT = "60";
    public static final String DEFAULT_INITIAL_CONNECTION_TIMEOUT = "0";
    public static final String DEFAULT_OUTPUT_LIB = OUTPUT_LIB_AAUDIO;
    public static boolean DEFAULT_START_SERVICE = false;
    public static boolean DEFAULT_USE_WAKE_LOCK = false;
    public static String DEFAULT_VOLUME_CONTROL = VOLUME_CONTROL_SYNCHRONIZED;

    static public SharedPreferences get(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = null;

        if (!sharedPreferences.contains(PLAYER_MAC_KEY)) {
            List<String> parts = new LinkedList<>();
            Random rand = new Random();
            parts.add("cd");
            parts.add("ef");
            while (parts.size()<6) {
                parts.add(String.format("%02x", rand.nextInt(255)));
            }
            String newMac = String.join(":", parts);
            editor = sharedPreferences.edit();
            editor.putString(PLAYER_MAC_KEY, newMac);
        }
        if (!sharedPreferences.contains(PLAYER_NAME_KEY)) {
            if (null==editor) {
                editor = sharedPreferences.edit();
            }
            editor.putString(PLAYER_NAME_KEY, Settings.Global.getString(context.getContentResolver(), "device_name"));
        }
        if (!sharedPreferences.contains(START_SERVICE_KEY)) {
            if (null==editor) {
                editor = sharedPreferences.edit();
            }
            editor.putBoolean(START_SERVICE_KEY, DEFAULT_START_SERVICE);
        }
        if (!sharedPreferences.contains(USE_WAKE_LOCK_KEY)) {
            if (null==editor) {
                editor = sharedPreferences.edit();
            }
            editor.putBoolean(USE_WAKE_LOCK_KEY, DEFAULT_USE_WAKE_LOCK);
        }
        if (!sharedPreferences.contains(VOLUME_CONTROL_KEY)) {
            if (null==editor) {
                editor = sharedPreferences.edit();
            }
            editor.putString(VOLUME_CONTROL_KEY, DEFAULT_VOLUME_CONTROL);
        }
        if (!sharedPreferences.contains(CONNECTION_LOST_TIMEOUT_KEY)) {
            if (null==editor) {
                editor = sharedPreferences.edit();
            }
            editor.putString(CONNECTION_LOST_TIMEOUT_KEY, DEFAULT_CONNECTION_LOST_TIMEOUT);
        }
        if (!sharedPreferences.contains(INITIAL_CONNECTION_TIMEOUT_KEY)) {
            if (null==editor) {
                editor = sharedPreferences.edit();
            }
            editor.putString(INITIAL_CONNECTION_TIMEOUT_KEY, DEFAULT_INITIAL_CONNECTION_TIMEOUT);
        }
        if (!sharedPreferences.contains(OUTPUT_LIB_KEY)) {
            if (null==editor) {
                editor = sharedPreferences.edit();
            }
            editor.putString(OUTPUT_LIB_KEY, DEFAULT_OUTPUT_LIB);
        }
        if (editor!=null) {
            editor.apply();
        }
        return sharedPreferences;
    }

    public static boolean hasBeenConfigured(SharedPreferences prefs) {
        return prefs.contains(Prefs.SERVER_KEY) || !prefs.getBoolean(Prefs.INITIAL_KEY, true);
    }
}
