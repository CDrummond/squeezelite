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
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.Keep;

import org.json.JSONObject;

public class Library {
    private static final String[] PREV_COMMAND = {"button", "jump_rew"};
    private static final String[] TOGGLE_PLAY_PAUSE_COMMAND = {"pause"};
    private static final String[] NEXT_COMMAND = {"playlist", "index", "+1"};

    // Timeout after which Squeezelite will close audio stream
    static final int STREAM_IDLE_TIMEOUT = 2000;
    static long MIN_LMS_VOLUME_UPDATE_TIME = 750;
    // received volume values for 0..100
    static int[] LMS_VOLS = new int[]{0,16,18,22,26,31,36,43,51,61,72,85,101,120,142,168,200,237,281,333,395,468,555,658,781,926,980,1037,1098,1162,1230,1302,1378,1458,1543,1634,1729,1830,1937,2050,2048,2304,2304,2560,2816,2816,3072,3328,3328,3584,3840,4096,4352,4608,4864,5120,5376,5632,6144,6400,6656,7168,7680,7936,8448,8960,9472,9984,10752,11264,12032,12544,13312,14080,14848,15872,16640,17664,18688,19968,20992,22272,23552,24832,26368,27904,29696,31232,33024,35072,37120,39424,41728,44032,46592,49408,52224,55296,58624,61952,65536};
    static final int UNKNOWN_VOL = -100000;
    static final int LOG_ERROR = 0;
    static final int LOG_WARN = 1;
    static final int LOG_lINFO = 2;
    static final int LOG_DEBUG = 3;
    static final int LOG_SDEBUG= 4;

    static final int VOL_SEP = 0;
    static final int VOL_DEV = 1;
    static final int VOL_SYNC = 2;

    private Thread thread;
    private boolean loaded;

    private boolean initialLmsVolSeen = false;
    private int androidVolume = UNKNOWN_VOL;
    private int androidMaxVolume = 100;
    private int lmsVolumeReceived = UNKNOWN_VOL;
    private int lmsVolumeSent = UNKNOWN_VOL;
    private long lmsVolumeSendTime;
    private int volumeControl = VOL_SEP;
    private int maxBitrate = 0;
    private PlayerService service;
    private VolumeChangeObserver observer;
    private AudioManager audioManager;
    private JsonRpc jsonRpc;
    private String ipAddress;

    private class VolumeChangeObserver extends ContentObserver {
        public VolumeChangeObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onChange(boolean selfChange) {
            volumeChanged();
        }
    }

    public Library() {
        try {
            System.loadLibrary("squeezelite");
            loaded = true;
        } catch (Exception e) {
            loaded = false;
            Utils.error("Failed to load library", e);
        }
    }

