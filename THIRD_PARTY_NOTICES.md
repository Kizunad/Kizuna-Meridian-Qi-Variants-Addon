# 第三方说明

PolyForm Noncommercial 1.0.0 仅适用于本项目有权许可的原创部分。

| 组件 | 版本 / 来源 | 许可与使用 |
|---|---|---|
| Kizuna's Meridian Mod Framework | 同级独立仓库 | PolyForm Noncommercial 1.0.0；通过 Gradle composite build 依赖，不复制源码或生成类 |
| Gradle Wrapper | 9.8.0 | Apache License 2.0；启动脚本和 Wrapper JAR 来自 Framework 仓，保留版权头、JAR 内许可与分发校验和 |
| Protocol Buffers | 4.36.2 | BSD 3-Clause；protoc 为构建工具，protobuf-java 作为外部依赖解析 |
| JUnit / Hamcrest Core | 4.13.2 / 1.3 | Eclipse Public License 1.0 / BSD 3-Clause；仅用于测试，不随运行库打包 |

本项目 JAR 不内嵌框架、Protobuf 运行库或 Minecraft 实现。框架的传递依赖保留其各自许可，分发宿主负责解析并保留所使用依赖的原始许可。
