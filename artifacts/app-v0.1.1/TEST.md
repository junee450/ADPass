# ADSkip 0.1.1 更名与图标验收

时间：2026-10-10，Asia/Shanghai。测试环境：Android 14 ARM64 模拟器 emulator-5554。

应用显示名、首页标题、权限说明、无障碍服务名称与通知标题统一改为 ADSkip。新增紫色背景、白色播放/跳过图形及薄荷绿终点竖条的图标，同时提供自适应图标、Android 13 单色图标和单色通知小图标。

- 构建和 APK 签名验证通过，覆盖安装成功。版本号 0.1.1 / versionCode 2，包名保持 `io.adpass.app`；`apk-badging.txt`。
- 覆盖升级后正常启动：首页标题、版本标记和新图标显示正确，圆形自适应遮罩未裁切核心符号；`app.png`。
- Android 应用抽屉显示新图标和 ADSkip 标签，点击新图标可正常启动；`launcher.png`。
- 原有暂停状态、8 秒冷却和累计完成上滑数 2 均保留。更名后仍未开启自动跳过。
- 本轮 AndroidRuntime 检查未出现崩溃。本次只修改名称、图标和显示界面，广告分类与自动动作逻辑未改动；没有新增重复广告实测。

图标标准导出 `app/branding/adskip-icon.png`，生成来源与提示词见 `app/branding/DESIGN.md`。直播卡片与原生全宽横屏广告仍为 pending。
