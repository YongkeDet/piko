# 悬浮玻璃底栏

目标为 X 12.34.0-prod.01 / 312340001，arm64-v8a，Android 10+。
新增 `NewX: Floating glass navigation bar` 补丁，默认选中；样式开关默认关闭。

安装后在 **Piko 设置 → 外观 → 悬浮玻璃底栏** 开启并重启。
手机主底栏变为圆角胶囊，左右各留 12dp，底部额外留 8dp，
7dp 阴影、0.75dp 细边框，内部使用 X 原生 Haze 背景模糊和随主题变化的半透明底色。
系统手势/导航栏安全区保留在胶囊外侧。

这是 Android 上接近液态玻璃的视觉样式，未实现 Apple 的动态光学折射、镜面追踪和交互变形。
范围是手机主底栏，不扩展到上栏、设置、弹窗或平板侧栏。关闭开关恢复原生布局。
“关闭模糊效果”仍优先，此时保留悬浮外观，使用 Haze 的原生背景回退效果。

## 原生契约

从保留的 `BottomBarOffsetAnimation` 标记解析布局宿主，沿 Function3 和 Haze 字段找到底栏。
证明系统 inset 的结果在 blur/fallback 两条分支上均原样到达唯一 modifier 合并点后，
在 inset 创建后、两分支之前增加间距、阴影、clip 和 border，保留原合并指令及其标签。
没有装饰整个页面 scaffold，也没有重建图标、标签、未读角标、点击、排序和替换逻辑。

为此底栏复制同属主的原生 Haze wrapper，仅覆盖 Color(J,F) 的 alpha 输入；原共享方法不变。
Composer、Haze 状态与原生变更标志原样传递，Object bridge 结果在原生调用点恢复 Modifier 类型。
原生 Haze 能力门和 Disable blur patch 保留。关闭样式时，modifier 原样返回，使用原始 Haze，
分隔线仍执行；开启时仅跳过底栏的一条 void 分隔线调用。

helper 通过相对 padding 布尔构造参数及非负间距校验、50% 圆角单例、Shape/Color 方法契约解析。
不使用固定 R8 类名或运行时反射，使用 typed bytecode 层；所有候选要求唯一，否则停止修补。
兼容声明仅覆盖有确切 APKM 的 12.34，不扩展旧目标。

[构建、修补和静态验证](newx-resolver-linter/x12.34-floating-glass-2026-10-16.md)不能替代实机视觉和触控测试。
