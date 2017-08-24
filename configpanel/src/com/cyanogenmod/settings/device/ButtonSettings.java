/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.cyanogenmod.settings.device;

import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.provider.Settings;

import com.cyanogenmod.settings.device.utils.NodePreferenceActivity;

import org.cyanogenmod.internal.util.ScreenType;

public class ButtonSettings extends NodePreferenceActivity {
    private static final String KEY_IGNORE_AUTO = "notification_slider_ignore_auto";
    private static final String PROP_IGNORE_AUTO = "persist.op.slider_ignore_auto";

    private static final String KEY_NOTIFICATION_SLIDER_UP = "notification_slider_up";
    private static final String KEY_NOTIFICATION_SLIDER_MIDDLE = "notification_slider_middle";
    private static final String KEY_NOTIFICATION_SLIDER_BOTTOM = "notification_slider_bottom";

    // Same as in KeyHandler
    private static final String SETTING_NOTIF_SLIDER_UP =
            "device_oppo_common_notification_slider_up";
    private static final String SETTING_NOTIF_SLIDER_MIDDLE =
            "device_oppo_common_notification_slider_middle";
    private static final String SETTING_NOTIF_SLIDER_BOTTOM =
            "device_oppo_common_notification_slider_bottom";

    private SwitchPreference mIgnoreAuto;
    private ListPreference mNotificationSliderUp;
    private ListPreference mNotificationSliderMiddle;
    private ListPreference mNotificationSliderBottom;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.button_panel);

        mIgnoreAuto = (SwitchPreference) findPreference(KEY_IGNORE_AUTO);
        mIgnoreAuto.setOnPreferenceChangeListener(this);

        mNotificationSliderUp = (ListPreference) findPreference(KEY_NOTIFICATION_SLIDER_UP);
        mNotificationSliderMiddle =
                (ListPreference) findPreference(KEY_NOTIFICATION_SLIDER_MIDDLE);
        mNotificationSliderBottom = (ListPreference) findPreference(KEY_NOTIFICATION_SLIDER_BOTTOM);
        mNotificationSliderUp.setOnPreferenceChangeListener(this);
        mNotificationSliderMiddle.setOnPreferenceChangeListener(this);
        mNotificationSliderBottom.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        if (KEY_IGNORE_AUTO.equals(key)) {
            final boolean value = (Boolean) newValue;
            SystemProperties.set(PROP_IGNORE_AUTO, value ? "true" : "false");
            return true;
        } else if (KEY_NOTIFICATION_SLIDER_UP.equals(key)) {
            Settings.System.putInt(getContentResolver(), SETTING_NOTIF_SLIDER_UP,
                    Integer.parseInt((String) newValue));
            return true;
        } else if (KEY_NOTIFICATION_SLIDER_MIDDLE.equals(key)) {
            Settings.System.putInt(getContentResolver(), SETTING_NOTIF_SLIDER_MIDDLE,
                    Integer.parseInt((String) newValue));
            return true;
        } else if (KEY_NOTIFICATION_SLIDER_BOTTOM.equals(key)) {
            Settings.System.putInt(getContentResolver(), SETTING_NOTIF_SLIDER_BOTTOM,
                    Integer.parseInt((String) newValue));
            return true;
        }

        return super.onPreferenceChange(preference, newValue);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mIgnoreAuto.setChecked(SystemProperties.get(PROP_IGNORE_AUTO).equals("true"));

        mNotificationSliderUp.setValue(String.valueOf(Settings.System.getInt(getContentResolver(),
                SETTING_NOTIF_SLIDER_UP, 1)));
        mNotificationSliderMiddle.setValue(String.valueOf(Settings.System.getInt(
                getContentResolver(), SETTING_NOTIF_SLIDER_MIDDLE, 2)));
        mNotificationSliderBottom.setValue(String.valueOf(Settings.System.getInt(
                getContentResolver(), SETTING_NOTIF_SLIDER_BOTTOM, 3)));
    }
}
