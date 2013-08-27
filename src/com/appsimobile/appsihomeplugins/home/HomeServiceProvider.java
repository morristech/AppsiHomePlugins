package com.appsimobile.appsihomeplugins.home;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;

import com.appsimobile.appsihomeplugins.R;
import com.appsimobile.appsisupport.home.AppsiHomeServiceProvider;
import com.appsimobile.appsisupport.home.HomeServiceContract;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by nick on 8/9/13.
 */
public class HomeServiceProvider extends AppsiHomeServiceProvider {

    // define constants, do not change after production.
    // these are used in Appsi to know the field being used

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

    public static final String ACTION_TOGGLE = "com.appsimobile.appsihomeplugins.home.ACTION_TOGGLE";

    private Handler mHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();
        Intent i = new Intent(this, CallLogMonitorService.class);
        startService(i);
    }

    /**
     * This is called when Appsi needs to update a specific field. Fill the
     * bundle with the values that need to be displayed
     * @param bundle
     * @param fieldId
     */
    @Override
    protected void updateBundleForField(Bundle bundle, int fieldId) {
        switch (fieldId) {
            case FIELD_DOWNLOADS:
                createDownloadsValues(bundle);
                break;
            case FIELD_USER_PROFILE:
                createUserProfileValues(bundle);
                break;
            case FIELD_TOGGLE_SAMPLE:
                createToggleValues(bundle);
                break;
            case FIELD_MISSED_CALLS_COUNT:
                createMissedCallCountValues(bundle);
                break;
        }
    }

    private void createMissedCallCountValues(Bundle bundle) {
        int missedCallCount = getMissedCallsCount();
        bundle.putCharSequence(HomeServiceContract.DataResponse.EXTRA_TEXT, getString(R.string.calls));

        Intent showCallLog = new Intent();
        showCallLog.setAction(Intent.ACTION_VIEW);
        showCallLog.setType(CallLog.Calls.CONTENT_TYPE);
        showCallLog.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pi = PendingIntent.getActivity(this, 1, showCallLog, PendingIntent.FLAG_UPDATE_CURRENT);

        bundle.putParcelable(HomeServiceContract.DataResponse.EXTRA_INTENT, pi);

        Bitmap image = BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo_missed_calls);
        bundle.putParcelable(HomeServiceContract.DataResponse.EXTRA_LEFT_IMAGE, image);
        bundle.putInt(HomeServiceContract.DataResponse.EXTRA_COUNT, missedCallCount);


    }


    private void createUserProfileValues(Bundle bundle) {
        Object[] photoAndName = getUserProfileData();

        Bitmap photo = (Bitmap) photoAndName[0];
        String displayName = (String) photoAndName[1];

        // we only show the image in Appsi, so we add no intent extra
        // add the photo and name
        bundle.putParcelable(HomeServiceContract.DataResponse.EXTRA_LARGE_IMAGE, photo);
        bundle.putString(HomeServiceContract.DataResponse.EXTRA_HEADER, displayName);


        StringBuffer text = new StringBuffer();
        text.append("Android ").append(Build.VERSION.RELEASE).append("\n");
        text.append(Build.MANUFACTURER).append("\n");
        text.append(Build.MODEL);
        bundle.putString(HomeServiceContract.DataResponse.EXTRA_TEXT, text.toString());
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

    private void createToggleValues(Bundle bundle) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isEnabled = preferences.getBoolean("sample_toggle", false);

        bundle.putCharSequence(HomeServiceContract.DataResponse.EXTRA_TEXT, getString(R.string.toggle_sample));

        Intent intent = new Intent(this, HomeServiceProvider.class);
        intent.setClass(this, getClass());
        intent.setAction(ACTION_TOGGLE);

        PendingIntent pi = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        bundle.putParcelable(HomeServiceContract.DataResponse.EXTRA_INTENT, pi);

        Bitmap image = BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo_toggle);
        bundle.putParcelable(HomeServiceContract.DataResponse.EXTRA_LEFT_IMAGE, image);

        bundle.putInt(HomeServiceContract.DataResponse.EXTRA_TOGGLE_COLOR, 0xdddddd);
        bundle.putInt(HomeServiceContract.DataResponse.EXTRA_TOGGLE_STATUS, isEnabled ? 200 : 40);
    }

    private void createDownloadsValues(Bundle bundle) {
        Intent i = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        updateDownloadBundle(bundle, getString(R.string.downloads), R.drawable.ic_plugin_downloads, 12, i);
    }

    private void updateDownloadBundle(Bundle bundle, String name, int drawable, int count, Intent intent) {
        bundle.putCharSequence(HomeServiceContract.DataResponse.EXTRA_TEXT, name);
        bundle.putInt(HomeServiceContract.DataResponse.EXTRA_COUNT, count);

        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        bundle.putParcelable(HomeServiceContract.DataResponse.EXTRA_INTENT, pi);

        Bitmap image = BitmapFactory.decodeResource(getResources(), drawable);
        bundle.putParcelable(HomeServiceContract.DataResponse.EXTRA_LEFT_IMAGE, image);
    }

    /**
     * Called when the service is created to setup the field you are using. Do not do complex computations
     * here. Simply register your fields.
     */
    @Override
    protected void onRegisterFields() {
        registerField(FIELD_DOWNLOADS, HomeServiceContract.FieldsResponse.DISPLAY_TYPE_SIMPLE, getString(R.string.downloads), R.drawable.ic_plugin_downloads);
        registerField(FIELD_MISSED_CALLS_COUNT, HomeServiceContract.FieldsResponse.DISPLAY_TYPE_MISSED_COUNT, getString(R.string.calls), R.drawable.ic_logo_missed_calls);
        registerField(FIELD_TOGGLE_SAMPLE, HomeServiceContract.FieldsResponse.DISPLAY_TYPE_TOGGLE_STYLE, getString(R.string.toggle_sample), R.drawable.ic_logo_toggle);
        registerField(FIELD_USER_PROFILE, HomeServiceContract.FieldsResponse.DISPLAY_PROFILE_IMAGE_STYLE, getString(R.string.profile_image), R.drawable.ic_logo_profile);
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
