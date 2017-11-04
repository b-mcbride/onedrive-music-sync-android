package com.brianhmcbride.onedrivemusicsync.data;

import android.provider.BaseColumns;

public final class MusicSyncContract {
    private MusicSyncContract() {
    }

    public static class DriveItem implements BaseColumns {
        public static final String TABLE_NAME = "DriveItem";
        public static final String COLUMN_NAME_DRIVE_ITEM_ID = "DriveItemId";
        public static final String COLUMN_NAME_FILESYSTEM_PATH = "FileSystemPath";
        public static final String COLUMN_NAME_DOWNLOAD_ID = "DownloadId";
        public static final String COLUMN_NAME_IS_DOWNLOAD_COMPLETE = "IsDownloadComplete";
        public static final String COLUMN_NAME_IS_MARKED_FOR_DELETION = "IsMarkedForDeletion";
    }

    public static final String SQL_CREATE_ENTRIES_DRIVE_ITEM =
            "CREATE TABLE " + DriveItem.TABLE_NAME + " (" +
                    DriveItem._ID + " INTEGER PRIMARY KEY," +
                    DriveItem.COLUMN_NAME_DRIVE_ITEM_ID + " TEXT NOT NULL," +
                    DriveItem.COLUMN_NAME_FILESYSTEM_PATH + " TEXT NOT NULL," +
                    DriveItem.COLUMN_NAME_DOWNLOAD_ID + " BIGINT," +
                    DriveItem.COLUMN_NAME_IS_DOWNLOAD_COMPLETE + " BOOLEAN NOT NULL CHECK (" + DriveItem.COLUMN_NAME_IS_DOWNLOAD_COMPLETE + " IN (0,1))," +
                    DriveItem.COLUMN_NAME_IS_MARKED_FOR_DELETION + " BOOLEAN NOT NULL CHECK (" + DriveItem.COLUMN_NAME_IS_MARKED_FOR_DELETION + " IN (0,1)))";

    public static final String SQL_DELETE_ENTRIES_DRIVE_ITEM =
            "DROP TABLE IF EXISTS " + DriveItem.TABLE_NAME;
}
