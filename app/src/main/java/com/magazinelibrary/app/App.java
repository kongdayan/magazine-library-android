package com.magazinelibrary.app;

import android.app.Application;
import android.content.Context;

/** Holds the process-wide context used for localized strings outside activities. */
public class App extends Application {
    private static App instance;

    public static Context get() {
        if (instance == null) throw new IllegalStateException("App is not created yet");
        return instance;
    }

    public static Context context() {
        return get();
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }
}
