package com.brianhmcbride.onedrivemusicsync;

import android.Manifest;
import android.app.AlertDialog;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.brianhmcbride.onedrivemusicsync.data.MusicSyncContract;
import com.brianhmcbride.onedrivemusicsync.data.MusicSyncDbHelper;
import com.microsoft.identity.client.*;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.class.getSimpleName();

    Button signInButton;
    Button signOutButton;
    Button syncMusicButton;
    Button clearSyncedCollectionButton;
    TextView syncMusicStatusText;
    TextView welcomeText;
    LinearLayout linlaHeaderProgress;

    private BroadcastReceiver clearSyncedCollectonCompleteBroadcastReceiver;
    private BroadcastReceiver syncCompleteBroadcastReceiver;
    private BroadcastReceiver syncPartialBroadcastReceiver;
    private BroadcastReceiver deletionsCompleteBroadcastReceiver;
    private BroadcastReceiver downloadsCompleteBroadcastReceiver;

    private Handler triggerDownloadsHandler = new Handler();
    private Handler triggerRefreshTokenHandler = new Handler();

    MusicSyncDbHelper dbHelper;
    SQLiteDatabase dbReader;

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncCompleteBroadcastReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncPartialBroadcastReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(clearSyncedCollectonCompleteBroadcastReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(deletionsCompleteBroadcastReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadsCompleteBroadcastReceiver);

        triggerRefreshTokenHandler.removeCallbacks(triggerRefreshTokenRunnable);
        dbHelper.close();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        isStoragePermissionGranted();

        setContentView(R.layout.activity_main);

        dbHelper = new MusicSyncDbHelper(App.get());
        dbReader = dbHelper.getReadableDatabase();

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

        linlaHeaderProgress = (LinearLayout) findViewById(R.id.linlaHeaderProgress);

        registerReceivers();

        if (AuthenticationManager.getInstance().getAuthenticatedUserCount() == 1) {
            AuthenticationManager.getInstance().acquireTokenSilent(getAuthSilentCallback());
        } else {
            signInButton.setVisibility(View.VISIBLE);
        }
    }

    private void registerReceivers() {
        syncCompleteBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String[] projection = new String[]{"COUNT(*)"};
                String selection = String.format("%s = ?", MusicSyncContract.DriveItem.COLUMN_NAME_IS_MARKED_FOR_DELETION);
                String[] selectionArgs = new String[]{"1"};

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
                int numberOfMarkedDeletions = cursor.getInt(0);
                cursor.close();

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
                int numberOfDownloads = cursor.getInt(0);
                cursor.close();

                if (numberOfMarkedDeletions == 0 && numberOfDownloads == 0) {
                    syncMusicStatusText.setText(R.string.collection_in_sync);
                    clearSyncedCollectionButton.setVisibility(View.VISIBLE);
                } else {
                    syncMusicButton.setVisibility(View.GONE);
                    syncMusicStatusText.setText(getString(R.string.pending_sync_message, numberOfMarkedDeletions, numberOfDownloads));
                    MusicSyncIntentService.startActionDelete(getActivity());
                }

                linlaHeaderProgress.setVisibility(View.GONE);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(syncCompleteBroadcastReceiver, new IntentFilter(MusicSyncIntentService.BROADCAST_SYNC_COMPLETE_ACTION));

        syncPartialBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(syncPartialBroadcastReceiver, new IntentFilter(MusicSyncIntentService.BROADCAST_SYNC_PARTIAL_ACTION));

        clearSyncedCollectonCompleteBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                clearSyncedCollectionButton.setVisibility(View.GONE);
                syncMusicStatusText.setText(getString(R.string.initial_sync_message));
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(clearSyncedCollectonCompleteBroadcastReceiver, new IntentFilter(MusicSyncIntentService.BROADCAST_CLEAR_SYNCED_COLLECTION_COMPLETE_ACTION));

        deletionsCompleteBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                triggerDownloadsHandler.postDelayed(triggerDownloadsRunnable, 100);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(deletionsCompleteBroadcastReceiver, new IntentFilter(MusicSyncIntentService.BROADCAST_DELETE_COMPLETE_ACTION));

        downloadsCompleteBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                triggerDownloadsHandler.removeCallbacks(triggerDownloadsRunnable);
                syncMusicButton.setVisibility(View.VISIBLE);
                syncMusicStatusText.setText(getString(R.string.collection_in_sync));
                clearSyncedCollectionButton.setVisibility(View.VISIBLE);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(downloadsCompleteBroadcastReceiver, new IntentFilter(MusicSyncIntentService.BROADCAST_DOWNLOADS_COMPLETE_ACTION));
    }

    private Runnable triggerDownloadsRunnable = new Runnable() {
        @Override
        public void run() {
            MusicSyncIntentService.startActionDownload(getActivity());

            triggerDownloadsHandler.postDelayed(this, MusicSyncIntentService.WAIT_TIME_BETWEEN_DOWNLOAD_BATCHES);
        }
    };

    private Runnable triggerRefreshTokenRunnable = new Runnable() {
        @Override
        public void run() {
            AuthenticationManager.getInstance().refreshToken(new AuthenticationCallback() {
                @Override
                public void onSuccess(AuthenticationResult authenticationResult) {
                    Log.d(TAG, "Successfully refreshed token");
                    triggerRefreshTokenHandler.postDelayed(triggerRefreshTokenRunnable, 2700000);
                }

                @Override
                public void onError(MsalException exception) {
                    Log.e(TAG, "Failure refreshing token", exception);
                }

                @Override
                public void onCancel() {

                }
            });
        }
    };

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

        triggerRefreshTokenHandler.postDelayed(triggerRefreshTokenRunnable, 2700000);
    }

    private void setUIToLoggedOut() {
        signInButton.setVisibility(View.VISIBLE);
        syncMusicButton.setVisibility(View.GONE);
        syncMusicStatusText.setVisibility(View.GONE);
        signOutButton.setVisibility(View.GONE);
        clearSyncedCollectionButton.setVisibility(View.GONE);
        welcomeText.setVisibility(View.GONE);
        triggerRefreshTokenHandler.removeCallbacks(triggerRefreshTokenRunnable);
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
            showToast(getString(R.string.sign_in_before_sync));
            return;
        }

        syncMusicStatusText.setVisibility(View.VISIBLE);
        syncMusicStatusText.setText("");

        linlaHeaderProgress.setVisibility(View.VISIBLE);

        String deltaLink = DeltaLinkManager.getInstance().getDeltaLink();

        if (deltaLink == null) {
            deltaLink = MusicSyncIntentService.DRIVE_MUSIC_ROOT_URL;
        }

        MusicSyncIntentService musicSyncIntentService = new MusicSyncIntentService();
        musicSyncIntentService.SyncMusic(deltaLink, null);
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