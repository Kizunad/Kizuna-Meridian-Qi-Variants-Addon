package dev.kizuna.meridian.qivariants.server;

import dev.kizuna.meridian.protocol.CapabilityProtobuf;
import dev.kizuna.meridian.error.MeridianErrors.AddonRejected;
import dev.kizuna.meridian.protocol.QiDensity;
import dev.kizuna.meridian.protocol.pb.AddonMessages.*;
import dev.kizuna.meridian.protocol.pb.BodyMessages;
import dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages.*;
import org.junit.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static dev.kizuna.meridian.qivariants.server.TestCatalogs.*;
import static org.junit.Assert.*;

/** 转换结果必须能通过框架真实校验和调用方内容包安装。 */
public class OrganRecipeConversionTest {
    @Test
    public void convertsRatiosBatchLossIdentityAndMinimumRealm() {
        // 一条合成定义可绑定多个独立器官配方，不复用合成 ID。
        var synthesis = recipe("test:a", A, .25, METAL, WOOD, WATER).toBuilder()
                .setInputs(1, SynthesisInput.newBuilder().setAttributeId(WOOD).setRatio(3))
                .setInputs(2, SynthesisInput.newBuilder().setAttributeId(WATER).setRatio(2))
                .setMinimumRealm(BodyMessages.Realm.TRUE_QI).build();
        var registry = registry(catalog(synthesis));
        var recipe = registry.toOrganRecipe(synthesis, "consumer:reactor_batch", 7, "consumer:reactor", 12, 1500, .4);
        var core = CapabilityProtobuf.fromRecipe(recipe);
        assertEquals("consumer:reactor_batch", recipe.getId());
        assertEquals(7, recipe.getVersion());
        assertEquals("consumer:reactor", recipe.getOrganKind());
        assertEquals(1500, recipe.getDurationMillis());
        assertEquals(BodyMessages.Realm.TRUE_QI, recipe.getMinimumRealm());
        Map<String, Double> amounts = recipe.getInput().getEntriesList().stream()
                .collect(Collectors.toMap(BodyMessages.Qi::getAttribute, BodyMessages.Qi::getAmount));
        assertEquals(Map.of(METAL, 2.0, WOOD, 6.0, WATER, 4.0), amounts);
        assertEquals(12, core.input().totalQi(), 0);
        assertEquals(9, core.output().totalQi(), 0);
        assertEquals(1, recipe.getOutput().getEntriesCount());
        assertEquals(A, recipe.getOutput().getEntries(0).getAttribute());
        assertEquals(recipe, CapabilityProtobuf.decodeRecipe(CapabilityProtobuf.encodeRecipe(recipe)));
        assertNotEquals(recipe.getId(), registry.toOrganRecipe(synthesis,
                "consumer:another_batch", 1, "consumer:another", 2, 500, 1).getId());
    }

    @Test
    public void keepsTheChosenDensityAndMapsAbsentRealmToTheLowestValidRealm() {
        // 所有相态边界都由框架判断，输入要求和产物使用同一个指定密度。
        var synthesis = recipe("test:a", A, 0, METAL, WOOD);
        var registry = registry(catalog(synthesis));
        for (double density : new double[] {.01, .099, .1, .999, 1, 9.99, 10, 99, 100, 999, 1000}) {
            var recipe = registry.toOrganRecipe(synthesis, "consumer:batch", 1, "consumer:reactor", 2, 10, density);
            assertEquals(BodyMessages.Realm.WANDERING_QI, recipe.getMinimumRealm());
            recipe.getInput().getEntriesList().forEach(qi -> {
                // 配方声明不承诺改变实际原料密度。
                assertEquals(density, qi.getDensity(), 0);
                assertEquals(QiDensity.phase(density), QiDensity.phase(qi.getDensity()));
            });
            assertEquals(density, recipe.getOutput().getEntries(0).getDensity(), 0);
            assertEquals(2, CapabilityProtobuf.fromRecipe(recipe).output().totalQi(), 0);
        }
    }

