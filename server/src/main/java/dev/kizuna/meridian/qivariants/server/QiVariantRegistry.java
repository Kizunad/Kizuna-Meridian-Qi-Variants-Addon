package dev.kizuna.meridian.qivariants.server;

import dev.kizuna.meridian.core.DefinitionId;
import dev.kizuna.meridian.protocol.AddonProtobuf;
import dev.kizuna.meridian.protocol.pb.AddonMessages.ContentSymbolKind;
import dev.kizuna.meridian.protocol.pb.BodyMessages;
import dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages.SynthesisRecipe;
import dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages.VariantAttribute;
import dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages.VariantCatalog;
import dev.kizuna.meridian.server.AddonRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.random.RandomGenerator;

/** 校验后不可变的合成图；仅查询和生成配方，不执行加工或修改环境库存。 */
public final class QiVariantRegistry {
    private final VariantCatalog catalog;
    private final Map<String, VariantAttribute> attributes;
    private final Map<String, SynthesisRecipe> recipes;
    private final Map<String, List<SynthesisRecipe>> recipesByOutput;
    private final Map<String, Integer> tiers;
    private final List<String> topologicalOrder;

    private QiVariantRegistry(VariantCatalog catalog, Map<String, VariantAttribute> attributes,
                              Map<String, SynthesisRecipe> recipes,
                              Map<String, List<SynthesisRecipe>> recipesByOutput,
                              Map<String, Integer> tiers, List<String> topologicalOrder) {
        // 所有映射和嵌套列表均复制；调用方不能影响后续查询。
        this.catalog = catalog;
        this.attributes = Collections.unmodifiableMap(new TreeMap<>(attributes));
        this.recipes = Map.copyOf(recipes);
        var grouped = new HashMap<String, List<SynthesisRecipe>>();
        recipesByOutput.forEach((id, values) -> grouped.put(id, List.copyOf(values)));
        this.recipesByOutput = Map.copyOf(grouped);
        this.tiers = Map.copyOf(tiers);
        this.topologicalOrder = List.copyOf(topologicalOrder);
    }

    public static QiVariantRegistry load(AddonRegistry framework, VariantCatalog catalog) {
        // 结构与框架身份全部验证成功后，才交付整份目录。
        Objects.requireNonNull(framework, "framework");
        var registry = validate(catalog);
        for (String id : registry.attributes.keySet()) {
            QiVariantsErrors.require(framework.findSymbol(id)
                            .filter(symbol -> symbol.getKind() == ContentSymbolKind.QI_ATTRIBUTE).isPresent(),
                    "变体必须登记为框架 QI_ATTRIBUTE", id);
        }
        return registry;
    }

