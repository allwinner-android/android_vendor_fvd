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

package com.android.tv.launcher.users;

import android.content.Context;
import android.content.RestrictionEntry;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings.Secure;

import com.android.tv.launcher.R;

import java.util.ArrayList;

public class RestrictionUtils {

    public static final String [] sRestrictionKeys = {
        UserManager.DISALLOW_SHARE_LOCATION,
    };

    public static final int [] sRestrictionTitles = {
        R.string.restriction_location_enable_title,
    };

    public static final int [] sRestrictionDescriptions = {
        R.string.restriction_location_enable_summary,
    };

    /**
     * Returns the current user restrictions in the form of application
     * restriction entries.
     * @return list of RestrictionEntry objects with user-visible text.
     */
    public static ArrayList<RestrictionEntry> getRestrictions(Context context, UserHandle user) {
        Resources res = context.getResources();
        ArrayList<RestrictionEntry> entries = new ArrayList<RestrictionEntry>();
        UserManager um = UserManager.get(context);
        Bundle userRestrictions = um.getUserRestrictions(user);

        for (int i = 0; i < sRestrictionKeys.length; i++) {
            RestrictionEntry entry = new RestrictionEntry(
                    sRestrictionKeys[i],
                    !userRestrictions.getBoolean(sRestrictionKeys[i], false));
            entry.setTitle(res.getString(sRestrictionTitles[i]));
            entry.setDescription(res.getString(sRestrictionDescriptions[i]));
            entry.setType(RestrictionEntry.TYPE_BOOLEAN);
            entries.add(entry);
        }

        return entries;
    }

    public static void setRestrictions(Context context, ArrayList<RestrictionEntry> entries,
            UserHandle user) {
        UserManager um = UserManager.get(context);
        Bundle userRestrictions = um.getUserRestrictions(user);

        for (RestrictionEntry entry : entries) {
            userRestrictions.putBoolean(entry.getKey(), !entry.getSelectedState());
            if (entry.getKey().equals(UserManager.DISALLOW_SHARE_LOCATION)
                    && !entry.getSelectedState()) {
                Secure.putIntForUser(context.getContentResolver(),
                        Secure.LOCATION_MODE, Secure.LOCATION_MODE_OFF, user.getIdentifier());
            }
        }
        um.setUserRestrictions(userRestrictions, user);
    }

    public static Bundle restrictionsToBundle(ArrayList<RestrictionEntry> entries) {
        final Bundle bundle = new Bundle();
        for (RestrictionEntry entry : entries) {
            if (entry.getType() == RestrictionEntry.TYPE_BOOLEAN) {
                bundle.putBoolean(entry.getKey(), entry.getSelectedState());
            } else if (entry.getType() == RestrictionEntry.TYPE_MULTI_SELECT) {
                bundle.putStringArray(entry.getKey(), entry.getAllSelectedStrings());
            } else {
                bundle.putString(entry.getKey(), entry.getSelectedString());
            }
        }
        return bundle;
    }
}
