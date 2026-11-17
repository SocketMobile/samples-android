package com.example.socketmobile.android.hellocapture;

import android.app.Application;

import com.socketmobile.capture.android.Capture;

public class HelloCaptureApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Capture once for the whole process, so it is set up no matter
        // which activity Android restores into. Capture registers every resumed
        // activity on its EventBus automatically: that is how the @Subscribe
        // methods in the activities receive their events without an explicit
        // EventBus.register() call.
        Capture.builder(this)
                .enableLogging(BuildConfig.DEBUG)
                .build();
    }
}
