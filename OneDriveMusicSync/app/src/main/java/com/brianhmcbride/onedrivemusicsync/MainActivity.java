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
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.identity.client.*;

public class MainActivity extends AppCompatActivity {
    static final String ODATA_DELTA_LINK = "@odata.deltaLink";
    static final String ODATA_NEXT_LINK = "@odata.nextLink";
    static final String CLIENT_ID = "8fbb52c6-a9eb-41a1-9933-4be38cdefbd3";
    static final String SCOPES[] = {"https://graph.microsoft.com/User.Read", "https://graph.microsoft.com/Files.Read"};
    static final String DRIVE_MUSIC_ROOT_URL = "https://graph.microsoft.com/v1.0/me/drive/root:/OneDriveMusicSync:/delta"; //"https://graph.microsoft.com/v1.0/me/drive/special/music/delta"
    static final String PATH_REPLACE = "/drive/root:/OneDriveMusicSync/"; //"/drive/root:/Music/"
    static final String TAG = MainActivity.class.getSimpleName();
    static final String PREFS_NAME = "OneDriveMusicSyncPreferences";

    int queuedDownloads = 0;
    int deletes = 0;
    long enqueue;
    DownloadManager dm;
    Button signInButton;
    Button signOutButton;
    Button syncMusicButton;
    TextView syncMusicStatusText;
    TextView welcomeText;
    PublicClientApplication MSALClientApplication;
    AuthenticationResult authResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        isStoragePermissionGranted();

        setContentView(R.layout.activity_main);