    @Test
    public void registersConvertedRecipesInTheConsumersNamespace() {
        // 同时验证属性直接依赖、器官种类和生成配方的命名空间所有权。
        var synthesis = recipe("test:a", A, .35, METAL, WOOD);
        var catalog = catalog(synthesis);
        var recipe = registry(catalog).toOrganRecipe(synthesis,
                "consumer:batch", 1, "consumer:reactor", 2, 1000, .1);
        var consumer = AddonBundle.newBuilder().setSchemaVersion(1)
                .setManifest(AddonManifest.newBuilder().setNamespace("consumer").setContentVersion(1)
                        .setApiVersions(ContentVersionRange.newBuilder().setMinimum(1).setMaximum(1))
                        .addSupportedHosts("meridian:java_reference")
                        .addDependencies(AddonDependency.newBuilder().setNamespace("test")
                                .setVersions(ContentVersionRange.newBuilder().setMinimum(1).setMaximum(1))))
                .addSymbols(ContentSymbol.newBuilder().setId("consumer:reactor").setKind(ContentSymbolKind.ORGAN_KIND))
                .addOrganRecipes(recipe).build();
        var installed = framework(bundle(catalog), consumer);
        assertTrue(installed.recipes().find(recipe.getId(), recipe.getVersion()).isPresent());
        var withoutDependency = consumer.toBuilder().setManifest(consumer.getManifest().toBuilder()
                .clearDependencies()).build();
        var rejection = assertThrows(AddonRejected.class, () -> framework(bundle(catalog), withoutDependency));
        assertEquals(AddonIssueCode.UNDECLARED_DEPENDENCY, rejection.issue().getCode());
    }

    @Test
    public void scalesHugeRatiosAndPreservesZeroLossConservation() {
        // 比例相加会溢出时仍能转换，零损耗产物以实际输入合计为准。
        var synthesis = recipe("test:a", A, 0, METAL, WOOD).toBuilder()
                .setInputs(0, SynthesisInput.newBuilder().setAttributeId(METAL).setRatio(Double.MAX_VALUE))
                .setInputs(1, SynthesisInput.newBuilder().setAttributeId(WOOD).setRatio(Double.MAX_VALUE)).build();
        var registry = registry(catalog(synthesis));
        var core = CapabilityProtobuf.fromRecipe(registry.toOrganRecipe(synthesis,
                "consumer:batch", 1, "consumer:reactor", Double.MAX_VALUE, 1, 1));
        assertEquals(Double.MAX_VALUE, core.input().totalQi(), 0);
        assertEquals(core.input().totalQi(), core.output().totalQi(), 0);
    }

    @Test
    public void rejectsUnregisteredOrModifiedSynthesisDefinitions() {
        // 仅 ID 相同不能授权不同输入比例或损耗率。
        var synthesis = recipe("test:a", A, .2, METAL, WOOD);
        var registry = registry(catalog(synthesis));
        assertThrows(IllegalArgumentException.class, () -> registry.toOrganRecipe(
                synthesis.toBuilder().setLossRatio(.3).build(), "consumer:batch", 1, "consumer:reactor", 2, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> registry.toOrganRecipe(
                synthesis.toBuilder().setId("test:missing").build(), "consumer:batch", 1, "consumer:reactor", 2, 1, 1));
    }

    @Test
    public void rejectsInvalidConversionParametersAndUnrepresentableDoses() {
        // 复用框架约束；浮点下溢不能变成免费原料或静默消失的损耗。
        var synthesis = recipe("test:a", A, .2, METAL, WOOD);
        var registry = registry(catalog(synthesis));
        for (double amount : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> registry.toOrganRecipe(
                    synthesis, "consumer:batch", 1, "consumer:reactor", amount, 1, 1));
        }
        for (double density : new double[] {0, .009, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> registry.toOrganRecipe(
                    synthesis, "consumer:batch", 1, "consumer:reactor", 2, 1, density));
        }
        assertThrows(IllegalArgumentException.class, () -> registry.toOrganRecipe(
                synthesis, "consumer:batch", 0, "consumer:reactor", 2, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> registry.toOrganRecipe(
                synthesis, "consumer:batch", 1, "consumer:reactor", 2, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> registry.toOrganRecipe(
                synthesis, "invalid", 1, "consumer:reactor", 2, 1, 1));
        var tinyLoss = synthesis.toBuilder().setLossRatio(Double.MIN_VALUE).build();
        var tinyLossRegistry = registry(catalog(tinyLoss));
        assertThrows(IllegalArgumentException.class, () -> tinyLossRegistry.toOrganRecipe(
                tinyLoss, "consumer:batch", 1, "consumer:reactor", 2, 1, 1));
        var tinyInput = synthesis.toBuilder().setInputs(0,
                SynthesisInput.newBuilder().setAttributeId(METAL).setRatio(Double.MIN_VALUE)).build();
        var hugeInput = tinyInput.toBuilder().setInputs(1,
                SynthesisInput.newBuilder().setAttributeId(WOOD).setRatio(Double.MAX_VALUE)).build();
        var tinyInputRegistry = registry(catalog(hugeInput));
        assertThrows(IllegalArgumentException.class, () -> tinyInputRegistry.toOrganRecipe(
                hugeInput, "consumer:batch", 1, "consumer:reactor", 2, 1, 1));
    }
}
