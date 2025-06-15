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
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {
    public static final int PERMISSION_POST_NOTIFICATIONS = 1;

    private Button settingsButton;
    private Button controlButton;
    PlayerReceiver playerReceiver;

    private class PlayerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PlayerService.STATUS_INTENT.equals(intent.getAction())) {
                controlWidgets();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.info("");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settingsButton = findViewById(R.id.settings);
        controlButton = findViewById(R.id.control);
        controlWidgets();

        settingsButton.setOnClickListener(view -> {
            Utils.debug("Navigate to settings");
            startActivity(new Intent(this, SettingsActivity.class));
        });

        controlButton.setOnClickListener(view -> {
            if (isPlayerRunning()) {
                stopPlayer();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Utils.notificationAllowed(this, PlayerService.NOTIFICATION_CHANNEL_ID)) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_POST_NOTIFICATIONS);
                } else {
                    startPlayer();
                }
            }
        });
        playerReceiver = new PlayerReceiver();
    }

    @Override
    protected void onPause() {
        Utils.info("");
        super.onPause();
        unregisterReceiver(playerReceiver);
    }

    @Override
    protected void onResume() {
        Utils.info("");
        super.onResume();
        registerReceiver(playerReceiver, new IntentFilter(PlayerService.STATUS_INTENT));
        controlWidgets();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_POST_NOTIFICATIONS) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                startPlayer();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean isPlayerRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (service.service.getClassName().equals(PlayerService.class.getCanonicalName())) {
                return true;
            }
        }
        return false;
    }

    private void startPlayer() {
        Utils.debug("Start player service");
        Intent intent = new Intent(MainActivity.this, PlayerService.class);
        startService(intent);
    }

    private void stopPlayer() {
        Utils.debug("Stop player service");
        stopService(new Intent(MainActivity.this, PlayerService.class));
    }

    private void controlWidgets() {
        boolean configured = Prefs.get(this).contains(Prefs.SERVER_KEY);
        boolean running = isPlayerRunning();
        float alpha = running ? 0.5f : 1.0f;
        controlButton.setText(running ? R.string.stop_player : R.string.start_player);
        controlButton.setAlpha(configured ? 1.0f : 0.5f);
        controlButton.setEnabled(configured);
        settingsButton.setAlpha(alpha);
        settingsButton.setEnabled(!running);
    }
}