    static QiVariantRegistry validate(VariantCatalog catalog) {
        // 构建期也使用相同的结构校验；符号安装由公开 load 入口追加检查。
        Objects.requireNonNull(catalog, "catalog");
        QiVariantsErrors.require(catalog.getSchemaVersion() == QiVariantsContent.SCHEMA_VERSION,
                "不支持的真元变体目录版本", Integer.toString(catalog.getSchemaVersion()));
        var attributes = new TreeMap<String, VariantAttribute>();
        for (var attribute : catalog.getAttributesList()) {
            String id = attribute.getId();
            validateOwnedId(id);
            QiVariantsErrors.require(!attribute.getDisplayName().isBlank(), "展示名不能为空", id);
            QiVariantsErrors.require(attribute.hasColorRgb()
                            && Integer.toUnsignedLong(attribute.getColorRgb()) <= 0xFFFFFF,
                    "必须显式提供 24 位 RGB 颜色", id);
            QiVariantsErrors.require(attributes.putIfAbsent(id, attribute) == null, "属性 ID 重复", id);
        }
        var recipes = new TreeMap<String, SynthesisRecipe>();
        var grouped = new HashMap<String, List<SynthesisRecipe>>();
        for (var recipe : catalog.getRecipesList()) {
            validateRecipe(recipe, attributes);
            QiVariantsErrors.require(recipes.putIfAbsent(recipe.getId(), recipe) == null,
                    "合成配方 ID 重复", recipe.getId());
        }
        for (var recipe : recipes.values()) {
            grouped.computeIfAbsent(recipe.getOutputAttributeId(), ignored -> new ArrayList<>()).add(recipe);
        }
        var dependents = new HashMap<String, Set<String>>();
        var remaining = new HashMap<String, Integer>();
        var ready = new PriorityQueue<String>();
        for (String id : attributes.keySet()) {
            QiVariantsErrors.require(grouped.containsKey(id), "变体至少需要一条合成配方", id);
            var parents = new TreeSet<String>();
            for (var recipe : grouped.get(id)) {
                for (var input : recipe.getInputsList()) {
                    if (attributes.containsKey(input.getAttributeId())) {
                        parents.add(input.getAttributeId());
                    }
                }
            }
            remaining.put(id, parents.size());
            for (String parent : parents) {
                dependents.computeIfAbsent(parent, ignored -> new TreeSet<>()).add(id);
            }
            if (parents.isEmpty()) {
                ready.add(id);
            }
        }
        var tiers = new HashMap<String, Integer>();
        FiveElements.IDS.forEach(id -> tiers.put(id, 0));
        var order = new ArrayList<String>();
        while (!ready.isEmpty()) {
            String id = ready.remove();
            int tier = Integer.MAX_VALUE;
            for (var recipe : grouped.get(id)) {
                int inputTier = recipe.getInputsList().stream()
                        .mapToInt(input -> tiers.get(input.getAttributeId())).max().orElseThrow();
                tier = Math.min(tier, Math.addExact(inputTier, 1));
            }
            tiers.put(id, tier);
            order.add(id);
            for (String dependent : dependents.getOrDefault(id, Set.of())) {
                int count = remaining.compute(dependent, (key, value) -> value - 1);
                if (count == 0) {
                    ready.add(dependent);
                }
            }
        }
        QiVariantsErrors.require(order.size() == attributes.size(), "合成关系存在循环",
                new TreeSet<>(attributes.keySet().stream().filter(id -> !tiers.containsKey(id)).toList()).toString());
        return new QiVariantRegistry(catalog, attributes, recipes, grouped, tiers, order);
    }

    public VariantCatalog catalog() {
        // Protobuf 消息本身不可变；不暴露内部 Builder。
        return catalog;
    }

    public int tier(String attributeId) {
        // 五行是零阶；其他属性必须属于已校验目录。
        if (attributeId == null) {
            throw QiVariantsErrors.unknown(null);
        }
        Integer result = tiers.get(attributeId);
        if (result == null) {
            throw QiVariantsErrors.unknown(attributeId);
        }
        return result;
    }

    public List<SynthesisRecipe> recipesFor(String output) {
        // 五行没有合成配方；同一产物的多条路线按配方 ID 稳定排序。
        tier(output);
        return recipesByOutput.getOrDefault(output, List.of());
    }

    public Set<String> ancestors(String attributeId) {
        // 汇总全部合成路线的传递输入，不包含自身；迭代遍历避免深图栈溢出。
        tier(attributeId);
        var ancestors = new TreeSet<String>();
        var pending = new ArrayDeque<String>();
        pending.add(attributeId);
        while (!pending.isEmpty()) {
            for (var recipe : recipesFor(pending.removeFirst())) {
                for (var input : recipe.getInputsList()) {
                    if (ancestors.add(input.getAttributeId())) {
                        pending.addLast(input.getAttributeId());
                    }
                }
            }
        }
        return Collections.unmodifiableSet(ancestors);
    }

    public Map<String, Double> weights(Map<String, Double> regionShares, int maxTier) {
        // 先计算完整图，再筛阶归一化；较高阶的另一条路线也必须参与计算。
        Objects.requireNonNull(regionShares, "regionShares");
        QiVariantsErrors.require(maxTier >= 0, "最大阶数不能为负", Integer.toString(maxTier));
        var region = new TreeMap<String, Double>();
        for (var entry : regionShares.entrySet()) {
            tier(entry.getKey());
            Double value = entry.getValue();
            QiVariantsErrors.require(value != null && Double.isFinite(value) && value >= 0,
                    "区域数量或份额必须有限且非负", entry.getKey());
            region.put(entry.getKey(), value);
        }
        Map<String, Double> direct = normalize(region);
        var values = new TreeMap<String, Double>();
        FiveElements.IDS.forEach(id -> values.put(id, direct.getOrDefault(id, 0.0)));
        for (String id : topologicalOrder) {
            double weight = direct.getOrDefault(id, 0.0);
            for (var recipe : recipesByOutput.get(id)) {
                double product = 1;
                for (var input : recipe.getInputsList()) {
                    product *= values.get(input.getAttributeId());
                }
                weight = Math.max(weight, product);
            }
            values.put(id, weight);
        }
        values.entrySet().removeIf(entry -> tier(entry.getKey()) > maxTier || entry.getValue() == 0);
        return normalize(values);
    }

