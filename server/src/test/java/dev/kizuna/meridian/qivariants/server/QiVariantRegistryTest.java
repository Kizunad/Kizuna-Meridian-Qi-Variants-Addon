package dev.kizuna.meridian.qivariants.server;

import dev.kizuna.meridian.protocol.pb.AddonMessages.*;
import dev.kizuna.meridian.protocol.pb.BodyMessages;
import dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static dev.kizuna.meridian.qivariants.server.TestCatalogs.*;
import static org.junit.Assert.*;

/** 合成规则回归只使用测试配方，与生产内容分离。 */
public class QiVariantRegistryTest {
    @Test
    public void emptyCatalogKeepsFrameworkRootsAndSupportsEmptyRegions() {
        // 空目录合法；五行仍可查询，空区域不生成默认属性。
        var registry = registry(catalog());
        assertEquals(0, registry.catalog().getAttributesCount());
        for (String id : FiveElements.IDS) {
            assertEquals(0, registry.tier(id));
            assertTrue(registry.recipesFor(id).isEmpty());
            assertTrue(registry.ancestors(id).isEmpty());
        }
        assertWeights(Map.of(METAL, .75, WATER, .25), registry.weights(Map.of(METAL, 3.0, WATER, 1.0), 0));
        assertTrue(registry.weights(Map.of(), 10).isEmpty());
        assertTrue(registry.weights(Map.of(METAL, 0.0), 0).isEmpty());
        assertTrue(registry.sample(Map.of(), 0, new Random(1)).isEmpty());
    }

    @Test
    public void supportsManyElementsTwoVariantsAndMixedInputs() throws Exception {
        // 覆盖三种五行、一阶加一阶、一阶加多种五行，以及协议往返。
        var catalog = catalog(recipe("test:a", A, .2, METAL, WOOD, WATER),
                recipe("test:b", B, .1, FIRE, EARTH), recipe("test:c", C, .3, A, B),
                recipe("test:d", D, .4, A, FIRE, EARTH));
        var registry = registry(VariantCatalog.parseFrom(catalog.toByteArray()));
        assertEquals(catalog, registry.catalog());
        assertEquals(1, registry.tier(A));
        assertEquals(1, registry.tier(B));
        assertEquals(2, registry.tier(C));
        assertEquals(2, registry.tier(D));
        assertEquals(Set.of(A, B, METAL, WOOD, WATER, FIRE, EARTH), registry.ancestors(C));
        assertEquals(Set.of(A, METAL, WOOD, WATER, FIRE, EARTH), registry.ancestors(D));
        assertFalse(registry.ancestors(C).contains(C));
    }

    @Test
    public void usesMinimumTierButAllRoutesForAncestorsAndWeights() {
        // 低阶产物的另一条路线依赖高阶输入，按最小阶数排序会漏算该路线。
        var registry = registry(catalog(recipe("test:a", A, 0, METAL, WOOD),
                recipe("test:b", B, 0, A, FIRE), recipe("test:c", C, 0, B, WATER),
                recipe("test:d_roots", D, 0, WATER, EARTH),
                recipe("test:d_high", D, 0, C, FIRE)));
        assertEquals(3, registry.tier(C));
        assertEquals(1, registry.tier(D));
        assertEquals(Set.of(A, B, C, METAL, WOOD, WATER, FIRE, EARTH), registry.ancestors(D));
        assertEquals(List.of("test:d_high", "test:d_roots"),
                registry.recipesFor(D).stream().map(SynthesisRecipe::getId).toList());
        assertWeights(Map.of(FIRE, 10.0 / 19, D, 9.0 / 19), registry.weights(Map.of(C, 9.0, FIRE, 1.0), 1));
    }

    @Test
    public void rejectsEveryCycleEvenWhenAnotherRouteReachesRoots() {
        // 不能用一条合法路线掩盖另一条路线中的环，也不能自引用。
        assertInvalid(catalog(recipe("test:a", A, 0, B, METAL), recipe("test:b", B, 0, A, WOOD),
                recipe("test:a_roots", A, 0, METAL, WOOD)));
        assertInvalid(catalog(recipe("test:self", A, 0, A, METAL)));
    }

