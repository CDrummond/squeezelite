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
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.media.session.MediaButtonReceiver;

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
    private int initialConnectionTimeout = 0;
    private int connectionLostTimeout = 0;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> terminateOnConnectionLostHandler;
    private String playerName;
    private MediaSessionCompat mediaSession;
    private MediaSessionCompat.Callback mediaSessionCallback;

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
        Utils.debug("");
        if (intent != null) {
            String action = intent.getAction();
            if (QUIT_INTENT.equals(action)) {
                stopForegroundService();
                return START_NOT_STICKY;
            }
        }

        SharedPreferences prefs = Prefs.get(this);

        if (!Prefs.hasBeenConfigured(prefs)) {
            Intent actIntent = new Intent(this, MainActivity.class);
            actIntent.putExtra(MainActivity.FROM_PLAYER_SERVICE_EXTRA, true);
            actIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(actIntent);
            stopForegroundService();
            return START_NOT_STICKY;
        }

        connectionLostTimeout = Utils.toInt(Prefs.get(this).getString(Prefs.CONNECTION_LOST_TIMEOUT_KEY, Prefs.DEFAULT_CONNECTION_LOST_TIMEOUT), 60);
        initialConnectionTimeout = Utils.toInt(Prefs.get(this).getString(Prefs.CONNECTION_LOST_TIMEOUT_KEY, Prefs.DEFAULT_CONNECTION_LOST_TIMEOUT), 300);
        if (null!=mediaSession) {
            MediaButtonReceiver.handleIntent(mediaSession, intent);
        }
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
            intent.putExtra(MainActivity.FROM_PLAYER_SERVICE_EXTRA, true);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
            SharedPreferences prefs = Prefs.get(this);
            String name = Utils.isEmpty(playerName) ? prefs.getString(Prefs.PLAYER_NAME_KEY, Prefs.DEFAULT_PLAYER_NAME) : playerName;
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

        Utils.debug("startForegroundService");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Utils.debug("startForegroundService");
            startForegroundService(new Intent(this, PlayerService.class));
        } else {
            Utils.debug("startService");
            startService(new Intent(this, PlayerService.class));
        }

        Utils.debug("ServiceCompat.startForeground");
        ServiceCompat.startForeground(this, MSG_ID, notification, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0);
    }

    private void stopForegroundService() {
        Utils.debug("");
        stopForeground(true);
        stopSelf();
        stopPlayer();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void startPlayer() {
        if (null==lib) {
            lib = new Library();
        }
        startTerminateTimer(initialConnectionTimeout);
        playerName = lib.startPlayer(this);
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
        if (!Utils.isEmpty(playerName)) {
            updateNotification();
        }

        mediaSession = new MediaSessionCompat(getApplicationContext(), "Squeezelite");
        if (mediaSessionCallback==null) {
            mediaSessionCallback=new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    Utils.debug("");
                    if (null!=lib) {
                        lib.playPause();
                    }
                }

                @Override
                public void onPause() {
                    Utils.debug("");
                    if (null!=lib) {
                        lib.playPause();
                    }
                }

                @Override
                public void onSkipToNext() {
                    if (null!=lib) {
                        lib.next();
                    }                }

                @Override
                public void onSkipToPrevious() {
                    if (null!=lib) {
                        lib.prev();
                    }
                }

                @Override
                public void onSeekTo(long pos) {
                    //sendCommand(new String[]{"time", Double.toString(pos/1000.0)});
                }

                public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                    Utils.debug("");
                    KeyEvent event = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (lib!=null && event!=null && 1==event.getAction()) {
                        Utils.debug("KeyCode:" + event.getKeyCode());
                        switch (event.getKeyCode()) {
                            case KeyEvent.KEYCODE_MEDIA_PLAY:
                                Utils.debug("Play");
                                lib.playPause();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                                Utils.debug("Pause");
                                lib.playPause();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                                Utils.debug("Play/pause");
                                lib.playPause();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                                Utils.debug("Prev");
                                lib.prev();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_NEXT:
                                Utils.debug("Next");
                                lib.next();
                                return true;
                            default:
                                break;
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent);
                }
            };
        }
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(mediaSessionCallback);
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
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
    }

    private void sendStatus(boolean running) {
        Intent intent = new Intent(STATUS_INTENT);
        intent.putExtra(RUNNING_KEY, running);
        sendBroadcast(intent);
    }

    public void poweredOff() {
        Utils.debug("");
        if (Prefs.get(this).getBoolean(Prefs.STOP_ON_POWER_OFF_KEY, Prefs.DEFAULT_STOP_ON_POWER_OFF)) {
            handler.post(this::stopForegroundService);
        }
    }

    public void connectionStateChanged(String ip) {
        boolean changed = (null==currentServerAddress && null!=ip) || (null!=currentServerAddress && null==ip) || (null!=currentServerAddress && !currentServerAddress.equals(ip));
        currentServerAddress = ip;
        if (changed) {
            handler.post(this::updateNotification);
        }
        if (Utils.isEmpty(ip)) {
            startTerminateTimer(connectionLostTimeout);
        } else {
            stopTerminateTimer();
        }
    }

    private void startTerminateTimer(int timeout) {
        Utils.debug("");
        stopTerminateTimer();
        if (timeout>0) {
            terminateOnConnectionLostHandler = executorService.schedule(this::stopForegroundService, timeout, TimeUnit.SECONDS);
        }
    }

    private void stopTerminateTimer() {
        Utils.debug("");
        if (null!= terminateOnConnectionLostHandler) {
            terminateOnConnectionLostHandler.cancel(false);
            terminateOnConnectionLostHandler = null;
        }
    }
}
