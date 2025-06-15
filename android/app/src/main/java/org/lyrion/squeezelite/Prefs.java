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
    public static final String SERVER_KEY = "server";
    public static final String PLAYER_MAC_KEY = "mac";
    public static final String PLAYER_NAME_KEY = "player_name";

    public static final String DEFAULT_PLAYER_NAME = "Squeezelite";
    public static final String DEFAULT_PLAYER_MAC = "01:02:03:04:05:06";

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
        if (editor!=null) {
            editor.apply();
        }
        return sharedPreferences;
    }
}
