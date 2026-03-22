package com.martinrevert.latorrentola.network;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.martinrevert.latorrentola.constants.Constants;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class FCMRegistrationWorker extends Worker {

    private static final String TAG = "FCMRegistrationWorker";

    public FCMRegistrationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String token = getInputData().getString("token");
        if (token == null) {
            return Result.failure();
        }

        RequestFCMInterface requestFCMInterface = new Retrofit.Builder()
                .baseUrl(Constants.FCM_BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(RequestFCMInterface.class);

        try {
            // Using blockingGet() since Worker runs on a background thread
            requestFCMInterface.subscribeToTopic(token).blockingAwait();
            Log.d(TAG, "Successfully registered token to server");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Failed to register token to server, retrying...", e);
            return Result.retry();
        }
    }
}
