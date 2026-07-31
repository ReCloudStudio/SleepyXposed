package io.github.recloudstudio.sleepyxposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class ModuleMain : XposedModule() {
    companion object {
        private const val TAG = "SleepyXposed"
        var instance: ModuleMain? = null
            private set
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        instance = this
        log(Log.INFO, TAG, "onModuleLoaded: ${param.processName}")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        instance = this
        try {
            ForegroundAppMonitor { message -> log(Log.INFO, TAG, message) }
                .initializeForSystemServer(param.classLoader)
            log(Log.INFO, TAG, "Modern API bootstrap succeeded")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Modern API bootstrap failed", t)
        }

        try {
            MediaStatusMonitor { message -> log(Log.INFO, TAG, message) }
                .initializeForSystemServer(param.classLoader)
            log(Log.INFO, TAG, "Media status monitor bootstrap succeeded")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Media status monitor bootstrap failed", t)
        }
    }
}
