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

package com.android.tv.launcher.device.apps;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.INotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.ServiceManager;
import android.os.UserHandle;

/**
 * Handles notifications for an application.
 */
class NotificationSetter {

    private final AppInfo mAppInfo;
    private boolean mNotificationsOn;

    NotificationSetter(AppInfo appInfo) {
        mAppInfo = appInfo;
        try {
            mNotificationsOn = getNotificationManager()
                    .areNotificationsEnabledForPackage(mAppInfo.getPackageName(),
                            mAppInfo.getUid());
        } catch (android.os.RemoteException ex) {
            // this does not bode well
            mNotificationsOn = true; // default on
        }
    }

    boolean areNotificationsOn() {
        return mNotificationsOn;
    }

    boolean enableNotifications() {
        return setNotificationsEnabled(true);
    }

    boolean disableNotifications() {
        return setNotificationsEnabled(false);
    }

    private boolean setNotificationsEnabled(boolean enabled) {
        boolean result = true;
        if (mNotificationsOn != enabled) {
            try {
                getNotificationManager().setNotificationsEnabledForPackage(
                        mAppInfo.getPackageName(), mAppInfo.getUid(), enabled);
                mNotificationsOn = enabled;
            } catch (android.os.RemoteException ex) {
                result = false;
            }
        }
        return result;
    }

    private INotificationManager getNotificationManager() {
        return INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
    }
}
