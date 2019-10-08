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

package com.slim.device.settings;

import android.app.ActionBar;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceActivity;
import androidx.preference.SwitchPreference;
import android.view.MenuItem;

import com.slim.device.KernelControl;
import com.slim.device.R;
import com.slim.device.util.FileUtils;

public class SliderSettings extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.slider_panel);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setSummary(ListPreference preference, String file) {
        String keyCode;
        if ((keyCode = FileUtils.readOneLine(file)) != null) {
            preference.setValue(keyCode);
            preference.setSummary(preference.getEntry());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
        /*
        final String file;
        if (preference == mSliderTop) {
            file = KernelControl.KEYCODE_SLIDER_TOP;
        } else if (preference == mSliderMiddle) {
            file = KernelControl.KEYCODE_SLIDER_MIDDLE;
        } else if (preference == mSliderBottom) {
            file = KernelControl.KEYCODE_SLIDER_BOTTOM;
        } else {
            return false;
        }

        FileUtils.writeLine(file, (String) newValue);
        setSummary((ListPreference) preference, file);

        return true;
        */
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Remove padding around the listview
            getListView().setPadding(0, 0, 0, 0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
