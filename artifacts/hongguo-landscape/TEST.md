# ADSkip 0.2.0 原生全宽横屏验收

日期：2026-10-11。Android 14 / API 34 ARM64 模拟器 emulator-5554，红果 7.3.9.32，用户登录的测试账号。用户明确授权使用 ADB 读取、截图、点击、上滑及自动广告规则；本轮用户指出“现在就出现了”，直接保留并测试该广告。

## 真实布局与动作

原生播放器为 `com.dragon.read.component.shortvideo.impl.fullscreen.ShortSeriesLandActivity`，应用窗口 `[0,0,2340,1080]`，宽度大于高度。截图 [`ad-now.png`](ad-now.png) / [`before-app-test.png`](before-app-test.png) 显示：右上方“上滑继续观看剧集”，精确“广告”标记，居中商品视频及右侧优惠卡片。

[`ad-now.json`](ad-now.json)、[`ad-inclusive.json`](ad-inclusive.json) 确认广告标记可读，而跳过提示不暴露文字节点；尝试包含非重要节点后依然没有提示。产品因此新增可选的本机 OCR，不根据普通横屏的“下一集”按钮决定自动操作。

启用新版实际 `io.adpass.app/.SkipService`，关闭探针服务；开启“横屏辅助识别”，返回当前广告。服务完成两次确认后的 350 ms 上滑，画面恢复正常横屏剧集。测试没有发送 ADB 广告跳过手势，ADB 点击只用于设置、返回红果和事后显示控制栏。

证据：

- [`ocr-consent.png`](ocr-consent.png)：辅助识别说明与单独确认。
- [`app-ready.png`](app-ready.png)：测试前累计 3、今日 0。
- [`returned-ad.png`](returned-ad.png)、[`after-app-test.png`](after-app-test.png)：返回后已恢复剧集，连续画面在变化。
- [`app-after-skip.png`](app-after-skip.png)：累计 4、今日 1，恰好增加一次。
- [`operation-log.png`](operation-log.png)：14:49:31 “已完成横屏广告上滑”；14:50:56 “自动跳过已暂停”。
- [`app-new-settings.png`](app-new-settings.png)：新功能、Android 11 要求及直播 pending 的实际界面。

此广告视频内出现“点击进入直播间”，但它有广告标记和继续观看提示。本轮验证的是广告跳过，未据此放开剧集之间插入直播卡片的规则。

## 回放和约束

`./app/tests/run.sh` 通过 25 项动作门控、22 项视觉提示与倒计时检查、39 个历史页面回放。普通内容、其他应用、锁屏、关屏、重叠手势、倒计时、模糊 OCR、嵌入式文案都不能授权动作；不同窗口/方向/广告内容重新确认。

实际 Android OCR 引擎截图回放（与产品共用源码，裁剪右上方 x=50%–95%、y=0%–23%）：

| 真实截图 | 结果 | 解释 |
| --- | --- | --- |
| ad-now.png | ad-ready；提示最低词置信度 87 | 完整“上滑继续观看剧集”，忽略低置信度且高度明显小于文字的箭头 |
| fullscreen.png | content | 正常横屏内容不授权上滑 |
| next-title.png | ad-waiting | 普通控制栏中的数字造成保守等待，不授权上滑 |

原始 OCR 先检查数字、倒计时等信号，避免过滤箭头时误丢倒计时。允许跳过的文字必须整行精确匹配，词置信度至少 80。截图处理全在独立工作线程，动作前再次确认红果前台、广告节点、相同窗口和内容、屏幕与锁定状态；超过 2 秒的结果丢弃，暂停和配置变更使在途结果失效。

没有网络权限。截图与识别文字不落盘；这里只保存开发者通过 ADB 取得的验收材料。第三方库许可证已随 APK 打包。

## 尚未覆盖

本轮验证了一种真实原生全宽横屏上滑广告及两种正常横屏截图。尚无真实原生横屏倒计时样本、其他提示位置/颜色、点击式跳过和真机系统差异样本。无法确认的页面保持等待。直播插入卡片仍 pending。

实测后自动跳过已暂停，辅助识别配置保留。最终 0.2.0 包保留原包名和签名，可覆盖升级；最终大小、SHA-256 和签名检查见 [`PACKAGE.txt`](PACKAGE.txt)。
