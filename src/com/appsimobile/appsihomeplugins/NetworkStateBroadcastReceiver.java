package com.appsimobile.appsihomeplugins;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.appsimobile.appsihomeplugins.home.HomeServiceProvider;
import com.appsimobile.appsisupport.home.HomeServiceContract;

/**
 * Created by nick on 9/6/13.
 */
public class NetworkStateBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        HomeServiceContract.requestPluginUpdate(context, HomeServiceProvider.FIELD_TETHERING_INFO);
    }
}
