# X 12.34 迁移范围与验证边界

本分支发布的是 NewX 补丁集，包含 55 个可选择补丁，默认启用 49 个。
新增的 Download all profile media 支持主页照片、视频的逐页批量下载；旧版功能对照的状态不因此改变。
旧版 Piko 的设置、View 和模型接口已发生变化，不能把旧版 73 项选择原封不动用于新版。

“新版对应”表示有相应新版实现；“部分对应”明确列出范围差异；“尚未迁移”的功能
没有用空实现代替，也没有通过忽略异常伪装为成功。下表按仓库旧版 Twitter 源码中的公开补丁名称列出，
不假设它与某个用户保存的 73 项选择完全一致。

用户已反馈 `3.10.0-custom-newx.2` 可正常使用；这不等于逐项验证所有功能。`.3` 作者分文件夹功能已做构建和整包修补验证，仍需手机验证交互。旧目标只保留原有路径及形状测试，未获得旧 APK 做整包回归。

| 旧版名称 | 状态 | 新版对应或限制 |
| --- | --- | --- |
| Add ability to copy media link | 新版对应 | Inline download button → 媒体选择器的 Copy link |
| Block redirecting to X Lite | 不适用于 NewX | 输入本身已经是新版界面；不提供伪装成功的旧界面跳转补丁 |
| Block update screen | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Bring back twitter | 新版对应 | Restore Twitter branding（可选） |
| Change app icon | 部分对应 | Restore Twitter branding 提供鸟图标；旧版其他图标选项未迁移 |
| Clear tracking params | 新版对应 | Custom sharing domain → 清除分享链接中的追踪参数；仅清理 X/Twitter 帖子链接，保留其他查询参数和编码 |
| Control video auto scroll | 部分对应 | Disable video player scrolling 目前提供关闭滚动的控制 |
| Custom download folder | 新版对应 | Inline download button / Redirect downloads to chosen folder；图片和视频分别使用 SAF 目录，支持按作者建子文件夹 |
| Custom emoji font | 新版对应 | Custom font → Custom emoji font |
| Custom font | 新版对应 | Custom font |
| Custom share menu | 部分对应 | Customize post menu items / Customize media menu items；旧版分享菜单未整体复刻 |
| Custom sharing domain | 新版对应 | Custom sharing domain |
| Customise post font size | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Customize default reply sorting | 新版对应 | Set default reply sorting |
| Customize explore tabs | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Customize Inline action Bar items | 新版对应 | Customize inline actions |
| Customize Navigation Bar items | 新版对应 | Customize navigation bar |
| Customize notification tabs | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Customize profile tabs | 新版对应 | Customize profile tabs |
| Customize search suggestions | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Customize search tab items | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Customize side bar items | 新版对应 | Customize drawer items |
| Customize timeline top bar | 部分对应 | Customize timeline tabs / Hide timeline tabs bar；旧版其余项目未逐一迁移 |
| Delete from database | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Disable auto timeline scroll on launch | 新版对应 | Disable automatic timeline refresh / Restore timeline position |
| Disable chirp font | 新版对应 | Custom font → Use system font |
| Download patch | 新版对应 | Unlock downloads / Inline download button |
| Dynamic color | 新版对应 | Theme → Material You |
| Enable debug menu for posts | 部分对应 | Browse tweet object 可查看对象；不是旧版完整调试菜单 |
| Enable force HD videos | 新版对应 | Force highest video resolution |
| Enable PiP mode automatically | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Enable Undo Posts | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Force enable translate | 新版对应 | Force translate option |
| Handle custom twitter links | 新版对应 | Handle custom links；注册镜像域名并通过原生入口打开，需在 Android 的默认打开方式中启用这些链接 |
| Hide badges from navigation bar icons | 新版对应 | Customize navigation bar → Hide badges（导航栏编辑器内） |
| Hide Banner | 新版对应 | Hide new posts pill |
| Hide bookmark icon in timeline | 新版对应 | Customize inline actions → AddRemoveBookmarks |
| Hide community badges | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide Community Notes | 新版对应 | Hide Community Notes |
| Hide FAB | 新版对应 | Hide compose button |
| Hide FAB Menu Buttons | 部分对应 | Hide compose button 可隐藏整体；旧版各子按钮开关尚未迁移 |
| Hide followed by context | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide hidden replies | 尚未迁移 | 12.34 帖子模型中未发现旧 hasHiddenReplies 对应字段，本次放弃完整迁移；帖子菜单可隐藏“查看隐藏回复”入口，但范围不同 |
| Hide immersive player | 部分对应 | Disable video player scrolling 仅控制播放器滚动 |
| Hide Live Threads | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide nudge button | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide post metrics | 部分对应 | Customize inline actions 可以移除对应操作项；独立计数显示开关未迁移 |
| Hide promote button | 新版对应 | Hide promote button |
| Hide recommendation items | 部分对应 | Hide who to follow / Hide Discover more；旧版其他推荐项未逐一迁移 |
| Hide Recommended Users | 部分对应 | Hide who to follow 覆盖时间线和个人资料推荐；关注后弹出入口未真机确认 |
| Hook feature flag | 新版对应 | Feature switch overrides |
| Import/Export login token | 尚未迁移 | 未验证新版鉴权及令牌恢复接口，本次放弃迁移 |
| Legacy share links | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Log server response | 部分对应 | Server error logging 记录错误；并非旧版所有响应日志 |
| More information on profile | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Native downloader | 新版对应 | Unlock downloads / Inline download button |
| Native reader mode | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Native translator | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| No shortened URL | 新版对应 | Open canonical URLs |
| Pause search suggestions | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Remove Ads | 部分对应 | Remove ads：过滤时间线推广帖子和模块；不声称覆盖旧版全部广告入口 |
| Remove premium upsell | 新版对应 | Hide premium upsell |
| Remove search suggestions | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Remove view count | 新版对应 | Customize inline actions → ViewCount |
| Restore legacy follower lists | 尚未迁移 | 未发现可可靠恢复的旧版列表实现，本次放弃迁移 |
| Round off numbers | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Selectable Text | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Share Tweet as Image | 新版对应 | Share post as image（可选） |
| Show changelogs | 部分对应 | Show changelog → 高级设置中的本 fork 发布说明链接；未迁移安装新补丁后自动弹窗 |
| Show poll results | 新版对应 | Show poll results |
| Show post source label | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Show sensitive media | 新版对应 | Show sensitive media |
| Support external downloader | 部分对应 | Inline download button → 显示外部下载器按钮；通过系统分享面板发送当前媒体直链，未迁移旧版按指定包名发送帖子链接的流程 |