    @Test
    public void rejectsDuplicateAttributesAndRecipeIdsRegardlessOfVersion() {
        // 合成目录每个 ID 仅有一条当前修订，器官配方身份另由调用方指定。
        var recipe = recipe("test:a", A, 0, METAL, WOOD);
        var valid = catalog(recipe);
        assertInvalid(valid.toBuilder().addAttributes(attribute(A)).build());
        assertInvalid(valid.toBuilder().addRecipes(recipe.toBuilder().setVersion(2)).build());
    }

    @Test
    public void rejectsMissingReferencesRootOutputsAndOrphans() {
        // 输入只能来自五行和当前目录，所有变体都必须可合成。
        assertInvalid(catalog(recipe("test:a", A, 0, METAL, "test:missing")));
        assertInvalid(catalog(recipe("test:a", A, 0, METAL, "meridian:none")));
        var valid = catalog(recipe("test:a", A, 0, METAL, WOOD));
        assertInvalid(valid.toBuilder().setRecipes(0, valid.getRecipes(0).toBuilder()
                .setOutputAttributeId(WOOD)).build());
        assertInvalid(valid.toBuilder().setRecipes(0, valid.getRecipes(0).toBuilder()
                .setOutputAttributeId("test:missing")).build());
        assertInvalid(valid.toBuilder().addAttributes(attribute(B)).build());
    }

    @Test
    public void rejectsTooFewOrRepeatedInputsAndInvalidRatios() {
        // 同种属性重复投入不能冒充多属性合成。
        assertInvalid(catalog(recipe("test:a", A, 0, METAL)));
        assertInvalid(catalog(recipe("test:a", A, 0, METAL, METAL)));
        var valid = recipe("test:a", A, 0, METAL, WOOD);
        for (double ratio : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertInvalid(catalog(valid.toBuilder().setInputs(0,
                    valid.getInputs(0).toBuilder().setRatio(ratio)).build()));
        }
    }

