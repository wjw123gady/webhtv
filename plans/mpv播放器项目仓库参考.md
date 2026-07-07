# mpv 播放器项目仓库参考价值梳理

记录时间：2026-07-07  
适用分支：`feature/android-mpv-player`  
关联文档：

- `plans/安卓MPV播放器最佳实践与专项优化路线.md`
- `plans/安卓MPV相对Exo功能缺口TODO.md`
- `plans/安卓MPV播放器集成实现与踩坑记录.md`
- `plans/安卓libmpv二次开发指南.md`

## 结论摘要

这份文档只保留后续 MPV 二次开发真正值得查的项目。此前 GitHub 搜索里大量 `MPV` 实际是 Android `MVP` 架构样例、低活跃 fork、桌面/遥控工具或极简空壳，已经从参考列表中删减，避免后续误读。

后续做 MPV 播放器能力补齐时，按下面顺序查资料：

1. 先看本项目 Exo 成熟实现，明确 UI 契约、生命周期、错误文案、headers/输入层边界。
2. 再看 mpv 官方文档和 `mpv-android/mpv-android`，确认 option/property/command 的正确语义。
3. 按具体问题查 A 类主参考仓库。
4. Native 构建、Dolby Vision、TV 操控、跨平台组件这类专题再查 B 类仓库。
5. 任何 GPL/AGPL 仓库只作为行为和设计参考；直接复制代码前必须确认许可证兼容。

## A 类：主参考

这些项目与本项目 Android/libmpv 集成直接相关。遇到对应问题时优先阅读。

| 仓库 | 参考价值 | 适用场景 |
| --- | --- | --- |
| `mpv-android/mpv-android` | 官方 Android libmpv 播放器。Surface、初始化 options、属性观察、轨道、字幕、章节、stats、native build 的第一参考。 | 任何 libmpv 基础行为、Surface/VO、官方默认参数、mpv Android 限制。 |
| `FongMi/mpv-android` | 与官方 mpv-android 同源，适合对照 FongMi/WebHome 生态里的直接用法。 | 需要确认 FongMi 系列项目对 mpv-android 的改法。 |
| `sbenmeddour/libmpv-android` | 独立 AAR/JNI 封装。JNI 已把 `END_FILE` 的 `reason/error` 封成结构化事件，也有 Kotlin track model。 | 补本项目 JNI 结构化错误、`END_FILE` 映射、track-list 解析。 |
| `MakD/AFinity` | Jellyfin 客户端，LibMPV + ExoPlayer。把 MPV `track-list` 映射为 Media3 `Tracks`，并有 PlaybackInfo、streamUrl、播放会上报、字幕 URL 构造。 | `MpvPlayerEngine.getCurrentTracks()`、`setTrack()`、Jellyfin/服务端媒体映射、播放会上报。 |
| `raulshma/jellyplay` | 多引擎 Jellyfin 客户端，明确 Exo/libmpv/LibVLC 边界，DeviceProfile 和播放会上报比较完整。 | Direct Play/Transcode 能力声明、服务端媒体流索引与播放器轨道 id 不一致、PGS/字幕能力声明。 |
| `lkcv/jellyfin-android-with-mpv` | 官方 Jellyfin Android fork，MPV 实现 Media3 `Player` 接口并接入原 Exo 播放 UI。 | “如何最小改造 Exo 播放链路接入 MPV”的参考；注意它绕过标准 Media3 TrackSelection 的做法不能照搬。 |
| `kernoeb/telegramedia` | 本地 HTTP/1.1 server 给 mpv 播放，支持 `Range`、`206`、`Content-Range`、渐进下载。 | HLS/本地代理/网盘临时文件/Range 支持。 |
| `Laskco/mpvNova` | Android TV-first mpv-android fork。遥控器 UI、decoder 面板、双字幕、字幕记忆、`hwdec-current` 诊断完善。 | TV 端操控、硬解/软解手动切换、双字幕、decoder 可视化。 |
| `sfsakhawat999/mpvRex` / `busydoggo/mPlayer` | 现代 Android MPV 播放器，覆盖智能轨道、字幕、shader、截图、逐帧、网络流等。 | MPV 高级控制面板、字幕体验、shader/Anime4K、截图逐帧。 |
| `azxcvn/mpv-android-anime4k` | mpv-android + Anime4K/HLS 参数实践。 | `glsl-shaders`、Anime4K 资产组织、`http-header-fields`、`hls-bitrate=max`。 |

## B 类：专题参考

这些项目不作为日常主参考，只在对应专题需要时查。

| 仓库 | 参考价值 | 注意点 |
| --- | --- | --- |
| `XBigTK13X/expo-libmpv` | Android 层有 `LibmpvSession`，把 seek/音轨/字幕选择延迟到 `FILE_LOADED/PLAYBACK_RESTART` 后应用。 | React/Expo 架构不同，不能按它的 track index `+1` 做法照搬；只参考 deferred intent 思路。 |
| `nitanmarcel/mpv-compose` | Compose/Kotlin 组件样例，能参考 SurfaceView 与 player lifecycle 包装方式。 | JNI event 仍主要上报 event id，不足以解决本项目错误分类。 |
| `silenium-dev/mpv-kt` | KMP mpv wrapper，可参考 API 抽象。 | Android 支持和成熟度有限，不能替代当前 Java/JNI 集成。 |
| `dcs-studio/mpv-build-android` | mpv/FFmpeg/libass/libplacebo 等 Android 构建脚本。 | 适合后续建立可维护 native 产物来源，不是 App 层播放 bug 的直接答案。 |
| `FortunasXP/mpv-android-libdovi` | libdovi + libplacebo + FFmpeg 构建，用于 Dolby Vision profile 5/7/8.x。 | 后续 HDR/Dolby Vision 专项再看，当前 P0 不应扩大到 native codec 包。 |
| `LinDevHard/sakuro` | Android/KMP 视频播放器与 Anime4K/Media3 engine 抽象。 | 更偏架构/效果链参考，不是本项目 libmpv P0 修复来源。 |

