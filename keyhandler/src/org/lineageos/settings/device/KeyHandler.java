/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
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

package org.lineageos.settings.device;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;

import lineageos.providers.LineageSettings;

import java.util.HashSet;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final int GESTURE_REQUEST = 1;

    // Supported scancodes
    private static final int FLIP_CAMERA_SCANCODE = 249;
    // Keycodes from kernel found in drivers/input/misc/tri_state_key.c
    private static final int SLIDER_TOP = 601;
    private static final int SLIDER_MIDDLE = 602;
    private static final int SLIDER_BOTTOM = 603;

    // Supported tri-state actions
    private static final int ACTION_TOTAL_SILENCE = 0;
    private static final int ACTION_ALARMS_ONLY = 1;
    private static final int ACTION_PRIORITY_ONLY = 2;
    private static final int ACTION_NONE = 3;
    private static final int ACTION_VIBRATE = 4;
    private static final int ACTION_RING = 5;

    // Same as in ButtonSettings
    private static final String SETTING_NOTIF_SLIDER_UP =
            "device_oppo_common_notification_slider_up";
    private static final String SETTING_NOTIF_SLIDER_MIDDLE =
            "device_oppo_common_notification_slider_middle";
    private static final String SETTING_NOTIF_SLIDER_BOTTOM =
            "device_oppo_common_notification_slider_bottom";

    private static final int GESTURE_WAKELOCK_DURATION = 3000;

    private static final HashSet<Integer> sSupportedSliderModes = new HashSet<>();
    static {
        sSupportedSliderModes.add(SLIDER_TOP);
        sSupportedSliderModes.add(SLIDER_MIDDLE);
        sSupportedSliderModes.add(SLIDER_BOTTOM);
    }

    private static final SparseIntArray sSupportedSliderActions = new SparseIntArray();
    static {
        sSupportedSliderActions.put(ACTION_TOTAL_SILENCE,
                Settings.Global.ZEN_MODE_NO_INTERRUPTIONS);
        sSupportedSliderActions.put(ACTION_ALARMS_ONLY, Settings.Global.ZEN_MODE_ALARMS);
        sSupportedSliderActions.put(ACTION_PRIORITY_ONLY,
                Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS);
        sSupportedSliderActions.put(ACTION_NONE, Settings.Global.ZEN_MODE_OFF);
        sSupportedSliderActions.put(ACTION_VIBRATE, AudioManager.RINGER_MODE_VIBRATE);
        sSupportedSliderActions.put(ACTION_RING, AudioManager.RINGER_MODE_NORMAL);
    }

    private final Context mContext;
    private final PowerManager mPowerManager;
    private final AudioManager mAudioManager;
    private final NotificationManager mNotificationManager;
    private EventHandler mEventHandler;
    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private Vibrator mVibrator;
    WakeLock mProximityWakeLock;
    WakeLock mGestureWakeLock;
    private int mProximityTimeOut;
    private boolean mProximityWakeSupported;

    private int mSliderUpAction;
    private int mSliderMiddleAction;
    private int mSliderBottomAction;

    public KeyHandler(Context context) {
        mContext = context;
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mNotificationManager
                = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mEventHandler = new EventHandler();
        mGestureWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GestureWakeLock");

        final Resources resources = mContext.getResources();
        mProximityTimeOut = resources.getInteger(
                org.lineageos.platform.internal.R.integer.config_proximityCheckTimeout);
        mProximityWakeSupported = resources.getBoolean(
                org.lineageos.platform.internal.R.bool.config_proximityCheckOnWake);

        if (mProximityWakeSupported) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "ProximityWakeLock");
        }

        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

        new SettingsObserver(new Handler()).observe();
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.arg1 == FLIP_CAMERA_SCANCODE) {
                mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);

                Intent intent = new Intent(
                        lineageos.content.Intent.ACTION_SCREEN_CAMERA_GESTURE);
                mContext.sendBroadcast(intent, Manifest.permission.STATUS_BAR_SERVICE);
                doHapticFeedback();
            }
        }
    }

    private boolean hasSetupCompleted() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
    }

    public KeyEvent handleKeyEvent(KeyEvent event) {
        int scanCode = event.getScanCode();
        boolean isKeySupported = scanCode == FLIP_CAMERA_SCANCODE;
        boolean isSliderModeSupported = sSupportedSliderModes.contains(scanCode);
        if (!isKeySupported && !isSliderModeSupported) {
            return event;
        }

        if (!hasSetupCompleted()) {
            return event;
        }

        // We only want ACTION_UP event, except FLIP_CAMERA_SCANCODE
        if (scanCode == FLIP_CAMERA_SCANCODE) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return null;
            }
        } else if (event.getAction() != KeyEvent.ACTION_UP) {
            return null;
        }

        if (isSliderModeSupported) {
            int sliderAction;
            if (scanCode == SLIDER_TOP) {
                sliderAction = mSliderUpAction;
            } else if (scanCode == SLIDER_MIDDLE) {
                sliderAction = mSliderMiddleAction;
            } else { // scanCode == SLIDER_BOTTOM
                sliderAction = mSliderBottomAction;
            }
            if (sliderAction <= ACTION_NONE) {
                mNotificationManager.setZenMode(sSupportedSliderActions.get(sliderAction),
                        null, TAG);
            } else {
                mAudioManager.setRingerModeInternal(sSupportedSliderActions.get(sliderAction));
            }
            doHapticFeedback();
        } else if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
            Message msg = getMessageForKeyEvent(scanCode);
            boolean defaultProximity = mContext.getResources().getBoolean(
                org.lineageos.platform.internal.R.bool.config_proximityCheckOnWakeEnabledByDefault);
            boolean proximityWakeCheckEnabled = LineageSettings.System.getInt(
                    mContext.getContentResolver(), LineageSettings.System.PROXIMITY_ON_WAKE,
                    defaultProximity ? 1 : 0) == 1;
            if (mProximityWakeSupported && proximityWakeCheckEnabled && mProximitySensor != null) {
                mEventHandler.sendMessageDelayed(msg, mProximityTimeOut);
                processEvent(scanCode);
            } else {
                mEventHandler.sendMessage(msg);
            }
        }
        return null;
    }

    private Message getMessageForKeyEvent(int scancode) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.arg1 = scancode;
        return msg;
    }

    private void processEvent(final int scancode) {
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
                    Message msg = getMessageForKeyEvent(scancode);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void doHapticFeedback() {
        if (mVibrator == null) {
            return;
        }
        boolean enabled = LineageSettings.System.getInt(mContext.getContentResolver(),
                LineageSettings.System.TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK, 1) != 0;
        if (enabled) {
            mVibrator.vibrate(50);
        }
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

            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        void update() {
            mSliderUpAction = Settings.System.getInt(mContext.getContentResolver(),
                        SETTING_NOTIF_SLIDER_UP, 1);
            mSliderMiddleAction = Settings.System.getInt(mContext.getContentResolver(),
                        SETTING_NOTIF_SLIDER_MIDDLE, 2);
            mSliderBottomAction = Settings.System.getInt(mContext.getContentResolver(),
                        SETTING_NOTIF_SLIDER_BOTTOM, 3);

        }

    }
}
