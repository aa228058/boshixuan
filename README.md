<div align="center">

# 🥭 Mango Client · 芒果客户端

**面向 Android 的 Telegram 第三方增强客户端**  
**An independent, enhanced Telegram client for Android**

[![License: GPL v2](https://img.shields.io/badge/License-GPL--2.0-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Platform-Android-brightgreen)
![Version](https://img.shields.io/badge/Version-4.9%20(7009)-orange)

[简体中文](#-简体中文) · [English](#-english) · [源码 / Source](https://github.com/aa228058/boshixuan) · [版本发布 / Releases](https://github.com/aa228058/boshixuan/releases) · [官方频道 / Official Channel](https://t.me/hhhhp) · [修改建议 / Bug 反馈](https://t.me/zsq888)

</div>

---

## 🇨🇳 简体中文

### 项目简介

**芒果客户端（Mango Client）** 是基于 Telegram Android 源码开发的独立第三方客户端，重点改善消息处理效率、聊天管理、媒体体验、界面定制和隐私控制。项目开放源代码，方便用户了解实现方式，也欢迎开发者审查、反馈与贡献。

> **非官方声明：** 芒果客户端不是 Telegram 官方应用，也不隶属于 Telegram。Telegram 名称及相关商标归其相应权利人所有。

### ✨ 核心功能

| 分类 | 主要能力 |
| --- | --- |
| 💬 消息与聊天 | 消息管理、历史定位、关键词提醒、发送前确认、快捷操作 |
| 📁 文件与媒体 | 下载体验优化、媒体筛选、文件清理与保存辅助工具 |
| 🔐 隐私与安全 | 隐私设置增强、风险提醒、可自定义的交互行为 |
| 🌐 网络与代理 | 代理与连接管理、网络体验相关增强 |
| 🎨 个性与效率 | 界面定制、扩展中心、设置搜索及多种效率工具 |

功能随版本变化，具体以客户端内设置、版本更新记录及源码实现为准；部分功能可能受 Telegram 服务端规则、系统权限和网络环境限制。

### 📦 下载与版本

- **当前源码版本：** `4.9 (7009)`
- **Android 包名：** `com.boshixuan.im`
- **源码：** [GitHub · Mango](https://github.com/aa228058/boshixuan)
- **APK / 版本更新：** [GitHub Releases](https://github.com/aa228058/boshixuan/releases)

> Releases 页面中的安装包需要由项目维护者单独发布；仓库公开源码不代表已经发布 APK。请优先从可信的项目发布渠道获取安装包，并核对版本及签名信息。

### 🛠️ 源码构建

本仓库包含 Gradle 工程、`TMessagesProj`、`TMessagesProj_App`、JNI 与所需的第三方源码。主要步骤如下：

1. 使用 Android Studio **Open** 仓库根目录（不要只打开 `TMessagesProj`）。
2. 配置 Android SDK；根据 `TMessagesProj/build.gradle` 安装匹配的 NDK / CMake 版本。
3. 使用项目支持的 JDK（源码包构建说明提及 JDK 17 / 21），完成 Gradle Sync。
4. 按需配置自己的 Telegram API 凭据、Google 服务配置及签名信息；不要把真实密钥提交到公开仓库。
5. 在满足项目依赖与配置的环境下构建 Debug：

```bash
./gradlew :TMessagesProj_App:assembleDebug
```

Debug 产物通常位于：

```text
TMessagesProj_App/build/outputs/apk/debug/
```

Release 构建命令：

```bash
./gradlew :TMessagesProj_App:assembleRelease
```

Release 构建还需要你自己的签名文件与相应 Gradle 配置。仓库中用于开源展示的 Google API Key 已替换为占位符，相关服务在正确配置前可能无法工作。**这里提供的是构建入口说明，不代表已验证任意开发环境均能直接编译通过。**

更多细节见 [`mango_build_readme.txt`](mango_build_readme.txt)，但请勿照搬其中涉及发布签名密钥的历史打包说明，应以你自己的安全签名配置为准。

### 🔏 隐私与安全

芒果客户端的部分增强功能设计为在设备本地运行。公开源码是为了提高可审查性，但不等于已经完成第三方安全审计。

- Telegram 消息与媒体通信仍需要连接 Telegram 服务及你所选择的网络路径或代理。
- 使用翻译、外部链接、更新检查或其他联网功能时，可能发生相应网络请求，具体行为请以源码和功能设置为准。
- 不要在 Issues、日志或提交记录中公开手机号、会话、API Key、Bot Token、代理凭据、签名文件或其他私人数据。
- 欢迎通过源码审查与 Issues 提出可复现的安全或隐私问题。

### 📜 开源许可与致谢

本仓库基于 [Telegram Android](https://github.com/DrKLO/Telegram) 的开放源码开发，保留了上游及第三方组件的版权和许可证文件。仓库根目录附有 [`LICENSE`](LICENSE)（GNU GPL v2）；第三方组件可能适用不同许可证，以其各自文件为准。

重新分发、修改或编译本项目时，请遵守适用许可证、保留必要的版权声明，并按许可证要求提供对应源码。项目使用自己的应用名称和视觉标识，不代表 Telegram 官方认可或背书。

### 🤝 参与项目

欢迎提出 Bug、修改建议和 Pull Request：

- 📢 **官方频道：** [芒果客户端官方频道](https://t.me/hhhhp)
- 🐛 **修改建议 / Bug 反馈：** [@zsq888](https://t.me/zsq888)
- 💬 **交流群：** [Telegram 交流群](https://t.me/AKUOD)
- [GitHub Issues：公开问题追踪](https://github.com/aa228058/boshixuan/issues)
- [Pull Requests：提交代码改进](https://github.com/aa228058/boshixuan/pulls)

提交问题前请先检查是否已有相同反馈，并尽量提供系统版本、客户端版本、复现步骤及已脱敏的日志。

---

## 🌍 English

### About

**Mango Client** is an independent, third-party Android client built on Telegram's open-source Android codebase. It focuses on more efficient messaging, chat management, media handling, interface customization, and privacy controls.

The source code is published for transparency, review, and community contributions. **Mango Client is not an official Telegram app and is not affiliated with Telegram.**

### ✨ Highlights

| Area | Features |
| --- | --- |
| 💬 Messaging | Chat and message management, history navigation, keyword alerts, send confirmations, quick actions |
| 📁 Media & Files | Download-related improvements, media filtering, file organization and cleanup tools |
| 🔐 Privacy & Safety | Additional privacy settings, risk reminders, customizable interaction behavior |
| 🌐 Connectivity | Proxy and network management enhancements |
| 🎨 Customization | UI customization, an extension center, settings search, and productivity tools |

Availability varies by release, Android version, server-side behavior, and user settings.

### 📦 Download & Versions

- **Source version:** `4.9 (7009)`
- **Android application ID:** `com.boshixuan.im`
- **Source code:** [GitHub repository](https://github.com/aa228058/boshixuan)
- **APK releases:** [GitHub Releases](https://github.com/aa228058/boshixuan/releases)

> APK files are published separately by the maintainers. An open-source repository does not necessarily contain downloadable release binaries.

### 🛠️ Building from Source

1. Open the **repository root** in Android Studio.
2. Install the required Android SDK, NDK, and CMake versions as specified by the Gradle project.
3. Configure a compatible JDK and allow Gradle to sync dependencies.
4. Provide your **own** API credentials, service configuration, and signing materials where required. Do not commit secrets.
5. Build a Debug APK:

```bash
./gradlew :TMessagesProj_App:assembleDebug
```

For a release build, configure your own signing credentials and use:

```bash
./gradlew :TMessagesProj_App:assembleRelease
```

See [`mango_build_readme.txt`](mango_build_readme.txt) for additional project-specific notes. The public source has placeholder Google API keys, so integrations that rely on those keys may not function until configured. These commands are provided as build entry points, **not as a guarantee of reproducible or successful builds on every environment**.

### 🔏 Privacy & Security

Some enhancements are designed to run locally on the device. Source availability makes the code reviewable, but is not a substitute for an independent security audit.

Telegram messaging still depends on Telegram's services and the network route or proxy you choose. Features that access external services may make additional network requests. Never include personal data, tokens, signing keys, or authentication credentials in public issues or commits.

### 📜 Licensing & Attribution

This project is based on [Telegram for Android](https://github.com/DrKLO/Telegram). The repository retains upstream and third-party notices and license files. The repository root includes [`LICENSE`](LICENSE) (GNU GPL v2); individual dependencies may be governed by their own licenses.

When modifying or redistributing the project, follow all applicable license obligations and preserve required attributions. This independent project is not endorsed by Telegram.

### 🤝 Contributing & Community

Bug reports, improvement suggestions, and pull requests are welcome:

- 📢 **Official channel:** [Mango Client](https://t.me/hhhhp)
- 🐛 **Suggestions & Bug Reports:** [@zsq888](https://t.me/zsq888)
- 💬 **Discussion group:** [Telegram community](https://t.me/AKUOD)
- [GitHub Issues](https://github.com/aa228058/boshixuan/issues)
- [Pull Requests](https://github.com/aa228058/boshixuan/pulls)

Please include a clear reproduction procedure and redact any private data before sharing logs.

---

<div align="center">

**🥭 Mango Client · Open Source · Built for a better Telegram experience**

</div>
