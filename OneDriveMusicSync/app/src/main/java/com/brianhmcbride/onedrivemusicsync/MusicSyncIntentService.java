package com.brianhmcbride.onedrivemusicsync;

import android.app.DownloadManager;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.brianhmcbride.onedrivemusicsync.data.MusicSyncContract;
import com.brianhmcbride.onedrivemusicsync.data.MusicSyncDbHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import static com.brianhmcbride.onedrivemusicsync.DeltaLinkManager.ODATA_DELTA_LINK;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class MusicSyncIntentService extends IntentService {
    public static final String TAG = MusicSyncIntentService.class.getSimpleName();
    public static final String BROADCAST_SYNC_COMPLETE_ACTION = "com.brianhmcbride.onedrivemusicsync.COMPLETE";
    public static final String BROADCAST_SYNC_PARTIAL_ACTION = "com.brianhmcbride.onedrivemusicsync.PARTIAL";
    public static final String BROADCAST_DELETE_COMPLETE_ACTION = "com.brianhmcbride.onedrivemusicsync.DELETE_COMPLETE";
    public static final String BROADCAST_CLEAR_SYNCED_COLLECTION_COMPLETE_ACTION = "com.brianhmcbride.onedrivemusicsync.CLEAR_SYNCED_COLLECTION_COMPLETE";
    public static final String BROADCAST_DOWNLOADS_COMPLETE_ACTION = "com.brianhmcbride.onedrivemusicsync.DOWNLOADS_COMPLETE";


    static final String WAKE_LOCK_TAG = "com.brianhmcbride.onedrivemusicsync.wakelock";
    static final String ODATA_NEXT_LINK = "@odata.nextLink";
    static final String ACTION_DELETE = "com.brianhmcbride.onedrivemusicsync.action.DELETE";
    static final String ACTION_DOWNLOAD = "com.brianhmcbride.onedrivemusicsync.action.DOWNLOAD";
    static final String ACTION_CLEAR_SYNCED_COLLECTION = "com.brianhmcbride.onedrivemusicsync.action.CLEAR_SYNCED_COLLECTION";
    static final String MUSIC_STORAGE_FOLDER = "OneDriveMusicSync";

    /* DEVELOPMENT*/
//    static final int MAX_DOWNLOADS_TO_QUEUE = 1;
//    static final int WAIT_TIME_BETWEEN_DOWNLOAD_BATCHES = 5000;
//    static final String DRIVE_MUSIC_ROOT_URL = "https://graph.microsoft.com/v1.0/me/drive/root:/OneDriveMusicSync:/delta";
//    static final String PATH_REPLACE = "/drive/root:/OneDriveMusicSync/";
//    static final int ALLOWED_NETWORK_TYPES = DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE;

    /*DEPLOYMENT*/
    static final int MAX_DOWNLOADS_TO_QUEUE = 100;
    static final int WAIT_TIME_BETWEEN_DOWNLOAD_BATCHES = 15000;
    static final String DRIVE_MUSIC_ROOT_URL = "https://graph.microsoft.com/v1.0/me/drive/root:/Music:/delta";
    static final String PATH_REPLACE = "/drive/root:/Music/";
    static final int ALLOWED_NETWORK_TYPES = DownloadManager.Request.NETWORK_WIFI;

    public MusicSyncIntentService() {
        super("MusicSyncIntentService");
    }

    public static void startActionDelete(Context context) {
        Intent intent = new Intent(context, MusicSyncIntentService.class);
        intent.setAction(ACTION_DELETE);
        context.startService(intent);
    }

    public static void startActionDownload(Context context) {
        Intent intent = new Intent(context, MusicSyncIntentService.class);
        intent.setAction(ACTION_DOWNLOAD);
        context.startService(intent);
    }

    public static void startActionClearSyncedCollection(Context context) {
        Intent intent = new Intent(context, MusicSyncIntentService.class);
        intent.setAction(ACTION_CLEAR_SYNCED_COLLECTION);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();

            if (ACTION_DELETE.equals(action)) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);

                wakeLock.acquire();

                MusicSyncDbHelper dbHelper = new MusicSyncDbHelper(App.get());
                SQLiteDatabase dbReader = dbHelper.getReadableDatabase();
                SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();

                String[] projection = {MusicSyncContract.DriveItem._ID, MusicSyncContract.DriveItem.COLUMN_NAME_FILESYSTEM_PATH};
                String selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_IS_MARKED_FOR_DELETION);
                String[] selectionArgs = {"1"};

                Cursor cursor = dbReader.query(
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
                    String fileSystemPath = cursor.getString(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem.COLUMN_NAME_FILESYSTEM_PATH));

                    try {
                        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), fileSystemPath);

                        if (file.exists() && file.isFile()) {
                            boolean isDeleteSuccess = file.delete();

                            if (!isDeleteSuccess) {
                                showToast(String.format("Failed to delete file: %s", fileSystemPath));
                            } else {
                                // Define 'where' part of query.
                                selection = MusicSyncContract.DriveItem._ID + " = ?";
                                // Specify arguments in placeholder order.
                                selectionArgs = new String[]{String.valueOf(id)};
                                dbWriter.delete(MusicSyncContract.DriveItem.TABLE_NAME, selection, selectionArgs);
                            }
                        }
                    } catch (Exception e) {
                        String failureMessage = String.format("Failed to delete file: %s", fileSystemPath);
                        Log.e(TAG, failureMessage, e);
                        showToast(failureMessage);
                    }
                }

                cursor.close();
                dbHelper.close();
                wakeLock.release();

                broadcastStatus(BROADCAST_DELETE_COMPLETE_ACTION);
            }

            if (ACTION_CLEAR_SYNCED_COLLECTION.equals(action)) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);

                wakeLock.acquire();
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), MUSIC_STORAGE_FOLDER);

                if (file.exists() && file.isDirectory()) {
                    deleteRecursive(file);
                }

                DeltaLinkManager.getInstance().clearDeltaLink();

                MusicSyncDbHelper dbHelper = new MusicSyncDbHelper(App.get());
                SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();
                dbWriter.execSQL("DELETE FROM " + MusicSyncContract.DriveItem.TABLE_NAME);

                dbHelper.close();
                wakeLock.release();

                broadcastStatus(BROADCAST_CLEAR_SYNCED_COLLECTION_COMPLETE_ACTION);
            }

            if (ACTION_DOWNLOAD.equals(action)) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);

                wakeLock.acquire();

                MusicSyncDbHelper dbHelper = new MusicSyncDbHelper(App.get());
                SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();
                SQLiteDatabase dbReader = dbHelper.getReadableDatabase();

                String[] projection = new String[]{"COUNT(*)"};
                String selection = String.format("%s = ? AND (%s != ? AND %s IS NOT NULL)", MusicSyncContract.DriveItem.COLUMN_NAME_IS_DOWNLOAD_COMPLETE, MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID, MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID);
                String[] selectionArgs = new String[]{"0", "0"};

                Cursor cursor = dbReader.query(
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

                if (downloadsInProgress == 0) {
                    projection = new String[]{"COUNT(*)"};
                    selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_IS_DOWNLOAD_COMPLETE);
                    selectionArgs = new String[]{"0"};

                    cursor = dbReader.query(
                            MusicSyncContract.DriveItem.TABLE_NAME,
                            projection,
                            selection,
                            selectionArgs,
                            null,
                            null,
                            null
                    );

                    cursor.moveToFirst();
                    int downloadsRemaining = cursor.getInt(0);

                    if (downloadsRemaining != 0) {
                        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                        projection = new String[]{MusicSyncContract.DriveItem._ID, MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID, MusicSyncContract.DriveItem.COLUMN_NAME_FILESYSTEM_PATH};
                        selection = String.format("%s = ? OR %s IS NULL", MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID, MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID);
                        selectionArgs = new String[]{"0"};

                        cursor = dbReader.query(
                                MusicSyncContract.DriveItem.TABLE_NAME,
                                projection,
                                selection,
                                selectionArgs,
                                null,
                                null,
                                null,
                                String.valueOf(MAX_DOWNLOADS_TO_QUEUE)
                        );

                        while (cursor.moveToNext()) {
                            int id = cursor.getInt(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem._ID));
                            String driveItemId = cursor.getString(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID));
                            String fileSystemPath = cursor.getString(cursor.getColumnIndexOrThrow(MusicSyncContract.DriveItem.COLUMN_NAME_FILESYSTEM_PATH));
                            Uri downloadContentUri = Uri.parse(String.format("https://graph.microsoft.com/v1.0/me/drive/items/%s/content", driveItemId));

                            DownloadManager.Request request = new DownloadManager.Request(downloadContentUri);
                            request.addRequestHeader("Authorization", String.format("Bearer %s", AuthenticationManager.getInstance().getAccessToken()));
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileSystemPath);
                            request.setAllowedNetworkTypes(ALLOWED_NETWORK_TYPES);
                            request.setVisibleInDownloadsUi(false);
                            long downloadId = downloadManager.enqueue(request);

                            selection = String.format("%s = ?", MusicSyncContract.DriveItem._ID);
                            selectionArgs = new String[]{String.valueOf(id)};

                            ContentValues values = new ContentValues();
                            values.put(MusicSyncContract.DriveItem.COLUMN_NAME_DOWNLOAD_ID, downloadId);

                            dbWriter.update(MusicSyncContract.DriveItem.TABLE_NAME, values, selection, selectionArgs);
                        }

                        cursor.close();
                    } else{
                        broadcastStatus(BROADCAST_DOWNLOADS_COMPLETE_ACTION);
                    }
                }

                dbHelper.close();
                wakeLock.release();
            }
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        boolean isSuccess = fileOrDirectory.delete();

        if (!isSuccess) {
            showToast(String.format("Failure deleting %s", fileOrDirectory.getName()));
        }
    }

    public void SyncMusic(String deltaLink, String nextLink) {
        RequestQueue queue = Volley.newRequestQueue(App.get());
        JSONObject parameters = new JSONObject();

        try {
            parameters.put("key", "value");
        } catch (Exception e) {
            Log.e(TAG, "Failed to put parameters", e);
        }

        String url = deltaLink == null ? nextLink : deltaLink;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                parameters,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        if (response.has("value")) {
                            MusicSyncDbHelper dbHelper = null;
                            try {
                                dbHelper = new MusicSyncDbHelper(App.get());
                                SQLiteDatabase dbReader = dbHelper.getReadableDatabase();
                                SQLiteDatabase dbWriter = dbHelper.getWritableDatabase();

                                JSONArray driveItemArray = response.getJSONArray("value");

                                for (int i = 0; i < driveItemArray.length(); i++) {
                                    try {
                                        JSONObject driveItem = driveItemArray.getJSONObject(i);

                                        if (driveItem.has("file")) {
                                            String id = driveItem.getString("id");
                                            String name = driveItem.getString("name");
                                            boolean isDeletedFile = driveItem.has("deleted");

                                            if (driveItem.getString("name").contains(".m4a") || driveItem.getString("name").contains("mp3")) {
                                                String[] projection = {MusicSyncContract.DriveItem._ID, MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID};
                                                String selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID);
                                                String[] selectionArgs = {id};

                                                Cursor cursor = dbReader.query(
                                                        MusicSyncContract.DriveItem.TABLE_NAME,
                                                        projection,
                                                        selection,
                                                        selectionArgs,
                                                        null,
                                                        null,
                                                        null
                                                );

                                                boolean driveItemExistsInDatabase = cursor.getCount() > 0;
                                                cursor.close();

                                                String parentPath = driveItem.getJSONObject("parentReference").getString("path").replace(PATH_REPLACE, "");
                                                String filePath = String.format("%s/%s/%s", MUSIC_STORAGE_FOLDER, parentPath, name);
                                                filePath = URLDecoder.decode(filePath, "UTF-8");

                                                if (isDeletedFile) {
                                                    if (driveItemExistsInDatabase) {
                                                        ContentValues values = new ContentValues();
                                                        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_IS_MARKED_FOR_DELETION, true);

                                                        dbWriter.update(MusicSyncContract.DriveItem.TABLE_NAME, values, selection, selectionArgs);
                                                    }
                                                } else {
                                                    if (!driveItemExistsInDatabase) {

                                                        ContentValues values = new ContentValues();
                                                        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_DRIVE_ITEM_ID, id);
                                                        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_IS_DOWNLOAD_COMPLETE, false);
                                                        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_IS_MARKED_FOR_DELETION, false);
                                                        values.put(MusicSyncContract.DriveItem.COLUMN_NAME_FILESYSTEM_PATH, filePath);

                                                        dbWriter.insert(MusicSyncContract.DriveItem.TABLE_NAME, null, values);
                                                    } else {
                                                        //Update scenario TBD
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        String failureMessage = String.format("Failure working with driveItem. %s", driveItemArray.getJSONObject(i).toString());
                                        Log.e(TAG, failureMessage, e);
                                        showToast("Music sync failure on driveItem");
                                    }
                                }
                            } catch (Exception e) {
                                String failureMessage = "Unrecoverable failure.";
                                Log.e(TAG, failureMessage, e);
                                showToast(String.format("Unknown music sync failure:%s", e.getMessage()));
                            } finally {
                                if (dbHelper != null) {
                                    dbHelper.close();
                                }
                            }
                        }

                        try {
                            if (response.has(ODATA_NEXT_LINK)) {
                                broadcastStatus(BROADCAST_SYNC_PARTIAL_ACTION);
                                SyncMusic(null, response.getString(ODATA_NEXT_LINK));
                            }
                        } catch (Exception e) {
                            String failureMessage = "Unable to access odata next link.";
                            Log.e(TAG, failureMessage, e);
                            showToast(failureMessage);
                        }

                        try {
                            if (response.has(DeltaLinkManager.ODATA_DELTA_LINK)) {
                                DeltaLinkManager.getInstance().setDeltaLink(response.getString(ODATA_DELTA_LINK));

                                broadcastStatus(BROADCAST_SYNC_COMPLETE_ACTION);
                            }
                        } catch (Exception e) {
                            String failureMessage = "Unable to access odata delta link OR setting preferences.";
                            Log.e(TAG, failureMessage, e);
                            showToast(failureMessage);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, String.format("Error: %s", error.toString()));
                        showToast("Failure syncing music. Review logs");
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", String.format("Bearer %s", AuthenticationManager.getInstance().getAccessToken()));
                return headers;
            }
        };

        Log.d(TAG, String.format("Adding HTTP GET to Queue, Request: %s", request.toString()));

        request.setRetryPolicy(new DefaultRetryPolicy(
                3000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(request);
    }

    private void broadcastStatus(String action) {
        Intent localIntent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    protected void showToast(final String msg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                // run this code in the main thread
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}