# 红果模拟器实测记录

2026-10-09，Asia/Shanghai。本轮完成模拟器搭建及基础能力实测；目标广告尚未复现，自动跳过广告未通过端到端验证。

## 环境

| 项目 | 实测配置 |
| --- | --- |
| 模拟器 | `ADPass_API_34`，`emulator-5554` |
| 系统 | Android 14 / API 34，Google APIs ARM64 镜像 revision 14 |
| 设备 | Pixel 5，1080 × 2340，3 GB RAM |
| 硬件加速 | macOS Hypervisor.Framework |
| 红果 | `com.phoenix.read`，7.3.9.32 / versionCode 73932 |
| APK 来源 | 用户提供的 `/Users/junee/Downloads/base.apk.1.1` |
| 探针 | `io.adpass.probe`，离线、无网络权限，Android 无障碍服务 |

APK 签名校验成功，详情见 [apk-signature.txt](apk-signature.txt)；未与官方发行证书独立比对。应用参数见 [apk-badging.txt](apk-badging.txt)。用户已接受 Google 系统镜像许可，并授权 ADB 界面操作和本轮自动规则。

## 结果

| 验证项 | 结果 | 证据与边界 |
| --- | --- | --- |
| 安装、启动和播放 | 通过 | 目录加载；实际视频画面出现并变化。第二部剧从第 1 集播放至第 4 集。 |
| 无障碍节点读取 | 通过 | 读到剧名、集数、选集等节点；[final-observation.json](final-observation.json)。仅验证普通页面，未验证目标广告文案。 |
| 无障碍截图 | 通过 | `takeScreenshot` 成功；[accessibility-screenshot.png](accessibility-screenshot.png)。 |
| 无障碍上滑手势 | 通过 | `dispatchGesture` 返回 accepted=true 并回调完成；手动测试从第 36 集切到第 37 集，见 [before-gesture.json](before-gesture.json)、[after-gesture.json](after-gesture.json) 和 [after-accessibility-gesture.png](after-accessibility-gesture.png)。这次发生在普通播放页面。 |
| 自动规则启动与停止 | 通过 | 日志记录 22:39:37.783 开启，22:46:35.118 关闭，开启约 6 分 57 秒。 |
| 广告识别与自动跳过 | 未验证 | 保存的 94 条观察记录中匹配为 0；没有自动手势。未采集到目标广告页面。 |
| OCR 回退 | 未实现、未验证 | 尚无目标广告截图可用于判断是否必须 OCR。 |
| 真机兼容性、耗电、长期误触率 | 未验证 | 本轮仅为 ARM64 模拟器基础实测。 |

## 观察过程

- 第一部《被甩当天，我带崽闪婚豪门》用于读取、截图和一次手动上滑测试。探针日志从 22:36:42 开始；期间集数有快速变化，未确认原因，不能据此推断自动规则或完整播放行为。
- 第二部《换巢鸾凤5》于约 22:41:21 开始观察，至 22:46:36，共约 5 分 15 秒，看到第 1 至第 4 集。该段没有手动或自动上滑。
- 出现过金币提醒及“立即领取”按钮，未领取，未作为目标广告匹配。
- 完整导出的 [probe-events.ndjson](probe-events.ndjson) 仅有一次手动手势及其完成回调，没有 `ad-two-hits` 事件。[probe-samples.ndjson](probe-samples.ndjson) 保存变化、命中及每 15 轮一次的观察；94 条是留存记录数，不是全部轮数。服务曾重连，采样编号不能跨重连直接累加。

自动规则只处理红果前台、屏幕点亮且未锁屏的情况：顶部 30% 区域中坐标有效的节点包含“上滑继续观看”，连续两轮命中后上滑，冷却 8 秒。轮询间隔为每轮处理结束后约 1 秒。规则源代码在 [ProbeService.java](../../tools/android-probe/src/io/adpass/probe/ProbeService.java)。

本轮发现节点树包含前后集缓存的屏外节点，已使用顶部位置及有效矩形作为匹配约束。尚未验证这能覆盖真实广告布局，也未使用 OCR 对全部视频帧进行检查。因此“0 匹配”只能说明现有节点规则未命中，不能证明红果不会投放广告。

UIAutomator 曾打断无障碍服务并触发重连，使自动状态恢复关闭。之后自动观察使用探针节点和 ADB 截图；截图脚本现在会在探针启用时跳过 UIAutomator。

## 收尾与下一步

自动规则已关闭，无障碍服务已禁用（设置值 `null` / `0`）；红果停在第 4 集暂停画面，模拟器继续保留。见 [paused.png](paused.png)。复用方法见项目 [README.md](../../README.md)。

下一步需要实际出现“上滑继续观看”的广告页，保存节点和截图：若节点可读，直接验证连续命中、上滑及恢复播放；若节点不可读，再加入顶部截图 OCR 并验证其识别耗时与误触。现阶段不应宣称已经实现可靠跳广告。
