# SubBoost Android

SubBoost Android 是仓库内独立的原生 Android 客户端。它在设备本地完成订阅解析和 Mihomo 配置生成，不需要部署 SubBoost 服务端。

## 当前能力

- 从一个或多个 HTTPS 订阅地址、系统文件或粘贴文本导入内容，并自动聚合多来源节点
- 解析 Clash YAML、Base64 订阅和常用分享链接
- 记忆已导入的 HTTPS 订阅地址，重新打开应用后可直接点击“获取订阅”
- 支持 SS、SSR、VMess、VLESS、Trojan、Hysteria2、TUIC、HTTP(S) 与 SOCKS5
- 自动处理重复节点名，并把 `HK`、`JP`、`US` 等节点名缩写映射为香港、日本、美国等中文名称
- 精简版、标准版、完整版三套内置模板
- 高级模式：订阅来源/地区/正则筛选、组类型、负载均衡策略、精确成员及成员顺序
- 自定义策略组、远程规则集、规则、规则目标与规则顺序
- 链式代理、中转节点、节点/策略组独立监听端口
- 节点删除/改名、策略组改名及策略组顺序
- 自定义基础与 DNS YAML
- 完整模板 JSON 导入导出，设置自动保存在设备上
- 按需启动带随机令牌的局域网 `config.yaml` HTTP 链接
- 复制、创建局域网导入链接或通过 Android 文件选择器保存生成结果

本应用只生成配置，不内置代理内核，也不会启动 VPN。订阅凭据不会上传；网络导入只允许 HTTPS。

## 模板与高级模式

在主界面选择 **模板与高级模式** 即可进入完整编辑器。内置模板与 Web 端定义一致：

- 精简版：节点选择、自动选择、广告、私有网络、国内、非中国和兜底
- 标准版：在精简版基础上增加 AI、YouTube、Google、Microsoft、Apple、GitHub、Telegram
- 完整版：启用除成人内容、Gemini 和 Google Scholar 之外的全部内置模块；这些模块仍可手动打开

导入节点时，节点名称中的独立国家/地区缩写会自动补充中文名称并保留原缩写，例如 `VLESS-CA-32` 会显示为 `VLESS-加拿大-CA-32`、`Hysteria2-NL-01` 会显示为 `Hysteria2-荷兰-NL-01`；生成 YAML 后，Mihomo 的节点选择列表直接使用这些中文名称。高级文本配置统一使用 `|` 分栏，界面内每一项均带有格式提示。例如：

```text
# 自定义规则
DOMAIN-SUFFIX|example.com|🚀 节点选择

# 自定义远程规则集
company|domain|geosite/company.mrs|DIRECT

# 中转组
香港中转|url-test|consistent-hashing|香港入口 01|美国落地 01

# 策略组监听
香港中转|10080|allow-lan
```

模板 JSON 的 schema 是 `subboost-android-config/v1`。导出的 JSON 包含所有模板和高级设置，但不包含订阅正文或节点密码。

## 局域网导入链接

生成 YAML 后，在 **局域网本地连接** 区域选择 **启动/更新连接**，应用会启动本地 HTTP 服务，并显示类似下面的地址：

```text
http://192.168.1.20:17890/config.yaml?token=随机访问令牌
```

- HTTP 服务监听设备的局域网接口，默认端口为 `17890`，可在高级设置中修改。
- 链接必须携带随机令牌；更换令牌后旧链接立即失效。
- 随机令牌会保存在应用本地。退出并重新打开 App 后，只要重新生成配置并启动连接，原链接仍可继续使用（局域网 IP 和端口未变化时）。
- 支持 `GET`、`HEAD` 和浏览器 CORS 预检，可供 Mihomo、自动化工具或同一局域网内的其他应用调用。
- 生成新配置时，正在运行的链接会自动更新到最新 YAML。
- 前台服务通知用于保持链接运行，可从通知或应用界面停止。
- 链接包含节点凭据对应的完整配置，只应提供给可信设备。局域网链路使用 HTTP，如调用方禁止明文 HTTP，需要为该局域网地址放行。

## 构建

需要 JDK 17、Android SDK 35 和 Gradle 8.13：

```bash
cd android
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

也可在仓库的 **Actions → Build Android APK → Run workflow** 手动触发构建。完成后下载 `subboost-android-debug-运行编号` 构件，其中包含 Debug APK 和对应的 SHA-256 校验文件。该工作流不会因 Push 或 Pull Request 自动执行。

## 发布签名

Actions 默认生成可直接安装测试的 Debug APK。正式发布时，请在可信环境中配置 Android keystore 并增加 release signing config；不要把 keystore 或密码提交到仓库。
