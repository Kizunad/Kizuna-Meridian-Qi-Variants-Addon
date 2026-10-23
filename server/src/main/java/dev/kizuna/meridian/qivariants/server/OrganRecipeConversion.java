package dev.kizuna.meridian.qivariants.server;

import dev.kizuna.meridian.core.DefinitionId;
import dev.kizuna.meridian.core.OrganRecipe;
import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.core.QiInventory;
import dev.kizuna.meridian.core.Realm;
import dev.kizuna.meridian.protocol.CapabilityProtobuf;
import dev.kizuna.meridian.protocol.pb.BodyMessages;
import dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages.SynthesisInput;
import dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages.SynthesisRecipe;

import java.util.ArrayList;
import java.util.List;

/** 将已校验的合成定义转换为固定剂量配方；不安装配方或操作器官。 */
final class OrganRecipeConversion {
    private OrganRecipeConversion() {
        // 配方转换不保存宿主或目录状态。
    }

    static BodyMessages.OrganRecipe convert(SynthesisRecipe recipe, String id, int version,
                                            String organKind, double batchQi,
                                            long durationMillis, double density) {
        // 最大比例缩放防止求和溢出，数量和相态的最终约束复用框架构造器。
        QiVariantsErrors.require(Double.isFinite(batchQi) && batchQi > 0,
                "输入批量必须正且有限", recipe.getId());
        double maximum = recipe.getInputsList().stream().mapToDouble(SynthesisInput::getRatio).max().orElseThrow();
        double total = recipe.getInputsList().stream().mapToDouble(input -> input.getRatio() / maximum).sum();
        var entries = new ArrayList<Qi>();
        for (var input : recipe.getInputsList()) {
            double amount = batchQi * ((input.getRatio() / maximum) / total);
            QiVariantsErrors.require(amount > 0 && Double.isFinite(amount),
                    "输入剂量超出 double 可表示范围", recipe.getId());
            entries.add(new Qi(Qi.QiAttribute.of(input.getAttributeId()), amount, density));
        }
        var inputs = new QiInventory(entries);
        // 以实际可表示的输入合计计算产物，避免零损耗时因舍入制造真元。
        double outputAmount = inputs.totalQi() * (1 - recipe.getLossRatio());
        QiVariantsErrors.require(outputAmount > 0
                        && (recipe.getLossRatio() == 0 || outputAmount < inputs.totalQi()),
                "产物或损耗超出 double 可表示精度", recipe.getId());
        var outputs = new QiInventory(List.of(new Qi(Qi.QiAttribute.of(recipe.getOutputAttributeId()),
                outputAmount, density)));
        Realm minimumRealm = recipe.hasMinimumRealm()
                ? Realm.valueOf(recipe.getMinimumRealm().name()) : Realm.WANDERING_QI;
        return CapabilityProtobuf.toRecipe(new OrganRecipe(new DefinitionId(id), version,
                new DefinitionId(organKind), inputs, outputs, durationMillis, minimumRealm));
    }
}
