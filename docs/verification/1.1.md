# Magazine Library 1.1 验证记录

设备：HONOR BVL-AN20，Android 16，通过已授权 ADB 连接测试。构建使用 JDK 17 / Gradle 8.11.1 / Android SDK 36。1.1.0 的 versionCode 为 2，沿用上一版证书覆盖安装。

## 下载状态与名称

- 本地 HTTPS 真实传输测试：普通 HTML 下载页进入 waiting；带 cf-mitigated: challenge 的响应进入 auth，均没有错误发布成 PDF。
- WebView 打开测试页面，页面标题为 China Report，用户点击的 PDF 锚点文字为 Home Networking。测试保留一个旧 waiting 任务，点击新链接后验证新文件沿用锚点标题、旧任务保持 waiting。
- 下载任务另存原始点击链接 link，实际传输 URL 可随跳转更新。新点击和目录页导航清理旧浏览下载绑定。
- 证据：`../verification/v1.1/revision.json`。

## 已有书库修复

手机原有条目 17 的 URL 文件名明确为 Home Networking For Beginners - Autumn 2026，内容为 105 页 / 56,473,709 bytes。修正书名、分类、下载文件名和详情来源。

修正前后 SHA-256 一致：`612b1d8c64de09e87543a25b36a0f622900a8232026dff3886afd5cee9688ffb`。阅读进度保留第 62 页，另 3 本杂志保留。证据：`../verification/v1.1/correction.json`。

## 阅读与删除

- 在原有 105 页杂志上测试连续模式跳到第 61 页、下一页、实际触摸滚动、多次远距离跳页。最终 release 实测连续缓存为 3 页，受 8 页 / 24 MB 上限约束。
- 切回单页保持页码，双指缩放实测为 1.89 倍；关闭再打开恢复阅读位置。
- 使用测试生成的 PDF 验证删除同时移除文件与书库；「仅移出书库」保留文件。清理仅针对本次创建的测试任务和文件。
- 原有 4 本 PDF 的 SHA-256 全部保持一致。测试后恢复用户原有阅读位置与默认模式。
- 证据：`../verification/v1.1/revision.json`、`continuous-page61.png`、`single-page61.png`。

## 页面布局

顶部收为单行工具栏，搜索按需展开；书库卡片使用小封面和显式管理菜单，元信息单行；设置为独立的文件、阅读及浏览列表。已在设备字体和显示设置下查看截图。

证据：`../verification/v1.1/library.png`、`settings.png`、`search.png`、`browser.png`。

## 构建检查

运行 `:app:assembleRelease :app:testDebugUnitTest :app:lintDebug`，6 项单元测试与 Lint 均通过。Lint 保留非阻断的本地化、目标 SDK、JavaScript 和自定义绘图提示。交付 APK 不包含测试 TLS 证书或测试代码。

证据：`../verification/v1.1/build.log`。

## 真实站点下载与最终安装

在真实 Home Networking 详情页取得 PDF 锚点文字，使用网站自己的文件准备流程，接着等待 VK 跳转。真实 PDF 自动开始传输，无需点击下载按钮，本轮从未标记为 auth。

下载得到 56,473,709 bytes / 105 页，书名沿用点击锚点，SHA-256 与原书相同。只清理测试下载副本，原文件保留。证据：`../verification/v1.1/live-download.json`。

最终 release APK 已覆盖安装到设备，versionCode 2 / versionName 1.1.0，无 DEBUGGABLE 标志。相同测试在 release 安装后再次通过，原有 4 本 PDF 保持可读、阅读位置恢复。证据：`revision.json`、`final-library.json` 和 `package.json`。本轮未遇到交互式 Cloudflare 挑战；模拟挑战响应只用于验证状态分类，没有自动解决 CAPTCHA。

最终清点时用户已新增两项任务，未按固定书库总数清理；新增条目原样保留。最终验证记录只展开原有四本书，并单列新增任务 ID / 状态。
