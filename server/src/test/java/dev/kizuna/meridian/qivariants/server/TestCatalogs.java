package dev.kizuna.meridian.qivariants.server;

import dev.kizuna.meridian.protocol.pb.AddonMessages.*;
import dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages.*;
import dev.kizuna.meridian.server.AddonRegistry;

import java.util.Arrays;
import java.util.List;

/** 测试专用属性与合成定义；规则测试不读取正式内容。 */
final class TestCatalogs {
    static final String METAL = "meridian:metal";
    static final String WOOD = "meridian:wood";
    static final String WATER = "meridian:water";
    static final String FIRE = "meridian:fire";
    static final String EARTH = "meridian:earth";
    static final String A = "test:alpha";
    static final String B = "test:beta";
    static final String C = "test:gamma";
    static final String D = "test:delta";

    private TestCatalogs() {
        // 测试工具不向生产目录添加内容。
    }

    static VariantAttribute attribute(String id) {
        // 黑色也是合法颜色，必须保留显式存在性。
        return VariantAttribute.newBuilder().setId(id).setDisplayName("测试属性").setColorRgb(0).build();
    }

    static SynthesisRecipe recipe(String id, String output, double loss, String... inputs) {
        // 默认等比例，特定比例由各测试明确覆盖。
        var builder = SynthesisRecipe.newBuilder().setId(id).setVersion(1)
                .setOutputAttributeId(output).setLossRatio(loss);
        for (String input : inputs) {
            builder.addInputs(SynthesisInput.newBuilder().setAttributeId(input).setRatio(1));
        }
        return builder.build();
    }

    static VariantCatalog catalog(SynthesisRecipe... recipes) {
        // 从产物生成测试元数据，不绑定正式属性名称或配方数量。
        return VariantCatalog.newBuilder().setSchemaVersion(1)
                .addAllAttributes(Arrays.stream(recipes).map(SynthesisRecipe::getOutputAttributeId)
                        .distinct().sorted().map(TestCatalogs::attribute).toList())
                .addAllRecipes(List.of(recipes)).build();
    }

    static AddonBundle bundle(VariantCatalog catalog) {
        // 规则测试使用框架真实的符号登记入口。
        var builder = AddonBundle.newBuilder().setSchemaVersion(1)
                .setManifest(AddonManifest.newBuilder().setNamespace("test").setContentVersion(1)
                        .setApiVersions(ContentVersionRange.newBuilder().setMinimum(1).setMaximum(1))
                        .addSupportedHosts("meridian:java_reference"));
        for (var attribute : catalog.getAttributesList()) {
            builder.addSymbols(ContentSymbol.newBuilder().setId(attribute.getId())
                    .setKind(ContentSymbolKind.QI_ATTRIBUTE));
        }
        return builder.build();
    }

    static AddonRegistry framework(AddonBundle... bundles) {
        // 不安装正式目录，防止内容设计变更影响规则测试。
        return AddonRegistry.load(AddonRegistration.newBuilder().setProtocolVersion(1)
                .setHost(AddonHostProfile.newBuilder().setHostId("meridian:java_reference").setApiVersion(1)
                        .addCapabilities(AddonRegistry.ORGAN_RECIPES))
                .addAllBundles(List.of(bundles)).build());
    }

    static QiVariantRegistry registry(VariantCatalog catalog) {
        // 结构校验和框架符号校验都经过公开接口。
        return QiVariantRegistry.load(framework(bundle(catalog)), catalog);
    }
}
