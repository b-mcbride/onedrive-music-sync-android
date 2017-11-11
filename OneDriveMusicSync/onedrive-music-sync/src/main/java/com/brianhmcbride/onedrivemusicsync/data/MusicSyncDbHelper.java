package com.brianhmcbride.onedrivemusicsync.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.SparseArray;

import com.brianhmcbride.onedrivemusicsync.App;

import java.util.ArrayList;

public class MusicSyncDbHelper extends SQLiteOpenHelper {
    private static MusicSyncDbHelper sInstance;

    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "MusicSync.db";

    public static synchronized MusicSyncDbHelper getInstance(Context context) {

        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            sInstance = new MusicSyncDbHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Constructor should be private to prevent direct instantiation.
     * make call to static method "getInstance()" instead.
     */
    private MusicSyncDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(MusicSyncContract.SQL_CREATE_ENTRIES_DRIVE_ITEM);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(MusicSyncContract.SQL_DELETE_ENTRIES_DRIVE_ITEM);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public int getNumberOfDriveItemsMarkedForDeletion() {
        String[] projection = new String[]{"COUNT(*)"};
        String selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_IS_MARKED_FOR_DELETION);
        String[] selectionArgs = new String[]{"1"};

        Cursor cursor = this.getReadableDatabase().query(
                MusicSyncContract.DriveItem.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        cursor.moveToFirst();
        int numberOfMarkedDeletions = cursor.getInt(0);
        cursor.close();

        return numberOfMarkedDeletions;
    }

    public int getNumberOfDriveItemDownloads() {
        String[] projection = new String[]{"COUNT(*)"};
        String selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_IS_DOWNLOAD_COMPLETE);
        String[] selectionArgs = new String[]{"0"};

        Cursor cursor = this.getReadableDatabase().query(
                MusicSyncContract.DriveItem.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        cursor.moveToFirst();
        int numberOfDownloads = cursor.getInt(0);
        cursor.close();

        return numberOfDownloads;
    }

    public void setDriveItemDownloadComplete(long downloadId, boolean isDownloadComplete) {
        String selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID);
        String[] selectionArgs = {String.valueOf(downloadId)};

        ContentValues values = new ContentValues();
        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_IS_DOWNLOAD_COMPLETE, isDownloadComplete);

