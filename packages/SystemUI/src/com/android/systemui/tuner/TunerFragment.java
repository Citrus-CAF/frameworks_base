/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.systemui.tuner;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.System;
import android.support.v14.preference.PreferenceFragment;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceScreen;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.util.custom.CustomUtils;
import com.android.systemui.R;

public class TunerFragment extends PreferenceFragment {

    private static final String TAG = "TunerFragment";

    private static final String BLUETOOTH_SHOW_BATTERY = "bluetooth_show_battery";

    private static final String SHOW_LTE_FOURGEE = "show_lte_fourgee";

    private static final String ROAMING_INDICATOR_ICON = "roaming_indicator_icon";

    private static final String DATA_DISABLED_ICON = "data_disabled_icon";

    private SwitchPreference mBluetoothBattery;

    private SwitchPreference mShowLteFourGee;

    private SwitchPreference mShowRoamingIcon;

    private SwitchPreference mShowNoDataIcon;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        PreferenceScreen prefSet = getPreferenceScreen();
        final ContentResolver resolver = getActivity().getContentResolver();

        mShowLteFourGee = (SwitchPreference) findPreference(SHOW_LTE_FOURGEE);
        if (CustomUtils.isWifiOnly(getActivity())) {
            prefSet.removePreference(mShowLteFourGee);
        } else {
        mShowLteFourGee.setChecked((Settings.System.getInt(resolver,
                Settings.System.SHOW_LTE_FOURGEE, 0) == 1));
        }

        mShowRoamingIcon = (SwitchPreference) findPreference(ROAMING_INDICATOR_ICON);
        if (CustomUtils.isWifiOnly(getActivity())) {
            prefSet.removePreference(mShowRoamingIcon);
        } else {
        mShowRoamingIcon.setChecked((Settings.System.getInt(resolver,
                 Settings.System.ROAMING_INDICATOR_ICON, 1) == 1));
        }

       mShowNoDataIcon = (SwitchPreference) findPreference(DATA_DISABLED_ICON);
        if (CustomUtils.isWifiOnly(getActivity())) {
            prefSet.removePreference(mShowNoDataIcon);
        } else {
        mShowNoDataIcon.setChecked((Settings.System.getInt(resolver,
                 Settings.System.DATA_DISABLED_ICON, 1) == 1));
        }
        mBluetoothBattery = (SwitchPreference) findPreference(BLUETOOTH_SHOW_BATTERY);
        mBluetoothBattery.setChecked((Settings.System.getInt(resolver,
                Settings.System.BLUETOOTH_SHOW_BATTERY, 0) == 1));

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.tuner_prefs);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(R.string.statusbar_icons_blacklist);

        MetricsLogger.visibility(getContext(), MetricsEvent.TUNER, true);
    }

    @Override
    public void onPause() {
        super.onPause();

        MetricsLogger.visibility(getContext(), MetricsEvent.TUNER, false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if  (preference == mShowLteFourGee) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SHOW_LTE_FOURGEE, checked ? 1:0);
            return true;
        } else if  (preference == mShowRoamingIcon) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.ROAMING_INDICATOR_ICON, checked ? 1:0);
            return true;            
        } else if  (preference == mBluetoothBattery) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.BLUETOOTH_SHOW_BATTERY, checked ? 1:0);
            return true;
        } else if  (preference == mShowNoDataIcon) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.DATA_DISABLED_ICON, checked ? 1:0);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
