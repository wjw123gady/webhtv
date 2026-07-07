# mpv 播放器项目仓库参考价值梳理

记录时间：2026-07-07  
适用分支：`feature/android-mpv-player`  
关联文档：

- `plans/安卓MPV播放器最佳实践与专项优化路线.md`
- `plans/安卓MPV相对Exo功能缺口TODO.md`
- `plans/安卓MPV播放器集成实现与踩坑记录.md`
- `plans/安卓libmpv二次开发指南.md`

## 结论摘要

这份 GitHub 搜索结果里只有一部分是真正的 MPV/libmpv Android 项目。大量仓库里的 `MPV` 实际指 Android `MVP` 架构样例，或者只是低活跃 fork/备份，不能作为播放器集成参考。

后续做 MPV 播放器能力补齐时，优先级应按下面顺序判断：

1. 先看本项目 Exo 成熟实现，明确 UI 契约、生命周期、错误文案、headers/输入层边界。
2. 再看 `mpv-android/mpv-android` 和本项目当前 libmpv 封装，确认官方/同源做法。
3. 按具体问题查 A 类主参考仓库。
4. 对 Native 构建、shader、服务端媒体流、TV 操控等专题，再查 B 类仓库。
5. 任何 GPL/AGPL 仓库先作为行为和设计参考，不直接复制代码，除非确认许可证兼容。

## A 类：持续重点参考

这些仓库与本项目当前 Android/libmpv 集成直接相关，后续遇到同类问题应优先阅读。

| 仓库 | 参考价值 | 适用场景 |
| --- | --- | --- |
| `mpv-android/mpv-android` | 官方 Android libmpv 播放器。Surface、初始化 options、属性观察、轨道、字幕、章节、stats、native build 的第一参考。 | 任何 libmpv 基础行为、Surface/VO、官方默认参数、mpv Android 限制。 |
| `FongMi/mpv-android` | 与官方 mpv-android 同源，适合对照 FongMi/WebHome 生态里可复用的 MPV 行为。 | 需要确认 FongMi 系列项目对 mpv-android 的改法。 |
| `sbenmeddour/libmpv-android` | 独立 AAR/封装库。JNI 已把 `END_FILE` 的 `reason/error` 封成结构化事件，也有 Kotlin track model。 | 补本项目 JNI 结构化事件、`END_FILE` 错误映射、track-list 解析。 |
| `sfsakhawat999/mpvRex` | 现代 Android MPV 播放器，覆盖智能轨道、字幕、shader、截图、逐帧、网络流等。 | 轨道/字幕体验、Anime4K、MPV 高级控制面板。 |
| `busydoggo/mPlayer` | mpvRex 相关分支，功能面板和高级播放控制完整。 | 字幕面板、延迟、zoom/pan、shader UI、逐帧截图。 |
| `marlboro-advance/mpvEx` / `abdallahmehiz/mpvKt` | Android mpv 播放器主线/祖先项目，功能成熟但部分仓库归档或衍生。 | 对照移动端交互、播放设置、历史兼容逻辑。 |
| `MakD/AFinity` | Jellyfin 客户端，LibMPV + ExoPlayer。把 MPV `track-list` 映射为 Media3 `Tracks`，`aid/sid/vid` 映射清晰。 | 实现本项目 `MpvPlayerEngine.getCurrentTracks()`、`setTrack()`、Media3 Player 契约。 |
| `raulshma/jellyplay` | 多播放引擎文档和 Jellyfin server profile 处理较完整，明确 Exo/libmpv/LibVLC 边界。 | 服务端媒体流索引与 MPV track id 不一致、Direct Play/Transcode 能力声明。 |
| `kernoeb/telegramedia` | 本地 HTTP/1.1 server 给 mpv 播放，支持 `Range`、`206`、`Content-Range`、渐进下载。 | HLS/本地代理/网盘临时文件/Range 支持。 |
| `Laskco/mpvNova` | Android TV-first mpv-android fork。遥控器 UI、decoder 面板、双字幕、字幕记忆、`hwdec-current` 诊断完善。 | TV 端操控、硬解/软解手动切换、双字幕、decoder 可视化。 |
| `azxcvn/mpv-android-anime4k` | mpv-android + Anime4K/HLS 参数实践。 | `glsl-shaders`、Anime4K 资产组织、`http-header-fields`、`hls-bitrate=max`。 |

