# SleepyXposed — AGENTS.md

Xposed 模块，监控 Android 前台应用切换并上报到 Sleepy 服务器。

## 关键架构

- **包名**: `io.github.recloudstudio.sleepyxposed`，作用域必须锁定 `system`（系统框架，LSPosed 1.9.1+ 现代模块语义；`scope.list` 中 `android` 指向 Android系统 app 而非系统框架）
- **双入口**: `ModuleMain`（现代 LibXposed API）和 `LegacyEntry`（传统 XposedBridge API），均 hook `ActivityRecord.completeResumeLocked`/`completeResume`
- **配置链**: `MainActivity`（UI）→ `ConfigManager`（SharedPreferences + JSON 文件回退）→ `ForegroundAppMonitor`（XSharedPreferences/远程 Prefs 读取）
- **ConfigManager** 将配置同步写入 credential 和 device-protected 存储，并通过 `makePrefsWorldReadable()` 暴露给系统进程

## 构建

```bash
./gradlew assembleRelease   # 产出 release APK
```

- minSdk 26, targetSdk 34, compileSdk 34
- AGP 8.1.0, Kotlin 1.9.0, Gradle 9.3.1（配置缓存启用）
- 签名: 根目录 `keystore.properties` + `*.jks`

## 注意事项

- `settings.gradle`（Groovy）包含 Xposed maven (`api.xposed.info`)，`settings.gradle.kts`（Gradle init 生成）缺少此仓库。构建若报 `de.robv.android.xposed:api:82` 找不到，需确认使用的是 `.gradle` 文件。
- LibXposed API 参考: https://github.com/libxposed/api
- 构建时需安装 Android SDK 34
- CI: `.github/workflows/build.yml`，push 分支/PR/tag 时构建；push `v*` tag 自动发布 GitHub Release（附 debug + release APK）。无测试文件

## 依赖

| 依赖 | 版本 |
| --- | --- |
| `io.github.libxposed:api` | 102.0.0（compileOnly） |
| `de.robv.android.xposed:api` | 82（compileOnly） |
| OkHttp | 4.12.0 |
| Kotlin Coroutines | 1.8.1 |

## 调试

```bash
adb logcat | grep SleepyXposed
```
