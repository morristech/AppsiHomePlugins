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

package com.appsimobile.appsihomeplugins.dashclock.nextalarm;

import com.appsimobile.appsihomeplugins.DashClockHomeExtension;
import com.appsimobile.appsihomeplugins.R;
import com.appsimobile.appsihomeplugins.dashclock.LogUtils;
import com.appsimobile.appsihomeplugins.dashclock.Utils;
import com.appsimobile.appsisupport.home.FieldDataBuilder;
import com.appsimobile.appsisupport.home.FieldsBuilder;
import com.appsimobile.appsisupport.home.HomeServiceContract;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Next alarm extension.
 */
public class NextAlarmExtension extends DashClockHomeExtension {
    private static final String TAG = LogUtils.makeLogTag(NextAlarmExtension.class);
    private static Pattern sDigitPattern = Pattern.compile("\\s[0-9]");

    public NextAlarmExtension(Context context) {
        super(context);
    }

    @Override
    public void onInitialize(FieldsBuilder builder) {
        /*
        if (!isReconnect) {
            addWatchContentUris(new String[]{
                    Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED).toString()
            });
        }
        */
        builder.registerField(
                DASHCLOCK_EXTENSION_NEXT_ALARM,
                HomeServiceContract.FieldsResponse.DISPLAY_TYPE_SIMPLE,
                R.string.next_alarm_extension_title,
                R.drawable.ic_extension_next_alarm,
                null,
                Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED).toString()
        );
    }

    @Override
    public void onUpdateData(FieldDataBuilder builder) {
        String nextAlarm = Settings.System.getString(getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED);
        if (!TextUtils.isEmpty(nextAlarm)) {
            Matcher m = sDigitPattern.matcher(nextAlarm);
            if (m.find() && m.start() > 0) {
                nextAlarm = nextAlarm.substring(0, m.start()) + "\n"
                        + nextAlarm.substring(m.start() + 1); // +1 to skip whitespace
            }
        } else {
            nextAlarm = getString(R.string.no_alarm_set);
        }
        Intent intent = Utils.getDefaultAlarmsIntent(getContext());
        PendingIntent pendingIntent = intent == null ? null : PendingIntent.getActivity(getContext(), DashClockHomeExtension.DASHCLOCK_EXTENSION_NEXT_ALARM, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_extension_next_alarm);

        builder.intent(pendingIntent)
                .leftImage(bitmap)
                .text(nextAlarm);


        /*
        publishUpdate(new ExtensionData()
                .visible(!TextUtils.isEmpty(nextAlarm))
                .icon(R.drawable.ic_extension_next_alarm)
                .status(nextAlarm)
                .clickIntent(Utils.getDefaultAlarmsIntent(this)));
                */
    }
}
