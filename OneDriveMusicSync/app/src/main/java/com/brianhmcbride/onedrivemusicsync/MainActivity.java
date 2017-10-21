package com.brianhmcbride.onedrivemusicsync;

import android.Manifest;
import android.app.DownloadManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.*;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.identity.client.*;

public class MainActivity extends AppCompatActivity {
    public static final String ODATA_DELTA_LINK = "@odata.deltaLink";
    public static final String ODATA_NEXT_LINK = "@odata.nextLink";
    private long enqueue;
    private DownloadManager dm;

    final static String CLIENT_ID = "8fbb52c6-a9eb-41a1-9933-4be38cdefbd3";
    final static String SCOPES[] = {"https://graph.microsoft.com/User.Read", "https://graph.microsoft.com/Files.Read"};
    //final static String DRIVE_MUSIC_ROOT_URL = "https://graph.microsoft.com/v1.0/me/drive/special/music/delta";
    final static String DRIVE_MUSIC_ROOT_URL = "https://graph.microsoft.com/v1.0/me/drive/root:/OneDriveMusicSync:/delta";

    /* UI & Debugging Variables */
    private static final String TAG = MainActivity.class.getSimpleName();
    Button callGraphButton;
    Button signOutButton;
    Button syncMusicButton;

    /* Azure AD Variables */
    private PublicClientApplication MSALClientApplication;
    private AuthenticationResult authResult;

    public static final String PREFS_NAME = "OneDriveMusicSyncPreferences";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        isStoragePermissionGranted();

        setContentView(R.layout.activity_main);

        callGraphButton = (Button) findViewById(R.id.callGraph);
        signOutButton = (Button) findViewById(R.id.clearCache);
        syncMusicButton = (Button) findViewById(R.id.syncMusic);


        callGraphButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onCallGraphClicked();
            }
        });

        signOutButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSignOutClicked();
            }
        });

        syncMusicButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSyncMusicClicked();
            }
        });

  /* Configure your sample app and save state for this activity */
        MSALClientApplication = null;
        if (MSALClientApplication == null) {
            MSALClientApplication = new PublicClientApplication(
                    this.getApplicationContext(),
                    CLIENT_ID);
        }

  /* Attempt to get a user and acquireTokenSilent
   * If this fails we do an interactive request
   */
        List<User> users = null;

        try {
            users = MSALClientApplication.getUsers();

            if (users != null && users.size() == 1) {
          /* We have 1 user */

                MSALClientApplication.acquireTokenSilentAsync(SCOPES, users.get(0), getAuthSilentCallback());
            } else {
          /* We have no user */

          /* Let's do an interactive request */
                MSALClientApplication.acquireToken(this, SCOPES, getAuthInteractiveCallback());
            }
        } catch (MsalClientException e) {
            Log.d(TAG, "MSAL Exception Generated while getting users: " + e.toString());

        } catch (IndexOutOfBoundsException e) {
            Log.d(TAG, "User at this position does not exist: " + e.toString());
        }
    }

