package com.brianhmcbride.onedrivemusicsync;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.brianhmcbride.onedrivemusicsync.data.MusicSyncContract;
import com.brianhmcbride.onedrivemusicsync.data.MusicSyncDbHelper;

public class DownloadReceiver extends BroadcastReceiver {
    public static final String TAG = DownloadReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Download finished intent received");
        long downloadId = intent.getExtras().getLong(DownloadManager.EXTRA_DOWNLOAD_ID);

        String selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID);
        String[] selectionArgs = {String.valueOf(downloadId)};

        ContentValues values = new ContentValues();
        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_IS_DOWNLOAD_COMPLETE, true);

        MusicSyncDbHelper dbHelper = new MusicSyncDbHelper(App.get());
        SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();

        dbWriter.update(MusicSyncContract.DriveItem.TABLE_NAME, values, selection, selectionArgs);

        dbHelper.close();
    }
}
