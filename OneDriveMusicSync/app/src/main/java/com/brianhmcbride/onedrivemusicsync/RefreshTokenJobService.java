package com.brianhmcbride.onedrivemusicsync;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.AuthenticationResult;
import com.microsoft.identity.client.MsalException;

public class RefreshTokenJobService extends JobService {
    public static final String TAG = RefreshTokenJobService.class.getSimpleName();

    private Handler mJobHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage( Message msg ) {
            AuthenticationManager.getInstance().refreshToken(new AuthenticationCallback() {
                @Override
                public void onSuccess(AuthenticationResult authenticationResult) {
                    Log.d(TAG, "Successfully refreshed token");
                }

                @Override
                public void onError(MsalException exception) {
                    Log.e(TAG, "Failure refreshing token", exception);
                }

                @Override
                public void onCancel() {

                }
            });

            jobFinished( (JobParameters) msg.obj, false );
            return true;
        }

    } );
    @Override
    public boolean onStartJob(JobParameters params) {
        mJobHandler.sendMessage( Message.obtain( mJobHandler, 1, params ) );
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        mJobHandler.removeMessages( 1 );
        return false;
    }
}
