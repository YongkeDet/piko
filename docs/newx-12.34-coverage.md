# X 12.34 迁移范围与验证边界

本分支发布的是 NewX 补丁集，包含 52 个可选择补丁，默认启用 46 个。
旧版 Piko 的设置、View 和模型接口已发生变化，不能把旧版 73 项选择原封不动用于新版。

“新版对应”表示有相应新版实现；“部分对应”明确列出范围差异；“尚未迁移”的功能
没有用空实现代替，也没有通过忽略异常伪装为成功。下表按仓库旧版 Twitter 源码中的公开补丁名称列出，
不假设它与某个用户保存的 73 项选择完全一致。

补丁应用、APK 重建、静态字节码检查均不能代替手机上的功能验证。登录、下载、滚动、主题等
真实操作尚未在设备上测试；旧目标只保留原有路径及形状测试，未获得旧 APK 做整包回归。

| 旧版名称 | 状态 | 新版对应或限制 |
| --- | --- | --- |
| Add ability to copy media link | 新版对应 | Inline download button → 媒体选择器的 Copy link |
| Block redirecting to X Lite | 不适用于 NewX | 输入本身已经是新版界面；不提供伪装成功的旧界面跳转补丁 |
| Block update screen | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Bring back twitter | 新版对应 | Restore Twitter branding（可选） |
| Change app icon | 部分对应 | Restore Twitter branding 提供鸟图标；旧版其他图标选项未迁移 |
| Clear tracking params | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Control video auto scroll | 部分对应 | Disable video player scrolling 目前提供关闭滚动的控制 |
| Custom download folder | 新版对应 | Inline download button / Redirect downloads to chosen folder；图片和视频分别使用 SAF 目录 |
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
| Handle custom twitter links | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide badges from navigation bar icons | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide Banner | 新版对应 | Hide new posts pill |
| Hide bookmark icon in timeline | 新版对应 | Customize inline actions → AddRemoveBookmarks |
| Hide community badges | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide Community Notes | 新版对应 | Hide Community Notes |
| Hide FAB | 新版对应 | Hide compose button |
| Hide FAB Menu Buttons | 部分对应 | Hide compose button 可隐藏整体；旧版各子按钮开关尚未迁移 |
| Hide followed by context | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide hidden replies | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide immersive player | 部分对应 | Disable video player scrolling 仅控制播放器滚动 |
| Hide Live Threads | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide nudge button | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Hide post metrics | 部分对应 | Customize inline actions 可以移除对应操作项；独立计数显示开关未迁移 |
| Hide promote button | 新版对应 | Hide promote button |
| Hide recommendation items | 部分对应 | Hide who to follow / Hide Discover more；旧版其他推荐项未逐一迁移 |
| Hide Recommended Users | 部分对应 | Hide who to follow 覆盖时间线和个人资料推荐；关注后弹出入口未真机确认 |
| Hook feature flag | 新版对应 | Feature switch overrides |
| Import/Export login token | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
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
| Restore legacy follower lists | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Round off numbers | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Selectable Text | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Share Tweet as Image | 新版对应 | Share post as image（可选） |
| Show changelogs | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Show poll results | 新版对应 | Show poll results |
| Show post source label | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |
| Show sensitive media | 新版对应 | Show sensitive media |
| Support external downloader | 尚未迁移 | 本次 NewX 补丁包不提供此旧版功能 |

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
