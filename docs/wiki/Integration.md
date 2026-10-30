# 构建与接入

依赖 JDK 17、Gradle Wrapper 8.14.6、Protobuf 插件 0.9.6 与 protoc / protobuf-java 4.36.2，版本统一取自 Framework 的 `scripts/libs.versions.toml`。运行库只依赖 Meridian Framework、本附属 `shared` 和 Protobuf；JUnit 4.13.2 仅用于测试。

```bash
./gradlew build
./gradlew demo
python3 "scripts/check_docs.py"
```

构建产物为 `shared/build/libs/` 下的协议库与 `server/build/libs/` 下的查询库，以及各自的 sources JAR。`shared` JAR 携带本附属的 `.proto`、生成消息和描述集 `META-INF/kizuna_meridian/protocol/qi-variants-v1.desc`。描述集包含导入的框架协议，供其他语言读取，但 JAR 不复制框架生成类。

服务端 JAR 包含：

| 路径 | 用途 |
|---|---|
| `qi-variants.textproto` | 正式目录的唯一文本源副本 |
| `META-INF/kizuna_meridian/addons/qi_variants.pb` | 框架 `AddonBundle`，加入宿主的完整 `AddonRegistration` |
| `META-INF/kizuna_meridian/qi-variants.pb` | 本附属 `VariantCatalog`，供查询与跨语言展示读取 |

生产目录在首次访问时从 UTF-8 TextFormat 解析并整份校验；构建导出器由相同入口生成两份二进制资源。构建工具和演示不进入公共运行库。

本内容包不反向依赖 Human、Items 或具体生物附属，框架兼容性由 API 版本表达。支持清单列出 `meridian:java_reference`、`meridian:fabric` 和 `bong:server`，不代表已完成所有宿主接线。

## Gradle 依赖

使用方通过 composite build 引用本库：

```groovy
// settings.gradle
includeBuild('../Kizuna-Meridian-Qi-Variants-Addon')

// 服务端子项目 build.gradle
dependencies {
    api 'dev.kizuna.meridian.qivariants:server:0.1.0-SNAPSHOT'
}
```

本库默认使用相邻框架仓库，位置由 Framework 的装配清单给出，可通过 `-Pkizuna.dir.framework="/path/to/framework"` 替换。框架尚无独立协议子项目，本库沿用 Human 的框架客户端产物导入协议，并用 `compileOnlyApi` 避免将客户端实现带入服务端运行路径；服务端运行时由框架 server 提供同一套消息。

客户端依赖本库 `shared`，同时提供框架 client 作为运行依赖，用于 `Realm` 等导入类型。它不需要本库 server 或框架 server：

```groovy
dependencies {
    implementation 'dev.kizuna.meridian.qivariants:shared:0.1.0-SNAPSHOT'
    implementation 'local.meridian:client'
}
```

`local.meridian:client/server` 坐标须在宿主 composite build 中映射到相邻框架的对应子项目。本库的 `settings.gradle` 提供了映射示例。

## Java 查询

宿主先把 `QiVariantsContent.bundle()` 加入完整内容注册，再使用已安装的框架目录：

```java
var variants = QiVariantRegistry.load(installed, QiVariantsContent.catalog());
var region = Map.of("meridian:metal", 1.0, "meridian:wood", 1.0, "meridian:fire", 1.0);
var immutableCatalog = variants.catalog();
var tier = variants.tier("qi_variants:wither"); // 2
var routes = variants.recipesFor("qi_variants:wither");
var ancestors = variants.ancestors("qi_variants:wither"); // 所有路线的输入并集
var probabilities = variants.weights(region, 2);
var selected = variants.sample(region, 2, new Random(42));
```

主要类型位于 `dev.kizuna.meridian.qivariants.server`，消息位于 `dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages`。二进制目录可以用 `VariantCatalog.parseFrom` 读取，然后通过相同的 `load` 入口验证。根 ID 列表通过 `FiveElements.IDS` 提供，身份来自框架常量。

宿主可以提供完整的聚合目录；所有输入必须在同一目录可解析，每个变体先由其所属内容包登记。目录不可变，查询不读取世界、不写库存、不提供网络通道。

## 使用方发布器官配方

```java
var definition = variants.recipesFor("qi_variants:withering").get(0);
var organRecipe = variants.toOrganRecipe(
        definition, "creatures:withering_batch", 1, "creatures:reactor",
        2.0, 1000L, 0.1);
```

这会生成 1 元金加 1 元木、产出 1.3 元枯的配方。目标配方 ID 与版本由使用方决定，同一合成定义可以生成多个不同批次或器官的配方。转换只接受当前已校验目录中完全相同的合成定义。

使用方负责把结果放入自己的 `AddonBundle.organ_recipes`，登记自己的 `ORGAN_KIND`，并在清单中声明 `qi_variants` 的直接依赖及内容版本范围。目标配方 ID 必须属于发布它的包命名空间；器官种类若来自外包，也要声明对应直接依赖。宿主须声明支持 `AddonRegistry.ORGAN_RECIPES` 能力，否则框架拒绝安装。

本库只返回配方，不决定哪个器官学习它、不扣原料、不调用加工命令。具体损耗和密度语义见 [合成与权重规则](Derivation.md)。

## 验证范围

通用规则测试使用 `test:` 属性，与正式内容分离，覆盖多输入、多路线、混合阶数、深图、循环拒绝、引用与数值校验、权重归一化、直接变体份额及稳定抽取。转换测试通过框架 Protobuf 校验和调用方内容包真实安装，验证身份、比例、密度、缺省境界与数值边界。

正式内容仅有一项冒烟测试：真实安装后目录可加载，枯和凋零分别为一、二阶。产物测试核对登记包与目录一致、描述集包含导入协议、运行库未夹带工具或重复生成类。`demo` 展示正式目录、两阶候选概率和一次配方转换。
