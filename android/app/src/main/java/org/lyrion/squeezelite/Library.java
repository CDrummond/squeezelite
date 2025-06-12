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

public class Library {
    private Thread thread;
    private boolean loaded;

    private static class LazyHolder {
        private static final Library instance = new Library();
    }

    public static Library getInstance() {
        return LazyHolder.instance;
    }

    private Library() {
        try {
            System.loadLibrary("squeezelite");
            loaded = true;
        } catch (Exception e) {
            loaded = false;
            Utils.error("Failed to load library", e);
        }
    }

    public synchronized boolean startPlayer(Context context) {
        if (null!=thread || !loaded) {
            return false;
        }
        Utils.info("");
        thread = new Thread(() -> {
            Prefs prefs = new Prefs(context);
            start(prefs.getLmsAddress(), prefs.getPlayerMac(), prefs.getPlayerName());
        });
        thread.start();
        return true;
    }

    public synchronized boolean stopPlayer() {
        if (null==thread || !loaded) {
            return false;
        }
        Utils.info("");
        stop();
        thread.interrupt();
        thread = null;
        return true;
    }

    private native void start(String lms, String mac, String name);
    private native void stop();
}
