# 发布、签名与验证

## 构建默认值与密钥边界

普通 `./gradlew assembleDebug assembleRelease` 仍为 versionCode 1 / versionName 1.0：Debug 使用 Android 调试证书，Release 输出未签名 APK。没有正式发布凭据时不生成或冒充正式密钥；现有模拟器 Release 测试仍只给 APK **副本**签调试证书。

`gradle/release-config.gradle` 从环境读取下列输入，不从仓库文件、Gradle `-P` 参数或 BuildConfig 读取密码：

| 环境变量 | 约束/用途 |
| --- | --- |
| `RELEASE_VERSION_CODE` | 1～2,100,000,000 的整数，必须与版本名同时设置 |
| `RELEASE_VERSION_NAME` | 1～64 位 ASCII 标签；首位字母/数字，后续允许字母、数字、点、下划线、加号、短横线 |
| `ANDROID_RELEASE_KEYSTORE` | 可读密钥库的绝对路径，建议保存在仓库外 |
| `ANDROID_RELEASE_STORE_PASSWORD` | 密钥库密码；只注入当前构建环境 |
| `ANDROID_RELEASE_KEY_ALIAS` | 发布私钥别名 |
| `ANDROID_RELEASE_KEY_PASSWORD` | 私钥密码；PKCS12 常与密钥库密码相同 |
| `ANDROID_RELEASE_CERT_SHA256` | 打包时必须提供、从可信发布证书独立核对的 SHA-256 指纹；接受大小写及冒号分隔 |

- 版本字段缺一、非法版本、签名四项缺一、文件不存在或签名密码错误均失败，不悄悄降级成未签名发布包。显式签名还要求显式版本，避免无意签出默认 1.0。
- 仅设置合法版本对而不配置签名时可以构建指定版本的 unsigned APK；不满足已签名归档入口的条件。
- 签名配置不改 Debug signingConfig，但版本对属于 defaultConfig，因此同次构建的 Debug/Test 应用也使用该版本。专用验证脚本结束会重新构建默认版本产物，不安装到手机。
- 不将密码放入命令行、提交到 Git、上传 build scan 或启用签名任务的 configuration cache。发布入口使用单次 Gradle daemon 并关闭 configuration cache；CI 发布任务关闭 Gradle 缓存。不要在同一工作目录同时进行不同版本/密钥的构建。
- `.gitignore` 增加密钥库和 `.env` 规则只是防误提交，不会移除已经被 Git 跟踪的秘密；泄露的密钥需由持有方独立处理。脚本不查找、读取或替换已有个人发布证书。

## 本地生成可追踪归档

先在自己的终端或密钥管理器中注入上表环境变量，并设置 `ANDROID_HOME`（JDK 17、Python 3.9+、SDK 37/build-tools 37.0.0）。不要把真实密码粘贴到任务或命令历史。

```bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/package-release.py
```

入口依次运行 JVM 回归、Debug/Release Lint、实际 minified Release 构建，然后：

1. 从当前 AGP metadata 选择唯一、未拆分的 Release APK，检查项目包名及请求的版本号/版本名。
2. 要求 `apksigner verify` 成功，且已验证的唯一签名身份与期望证书一致；拒绝常规 `CN=Android Debug` 身份。兼容新旧 build-tools 的证书输出格式，不把“打印了证书”当作验签成功。
3. 使用 `aapt2 dump badging` 检查 APK **内部**实际包名、版本与非 debuggable 状态，不只相信输出文件名。
4. 校验 R8 mapping 正文的 `pg_map_hash`，再将 `pg_map_id` 与 APK DEX 内的 Release R8 marker 对应，拒绝其他构建、缺失、修改或截断的 mapping。
5. 创建 `app/build/release-archives/<包名>-<versionCode>-<APK哈希前12位>.zip`。同名归档已存在时拒绝覆盖；不删除旧正式归档。

ZIP 仅包含：

- `app.apk`：经验签、非 debuggable 的混淆 Release APK。
- `mapping.txt`：与该 APK DEX 对应的原始 R8 映射。
- `release-manifest.json`：包名、版本、签名证书 SHA-256、R8 map ID、各文件 SHA-256、Git revision、工作区是否脏、UTC 打包时间；不含密码、密钥库路径或私钥。
- `SHA256SUMS`：APK、mapping、manifest 的完整性校验值。它不替代 APK 签名，也不构成独立可信的发布认证。

归档只在本地/CI 保存，**没有上传应用商店、网站或自动发版**。允许本地脏工作区验证，但 manifest 明确标记 `workingTreeDirty`，不是可重建性保证；正式发布应先审查代码并在发布方自己的清晰 Git revision/tag 上构建。版本是否大于已经上线的版本须由发布方核对，仓库没有商店查询凭据或伪造版本状态。

## GitHub Actions

- `.github/workflows/android.yml`：push/PR/手动触发普通构建、测试、Lint、Python 打包策略测试，以及用临时证书的真实签名配置回归；不依赖发布秘密。
- 同工作流的 native job 在 API 33/36、x86_64 Google APIs 模拟器执行 `scripts/verify-native-ci.sh emulator-5580`，生成合成媒体并串行运行所有原生用例及真正混淆 Release 黑盒。仅上传日志/截图，不上传测试签名 APK、偏好快照或临时密钥。
- `.github/workflows/release.yml`：**仅手动**创建已验证归档；先调用完整验证 workflow（不传发布秘密），构建/策略/签名配置/两档原生矩阵全部成功后才进入签名 job。配置 `release` environment、必需审核人、受限发布分支/tag；版本输入通过环境传递，不拼进 shell 命令。
- 发布方在该 environment 配置 `RELEASE_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`、`RELEASE_CERT_SHA256`。Base64 是编码，不是加密；仅作为 GitHub 加密 Secret 保存。密钥解码到 runner 临时目录，权限 0600，最终步骤删除；不会放进工作区或上传 artifact。
- 已验证归档默认保留 90 天，普通报告 7 天，原生报告 14 天。正式 mapping 必须在到期前另存到发布方受限的长期版本档案，与 APK 哈希绑定；工作流未虚构外部存储或符号服务。
- 发布环境、Secret、分支保护及 Actions 执行由仓库持有方配置；本地脚本通过和 actionlint 通过不等于远端工作流已运行。

## 可复跑的发布配置回归

```bash
ANDROID_HOME=/path/to/sdk python3 scripts/verify-release-config.py
ANDROID_HOME=/path/to/sdk scripts/verify-native-ci.sh emulator-5580
```

前一脚本使用临时目录、2 天有效的**一次性测试证书**验证默认 unsigned、版本缺项/越界、签名缺项、指定版本真实签名及归档、错误指纹、错误 mapping、错误密码失败；不读取调用方传入的真实签名输入，临时证书结束删除。保留证据到 `app/build/verification/release-config/`，测试归档明确命名 `TEST-ONLY-release.zip`，不留在正式归档目录，结束恢复默认 Debug/Release/AndroidTest 产物。

后一脚本会重置应用测试数据、修改测试权限/偏好并播放合成媒体，只接受 API 33+ **可丢弃 emulator-* 设备**，不用于已有个人数据的设备。所有 adb 调用显式指定传入序列号；成功标记 `NATIVE_CI_OK`。云端 x86_64、真实耳机/后台/OEM/电视仍是独立验收，不由本机模拟器结果代替。
