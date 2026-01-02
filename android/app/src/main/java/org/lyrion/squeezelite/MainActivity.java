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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import io.github.muddz.styleabletoast.StyleableToast;

public class MainActivity extends AppCompatActivity {
    public static final String FROM_PLAYER_SERVICE_EXTRA = "from-player-service";
    public static final String START_PLAYER_EXTRA = "start-player";
    public static final int PERMISSION_POST_NOTIFICATIONS = 1;

    private Button settingsButton;
    private Button controlButton;
    PlayerReceiver playerReceiver;

    private class PlayerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PlayerService.STATUS_INTENT.equals(intent.getAction())) {
                StyleableToast.makeText(context, getResources().getString(Utils.isPlayerRunning(context) ? R.string.player_started : R.string.player_stopped), Toast.LENGTH_SHORT, R.style.toast).show();
                controlWidgets();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.info("");
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = Prefs.get(this);
        if (prefs.getBoolean(Prefs.START_SERVICE_KEY, Prefs.DEFAULT_START_SERVICE) && canStartPlayer()) {
            Intent intent = getIntent();
            boolean askedToStartPlayer = null!=intent && intent.getBooleanExtra(START_PLAYER_EXTRA, false);
            boolean alreadyRunning = Utils.isPlayerRunning(this);

            if (askedToStartPlayer && alreadyRunning) {
                Utils.debug("Asked to start player, but already running..");
                finish();
                return;
            }

            if (!alreadyRunning &&
                 (
                   askedToStartPlayer ||
                   (Prefs.hasBeenConfigured(prefs) && (null==intent || !intent.getBooleanExtra(FROM_PLAYER_SERVICE_EXTRA, false)))
                 )
               ) {
                Utils.debug("Start player from launcher/app...");
                startPlayer();
                finish();
                return;
            }
        }

        setContentView(R.layout.activity_main);
        settingsButton = findViewById(R.id.settings);
        controlButton = findViewById(R.id.control);
        controlWidgets();

        settingsButton.setOnClickListener(view -> {
            Utils.debug("Navigate to settings");
            startActivity(new Intent(this, SettingsActivity.class));
        });

        controlButton.setOnClickListener(view -> {
            if (Utils.isPlayerRunning(this)) {
                stopPlayer();
            } else {
                if (!canStartPlayer()) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playerReceiver, new IntentFilter(PlayerService.STATUS_INTENT), RECEIVER_EXPORTED);
        } else {
            registerReceiver(playerReceiver, new IntentFilter(PlayerService.STATUS_INTENT));
        }
        controlWidgets();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_POST_NOTIFICATIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startPlayer();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void startPlayer() {
        Utils.debug("Start player service");
        startService(new Intent(MainActivity.this, PlayerService.class));
    }

    private void stopPlayer() {
        Utils.debug("Stop player service");
        stopService(new Intent(MainActivity.this, PlayerService.class));
    }

    private void controlWidgets() {
        SharedPreferences prefs = Prefs.get(this);
        boolean configured = Prefs.hasBeenConfigured(prefs);
        boolean running = Utils.isPlayerRunning(this);
        float alpha = running ? 0.5f : 1.0f;
        controlButton.setText(running ? R.string.stop_player : R.string.start_player);
        controlButton.setAlpha(configured ? 1.0f : 0.5f);
        controlButton.setEnabled(configured);
        settingsButton.setAlpha(alpha);
        settingsButton.setEnabled(!running);
    }

    private boolean canStartPlayer() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Utils.notificationAllowed(this, PlayerService.NOTIFICATION_CHANNEL_ID);
    }
}
