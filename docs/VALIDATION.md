# 验证记录 · 0.1.0-beta.1

日期：2026-10-19。应用代码与签名构建对应提交：`d287884aac18f2c5837ea81033b53f6d4f0cf0b4`。后续文档、截图与发布元数据提交不改变应用代码。

## 已验证完成

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| JVM 规则测试 | 7 项通过，0 失败 | [构建与检查](https://github.com/Aurora-Lucas365/PauseCart/actions/runs/39220109508) |
| Android lint | 0 错误；仍有依赖更新、目标 SDK 与工具链迁移建议等警告 | 同上 check-reports |
| Android 8.0 / API 26 模拟器 | 2 项流程测试通过 | 同上 emulator-api-26 |
| Android 15 / API 35 模拟器 | 2 项流程测试通过 | 同上 emulator-api-35 |
| Release APK | 签名构建成功，APK Signature Scheme v2 验证通过 | [签名构建](https://github.com/Aurora-Lucas365/PauseCart/actions/runs/39220113442) |
| 签名来源 | APK 证书摘要与本机保管的发布密钥一致 | Release 的 SIGNATURE.txt |
| 合并清单 | minSdk 26 / targetSdk 35；无 INTERNET 权限；不可调试；备份关闭；无 debug 模拟 Activity | [APK-inspection.json](APK-inspection.json) |
| CPU 与页大小静态检查 | 四种 ABI；原生库 ELF 加载段和 APK 库数据均 16 KB 对齐 | 同上 |
| 界面核对 | 查看模拟器首页与分享表单截图，主要文字与按钮显示正常 | [预览构建](https://github.com/Aurora-Lucas365/PauseCart/actions/runs/39220795959) |

APK SHA-256：`70f7ed1c39b9cd85fd157a3048c8621c5247ac22471d784bb7d404c9e0b16c18`。

## 测试实际覆盖

- JVM：结算判断需组合信号，非目标 App 排除，退款 / 历史订单 / 支付结果排除；金额精度；放行与限频；分享解析。
- UI：不开无障碍权限，新增商品、核对保存价格、展示记录并标记放弃。
- 服务：仅使用 debug APK 中的本机模拟页面，测试服务连接、结算信号触发覆盖提醒、继续后的临时放行。结束后恢复模拟器设置；不访问商家、不发生交易。
- 验证安装包没有互联网权限、不启用应用备份，测试模拟页面不进入 release 包。

## 尚未验证

荣耀 400 / MagicOS 10 / Android 16 以及其他 ARM 真机、各品牌后台恢复、三平台的实际版本与结算路径、长时间耗电、16 KB 页大小真机运行、双开 / 工作资料、大字体与折叠屏等完整覆盖。

模拟器服务测试不证明淘宝 / 京东 / 拼多多结算规则有效；真实页面识别率、误报率、延迟与耗电尚无测量结果。95% / 1% 等设计目标不是已达成指标。此版本不是付款拦截器。
