package com.appsimobile.appsihomeplugins.home;

import android.app.Service;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CallLog;

import com.appsimobile.appsisupport.home.HomeServiceContract;

import org.jetbrains.annotations.Nullable;

/**
 * This service monitors the call log content uri.
 * Once something changes in there, it will request an
 * update for the calls count field
 * Created by nick on 8/24/13.
 */
public class CallLogMonitorService extends Service {

    private ContentObserver mContentObserver;
    private Handler mHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();
        mContentObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                HomeServiceContract.requestPluginUpdate(CallLogMonitorService.this, HomeServiceProvider.FIELD_MISSED_CALLS_COUNT);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                HomeServiceContract.requestPluginUpdate(CallLogMonitorService.this, HomeServiceProvider.FIELD_MISSED_CALLS_COUNT);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        getContentResolver().registerContentObserver(CallLog.CONTENT_URI, false, mContentObserver);
        HomeServiceContract.requestPluginUpdate(this, HomeServiceProvider.FIELD_MISSED_CALLS_COUNT);
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
