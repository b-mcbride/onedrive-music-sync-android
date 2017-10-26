package com.brianhmcbride.onedrivemusicsync;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
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
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.AuthenticationResult;
import com.microsoft.identity.client.MsalException;

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
    public static final String EXTENDED_DATA_STATUS = "com.brianhmcbride.onedrivemusicsync.STATUS";
    public static final String BROADCAST_CLEAR_SYNCED_COLLECTION_COMPLETE_ACTION = "com.brianhmcbride.onedrivemusicsync.CLEAR_SYNCED_COLLECTION_COMPLETE";

    static final String WAKE_LOCK_TAG = "com.brianhmcbride.onedrivemusicsync.wakelock";
    static final String ODATA_NEXT_LINK = "@odata.nextLink";
    static final String ACTION_SYNC = "com.brianhmcbride.onedrivemusicsync.action.SYNC";
    static final String ACTION_DOWNLOAD_AND_DELETE = "com.brianhmcbride.onedrivemusicsync.action.DOWNLOAD_AND_DELETE";
    static final String ACTION_CLEAR_SYNCED_COLLECTION = "com.brianhmcbride.onedrivemusicsync.action.CLEAR_SYNCED_COLLECTION";
    static final String MUSIC_STORAGE_FOLDER = "OneDriveMusicSync";
    static final int TIMEOUT = 5000;

    /* DEVELOPMENT*/
    static final String DRIVE_MUSIC_ROOT_URL = "https://graph.microsoft.com/v1.0/me/drive/root:/OneDriveMusicSync:/delta";
    static final String PATH_REPLACE = "/drive/root:/OneDriveMusicSync/";
    static final int MAX_DOWNLOADS_TO_QUEUE = 1;
    static final int ALLOWED_NETWORK_TYPES = DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE;

    /*DEPLOYMENT*/
