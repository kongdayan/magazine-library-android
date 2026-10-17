# 设备验证记录

日期：2026-10-12（Asia/Shanghai）。实测设备：HONOR BVL-AN20，Android 16 / API 36，WebView 153.0.8010.57。此记录仅表示本次设备和网站状态。

## 交付

- APK：`dist/MagazineLibrary-1.0.0.apk`，94,800 字节。
- 版本：1.0.0 / versionCode 1；minSdk 29，targetSdk 35，compileSdk 36。
- release 构建已安装，包标志不含 `DEBUGGABLE`。沿用本机签名升级，安装前的数据和已下载杂志仍可读取。
- APK SHA-256：`9e590d3ee52f9831f6ae8f974d0b93315079bea02c68e892a2ec2d012b7dc1e6`。
- `apksigner verify` 通过。交付 APK 不包含测试 TLS 证书或测试 assets。

## 真实网站与文件

在应用内打开首页、执行 `Journal of The Air Force` 搜索，并进入 Spring 2026 杂志详情。确认搜索结果含 Spring、Fall、Summer 等不同期刊链接。证据：`verification/release-web.json`。

最终下载过程保留了网站文件准备和浏览器中间页，原生下载器接管真正的文件地址。保留在手机书库的文件：

| 项目 | 实测结果 |
| --- | --- |
| 杂志 | Journal of The Air Force Historical Foundation - Spring 2026 |
| 状态 | ready |
| 文件字节 | 2,852,453（约 2.72 MiB） |
| PDF 页数 | 68 |
| 保存位置 | Download / Magazine Library |
| MediaStore URI | content://media/external/downloads/1000070023 |
| SHA-256 | d26b39f29fb868a2ae960946e25ff6b101b1611408a85b74d32233cd032333fc |

证据：`verification/final-device.json`、`verification/final-library.png`。文件在测试与 release 升级后保持可读。另一次直接文件下载得到相同内容，清理时验证两个副本 SHA-256 一致，并保留原始完整文件。

阅读器实测打开这份 68 页 PDF，翻到下一页，保存阅读进度；再次打开恢复之前的页码。双指手势将页面放大到约 1.89 倍。证据：`verification/real-pdf-reader.json`、`verification/real-pdf-reader.png`。

## 后台下载与分类

下列测试在同一真实手机上运行应用的实际下载服务和存储代码。HTTPS 测试服务位于设备 loopback；测试证书和临时信任配置仅位于独立测试 APK/测试进程。正式 APK 的 TLS 校验保持系统默认。

| 测试 | 结果 |
| --- | --- |
| 浏览器 Cookie 转交下载 | 通过，测试服务确认收到相同会话 Cookie |
| HTTP 跳转与 Set-Cookie | 通过，下一跳收到响应中新设置的 Cookie |
| 跨主机 Cookie 隔离 | 通过，localhost Cookie 未发送给 127.0.0.1 |
| 暂停与续传 | 通过，保留部分字节，继续请求使用 Range / If-Range |
| 界面退到后台后下载 | 通过，恢复的传输在后台完成 |
| 403 challenge 页面 | 不发布文件，进入等待网页验证状态 |
| HTTP 200 HTML 页面 | 不作为 PDF 发布 |
| 下载后 PDF 解析 | 保存的 PDF 可被 PdfRenderer 读取 |
| 同刊不同月份归类 | 两份自制 Field Notes PDF 归在同一系列 |
| 自选目录授权与保存 | 系统选择器授权 Download/Magazine Library Test，实际保存 3 页 PDF，并重新打开验证 |

证据：`verification/release-transfers.json`、`verification/library.json`、`verification/library.png`、`verification/chosen-directory.json`。测试目录和本次生成的测试 PDF 已清理，目录设置恢复默认。真实下载的 68 页杂志保留。

## 构建与限制

`:app:assembleRelease`、4 项名称/归类单元测试和 `:app:lintDebug` 均通过，构建记录在 `verification/final-build.log`。Lint 仍有非阻断警告，主要是固定中文 UI 字符串、JavaScript 的通用提示和 targetSdk 版本提示；没有错误。

本轮网站没有呈现交互式 Cloudflare CAPTCHA，因此没有将其标为已实测通过。应用使用普通 WebView 的 JavaScript、DOM storage、Cookie 和默认 User-Agent，验证由用户在网页内完成。模拟 challenge 测试仅验证下载器如何识别和处理验证页面，不替代真实 Cloudflare 验证。

只实测了这一台 Android 16 设备；Android 10–15 已做 API 兼容性检查，未逐版本做物理设备测试。VK 页面、临时文件地址与站点验证规则可能变化，等待下载页时需按照网页显示操作。
