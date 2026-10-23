# 仓库协作约定

- 本仓库是真元变体公共依赖，只依赖 Meridian Framework；不反向依赖 Human、生物、Items、UI 或功法附属，不修改框架来加入具体属性。
- `shared/` 存放独立 Protobuf 和展示元数据；`server/` 是 Java 17 纯 Java 库，不引用 Minecraft 或 Fabric 类型。
- 属性身份通过框架 `AddonBundle` 的 `QI_ATTRIBUTE` 登记；五行根使用框架内置 ID，不复制框架协议、生成类或权威库存。
- 正式属性和配方只维护在 `server/src/main/resources/qi-variants.textproto`；当前为枯、凋零两条，规则测试属性仅存在于测试源码集。
- 合成图查询提供不可变目录与候选结果，并提供框架器官配方转换工具；具体器官选择、加工执行、战斗效果、世界读取、网络及库存写入属于使用方或宿主。
- 损耗遵循框架现有规则：转为无属性游离真元释放；转换工具不改变框架损耗或实际原料匹配语义。
- 简体中文沟通、文档和函数内注释，公开标识符使用英文；新增方法和构造器包含中文意图注释。
- 契约变化同步更新 `docs/wiki/`；README 作为入口。交付前运行 `./gradlew build`、`./gradlew demo` 和 `python3 "scripts/check_docs.py"`。
- 使用 patch/Edit 修改文件，命令路径用双引号。保留用户修改；提交、推送及远端建仓分别按用户授权执行。
- 原创部分采用 PolyForm Noncommercial 1.0.0，保留第三方许可。公共库的构建成功不等于已完成游戏宿主接线。
