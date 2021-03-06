package com.brianhmcbride.onedrivemusicsync;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.brianhmcbride.onedrivemusicsync.data.DriveItem;
import com.brianhmcbride.onedrivemusicsync.data.MusicSyncDbHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
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
    public static final String BROADCAST_MOVE_COMPLETE_ACTION = "com.brianhmcbride.onedrivemusicsync.MOVE_COMPLETE";
    public static final String BROADCAST_CLEAR_SYNCED_COLLECTION_COMPLETE_ACTION = "com.brianhmcbride.onedrivemusicsync.CLEAR_SYNCED_COLLECTION_COMPLETE";
    public static final String BROADCAST_DOWNLOADS_COMPLETE_ACTION = "com.brianhmcbride.onedrivemusicsync.DOWNLOADS_COMPLETE";
    public static final String BROADCAST_DOWNLOADS_IN_PROGESS_ACTION = "com.brianhmcbride.onedrivemusicsync.DOWNLOADS_IN_PROGRESS";


    static final String WAKE_LOCK_TAG = "com.brianhmcbride.onedrivemusicsync.wakelock";
    static final String ODATA_NEXT_LINK = "@odata.nextLink";
    static final String ACTION_DELETE = "com.brianhmcbride.onedrivemusicsync.action.DELETE";
    static final String ACTION_MOVE = "com.brianhmcbride.onedrivemusicsync.action.MOVE";
    static final String ACTION_DOWNLOAD = "com.brianhmcbride.onedrivemusicsync.action.DOWNLOAD";
    static final String ACTION_CLEAR_SYNCED_COLLECTION = "com.brianhmcbride.onedrivemusicsync.action.CLEAR_SYNCED_COLLECTION";
    static final String MUSIC_STORAGE_FOLDER = "OneDriveMusicSync";
    static final String TMP_MUSIC_STORAGE_FOLDER = "tmp_OneDriveMusicSync";

    /* DEVELOPMENT*/
