package io.github.recloudstudio.sleepyxposed

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LegacyEntry : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "SleepyXposed"
        private const val SYSTEM_SERVER = "android"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != SYSTEM_SERVER) return
        val logger: (String) -> Unit = { msg ->
            Log.i(TAG, msg)
            XposedBridge.log(msg)
        }
        try {
            logger("$TAG: Legacy entry loaded, bootstrapping monitor")
            ForegroundAppMonitor(logger).initializeForSystemServer(lpparam.classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "$TAG: Legacy bootstrap failed", t)
            XposedBridge.log("$TAG: Legacy bootstrap failed: ${t.message}")
        }
    }
}
