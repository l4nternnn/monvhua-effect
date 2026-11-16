package com.kuilunfuzhe.monvhua.features.forensics.physics;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

/** Validate hooks against the installed Minecraft bytecode without starting a server or applying Mixins. */
class TerminalHookAbiTest {
    @Test void spectatorHookTargetsTheSuccessfulServerPlayerTransition()throws Exception{
        method(read(ServerPlayerEntity.class),"changeGameMode","(Lnet/minecraft/world/GameMode;)Z");
    }
    @Test void disconnectAndCarpetRemovalHooksRunOnExistingAuthorityMethods()throws Exception{
        method(read(ServerPlayNetworkHandler.class),"onDisconnected","(Lnet/minecraft/network/DisconnectionInfo;)V");
        method(read(PlayerManager.class),"remove","(Lnet/minecraft/server/network/ServerPlayerEntity;)V");
    }
    private static ClassNode read(Class<?> type)throws Exception{
        var node=new ClassNode();try(var input=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){
            assertNotNull(input);new ClassReader(input).accept(node,ClassReader.SKIP_CODE);
        }return node;
    }
    private static void method(ClassNode node,String name,String descriptor){
        assertTrue(node.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(descriptor)&&(m.access&Opcodes.ACC_STATIC)==0),node.name+"."+name+descriptor);
    }
}
