package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.UUID;

/** Actual optional Carpet bytecode, without remote profile lookups or existing saves. */
final class CarpetDeathGameChecks {
    private CarpetDeathGameChecks() {}
    static void run(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var killed = player(server, "KilledBot", true, true);
                int before = bodies();
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), "kill KilledBot");
                require(bodies() == before + 1, "/kill Carpet bot did not create exactly one body");
                require(!ForensicStore.get(server).life(killed.getUuid()).getString("corpse", "").isEmpty(), "Missing frozen death");
                killed.kill(server.getOverworld());
                require(bodies() == before + 1, "Repeated bot /kill duplicated body");
                var damaged = player(server, "DamageBot", true, true);
                damaged.setHealth(1);
                before = bodies();
                boolean applied = damaged.damage(server.getOverworld(), damaged.getDamageSources().genericKill(), 1000);
                require(bodies() == before + 1, "Actual Carpet damage death did not create exactly one body: applied="
                        + applied + " health=" + damaged.getHealth() + " alive=" + damaged.isAlive());
                var removed = player(server, "RemovedBot", true, true);
                before = bodies();
                try { removed.getClass().getMethod("kill", Text.class).invoke(removed, Text.literal("Test removal")); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                require(bodies() == before, "Administrative bot removal created a body");
                var untagged = player(server, "UntaggedBot", true, false);
                untagged.kill(server.getOverworld());
                require(bodies() == before, "Untagged bot created a body");
                var real = player(server, "DisconnectTest", false, true);
                real.setHealth(0);
                real.networkHandler.onDisconnected(new DisconnectionInfo(Text.literal("Test disconnect")));
                require(bodies() == before, "Real-player disconnect created a body");
                boolean enabled = ForensicConfig.current.enabled;
                try {
                    ForensicConfig.current.enabled = false;
                    var cosmetic = player(server, "CosmeticBot", true, true);
                    cosmetic.kill(server.getOverworld());
                    cosmetic.kill(server.getOverworld());
                    require(bodies() == before + 1, "Disabled-forensics bot /kill duplicated or lost cosmetic body");
                } finally { ForensicConfig.current.enabled = enabled; }
            });
            context.waitTicks(20);
            world.getServer().runOnServer(server -> require(bodies() == 3, "Delayed disconnections changed body count"));
        }
        try {
            Files.writeString(FabricLoader.getInstance().getGameDir().resolve("carpet-death-pass.txt"),
                    "PASS: real Carpet /kill, damage death, repeated kill, administrative removal, untagged bot, real-player zero-health disconnect, disabled-forensics deduplication; delayed disconnects unchanged.\n");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    private static int bodies() { return CorpseLoadedIndex.snapshot().size(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static ServerPlayerEntity player(MinecraftServer server, String name, boolean fake, boolean tagged) {
        try {
            var profile = new GameProfile(UUID.randomUUID(), name);
            var options = SyncedClientOptions.createDefault();
            ServerPlayerEntity player;
            if (fake) {
                var constructor = Class.forName("carpet.patches.EntityPlayerMPFake").getDeclaredConstructor(
                        MinecraftServer.class, ServerWorld.class, GameProfile.class, SyncedClientOptions.class, boolean.class);
                constructor.setAccessible(true);
                player = (ServerPlayerEntity) constructor.newInstance(server, server.getOverworld(), profile, options, false);
            } else player = new ServerPlayerEntity(server, server.getOverworld(), profile, options);
            var connection = (ClientConnection) Class.forName("carpet.patches.FakeClientConnection")
                    .getConstructor(NetworkSide.class).newInstance(NetworkSide.SERVERBOUND);
            server.getPlayerManager().onPlayerConnect(connection, player, new ConnectedClientData(profile, 0, options, false));
            player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            player.setLoaded(true); // No client exists to send the usual load-complete acknowledgement.
            player.refreshPositionAndAngles(.5, 100, .5, 0, 0);
            if (tagged) player.addCommandTag("dead_body");
            return player;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
