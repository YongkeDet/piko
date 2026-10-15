# X 自定义下载文件夹

此分支基于 Piko v3.10.0，为 X 添加系统文件夹选择器，不再局限于主文件夹的三个公共目录。

## X 12.34.0-prod.01（新版界面）

新版 X 重写了设置页及媒体保存流程，旧版的 `Custom download folder`、`Native downloader` 和 Piko 设置页不能直接用于此版本。本分支为新版提供独立的 `Custom download folder (modern X)` 补丁；其他旧版 Piko 功能尚未迁移到新版。

1. 使用原版 `12.34.0-prod.01` APKM 打补丁，选择 `Custom download folder (modern X)`。本次使用的是 APKMirror 的 **arm64-v8a、Android 10+、480–640dpi** 变体。
2. 安装后，长按桌面上的 **X 图标 → 下载文件夹**，点击 **选择下载文件夹**，在系统选择器中选择或新建目录，并允许访问。
3. 之后通过 X 自身的图片、视频和私信附件保存入口保存文件；该补丁不额外提供旧版 Piko 的下载按钮或解锁下载权限。
4. 点击 **恢复 X 默认目录** 可恢复默认行为。切换或恢复目录时，已开始写入的文件保留原目的地。

新版补丁仅拦截已确认的公共媒体写入位置：通用媒体保存、私信附件保存和导出视频保存。X 继续执行原本的网络请求、数据流复制和错误处理；补丁把新建媒体项替换为目标目录中的文档，并处理媒体库的完成标记及失败清理。应用缓存和其他内容提供者不受影响。

输入 APKM 的 SHA-256：`016a7eb2b6d8e2d6410da7f3c60e1bb46b002f6fedd797e366b068fb2897682b`。

## 新版验证

- [构建与测试记录](https://github.com/YongkeDet/piko/actions/runs/38703254600)：20 项 Android 模拟测试及完整扩展、补丁构建通过，源代码为 `0623ce2f437f176902f00cef6b9cd1671c80e3f6`。
- 使用上述 SHA-256 的原版 APKM，在 Morphe Desktop 1.18.1 中按默认补丁选择完成真实打补丁与 APK 重建，无需强制兼容。
- 核验生成 APK 包含三个媒体保存位置的创建、完成和失败清理调用、应用初始化、新设置 Activity、桌面快捷方式及对应字符串资源；原生库保持 arm64-v8a。
- 尚未在真实设备上验证登录、桌面启动器显示及实际媒体下载。Android 模拟测试与字节码、资源核验不代替真机测试。

## X 12.19.1-release.0（原界面）

1. 使用此分支生成的 `.mpp` 补丁包重新给兼容版本的 X 打补丁，包含 `Custom download folder`。使用 Piko 原生下载器时，也可以只包含 `Native downloader`。
2. 打开 **Piko 设置 → 下载 → 选择自定义下载文件夹**。**原生 → 原生下载器** 中也有同一个设置。
3. 选择或新建一个文件夹，并点击系统的“使用此文件夹”授权。之后通过 Piko 内置下载入口保存的图片和视频会直接放到该文件夹中。
4. 点击 **恢复默认下载文件夹** 可恢复原有行为：视频使用主文件夹和子文件夹设置，图片使用 `Pictures/Twitter`。

目录授权会持久保存，无需每次下载重新选择。选择自定义目录后，主文件夹和子文件夹设置将停用；不会额外追加原有子文件夹。取消选择不会改变原设置。下载中的任务保留开始时选中的目录。

系统文件夹选择器可选择设备或 SD 卡中 Android 允许授权的文件夹。Android 11 及以上不允许通过此方式选择存储根目录、`Download` 根目录、`Android/data` 或 `Android/obb`；可以在允许的位置创建子文件夹，例如 `Download/Piko`。这不需要 root 或“管理所有文件”权限。外部下载器使用它自身的保存目录。

## 构建

在 GitHub 的 **Actions → Build custom download folder** 中运行此分支的工作流。成功后，从该次运行的 **Artifacts → piko-custom-download-folder** 下载补丁包；它不是已打补丁的 APK，仍需用 Morphe Manager 给原版 X 打补丁。

工作流使用仓库自动提供的 `GITHUB_TOKEN` 读取上游 Morphe 依赖；无需配置原项目发布流程使用的 GPG 签名密钥。Morphe 的包访问策略如果限制当前账号或仓库，需要为构建环境提供有权读取这些包的凭据。

单独运行目录功能的 Android 模拟测试：

```sh
./gradlew -p tests/download-folder test
```

完整构建：

```sh
./gradlew --no-daemon --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  clean buildAndroid :patches:build
```

## 原界面的实现与验证范围

X 原有下载请求的地址、请求头和下载 ID 均保留。系统下载管理器先将内容写入应用的临时目录，完成后由 Android JobScheduler 将文件复制到获得授权的目录。只有目标文件写入并关闭后，才删除临时下载；复制失败会保留下载数据以供重试。打开 Piko 下载设置会重新检查未完成的保存任务。

测试覆盖默认目录、只读授权、持久目录授权、请求头保留、下载期间切换目录、权限撤销、完成回调、复制失败重试、同名文件和恢复默认设置。模拟测试不能替代真机上不同 Android 版本及文件提供器的验证。
