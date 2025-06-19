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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PlayerService extends Service {
    // How long after losing connection to server should we stop player?
    public static final String STATUS_INTENT = PlayerService.class.getCanonicalName()+".STATUS";
    private static final String QUIT_INTENT = PlayerService.class.getCanonicalName() + ".QUIT";
    public static final String RUNNING_KEY = "running";
    public static final String NOTIFICATION_CHANNEL_ID = "squeezelite_service";
    private static final int MSG_ID = 1;

    private String currentServerAddress = null;
    private NotificationCompat.Builder notificationBuilder;
    private NotificationManagerCompat notificationManager;
    private final Handler handler;
    private PowerManager.WakeLock wakeLock = null;
    private Library lib = null;
    private int terminateTimeout = 0;
    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    ScheduledFuture<?> terminateHandler;
    public PlayerService() {
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Utils.debug("");
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.debug("");
        startForegroundService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Utils.debug("");
        stopForegroundService();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (QUIT_INTENT.equals(action)) {
                stopForegroundService();
            }
            return START_STICKY;
        }

        SharedPreferences prefs = Prefs.get(this);

        if (!prefs.contains(Prefs.SERVER_KEY)) {
            Intent actIntent = new Intent(this, SettingsActivity.class);
            actIntent.putExtra(MainActivity.FROM_PLAYER_SERVICE, true);
            startActivity(actIntent);
            stopForegroundService();
            return START_NOT_STICKY;
        }
        terminateTimeout = Utils.toInt(Prefs.get(this).getString(Prefs.TERMINATEL_TIMER_KEY, Prefs.DEFAULT_TERMINATE_TIMER_KEY), 60);
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    private void startForegroundService() {
        Utils.debug("");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        } else {
            notificationBuilder = new NotificationCompat.Builder(this);
        }
        createNotification();
        startPlayer();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        Utils.debug("");
        notificationManager = NotificationManagerCompat.from(this);
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getApplicationContext().getResources().getString(R.string.main_notification), NotificationManager.IMPORTANCE_LOW);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        chan.setShowBadge(false);
        chan.enableLights(false);
        chan.enableVibration(false);
        chan.setSound(null, null);
        notificationManager.createNotificationChannel(chan);
        notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
    }

    @SuppressLint("MissingPermission")
    private synchronized Notification updateNotification() {
        Utils.debug("");
        if (!Utils.notificationAllowed(this, NOTIFICATION_CHANNEL_ID)) {
            return null;
        }
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(MainActivity.FROM_PLAYER_SERVICE, true);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
            SharedPreferences prefs = Prefs.get(this);
            String name = prefs.getString(Prefs.PLAYER_NAME_KEY, Prefs.DEFAULT_PLAYER_NAME);
            Intent quitIntent = new Intent(this, PlayerService.class);
            quitIntent.setAction(QUIT_INTENT);

            notificationBuilder
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSmallIcon(R.drawable.ic_mono_icon)
                    .setContentTitle(name + (Utils.isEmpty(currentServerAddress) ? "" : (" (" + currentServerAddress +")")))
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setContentIntent(pendingIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setVibrate(null)
                    .setSound(null)
                    .setShowWhen(false)
                    .setChannelId(NOTIFICATION_CHANNEL_ID);
            notificationBuilder.clearActions();
            notificationBuilder.addAction(new NotificationCompat.Action(R.drawable.ic_action_quit, getString(R.string.stop_player), PendingIntent.getService(this, 0, quitIntent, PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)));
            Notification notification = notificationBuilder.build();
            Utils.debug("Build notification.");
            notificationManager.notify(MSG_ID, notification);
            return notification;
        } catch (Exception e) {
            Utils.error("Failed to create control notification", e);
        }
        return null;
    }

    private void createNotification() {
        Utils.debug("");
        Notification notification = updateNotification();
        if (null==notification) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Utils.debug("startForegroundService");
            startForegroundService(new Intent(this, PlayerService.class));
        } else {
            Utils.debug("startService");
            startService(new Intent(this, PlayerService.class));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Utils.debug("ServiceCompat.startForeground");
            ServiceCompat.startForeground(this, MSG_ID, notification, Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0);
        }
    }

    private void stopForegroundService() {
        Utils.debug("");
        stopForeground(true);
        stopSelf();
        stopPlayer();
    }

    private void startPlayer() {
        if (null==lib) {
            lib = new Library();
        }
        lib.startPlayer(this);
        if (Prefs.get(this).getBoolean(Prefs.USE_WAKE_LOCK_KEY, Prefs.DEFAULT_USE_WAKE_LOCK)) {
            if (null==wakeLock) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Squeezelite:player");
            }
            if (null!=wakeLock) {
                wakeLock.acquire();
            }
        }
        sendStatus(true);
    }

    private void stopPlayer() {
        if (null==lib) {
            return;
        }
        if (null!=wakeLock) {
            wakeLock.release();
            wakeLock = null;
        }
        sendStatus(false);
        stopTerminateTimer();
        lib.stopPlayer(this);
        System.exit(0);
    }

    private void sendStatus(boolean running) {
        Intent intent = new Intent(STATUS_INTENT);
        intent.putExtra(RUNNING_KEY, running);
        sendBroadcast(intent);
    }

    public void connectionStateChanged(String ip) {
        boolean changed = (null==currentServerAddress && null!=ip) || (null!=currentServerAddress && null==ip) || (null!=currentServerAddress && !currentServerAddress.equals(ip));
        currentServerAddress = ip;
        if (changed) {
            handler.post(this::updateNotification);
        }
        if (Utils.isEmpty(ip)) {
            startTerminateTimer();
        } else {
            stopTerminateTimer();
        }
    }

    private void startTerminateTimer() {
        Utils.debug("");
        if (null==terminateHandler && terminateTimeout>0) {
            stopTerminateTimer();
            terminateHandler = executorService.schedule(this::stopForegroundService, terminateTimeout, TimeUnit.SECONDS);
        }
    }

    private void stopTerminateTimer() {
        Utils.debug("");
        if (null!=terminateHandler) {
            terminateHandler.cancel(false);
            terminateHandler = null;
        }
    }
}
