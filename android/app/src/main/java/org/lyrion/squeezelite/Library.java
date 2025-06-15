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

public class Library {
    private Thread thread;
    private boolean loaded;

    public Library() {
        try {
            System.loadLibrary("squeezelite");
            loaded = true;
        } catch (Exception e) {
            loaded = false;
            Utils.error("Failed to load library", e);
        }
    }

    public synchronized void startPlayer(Context context) {
        if (null!=thread || !loaded) {
            return;
        }
        Utils.info("");
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Utils.error("Unhandled exception", throwable);
        });
        thread = new Thread(() -> {
            SharedPreferences prefs = Prefs.get(context);
            ServerDiscovery.Server server = new ServerDiscovery.Server(prefs.getString(Prefs.SERVER_KEY, ""));
            start(server.address(), prefs.getString(Prefs.PLAYER_MAC_KEY, Prefs.DEFAULT_PLAYER_MAC), prefs.getString(Prefs.PLAYER_NAME_KEY, Prefs.DEFAULT_PLAYER_NAME));
        });
        thread.start();
    }

    public synchronized void stopPlayer() {
        if (null==thread || !loaded) {
            return;
        }
        Utils.info("");
        stop();
        thread.interrupt();
        thread = null;
    }

    private native void start(String lms, String mac, String name);
    private native void stop();
}
