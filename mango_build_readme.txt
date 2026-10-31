博士轩客户端 4.9（7009）源码包
导出时间：2026-10-31 13:53:28（本机时间）

本包直接导出当前工作目录，包含当前已修改及尚未提交的源码。

一、导入项目
1. 将整个压缩包解压到一个可写目录。
2. 使用新版 Android Studio，选择 Open，打开包含 settings.gradle 的项目根目录。
3. Gradle JDK 选择 JDK 17 或更高版本兼容的 JDK，推荐使用 Android Studio 内置 JBR。
4. 在 SDK Manager 安装 Android SDK Platform 35、Build Tools 35.0.0、NDK 21.4.7075529、CMake 3.10.2。
5. 由 Android Studio 配置本机 SDK 路径并生成 local.properties，等待 Gradle Sync 完成。
   首次同步和构建需要联网下载 Gradle 与 Maven 依赖。

二、构建
项目附带 Gradle Wrapper 8.13，使用 Android Gradle Plugin 8.13.2。
Android Studio 的 Build Variants 中，应用模块选择 TMessagesProj_App，变体选择 afatDebug 或 afatRelease，再构建 APK。
macOS/Linux 也可在项目根目录运行：
  chmod +x gradlew
  ./gradlew :TMessagesProj_App:assembleAfatDebug --console=plain
发布构建任务：
  ./gradlew :TMessagesProj_App:assembleAfatRelease --console=plain
Windows 可直接使用 Android Studio 的 Gradle 面板执行上述 assemble 任务。

三、包内容
保留三个 Gradle 模块、Java/JNI 源码、界面资源、工具脚本、文档、Gradle Wrapper、项目配置及构建必需的第三方依赖。
保留现有 google-services.json、gradle.properties、签名配置和对应 keystore，以匹配项目当前构建配置。
libs 下的 AAR、JNI 目录内的静态库属于构建输入，已保留。
不包含 APK/AAB、Git 历史、IDE 配置、Gradle/NDK 构建缓存、各模块 build 目录及本机 local.properties。

四、本次检查范围
检查压缩包完整性、关键构建文件与当前工作区源码一致性。本次只生成源码压缩包，不执行 APK 构建。
