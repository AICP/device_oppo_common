/*
 * Copyright (C) 2014 Slimroms
 * Copyright (C) 2019 Android Ice Cold Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.slim.device;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

import android.service.notification.ZenModeConfig;

import com.slim.device.settings.ScreenOffGesture;

import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.gzosp.ActionConstants;
import com.android.internal.util.gzosp.Action;

import java.util.HashSet;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final int GESTURE_REQUEST = 1;

    public static final String SETTING_NOTIF_SLIDER_UP =
            "device_oppo_common_notification_slider_up1";
    public static final String SETTING_NOTIF_SLIDER_MIDDLE =
            "device_oppo_common_notification_slider_middle1";
    public static final String SETTING_NOTIF_SLIDER_BOTTOM =
            "device_oppo_common_notification_slider_bottom1";
    public static final String SETTING_NOTIF_SLIDER_HAPTIC_FEEDBACK =
            "device_oppo_common_notification_slider_haptic_feedback";

    // Supported scancodes
    private static final int GESTURE_CIRCLE_SCANCODE = 250;
    private static final int GESTURE_SWIPE_DOWN_SCANCODE = 251;
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int GESTURE_LTR_SCANCODE = 253;
    private static final int GESTURE_GTR_SCANCODE = 254;
    private static final int GESTURE_V_UP_SCANCODE = 255;
    // Slider
    private static final int MODE_TOTAL_SILENCE = 600;
    private static final int MODE_ALARMS_ONLY = 601;
    private static final int MODE_PRIORITY_ONLY = 602;
    private static final int MODE_NONE = 603;
    private static final int MODE_VIBRATE = 604;
    private static final int MODE_RING = 605;
    // AICP additions: arbitrary value which hopefully doesn't conflict with upstream anytime soon
    private static final int MODE_SILENT = 620;

    // Keycodes from kernel found in drivers/input/misc/tri_state_key.c
    public static final int SLIDER_TOP = 601;
    public static final int SLIDER_MIDDLE = 602;
    public static final int SLIDER_BOTTOM = 603;

    private static final int[] sSupportedGestures = new int[]{
        GESTURE_CIRCLE_SCANCODE,
        GESTURE_SWIPE_DOWN_SCANCODE,
        GESTURE_V_SCANCODE,
        GESTURE_V_UP_SCANCODE,
        GESTURE_LTR_SCANCODE,
        GESTURE_GTR_SCANCODE,
        MODE_TOTAL_SILENCE,
        MODE_ALARMS_ONLY,
        MODE_PRIORITY_ONLY,
        MODE_NONE,
        MODE_VIBRATE,
        MODE_RING,
        MODE_SILENT,
    };

    private static final HashSet<Integer> sSupportedSliderModes = new HashSet<>();
    static {
        sSupportedSliderModes.add(SLIDER_TOP);
        sSupportedSliderModes.add(SLIDER_MIDDLE);
        sSupportedSliderModes.add(SLIDER_BOTTOM);
    }

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final PowerManager mPowerManager;
    private final NotificationManager mNotificationManager;
    private Context mGestureContext = null;
    private EventHandler mEventHandler;
    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private Vibrator mVibrator;
    WakeLock mProximityWakeLock;

    private int mSliderUpAction;
    private int mSliderMiddleAction;
    private int mSliderBottomAction;

    public KeyHandler(Context context) {
        mContext = context;
        mEventHandler = new EventHandler();
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mNotificationManager
                = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "ProximityWakeLock");

        try {
            mGestureContext = mContext.createPackageContext(
                    "com.slim.device", Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
        }

        new SettingsObserver(new Handler()).observe();
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            int scanCode;
            if (msg.obj instanceof KeyEvent) {
                KeyEvent event = (KeyEvent) msg.obj;
                scanCode = event.getScanCode();
            } else if (msg.obj instanceof Integer) {
                scanCode = (Integer) msg.obj;
            } else {
                return;
            }
            String action = null;
            switch(scanCode) {
            case GESTURE_CIRCLE_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_CIRCLE,
                        ActionConstants.ACTION_CAMERA);
                        doHapticFeedback();
                break;
            case GESTURE_SWIPE_DOWN_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_DOUBLE_SWIPE,
                        ActionConstants.ACTION_MEDIA_PLAY_PAUSE);
                        doHapticFeedback();
                break;
            case GESTURE_V_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_DOWN,
                        ActionConstants.ACTION_VIB_SILENT);
                        doHapticFeedback();
                break;
            case GESTURE_V_UP_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_UP,
                        ActionConstants.ACTION_TORCH);
                        doHapticFeedback();
                break;
            case GESTURE_LTR_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_LEFT,
                        ActionConstants.ACTION_MEDIA_PREVIOUS);
                        doHapticFeedback();
                break;
            case GESTURE_GTR_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_RIGHT,
                        ActionConstants.ACTION_MEDIA_NEXT);
                        doHapticFeedback();
                break;
            case MODE_TOTAL_SILENCE:
                setZenMode(Settings.Global.ZEN_MODE_NO_INTERRUPTIONS);
                break;
            case MODE_ALARMS_ONLY:
                setZenMode(Settings.Global.ZEN_MODE_ALARMS);
                break;
            case MODE_PRIORITY_ONLY:
                setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                setZenMode(Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS);
                break;
            case MODE_NONE:
                setZenMode(Settings.Global.ZEN_MODE_OFF);
                setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                break;
            case MODE_VIBRATE:
                setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                break;
            case MODE_RING:
                setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                break;
            case MODE_SILENT:
                setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                break;
            }

            if (action == null || action != null && action.equals(ActionConstants.ACTION_NULL)) {
                return;
            }
            if (action.equals(ActionConstants.ACTION_CAMERA)
                    || !action.startsWith("**")) {
                Action.processAction(mContext, ActionConstants.ACTION_WAKE_DEVICE, false);
            }
            Action.processAction(mContext, action, false);
        }
    }

    private void setZenMode(int mode) {
        mNotificationManager.setZenMode(mode, null, TAG);
        if (mVibrator != null) {
            mVibrator.vibrate(50);
        }
    }

    private void setRingerModeInternal(int mode) {
        mAudioManager.setRingerModeInternal(mode);
        if (mVibrator != null) {
            mVibrator.vibrate(50);
        }
    }

    private void doHapticFeedback() {
        if (mVibrator == null) {
            return;
        }
            mVibrator.vibrate(50);
    }

    private SharedPreferences getGestureSharedPreferences() {
        return mGestureContext.getSharedPreferences(
                ScreenOffGesture.GESTURE_SETTINGS,
                Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }

    public KeyEvent handleKeyEvent(KeyEvent event) {
        int scanCode = event.getScanCode();
        boolean isSliderModeSupported = sSupportedSliderModes.contains(scanCode);
        if (isSliderModeSupported) {
            // Remap slider actions
            if (scanCode == SLIDER_TOP) {
                scanCode = mSliderUpAction;
            } else if (scanCode == SLIDER_MIDDLE) {
                scanCode = mSliderMiddleAction;
            } else if (scanCode == SLIDER_BOTTOM) {
                scanCode = mSliderBottomAction;
            }
        }
        boolean isKeySupported = ArrayUtils.contains(sSupportedGestures, scanCode);
        if (!isKeySupported) {
            return event;
        }
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return null;
        }

        // In case we're too fast: possibly loose contact in hw button?
        // -> discard previous, apply last update
        mEventHandler.removeMessages(GESTURE_REQUEST);

        Message msg;
        if (scanCode != event.getScanCode()) {
            // Overwritten action
            msg = getMessageForScanCode(scanCode);
        } else {
            msg = getMessageForKeyEvent(event);
        }
        if (scanCode < MODE_TOTAL_SILENCE && mProximitySensor != null) {
            mEventHandler.sendMessageDelayed(msg, 200);
            processEvent(event);
        } else {
            mEventHandler.sendMessage(msg);
        }
        return null;
    }

    private Message getMessageForKeyEvent(KeyEvent keyEvent) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.obj = keyEvent;
        return msg;
    }

    private Message getMessageForScanCode(int scanCode) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.obj = new Integer(scanCode);
        return msg;
    }

    private void processEvent(final KeyEvent keyEvent) {
        mProximityWakeLock.acquire();
        mSensorManager.registerListener(new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent event) {
                mProximityWakeLock.release();
                mSensorManager.unregisterListener(this);
                if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
                    // The sensor took to long, ignoring.
                    return;
                }
                mEventHandler.removeMessages(GESTURE_REQUEST);
                if (event.values[0] == mProximitySensor.getMaximumRange()) {
                    Message msg = getMessageForKeyEvent(keyEvent);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(SETTING_NOTIF_SLIDER_UP),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(SETTING_NOTIF_SLIDER_MIDDLE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(SETTING_NOTIF_SLIDER_BOTTOM), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(SETTING_NOTIF_SLIDER_HAPTIC_FEEDBACK), false, this,
                    UserHandle.USER_ALL);

            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        void update() {
            mSliderUpAction = Settings.System.getInt(mContext.getContentResolver(),
                        SETTING_NOTIF_SLIDER_UP, 601);
            mSliderMiddleAction = Settings.System.getInt(mContext.getContentResolver(),
                        SETTING_NOTIF_SLIDER_MIDDLE, 602);
            mSliderBottomAction = Settings.System.getInt(mContext.getContentResolver(),
                        SETTING_NOTIF_SLIDER_BOTTOM, 603);


            if (Settings.System.getInt(mContext.getContentResolver(),
                        SETTING_NOTIF_SLIDER_HAPTIC_FEEDBACK, 1) != 0) {
                mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (mVibrator == null || !mVibrator.hasVibrator()) {
                    mVibrator = null;
                }
            } else {
                mVibrator = null;
            }
        }
    }

}
