package com.brianhmcbride.onedrivemusicsync;

import android.app.DownloadManager;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.MainThread;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
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
import com.microsoft.identity.client.MsalClientException;
import com.microsoft.identity.client.MsalException;
import com.microsoft.identity.client.MsalServiceException;
import com.microsoft.identity.client.MsalUiRequiredException;
import com.microsoft.identity.client.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class MusicSyncIntentService extends IntentService {
    public static final String TAG = MusicSyncIntentService.class.getSimpleName();
    public static final String ODATA_DELTA_LINK = "@odata.deltaLink";
    public static final String PREFS_NAME = "OneDriveMusicSyncPreferences";
    public static final String BROADCAST_SYNC_COMPLETE_ACTION = "com.brianhmcbride.onedrivemusicsync.COMPLETE";
    public static final String BROADCAST_SYNC_PARTIAL_ACTION = "com.brianhmcbride.onedrivemusicsync.PARTIAL";
    public static final String EXTENDED_DATA_STATUS = "com.brianhmcbride.onedrivemusicsync.STATUS";

    static final int MAX_DOWNLOADS_TO_QUEUE = 100;
    static final String WAKE_LOCK_TAG = "com.brianhmcbride.onedrivemusicsync.wakelock";
    static final String ODATA_NEXT_LINK = "@odata.nextLink";
    static final String ACTION_SYNC = "com.brianhmcbride.onedrivemusicsync.action.SYNC";
    static final String ACTION_DOWNLOAD_AND_DELETE = "com.brianhmcbride.onedrivemusicsync.action.DOWNLOAD_AND_DELETE";
    static final String DRIVE_MUSIC_ROOT_URL = "https://graph.microsoft.com/v1.0/me/drive/root:/Music:/delta"; //"https://graph.microsoft.com/v1.0/me/drive/root:/OneDriveMusicSync:/delta";
    static final String PATH_REPLACE = "/drive/root:/Music/"; //"/drive/root:/OneDriveMusicSync/";
    static final String MUSIC_STORAGE_FOLDER = "OneDriveMusicSync";

    int failures = 0;

    static ArrayList<QueuedDownload> downloads;
    static ArrayList<QueuedDeletion> deletions;

    private BroadcastReceiver onDownloadFinishReceiver;

    public MusicSyncIntentService() {
        super("MusicSyncIntentService");
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
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

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SYNC.equals(action)) {
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                String deltaLink = settings.getString(ODATA_DELTA_LINK, null);

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
                            Thread.sleep(5000);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to sleep", e);
                        }

                        if (getNumberOfPendingDownloads() == 0) {
                            try {
                                List<User> users = MainActivity.MSALClientApplication.getUsers();

                                MainActivity.MSALClientApplication.acquireTokenSilentAsync(MainActivity.SCOPES, users.get(0), "", true, new AuthenticationCallback() {
                                    @Override
                                    public void onSuccess(AuthenticationResult authenticationResult) {
                                        Log.d(TAG, "Successfully authenticated");

                                        MainActivity.authResult = authenticationResult;
                                        QueueDownloads();
                                    }

                                    @Override
                                    public void onError(MsalException exception) {
                                        Log.d(TAG, "Authentication failed: " + exception.toString());

                                        if (exception instanceof MsalClientException) {
                                            /* Exception inside MSAL, more info inside MsalError.java */
                                        } else if (exception instanceof MsalServiceException) {
                                            /* Exception when communicating with the STS, likely config issue */
                                        } else if (exception instanceof MsalUiRequiredException) {
                                            /* Tokens expired or no session, retry with interactive */
                                        }
                                    }

                                    @Override
                                    public void onCancel() {
                                        Log.d(TAG, "User cancelled login.");
                                    }
                                });
                            } catch (MsalClientException e) {
                                Log.d(TAG, "MSAL Exception Generated while getting users.", e);
                            } catch (IndexOutOfBoundsException e) {
                                Log.d(TAG, "User at this position does not exist.", e);
                            }
                        }
                    }

                    showToast("Music files queued to download");
                    unregisterReceiver(onDownloadFinishReceiver);
                }

                wakeLock.release();
            }
        }
    }

    private void QueueDownloads() {
        int downloadsRemaining = 0;
        int numberOfDownloadsToQueue = 0;

        for (QueuedDownload download: downloads) {
         if(download.getDownloadId() == 0){
             downloadsRemaining++;
         }
        }

        if(downloadsRemaining > MAX_DOWNLOADS_TO_QUEUE){
            numberOfDownloadsToQueue = MAX_DOWNLOADS_TO_QUEUE;
        } else {
            numberOfDownloadsToQueue = downloadsRemaining;
        }

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        int count = 0;
        for (QueuedDownload download : downloads) {
            if(count == numberOfDownloadsToQueue){
                break;
            }

            try {
                if(download.getDownloadId() == 0) {
                    DownloadManager.Request request = new DownloadManager.Request(download.getDownloadUri());
                    request.addRequestHeader("Authorization", "Bearer " + MainActivity.authResult.getAccessToken());
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, download.getFileSystemPath());
                    request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
                    request.setVisibleInDownloadsUi(false);

                    download.setDownloadId(downloadManager.enqueue(request));
                    count++;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to queue download: " + download.getFileSystemPath(), e);
            }
        }
    }

    private void DeleteItems() {
        for (QueuedDeletion deletion : deletions) {
            try {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), deletion.getFileSystemPath());

                if (file.exists() && file.isFile()) {
                    file.delete();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete file: " + deletion.getFileSystemPath(), e);
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

                                            if (driveItem.getString("name").contains(".mp3")) {
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
                                        String failureMessage = "Failure working with driveItem. " + driveItemArray.getJSONObject(i).toString();
                                        Log.e(TAG, failureMessage, e);
                                        failures++;
                                    }
                                }
                            } catch (Exception e) {
                                String failureMessage = "Unrecoverable failure.";
                                Log.e(TAG, failureMessage, e);
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
                        }

                        try {
                            if (response.has(ODATA_DELTA_LINK)) {
                                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                                SharedPreferences.Editor editor = settings.edit();
                                editor.putString(ODATA_DELTA_LINK, response.getString(ODATA_DELTA_LINK));
                                editor.commit();

                                broadcastStatus(BROADCAST_SYNC_COMPLETE_ACTION);
                            }
                        } catch (Exception e) {
                            String failureMessage = "Unable to access odata delta link OR setting preferences.";
                            Log.e(TAG, failureMessage, e);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, "Error: " + error.toString());
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + MainActivity.authResult.getAccessToken());
                return headers;
            }
        };

        Log.d(MainActivity.TAG, "Adding HTTP GET to Queue, Request: " + request.toString());

        request.setRetryPolicy(new DefaultRetryPolicy(
                3000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(request);
    }

    private void broadcastStatus(String action) {
        String status = downloads.size() + "|" + deletions.size() + "|" + failures;
        Intent localIntent = new Intent(action).putExtra(EXTENDED_DATA_STATUS, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    protected void showToast(final String msg) {
        //gets the main thread
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                // run this code in the main thread
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int getNumberOfPendingDownloads() {
        int count = 0;
        for (QueuedDownload download : downloads) {
            if (download.getDownloadId() != 0 && download.getIsDownloadComplete() == false) {
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