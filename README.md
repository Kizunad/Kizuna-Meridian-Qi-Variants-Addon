# Kizuna's Meridian Qi Variants Addon

真元变体公共附属：通过框架 `QI_ATTRIBUTE` 登记五行之外的属性，提供合成定义、校验、阶数推导、区域出现权重和器官配方转换，供生物及功法附属复用。

正式内容只有枯与凋零，统一维护在 [qi-variants.textproto](server/src/main/resources/qi-variants.textproto)。金与木合成一阶枯，枯与火合成二阶凋零；损耗遵循框架规则，作为无属性游离真元释放到环境。

Java 17；`shared/` 提供 Protobuf 与展示元数据，`server/` 是纯 Java 库。仅依赖 Meridian Framework，实际加工、库存及游戏接线由宿主负责。

[职责与系列分工](docs/wiki/Home.md) · [合成与权重规则](docs/wiki/Derivation.md) · [构建与接入](docs/wiki/Integration.md)

```bash
./gradlew build
./gradlew demo
python3 "scripts/check_docs.py"
```

默认通过 composite build 使用同级 `Kizuna-Meridian-Mod-Framework`；自定义路径使用 `-Pkizuna.dir.framework="/path/to/framework"`。第三方版本取 Framework 的 `scripts/libs.versions.toml`。

许可：[PolyForm Noncommercial 1.0.0](LICENSE)。