//
// App callbacks for MSAL
// ======================
// getActivity() - returns activity so we can acquireToken within a callback
// getAuthSilentCallback() - callback defined to handle acquireTokenSilent() case
// getAuthInteractiveCallback() - callback defined to handle acquireToken() case
//

    public Activity getActivity() {
        return this;
    }

    /* Callback method for acquireTokenSilent calls
     * Looks if tokens are in the cache (refreshes if necessary and if we don't forceRefresh)
     * else errors that we need to do an interactive request.
     */
    private AuthenticationCallback getAuthSilentCallback() {
        return new AuthenticationCallback() {
            @Override
            public void onSuccess(AuthenticationResult authenticationResult) {
            /* Successfully got a token, call Graph now */
                Log.d(TAG, "Successfully authenticated");

            /* Store the authResult */
                authResult = authenticationResult;

            /* update the UI to post call Graph state */
                updateSuccessUI();
            }

            @Override
            public void onError(MsalException exception) {
            /* Failed to acquireToken */
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
            /* User canceled the authentication */
                Log.d(TAG, "User cancelled login.");
            }
        };
    }

    /* Callback used for interactive request.  If succeeds we use the access
         * token to call the Microsoft Graph. Does not check cache
         */
    private AuthenticationCallback getAuthInteractiveCallback() {
        return new AuthenticationCallback() {
            @Override
            public void onSuccess(AuthenticationResult authenticationResult) {
            /* Successfully got a token, call graph now */
                Log.d(TAG, "Successfully authenticated");
                Log.d(TAG, "ID Token: " + authenticationResult.getIdToken());

            /* Store the auth result */
                authResult = authenticationResult;

            /* update the UI to post call Graph state */
                updateSuccessUI();
            }

            @Override
            public void onError(MsalException exception) {
            /* Failed to acquireToken */
                Log.d(TAG, "Authentication failed: " + exception.toString());

                if (exception instanceof MsalClientException) {
                /* Exception inside MSAL, more info inside MsalError.java */
                } else if (exception instanceof MsalServiceException) {
                /* Exception when communicating with the STS, likely config issue */
                }
            }

            @Override
            public void onCancel() {
            /* User canceled the authentication */
                Log.d(TAG, "User cancelled login.");
            }
        };
    }

    /* Set the UI for successful token acquisition data */
    private void updateSuccessUI() {
        callGraphButton.setVisibility(View.GONE);
        signOutButton.setVisibility(View.VISIBLE);
        syncMusicButton.setVisibility(View.VISIBLE);
        findViewById(R.id.welcome).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.welcome)).setText("Welcome, " +
                authResult.getUser().getName());
        findViewById(R.id.graphData).setVisibility(View.VISIBLE);
    }

    /* Use MSAL to acquireToken for the end-user
     * Callback will call Graph api w/ access token & update UI
     */
    private void onCallGraphClicked() {
        MSALClientApplication.acquireToken(getActivity(), SCOPES, getAuthInteractiveCallback());
    }

    /* Handles the redirect from the System Browser */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        MSALClientApplication.handleInteractiveRequestRedirect(requestCode, resultCode, data);
    }

    private void onSyncMusicClicked() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String deltaLink = settings.getString(ODATA_DELTA_LINK, null);

        if(deltaLink == null){
            deltaLink = DRIVE_MUSIC_ROOT_URL;
        }

        SyncMusic(deltaLink, null);
    }

    private void SyncMusic(String deltaLink, String nextLink){
        /* Make sure we have a token to send to graph */
        if (authResult.getAccessToken() == null) {
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);
        JSONObject parameters = new JSONObject();

        try {
            parameters.put("key", "value");
        } catch (Exception e) {
            Log.d(TAG, "Failed to put parameters: " + e.toString());
        }

        String url = deltaLink == null ? nextLink : deltaLink;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url,
                parameters, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
            /* Successfully called graph, process data and send to UI */
                Log.d(TAG, "Response: " + response.toString());

                try {
                    JSONArray driveItemArray = response.getJSONArray("value");

                    for (int i = 0; i < driveItemArray.length(); i++) {
                        JSONObject driveItem = driveItemArray.getJSONObject(i);

                        if (driveItem.has("file")) {
                            JSONObject driveItemFile = driveItem.getJSONObject("file");
                            //Log.d(TAG, "driveItemFile: " + driveItem.toString());
                            if (driveItemFile.has("mimeType") && driveItemFile.getString("mimeType").equals("audio/mpeg")) {
                                Log.i(TAG, "Found music file:" + driveItem.getString("name"));
                                String path = driveItem.getJSONObject("parentReference").getString("path").replace("/drive/root:/Music/", "");
                                path += "/" + driveItem.getString("name");

                                if(driveItem.has("deleted")){
                                    // Get the directory for the user's public pictures directory.
                                    File file = new File(Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_MUSIC), path);

                                    if(file.exists() && file.isFile()){
                                        file.delete();
                                    }
                                }else {
                                    dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse("https://graph.microsoft.com/v1.0/me/drive/items/" + driveItem.getString("id") + "/content"));
                                    request.addRequestHeader("Authorization", "Bearer " + authResult.getAccessToken());
                                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, path);
                                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                                    request.setVisibleInDownloadsUi(true);

                                    enqueue = dm.enqueue(request);
                                }
                            }
                        }
                    }

                    if(response.has(ODATA_NEXT_LINK)){
                        SyncMusic(null, response.getString(ODATA_NEXT_LINK));
                    } else if(response.has(ODATA_DELTA_LINK)){
                        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(ODATA_DELTA_LINK, response.getString(ODATA_DELTA_LINK));
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Failed to get JSONArray from value: " + e.toString());
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Error: " + error.toString());
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + authResult.getAccessToken());
                return headers;
            }
        };

        Log.d(TAG, "Adding HTTP GET to Queue, Request: " + request.toString());

        request.setRetryPolicy(new DefaultRetryPolicy(
                3000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }

    /* Clears a user's tokens from the cache.
 * Logically similar to "sign out" but only signs out of this app.
 */
    private void onSignOutClicked() {

    /* Attempt to get a user and remove their cookies from cache */
        List<User> users = null;

        try {
            users = MSALClientApplication.getUsers();

            if (users == null) {
            /* We have no users */

            } else if (users.size() == 1) {
            /* We have 1 user */
            /* Remove from token cache */
                MSALClientApplication.remove(users.get(0));
                updateSignedOutUI();

            } else {
            /* We have multiple users */
                for (int i = 0; i < users.size(); i++) {
                    MSALClientApplication.remove(users.get(i));
                }
            }

            Toast.makeText(getBaseContext(), "Signed Out!", Toast.LENGTH_SHORT)
                    .show();

        } catch (MsalClientException e) {
            Log.d(TAG, "MSAL Exception Generated while getting users: " + e.toString());

        } catch (IndexOutOfBoundsException e) {
            Log.d(TAG, "User at this position does not exist: " + e.toString());
        }
    }

    /* Set the UI for signed-out user */
    private void updateSignedOutUI() {
        callGraphButton.setVisibility(View.VISIBLE);
        syncMusicButton.setVisibility(View.INVISIBLE);
        signOutButton.setVisibility(View.INVISIBLE);
        findViewById(R.id.welcome).setVisibility(View.INVISIBLE);
        findViewById(R.id.graphData).setVisibility(View.INVISIBLE);
        ((TextView) findViewById(R.id.graphData)).setText("No Data");
    }

    public  boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
            Log.v(TAG,"Permission: "+permissions[0]+ "was "+grantResults[0]);
            //resume tasks needing this permission
        }
    }
}