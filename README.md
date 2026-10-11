# ADSkip

已产出可安装的 Android 初版 **0.2.2**：[`ADSkip-0.2.2.apk`](dist/ADSkip-0.2.2.apk)。原生全宽横屏广告先根据无障碍“广告”标记尝试上滑一次，未退出再使用单独开启的横屏辅助识别。本版修复真机横屏黑边导致固定裁剪截断提示的问题，优先通过无障碍容器边界定位；首次安装的自动跳过和辅助识别默认关闭，可覆盖升级旧版本。

保留无障碍引导、8 / 12 / 20 秒冷却、完成上滑计数、本地操作记录和通知栏暂停。剧集之间的直播卡片继续 pending。安装与构建见 [app/README.md](app/README.md)，本轮见[一次试滑与回退验收](artifacts/hongguo-landscape-badge/TEST.md)。历史验收见[0.2.0 横屏 OCR](artifacts/hongguo-landscape/TEST.md)、[0.1.0](artifacts/app-v0.1/TEST.md)、[0.1.1](artifacts/app-v0.1.1/TEST.md)。

此前完成 5 种原生横屏广告的无障碍复查，518 个连续节点样本没有提供可读继续观看提示；轻量 OCR 模型漏检两种素材，未替换产品模型。详见[复查报告](artifacts/hongguo-landscape-a11y/REPORT.md)。0.2.1 补齐真实横屏倒计时，验证它会阻止首次试滑；采用一次试滑与回退的流程，并收窄 OCR 裁剪以排除优惠券数字干扰，截图回放单次识别中位约 53 ms（模拟器，不含截图和确认等待）。

## 模拟器实测记录

已在本机建立 Android 14 / API 34 ARM64 模拟器，安装用户提供的红果免费短剧 APK，并搭建离线无障碍探针。初轮结果见 [REPORT.md](artifacts/hongguo-verification/REPORT.md)，登录账号后的再次实测见 [RETEST.md](artifacts/hongguo-retest/RETEST.md)。

已验证界面文本读取、无障碍截图与上滑手势；再次实测采集到真实广告的底部倒计时和可跳过提示，修正原先只匹配顶部的规则，并实际完成 5 次广告自动跳过并恢复播放。这些是历史探针结果。0.2.0 应用新增原生全宽横屏 OCR，覆盖范围以本轮横屏验收为准。

## 启动与安装

在项目目录运行：

```sh
./scripts/start-emulator.sh
./scripts/adb.sh devices -l
./scripts/install-hongguo.sh /Users/junee/Downloads/base.apk.1.1
./scripts/adb.sh shell am start -n com.phoenix.read/com.dragon.read.pages.splash.SplashActivity
```

模拟器名称为 `ADPass_API_34`，ADB 序列号为 `emulator-5554`。已有该模拟器运行时，无需再次启动。配置为 Pixel 5、1080 × 2340、3 GB RAM、10 GB 数据盘，使用 macOS Hypervisor.Framework。

SDK、镜像、AVD、APK 和探针构建产物位于被 Git 忽略的 `.local/android/`；其中 emulator 和 platform-tools 链接到 `/Users/junee/Library/Android/sdk/`。镜像来自 Google，用户已同意许可，全文保存为 `ANDROID-IMAGE-LICENSE.txt`。

## 无障碍探针

源码在 `tools/android-probe/`，仅供这个测试模拟器使用。无需 Gradle 或联网构建；构建脚本使用本机 Android Studio 的 JDK、SDK build-tools 36.0.0 和 android-34 平台：

```sh
./tools/android-probe/build.sh
./scripts/adb.sh install -r .local/android/probe-build/probe.apk
./scripts/adb.sh shell settings put secure enabled_accessibility_services io.adpass.probe/io.adpass.probe.ProbeService
./scripts/adb.sh shell settings put secure accessibility_enabled 1
```

上述设置用于这个全新模拟器；不要复制到有其他无障碍服务的个人设备，会覆盖其服务列表。

探针没有网络权限，仅处理前台包名 `com.phoenix.read`，要求屏幕点亮且未锁屏。每轮读取后约 1 秒开始下一轮；自动规则默认关闭，重连也会恢复关闭。读取可见且处于应用窗口中的有效节点，不再固定顶部位置：

