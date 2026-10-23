# 真元变体公共附属

本仓库定义五行合成出的公共真元属性。生产内容包为 `qi_variants` 修订 1，初始只有一阶枯与二阶凋零；五行根引用框架内置身份，不重复登记。首个计划使用方为 Vanilla Creature Threatening 的生人批次。

| 系列模块 | 职责 |
|---|---|
| Mod Framework | 身体、经脉、器官、真元权威状态，通用命令、配方及附属内容登记 |
| Human | 人体构型、出生候选、器官内容和基础聚气；通过 Fabric 模块接入游戏宿主 |
| UI / Human UI | 通用身体展示与真元操作 / 人体专属客户端表现 |
| Items | 物品绑定目录，以及 Meridian 与 BS3D 库存之间的桥接 |
| Progression | 修炼进度、残卷内容及游戏接入 |
| Vanilla Damage / Healing | 原版伤害、治疗与权威身体状态之间的映射 |
| Vanilla Creature Threatening | 生物内容使用方，引用公共变体身份 |
| Qi Variants（本仓库） | 属性展示元数据、合成定义、图校验、权重查询和器官配方转换 |

`shared/` 包含独立 Protobuf 和生成消息，复用框架 `Realm`，不复制框架协议类。客户端可以只读取名称和颜色。`server/` 依赖本附属 `shared` 与框架服务端库，不引用 Minecraft 或 Fabric 类型。工程沿用 Human 的 Java 17、Gradle 8.8、composite build、内容导出器和中文注释约定。

本库不规定哪个器官能合成、何时加工或属性产生什么战斗效果。转换工具仅生成可信配方定义，由使用方发布和调用；加工执行、身体及环境库存、世界读取、网络分发仍由框架和宿主负责。自然合成、退化和合成 UI 不在本版范围。

区域查询返回出现概率，不扣除五行、不产生真元，也不表示区域实际库存已经按配方合成。构建验证通过仅代表公共库契约可用，不代表 Threatening 或游戏宿主已经接线。

- [合成与权重规则](Derivation.md)
- [构建与接入](Integration.md)
- [项目入口](../../README.md)
