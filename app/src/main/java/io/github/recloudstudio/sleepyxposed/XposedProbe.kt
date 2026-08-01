package io.github.recloudstudio.sleepyxposed

/**
 * Application-process probe for module activation.
 *
 * Returns false when the method is not rewritten by a framework hook. If LSPosed scopes this
 * package and an optional hook is installed, it can force the return value to true. With the
 * current default scope (`android` / system_server only), the app process typically cannot prove
 * activation; the UI still surfaces config + permission health as operational status.
 *
 * Do not rename or inline without considering external hooks.
 */
object XposedProbe {
    @JvmStatic
    @Suppress("FunctionOnlyReturningConstant")
    fun isModuleActive(): Boolean = false
}