//    static final String DRIVE_MUSIC_ROOT_URL = "https://graph.microsoft.com/v1.0/me/drive/root:/Music:/delta";
//    static final String PATH_REPLACE = "/drive/root:/Music/";
//    static final int MAX_DOWNLOADS_TO_QUEUE = 100;
//    static final int ALLOWED_NETWORK_TYPES = DownloadManager.Request.NETWORK_WIFI;

    int failures = 0;

    static ArrayList<QueuedDownload> downloads;
    static ArrayList<QueuedDeletion> deletions;

    public MusicSyncIntentService() {
        super("MusicSyncIntentService");
    }

    public static void startActionSync(Context context) {
        Intent intent = new Intent(context, MusicSyncIntentService.class);
        intent.setAction(ACTION_SYNC);
        context.startService(intent);
    }

    public static void startActionDownloadAndDelete(Context context) {
        Intent intent = new Intent(context, MusicSyncIntentService.class);
        intent.setAction(ACTION_DOWNLOAD_AND_DELETE);
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
            if (ACTION_SYNC.equals(action)) {
                String deltaLink = DeltaLinkManager.getInstance().getDeltaLink();

                if (deltaLink == null) {
                    deltaLink = DRIVE_MUSIC_ROOT_URL;
                }

                downloads = new ArrayList<>();
                deletions = new ArrayList<>();
                failures = 0;

                SyncMusic(deltaLink, null);
            }

            if (ACTION_DOWNLOAD_AND_DELETE.equals(action)) {

                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);

                wakeLock.acquire();
                if (deletions.size() > 0) {
                    DeleteItems();

                    showToast("Music files deleted");
                }

                if (downloads.size() > 0) {
                    BroadcastReceiver onDownloadFinishReceiver;

                    registerReceiver(onDownloadFinishReceiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent i) {
                            Log.d(TAG, "Download finished intent received");
                            long downloadId = i.getExtras().getLong(DownloadManager.EXTRA_DOWNLOAD_ID);
                            for (QueuedDownload download : downloads) {
                                if (download.getDownloadId() == downloadId) {
                                    download.setIsDownloadComplete(true);
                                    break;
                                }
                            }
                        }
                    }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

                    while (getNumberOfDownloadsToBeQueued() > 0) {

                        try {
                            Thread.sleep(TIMEOUT);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to sleep", e);
                        }

                        if (getNumberOfMarkedDownloads() == 0 && getNumberOfPendingDownloads() == 0) {
                            markDownloads();
                            AuthenticationManager.getInstance().refreshToken(new AuthenticationCallback() {
                                @Override
                                public void onSuccess(AuthenticationResult authenticationResult) {
                                    Log.d(TAG, "Successfully authenticated");
                                    SendMarkedDownloadsToDownloadManager();
                                }

                                @Override
                                public void onError(MsalException exception) {
                                    showToast("Authentication failed during refreshToken");
                                }

                                @Override
                                public void onCancel() {
                                }
                            });
                        }
                    }

                    showToast("Music files queued to download");
                    unregisterReceiver(onDownloadFinishReceiver);
                }

                wakeLock.release();
            }

            if (ACTION_CLEAR_SYNCED_COLLECTION.equals(action)) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);

                wakeLock.acquire();
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), MUSIC_STORAGE_FOLDER);

                if (file.exists() && file.isDirectory()) {
                    deleteRecursive(file);

                    DeltaLinkManager.getInstance().clearDeltaLink();

                    Intent localIntent = new Intent(BROADCAST_CLEAR_SYNCED_COLLECTION_COMPLETE_ACTION);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
                } else {
                    showToast("Failed to delete music storage folder");
                }
                wakeLock.release();
            }
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        boolean isSuccess = fileOrDirectory.delete();

        if(!isSuccess){
            showToast(String.format("Failure deleting %s", fileOrDirectory.getName()));
        }
    }

    private void markDownloads() {
        int downloadsRemaining = 0;
        for (QueuedDownload download : downloads) {
            if (download.getDownloadId() == 0 && !download.getIsMarkedForDownload()) {
                downloadsRemaining++;
            }
        }

        int numberOfDownloadsToQueue;
        if (downloadsRemaining > MAX_DOWNLOADS_TO_QUEUE) {
            numberOfDownloadsToQueue = MAX_DOWNLOADS_TO_QUEUE;
        } else {
            numberOfDownloadsToQueue = downloadsRemaining;
        }

        int count = 0;
        for (QueuedDownload download : downloads) {
            if (count == numberOfDownloadsToQueue) {
                break;
            }

            if (download.getDownloadId() == 0 && !download.getIsMarkedForDownload()) {
                download.setIsMarkedForDownload(true);
                count++;
            }
        }
    }

    private void SendMarkedDownloadsToDownloadManager() {
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        for (QueuedDownload download : downloads) {
            try {
                if (download.getDownloadId() == 0 && download.getIsMarkedForDownload()) {
                    DownloadManager.Request request = new DownloadManager.Request(download.getDownloadUri());
                    request.addRequestHeader("Authorization", String.format("Bearer %s", AuthenticationManager.getInstance().getAccessToken()));
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, download.getFileSystemPath());
                    request.setAllowedNetworkTypes(ALLOWED_NETWORK_TYPES);
                    request.setVisibleInDownloadsUi(false);

                    download.setDownloadId(downloadManager.enqueue(request));
                    download.setIsMarkedForDownload(false);
                }
            } catch (Exception e) {
                String failureMessage = String.format("Failed to queue download: %s", download.getFileSystemPath());
                Log.e(TAG, failureMessage, e);
                showToast(failureMessage);
            }
        }
    }

    private void DeleteItems() {
        for (QueuedDeletion deletion : deletions) {
            try {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), deletion.getFileSystemPath());

                if (file.exists() && file.isFile()) {
                    boolean isDeleteSuccess = file.delete();

                    if (!isDeleteSuccess) {
                        showToast(String.format("Failed to delete file: %s", deletion.getFileSystemPath()));
                    }
                }
            } catch (Exception e) {
                String failureMessage = String.format("Failed to delete file: %s", deletion.getFileSystemPath());
                Log.e(TAG, failureMessage, e);
                showToast(failureMessage);
            }
        }
    }

    private void SyncMusic(String deltaLink, String nextLink) {
        RequestQueue queue = Volley.newRequestQueue(this);
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

                                            if (driveItem.getString("name").contains(".m4a") || driveItem.getString("name").contains("mp3")) {
                                                String parentPath = driveItem.getJSONObject("parentReference").getString("path").replace(PATH_REPLACE, "");
                                                String filePath = String.format("%s/%s/%s", MUSIC_STORAGE_FOLDER, parentPath, name);
                                                filePath = URLDecoder.decode(filePath, "UTF-8");

                                                if (isDeletedFile) {
                                                    deletions.add(new QueuedDeletion(filePath));
                                                } else {
                                                    String downloadContentPath = String.format("https://graph.microsoft.com/v1.0/me/drive/items/%s/content", id);
                                                    Uri downloadContentUri = Uri.parse(downloadContentPath);

                                                    downloads.add(new QueuedDownload(downloadContentUri, filePath));
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        String failureMessage = String.format("Failure working with driveItem. %s", driveItemArray.getJSONObject(i).toString());
                                        Log.e(TAG, failureMessage, e);
                                        failures++;
                                        showToast("Music sync failure on driveItem");
                                    }
                                }
                            } catch (Exception e) {
                                String failureMessage = "Unrecoverable failure.";
                                Log.e(TAG, failureMessage, e);
                                showToast(String.format("Unknown music sync failure:%s", e.getMessage()));
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

        Log.d(MainActivity.TAG, String.format("Adding HTTP GET to Queue, Request: %s", request.toString()));

        request.setRetryPolicy(new DefaultRetryPolicy(
                3000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(request);
    }

    private void broadcastStatus(String action) {
        @SuppressLint("DefaultLocale") String status = String.format("%d|%d|%d", downloads.size(), deletions.size(), failures);
        Intent localIntent = new Intent(action).putExtra(EXTENDED_DATA_STATUS, status);
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

    private int getNumberOfPendingDownloads() {
        int count = 0;
        for (QueuedDownload download : downloads) {
            if (download.getDownloadId() != 0 && !download.getIsDownloadComplete()) {
                count++;
            }
        }

        return count;
    }

    private int getNumberOfMarkedDownloads() {
        int count = 0;
        for (QueuedDownload download : downloads) {
            if (download.getIsMarkedForDownload()) {
                count++;
            }
        }

        return count;
    }

    private int getNumberOfDownloadsToBeQueued() {
        int count = 0;

        for (QueuedDownload download : downloads) {
            if (download.getDownloadId() == 0) {
                count++;
            }
        }

        return count;
    }
}