# Piko NewX Custom

此 fork 为新版 X 提供 NewX 补丁和自定义下载目录，基于 [crimera/piko 的 x-lite 分支](https://github.com/crimera/piko/tree/x-lite)。

本次验证目标是 **X 12.34.0-prod.01 / 312340001，arm64-v8a，Android 10+**，使用用户提供的 APKMirror 原版 APKM。

## 在 Morphe 中导入

[添加 Piko NewX Custom 补丁源](https://morphe.software/add-source?github=YongkeDet/piko&name=Piko%20NewX%20Custom)

已经添加此仓库的用户，在 Morphe 的补丁源页面刷新到 **3.10.0-custom-newx.1**。

1. 选择原版 `12.34.0-prod.01` arm64-v8a APKM，保持文件原样。
2. 选择本源中以 `NewX:` 开头的补丁，重新载入默认选择；旧版保存的 73 项选择不能直接沿用。
3. 完成修补后安装。默认选择不更改包名；需要与原版共存时，另选 `NewX: Clone`。

## 自定义下载目录

保留默认勾选的 `NewX: Inline download button` 和 `NewX: Redirect downloads to chosen folder`。

安装后打开 X 的 **Piko Settings → Posts and media → Download options**，分别为 Images、Videos 选择文件夹并授予访问权限。媒体直接保存到所选目录，可以使用文件名模板，也可以在媒体选择器复制链接或选择清晰度。

## 验证与迁移范围

本包提供 **52 项 NewX 补丁，其中 46 项默认启用**。本次修复了设置入口、链接处理、品牌资源、主题、回复栏、时间线位置和视频清晰度在 12.34 上的兼容问题，并增加社区附注、翻译选项和推广按钮策略。

最终构建、测试和实际 APKM 的默认/全选修补结果见[验证记录](docs/newx-resolver-linter/x12.34-port-2026-10-15.md)。

**旧版 73 项功能并未全部迁移。** 部分功能合并到了新版补丁中，另有功能仍缺失，例如登录令牌导入导出、原生阅读模式、撤回发帖和旧版关注列表。完整的[迁移对照表](docs/newx-12.34-coverage.md)逐项列出对应项和缺口。

补丁应用与 APK 重建验证不能代替真机测试；尚未在手机上验证登录、下载及各项交互。其他上游目标的既有路径保留，本次没有对应旧 APK 做整包回归。仅发布补丁包，不发布 X 安装包。

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
