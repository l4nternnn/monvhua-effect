package com.kuilunfuzhe.monvhua.features.forensics.physics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

/** Check the actual supplied third-party ABI without starting Minecraft or loading intermediary classes. */
class KomDyingAbiTest {
    @Test void supplied122JarHasTheLifecycleAndRescueMethodsWeHook() throws Exception {
        Path path=Path.of("run/mods/kom_dying-1.2.2.jar");Assumptions.assumeTrue(Files.isRegularFile(path));
        try(var jar=new JarFile(path.toFile())) {
            var manager=read(jar,"com/example/dying/DyingManager");
            check(manager,"startDyingState","(Lnet/minecraft/class_3222;Lnet/minecraft/class_3222;Lnet/minecraft/server/MinecraftServer;)V");
            check(manager,"startPostCancelCountdown","(Lnet/minecraft/class_3222;Lcom/example/dying/DyingState;)V");
            check(manager,"finishDyingByRescue","(Lnet/minecraft/class_3222;)V");
            check(manager,"clearDyingStateForDebug","(Lnet/minecraft/class_3222;)V");
            check(manager,"isPlayerInActiveDyingState","(Ljava/util/UUID;)Z");
            check(manager,"isPlayerDying","(Ljava/util/UUID;)Z");
            check(manager,"prepareDyingPlayerDeath","(Lnet/minecraft/class_3222;)V");
            check(read(jar,"com/example/dying/RescueManager"),"startRescue","(Lnet/minecraft/class_3222;Lnet/minecraft/class_3222;Lnet/minecraft/server/MinecraftServer;)V");
        }
    }
    private static ClassNode read(JarFile jar,String name)throws Exception{
        var node=new ClassNode();try(var input=jar.getInputStream(jar.getJarEntry(name+".class"))){new ClassReader(input).accept(node,ClassReader.SKIP_CODE);}return node;
    }
    private static void check(ClassNode node,String name,String descriptor){
        assertTrue(node.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(descriptor)&&(m.access&Opcodes.ACC_STATIC)!=0),node.name+"."+name+descriptor);
    }
}
