# 下载进度与原生顶部提示合并

仅为已冻结验证的 X 12.34.0-prod.01 / arm64-v8a 增加 `NewX: Unified download notifications` 默认补丁。旧目标的通知桥和卡片适配器不变。

普通下载和长图分片/拼接使用 X 原生横幅。主文字显示任务数、百分比或等待状态；原生 detailText 显示字节数、速度和单文件名。不再同时叠加独立进度卡片。批量主页下载保留有取消按钮的详细任务对话框，成员不逐条弹提示，最终摘要使用同一原生横幅。

优先级：错误使用原生 Error/High，成功使用 Success/Normal，普通提示为 Default/Normal，持续进度为 Default/Low。结果至少获得 5 秒的保护时间；低级信息不覆盖错误。进度让位于其他原生通知，在横幅空闲后继续。每秒最多一个前台进度更新，不发送系统 Toast；未知长度不虚报百分比。终态清除进度时只允许清除本补丁所属的进度，不能清除结果或其他应用提示。

解析从 `InAppNotification(message=`、`detailText`、`style`、`priority`、`Show(entry=` 等语义及字段/调用关系建立，不使用短混淆名。构造参数使用真实 Kotlin 默认位，不能假定为 R8 处理后的参数下标。所有指令由 morphe-bytecode 发射。

原生 actor 解包 Show 后调用 Object-only 桥。新进度替换本补丁旧进度时先调用原生 present，再 dispose 旧 entry；dispose 因旧 entry 已不是当前 entry，不会写入中间 null，也不取消新通知的计时器。旧计时事件按原生 entry 身份检查，不能关闭新通知。丢弃更新和清除控制事件仍 dispose 对应 entry，释放 sender 的挂起 continuation；其他事件和原生消息沿原路径运行。

通知归属和代次通过弱引用及对象身份管理。终态后排队的旧进度、旧任务的延迟清除、正在显示的结果被清除等路径由回归测试覆盖。未操作设备；最终外观和动画仍需手机实际确认。