- `ad-waiting`：广告提示节点 `aq8` 含倒计时或即将进入广告，不触发手势。
- `ad-ready`：明确的“上滑继续观看短剧/剧集”提示，并有已验证的 `aq8` 节点或“广告”标记。
- `live-preview`：APK 布局中已确认的 `jjy` 控件显示“点击进入直播间”，或同时出现精确的“直播中/正在直播”和“进入直播/点击观看直播/去直播间抢购”控件文案。这是待实际直播样本确认的保守规则，不能保证覆盖所有直播卡片。
- `content`：普通内容或未识别的页面，保持观看。

可跳过状态在相同窗口连续两轮命中后，从当前应用窗口的横向中心、75% 高度滑到 25% 高度，持续 350 ms，冷却 8 秒。横屏和 Android 居中窄窗口均使用当前窗口坐标。检测候选和手势完成后保存截图。原生横屏普通播放器是下一集按钮，手动测试需使用该按钮；自动手势仅用于出现明确上滑提示或直播卡片特征的页面。该历史探针没有 OCR；0.2.0 应用的辅助识别在 `app/` 中。

用户已授权本轮规则实测。再次运行的操作命令：

```sh
# 开启 / 关闭自动规则
printf 'auto-on\n' | ./scripts/adb.sh shell run-as io.adpass.probe tee files/command.txt
printf 'auto-off\n' | ./scripts/adb.sh shell run-as io.adpass.probe tee files/command.txt
# 无障碍截图；手动上滑一次（会切换当前内容）
printf 'capture\n' | ./scripts/adb.sh shell run-as io.adpass.probe tee files/command.txt
printf 'swipe\n' | ./scripts/adb.sh shell run-as io.adpass.probe tee files/command.txt
# 读取最新节点与日志
./scripts/adb.sh shell run-as io.adpass.probe cat files/latest.json
./scripts/adb.sh shell run-as io.adpass.probe cat files/events.ndjson
./scripts/adb.sh shell run-as io.adpass.probe cat files/samples.ndjson
```

命令由下一轮读取，勿并发写入。`latest.json` 是最后一次红果前台观察，离开红果后不会更新，检查其时间戳。其中 `kind` 是页面分类、`window` 是当前应用窗口坐标、`visible` 是节点可见性。`samples.ndjson` 只保存变化、命中和每 15 轮一次的记录，不是全部采样；截图在探针内部 `files/`。

`scripts/capture-screen.sh 标签` 保存 ADB 截图；探针已启用时跳过 UIAutomator。实测发现 UIAutomator 会打断并重连无障碍服务，导致自动状态复位，不应在自动观察期间运行它。

实测收尾后自动规则与探针已关闭，模拟器保留。恢复关闭的命令：

```sh
printf 'auto-off\n' | ./scripts/adb.sh shell run-as io.adpass.probe tee files/command.txt
# 等待探针处理命令后，在这个专用模拟器禁用服务
./scripts/adb.sh shell settings put secure enabled_accessibility_services null
./scripts/adb.sh shell settings put secure accessibility_enabled 0
```

使用真实页面记录回放分类规则：

```sh
python3 tools/android-probe/tests/replay.py
```

回放验证普通剧集不会被当作广告、倒计时不会被当作可跳过，以及提示在竖屏底部与横屏移动后都可识别。它不代替实际自动手势或直播卡片验证。

上述探针仅供历史实测使用；可安装的初版应用在 `app/`，使用已验证的分类规则并补充设置界面、用户授权引导和暂停入口。探针与初版是不同包名，不要同时开启两者。未知广告和直播卡片仍需采集节点、截图后再增加规则。

0.2.2 真机反馈处理：用户录屏已复现旧版截断提示，修复后顺序回放可连续确认；模拟器真实 8 秒倒计时首次失败后再次上滑并返回剧集。真机最终效果仍需安装复测，压缩录屏部分帧保持不确定。详见[本次修复与验证](artifacts/hongguo-device-countdown/REPORT.md)。
