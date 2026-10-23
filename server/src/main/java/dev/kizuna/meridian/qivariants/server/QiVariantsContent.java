package dev.kizuna.meridian.qivariants.server;

import com.google.protobuf.TextFormat;
import dev.kizuna.meridian.protocol.AddonProtobuf;
import dev.kizuna.meridian.protocol.pb.AddonMessages.*;
import dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages.VariantCatalog;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 从唯一文本源加载正式内容，并投影框架所需的属性身份。 */
public final class QiVariantsContent {
    public static final String NAMESPACE = "qi_variants";
    public static final int CONTENT_VERSION = 1;
    public static final int SCHEMA_VERSION = 1;
    public static final String BUNDLE_RESOURCE = "META-INF/kizuna_meridian/addons/qi_variants.pb";
    public static final String CATALOG_RESOURCE = "META-INF/kizuna_meridian/qi-variants.pb";
    public static final String SOURCE_RESOURCE = "qi-variants.textproto";

    private QiVariantsContent() {
        // 内容不持有玩家或区域运行状态。
    }

    public static VariantCatalog catalog() {
        // 延迟加载一次；对外始终交付完成结构校验的不可变消息。
        return CatalogHolder.CATALOG;
    }

    public static AddonBundle bundle() {
        // 属性元数据与登记包由同一目录投影，不手工维护第二份属性 ID。
        var manifest = AddonManifest.newBuilder().setNamespace(NAMESPACE).setContentVersion(CONTENT_VERSION)
                .setApiVersions(ContentVersionRange.newBuilder().setMinimum(1).setMaximum(1))
                .addSupportedHosts("meridian:java_reference")
                .addSupportedHosts("meridian:fabric")
                .addSupportedHosts("bong:server");
        var bundle = AddonBundle.newBuilder().setSchemaVersion(AddonProtobuf.SCHEMA_VERSION)
                .setManifest(manifest);
        for (var attribute : catalog().getAttributesList()) {
            QiVariantsErrors.require(AddonProtobuf.namespaceOf(attribute.getId()).equals(NAMESPACE),
                    "正式内容属性必须属于本包命名空间", attribute.getId());
            bundle.addSymbols(ContentSymbol.newBuilder().setId(attribute.getId())
                    .setKind(ContentSymbolKind.QI_ATTRIBUTE));
        }
        var result = bundle.build();
        AddonProtobuf.validateBundle(result);
        return result;
    }

    private static VariantCatalog readCatalog() {
        // 使用严格 TextFormat 解析，漏字段和错误关系由统一目录校验整份拒绝。
        try (var stream = QiVariantsContent.class.getClassLoader().getResourceAsStream(SOURCE_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("缺少正式变体目录：" + SOURCE_RESOURCE);
            }
            var builder = VariantCatalog.newBuilder();
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                TextFormat.getParser().merge(reader, builder);
            }
            return QiVariantRegistry.validate(builder.build()).catalog();
        } catch (IOException exception) {
            throw new IllegalStateException("无法解析正式变体目录：" + SOURCE_RESOURCE, exception);
        }
    }

    private static final class CatalogHolder {
        private static final VariantCatalog CATALOG = readCatalog();

        private CatalogHolder() {
            // 静态持有不可变目录，不提供可变全局登记接口。
        }
    }
}
