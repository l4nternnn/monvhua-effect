package com.kuilunfuzhe.monvhua.features.area_tip;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Reads actual optional local archives without initializing Axiom, Minecraft, or native ImGui. */
class AxiomLocalJarAbiTest {
    @Test void installedModernAxiomHasBooleanRenderEntryPoint() throws Exception {
        verify("Axiom-6.1.3-for-MC1.21.8.jar", "Z");
    }
    @Test void legacyAxiomHasIntegerRenderEntryPoint() throws Exception {
        verify("Axiom-5.4.2-for-MC1.21.8.jar.disabled", "I");
    }
    private static void verify(String archive, String flag) throws Exception {
        Path path = Path.of("run", "mods", archive);
        assumeTrue(Files.isRegularFile(path), "Optional local Axiom fixture absent: " + path);
        try (var zip = new ZipFile(path.toFile())) {
            var selection = new ClassNode();
            try (var stream = zip.getInputStream(zip.getEntry("com/moulberry/axiom/clipboard/Selection.class"))) {
                new ClassReader(stream).accept(selection, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            String descriptor = "(Lcom/moulberry/axiom/render/AxiomWorldRenderContext;" + flag + ")V";
            assertTrue(selection.methods.stream().anyMatch(m -> m.name.equals("render") && m.desc.equals(descriptor)));
            System.out.println(archive + ": Selection.render" + descriptor + " verified");
        }
    }
}
