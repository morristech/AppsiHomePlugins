package com.appsimobile.appsihomeplugins.home;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
import android.util.SparseArray;

import com.appsimobile.appsihomeplugins.DashClockHomeExtension;
import com.appsimobile.appsihomeplugins.R;
import com.appsimobile.appsihomeplugins.dashclock.calendar.CalendarExtension;
import com.appsimobile.appsihomeplugins.dashclock.gmail.GmailExtension;
import com.appsimobile.appsihomeplugins.dashclock.nextalarm.NextAlarmExtension;
import com.appsimobile.appsihomeplugins.dashclock.phone.MissedCallsExtension;
import com.appsimobile.appsihomeplugins.dashclock.phone.SmsExtension;
import com.appsimobile.appsihomeplugins.dashclock.weather.WeatherExtension;
import com.appsimobile.appsisupport.home.AppsiHomeServiceProvider;
import com.appsimobile.appsisupport.home.FieldsBuilder;
import com.appsimobile.appsisupport.home.HomeServiceContract;
import com.appsimobile.appsisupport.internal.FieldValues;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Scanner;

/**
 * Created by nick on 8/9/13.
 */
public class HomeServiceProvider extends AppsiHomeServiceProvider {

    // define constants, do not change after production.
    // these are used in Appsi to know the field being used

    final SparseArray<DashClockHomeExtension> mDashClockHomeExtensions = new SparseArray<DashClockHomeExtension>();

    /**
     * This field opens the downloads list
     */
    public static final int FIELD_DOWNLOADS = 1;
    /**
     * shows the user profile image
     */
    public static final int FIELD_USER_PROFILE = 2;
    /**
     * This fields shows a toggle sample
     */
    public static final int FIELD_TOGGLE_SAMPLE = 3;
    /**
     * Show a total missed call count
     * Note that this field is not being updated properly when
     * the call log is updated.
     */
    public static final int FIELD_MISSED_CALLS_COUNT = 4;

    /**
     * Show a tethering info field
     */
    public static final int FIELD_TETHERING_INFO = 5;

    public static final String ACTION_TOGGLE = "com.appsimobile.appsihomeplugins.home.ACTION_TOGGLE";