        this.getWritableDatabase().update(MusicSyncContract.DriveItem.TABLE_NAME, values, selection, selectionArgs);
    }

    public void deleteDriveItem(int id) {
        String selection = MusicSyncContract.DriveItem._ID + " = ?";
        String[] selectionArgs = new String[]{String.valueOf(id)};
        this.getWritableDatabase().delete(MusicSyncContract.DriveItem.TABLE_NAME, selection, selectionArgs);
    }

    public void deleteAllDriveItems() {
        this.getWritableDatabase().execSQL("DELETE FROM " + MusicSyncContract.DriveItem.TABLE_NAME);
    }

    public SparseArray<Long> getDriveItemsMarkedForDeletion() {
        SparseArray<Long> itemsMarkedForDeletion = new SparseArray<>();

        String[] projection = {MusicSyncContract.DriveItem._ID, MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID};
        String selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_IS_MARKED_FOR_DELETION);
        String[] selectionArgs = {"1"};

        Cursor cursor = this.getReadableDatabase().query(
                MusicSyncContract.DriveItem.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem._ID));
            long downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID));

            itemsMarkedForDeletion.put(id, downloadId);
        }

        cursor.close();
        return itemsMarkedForDeletion;
    }

    public int getNumberOfDriveItemDownloadsInProgress() {
        String[] projection = new String[]{"COUNT(*)"};
        String selection = String.format("%s = ? AND (%s != ? AND %s IS NOT NULL)", MusicSyncContract.DriveItem.COLUMN_NAME_IS_DOWNLOAD_COMPLETE, MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID, MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID);
        String[] selectionArgs = new String[]{"0", "0"};

        Cursor cursor = this.getReadableDatabase().query(
                MusicSyncContract.DriveItem.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        cursor.moveToFirst();
        int downloadsInProgress = cursor.getInt(0);
        cursor.close();
        return downloadsInProgress;
    }

    public void setDriveItemDownloadId(int id, long downloadId) {
        String selection = String.format("%s = ?", MusicSyncContract.DriveItem._ID);
        String[] selectionArgs = new String[]{String.valueOf(id)};

        ContentValues values = new ContentValues();
        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID, downloadId);

        this.getWritableDatabase().update(MusicSyncContract.DriveItem.TABLE_NAME, values, selection, selectionArgs);
    }

    public void insertDriveItem(String driveItemId, boolean isDownloadComplete, boolean isMarkedForDeletion, String filePath) {
        ContentValues values = new ContentValues();
        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID, driveItemId);
        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_IS_DOWNLOAD_COMPLETE, isDownloadComplete);
        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_IS_MARKED_FOR_DELETION, isMarkedForDeletion);
        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_FILESYSTEM_PATH, filePath);

        this.getWritableDatabase().insert(MusicSyncContract.DriveItem.TABLE_NAME, null, values);
    }

    public void setDriveItemMarkedForDeletion(String driveItemId, boolean isMarkedForDeletion) {
        String selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID);
        String[] selectionArgs = {driveItemId};
        ContentValues values = new ContentValues();
        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_IS_MARKED_FOR_DELETION, isMarkedForDeletion);

        this.getWritableDatabase().update(MusicSyncContract.DriveItem.TABLE_NAME, values, selection, selectionArgs);
    }

    public ArrayList<DriveItem> getDriveItemsToDownload(int limit) {
        ArrayList<DriveItem> driveItems = new ArrayList<>();

        String[] projection = new String[]{MusicSyncContract.DriveItem._ID, MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID, MusicSyncContract.DriveItem.COLUMN_NAME_FILESYSTEM_PATH};
        String selection = String.format("%s = ? OR %s IS NULL", MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID, MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID);
        String[] selectionArgs = new String[]{"0"};

        Cursor cursor = MusicSyncDbHelper.getInstance(App.get()).getReadableDatabase().query(
                MusicSyncContract.DriveItem.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null,
                String.valueOf(limit)
        );

        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem._ID));
            String driveItemId = cursor.getString(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID));
            String fileSystemPath = cursor.getString(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem.COLUMN_NAME_FILESYSTEM_PATH));

            driveItems.add(new DriveItem(id, driveItemId, fileSystemPath));
        }

        cursor.close();
        return driveItems;
    }

    public DriveItem getDriveItemByDriveItemId(String driveItemId) {
        DriveItem driveItem = null;

        String[] projection = new String[]{MusicSyncContract.DriveItem._ID, MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID, MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID};
        String selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID);
        String[] selectionArgs = new String[]{driveItemId};

        Cursor cursor = MusicSyncDbHelper.getInstance(App.get()).getReadableDatabase().query(
                MusicSyncContract.DriveItem.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null);

        if (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem._ID));
            String dbDriveItemId = cursor.getString(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID));
            long downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID));

            driveItem = new DriveItem(id, dbDriveItemId, downloadId);
        }

        cursor.close();

        return driveItem;
    }

    public ArrayList<Long> getAllDownloadIds() {
        ArrayList<Long> downloadIds = new ArrayList<>();

        String[] projection = new String[]{MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID};

        Cursor cursor = MusicSyncDbHelper.getInstance(App.get()).getReadableDatabase().query(
                MusicSyncContract.DriveItem.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                null);

        while (cursor.moveToNext()) {
            downloadIds.add(cursor.getLong(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID)));
        }

        cursor.close();

        return downloadIds;
    }

    public int getDriveItemCount(){
        String[] projection = new String[]{"COUNT(*)"};

        Cursor cursor = this.getReadableDatabase().query(
                MusicSyncContract.DriveItem.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                null
        );

        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }
}