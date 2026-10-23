package dev.kizuna.meridian.qivariants.demo;

import dev.kizuna.meridian.protocol.pb.AddonMessages.AddonHostProfile;
import dev.kizuna.meridian.protocol.pb.AddonMessages.AddonRegistration;
import dev.kizuna.meridian.qivariants.server.QiVariantRegistry;
import dev.kizuna.meridian.qivariants.server.QiVariantsContent;
import dev.kizuna.meridian.server.AddonRegistry;

import java.util.Map;
import java.util.Random;

/** 演示正式目录安装、两阶权重及器官配方转换，不接入游戏宿主。 */
public final class QiVariantsDemo {
    private QiVariantsDemo() {
        // 演示入口不属于生产运行库。
    }

    public static void main(String[] args) {
        // 安装真实内容包，查询只使用宿主提供的普通 Java 数据。
        var registration = AddonRegistration.newBuilder().setProtocolVersion(1)
                .setHost(AddonHostProfile.newBuilder().setHostId("meridian:java_reference").setApiVersion(1))
                .addBundles(QiVariantsContent.bundle()).build();
        var variants = QiVariantRegistry.load(AddonRegistry.load(registration), QiVariantsContent.catalog());
        var region = Map.of("meridian:metal", 1.0, "meridian:wood", 1.0, "meridian:fire", 1.0);
        System.out.println("已安装变体数：" + variants.catalog().getAttributesCount());
        System.out.println("枯的阶数：" + variants.tier("qi_variants:withering"));
        System.out.println("凋零的祖先：" + variants.ancestors("qi_variants:wither"));
        System.out.println("游炁候选概率：" + variants.weights(region, 1));
        System.out.println("真炁候选概率：" + variants.weights(region, 2));
        System.out.println("固定种子抽取：" + variants.sample(region, 2, new Random(42)).orElse("无可用属性"));
        var recipe = variants.toOrganRecipe(variants.recipesFor("qi_variants:withering").get(0),
                "demo:withering_batch", 1, "demo:reactor", 2, 1000, .1);
        System.out.println("器官配方输入：" + recipe.getInput().getEntriesList().stream()
                .map(qi -> qi.getAttribute() + "=" + qi.getAmount()).toList());
        System.out.println("产物：" + recipe.getOutput().getEntries(0).getAmount()
                + " 元；差额由框架作为无属性游离真元释放");
    }
}
