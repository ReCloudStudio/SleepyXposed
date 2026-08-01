package io.github.recloudstudio.sleepyxposed

import android.util.Log
import de.robv.android.xposed.XposedBridge

object ModuleHooks {

    private const val TAG = "SleepyXposed"

    @JvmStatic
    fun initForSystemServer(classLoader: ClassLoader) {
        val logger: (String) -> Unit = { msg ->
            Log.i(TAG, msg)
            XposedBridge.log(msg)
        }
        logger("$TAG: Bootstrapping hooks for system_server")
        ForegroundAppMonitor(logger).initializeForSystemServer(classLoader)
    }
}
