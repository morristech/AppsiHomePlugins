package com.appsimobile.appsihomeplugins.home;

import android.app.AlarmManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.appsimobile.appsihomeplugins.DashClockHomeExtension;
import com.appsimobile.appsihomeplugins.R;
import com.appsimobile.appsihomeplugins.dashclock.LogUtils;
import com.appsimobile.appsihomeplugins.dashclock.configuration.AppChooserPreference;
import com.appsimobile.appsihomeplugins.dashclock.weather.CantGetWeatherException;
import com.appsimobile.appsihomeplugins.dashclock.weather.WeatherData;
import com.appsimobile.appsihomeplugins.dashclock.weather.WeatherLocationPreference;
import com.appsimobile.appsihomeplugins.dashclock.weather.WeatherRetryReceiver;
import com.appsimobile.appsihomeplugins.dashclock.weather.YahooWeatherApiClient;
import com.appsimobile.appsisupport.home.HomeServiceContract;

import java.util.Arrays;
import java.util.Locale;

import static com.appsimobile.appsihomeplugins.dashclock.LogUtils.LOGD;
import static com.appsimobile.appsihomeplugins.dashclock.LogUtils.LOGE;
import static com.appsimobile.appsihomeplugins.dashclock.LogUtils.LOGW;
import static com.appsimobile.appsihomeplugins.dashclock.weather.YahooWeatherApiClient.getLocationInfo;
import static com.appsimobile.appsihomeplugins.dashclock.weather.YahooWeatherApiClient.getWeatherForLocationInfo;

/**
 * Created by nnma on 9/5/13.
 */
public class WeatherService extends IntentService {


    private static final String TAG = LogUtils.makeLogTag(WeatherService.class);

    public static final String PREF_WEATHER_UNITS = "pref_weather_units";
    public static final String PREF_WEATHER_SHORTCUT = "pref_weather_shortcut";
    public static final String PREF_WEATHER_LOCATION = "pref_weather_location";

    public static final Intent DEFAULT_WEATHER_INTENT = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=weather"));

    public static final String STATE_WEATHER_LAST_BACKOFF_MILLIS
            = "state_weather_last_backoff_millis";
    public static final String STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS
            = "state_weather_last_update_elapsed_millis";

    private static final long UPDATE_THROTTLE_MILLIS = 10 * DateUtils.MINUTE_IN_MILLIS; // At least 10 min b/w updates

    private static final long STALE_LOCATION_MILLIS = 10l * 60000000l; // 10 minutes

    private static final int INITIAL_BACKOFF_MILLIS = 30000; // 30 seconds for first error retry

    private static final int LOCATION_TIMEOUT_MILLIS = 60000; // 60 sec timeout for location attempt

    private static final Criteria sLocationCriteria;

    public static Intent sWeatherIntent;

    private boolean mOneTimeLocationListenerActive = false;

