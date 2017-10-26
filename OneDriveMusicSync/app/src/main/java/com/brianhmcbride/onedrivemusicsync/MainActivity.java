package com.brianhmcbride.onedrivemusicsync;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.identity.client.*;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.class.getSimpleName();

    Button signInButton;
    Button signOutButton;
    Button syncMusicButton;
    Button clearSyncedCollectionButton;
    TextView syncMusicStatusText;
    TextView welcomeText;

    private BroadcastReceiver clearSyncedCollectonCompleteBroadcastReceiver;
    private BroadcastReceiver syncCompleteBroadcastReceiver;
    private BroadcastReceiver syncPartialBroadcastReceiver;

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncCompleteBroadcastReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncPartialBroadcastReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(clearSyncedCollectonCompleteBroadcastReceiver);
    }

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

        clearSyncedCollectionButton = (Button) findViewById(R.id.clearSyncedCollection);
        clearSyncedCollectionButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                clearSyncedCollectionClicked();
            }
        });

        syncMusicStatusText = (TextView) findViewById(R.id.syncMusicStatus);
        welcomeText = (TextView) findViewById(R.id.welcome);

        IntentFilter musicSyncCompleteIntentFilter = new IntentFilter(MusicSyncIntentService.BROADCAST_SYNC_COMPLETE_ACTION);

        syncCompleteBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String status = intent.getStringExtra(MusicSyncIntentService.EXTENDED_DATA_STATUS);

                String[] statuses = status.split("\\|");
                int queuedDownloads = Integer.parseInt(statuses[0]);
                int deletes = Integer.parseInt(statuses[1]);
                int failures = Integer.parseInt(statuses[2]);

                if (queuedDownloads == 0 && deletes == 0) {
                    syncMusicStatusText.setText(getString(R.string.collection_already_in_sync));
                } else {
                    syncMusicStatusText.setText("Completed discovery." + System.getProperty("line.separator") +
                            "Queuing " + queuedDownloads + " downloads for new song(s)." + System.getProperty("line.separator") +
                            "Deleting " + deletes + " song(s)." + System.getProperty("line.separator") +
                            "Sync failures: " + failures);
                }

                clearSyncedCollectionButton.setVisibility(View.VISIBLE);
                MusicSyncIntentService.startActionDownloadAndDelete(getActivity());
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(syncCompleteBroadcastReceiver, musicSyncCompleteIntentFilter);

        IntentFilter musicSyncPartialIntentFilter = new IntentFilter(MusicSyncIntentService.BROADCAST_SYNC_PARTIAL_ACTION);

        syncPartialBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String status = intent.getStringExtra(MusicSyncIntentService.EXTENDED_DATA_STATUS);

                String[] statuses = status.split("\\|");
                int queuedDownloads = Integer.parseInt(statuses[0]);
                int deletes = Integer.parseInt(statuses[1]);
                int failures = Integer.parseInt(statuses[2]);

                syncMusicStatusText.setText("Processing..." + System.getProperty("line.separator") +
                        "Discovered " + queuedDownloads + " new song(s)." + System.getProperty("line.separator") +
                        "Discovered " + deletes + " deletion(s)." + System.getProperty("line.separator") +
                        "Sync failures: " + failures);
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(syncPartialBroadcastReceiver, musicSyncPartialIntentFilter);

        IntentFilter musicSyncClearSyncedCollectionCompleteIntentFilter = new IntentFilter(MusicSyncIntentService.BROADCAST_CLEAR_SYNCED_COLLECTION_COMPLETE_ACTION);

        clearSyncedCollectonCompleteBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                clearSyncedCollectionButton.setVisibility(View.GONE);
                syncMusicStatusText.setText(getString(R.string.initial_sync_message));
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(clearSyncedCollectonCompleteBroadcastReceiver, musicSyncClearSyncedCollectionCompleteIntentFilter);

        if (AuthenticationManager.getInstance().getAuthenticatedUserCount() == 1) {
            AuthenticationManager.getInstance().acquireTokenSilent(getAuthSilentCallback());
        } else {
            signInButton.setVisibility(View.VISIBLE);
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
                setUIToLoggedIn();
            }

            @Override
            public void onError(MsalException exception) {
                showToast("Authentication failed");
            }

            @Override
            public void onCancel() {
                showToast("User cancelled login.");
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
                setUIToLoggedIn();
            }

            @Override
            public void onError(MsalException exception) {
                showToast("Authentication failed");
            }

            @Override
            public void onCancel() {
                showToast("User canceled login");
            }
        };
    }

    private void setUIToLoggedIn() {
        signInButton.setVisibility(View.GONE);
        signOutButton.setVisibility(View.VISIBLE);
        syncMusicButton.setVisibility(View.VISIBLE);
        syncMusicStatusText.setVisibility(View.VISIBLE);

        String deltaLink = DeltaLinkManager.getInstance().getDeltaLink();

        if (deltaLink == null) {
            syncMusicStatusText.setText(getString(R.string.initial_sync_message));
        } else {
            clearSyncedCollectionButton.setVisibility(View.VISIBLE);
            syncMusicStatusText.setText("");
        }

        welcomeText.setVisibility(View.VISIBLE);
        welcomeText.setText(getString(R.string.welcome, AuthenticationManager.getInstance().getAuthenticatedUserName()));
    }

    private void setUIToLoggedOut() {
        signInButton.setVisibility(View.VISIBLE);
        syncMusicButton.setVisibility(View.GONE);
        syncMusicStatusText.setVisibility(View.GONE);
        signOutButton.setVisibility(View.GONE);
        clearSyncedCollectionButton.setVisibility(View.GONE);
        welcomeText.setVisibility(View.GONE);
    }

    private void onSignInClicked() {
        AuthenticationManager.getInstance().acquireToken(getActivity(), getAuthInteractiveCallback());
    }

    /* Handles the redirect from the System Browser */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        AuthenticationManager.getInstance().getMSALClientApplication().handleInteractiveRequestRedirect(requestCode, resultCode, data);
    }

    private void onSyncMusicClicked() {
        if (AuthenticationManager.getInstance().getAccessToken() == null) {
            Toast.makeText(getBaseContext(), "You must sign in before syncing", Toast.LENGTH_SHORT).show();
            return;
        }

        syncMusicStatusText.setVisibility(View.VISIBLE);
        syncMusicStatusText.setText(getString(R.string.processing));

        MusicSyncIntentService.startActionSync(getActivity());
    }

    private void clearSyncedCollectionClicked() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("This will erase all music off your device. Are you sure?");
        alertDialogBuilder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                MusicSyncIntentService.startActionClearSyncedCollection(getActivity());
            }
        });

        alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    /* Clears a user's tokens from the cache.
    * Logically similar to "sign out" but only signs out of this app.
    */
    private void onSignOutClicked() {
        if (AuthenticationManager.getInstance().signOut()) {
            setUIToLoggedOut();
        }
    }

    public boolean isStoragePermissionGranted() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission is granted");
            return true;
        } else {
            Log.v(TAG, "Permission is revoked");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission: " + permissions[0] + "was " + grantResults[0]);
        }
    }

    private void showToast(final String msg) {
        //gets the main thread
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                // run this code in the main thread
                Toast.makeText(App.get(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}