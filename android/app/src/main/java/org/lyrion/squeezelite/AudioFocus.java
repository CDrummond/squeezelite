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
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Holds audio focus whilst playing, as Android Auto only routes the media of the focus holder to
 * the car. Android Auto also takes focus for itself as a session starts, which is reclaimed.
 */
public class AudioFocus {
    private static final long RECLAIM_DELAY = 1500;
    // A loss later than this into a car session is another app
    private static final long STARTUP_WINDOW = 60000;

    private final Context context;
    private final AudioManager audioManager;
    private final Library lib;
    private final CarConnection carConnection;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioFocusRequest request = null;
    private boolean haveFocus = false;
    private boolean pausedByLoss = false;
    private boolean reclaimed = false;

    private final AudioManager.OnAudioFocusChangeListener listener = this::onFocusChange;
    private final Runnable reclaimTask = this::reclaim;

    public AudioFocus(Context context, Library lib, CarConnection carConnection) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.lib = lib;
        this.carConnection = carConnection;
    }

    private void onFocusChange(int focusChange) {
        Utils.debug("focusChange:" + focusChange);
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                haveFocus = true;
                handler.removeCallbacks(reclaimTask);
                if (pausedByLoss) {
                    pausedByLoss = false;
                    lib.play();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                haveFocus = false;
                lib.pause();
                handler.removeCallbacks(reclaimTask);
                pausedByLoss = mayBeCarStarting();
                if (pausedByLoss) {
                    handler.postDelayed(reclaimTask, RECLAIM_DELAY);
                } else {
                    Utils.debug("Another player has taken over");
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // e.g. phone call, or navigation prompt
                pausedByLoss = true;
                lib.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Android (8+) lowers our volume itself
                break;
            default:
                break;
        }
    }

    private boolean mayBeCarStarting() {
        if (reclaimed) {
            return false;
        }
        if (null==carConnection) {
            return true;
        }
        long since = carConnection.connectedSince();
        // 0 is a session not seen yet, i.e. only now starting
        return 0==since || (SystemClock.elapsedRealtime()-since)<STARTUP_WINDOW;
    }

    private void reclaim() {
        if (!pausedByLoss) {
            return;
        }
        new Thread(() -> {
            int state = CarConnection.state(context);
            handler.post(() -> onCarConnectionState(state));
        }).start();
    }

    private void onCarConnectionState(int state) {
        if (!pausedByLoss) {
            return;
        }
        pausedByLoss = false;
        if (CarConnection.NOT_CONNECTED == state) {
            Utils.debug("Focus lost to another app");
            return;
        }
        Utils.debug("Focus lost as car connected (" + state + ") - reclaim");
        reclaimed = true;
        acquire();
        if (haveFocus) {
            lib.play();
        }
    }

    public void request() {
        pausedByLoss = false;
        handler.removeCallbacks(reclaimTask);
        acquire();
    }

    private void acquire() {
        if (haveFocus || null==audioManager) {
            return;
        }
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (null==request) {
                request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(false)
                        .setOnAudioFocusChangeListener(listener, handler)
                        .build();
            }
            result = audioManager.requestAudioFocus(request);
        } else {
            result = audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        haveFocus = AudioManager.AUDIOFOCUS_REQUEST_GRANTED==result;
        if (haveFocus) {
            Utils.debug("Audio focus granted");
        } else {
            Utils.warn("Audio focus request failed:" + result);
        }
    }

    public void abandon() {
        if (pausedByLoss) {
            // Our own pause, on losing focus - keep the request so focus can be regained
            return;
        }
        releaseFocus();
    }

    public void release() {
        pausedByLoss = false;
        handler.removeCallbacks(reclaimTask);
        releaseFocus();
    }

    private void releaseFocus() {
        if (!haveFocus || null==audioManager) {
            return;
        }
        haveFocus = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (null!=request) {
                audioManager.abandonAudioFocusRequest(request);
            }
        } else {
            audioManager.abandonAudioFocus(listener);
        }
        Utils.debug("Audio focus abandoned");
    }
}
