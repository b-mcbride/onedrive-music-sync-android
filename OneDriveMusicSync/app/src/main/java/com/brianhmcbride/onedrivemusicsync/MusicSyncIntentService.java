package com.brianhmcbride.onedrivemusicsync;

import android.app.DownloadManager;
import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class MusicSyncIntentService extends IntentService {
    public static final String ODATA_DELTA_LINK = "@odata.deltaLink";
    public static final String PREFS_NAME = "OneDriveMusicSyncPreferences";
    public static final String BROADCAST_COMPLETE_ACTION = "com.brianhmcbride.onedrivemusicsync.COMPLETE";
    public static final String BROADCAST_PARTIAL_ACTION = "com.brianhmcbride.onedrivemusicsync.PARTIAL";
    public static final String EXTENDED_DATA_STATUS = "com.brianhmcbride.onedrivemusicsync.STATUS";

    static final String ODATA_NEXT_LINK = "@odata.nextLink";
    static final String ACTION_SYNC = "com.brianhmcbride.onedrivemusicsync.action.SYNC";
    static final String EXTRA_BEARER_TOKEN = "com.brianhmcbride.onedrivemusicsync.extra.BEARER_TOKEN";
    static final String DRIVE_MUSIC_ROOT_URL = "https://graph.microsoft.com/v1.0/me/drive/root:/OneDriveMusicSync:/delta"; //"https://graph.microsoft.com/v1.0/me/drive/root:/Music:/delta";
    static final String PATH_REPLACE = "/drive/root:/OneDriveMusicSync/"; //"/drive/root:/Music/";
    static final String MUSIC_STORAGE_FOLDER = "OneDriveMusicSync";

    int queuedDownloads = 0;
    int deletes = 0;
    int failures = 0;
    long enqueue;

    DownloadManager dm;

    public MusicSyncIntentService() {
        super("MusicSyncIntentService");
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionSync(Context context, String bearerToken) {
        Intent intent = new Intent(context, MusicSyncIntentService.class);
        intent.setAction(ACTION_SYNC);
        intent.putExtra(EXTRA_BEARER_TOKEN, bearerToken);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SYNC.equals(action)) {
                final String bearerToken = intent.getStringExtra(EXTRA_BEARER_TOKEN);

                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                String deltaLink = settings.getString(ODATA_DELTA_LINK, null);

                if (deltaLink == null) {
                    deltaLink = DRIVE_MUSIC_ROOT_URL;
                }

                queuedDownloads = 0;
                deletes = 0;
                failures = 0;

                SyncMusic(bearerToken, deltaLink, null);
            }
        }
    }

    private void SyncMusic(final String bearerToken, String deltaLink, String nextLink) {
        RequestQueue queue = Volley.newRequestQueue(this);
        JSONObject parameters = new JSONObject();

        try {
            parameters.put("key", "value");
        } catch (Exception e) {
            Log.d(MainActivity.TAG, "Failed to put parameters: " + e.toString());
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

                                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                                for (int i = 0; i < driveItemArray.length(); i++) {
                                    try {
                                        JSONObject driveItem = driveItemArray.getJSONObject(i);

                                        if (driveItem.has("file")) {
                                            JSONObject driveItemFile = driveItem.getJSONObject("file");

                                            if ((driveItemFile.has("mimeType") && driveItemFile.getString("mimeType").equals("audio/mpeg")) ||
                                                    driveItem.getString("name").contains(".mp3")) {

                                                String path = MUSIC_STORAGE_FOLDER + "/" + driveItem.getJSONObject("parentReference").getString("path").replace(PATH_REPLACE, "") + "/" + driveItem.getString("name");
                                                path = URLDecoder.decode(path, "UTF-8");

                                                if (driveItem.has("deleted")) {
                                                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), path);

                                                    if (file.exists() && file.isFile()) {
                                                        file.delete();
                                                        deletes++;
                                                    }
                                                } else {
                                                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse("https://graph.microsoft.com/v1.0/me/drive/items/" + driveItem.getString("id") + "/content"));
                                                    request.addRequestHeader("Authorization", "Bearer " + bearerToken);
                                                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, path);
                                                    request.setVisibleInDownloadsUi(true);

                                                    enqueue = downloadManager.enqueue(request);
                                                    queuedDownloads++;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        String failureMessage = "Failure working with driveItem. " + driveItemArray.getJSONObject(i).toString() + ". Exception data: " + e.toString();
                                        Log.d(MainActivity.TAG, failureMessage);
                                        failures++;
                                    }
                                }
                            } catch (Exception e) {
                                String failureMessage = "Unrecoverable failure. Queued " + queuedDownloads + " download(s). Performed " + deletes + " deletion(s). Total failures: " + failures + ". Exception data: " + e.toString();
                                Log.d(MainActivity.TAG, failureMessage);
                            }
                        }

                        try {
                            if (response.has(ODATA_NEXT_LINK)) {
                                broadcastStatus(BROADCAST_PARTIAL_ACTION, queuedDownloads, deletes, failures);
                                SyncMusic(bearerToken, null, response.getString(ODATA_NEXT_LINK));
                            }
                        } catch (Exception e) {
                            String failureMessage = "Unable to access odata next link. Exception data: " + e.toString();
                            Log.d(MainActivity.TAG, failureMessage);
                        }

                        try
                        {
                            if (response.has(ODATA_DELTA_LINK)) {
                                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                                SharedPreferences.Editor editor = settings.edit();
                                editor.putString(ODATA_DELTA_LINK, response.getString(ODATA_DELTA_LINK));
                                editor.commit();

                                broadcastStatus(BROADCAST_COMPLETE_ACTION, queuedDownloads, deletes, failures);
                            }
                        }
                        catch (Exception e){
                            String failureMessage = "Unable to access odata delta link OR setting preferences. Exception data: " + e.toString();
                            Log.d(MainActivity.TAG, failureMessage);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(MainActivity.TAG, "Error: " + error.toString());
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + bearerToken);
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

    private void broadcastStatus(String action, int queued, int deletes, int failures){
        String status = queued + "|" + deletes + "|" + failures;
        Intent localIntent = new Intent(action).putExtra(EXTENDED_DATA_STATUS, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }
}
