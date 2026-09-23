/*
 *  Squeezelite Android
 *
 *  (c) Craig Drummond 2025-2026 <craig.p.drummond@gmail.com>
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
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Watches whether Android Auto is projecting, via its content provider. The end of a session is
 * not reliably visible as a Bluetooth disconnection.
 */
public class CarConnection {
    // See androidx.car.app.connection.CarConnection
    private static final Uri URI = Uri.parse("content://androidx.car.app.connection");
    private static final String STATE = "CarConnectionState";
    public static final int NOT_CONNECTED = 0;
    private static final long CONFIRM_DELAY = 3000;
    // In case the provider does not notify a change
    private static final long POLL_INTERVAL = 30000;

    private final Context context;
    private final Runnable onSessionEnded;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable confirmTask = this::confirm;
    private final Runnable pollTask = this::poll;
    private ContentObserver observer = null;
    private boolean seenConnected = false;
    // 0 if no session is running
    private long connectedSince = 0;

    // Not for the main thread
    public static int state(Context context) {
        try (Cursor cursor = context.getContentResolver().query(URI, new String[]{STATE}, null, null, null)) {
            if (null == cursor) {
                return NOT_CONNECTED;
            }
            int col = cursor.getColumnIndex(STATE);
            return col < 0 || !cursor.moveToNext() ? NOT_CONNECTED : cursor.getInt(col);
        } catch (Exception e) {
            // Android Auto not installed, or too old
            Utils.debug("Car connection state unavailable");
            return NOT_CONNECTED;
        }
    }

    public long connectedSince() {
        return connectedSince;
    }

    public CarConnection(Context context, Runnable onSessionEnded) {
        this.context = context.getApplicationContext();
        this.onSessionEnded = onSessionEnded;
    }

    public void start() {
        if (null!=observer) {
            return;
        }
        observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                read(false);
            }
        };
        try {
            context.getContentResolver().registerContentObserver(URI, false, observer);
        } catch (Exception e) {
            Utils.error("Failed to watch car connection", e);
            observer = null;
            return;
        }
        read(false);
    }

    public void release() {
        handler.removeCallbacks(confirmTask);
        handler.removeCallbacks(pollTask);
        if (null!=observer) {
            try {
                context.getContentResolver().unregisterContentObserver(observer);
            } catch (Exception e) {
                Utils.error("Failed to stop watching car connection", e);
            }
            observer = null;
        }
    }

    private void read(boolean confirming) {
        new Thread(() -> {
            int state = state(context);
            handler.post(() -> onState(state, confirming));
        }).start();
    }

    private void onState(int state, boolean confirming) {
        if (null==observer) {
            // Released whilst the query was running
            return;
        }
        Utils.debug("state:"+state+", seenConnected:"+seenConnected+", confirming:"+confirming);
        if (NOT_CONNECTED!=state) {
            if (!seenConnected) {
                seenConnected = true;
                connectedSince = SystemClock.elapsedRealtime();
            }
            handler.removeCallbacks(confirmTask);
            schedulePoll();
            return;
        }
        if (!seenConnected) {
            schedulePoll();
            return;
        }
        if (confirming) {
            Utils.info("Android Auto session ended");
            seenConnected = false;
            connectedSince = 0;
            handler.removeCallbacks(pollTask);
            onSessionEnded.run();
            return;
        }
        handler.removeCallbacks(confirmTask);
        handler.postDelayed(confirmTask, CONFIRM_DELAY);
    }

    private void schedulePoll() {
        handler.removeCallbacks(pollTask);
        handler.postDelayed(pollTask, POLL_INTERVAL);
    }

    private void confirm() {
        read(true);
    }

    private void poll() {
        read(false);
    }
}