## 不再作为参考的项目

以下项目不纳入后续 MPV 集成参考，除非某个具体 issue 明确指向它。

| 项目或类型 | 不跟进原因 |
| --- | --- |
| `Shuejx/jellyfin-mpv-client` | 实际代码接近极简 Android 空壳，没有可复用的 MPV/Jellyfin 播放链路。 |
| `silenium-dev/compose-av` | README 已说明被 `mpv-kt` 取代，且 Android 方向不成熟。 |
| `AkimioJR/MediaWarp` | 更偏媒体工作流/跨播放器，不解决本项目 Android 内嵌 libmpv 的核心问题。 |
| `chenweiliang6/William-Player` | 桌面/Electron/Web MPV 方向，Android Surface/JNI 直接价值低。 |
| `pctechkid/stream-resolver`、`pctechkid/Nelflix`、`mr-bipolar/max-player` | 未验证出可直接复用的 Android/libmpv 实现，暂不列入参考链路。 |
| `yuroyami/syncplay-mobile` | 同步播放不是当前 MPV 集成缺口；只有做多人同步播放时再查。 |
| `SkyD666/PodAura` | 播客/音频场景，只有做后台音频、MediaSession、播客列表时再查。 |
| `compose-player/mpv-build-apple` | Apple 平台 native 构建，不适合当前 Android App 层问题。 |
| `Tyrbok/Android-Starter-Kotlin`、`dononcharles/MPVwithDagger2`、`husnulf/KotlinMPV`、`husnulf/CRUDKotlinWithMPV` 等 | 这里的 `MPV` 实际是 `MVP` 架构样例，不是 mpv 播放器。 |
| `Tuding071/*`、`tapman104/*`、`keepo/mpv1`、`estiaksoyeb/mpvSuper`、`Clouddark75/mpvRx` 等低活跃 fork | 缺少独立实现价值，优先看上游或主线项目。 |
| `djshaji/mpv-over-ssh`、`uBiWca/MPVremote`、`yerdonsu/mpv-gesture-remote`、`MagmaSKV/CastToMPV-Android` | 桌面/遥控/外部控制类，不解决 Android 内嵌 libmpv 问题。 |

## 功能缺口到参考项目的对照

| 本项目待补能力 | 优先参考 |
| --- | --- |
| `END_FILE reason/error`、错误分类、避免连接超时误判 | `sbenmeddour/libmpv-android`、mpv `client.h`、本项目 Exo 错误映射 |
| `track-list` -> Media3 `Tracks`、`setTrack()` | `MakD/AFinity`、`sbenmeddour/libmpv-android`、`Laskco/mpvNova` |
| 服务端媒体流索引与播放器轨道 id 不一致 | `MakD/AFinity`、`raulshma/jellyplay`、`lkcv/jellyfin-android-with-mpv` |
| Jellyfin PlaybackInfo、streamUrl、播放会上报 | `MakD/AFinity`、`raulshma/jellyplay`、`lkcv/jellyfin-android-with-mpv` |
| 外挂字幕 URL、外部字幕和 MPV `sid` 映射 | `MakD/AFinity`、`lkcv/jellyfin-android-with-mpv`、`Laskco/mpvNova` |
| HLS/网盘/临时文件 Range、本地代理 | `kernoeb/telegramedia`、本项目 Exo `DataSource`、mpvRex/mPlayer |
| Surface attach/detach、黑屏排查 | `mpv-android/mpv-android`、`XBigTK13X/expo-libmpv`、`sbenmeddour/libmpv-android` |
| 字幕延迟、音频延迟、第二字幕 | `Laskco/mpvNova`、`mpv-android/mpv-android`、mpv 官方 options |
| 硬解/软解、decoder 诊断 | `Laskco/mpvNova`、mpv-android issues、MediaKit Android issues |
| Anime4K/shader/HDR | `azxcvn/mpv-android-anime4k`、mpvRex、mPlayer、mpv 官方 `glsl-shaders` |
| native 构建可维护性、codec 包 | `mpv-android/mpv-android` buildscripts、`dcs-studio/mpv-build-android`、`FortunasXP/mpv-android-libdovi` |

## 使用规则

- 遇到 MPV 播放问题，先在 Exo 成熟实现里找同类输入层/生命周期/错误处理，再回到 MPV 项目确认 option/property/command。
- 修改代码前，在本文件或 `安卓MPV播放器最佳实践与专项优化路线.md` 找对应参考项目，不凭印象改。
- 对 `lkcv/jellyfin-android-with-mpv` 这类“能跑但绕开标准契约”的实现，只借鉴问题分解，不照搬架构捷径。
- 修复后必须把踩坑点写入 `安卓MPV播放器集成实现与踩坑记录.md`，避免再次把同类问题误判成“连接超时”。
- 每次代码或文档修改后提交并打 tag，方便回滚。
- 开源仓库用于学习实现方式和验证思路；直接复制代码前必须确认许可证兼容。
