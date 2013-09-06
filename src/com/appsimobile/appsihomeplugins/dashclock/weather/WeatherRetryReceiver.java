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

package com.appsimobile.appsihomeplugins.dashclock.weather;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.appsimobile.appsihomeplugins.DashClockHomeExtension;
import com.appsimobile.appsisupport.home.HomeServiceContract;

/**
 * Broadcast receiver that's a target of the retry-update alarm from {@link WeatherExtension}.
 */
public class WeatherRetryReceiver extends BroadcastReceiver {
    public static PendingIntent getPendingIntent(Context context) {
        return PendingIntent.getBroadcast(context, 0,
                new Intent(context, WeatherRetryReceiver.class),
                PendingIntent.FLAG_CANCEL_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        HomeServiceContract.requestPluginUpdate(context, DashClockHomeExtension.DASHCLOCK_EXTENSION_WEATHER);
    }
}
