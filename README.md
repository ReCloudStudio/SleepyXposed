# SleepyXposed - Sleepy 客户端的 Xposed 模块实现

<!-- [[简体中文]](README.md) | [[English]](README_en.md) -->

[![GitHub License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![GitHub Release](https://img.shields.io/badge/release-v1.0-blue)](https://github.com/RhenCloud/SleepyXposed/releases)

一个 Xposed 模块，用于实时监控 Android 设备的前台应用切换，并自动同步到 Sleepy 服务器，用于个人在线状态的展示。

**重要：本项目部分代码使用AI技术生成，请您谨慎使用。**

## ✨ 核心功能

- 🔍 **实时应用监控** - 无缝监控前台应用程序切换
- 🌐 **自动上报** - 将应用状态自动报告到 Sleepy 服务器
- 🔋 **电池追踪** - 实时获取设备电量百分比和充电状态
- ⚙️ **灵活配置** - 用户友好的设置界面，支持自定义服务器
- 📱 **应用信息** - 获取并显示当前前台应用的详细信息
- 🎵 **媒体状态上报** - 可选功能，将当前播放的媒体（标题/艺术家）作为独立设备上报，可自定义设备 ID 与显示名称
- 📝 **日志系统** - 内置多级别日志，便于调试和问题排查

## 🎵 媒体状态上报

在主界面的「媒体状态上报」区域可以：

1. 开关此功能（默认关闭）
2. 自定义媒体状态上报使用的**设备 ID** 和**显示名称**（与常规设备状态相互独立，可在 Sleepy 页面上显示为单独的一项）
3. 选择获取媒体播放状态的方式：

| 方式               | 说明                                                                 | 是否需要额外授权 |
| ------------------ | -------------------------------------------------------------------- | ----------------- |
| **自动（推荐）**   | 根据检测到的 Android 版本和系统 UI（ROM）厂商，自动在「系统钩子」和「通知监听」两种方式中选择最合适的一种（不自动选择 Dumpsys Shell） | 视结果而定         |
| **系统钩子**       | 通过 Xposed 钩子在 system_server 进程内直接调用 `MediaSessionManager` 获取，零配置 | 否                 |
| **通知监听**       | 使用标准 `NotificationListenerService` API，在应用自身进程内运行，跨 ROM 兼容性最好 | 是（需在设置中手动授权一次，可点击应用内按钮直达） |
| **Dumpsys Shell**  | 通过解析 `dumpsys media_session` 命令输出获取，思路参考自 Magisk 版 Sleepy 脚本，作为兼容性/调试备用方案 | 否（复用系统钩子权限） |

选择「自动」时，模块会检测当前设备是否为 MIUI/HyperOS、ColorOS、FuntouchOS/OriginOS、EMUI/MagicUI、One UI、Flyme 等定制系统：

- 接近原生 / AOSP 的系统 → 推荐**系统钩子**（无需额外操作）
- 检测到重度定制系统 → 推荐**通知监听**（这类系统通常对后台/系统级调用有更多限制，通知监听是官方标准方案，兼容性更好）

如果选择「通知监听」方式，请点击应用内的「授予通知访问权限」按钮，在系统设置中为 SleepyXposed 开启通知访问权限后功能才会生效。

## TODO

- [ ] 优化 UI 交互
- [ ] 优化日志系统
- [ ] 增加锁屏检测

## 📋 系统要求

| 要求               | 版本/说明                  |
| ------------------ | -------------------------- |
| **Android**        | 7.0 (API 24) 或更高        |
| **Xposed/LSPosed** | 已安装并激活               |
| **权限**           | Root 访问权限              |
| **网络**           | 互联网连接（用于上报数据） |

## 🚀 快速开始

### 1. 安装模块

#### 方式 A：使用预编译 APK（推荐）

从 [GitHub Actions](https://github.com/RhenCloud/SleepyXposed/actions) 下载最新的预编译 APK 文件：

1. 进入 Actions 页面
2. 选择最新的成功运行
3. 下载 `app-release.apk` 文件
4. 在设备上安装 APK

#### 方式 B：从源代码构建

```bash
# 克隆仓库
git clone https://github.com/RhenCloud/SleepyXposed.git
cd SleepyXposed

# 构建 APK
./gradlew assembleRelease

# 使用 adb 安装（可选）
adb install app/build/outputs/apk/release/app-release.apk
```

### 2. 配置 Sleepy 服务器

启动应用后，进行以下配置：

#### 必填项

| 字段           | 说明              | 示例                      |
| -------------- | ----------------- | ------------------------- |
| **服务器地址** | Sleepy 服务器地址 | `https://your-sleepy.com` |
| **服务器密钥** | Sleepy 认证密钥   | `your-secret-key-here`    |
| **设备 ID**    | 唯一标识此设备    | `android-phone-1`         |

#### 可选项

| 字段         | 说明                     | 默认值   |
| ------------ | ------------------------ | -------- |
| **显示名称** | 在 Sleepy 页面显示的名称 | 设备型号 |
| **启用上报** | 是否启用数据上报         | 禁用     |

点击 **"保存配置"** 按钮保存设置。

### 3. 启用 Xposed 模块

1. 打开 Xposed/LSPosed 管理器
2. 找到 **"SleepyXposed"** 模块
3. **启用** 该模块
4. 在作用域中勾选 **"系统框架"**
5. 重启设备

### 4. 验证工作状态

- 打开 SleepyXposed 应用
- 检查日志栏，查看是否有活动日志
- 访问 Sleepy 服务器页面，验证设备是否在线并显示当前应用

## 🔐 发布签名

`release` 构建会读取项目根目录下的 `keystore.properties`。你可以先复制 `keystore.properties.example`，然后把真实信息填进去：

```properties
storeFile=release.jks
storePassword=你的密钥库密码
keyAlias=你的别名
keyPassword=你的密钥密码
```

把对应的 `release.jks` 放在项目根目录，或者把 `storeFile` 改成你的实际路径。配置完成后运行 `./gradlew assembleRelease`，生成的 APK 就会使用该签名。

如果要让 GitHub Actions 产出签名版 release APK，需要在仓库 Secrets 中配置以下内容：

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

其中 `ANDROID_KEYSTORE_BASE64` 是 keystore 文件经过 base64 编码后的内容。workflow 会在非 PR 构建时自动恢复密钥并生成签名版 release 包。

## 📖 使用指南

### 查看实时日志

```bash
adb logcat | grep "SleepyXposed"
```

### 常见问题排查

| 问题           | 解决方案                                                                                    |
| -------------- | ------------------------------------------------------------------------------------------- |
| 应用未上报数据 | 1. 检查 "启用上报" 是否打开<br>2. 验证网络连接<br>3. 检查日志是否有错误信息                 |
| 连接超时       | 1. 验证服务器地址是否正确<br>2. 检查网络连接<br>3. 确认防火墙设置                           |
| 模块不工作     | 1. 检查 Xposed/LSPosed 是否启用<br>2. 验证设备是否已重启<br>3. 查看 Xposed 日志获取错误信息 |
| 电量信息不显示 | 确认设备未禁用电池状态权限                                                                  |

### API 集成示例

如果你正在运行自己的 Sleepy 服务器，可以按照以下格式集成数据：

```bash
curl -X POST https://your-server.com/api/device/set \
  -H "Content-Type: application/json" \
  -d '{
    "secret": "your-secret-key",
    "device_id": "android-phone-1",
    "current_app": "com.example.app",
    "app_name": "Example App",
    "battery": 85,
    "charging": true
  }'
```

## 🔧 技术栈

- **语言**: Kotlin, Java
- **框架**: Android Framework, Xposed Framework
- **网络**: OkHttp
- **异步**: Kotlin Coroutines
- **UI**: Android Material Design

## 🔐 安全性说明

- 密钥安全：服务器密钥存储在本地 SharedPreferences 中，建议使用 Android Keystore 进一步加密
- 网络安全：建议使用 HTTPS 与服务器通信
- 权限最小化：模块仅请求必要的权限
- 隐私保护：应用信息仅用于本地显示和服务器上报，不做其他用途

## 📝 关于 Sleepy

Sleepy 是一个个人在线状态和应用展示项目：

- **官方地址**: [sleepy-project/sleepy](https://github.com/sleepy-project/sleepy)
- **官方演示站点**: [sleepy.wyf9.top](https://sleepy.wyf9.top)
- **RhenCloud的个人站点**: [sleepy.rhen.cloud](https://sleepy.rhen.cloud)

## 📄 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 联系方式

- GitHub Issues: [RhenCloud/SleepyXposed/issues](https://github.com/RhenCloud/SleepyXposed/issues)
- Email: <i@rhen.cloud>

## 🙏 致谢

感谢以下社区及项目的支持：

- [LSPosed](https://github.com/LSPosed/LSPosed)
- [Sleepy](https://github.com/sleepy-project/sleepy)
- 图标来源：[SiiWay Icons - Sleepy](https://icons.siiway.org/sleepy)（由 [@NtKrnl32](https://github.com/NtKrnl32) 和 [@XiaoYuan151](https://github.com/XiaoYuan151) 制作）