## B 类：专题参考

这些仓库不是主实现参考，但在特定方向有价值。

| 仓库 | 参考价值 | 注意点 |
| --- | --- | --- |
| `XBigTK13X/expo-libmpv` | React/Expo 原生组件，但 Android 层有 `LibmpvSession`，把 seek/音轨/字幕选择延迟到 `FILE_LOADED/PLAYBACK_RESTART` 后应用。 | 架构不同，不能按它的 track index `+1` 做法照搬；可参考“deferred intent”思路。 |
| `nitanmarcel/mpv-compose` | Compose/Kotlin 封装样例，JNI event 仍主要上报 event id。 | 可参考包装形态，不足以解决本项目 `END_FILE reason/error`。 |
| `silenium-dev/mpv-kt` / `silenium-dev/compose-av` | KMP/Compose API 抽象思路。 | Android 支持有限或早期阶段，不能替代当前集成。 |
| `dcs-studio/mpv-build-android` | mpv/FFmpeg/libass/libplacebo 等 Android 构建脚本。 | 适合后续建立可维护 native 产物来源，不是 App 层播放 bug 的直接答案。 |
| `FortunasXP/mpv-android-libdovi` | libdovi + libplacebo + FFmpeg 构建，用于 Dolby Vision profile 5/7/8.x。 | 后续 HDR/Dolby Vision 专项再看，当前 P0 不应扩大到 native codec 包。 |
| `LinDevHard/sakuro` | Android/KMP 视频播放器与 Anime4K/Media3 engine 抽象。 | 更偏架构/效果链参考，不是本项目 libmpv P0 修复来源。 |
| `yuroyami/syncplay-mobile` | 同步播放/多人观影。 | 只有做同步播放功能时再研究。 |
| `lkcv/jellyfin-android-with-mpv` / `Shuejx/jellyfin-mpv-client` | Jellyfin + MPV 方向。 | 需逐个确认代码活跃度和实现完整性。 |
| `pctechkid/stream-resolver` / `pctechkid/Nelflix` / `mr-bipolar/max-player` | 流地址解析、播放器输入层可能有零散参考。 | 先按问题检索具体文件，避免被低活跃代码带偏。 |
| `nandieling/OmniPlay` | 用户提供项目，可参考运行期 property/command 调用和生命周期组织。 | Android libmpv 直接实现价值中等。 |
| `AkimioJR/MediaWarp` | 用户提供项目，更偏媒体工作流/跨播放器。 | 不能作为 MPV 核心播放 bug 的主要参考。 |
| `chenweiliang6/William-Player` | 用户提供项目，桌面/Electron/Web MPV 思路。 | Android Surface/JNI 直接价值有限。 |

## C 类：低价值或噪音

这些仓库不建议作为本项目 MPV 集成参考。

1. 名字里的 `MPV` 实际是 `MVP` 架构样例：
   - `Tyrbok/Android-Starter-Kotlin`
   - `dononcharles/MPVwithDagger2`
   - `husnulf/KotlinMPV`
   - `husnulf/CRUDKotlinWithMPV`
   - `armcha/MPV_architecture_adaptation`
   - `mahmoudahmedabdelrazek/Android-Architecture-Patterns---MPV`
   - `zackdu35/android-mpv-dagger-structure`
   - `luizfcdso/ArquiteturaMVP`
2. 低活跃/备份/重复 fork，除非某个 issue 明确指向它：
   - `Tuding071/*`
   - `tapman104/*`
   - `keepo/mpv1`
   - `estiaksoyeb/mpvSuper`
   - `Clouddark75/mpvRx`
   - `umeshwayakole27/mpvDroid`
   - `mindcreative134-creator/mpvEx-master`
   - `1497105876/mpvExgwxia78gai`
3. 桌面/遥控/外部控制类，不解决 Android 内嵌 libmpv 问题：
   - `djshaji/mpv-over-ssh`
   - `uBiWca/MPVremote`
   - `yerdonsu/mpv-gesture-remote`
   - `MagmaSKV/CastToMPV-Android`