    private Handler mHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        mDashClockHomeExtensions.put(DashClockHomeExtension.DASHCLOCK_EXTENSION_CALENDAR, new CalendarExtension(this));
        mDashClockHomeExtensions.put(DashClockHomeExtension.DASHCLOCK_EXTENSION_GMAIL, new GmailExtension(this));
        mDashClockHomeExtensions.put(DashClockHomeExtension.DASHCLOCK_EXTENSION_NEXT_ALARM, new NextAlarmExtension(this));
        mDashClockHomeExtensions.put(DashClockHomeExtension.DASHCLOCK_EXTENSION_MISSED_CALLS, new MissedCallsExtension(this));
        mDashClockHomeExtensions.put(DashClockHomeExtension.DASHCLOCK_EXTENSION_SMS, new SmsExtension(this));
        mDashClockHomeExtensions.put(DashClockHomeExtension.DASHCLOCK_EXTENSION_WEATHER, new WeatherExtension(this));
        mHandler = new Handler();
    }


    /**
     * This is called when Appsi needs to update a specific field. Fill the
     * bundle with the values that need to be displayed
     * @param builder
     * @param fieldId
     */
    @Override
    protected void updateBundleForField(FieldValues.Builder builder, int fieldId, int reason) {
        Log.i("HomeServiceProvider", "updating field: " + fieldId);
        switch (fieldId) {
            case FIELD_DOWNLOADS:
                createDownloadsValues(builder);
                break;
            case FIELD_USER_PROFILE:
                createUserProfileValues(builder);
                break;
            case FIELD_TOGGLE_SAMPLE:
                createToggleValues(builder);
                break;
            case FIELD_MISSED_CALLS_COUNT:
                createMissedCallCountValues(builder);
                break;
            case FIELD_TETHERING_INFO:
                createWifiFields(builder);
                break;
            default:
                mDashClockHomeExtensions.get(fieldId).onUpdateData(builder);
        }
    }

    private void createMissedCallCountValues(FieldValues.Builder builder) {
        int missedCallCount = getMissedCallsCount();
        builder.text(getString(R.string.calls));

        Intent showCallLog = new Intent();
        showCallLog.setAction(Intent.ACTION_VIEW);
        showCallLog.setType(CallLog.Calls.CONTENT_TYPE);
        showCallLog.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pi = PendingIntent.getActivity(this, 1, showCallLog, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.intent(pi);

        builder.leftImageResId(R.drawable.ic_logo_missed_calls);
        builder.amount("" + missedCallCount);


    }


    private void createWifiFields(FieldValues.Builder builder) {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID();

        builder.header(ssid);

        boolean wifiApEnabled = false;

        boolean wifiEnabled = wifiManager.isWifiEnabled();

        try {
            Method method = WifiManager.class.getMethod("isWifiApEnabled");
            wifiApEnabled = (Boolean) method.invoke(wifiManager);
        } catch (Exception e) {
            Log.e("HomeServiceProvider", "error getting AP state", e);
        }
        if (wifiApEnabled) {
            try {
                Scanner connectionsScanner = new Scanner(new File("/proc/net/arp"));

                Integer connectionsCount = 0;
                try {
                    while (connectionsScanner.hasNextLine()) {
                        connectionsScanner.nextLine();
                        connectionsCount++;
                    }
                    // remove header line
                    connectionsCount = connectionsCount - 1;
                } finally {
                    connectionsScanner.close();
                }

                String text = getResources().getQuantityString(R.plurals.connection_count, connectionsCount, connectionsCount);
                builder.text(text);

                Method getWifiApConfiguration = WifiManager.class.getDeclaredMethod("getWifiApConfiguration");
                builder.header(((WifiConfiguration) getWifiApConfiguration.invoke(wifiManager)).SSID);

                ComponentName comp = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");

                Intent intent = new Intent();
                intent.setComponent(comp);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, FIELD_TETHERING_INFO, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                builder.intent(pendingIntent);

            } catch (IOException e) {
                Log.e("HomeServiceProvider", "error reading from /proc/net/arp", e);
            } catch (Exception e) {
                Log.e("HomeServiceProvider", "error updating hotspot configuration", e);

            }
        } else if (wifiEnabled) {

            SupplicantState supplicantState = wifiInfo.getSupplicantState();
            int connectionStatusResId = 0;
            switch (supplicantState) {
                case ASSOCIATED:
                    connectionStatusResId = R.string.network_state_associated;
                    break;
                case ASSOCIATING:
                    connectionStatusResId = R.string.network_state_associating;
                    break;
                case AUTHENTICATING:
                    connectionStatusResId = R.string.network_state_authenticating;
                    break;
                case COMPLETED:
                    connectionStatusResId = R.string.network_state_completed;
                    break;
                case DISCONNECTED:
                    connectionStatusResId = R.string.network_state_disconnected;
                    break;
                case DORMANT:
                    connectionStatusResId = R.string.network_state_doramnt;
                    break;
                case FOUR_WAY_HANDSHAKE:
                    connectionStatusResId = R.string.network_state_fwh;
                    break;
                case GROUP_HANDSHAKE:
                    connectionStatusResId = R.string.network_state_gh;
                    break;
                case INACTIVE:
                    connectionStatusResId = R.string.network_state_inactive;
                    break;
                case INTERFACE_DISABLED:
                    connectionStatusResId = R.string.network_state_interface_disabled;
                    break;
                case INVALID:
                    connectionStatusResId = R.string.network_state_invalid;
                    break;
                case SCANNING:
                    connectionStatusResId = R.string.network_state_scanning;
                    break;
                case UNINITIALIZED:
                    connectionStatusResId = R.string.network_state_uninitialized;
                    break;

            }
            if (R.string.network_state_completed == connectionStatusResId) {
                int ip = wifiInfo.getIpAddress();
                String ipAddress = Formatter.formatIpAddress(ip);

                builder.text(ipAddress);
            } else {
                if (connectionStatusResId != 0) {
                    builder.text(getString(connectionStatusResId));
                }
            }
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, FIELD_TETHERING_INFO, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.intent(pendingIntent);


        } else {
            Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, FIELD_TETHERING_INFO, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.intent(pendingIntent);


            ConnectivityManager conMgr =  (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo i = conMgr.getActiveNetworkInfo();

            boolean isConnected = i != null && i.isConnected();


            if (!isConnected) {
                builder.header(getString(R.string.no_connections));
                builder.text("--");
                builder.leftImageResId(R.drawable.ic_disconnected);
                return;
            }

            int titleResId = 0;
            int imageResId = 0;
            int textResId = 0;
            boolean useSubTypeAsDescription = false;

            switch (i.getType()) {
                case ConnectivityManager.TYPE_ETHERNET:
                    titleResId = R.string.conn_eth;
                    imageResId = R.drawable.ic_settings_data;
                    break;
                case ConnectivityManager.TYPE_WIFI:
                    titleResId = R.string.conn_wifi;
                    imageResId = R.drawable.ic_mobile_conn;
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    titleResId = R.string.conn_mobile;
                    imageResId = R.drawable.ic_settings_data;
                    if (i.isRoaming()) {
                        textResId = R.string.roaming;
                    } else {
                        useSubTypeAsDescription = true;
                    }
                    break;
                case ConnectivityManager.TYPE_WIMAX:
                    titleResId = R.string.conn_wimax;
                    imageResId = R.drawable.ic_wimax;
                    break;
                case ConnectivityManager.TYPE_BLUETOOTH:
                    titleResId = R.string.conn_bluetooth;
                    imageResId = R.drawable.ic_settings_bluetooth2;
                    break;
            }

            builder.leftImageResId(imageResId);
            builder.header(titleResId == 0 ? "--" : getString(titleResId));
            String alt = useSubTypeAsDescription ? i.getSubtypeName() : "--";
            builder.text(textResId == 0 ? alt : getString(textResId));

            return;
        }
        int icon = wifiApEnabled ? R.drawable.ic_plugin_tethering : R.drawable.ic_settings_wireless;
        builder.leftImageResId(icon);



    }


    private void createUserProfileValues(FieldValues.Builder builder) {
        Object[] photoAndName = getUserProfileData();

        Bitmap photo = (Bitmap) photoAndName[0];
        String displayName = (String) photoAndName[1];

        // we only show the image in Appsi, so we add no intent extra
        // add the photo and name
        builder.largeImage(photo);
        builder.header(displayName);

        StringBuffer text = new StringBuffer();
        text.append("Android ").append(Build.VERSION.RELEASE).append("\n");
        text.append(Build.MANUFACTURER).append("\n");
        text.append(Build.MODEL);
        builder.text(text.toString());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(action)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean isEnabled = preferences.getBoolean("sample_toggle", false);

            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("sample_toggle", !isEnabled);
            editor.apply();

            HomeServiceContract.requestPluginUpdate(this, FIELD_TOGGLE_SAMPLE);
        }
        stopSelf();
        return Service.START_NOT_STICKY;
    }

    private void createToggleValues(FieldValues.Builder builder) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isEnabled = preferences.getBoolean("sample_toggle", false);

        builder.text(getString(R.string.toggle_sample));

        Intent intent = new Intent(this, HomeServiceProvider.class);
        intent.setClass(this, getClass());
        intent.setAction(ACTION_TOGGLE);

        PendingIntent pi = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.intent(pi);

        builder.leftImageResId(R.drawable.ic_logo_toggle);

        builder.toggle(isEnabled ? 200 : 40, 0xdddddd);
    }

    private void createDownloadsValues(FieldValues.Builder builder) {
        Intent i = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        updateDownloadBundle(builder, getString(R.string.downloads), R.drawable.ic_plugin_downloads, 12, i);
    }

    private void updateDownloadBundle(FieldValues.Builder builder, String name, int drawable, int count, Intent intent) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.text(name)
                .amount("" + count)
                .intent(pi)
                .leftImageResId(drawable);
    }

    /**
     * Called when the service is created to setup the field you are using. Do not do complex computations
     * here. Simply register your fields.
     */
    @Override
    protected void onRegisterFields(FieldsBuilder builder) {
        Log.d("HomeServiceProvider", "register fields");
        builder.registerField(FIELD_DOWNLOADS, HomeServiceContract.FieldsResponse.DISPLAY_TYPE_SIMPLE, R.string.downloads, R.drawable.ic_plugin_downloads, null);
        builder.registerField(FIELD_MISSED_CALLS_COUNT, HomeServiceContract.FieldsResponse.DISPLAY_TYPE_MISSED_COUNT, R.string.calls, R.drawable.ic_logo_missed_calls, null, CallLog.CONTENT_URI.toString());
        //builder.registerField(FIELD_TOGGLE_SAMPLE, HomeServiceContract.FieldsResponse.DISPLAY_TYPE_TOGGLE_STYLE, R.string.toggle_sample, R.drawable.ic_logo_toggle, null);
        builder.registerField(FIELD_USER_PROFILE, HomeServiceContract.FieldsResponse.DISPLAY_PROFILE_IMAGE_STYLE,R.string.profile_image, R.drawable.ic_logo_profile, null);
        builder.registerField(FIELD_TETHERING_INFO, HomeServiceContract.FieldsResponse.DISPLAY_TYPE_DASHCLOCK,R.string.wifi_info, R.drawable.ic_plugin_tethering, null);
        int count = mDashClockHomeExtensions.size();
        for (int i = 0; i < count; i++) {
            int key = mDashClockHomeExtensions.keyAt(i);
            mDashClockHomeExtensions.get(key).onInitialize(builder);
        }
    }

    private int getMissedCallsCount() {
        String[] projection = { CallLog.Calls.TYPE };
        String where = CallLog.Calls.TYPE + "=? AND " + CallLog.Calls.IS_READ + "=?";

        Cursor c = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                projection,
                where,
                new String[] {
                    "" + CallLog.Calls.MISSED_TYPE,
                    "0"
                },
                null);
        if (c == null) return 0;
        try {
            return c.getCount();
        } finally {
            c.close();
        }
    }

    private Object[] getUserProfileData() {
        ContentResolver contentResolver = getContentResolver();

        Cursor cursor = contentResolver.query(
                ContactsContract.Profile.CONTENT_URI,
                new String[]{
                        ContactsContract.Profile.PHOTO_URI,
                        ContactsContract.Profile.DISPLAY_NAME,
                        ContactsContract.Profile._ID,
                },
                null,
                null,
                null);

        Bitmap photo = null;
        String name = null;

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String uriString = cursor.getString(0); // index 0 = photo uri
                    if (uriString != null) {
                        Uri uri = Uri.parse(uriString);
                        try {
                            InputStream in = contentResolver.openInputStream(uri);
                            photo = BitmapFactory.decodeStream(in);
                        } catch (IOException e) {
                            Log.w("HomeServiceProvider", "error loading profile image", e);
                        }
                    }
                    name = cursor.getString(1); // index 1: displayName
                    if (name != null && photo != null) break;
                }
            } finally {
                cursor.close();
            }
        }
        return new Object[] {photo, name};
    }

}