    public synchronized String startPlayer(PlayerService service) {
        if (null!=thread || !loaded) {
            return null;
        }
        Utils.info("");

        this.service = service;
        SharedPreferences prefs = Prefs.get(service);
        ServerDiscovery.Server server = new ServerDiscovery.Server(prefs.getString(Prefs.SERVER_KEY, ""));
        String vc = prefs.getString(Prefs.VOLUME_CONTROL_KEY, Prefs.DEFAULT_VOLUME_CONTROL);
        boolean openSLES = Prefs.OUTPUT_LIB_OPENSLES.equals(prefs.getString(Prefs.OUTPUT_LIB_KEY, Prefs.DEFAULT_OUTPUT_LIB));
        volumeControl = Prefs.VOLUME_CONTROL_SEPARATE.equals(vc)
                ? VOL_SEP
                : Prefs.VOLUME_CONTROL_DEVICE.equals(vc)
                ? VOL_DEV
                : VOL_SYNC;
        int streamBuffer = Integer.parseInt(prefs.getString(Prefs.STREAM_BUFFER_KEY, Prefs.DEFAULT_STREAM_BUFFER));

        maxBitrate = Integer.parseInt(prefs.getString(Prefs.MAX_BITRATE_KEY, Prefs.DEFAULT_MAX_BITRATE));
        int maxBitrateWhen = Integer.parseInt(prefs.getString(Prefs.MAX_BITRATE_WHEN_KEY, Prefs.DEFAULT_MAX_BITRATE_WHEN));
        Utils.debug("maxBitrate:"+maxBitrate+", maxBitrateWhen:"+maxBitrateWhen);

        Utils.debug("Check network type");
        ConnectivityManager connMgr = (ConnectivityManager) service.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connMgr.getActiveNetworkInfo();
        boolean metered = connMgr.isActiveNetworkMetered();
        boolean cellular = activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE;
        Utils.debug("Metered:"+metered+" Cellular:"+cellular);

        if (maxBitrate>0 && maxBitrateWhen!=Prefs.MAX_BITRATE_ALWAYS &&
             (maxBitrateWhen==Prefs.MAX_BITRATE_WHEN_CELLULAR && !cellular) ||
             (maxBitrateWhen==Prefs.MAX_BITRATE_WHEN_METERED && !metered) ||
             (maxBitrateWhen==Prefs.MAX_BITRATE_WHEN_EITHER && !metered && !cellular)) {
            maxBitrate = 0;
            Utils.debug("Not setting max bit rate");
        }

        String mac = prefs.getString(Prefs.PLAYER_MAC_KEY, Prefs.DEFAULT_PLAYER_MAC);
        String name = prefs.getString(Prefs.PLAYER_NAME_KEY, Prefs.DEFAULT_PLAYER_NAME);
        boolean usingBtName = false;
        // Use mac+name of connected BT device, if configured to do so
        if (prefs.getBoolean(Prefs.USE_BT_ID_KEY, false)) {
            Utils.BtDevice bt = Utils.getConnectedDevice(service);
            Utils.debug("Connected BT device name:" + (null==bt ? "<>" : bt.name)+", mac:" + (null==bt ? "<>" : bt.mac));
            if (null!=bt) {
                name = bt.name;
                mac = bt.mac;
                usingBtName = true;
                Utils.debug("BT mac:" + mac + ", name:" + name);
            }
        }

        initialLmsVolSeen = false;
        ipAddress = server.address();
        if (null==audioManager) {
            audioManager = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
            androidMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        if (VOL_SYNC==volumeControl) {
            androidVolume = UNKNOWN_VOL;
            jsonRpc = new JsonRpc(service, server, mac);
            observer = new VolumeChangeObserver();
            service.getApplicationContext().getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, observer);
        }
        int volumeToRestore = prefs.getBoolean(Prefs.RESTORE_VOLUME_KEY, Prefs.DEFAULT_RESTORE_VOLUME) ? (int)prefs.getLong(Prefs.VOLUME_KEY, -1) : -1;
        if (volumeToRestore>=0) {
            Utils.info("Restore volume:" + volumeToRestore);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToRestore, 0);
        }

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> Utils.error("Unhandled exception", throwable));
        Utils.debug("Start mac:" + mac + ", name: " + name);

        final String macToUse = mac;
        final String nameToUse = name;
        thread = new Thread(() -> start(ipAddress,
              macToUse,
              nameToUse,
              STREAM_IDLE_TIMEOUT,
              VOL_SEP==volumeControl ? 0 : 1,
              LOG_ERROR,
              openSLES ? 1 : 0,
              cellular || metered ? 1 : 0,
              streamBuffer));
        thread.start();
        return usingBtName ? name : null;
    }

    public synchronized void stopPlayer(Context context) {
        if (null == thread || !loaded) {
            return;
        }
        service = null;
        Utils.info("");

        SharedPreferences prefs = Prefs.get(context);
        androidVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (androidVolume>=0) {
            Utils.info("Store volume:" + androidVolume);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(Prefs.VOLUME_KEY, androidVolume);
            editor.apply();
        }

        if (null != observer) {
            context.getApplicationContext().getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }
        stop();
        try {
            // Allow C code a little while to stop...
            thread.join(500);
        } catch (InterruptedException e) {
            Utils.error("InterruptedException joining player thread", e);
        }
        try {
            thread.interrupt();
        } catch (Exception e) {
            Utils.error("Exception interrupting player thread", e);
        }
        thread = null;
        if (null != jsonRpc) {
            jsonRpc.sendMessage(new String[]{"client", "forget"}, response -> {
                Utils.debug("Handle 'forget' resp");
                System.exit(0);
            });
            jsonRpc = null;
        } else {
            System.exit(0);
        }
    }

    private synchronized void volumeChanged() {
        if (!initialLmsVolSeen) {
            return;
        }
        int vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        Utils.debug("vol:"+vol+", androidVolume:"+androidVolume+", androidMaxVolume:"+androidMaxVolume);
        if (vol!=androidVolume) {
            androidVolume = vol;
            // Convert to 0..100
            int vol100 = (int)Math.ceil((vol*100.0f)/androidMaxVolume);
            if (null!=jsonRpc && lmsVolumeSent !=vol100) {
                lmsVolumeSent = vol100;
                lmsVolumeSendTime = SystemClock.elapsedRealtime();
                jsonRpc.sendMessage( new String[]{"mixer", "volume", ""+vol100});
            }
        }
    }

    private static float mapToPercent(int vol) {
        for (int i=0; i<LMS_VOLS.length; ++i) {
            if (LMS_VOLS[i]==vol || (i>0 && LMS_VOLS[i-1]<vol && (i==LMS_VOLS.length-1 || LMS_VOLS[i+1]>vol))) {
                return i/100.0f;
            }
        }
        return 0.0f;
    }

    @Keep
    public synchronized void volumeChanged(int left, int right) {
        // We send LMS our volume, and that will cause it to send an update - which can cause
        // volume 'wiggles'. Therefore, if we receive an update too soon after sending a change
        // then ignore it.
        if (lmsVolumeSendTime>0 && (SystemClock.elapsedRealtime()-lmsVolumeSendTime)<MIN_LMS_VOLUME_UPDATE_TIME) {
            return;
        }
        // Volume FROM LMS...
        if (VOL_SYNC==volumeControl && null!=audioManager) {
            int vol = (left + right)/2;
            if (vol==lmsVolumeReceived) {
                return;
            }
            lmsVolumeReceived = vol;
            int aVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            Utils.debug("left:"+left+", right:"+right+", initialLmsVolSeen:"+initialLmsVolSeen+", vol:"+vol+", aVol:"+aVol+", initialLmsVolSeen:"+initialLmsVolSeen);
            // If android media volume<=0 then use LMS's, even for initial...
            if (initialLmsVolSeen || aVol<=0) {
                float pc = mapToPercent(vol);
                aVol = (int)Math.ceil(pc*androidMaxVolume);
                Utils.debug("aVol:"+aVol+", androidVolume:"+androidVolume);
                if (aVol!=androidVolume) {
                    androidVolume = aVol;
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, aVol, 0);
                }
            } else {
                // C code has connected to LMS, LMS indicates initial volume, but override with
                // device's current volume...
                Utils.debug("First, aVol:"+aVol);
                androidVolume = UNKNOWN_VOL;
                volumeChanged();
            }
            initialLmsVolSeen = true;
        }
    }

    @Keep
    public synchronized void connectionStateChanged(String address) {
        Utils.debug("address:"+address+", prev:"+ipAddress);
        if (null!=service) {
            service.connectionStateChanged(address);
        }
        if (Utils.isEmpty(address)) {
            initialLmsVolSeen = false;
            lmsVolumeReceived = UNKNOWN_VOL;
            lmsVolumeSent = UNKNOWN_VOL;
            lmsVolumeSendTime = 0;
        } else {
            if (null!=jsonRpc) {
                jsonRpc.setAddress(address);
            }
            ipAddress = address;

            Utils.debug("maxBitrate:"+maxBitrate);
            if (maxBitrate >= 0 && null!=jsonRpc) {
                Utils.debug("Setting max bit rate to: " + maxBitrate);
                jsonRpc.sendMessage(new String[]{"playerpref", "maxBitrate", String.valueOf(maxBitrate)});
            }
        }
    }

    boolean isInitialPower = true;
    @Keep
    public synchronized void outputChanged(int spdif, int dac) {
        Utils.debug("spdif:"+spdif+", dac:"+dac+", isInitialPower:"+isInitialPower);
        if (0 == spdif && 0 == dac) {
            if (isInitialPower) {
                if (null!=jsonRpc) {
                    Utils.debug("Power on");
                    jsonRpc.sendMessage(new String[]{"power", "1"});
                }
            } else {
                service.poweredOff();
            }
        }
        isInitialPower = false;
    }

    public void prev() {
        sendCommand(PREV_COMMAND);
    }

    public void playPause() {
        sendCommand(TOGGLE_PLAY_PAUSE_COMMAND);
    }

    public void next() {
        sendCommand(NEXT_COMMAND);
    }

    public void sendCommand(String[] cmd) {
        if (null!=jsonRpc) {
            jsonRpc.sendMessage(cmd);
        }
    }

    private native void start(String lms, String mac, String name, int idleTimeout, int fixedVolume, int logging, int useOpenSLES, int mobileNetwork, int streamBuffer);
    private native void stop();
}
