# PeakMusic

Android 音乐播放器，支持在线音乐、本地媒体库、后台播放与同步歌词。

## 当前能力

- 在线/本地音乐列表、搜索、收藏、最近播放和自定义歌单。
- 前台播放服务、MediaSession、系统媒体通知、音频焦点和耳机拔出暂停。
- 顺序播放、列表循环、单曲循环、随机播放、睡眠定时及系统均衡器入口。
- 播放队列、当前曲目和进度恢复。
- 胶片播放页展示歌曲、歌手、封面和同步歌词；歌词支持自动居中、高亮及时间偏移。
- 首页侧边栏可修改、恢复及测试 API 地址。
- Debug 构建会打印格式化 JSON；超长响应完整保存在应用缓存，可在“API 响应日志”中查看、复制或分享。

## 构建要求

- JDK 17
- Android SDK 37
- Android Gradle Plugin 9.4.0
- Gradle 9.7.1（使用仓库内 Wrapper）
- 最低 Android 5.0（API 21）

完整验证命令：

```bash
./gradlew clean assembleDebug assembleRelease lintDebug testDebugUnitTest
```

产物：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`

## API 约定

默认地址定义在 `ApiUrl.BASE_URL`，运行时可由用户修改。当前客户端只依赖：

```text
GET music/getMusicList?search=...
```

音乐数据沿用现有字段：`id`、`name`、`singer`、`img`、`lrc`、`mp3`。相对媒体地址按当前 API 地址解析。为兼容用户配置的局域网 HTTP 媒体服务，应用明确保留 HTTP 支持；生产环境建议使用 HTTPS。

## 外部依赖与发布说明

- Release 构建目前是未签名 APK；正式发布签名及密钥不存放在仓库中，需要发布方提供。
- 未接入崩溃监控或统计 SDK，也没有填写监控密钥。后续接入需要明确选型、隐私策略和有效密钥。
- 不假设服务端存在歌词以外的新字段或接口；需要服务端能力的功能应先确定接口契约。
