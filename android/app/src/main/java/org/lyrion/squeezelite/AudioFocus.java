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

/**
 * Holds audio focus whilst playing, as Android Auto only routes the media of the focus holder to
 * the car.
 */
public class AudioFocus {
    private final AudioManager audioManager;
    private final Library lib;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioFocusRequest request = null;
    private boolean haveFocus = false;
    private boolean pausedByLoss = false;

    private final AudioManager.OnAudioFocusChangeListener listener = this::onFocusChange;

    public AudioFocus(Context context, Library lib) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.lib = lib;
    }

    private void onFocusChange(int focusChange) {
        Utils.debug("focusChange:" + focusChange);
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                haveFocus = true;
                if (pausedByLoss) {
                    pausedByLoss = false;
                    lib.play();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                pausedByLoss = false;
                lib.pause();
                release();
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

    public void request() {
        pausedByLoss = false;
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
        release();
    }

    public void release() {
        pausedByLoss = false;
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
