package dev.kizuna.meridian.qivariants.server;

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import dev.kizuna.meridian.protocol.AddonProtobuf;
import dev.kizuna.meridian.qivariants.protocol.pb.QiVariantsMessages.VariantCatalog;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class ArtifactContractTest {
    @Test
    public void serverJarContainsMatchingContentPackAndMetadata() throws Exception {
        // 验证分发资源，防止仅内存 Builder 正确而最终 JAR 漏包。
        assertEquals(1, Collections.list(getClass().getClassLoader()
                .getResources("dev/kizuna/meridian/protocol/pb/BodyMessages.class")).size());
        try (var jar = new ZipFile(System.getProperty("qiVariants.serverJar"))) {
            var bundle = AddonProtobuf.decodeBundle(read(jar, QiVariantsContent.BUNDLE_RESOURCE));
            assertEquals(QiVariantsContent.bundle(), bundle);
            var catalog = VariantCatalog.parseFrom(read(jar, QiVariantsContent.CATALOG_RESOURCE));
            assertEquals(QiVariantsContent.catalog(), catalog);
            assertEquals(catalog.getAttributesList().stream().map(attribute -> attribute.getId()).sorted().toList(),
                    bundle.getSymbolsList().stream().map(symbol -> symbol.getId()).sorted().toList());
            assertTrue(bundle.getSymbolsList().stream().allMatch(symbol -> symbol.getKind()
                    == dev.kizuna.meridian.protocol.pb.AddonMessages.ContentSymbolKind.QI_ATTRIBUTE));
            assertNotNull(jar.getEntry(QiVariantsContent.SOURCE_RESOURCE));
            assertNull(jar.getEntry("dev/kizuna/meridian/qivariants/tooling/ExportQiVariantsBundle.class"));
            assertNull(jar.getEntry("dev/kizuna/meridian/qivariants/demo/QiVariantsDemo.class"));
            assertNotNull(jar.getEntry("LICENSE"));
        }
    }

    @Test
    public void sharedJarShipsSchemaDescriptorAndMessagesWithoutServerClasses() throws Exception {
        // 客户端仅取 shared，即可解析展示字段，无需服务端或 Minecraft 实现。
        try (var jar = new ZipFile(System.getProperty("qiVariants.sharedJar"))) {
            String schema = new String(read(jar, "kizuna/meridian/qi_variants/v1/qi_variants.proto"),
                    StandardCharsets.UTF_8);
            assertTrue(schema.contains("display_name"));
            assertTrue(schema.contains("color_rgb"));
            var descriptors = FileDescriptorSet.parseFrom(read(jar,
                    "META-INF/kizuna_meridian/protocol/qi-variants-v1.desc"));
            assertTrue(descriptors.getFileList().stream().anyMatch(file ->
                    file.getPackage().equals("kizuna.meridian.qi_variants.v1")));
            assertTrue(descriptors.getFileList().stream().anyMatch(file ->
                    file.getName().equals("kizuna/meridian/v1/body.proto")));
            assertNotNull(jar.getEntry("dev/kizuna/meridian/qivariants/protocol/pb/QiVariantsMessages.class"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().contains("/server/")
                    || entry.getName().startsWith("net/minecraft/")
                    || entry.getName().startsWith("dev/kizuna/meridian/protocol/pb/")));
            assertNotNull(jar.getEntry("LICENSE"));
        }
    }

    private static byte[] read(ZipFile jar, String path) throws Exception {
        // 缺失条目先给出可定位断言，随后关闭读取流。
        var entry = jar.getEntry(path);
        assertNotNull(path, entry);
        try (var stream = jar.getInputStream(entry)) {
            return stream.readAllBytes();
        }
    }
}