## `.2` 汉化与补齐范围

- Piko 的设置标题、说明、选择项、设置编辑器和弹窗已补齐简体中文；媒体选择器、清晰度选择、下载通知、合并图片和崩溃提示也使用中文资源。汉化跟随 Android 的应用语言/系统语言，技术标识符、用户名、路径和文件名模板变量保留原值。
- 共补齐 569 条 NewX 简体中文资源，另有 2 条共享设置资源；聚合测试要求所有可翻译默认资源都存在中文且格式占位符一致。
- 外部下载器开关默认关闭：**Piko 设置 → 帖子与媒体 → 帖子内下载 → 显示外部下载器按钮**；在媒体选择器中点击箭头，通过系统分享面板选择支持媒体链接的下载器。
- 自定义链接默认包含 `vxtwitter.com`、`fixvx.com`、`fxtwitter.com`、`fixupx.com`、`twittpr.com`、`xcancel.com` 及其子域名。可在 Morphe 的 `NewX: Handle custom links` 选项中修改域名列表，重新修补后到 **Android 应用信息 → 默认打开方式/打开支持的链接** 中启用。系统不会自动验证这些域名。
- 追踪参数清理只针对分享路径中实际经过 `ShareUrlResolver` 的 X/Twitter 帖子链接；不改写其他网站、搜索链接或正文内容。
- 其余未迁移项按用户“无法可靠实现就放弃”的要求保留缺失，不用空补丁或忽略异常替代。

