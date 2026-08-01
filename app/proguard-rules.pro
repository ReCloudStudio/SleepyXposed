# ---- SleepyXposed R8 rules ----
# Do NOT keep the whole app package; that prevents shrinking Compose / Material.

# libxposed / legacy Xposed entry (compileOnly — names kept for runtime)
-keep class com.rhencloud.sleepyxposed.ModuleMain { *; }
-keep class * extends io.github.libxposed.api.XposedModule { *; }

# Components declared in the manifest
-keep class com.rhencloud.sleepyxposed.MainActivity { *; }
-keep class com.rhencloud.sleepyxposed.ConfigContentProvider { *; }
-keep class com.rhencloud.sleepyxposed.MediaListenerService { *; }

# Optional hook target for LSPosed (must keep signature)
-keep class com.rhencloud.sleepyxposed.XposedProbe {
    public static boolean isModuleActive();
}

# Config model used across processes / JSON
-keepclassmembers class com.rhencloud.sleepyxposed.SleepyConfig { *; }
-keepclassmembers class com.rhencloud.sleepyxposed.MediaMethod { *; }

# Hook / system-server logic must not be stripped (reflection + Xposed)
-keep class com.rhencloud.sleepyxposed.ConfigManager { *; }
-keep class com.rhencloud.sleepyxposed.ForegroundAppMonitor { *; }
-keep class com.rhencloud.sleepyxposed.MediaStatusMonitor { *; }
-keep class com.rhencloud.sleepyxposed.SleepyApiClient { *; }
-keep class com.rhencloud.sleepyxposed.RomDetector { *; }

# Reflection in ConfigManager (XSharedPreferences)
-dontwarn de.robv.android.xposed.**
-dontwarn io.github.libxposed.**

# Compose / Kotlin
-dontwarn org.jetbrains.annotations.**

# Keep line numbers for crash logs (small cost)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
