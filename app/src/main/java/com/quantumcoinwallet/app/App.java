package com.quantumcoinwallet.app;

import android.app.Application;
import android.util.Log;

import timber.log.Timber;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new ReleaseTree());
        }
    }

    /**
     * Release tree: drops VERBOSE/DEBUG/INFO; forwards WARN/ERROR to the system log
     * without any message payload (so no sensitive data leaks to logcat in release).
     */
    private static final class ReleaseTree extends Timber.Tree {
        @Override
        protected boolean isLoggable(String tag, int priority) {
            return priority >= Log.WARN;
        }

        @Override
        protected void log(int priority, String tag, String message, Throwable t) {
            if (priority < Log.WARN) return;
            Log.println(priority, tag != null ? tag : "QCW", "evt");
        }
    }
}
