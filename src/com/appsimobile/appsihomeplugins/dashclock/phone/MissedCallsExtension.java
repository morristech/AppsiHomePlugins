/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appsimobile.appsihomeplugins.dashclock.phone;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.CallLog;
import android.text.TextUtils;

import com.appsimobile.appsihomeplugins.DashClockHomeExtension;
import com.appsimobile.appsihomeplugins.R;
import com.appsimobile.appsihomeplugins.dashclock.LogUtils;
import com.appsimobile.appsisupport.home.FieldsBuilder;
import com.appsimobile.appsisupport.home.HomeServiceContract;
import com.appsimobile.appsisupport.internal.FieldValues;

import java.util.SortedSet;
import java.util.TreeSet;

import static com.appsimobile.appsihomeplugins.dashclock.LogUtils.LOGE;

/**
 * Number of missed calls extension.
 */
public class MissedCallsExtension extends DashClockHomeExtension {

    private static final String TAG = LogUtils.makeLogTag(MissedCallsExtension.class);

    public MissedCallsExtension(Context context) {
        super(context);
    }

    @Override
    public void onInitialize(FieldsBuilder builder) {
        builder.registerField(
                DASHCLOCK_EXTENSION_MISSED_CALLS,
                HomeServiceContract.FieldsResponse.DISPLAY_TYPE_DASHCLOCK,
                R.string.missed_calls_extension_title,
                R.drawable.ic_logo_missed_calls,
                null,
                CallLog.Calls.CONTENT_URI.toString());
        /*

        super.onInitialize(isReconnect);
        if (!isReconnect) {
            addWatchContentUris(new String[]{
                    CallLog.Calls.CONTENT_URI.toString()
            });
        }
        */
    }

    @Override
    public void onUpdateData(FieldValues.Builder builder) {
        Cursor cursor = tryOpenMissedCallsCursor();
        if (cursor == null) {
            LOGE(TAG, "Null missed calls cursor, short-circuiting.");
            return;
        }

        int missedCalls = 0;
        SortedSet<String> names = new TreeSet<String>();
        while (cursor.moveToNext()) {
            ++missedCalls;
            String name = cursor.getString(MissedCallsQuery.CACHED_NAME);
            if (TextUtils.isEmpty(name)) {
                name = cursor.getString(MissedCallsQuery.NUMBER);
                long parsedNumber = 0;
                try {
                    parsedNumber = Long.parseLong(name);
                } catch (Exception ignored) {
                }
                if (parsedNumber < 0) {
                    // Unknown or private number
                    name = getString(R.string.missed_calls_unknown);
                }
            }
            names.add(name);
        }
        cursor.close();

        Intent clickIntent = new Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI);
        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), DashClockHomeExtension.DASHCLOCK_EXTENSION_MISSED_CALLS, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.intent(pendingIntent)
                .leftImageResId(R.drawable.ic_extension_missed_calls)
                .header(getResources().getQuantityString(
                        R.plurals.missed_calls_title_template, missedCalls, missedCalls));
        if (missedCalls > 0) {
            builder.text(getString(R.string.missed_calls_body_template,
                        TextUtils.join(", ", names)));
        }

        /*
        publishUpdate(new ExtensionData()
                .visible(missedCalls > 0)
                .icon(R.drawable.ic_extension_missed_calls)
                .status(Integer.toString(missedCalls))
                .expandedTitle(
                        getResources().getQuantityString(
                                R.plurals.missed_calls_title_template, missedCalls, missedCalls))
                .expandedBody(getString(R.string.missed_calls_body_template,
                        TextUtils.join(", ", names)))
                .clickIntent(new Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)));
                */
    }

    private Cursor tryOpenMissedCallsCursor() {
        try {
            return getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    MissedCallsQuery.PROJECTION,
                    CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE + " AND "
                            + CallLog.Calls.NEW + "!=0",
                    null,
                    null);

        } catch (Exception e) {
            LOGE(TAG, "Error opening missed calls cursor", e);
            return null;
        }
    }

    private interface MissedCallsQuery {
        String[] PROJECTION = {
                CallLog.Calls._ID,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
        };

        int ID = 0;
        int CACHED_NAME = 1;
        int NUMBER = 2;
    }
}
