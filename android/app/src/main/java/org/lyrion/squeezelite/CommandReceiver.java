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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

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
            context.startForegroundService(new Intent(context, PlayerService.class));
        } else if (act.equals(STOP)) {
            context.stopService(new Intent(context, PlayerService.class));
        }
    }
}