4. 领域不匹配：
   - `SkyD666/PodAura` 是播客/音频场景，只有做后台音频、MediaSession、播客列表时才可能参考。
   - `compose-player/mpv-build-apple` 是 Apple native 构建方向，不适合当前 Android App 层问题。

## 功能缺口到参考项目的对照

| 本项目待补能力 | 优先参考 |
| --- | --- |
| `END_FILE reason/error`、错误分类、避免连接超时误判 | `sbenmeddour/libmpv-android`、mpv `client.h`、本项目 Exo 错误映射 |
| `track-list` -> Media3 `Tracks`、`setTrack()` | `MakD/AFinity`、`sbenmeddour/libmpv-android`、`Laskco/mpvNova` |
| 服务端媒体流索引与播放器轨道 id 不一致 | `raulshma/jellyplay`、Jellyfin/Findroid/Streamyfin 相关实现 |
| HLS/网盘/临时文件 Range、本地代理 | `kernoeb/telegramedia`、本项目 Exo `DataSource`、mpvRex/mPlayer |
| Surface attach/detach、黑屏排查 | `mpv-android/mpv-android`、`XBigTK13X/expo-libmpv`、`sbenmeddour/libmpv-android` |
| 字幕延迟、音频延迟、第二字幕 | `Laskco/mpvNova`、`mpv-android/mpv-android`、mpv 官方 options |
| 硬解/软解、decoder 诊断 | `Laskco/mpvNova`、mpv-android issues、MediaKit Android issues |
| Anime4K/shader/HDR | `azxcvn/mpv-android-anime4k`、mpvRex、mPlayer、mpv 官方 `glsl-shaders` |
| native 构建可维护性、codec 包 | `mpv-android/mpv-android` buildscripts、`dcs-studio/mpv-build-android`、`FortunasXP/mpv-android-libdovi` |

## 后续使用规则

- 遇到 MPV 播放问题，先在 Exo 成熟实现里找同类输入层/生命周期/错误处理，再回到 MPV 项目确认对应 option/property/command。
- 修改代码前，在本文件或 `安卓MPV播放器最佳实践与专项优化路线.md` 找对应参考项目，不凭印象改。
- 修复后必须把踩坑点写入 `安卓MPV播放器集成实现与踩坑记录.md`，避免再次把同类问题误判成“连接超时”。
- 每次代码或文档修改后提交并打 tag，方便回滚。
- 开源仓库用于学习实现方式和验证思路；直接复制代码前必须确认许可证兼容。

## 原始完整列表

已把 GitHub 搜索 (q=mpv language:Kotlin) 返回的全部 117 条仓库结果枚举出来，下面逐条列出（owner/repo）——这是完整的 1..117 列表：