    @Test
    public void rejectsMissingOrInvalidLossAndUnknownRealms() {
        // 零损耗合法但必须显式写入；缺省境界与非法零枚举区分处理。
        var valid = recipe("test:a", A, 0, METAL, WOOD);
        assertInvalid(catalog(valid.toBuilder().clearLossRatio().build()));
        for (double loss : new double[] {-1, 1, 2, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertInvalid(catalog(valid.toBuilder().setLossRatio(loss).build()));
        }
        assertInvalid(catalog(valid.toBuilder().setMinimumRealm(BodyMessages.Realm.REALM_UNSPECIFIED).build()));
        assertInvalid(catalog(valid.toBuilder().setMinimumRealmValue(999).build()));
        assertEquals(1, registry(catalog(valid.toBuilder().setMinimumRealm(BodyMessages.Realm.TRUE_QI).build())).tier(A));
    }

    @Test
    public void validatesSchemaIdsVersionsNamesAndColors() {
        // 展示字段属于公共契约，不能把缺色误认为黑色。
        var valid = catalog(recipe("test:a", A, 0, METAL, WOOD));
        assertInvalid(valid.toBuilder().setSchemaVersion(999).build());
        for (VariantAttribute bad : List.of(attribute(A).toBuilder().clearColorRgb().build(),
                attribute(A).toBuilder().setColorRgb(0x1000000).build(),
                attribute(A).toBuilder().setColorRgb(-1).build(),
                attribute(A).toBuilder().setDisplayName(" ").build(),
                attribute("invalid"), attribute(METAL), attribute("meridian:none"))) {
            assertInvalid(valid.toBuilder().setAttributes(0, bad).build());
        }
        for (int version : new int[] {0, -1}) {
            assertInvalid(valid.toBuilder().setRecipes(0, valid.getRecipes(0).toBuilder().setVersion(version)).build());
        }
        assertInvalid(valid.toBuilder().setRecipes(0, valid.getRecipes(0).toBuilder().setId("invalid")).build());
        assertInvalid(valid.toBuilder().setRecipes(0, valid.getRecipes(0).toBuilder().setId("meridian:reserved")).build());
    }

    @Test
    public void requiresActualFrameworkAttributeSymbolsAndRejectsWholeInvalidCatalog() {
        // 结构正确也不能绕过框架登记；加载失败不修改已经安装的框架。
        var valid = catalog(recipe("test:a", A, 0, METAL, WOOD));
        assertThrows(IllegalArgumentException.class, () -> QiVariantRegistry.load(framework(), valid));
        var wrongKind = bundle(valid).toBuilder().clearSymbols()
                .addSymbols(ContentSymbol.newBuilder().setId(A).setKind(ContentSymbolKind.ORGAN_KIND)).build();
        assertThrows(IllegalArgumentException.class, () -> QiVariantRegistry.load(framework(wrongKind), valid));
        var framework = framework(bundle(valid));
        var invalid = valid.toBuilder().addAttributes(attribute(B)).build();
        assertThrows(IllegalArgumentException.class, () -> QiVariantRegistry.load(framework, invalid));
        assertEquals(1, QiVariantRegistry.load(framework, valid).tier(A));
    }

    @Test
    public void takesMaximumRouteProductAndNormalizesOnceAtTheEnd() {
        // 多路线取最大值而非相加；五行直接份额不会先被合成候选扣除。
        var registry = registry(catalog(recipe("test:a1", A, .2, METAL, WOOD),
                recipe("test:a2", A, .3, METAL, WATER)));
        assertWeights(Map.of(METAL, 4.0 / 9, WOOD, 2.0 / 9, WATER, 2.0 / 9, A, 1.0 / 9),
                registry.weights(Map.of(METAL, 2.0, WOOD, 1.0, WATER, 1.0), 1));
        assertWeights(Map.of(METAL, .5, WOOD, .25, WATER, .25),
                registry.weights(Map.of(METAL, 2.0, WOOD, 1.0, WATER, 1.0), 0));
    }

    @Test
    public void directVariantSharesPropagateAndCompeteWithSynthesisProducts() {
        // 直接存在的一阶属性可以参与二阶权重，即使区域内没有它的五行原料。
        var registry = registry(catalog(recipe("test:a", A, 0, METAL, WOOD),
                recipe("test:b", B, 0, A, FIRE)));
        assertWeights(Map.of(A, 12.0 / 19, FIRE, 4.0 / 19, B, 3.0 / 19),
                registry.weights(Map.of(A, 3.0, FIRE, 1.0), 2));
        assertWeights(Map.of(A, .75, FIRE, .25), registry.weights(Map.of(A, 3.0, FIRE, 1.0), 1));
        assertWeights(Map.of(A, 1.0), registry.weights(Map.of(A, 1.0), 1));
        var direct = registry.weights(Map.of(METAL, 4.0, WOOD, 4.0, A, 1.0), 1);
        assertWeights(Map.of(METAL, 18.0 / 44, WOOD, 18.0 / 44, A, 8.0 / 44), direct);
    }

    @Test
    public void occurrenceWeightsDoNotUseSynthesisRatiosOrLosses() {
        // 出生倾向与实际可加工产量是独立概念，保持草案规定的乘积公式。
        var first = recipe("test:a", A, 0, METAL, WOOD);
        var second = first.toBuilder().setLossRatio(.99)
                .setInputs(1, first.getInputs(1).toBuilder().setRatio(99)).build();
        Map<String, Double> region = Map.of(METAL, 1.0, WOOD, 1.0);
        assertEquals(registry(catalog(first)).weights(region, 1), registry(catalog(second)).weights(region, 1));
    }

    @Test
    public void handlesLargeFiniteSharesAndRejectsInvalidQueryArguments() {
        // 大数先缩放后归一化；未知或非法数值不能静默变成零。
        var registry = registry(catalog());
        assertWeights(Map.of(METAL, .5, WOOD, .5),
                registry.weights(Map.of(METAL, Double.MAX_VALUE, WOOD, Double.MAX_VALUE), 0));
        for (double value : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> registry.weights(Map.of(METAL, value), 0));
        }
        assertThrows(IllegalArgumentException.class, () -> registry.weights(Map.of("meridian:none", 1.0), 0));
        assertThrows(IllegalArgumentException.class, () -> registry.weights(Map.of(A, 1.0), 0));
        assertThrows(IllegalArgumentException.class, () -> registry.weights(Map.of(), -1));
        var nullValue = new HashMap<String, Double>();
        nullValue.put(METAL, null);
        assertThrows(IllegalArgumentException.class, () -> registry.weights(nullValue, 0));
        assertThrows(IllegalArgumentException.class, () -> registry.tier("test:missing"));
        assertThrows(IllegalArgumentException.class, () -> registry.ancestors("test:missing"));
        assertThrows(IllegalArgumentException.class, () -> registry.recipesFor("test:missing"));
    }

