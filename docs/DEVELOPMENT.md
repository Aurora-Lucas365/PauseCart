# 开发与构建

JDK 17、Android SDK Platform 35 / Build Tools 35.0.0、Gradle Wrapper 8.9。

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

Windows 使用 `gradlew.bat`。`local.properties` 设置本机 `sdk.dir`，不要提交。

正式签名使用环境变量 `PAUSECART_KEYSTORE`、`PAUSECART_STORE_PASSWORD`、`PAUSECART_KEY_PASSWORD`，别名 `pausecart`。签名密钥不得提交或上传至 Release；同一密钥用于后续覆盖升级。

```text
core/      本机判断、金额解析、访问去重状态机
service/   系统事件、有限扫描、覆盖窗口、进程内诊断状态
ui/        Compose 三页界面、可观察设置、表单
 data/     本机清单、设置流、可选通知
```

设置通过可观察流驱动界面，服务连接与实际应用配置单独确认。诊断只在进程内保留包名、时间与处理结果；不记录原始页面文字。

模拟器的 debug 页面只用于事件与提醒流程测试，不进入正式安装包。请区分本机窗口测试、真实目标 App 事件测试与真实结算规则测试。

依赖各自许可适用，Gradle / AndroidX 使用 Apache-2.0，项目采用 MIT。请保留分发所需许可文本。
