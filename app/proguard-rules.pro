# ---- SleepyXposed R8 rules ----
# Do NOT keep the whole app package; that prevents shrinking Compose / Material.

# libxposed / legacy Xposed entry (compileOnly — names kept for runtime)
-keep class io.github.recloudstudio.sleepyxposed.ModuleMain { *; }
-keep class * extends io.github.libxposed.api.XposedModule { *; }

# Components declared in the manifest
-keep class io.github.recloudstudio.sleepyxposed.MainActivity { *; }
-keep class io.github.recloudstudio.sleepyxposed.ConfigContentProvider { *; }
-keep class io.github.recloudstudio.sleepyxposed.MediaListenerService { *; }

# Optional hook target for LSPosed (must keep signature)
-keep class io.github.recloudstudio.sleepyxposed.XposedProbe {
    public static boolean isModuleActive();
}

# Config model used across processes / JSON
-keepclassmembers class io.github.recloudstudio.sleepyxposed.SleepyConfig { *; }
-keepclassmembers class io.github.recloudstudio.sleepyxposed.MediaMethod { *; }

# Hook / system-server logic must not be stripped (reflection + Xposed)
-keep class io.github.recloudstudio.sleepyxposed.ConfigManager { *; }
-keep class io.github.recloudstudio.sleepyxposed.ForegroundAppMonitor { *; }
-keep class io.github.recloudstudio.sleepyxposed.MediaStatusMonitor { *; }
-keep class io.github.recloudstudio.sleepyxposed.SleepyApiClient { *; }
-keep class io.github.recloudstudio.sleepyxposed.RomDetector { *; }

# Reflection in ConfigManager (XSharedPreferences)
-dontwarn de.robv.android.xposed.**
-dontwarn io.github.libxposed.**

# Compose / Kotlin
-dontwarn org.jetbrains.annotations.**

# Keep line numbers for crash logs (small cost)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
