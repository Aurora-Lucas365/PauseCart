# 缓购 PauseCart 0.2.0-beta.1

本次重做提醒、清单与设置，修复模式显示滞后和提醒静默限制。

## 变化

- 设置跟随状态立即更新，取消依赖定时刷新的模式圆点。
- 同次访问提醒一次；离开同平台满 30 秒后再次进入可重新提醒。
- 15 分钟放行只由用户主动选择，按平台独立显示和结束。
- 切换模式、暂停或结束放行时清理旧任务与相关状态。
- 增加授权、服务连接、窗口检查和最近目标 App 检测结果。
- 统一三页界面、深浅色、图标与底部提醒卡；精简项目说明。

## 安装

Android 8.0+；ARM64 / ARMv7 / x86 / x86_64；不依赖 Google 服务。使用正式签名 APK 覆盖升级，保留本机清单，不要先卸载。

自动提醒需要系统无障碍服务，到期通知单独选择。荣耀若提示受限制设置，先在应用信息页按系统提供的方式解除，再返回开启。无需 Root、开发者模式、Shizuku 或录屏。

## 本次测试范围

已通过 10 项规则测试、Android 8 / 13 / 15 / 16 模拟器各 4 项流程，以及 lint 与正式签名检查。具体范围见 [验证记录](https://github.com/Aurora-Lucas365/PauseCart/blob/main/docs/VALIDATION.md)。真实三平台与荣耀 400 新版仍需手机复测。本机窗口检查不能替代真实商家检测；下单前识别保留试用身份。

当前为待手机复测的预发布草稿，不作为正式稳定版。

[使用帮助](https://github.com/Aurora-Lucas365/PauseCart/blob/main/docs/GUIDE.md) · [兼容记录](https://github.com/Aurora-Lucas365/PauseCart/blob/main/docs/COMPATIBILITY.md) · [隐私](https://github.com/Aurora-Lucas365/PauseCart/blob/main/PRIVACY.md)
