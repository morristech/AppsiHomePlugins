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

import com.appsimobile.appsihomeplugins.DashClockHomeExtension;
import com.appsimobile.appsihomeplugins.R;
import com.appsimobile.appsihomeplugins.dashclock.calendar.CalendarSettingsActivity;
import com.appsimobile.appsihomeplugins.home.WeatherService;
import com.appsimobile.appsisupport.home.FieldDataBuilder;
import com.appsimobile.appsisupport.home.FieldsBuilder;
import com.appsimobile.appsisupport.home.HomeServiceContract;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.util.Log;

import java.net.URISyntaxException;


/**
 * A local weather and forecast extension.
 */
public class WeatherExtension extends DashClockHomeExtension {
    private static String sWeatherUnits = "f";

    public WeatherExtension(Context context) {
        super(context);
    }

    @Override
    public void onInitialize(FieldsBuilder builder) {
        builder.registerField(
                DASHCLOCK_EXTENSION_WEATHER,
                HomeServiceContract.FieldsResponse.DISPLAY_TYPE_DASHCLOCK,
                R.string.weather_extension_title,
                R.drawable.ic_weather_sunny,
                new ComponentName(getContext().getPackageName(), WeatherSettingsActivity.class.getName())
        );
    }

    @Override
    public void onUpdateData(FieldDataBuilder builder) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        sWeatherUnits = sp.getString(WeatherService.PREF_WEATHER_UNITS, sWeatherUnits);

        YahooWeatherApiClient.setWeatherUnits(sWeatherUnits);

        Intent i = new Intent(getContext(), WeatherService.class);
        getContext().startService(i);

        // set data from shared preferences.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        int icon = sharedPreferences.getInt("last_conditionIconId", R.drawable.ic_weather_clear);
        String header = sharedPreferences.getString("last_title", getString(R.string.no_weather_data));
        String text = sharedPreferences.getString("last_text", null);
        String intentUri = sharedPreferences.getString("last_intent", null);
        Intent intent = null;
        try {
            intent = intentUri == null ? null : Intent.parseUri(intentUri, 0);
        } catch (URISyntaxException e) {
            Log.w("WeatherExtension", "error parsing uri", e);
        }

        Bitmap image = icon == 0 ? null : BitmapFactory.decodeResource(getResources(), icon);
        PendingIntent pendingIntent = intent == null ? null : PendingIntent.getActivity(getContext(), DashClockHomeExtension.DASHCLOCK_EXTENSION_WEATHER, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.leftImage(image)
                .intent(pendingIntent)
                .header(header)
                .text(text);

    }

}