    public Optional<String> sample(Map<String, Double> regionShares, int maxTier, RandomGenerator random) {
        // 使用调用方随机源和稳定 ID 顺序；空分布不产生默认属性。
        Objects.requireNonNull(random, "random");
        Map<String, Double> probabilities = weights(regionShares, maxTier);
        if (probabilities.isEmpty()) {
            return Optional.empty();
        }
        double remaining = random.nextDouble();
        String last = null;
        for (var entry : probabilities.entrySet()) {
            last = entry.getKey();
            remaining -= entry.getValue();
            if (remaining < 0) {
                return Optional.of(last);
            }
        }
        // 累加舍入尾差归最后一个正权重属性；空分布仍返回 empty。
        return Optional.ofNullable(last);
    }

    public BodyMessages.OrganRecipe toOrganRecipe(SynthesisRecipe recipe, String organRecipeId,
                                                  int organRecipeVersion, String organKind,
                                                  double batchQi, long durationMillis, double density) {
        // 只转换本目录中的完整定义，避免用同 ID 的篡改消息绕过目录校验。
        Objects.requireNonNull(recipe, "recipe");
        QiVariantsErrors.require(recipe.equals(recipes.get(recipe.getId())),
                "合成配方不属于已校验目录", recipe.getId());
        return OrganRecipeConversion.convert(recipe, organRecipeId, organRecipeVersion, organKind,
                batchQi, durationMillis, density);
    }

    private static void validateOwnedId(String id) {
        // 框架保留整个 meridian 命名空间，附属不能覆盖内置或未来框架身份。
        new DefinitionId(id);
        QiVariantsErrors.require(!AddonProtobuf.namespaceOf(id).equals("meridian"),
                "不能定义框架保留命名空间中的内容", id);
    }

    private static void validateRecipe(SynthesisRecipe recipe, Map<String, VariantAttribute> attributes) {
        // 每一条路线都必须独立合法；存在其他可行路线也不能掩盖错误定义。
        String id = recipe.getId();
        validateOwnedId(id);
        QiVariantsErrors.require(recipe.getVersion() > 0, "合成配方版本必须为正整数", id);
        QiVariantsErrors.require(attributes.containsKey(recipe.getOutputAttributeId()),
                "产物必须是目录内的变体属性", id);
        QiVariantsErrors.require(recipe.getInputsCount() >= 2, "合成至少需要两种不同属性", id);
        var seen = new HashSet<String>();
        for (var input : recipe.getInputsList()) {
            String inputId = input.getAttributeId();
            QiVariantsErrors.require(FiveElements.IDS.contains(inputId) || attributes.containsKey(inputId),
                    "输入必须是五行或目录内的变体", id + " -> " + inputId);
            QiVariantsErrors.require(seen.add(inputId), "合成输入属性不能重复", id);
            QiVariantsErrors.require(Double.isFinite(input.getRatio()) && input.getRatio() > 0,
                    "输入比例必须正且有限", id);
        }
        QiVariantsErrors.require(recipe.hasLossRatio() && Double.isFinite(recipe.getLossRatio())
                        && recipe.getLossRatio() >= 0 && recipe.getLossRatio() < 1,
                "必须显式提供 [0,1) 内的损耗率", id);
        QiVariantsErrors.require(!recipe.hasMinimumRealm()
                        || (recipe.getMinimumRealmValue() > 0
                        && recipe.getMinimumRealm() != BodyMessages.Realm.UNRECOGNIZED),
                "最低境界必须是框架已知的非零枚举", id);
    }

    private static Map<String, Double> normalize(Map<String, Double> values) {
        // 缩放后再求和，避免有限的大数相加溢出；极小权重可能按 double 精度下溢为零。
        double maximum = values.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (maximum == 0) {
            return Map.of();
        }
        var scaled = new TreeMap<String, Double>();
        values.forEach((id, value) -> {
            double amount = value / maximum;
            if (amount > 0) {
                scaled.put(id, amount);
            }
        });
        double total = scaled.values().stream().mapToDouble(Double::doubleValue).sum();
        scaled.replaceAll((id, amount) -> amount / total);
        scaled.values().removeIf(amount -> amount == 0);
        return Collections.unmodifiableMap(scaled);
    }
}
