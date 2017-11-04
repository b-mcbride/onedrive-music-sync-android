package com.brianhmcbride.onedrivemusicsync;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.brianhmcbride.onedrivemusicsync.data.MusicSyncDbHelper;

public class DownloadReceiver extends BroadcastReceiver {
    public static final String TAG = DownloadReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Download finished intent received");
        long downloadId = intent.getExtras().getLong(DownloadManager.EXTRA_DOWNLOAD_ID);

        MusicSyncDbHelper.getInstance(App.get()).setDriveItemDownloadComplete(downloadId, true);
    }
}
