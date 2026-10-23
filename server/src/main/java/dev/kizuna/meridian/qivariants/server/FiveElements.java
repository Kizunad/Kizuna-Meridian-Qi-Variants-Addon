package dev.kizuna.meridian.qivariants.server;

import dev.kizuna.meridian.core.Qi;

import java.util.List;

/** 合成图唯一允许的根；身份来自框架，不包含无属性真元。 */
public final class FiveElements {
    public static final List<String> IDS = List.of(
            Qi.QiAttribute.METAL.id().value(), Qi.QiAttribute.WOOD.id().value(),
            Qi.QiAttribute.WATER.id().value(), Qi.QiAttribute.FIRE.id().value(),
            Qi.QiAttribute.EARTH.id().value());

    private FiveElements() {
        // 根身份只读共享，不另建可变登记表。
    }
}
