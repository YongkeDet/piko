# Piko NewX Custom

此 fork 为新版 X 提供 NewX 补丁和自定义下载目录，基于 [crimera/piko 的 x-lite 分支](https://github.com/crimera/piko/tree/x-lite)。

本次验证目标是 **X 12.34.0-prod.01 / 312340001，arm64-v8a，Android 10+**，使用用户提供的 APKMirror 原版 APKM。

## 在 Morphe 中导入

[添加 Piko NewX Custom 补丁源](https://morphe.software/add-source?github=YongkeDet/piko&name=Piko%20NewX%20Custom)

已经添加此仓库的用户，在 Morphe 的补丁源页面刷新到 **3.10.0-custom-newx.6**。

1. 选择原版 `12.34.0-prod.01` arm64-v8a APKM，保持文件原样。
2. 选择本源中以 `NewX:` 开头的补丁，重新载入默认选择；旧版保存的 73 项选择不能直接沿用。
3. 完成修补后安装。默认选择不更改包名；需要与原版共存时，另选 `NewX: Clone`。

## 自定义下载目录

保留默认勾选的 `NewX: Inline download button` 和 `NewX: Redirect downloads to chosen folder`。

安装后打开 X 的 **Piko 设置 → 下载与保存 → 保存位置与文件命名**，分别为图片、视频选择文件夹并授予访问权限。媒体直接保存到所选目录，可以使用文件名模板，也可以在媒体选择器复制链接或选择清晰度。

新增的“按作者分别建文件夹”默认开启：图片保存为“图片目录/@用户名/文件”，视频保存为“视频目录/@用户名/文件”。用户名统一为小写；无法确认用户名时使用来源用户 ID，仍无法识别时使用 `unknown`。开关就在保存位置与文件命名中，关闭后继续直接保存到所选根目录。仅影响之后通过 Piko 下载的媒体，不移动已有文件；外部下载器的保存位置由其自身控制。

## 主页一键下载

保留默认勾选的 `NewX: Download all profile media`、照片画廊及行内下载补丁。在用户主页的 **媒体 → 照片** 页，点击画廊上方的 **下载此主页全部图片和视频**。任务自动逐页遍历照片、视频和 GIF，显示进度并支持取消，完成后恢复原来的标签页。最多同时排入四个文件，继续使用已有的目录、作者子文件夹、命名、画质与重名规则；批量模式中的“每次询问”使用最高可用画质。

请保持页面在前台，且该主页须同时提供照片和视频标签页。“全部”指 X 当前允许该账号通过分页读取的媒体；网络或分页失败会显示未完成，不会当作全部成功。详情见[功能与原生契约](docs/newx-profile-bulk-download.md)和[构建、修补验证记录](docs/newx-resolver-linter/x12.34-profile-bulk-2026-10-16.md)。

`.6` 修复空照片/视频标签页被误判为尚未加载的问题，并区分缓存中的历史失败和新请求结果。暂时性失败最多尝试三次；首屏没有加载时请求原生刷新，重试进度可见。任务失败会显示具体原因、停止阶段和累计页数，单条异常媒体不会中断其余内容。详见[空页与重试修复记录](docs/newx-resolver-linter/x12.34-profile-bulk-empty-retry-2026-10-16.md)。

## 设置分类

设置依次排列为：**下载与保存、个人主页、时间线、内容过滤、帖子与互动、导航、外观、高级**。下载设置细分为保存位置与文件命名、下载方式、画质与重名处理、媒体选择窗口。原有设置 ID 和取值保留，已有配置与备份继续使用；新入口和说明均提供简体中文。

## 下载成功提示

`.4` 修复图片、视频和合并图片保存成功时上下两处重复提示的问题：只保留页面上方的“已保存”横幅。详情见[问题分析与验证记录](docs/newx-resolver-linter/x12.34-download-success-toast-2026-10-16.md)。

## 验证与迁移范围

本包提供 **55 项 NewX 补丁，其中 49 项默认启用**。本次修复了设置入口、链接处理、品牌资源、主题、回复栏、时间线位置和视频清晰度在 12.34 上的兼容问题，并增加社区附注、翻译选项和推广按钮策略。

新增分享链接追踪参数清理、镜像域名链接处理、外部下载器入口和发布说明入口；设置、选项、弹窗及下载通知已完整补齐简体中文，跟随应用/系统语言。外部下载器采用系统分享面板，可在“下载与保存 → 下载方式”中开启。镜像域名还需在 Android 应用的默认打开方式中启用。

作者分文件夹版本的构建和整包修补结果见[验证记录](docs/newx-resolver-linter/x12.34-author-folders-2026-10-15.md)。汉化版验证记录见[此处](docs/newx-resolver-linter/x12.34-zh-2026-10-15.md)，前一版适配详见[原验证记录](docs/newx-resolver-linter/x12.34-port-2026-10-15.md)。

**旧版 73 项功能并未全部迁移。** 部分功能合并到了新版补丁中，另有功能仍缺失，例如登录令牌导入导出、原生阅读模式、撤回发帖和旧版关注列表。完整的[迁移对照表](docs/newx-12.34-coverage.md)逐项列出对应项和缺口。

用户已反馈早期版本可正常使用。本次 `.6` 通过 348 项测试，55 项全选及 49 项默认修补均零失败并重建成功；最终两份 APK 的 30 项检查分别确认 Empty 分支、原生刷新、游标字段链、顶部成功提示和 603 条简体中文资源。修复后的真实账号分页、目录授权及取消交互仍需手机验证。其他上游目标的既有路径保留，本次没有对应旧 APK 做整包回归。仅发布补丁包，不发布 X 安装包。

## 构建

使用 Java 17、Android SDK 36 和具备上游依赖读取权限的 `GITHUB_TOKEN`，运行：

```sh
./gradlew :extensions:newx:test buildAndroid :patches:build :patches:lintNewxResolvers :patches:checkExtensionDescriptors
```

实际修补验证使用官方 Morphe Desktop 和显式传入的原版 APKM：

```sh
MORPHE_CLI_JAR=/path/to/morphe-desktop-all.jar ./patch-twitter.sh ORIGINAL.apkm --all-newx --unsigned
```

该脚本验证构建来源、补丁失败列表和重建结果。需要从当前源码构建后才能验证。

项目遵循 [GPLv3](LICENSE)，保留上游版权及 [NOTICE](NOTICE)。
