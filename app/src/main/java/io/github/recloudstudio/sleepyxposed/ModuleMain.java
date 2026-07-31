package io.github.recloudstudio.sleepyxposed;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import de.robv.android.xposed.XposedBridge;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;

public class ModuleMain extends XposedModule {
    private static final String TAG = "SleepyXposed";
    private static final int MAX_RETRIES = 30;
    private static final long RETRY_DELAY_MS = 1000L;

    public ModuleMain() {
    }

    public ModuleMain(XposedInterface base, ModuleLoadedParam param) {
        if (param.isSystemServer()) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                Log.i(TAG, TAG + ": Module constructed in system_server, scheduling hooks");
                XposedBridge.log(TAG + ": Module constructed in system_server");
                retryHook(cl, 0);
            }
        }
    }

    private void retryHook(ClassLoader cl, int attempt) {
        try {
            Class.forName("com.android.server.wm.ActivityRecord", false, cl);
            Log.i(TAG, TAG + ": ActivityRecord found on attempt " + (attempt + 1));
            XposedBridge.log(TAG + ": ActivityRecord found, initializing hooks");
            ModuleHooks.initForSystemServer(cl);
        } catch (ClassNotFoundException e) {
            if (attempt < MAX_RETRIES) {
                new Handler(Looper.getMainLooper()).postDelayed(
                    () -> retryHook(cl, attempt + 1), RETRY_DELAY_MS);
            } else {
                String msg = TAG + ": Failed to find ActivityRecord after " + MAX_RETRIES + " attempts";
                Log.e(TAG, msg);
                XposedBridge.log(msg);
            }
        }
    }
}