        signInButton = (Button) findViewById(R.id.signIn);
        signInButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSignInClicked();
            }
        });

        signOutButton = (Button) findViewById(R.id.signOut);
        signOutButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSignOutClicked();
            }
        });

        syncMusicButton = (Button) findViewById(R.id.syncMusic);
        syncMusicButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSyncMusicClicked();
            }
        });

        syncMusicStatusText = (TextView) findViewById(R.id.syncMusicStatus);
        welcomeText = (TextView) findViewById(R.id.welcome);

        /* Configure your sample app and save state for this activity */
        MSALClientApplication = null;
        if (MSALClientApplication == null) {
            MSALClientApplication = new PublicClientApplication(this.getApplicationContext(), CLIENT_ID);
        }

        /* Attempt to get a user and acquireTokenSilent
        * If this fails we do an interactive request
        */
        List<User> users = null;

        try {
            users = MSALClientApplication.getUsers();

            if (users != null && users.size() == 1) {
                MSALClientApplication.acquireTokenSilentAsync(SCOPES, users.get(0), getAuthSilentCallback());
            } else {
                signInButton.setVisibility(View.VISIBLE);
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
                Log.d(TAG, "Successfully authenticated");

                authResult = authenticationResult;

                setUIToLoggedIn();
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
        };
    }

    /* Callback used for interactive request.  If succeeds we use the access
    * token to call the Microsoft Graph. Does not check cache
    */
    private AuthenticationCallback getAuthInteractiveCallback() {
        return new AuthenticationCallback() {
            @Override
            public void onSuccess(AuthenticationResult authenticationResult) {
                Log.d(TAG, "Successfully authenticated");
                Log.d(TAG, "ID Token: " + authenticationResult.getIdToken());

                authResult = authenticationResult;

                setUIToLoggedIn();
            }

            @Override
            public void onError(MsalException exception) {
                Log.d(TAG, "Authentication failed: " + exception.toString());

                if (exception instanceof MsalClientException) {
                /* Exception inside MSAL, more info inside MsalError.java */
                } else if (exception instanceof MsalServiceException) {
                /* Exception when communicating with the STS, likely config issue */
                }
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "User cancelled login.");
            }
        };
    }

    private void setUIToLoggedIn() {
        signInButton.setVisibility(View.GONE);
        signOutButton.setVisibility(View.VISIBLE);
        syncMusicButton.setVisibility(View.VISIBLE);
        syncMusicStatusText.setVisibility(View.VISIBLE);
        syncMusicStatusText.setText("");
        welcomeText.setVisibility(View.VISIBLE);
        welcomeText.setText("Welcome, " + authResult.getUser().getName());
    }

    private void setUIToLoggedOut() {
        signInButton.setVisibility(View.VISIBLE);
        syncMusicButton.setVisibility(View.GONE);
        syncMusicStatusText.setVisibility(View.GONE);
        signOutButton.setVisibility(View.GONE);
        welcomeText.setVisibility(View.GONE);
    }

    /* Use MSAL to acquireToken for the end-user
     * Callback will call Graph api w/ access token & update UI
     */
    private void onSignInClicked() {
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

        if (deltaLink == null) {
            deltaLink = DRIVE_MUSIC_ROOT_URL;
        }

        queuedDownloads = 0;
        deletes = 0;

        syncMusicStatusText.setVisibility(View.VISIBLE);
        syncMusicStatusText.setText("Processing...");
        SyncMusic(deltaLink, null);
    }

    private void SyncMusic(String deltaLink, String nextLink) {
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

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                parameters,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            JSONArray driveItemArray = response.getJSONArray("value");

                            for (int i = 0; i < driveItemArray.length(); i++) {
                                JSONObject driveItem = driveItemArray.getJSONObject(i);

                                if (driveItem.has("file")) {
                                    JSONObject driveItemFile = driveItem.getJSONObject("file");

                                    if ((driveItemFile.has("mimeType") && driveItemFile.getString("mimeType").equals("audio/mpeg")) ||
                                            driveItem.getString("name").contains(".mp3")) {

                                        String path = driveItem.getJSONObject("parentReference").getString("path").replace(PATH_REPLACE, "");
                                        path += "/" + driveItem.getString("name");
                                        path = URLDecoder.decode(path, "UTF-8");

                                        if (driveItem.has("deleted")) {
                                            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), path);

                                            if (file.exists() && file.isFile()) {
                                                file.delete();
                                                deletes++;
                                            }
                                        } else {
                                            dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse("https://graph.microsoft.com/v1.0/me/drive/items/" + driveItem.getString("id") + "/content"));
                                            request.addRequestHeader("Authorization", "Bearer " + authResult.getAccessToken());
                                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, path);
                                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                                            request.setVisibleInDownloadsUi(true);

                                            enqueue = dm.enqueue(request);
                                            queuedDownloads++;
                                        }
                                    }
                                }
                            }

                            if (response.has(ODATA_NEXT_LINK)) {
                                SyncMusic(null, response.getString(ODATA_NEXT_LINK));
                            } else if (response.has(ODATA_DELTA_LINK)) {
                                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                                SharedPreferences.Editor editor = settings.edit();
                                editor.putString(ODATA_DELTA_LINK, response.getString(ODATA_DELTA_LINK));
                                editor.commit();

                                if (queuedDownloads == 0 && deletes == 0) {
                                    syncMusicStatusText.setText("Your collection is already in sync");
                                } else {
                                    syncMusicStatusText.setText("Queued " + queuedDownloads + " download(s). Performed " + deletes + " deletion(s)");
                                }
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "Failed to get JSONArray from value: " + e.toString());
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
        List<User> users = null;

        try {
            users = MSALClientApplication.getUsers();

            if (users == null) {
            } else if (users.size() == 1) {
                MSALClientApplication.remove(users.get(0));
                setUIToLoggedOut();
            } else {
                for (int i = 0; i < users.size(); i++) {
                    MSALClientApplication.remove(users.get(i));
                }
            }

            Toast.makeText(getBaseContext(), "Signed Out!", Toast.LENGTH_SHORT).show();
        } catch (MsalClientException e) {
            Log.d(TAG, "MSAL Exception Generated while getting users: " + e.toString());

        } catch (IndexOutOfBoundsException e) {
            Log.d(TAG, "User at this position does not exist: " + e.toString());
        }
    }

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "Permission is granted");
                return true;
            } else {
                Log.v(TAG, "Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG, "Permission is granted");
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission: " + permissions[0] + "was " + grantResults[0]);
        }
    }
}