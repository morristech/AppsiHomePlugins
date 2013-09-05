package com.appsimobile.appsihomeplugins;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import com.appsimobile.appsisupport.home.FieldDataBuilder;
import com.appsimobile.appsisupport.home.FieldsBuilder;

/**
 * Created by nnma on 9/5/13.
 */
public abstract class DashClockHomeExtension {

    public static final int DASHCLOCK_EXTENSION_CALENDAR = 100;
    public static final int DASHCLOCK_EXTENSION_GMAIL = 101;
    public static final int DASHCLOCK_EXTENSION_NEXT_ALARM = 102;
    public static final int DASHCLOCK_EXTENSION_MISSED_CALLS = 103;
    public static final int DASHCLOCK_EXTENSION_SMS = 104;
    public static final int DASHCLOCK_EXTENSION_WEATHER = 105;

    private Context mContext;

    public DashClockHomeExtension(Context context) {
        mContext = context;
    }

    public Context getContext() {
        return mContext;
    }

    public String getString(int resId) {
        return mContext.getString(resId);
    }

    public String getString(int resId, Object... formatArgs) {
        return mContext.getString(resId, formatArgs);
    }

    public Resources getResources() {
        return mContext.getResources();
    }

    public ContentResolver getContentResolver() {
        return mContext.getContentResolver();
    }

    public Object getSystemService(String name) {
        return mContext.getSystemService(name);
    }
    public PackageManager getPackageManager() {
        return mContext.getPackageManager();
    }

    public abstract void onUpdateData(FieldDataBuilder builder);
    public abstract void onInitialize(FieldsBuilder builder);
}
