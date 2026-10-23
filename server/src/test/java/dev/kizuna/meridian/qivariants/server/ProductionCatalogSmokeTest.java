package dev.kizuna.meridian.qivariants.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** 正式内容仅验证可加载和初始两条配方的阶数，不承担通用规则测试。 */
public class ProductionCatalogSmokeTest {
    @Test
    public void initialCatalogLoadsWithFirstAndSecondTierAttributes() {
        // 真实安装正式登记包后加载目录，内容变更只需要调整这一项冒烟测试。
        var registry = QiVariantRegistry.load(TestCatalogs.framework(QiVariantsContent.bundle()),
                QiVariantsContent.catalog());
        assertEquals(2, registry.catalog().getAttributesCount());
        assertEquals(2, registry.catalog().getRecipesCount());
        assertEquals(1, registry.tier("qi_variants:withering"));
        assertEquals(2, registry.tier("qi_variants:wither"));
    }
}