//    static final int MAX_DOWNLOADS_TO_QUEUE = 1;
//    static final int WAIT_TIME_BETWEEN_DOWNLOAD_BATCHES = 5000;
//    static final String DRIVE_MUSIC_ROOT_URL = "https://graph.microsoft.com/v1.0/me/drive/root:/OneDriveMusicSync:/delta";
//    static final String PATH_REPLACE = "/drive/root:/OneDriveMusicSync/";
//    static final int ALLOWED_NETWORK_TYPES = DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE;

    /*DEPLOYMENT*/
    static final int MAX_DOWNLOADS_TO_QUEUE = 100;
    static final int WAIT_TIME_BETWEEN_DOWNLOAD_BATCHES = 30000;
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

    public static void startActionMove(Context context) {
        Intent intent = new Intent(context, MusicSyncIntentService.class);
        intent.setAction(ACTION_MOVE);
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

                SparseArray<Long> itemsMarkedForDeletion = MusicSyncDbHelper.getInstance(App.get()).getDriveItemsMarkedForDeletion();
                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                for (int i = 0; i < itemsMarkedForDeletion.size(); i++) {
                    int id = itemsMarkedForDeletion.keyAt(i);
                    long downloadId = itemsMarkedForDeletion.get(id);
                    delete(id, downloadId, downloadManager);
                }

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

                ArrayList<Long> allDownloadIds = MusicSyncDbHelper.getInstance(App.get()).getAllDownloadIds();
                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                for (long downloadId : allDownloadIds) {
                    try {
                        downloadManager.remove(downloadId);
                    } catch (Exception e) {
                        @SuppressLint("DefaultLocale") String failureMessage = String.format("Failed to delete downloadId: %d", downloadId);
                        Log.e(TAG, failureMessage, e);
                        showToast(failureMessage);
                    }
                }

                MusicSyncDbHelper.getInstance(App.get()).deleteAllDriveItems();

                wakeLock.release();

                broadcastStatus(BROADCAST_CLEAR_SYNCED_COLLECTION_COMPLETE_ACTION);
            }

            if (ACTION_DOWNLOAD.equals(action)) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);

                wakeLock.acquire();

                int downloadsInProgress = MusicSyncDbHelper.getInstance(App.get()).getNumberOfDriveItemDownloadsInProgress();

                if (downloadsInProgress == 0) {
                    int downloadsRemaining = MusicSyncDbHelper.getInstance(App.get()).getNumberOfDriveItemDownloads();

                    if (downloadsRemaining != 0) {
                        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                        ArrayList<DriveItem> driveItems = MusicSyncDbHelper.getInstance(App.get()).getDriveItemsToDownload(MAX_DOWNLOADS_TO_QUEUE);

                        for (DriveItem driveItem : driveItems) {
                            Uri downloadContentUri = Uri.parse(String.format("https://graph.microsoft.com/v1.0/me/drive/items/%s/content", driveItem.getDriveItemId()));

                            DownloadManager.Request request = new DownloadManager.Request(downloadContentUri);
                            request.addRequestHeader("Authorization", String.format("Bearer %s", AuthenticationManager.getInstance().getAccessToken()));
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, driveItem.getFileSystemPath());
                            request.setAllowedNetworkTypes(ALLOWED_NETWORK_TYPES);
                            request.setVisibleInDownloadsUi(false);
                            long downloadId = downloadManager.enqueue(request);

                            MusicSyncDbHelper.getInstance(App.get()).setDriveItemDownloadId(driveItem.getId(), downloadId);
                        }

                        broadcastStatus(BROADCAST_DOWNLOADS_IN_PROGESS_ACTION);
                    } else {
                        broadcastStatus(BROADCAST_DOWNLOADS_COMPLETE_ACTION);
                    }
                } else {
                    broadcastStatus(BROADCAST_DOWNLOADS_IN_PROGESS_ACTION);
                }
                wakeLock.release();
            }

            if (ACTION_MOVE.equals(action)) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);

                wakeLock.acquire();

                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), TMP_MUSIC_STORAGE_FOLDER);

                if (file.exists() && file.isDirectory()) {
                    moveRecursive(file);
                }

                deleteRecursive(file);

                wakeLock.release();

                broadcastStatus(BROADCAST_MOVE_COMPLETE_ACTION);
            }
        }
    }

    private void delete(int id, long downloadId, DownloadManager downloadManager) {
        try {
            downloadManager.remove(downloadId);
            MusicSyncDbHelper.getInstance(App.get()).deleteDriveItem(id);
        } catch (Exception e) {
            @SuppressLint("DefaultLocale") String failureMessage = String.format("Failed to delete downloadId: %d", downloadId);
            Log.e(TAG, failureMessage, e);
            showToast(failureMessage);
        }
    }

    private void moveRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                moveRecursive(child);

        if(!fileOrDirectory.isDirectory()){
            File to = new File(fileOrDirectory.getParent().replace(TMP_MUSIC_STORAGE_FOLDER, MUSIC_STORAGE_FOLDER), fileOrDirectory.getName());

            if(!to.getParentFile().exists()){
                boolean isSuccessMkDirs = to.getParentFile().mkdirs();

                if(!isSuccessMkDirs){
                    showToast(String.format("Failure making directories for %s", fileOrDirectory.getName()));
                }
            }

            boolean isSuccess = fileOrDirectory.renameTo(to);

            if (!isSuccess) {
                showToast(String.format("Failure moving %s", fileOrDirectory.getName()));
            }else{
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(to)));
            }
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if(fileOrDirectory.exists()){
            if (fileOrDirectory.isDirectory()){
                for (File child : fileOrDirectory.listFiles()) {
                    deleteRecursive(child);
                }
            }

            boolean isSuccess = fileOrDirectory.delete();

            if (!isSuccess) {
                showToast(String.format("Failure deleting %s", fileOrDirectory.getName()));
            }
        }
    }

    public void resetDelta(final Context context){
        RequestQueue queue = Volley.newRequestQueue(App.get());
        JSONObject parameters = new JSONObject();

        try {
            parameters.put("key", "value");
        } catch (Exception e) {
            Log.e(TAG, "Failed to put parameters", e);
        }

        String url = String.format("%s?token=latest", MusicSyncIntentService.DRIVE_MUSIC_ROOT_URL);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                parameters,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            if (response.has(DeltaLinkManager.ODATA_DELTA_LINK)) {
                                DeltaLinkManager.getInstance().setDeltaLink(response.getString(ODATA_DELTA_LINK));
                                showToast("Reset delta complete", context);
                            }
                        } catch (Exception e) {
                            String failureMessage = "Unable to access odata delta link OR setting preferences.";
                            Log.e(TAG, failureMessage, e);
                            showToast(failureMessage, context);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, String.format("Error: %s", error.toString()));
                        showToast("Failure resetting delta. Review logs", context);
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

    public void syncMusic(String deltaLink, String nextLink, final Context context) {
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
                            try {
                                JSONArray driveItemArray = response.getJSONArray("value");

                                for (int i = 0; i < driveItemArray.length(); i++) {
                                    try {
                                        JSONObject driveItem = driveItemArray.getJSONObject(i);

                                        if (driveItem.has("file")) {
                                            String id = driveItem.getString("id");
                                            String name = driveItem.getString("name");
                                            boolean isDeletedFile = driveItem.has("deleted");
                                            boolean isAudio = driveItem.has("audio");

                                            if (isAudio) {
                                                DriveItem dbDriveItem = MusicSyncDbHelper.getInstance(context).getDriveItemByDriveItemId(id);

                                                if (dbDriveItem != null) {
                                                    //this scenario is either an edit or a delete. Either way we need to delete
                                                    MusicSyncDbHelper.getInstance(context).setDriveItemMarkedForDeletion(id, true);
                                                }

                                                if (!isDeletedFile) {
                                                    //this scenario is either a new song or an edit. Either way we need to download
                                                    String parentPath = driveItem.getJSONObject("parentReference").getString("path").replace(PATH_REPLACE, "");
                                                    String filePath = String.format("%s/%s/%s", TMP_MUSIC_STORAGE_FOLDER, parentPath, name);
                                                    filePath = URLDecoder.decode(filePath, "UTF-8");
                                                    MusicSyncDbHelper.getInstance(context).insertDriveItem(id, false, false, filePath);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        String failureMessage = String.format("Failure working with driveItem. %s", driveItemArray.getJSONObject(i).toString());
                                        Log.e(TAG, failureMessage, e);
                                        showToast("Music sync failure on driveItem", context);
                                    }
                                }
                            } catch (Exception e) {
                                String failureMessage = "Unrecoverable failure.";
                                Log.e(TAG, failureMessage, e);
                                showToast(String.format("Unknown music sync failure:%s", e.getMessage()), context);
                            }
                        }

                        try {
                            if (response.has(ODATA_NEXT_LINK)) {
                                broadcastStatus(BROADCAST_SYNC_PARTIAL_ACTION);
                                syncMusic(null, response.getString(ODATA_NEXT_LINK), context);
                            }
                        } catch (Exception e) {
                            String failureMessage = "Unable to access odata next link.";
                            Log.e(TAG, failureMessage, e);
                            showToast(failureMessage, context);
                        }

                        try {
                            if (response.has(DeltaLinkManager.ODATA_DELTA_LINK)) {
                                DeltaLinkManager.getInstance().setDeltaLink(response.getString(ODATA_DELTA_LINK));

                                broadcastStatus(BROADCAST_SYNC_COMPLETE_ACTION);
                            }
                        } catch (Exception e) {
                            String failureMessage = "Unable to access odata delta link OR setting preferences.";
                            Log.e(TAG, failureMessage, e);
                            showToast(failureMessage, context);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, String.format("Error: %s", error.toString()));
                        showToast("Failure syncing music. Review logs", context);
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
                Toast.makeText(App.get(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    protected void showToast(final String msg, final Context context) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                // run this code in the main thread
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}