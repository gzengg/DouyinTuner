# DouyinTuner（抖音精简助手）

LSPosed 模块，针对抖音 40.7.0（`com.ss.android.ugc.aweme`，versionCode 400701）做去广告 / 界面精简 /
停上报 / 无水印直链 / 防撤回等 19 项面板开关（18 个 hook + 1 个桌面图标开关）。

文档分工（重要）：`README.md` 是**对外的功能说明**，只写用户可见的行为与限制，**不写任何 hook 落点**
（类名/方法名/dex 名一律不出现在 README）；实现细节的权威来源是**代码本身**，加上 `_recon/` 里
几份实施前侦查报告。本文件是下次会话恢复上下文的边界，也不写具体落点。

## 构建与验证

```bash
export JAVA_HOME="E:\\JAVA\\20"
./gradlew :app:assembleRelease        # 产物 app/build/outputs/apk/release/app-release.apk
```

签名用 `E:/huaian.jks`（别名 `huaian`，凭据 `keystore.properties`，两者都在 .gitignore 里，不提交）。

改完 hook 目标必须跑回查脚本（只读 dex 头，秒级）：

```bash
py E:/DY_cj/_recon/verify_targets.py   # 退出 0 = 全部命中（当前 缺失 0 项）
```

真机验证清单在 `README.md`「真机验证清单」，日志走 `adb logcat -s DouyinTuner`（默认只打
加载行、已挂钩摘要行、跳过/异常行）。

## 技术栈

Java 17 / compileSdk 35 / minSdk 24；依赖只有 `compileOnly de.robv.android.xposed:api:82`，
不引第三方库。UI 全用代码构建（没有 layout XML）。

## 目录

- `app/src/main/java/com/mod/douyintuner/`：`XposedHook` 是入口，按功能一个 `*Hooks.java`；
  `Config` 存类名/开关 key/面板开关表，`H` 是 hook 辅助，`Log` 是两极日志。
- `E:/DY_cj/_recon/`：**仓库外的侦查现场**，也是实现细节的唯一书面来源。`dex/`（55 个 dex，438MB）
  是回查脚本的数据源，`*.md` 是各项功能的侦查报告（含落点、AB key、当时的未解决项），
  `verify_targets.py`/`q.py`/`methods.py` 等是只读定位工具。不要把它复制进仓库，也不要提交。
- `E:/DY_cj/*.apk`：被分析的抖音原始 APK，改 hook 前先在它身上确认类/方法存在。

## 约定

- 新 hook 一律先 `H.cls(...)`/`H.hook...` 做存在性判断，缺项只记一条 `跳过(...)` 日志，不让模块变成崩溃源；
  写死类名后同步加进 `verify_targets.py` 的 `TARGETS`。
- 日志分两级：`Log.k` 关键（加载、摘要、跳过/异常、少数用户可见结果）默认输出；`Log.i` 细节默认丢弃，
  排查时才把 `Log.VERBOSE` 改 true。不要用 `Log.k` 打逐事件日志。
- 每次只编译本次改动涉及的目标，不做全量构建；真机验证交用户在真机执行，不要自行起模拟器。
- README 的「功能」表 = 面板开关 → 用户可见作用；只改用户可见行为/限制时同步改 README，
  新增或改动 hook 落点时**不要**往 README 里写类名、方法名、dex 名、接口路径。

## 当前状态与下一步（2026-10-11）

- 无远端，只在 40.7.0 上核对过；最新提交是日志两级化（`Log.k`/`Log.i`）。
- 面板开关 → hook 落点的映射看代码（`Config.SWITCHES` + 各 `*Hooks.java`）。模块只在真机上验证，不要在模拟器里试。
- 尚未做真机确认：冷启动加速的实际首帧收益（README「冷启动加速做了什么」）。