    @Test
    public void returnsImmutableStableResultsAndSamplesProbabilityBoundaries() {
        // 分布按 ID 稳定排序：metal .4、wood .4、alpha .2。
        var source = catalog(recipe("test:a", A, 0, METAL, WOOD)).toBuilder();
        var registry = registry(source.build());
        source.clearRecipes().clearAttributes();
        var region = new HashMap<>(Map.of(METAL, 1.0, WOOD, 1.0));
        var result = registry.weights(region, 1);
        region.clear();
        assertWeights(Map.of(METAL, .4, WOOD, .4, A, .2), result);
        assertEquals(List.of(METAL, WOOD, A), new ArrayList<>(result.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> result.put(A, 1.0));
        assertThrows(UnsupportedOperationException.class, () -> registry.recipesFor(A).clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.ancestors(A).clear());
        Map<String, Double> shares = Map.of(METAL, 1.0, WOOD, 1.0);
        assertEquals(METAL, registry.sample(shares, 1, fixedRandom(.399)).orElseThrow());
        assertEquals(WOOD, registry.sample(shares, 1, fixedRandom(.4)).orElseThrow());
        assertEquals(A, registry.sample(shares, 1, fixedRandom(.8)).orElseThrow());
        assertEquals(A, registry.sample(shares, 1, fixedRandom(Math.nextDown(1.0))).orElseThrow());
    }

    @Test
    public void supportsDeepGraphsWithoutRecursiveTraversalOrATierLimit() {
        // 深图加载与祖先查询采用迭代算法，阶数没有人为上限。
        var recipes = new ArrayList<SynthesisRecipe>();
        String previous = METAL;
        for (int i = 1; i <= 512; i++) {
            String next = "test:level_" + i;
            recipes.add(recipe("test:route_" + i, next, 0, previous, WOOD));
            previous = next;
        }
        var registry = registry(catalog(recipes.toArray(SynthesisRecipe[]::new)));
        assertEquals(512, registry.tier(previous));
        assertEquals(513, registry.ancestors(previous).size());
    }

    private static void assertInvalid(VariantCatalog catalog) {
        // 单独验证结构拒绝，避免缺少框架符号让本应合法的目录也通过负向断言。
        assertThrows(IllegalArgumentException.class, () -> QiVariantRegistry.validate(catalog));
    }

    private static Random fixedRandom(double value) {
        // 固定分位点检验抽样边界，避免依赖统计次数的偶发失败。
        return new Random(0) {
            @Override
            public double nextDouble() {
                // 仅替换抽样使用的随机操作。
                return value;
            }
        };
    }

    private static void assertWeights(Map<String, Double> expected, Map<String, Double> actual) {
        // 同时核对身份集合、每项概率和总和。
        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((id, value) -> assertEquals(id, value, actual.get(id), 1e-12));
        assertEquals(expected.isEmpty() ? 0 : 1,
                actual.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
    }
}
