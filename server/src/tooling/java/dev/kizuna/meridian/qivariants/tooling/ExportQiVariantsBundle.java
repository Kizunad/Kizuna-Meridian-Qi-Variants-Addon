package dev.kizuna.meridian.qivariants.tooling;

import dev.kizuna.meridian.protocol.AddonProtobuf;
import dev.kizuna.meridian.qivariants.server.QiVariantsContent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 构建期从唯一文本源导出框架内容包与跨语言可读的合成目录。 */
public final class ExportQiVariantsBundle {
    private ExportQiVariantsBundle() {
        // 构建工具不进入公共运行库。
    }

    public static void main(String[] args) throws IOException {
        // 两份资源均由同一文本目录生成，避免属性登记与合成定义漂移。
        Path output = Path.of(args[0]);
        write(output.resolve(QiVariantsContent.BUNDLE_RESOURCE),
                AddonProtobuf.encodeBundle(QiVariantsContent.bundle()));
        write(output.resolve(QiVariantsContent.CATALOG_RESOURCE), QiVariantsContent.catalog().toByteArray());
    }

    private static void write(Path target, byte[] payload) throws IOException {
        // 只创建当前构建目录中的目标父目录。
        Files.createDirectories(target.getParent());
        Files.write(target, payload);
    }
}