    private Handler mTimeoutHandler = new Handler();

    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }


    public WeatherService() {
        super("WeatherService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sWeatherIntent = AppChooserPreference.getIntentValue(
                sp.getString(PREF_WEATHER_SHORTCUT, null), DEFAULT_WEATHER_INTENT);

        long lastUpdateElapsedMillis = sp.getLong(STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS, -UPDATE_THROTTLE_MILLIS);
        long nowElapsedMillis = SystemClock.elapsedRealtime();

        if (nowElapsedMillis > lastUpdateElapsedMillis + UPDATE_THROTTLE_MILLIS) {
            updateWeatherData(sp);
        }

    }


    private void resetAndCancelRetries() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().remove(STATE_WEATHER_LAST_BACKOFF_MILLIS).apply();
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.cancel(WeatherRetryReceiver.getPendingIntent(this));
    }

    private void scheduleRetry() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        int lastBackoffMillis = sp.getInt(STATE_WEATHER_LAST_BACKOFF_MILLIS, 0);
        int backoffMillis = (lastBackoffMillis > 0)
                ? lastBackoffMillis * 2
                : INITIAL_BACKOFF_MILLIS;
        sp.edit().putInt(STATE_WEATHER_LAST_BACKOFF_MILLIS, backoffMillis).apply();
        LOGD(TAG, "Scheduling weather retry in " + (backoffMillis / 1000) + " second(s)");
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + backoffMillis,
                WeatherRetryReceiver.getPendingIntent(this));
    }

    private void updateWeatherData(SharedPreferences sp) {


        NetworkInfo ni = ((ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            LOGD(TAG, "No network connection; not attempting to update weather.");
            return;
        }

        String manualLocationWoeid = WeatherLocationPreference.getWoeidFromValue(
                sp.getString(PREF_WEATHER_LOCATION, null));
        if (!TextUtils.isEmpty(manualLocationWoeid)) {
            // WOEIDs
            // Honolulu = 2423945
            // Paris = 615702
            // London = 44418
            // New York = 2459115
            // San Francisco = 2487956
            YahooWeatherApiClient.LocationInfo locationInfo = new YahooWeatherApiClient.LocationInfo();
            locationInfo.woeids = Arrays.asList(manualLocationWoeid);
            tryPublishWeatherUpdateFromLocationInfo(locationInfo);
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String provider = lm.getBestProvider(sLocationCriteria, true);
        if (TextUtils.isEmpty(provider)) {
            publishErrorUpdate(new CantGetWeatherException(false, R.string.no_location_data, "No available location providers matching criteria."));
            return;
        }

        final Location lastLocation = lm.getLastKnownLocation(provider);
        if (lastLocation == null ||
                (System.currentTimeMillis() - lastLocation.getTime())
                        >= STALE_LOCATION_MILLIS) {
            LOGW(TAG, "Stale or missing last-known location; requesting single coarse location "
                    + "update.");
            disableOneTimeLocationListener();
            mOneTimeLocationListenerActive = true;
            lm.requestSingleUpdate(provider, mOneTimeLocationListener, null);

            // Time-out single location update request
            mTimeoutHandler.removeCallbacksAndMessages(null);
            mTimeoutHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    LOGE(TAG, "Location request timed out.");
                    disableOneTimeLocationListener();
                    scheduleRetry();
                }
            }, LOCATION_TIMEOUT_MILLIS);
        } else {
            tryPublishWeatherUpdateFromGeolocation(lastLocation);
        }

    }



    private void disableOneTimeLocationListener() {
        if (mOneTimeLocationListenerActive) {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lm.removeUpdates(mOneTimeLocationListener);
            mOneTimeLocationListenerActive = false;
        }
    }

    private LocationListener mOneTimeLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            LOGD(TAG, "Got network location update");
            mTimeoutHandler.removeCallbacksAndMessages(null);
            tryPublishWeatherUpdateFromGeolocation(location);
            disableOneTimeLocationListener();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            LOGD(TAG, "Network location provider status change: " + status);
            if (status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                scheduleRetry();
                disableOneTimeLocationListener();
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        disableOneTimeLocationListener();
    }

    private void tryPublishWeatherUpdateFromGeolocation(Location location) {
        try {
            LOGD(TAG, "Using location: " + location.getLatitude() + "," + location.getLongitude());
            tryPublishWeatherUpdateFromLocationInfo(getLocationInfo(location));
        } catch (CantGetWeatherException e) {
            publishErrorUpdate(e);
            if (e.isRetryable()) {
                scheduleRetry();
            }
        }
    }

    private void tryPublishWeatherUpdateFromLocationInfo(YahooWeatherApiClient.LocationInfo locationInfo) {
        try {
            publishWeatherUpdate(getWeatherForLocationInfo(locationInfo));
        } catch (CantGetWeatherException e) {
            publishErrorUpdate(e);
            if (e.isRetryable()) {
                scheduleRetry();
            }
        }
    }

    private void publishErrorUpdate(CantGetWeatherException e) {
        LOGE(TAG, "Showing a weather extension error", e);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("last_conditionIconId", R.drawable.ic_weather_clear);
        editor.putString("last_title", getString(R.string.status_none));
        editor.putString("last_text", getString(e.getUserFacingErrorStringId()));
        editor.putString("last_intent", sWeatherIntent.toUri(0));
        editor.commit();

        HomeServiceContract.requestPluginUpdate(this, DashClockHomeExtension.DASHCLOCK_EXTENSION_WEATHER);


    }

    private void publishWeatherUpdate(WeatherData weatherData) {
        String temperature = (weatherData.temperature != WeatherData.INVALID_TEMPERATURE)
                ? getString(R.string.temperature_template, weatherData.temperature)
                : getString(R.string.status_none);
        StringBuilder expandedBody = new StringBuilder();

        if (weatherData.low != WeatherData.INVALID_TEMPERATURE
                && weatherData.high != WeatherData.INVALID_TEMPERATURE) {
            expandedBody.append(getString(R.string.weather_low_high_template,
                    getString(R.string.temperature_template, weatherData.low),
                    getString(R.string.temperature_template, weatherData.high)));
        }

        int conditionIconId = WeatherData.getConditionIconId(weatherData.conditionCode);
        if (WeatherData.getConditionIconId(weatherData.todayForecastConditionCode)
                == R.drawable.ic_weather_raining) {
            // Show rain if it will rain today.
            conditionIconId = R.drawable.ic_weather_raining;

            if (expandedBody.length() > 0) {
                expandedBody.append(", ");
            }
            expandedBody.append(
                    getString(R.string.later_forecast_template, weatherData.forecastText));
        }

        if (expandedBody.length() > 0) {
            expandedBody.append("\n");
        }
        expandedBody.append(weatherData.location);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        String sWeatherUnits = sharedPreferences.getString(WeatherService.PREF_WEATHER_UNITS, "f");

        editor.putInt("last_conditionIconId", conditionIconId);
        editor.putString("last_title", getString(R.string.weather_expanded_title_template,
                temperature + sWeatherUnits.toUpperCase(Locale.US),
                weatherData.conditionText));
        editor.putString("last_text", expandedBody.toString());
        editor.putString("last_intent", sWeatherIntent.toUri(0));
        editor.putLong(STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS,
                SystemClock.elapsedRealtime());
        editor.commit();

        HomeServiceContract.requestPluginUpdate(this, DashClockHomeExtension.DASHCLOCK_EXTENSION_WEATHER);

        // Mark that a successful weather update has been pushed
        resetAndCancelRetries();
    }

}