1. mpv-android/mpv-android  
2. sfsakhawat999/mpvRex  
3. MakD/AFinity  
4. sbenmeddour/libmpv-android  
5. silenium-dev/mpv-kt  
6. XBigTK13X/expo-libmpv  
7. JeelPatel231/CoMPVose  
8. silenium-dev/compose-av  
9. Vulcankta/SimpleMPV  
10. nguyenvanvutlv/mpv-android  
11. FortunasXP/cyfer-streaming-android  
12. kernoeb/telegramedia  
13. LinDevHard/sakuro  
14. Rene-Kuhm/DespegueTv  
15. marlboro-advance/mpvEx  
16. abdallahmehiz/mpvKt  
17. azxcvn/mpv-android-anime4k  
18. Laskco/mpvNova  
19. azxcvn/mpvEx-CN  
20. mpv-android-vr/mpv-android-vr  
21. aelrased/NuvioDesktop-unofficial  
22. nitanmarcel/mpv-compose  
23. XIONGPEILIN/mpvExtended-android  
24. DevSon1024/Nosved-Player  
25. SkyD666/PodAura  
26. bluelul/RTSP_Camera_Stream  
27. yuroyami/syncplay-mobile  
28. allentown521/mpv-video  
29. FortunasXP/mpv-android-libdovi  
30. dcs-studio/mpv-build-android  
31. keepo/mpv1  
32. estiaksoyeb/mpvSuper  
33. Clouddark75/mpvRx  
34. umeshwayakole27/mpvDroid  
35. Tuding071/MpvTD  
36. Tuding071/MpvRF  
37. WindustH/mpvDanmaku  
38. gulshanz/MPVAndroidLearning  
39. Tuding071/mpvKt  
40. cualquiercosa327/mpvEx  
41. Tyrbok/Android-Starter-Kotlin  
42. Kimdracula/MPV  
43. tungvhsp/mpv-android  
44. Tuding071/MpvEx-backup  
45. jiawei-paopao/mpv  
46. Tuding071/MpvRP2  
47. luoyincheng/mpv  
48. mindcreative134-creator/mpvEx-master  
49. dononcharles/MPVwithDagger2  
50. Tuding071/MpvEx-mod-attempt-  
51. 1497105876/mpvExgwxia78gai  
52. djshaji/mpv-over-ssh  
53. yurimachioni/mpvmovies  
54. lkcv/jellyfin-android-with-mpv  
55. gabrielefuoco/MPVUpscaled  
56. vdcoders/mpvkt  
57. uBiWca/MPVremote  
58. Tuding071/MPVrp  
59. yurimachioni/MPVSample  
60. dinhphucaz2005/mpvplus  
61. saltpi/viper-mpv-android  
62. Tuding071/mpvw  
63. b80279298-art/Mpvnovamobile  
64. notmarek/animu-android  
65. perpetus/stremio-android  
66. Shivam-stackdev/mpv-android  
67. vanshpanchal026/productive-mpv  
68. mosayeb-a/mpv-android  
69. sam-reza/mpv-kt  
70. tapman104/potato-mpv  
71. guyuuan/mpv_kmp  
72. MayaAzizah/android_mpv  
73. lanhnv0108/demoMPV  
74. FreeMobileOS/mpv-android  
75. husnulf/KotlinMPV  
76. Febryan1453/mpv-foody  
77. StevenTopp/mpv-android  
78. Anam38/android-MPV  
79. DStoikov1/mpv_player  
80. Ekskogen/PokeApp  
81. Tuding071/MPV-1  
82. LenaTopoleva/JpgToPngConverter  
83. Fritzent/StudyCaseRoom  
84. raulshma/jellyplay  
85. acr994/MPV.conf-Maker  
86. blued-gear/BlueBeats-Mpv  
87. ayooooo123/react-native-mpv  
88. bijaykumarpun/android-mpv-launchpad  
89. Mariocanedo/mvc_mpv_am  
90. husnulf/CRUDKotlinWithMPV  
91. pablo-pbc/SpyDrive-MPV  
92. tapman104/potato-mpv-jacked  
93. compose-player/mpv-build-apple  
94. sdk0001/Solenya-MPV-Engine  
95. armcha/MPV_architecture_adaptation  
96. yerdonsu/mpv-gesture-remote  
97. augustanational/mpv-android-youtube  
98. huunam118/SimpleMpvKotlin  
99. mr-bipolar/max-player  
100. Shuejx/jellyfin-mpv-client  
101. MagmaSKV/CastToMPV-Android  
102. mahmoudahmedabdelrazek/Android-Architecture-Patterns---MPV  
103. vrcr7/Individual7ModelMPV  
104. zackdu35/android-mpv-dagger-structure  
105. azxcvn/mpv-android-bytedance-DanmakuRenderEngine  
106. pctechkid/Nelflix  
107. tapman104/real-mpv-potato-player-by-tapman104  
108. eeriemyxi/cloudstream-tricks  
109. LenaTopoleva/Infocratia  
110. madless/KotlinTestApp  
111. luizfcdso/ArquiteturaMVP  
112. Alex-Wang08/TestBuddy  
113. The-JDdev/mlc-android  
114. JacobMayor007/ethereal_massage  
115. ju22dev/LetsWatchPlayer  
116. busydoggo/mPlayer  
117. pctechkid/stream-resolver
https://github.com/nandieling/OmniPlay
https://github.com/AkimioJR/MediaWarp
https://github.com/chenweiliang6/William-Player
