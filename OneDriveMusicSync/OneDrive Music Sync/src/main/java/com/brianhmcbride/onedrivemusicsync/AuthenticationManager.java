package com.brianhmcbride.onedrivemusicsync;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.AuthenticationResult;
import com.microsoft.identity.client.MsalClientException;
import com.microsoft.identity.client.MsalException;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.User;

import java.util.List;

class AuthenticationManager {
    private static final String TAG = AuthenticationManager.class.getSimpleName();

    private static final String CLIENT_ID = "8fbb52c6-a9eb-41a1-9933-4be38cdefbd3";
    private String SCOPES[] = {"https://graph.microsoft.com/User.Read", "https://graph.microsoft.com/Files.Read"};
    private PublicClientApplication MSALClientApplication;
    private AuthenticationResult authResult;

    private static AuthenticationManager instance = new AuthenticationManager();

    static AuthenticationManager getInstance() {
        if (instance == null) {
            instance = getSync();
        }

        return instance;
    }

    private static synchronized AuthenticationManager getSync() {
        if (instance == null) {
            instance = new AuthenticationManager();
        }

        return instance;
    }

    private AuthenticationManager() {
        MSALClientApplication = new PublicClientApplication(App.get(), CLIENT_ID);
    }

    String getAccessToken() {
        if (this.authResult == null) {
            return null;
        } else {
            return this.authResult.getAccessToken();
        }
    }

    String getAuthenticatedUserName() {
        if (authResult == null) {
            return null;
        } else {
            return authResult.getUser().getName();
        }
    }

    PublicClientApplication getMSALClientApplication() {
        return this.MSALClientApplication;
    }

    void acquireToken(Activity activity, @NonNull final AuthenticationCallback callback) {
        MSALClientApplication.acquireToken(activity, SCOPES, new AuthenticationCallback() {
            @Override
            public void onSuccess(AuthenticationResult authenticationResult) {
                Log.d(TAG, "Successfully authenticated");

                authResult = authenticationResult;
                callback.onSuccess(null);
            }

            @Override
            public void onError(MsalException exception) {
                Log.d(TAG, "Authentication failed: " + exception.toString());
                callback.onError(exception);
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "User cancelled login.");
                callback.onCancel();
            }
        });
    }

    void refreshToken(@NonNull final AuthenticationCallback callback) {
        try {
            List<User> users = MSALClientApplication.getUsers();

            MSALClientApplication.acquireTokenSilentAsync(SCOPES, users.get(0), "", true, new AuthenticationCallback() {
                @Override
                public void onSuccess(AuthenticationResult authenticationResult) {
                    Log.d(TAG, "Successfully authenticated");

                    authResult = authenticationResult;
                    callback.onSuccess(null);
                }

                @Override
                public void onError(MsalException exception) {
                    Log.d(TAG, "Authentication failed: " + exception.toString());
                    callback.onError(exception);
                }

                @Override
                public void onCancel() {
                    Log.d(TAG, "User cancelled login.");
                    callback.onCancel();
                }
            });
        } catch (MsalClientException e) {
            String failureMessage = "MSAL Exception Generated while getting users.";
            Log.e(TAG, failureMessage, e);
            showToast(failureMessage);
        }
    }

    void acquireTokenSilent(@NonNull final AuthenticationCallback callback) {
        try {
            List<User> users = MSALClientApplication.getUsers();

            if (users != null && users.size() == 1) {
                MSALClientApplication.acquireTokenSilentAsync(SCOPES, users.get(0),
                        new AuthenticationCallback() {
                            @Override
                            public void onSuccess(AuthenticationResult authenticationResult) {
                                Log.d(TAG, "Successfully authenticated");
                                authResult = authenticationResult;

                                callback.onSuccess(null);
                            }

                            @Override
                            public void onError(MsalException exception) {
                                Log.d(TAG, "Authentication failed: " + exception.toString());
                                callback.onError(exception);
                            }

                            @Override
                            public void onCancel() {
                                Log.d(TAG, "User cancelled login.");
                                callback.onCancel();
                            }
                        });
            }
        } catch (MsalClientException e) {
            String failureMessage = "MSAL Exception Generated while getting users.";
            Log.e(TAG, failureMessage, e);
            showToast(failureMessage);
        }
    }

    int getAuthenticatedUserCount() {
        try {
            List<User> users = MSALClientApplication.getUsers();

            if (users == null) {
                return 0;
            }

            return users.size();
        } catch (MsalClientException e) {
            String failureMessage = "MSAL Exception Generated while getting users.";
            Log.e(TAG, failureMessage, e);
            showToast(failureMessage);
            return 0;
        }
    }

    boolean signOut() {
        boolean isSignOutSuccessful = true;
        try {
            List<User> users = MSALClientApplication.getUsers();

            if (users == null) {
                showToast("No users to sign out");
            } else if (users.size() == 1) {
                MSALClientApplication.remove(users.get(0));
            } else {
                for (int i = 0; i < users.size(); i++) {
                    MSALClientApplication.remove(users.get(i));
                }
            }

            showToast("Signed out!");
        } catch (MsalClientException e) {
            isSignOutSuccessful = false;
            String failureMessage = "MSAL Exception Generated while getting users";
            Log.e(TAG, failureMessage, e);
            showToast(failureMessage);
        }

        return isSignOutSuccessful;
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
