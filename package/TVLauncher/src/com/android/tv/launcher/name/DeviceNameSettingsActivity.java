/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.launcher.name;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import java.util.ArrayList;

import com.android.tv.launcher.R;
import com.android.tv.launcher.connectivity.setup.TextInputWizardFragment;

/**
 * Activity that displays Device Name settings
 * 关于-->设备名称-->更改名称 的功能 
 */
public class DeviceNameSettingsActivity extends Activity implements AdapterView.OnItemClickListener,
        TextInputWizardFragment.Listener {

    private static final String TAG = DeviceNameSettingsActivity.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final String EXTRA_SETUP_MODE = "in_setup_mode";
    private static final String INITIAL_STATE = "initial";

    private ArrayList<String> mOptions = new ArrayList<String>(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.v(TAG, "Starting Device Name activity");
        setContentView(R.layout.setup_activity);

        Resources res = getResources();
        mOptions.add(res.getString(R.string.keep_settings));
        mOptions.add(res.getString(R.string.change_setting));
        displaySummary();
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() == 1) {
        	if (DEBUG) Log.v(TAG, "IKE onBackPressed() finish");
            finish();
        } else {
        	if (DEBUG) Log.v(TAG, "IKE onBackPressed() super.onBackPressed");
            super.onBackPressed();
        }
    }

    @Override
    public boolean onTextInputComplete(String name) {
    	if (DEBUG) Log.v(TAG, "IKE onTextInputComplete() name="+name);
        setNameAndResetUi(name);
        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> listView, View itemView, int position, long id) {
        if (position == 0) {
            // "Don't change" was selected, we're done!
            setResult(RESULT_OK);
            finish();
        } else {
            displaySetName();
        }
    }

    //左边的简介  和 右边选项options
    private void displaySummary() {
        DeviceNameSummaryFragment fragment = DeviceNameSummaryFragment.createInstance(
                getResources().getString(R.string.settings_status_title),
                getResources().getString(R.string.settings_status_description), mOptions);
        fragment.setOnItemClickListener(this);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.content_wrapper, fragment);
        transaction.addToBackStack(INITIAL_STATE);
        transaction.commit();
    }
    //显示更改页面
    private void displaySetName() {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        String[] rooms = getResources().getStringArray(R.array.rooms);
        ArrayList<String> roomsList = new ArrayList<String>();
        for (int ptr = 0; ptr < rooms.length; ptr++) {
            roomsList.add(rooms[ptr]);
        }
        SetDeviceNameFragment nameUi = SetDeviceNameFragment.createInstance(roomsList, true);

        nameUi.setListener(new SetDeviceNameListener() {
            @Override
            public boolean validateName(String name) {
                return true;
            }

            @Override
            public void onDeviceNameSelected(String name) {
                setNameAndResetUi(name);
            }

            @Override
            public void onCustomNameRequested() {
                displayTextInputFragment();
            }
        });

        transaction.replace(R.id.content_wrapper, nameUi);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    /**
     * Show the fragment that allows the user to give a custom name to their device
     */
    private void displayTextInputFragment() {
        // Magically TextInputWizardFragment hopes its enclosing activity is an instance of
        // its listener type, and we are so, onTextInputComplete(String) is automatically
        // called here
        String title = getString(R.string.select_title);
        String description = getString(R.string.select_description);
        TextInputWizardFragment customName = TextInputWizardFragment.newInstance(
                TextUtils.expandTemplate(title, Build.MODEL).toString(),
                TextUtils.expandTemplate(description, Build.MODEL).toString(),
                TextInputWizardFragment.INPUT_TYPE_NORMAL,
                null);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.content_wrapper, customName);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void setNameAndResetUi(String name) {
        // if the name gets set, consider it success
        setResult(RESULT_OK);
        setDeviceName(name);
        // TODO delay reset until name update propagates
        getFragmentManager().popBackStack(INITIAL_STATE, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        finish();
    }

    private void setDeviceName(String name) {
        if (DEBUG) Log.v(TAG, String.format("Setting device name to %s", name));
        DeviceManager.setDeviceName(getApplicationContext(), name);
    }
}