## `.3` 按作者建文件夹

**Piko 设置 → 帖子与媒体 → 下载选项 → 按作者分别建文件夹**，默认开启并纳入设置备份。普通下载、被接管的 X 内置下载及合并图片均使用相同目录策略；图片和视频仍使用各自选定的根目录。作者目录形如 `@alice`，不使用可能重名的显示昵称。转帖归属通过原帖对象或与来源帖子编号匹配的链接确认；只有来源用户 ID 时使用 `user_编号`，完全无法识别时使用 `unknown`。

同一作者重复下载会复用目录，文件冲突策略在该子目录内生效。目录与文件的预留串行处理，网络传输仍可并行。关闭开关恢复平铺保存，只影响之后的下载，不移动已有文件。外部下载器或未被接管的原生下载不受此设置控制。

作者目录属于现有 Custom download folder 对应项，旧版 73 项覆盖数仍是 **33 项新版对应、14 项部分对应、25 项未迁移、1 项不适用**。

## 当前可选补丁

“默认”指补丁默认加入，不表示其所有运行时设置都开启。Clone、恢复 Twitter 品牌、调试和分享图片等为可选项。

| NewX 补丁 | 选择 |
| --- | --- |
| NewX: Remove ads | 默认 |
| NewX: Disable blur effects | 默认 |
| NewX: Restore Twitter branding | 可选 |
| NewX: Browse tweet object | 可选 |
| NewX: Open canonical URLs | 默认 |
| NewX: Clone | 可选 |
| NewX: Crash logs | 默认 |
| NewX: Custom font | 默认 |
| NewX: Custom sharing domain | 默认 |
| NewX: Handle custom links | 默认 |
| NewX: Show changelog | 默认 |
| NewX: Customize drawer items | 默认 |
| NewX: Theme | 默认 |
| NewX: Feature switch overrides | 默认 |
| NewX: Classic inline action spacing | 默认 |
| NewX: Customize inline actions | 默认 |
| NewX: Inline download button | 默认 |
| NewX: Redirect downloads to chosen folder | 默认 |
| NewX: Force highest video resolution | 默认 |
| NewX: Customize media menu items | 默认 |
| NewX: Set default media tab | 默认 |
| NewX: Gallery profile Photos tab | 默认 |
| NewX: Customize navigation bar | 默认 |
| NewX: Hide post reply bar | 默认 |
| NewX: Customize post menu items | 默认 |
| NewX: Set default profile post sorting | 默认 |
| NewX: Customize profile tabs | 默认 |
| NewX: Set default reply sorting | 默认 |
| NewX: Server error logging | 可选 |
| NewX: Share post as image | 可选 |
| NewX: Disable video player scrolling | 默认 |
| NewX: Hide premium upsell | 默认 |
| NewX: Unlock color customization | 默认 |
| NewX: Unlock downloads | 默认 |
| NewX: Customize timeline tabs | 默认 |
| NewX: Disable automatic timeline refresh | 默认 |
| NewX: Filter For You by topic | 默认 |
| NewX: Hide AI-generated posts | 默认 |
| NewX: Hide Discover more | 默认 |
| NewX: Hide compose button | 默认 |
| NewX: Hide new posts pill | 默认 |
| NewX: Hide post dividers | 可选 |
| NewX: Hide Spaces bar | 默认 |
| NewX: Hide timeline tabs bar | 默认 |
| NewX: Hide who to follow | 默认 |
| NewX: Hide Community Notes | 默认 |
| NewX: Force translate option | 默认 |
| NewX: Hide promote button | 默认 |
| NewX: Restore pinned home tab | 默认 |
| NewX: Restore timeline position | 默认 |
| NewX: Show poll results | 默认 |
| NewX: Show sensitive media | 默认 |
| NewX: Hide posts by verified account type | 默认 |
| NewX: Filter posts by keyword | 默认 |
