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

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import java.util.Set;

/**
 * Start/stop service via:
 * adb shell am broadcast -n org.lyrion.squeezelite/.CommandReceiver -a org.lyrion.squeezelite.START
 * adb shell am broadcast -n org.lyrion.squeezelite/.CommandReceiver -a org.lyrion.squeezelite.STOP
 */
public class CommandReceiver extends BroadcastReceiver {
    private static final String START = "org.lyrion.squeezelite.START";
    private static final String STOP = "org.lyrion.squeezelite.STOP";

    @Override
    public void onReceive(Context context, Intent intent) {
        String act = intent.getAction();
        if (null==act) {
            return;
        }
        Utils.debug(act);
        if (act.equals(START) ||
                (act.equals(Intent.ACTION_BOOT_COMPLETED) && Prefs.get(context).getBoolean(Prefs.START_ON_BOOT_KEY, Prefs.DEFAULT_START_ON_BOOT))) {
            startOnBoot(context);
        } else if (act.equals(STOP)) {
            context.stopService(new Intent(context, PlayerService.class));
        } else if (act.equals(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
            handleBtIntent(context, intent);
        }
    }

    private void handleBtIntent(Context context, Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) {
            return;
        }
        String macAddress = device.getAddress();

        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
        if (BluetoothProfile.STATE_CONNECTED != state && BluetoothProfile.STATE_DISCONNECTED != state) {
            Utils.debug("Ignoring A2DP state " + state);
            return;
        }
        boolean connected = BluetoothProfile.STATE_CONNECTED == state;

        if (!connected && isOtherConfiguredDeviceConnected(context, macAddress)) {
            Utils.debug("Another configured BT device is still connected");
            return;
        }

        if (!connected && Prefs.get(context).getBoolean(Prefs.AUTOSTOP_BT_KEY, false)) {
            if (Utils.isPlayerRunning(context)) {
                context.stopService(new Intent(context, PlayerService.class));
            }
            return;
        }
        if (!Prefs.get(context).getBoolean(Prefs.AUTOSTART_BT_KEY, false)) {
            Utils.debug("Not configured for BT auto-start");
            return;
        }

        Set<String> macs = Prefs.get(context).getStringSet(Prefs.BT_MAC_ADDRESSES_KEY, null);
        if (null==macs || macs.isEmpty()) {
            Utils.debug("No BT MACs configured");
            return;
        }

        if (!macs.contains(macAddress)) {
            Utils.debug("Not a configured BT MAC");
            return;
        }

        if (Utils.isPlayerRunning(context)) {
            context.stopService(new Intent(context, PlayerService.class));
        }

        if (connected) {
            startService(context);
        }
    }

    private boolean isOtherConfiguredDeviceConnected(Context context, String macAddress) {
        Set<String> macs = Prefs.get(context).getStringSet(Prefs.BT_MAC_ADDRESSES_KEY, null);
        return null!=macs && !macs.isEmpty() && null!=Utils.getConnectedDevice(context, macs, macAddress);
    }

    private void startOnBoot(Context context) {
        int delay = Utils.toInt(Prefs.get(context).getString(Prefs.START_ON_BOOT_DELAY_KEY, Prefs.DEFAULT_START_ON_BOOT_DELAY), 0);
        if (delay>0 && delay<=60) {
            Utils.debug("Sleeping for " + delay + " seconds");
            try {
                Thread.sleep(delay*1000);
            } catch (InterruptedException ignored) {
            }
        }
        startService(context);
    }

    private void startService(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(new Intent(context, PlayerService.class));
        } else {
            context.startService(new Intent(context, PlayerService.class));
        }
    }
}
