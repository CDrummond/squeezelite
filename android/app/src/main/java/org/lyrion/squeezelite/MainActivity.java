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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.List;

import io.github.muddz.styleabletoast.StyleableToast;

public class MainActivity extends AppCompatActivity {
    public static final int PERMISSION_POST_NOTIFICATIONS = 1;

    private TextView serverTitle;
    private EditText serverText;
    private TextView playerNameTitle;
    private EditText playerNameText;
    private Button discoverButton;
    private Button controlButton;
    PlayerReceiver playerReceiver;

    private class Discovery extends ServerDiscovery {
        Discovery(Context context) {
            super(context, false);
        }

        public void discoveryFinished(List<Server> servers) {
            Utils.debug("Discovery finished");
            if (servers.isEmpty()) {
                Utils.debug("No server found");
                StyleableToast.makeText(context, getResources().getString(R.string.no_servers), Toast.LENGTH_SHORT, R.style.toast).show();
            } else {
                Utils.debug("Discovered server");
                String current = serverText.getText().toString().trim();
                for (Server server: servers) {
                    if (!current.equals(server.address())) {
                        StyleableToast.makeText(context, getResources().getString(R.string.server_discovered) + "\n\n" + servers.get(0).describe(), Toast.LENGTH_SHORT, R.style.toast).show();
                        serverText.setText(server.address());
                        break;
                    }
                }
            }
        }
    }

    private class PlayerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PlayerService.STATUS_INTENT.equals(intent.getAction())) {
                controlWidgets();
            }
        }
    }

    private void discoverServer() {
        Utils.debug("");
        runOnUiThread(() -> {
            StyleableToast.makeText(getBaseContext(), getResources().getString(R.string.discovering_server), Toast.LENGTH_SHORT, R.style.toast).show();
        });
        Discovery discovery = new Discovery(getApplicationContext());
        discovery.discover();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.info("");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Prefs prefs = new Prefs(this);
        serverTitle = findViewById(R.id.lms_server_title);
        serverText = findViewById(R.id.lms_server);
        playerNameTitle = findViewById(R.id.player_name_title);
        playerNameText = findViewById(R.id.player_name);
        discoverButton = findViewById(R.id.discover);
        controlButton = findViewById(R.id.control);
        serverText.setText(prefs.getLmsAddress());
        playerNameText.setText(prefs.getPlayerName());
        controlWidgets();

        discoverButton.setOnClickListener(view -> {
            discoverServer();
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
        saveSettings();
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
        switch (requestCode) {
            case PERMISSION_POST_NOTIFICATIONS: {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    startPlayer();
                }
                return;
            }
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
        saveSettings();
        startService(intent);
    }

    private void stopPlayer() {
        Utils.debug("Stop player service");
        stopService(new Intent(MainActivity.this, PlayerService.class));
    }

    private void controlWidgets() {
        boolean running = isPlayerRunning();
        float alpha = running ? 0.5f : 1.0f;
        controlButton.setText(running ? R.string.stop_player : R.string.start_player);
        serverTitle.setAlpha(alpha);
        serverText.setAlpha(alpha);
        serverText.setEnabled(!running);
        discoverButton.setAlpha(alpha);
        discoverButton.setEnabled(!running);
        playerNameTitle.setAlpha(alpha);
        playerNameText.setAlpha(alpha);
        playerNameText.setEnabled(!running);
    }

    private void saveSettings() {
        Prefs prefs = new Prefs(this);
        prefs.setLmsAddress(serverText.getText().toString().trim());
        prefs.setPlayerName(playerNameText.getText().toString().trim());
    }
}
