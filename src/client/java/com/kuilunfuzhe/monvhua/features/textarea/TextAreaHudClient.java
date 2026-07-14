package com.kuilunfuzhe.monvhua.features.textarea;

import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipClient;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TextAreaHudClient {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path COUNTS_PATH = FabricLoader.getInstance().getConfigDir().resolve("monvhua/text_area_playback_counts.json");
    private static final List<PlaybackSession> PLAYBACKS = new ArrayList<>();
    private static final Map<String, Integer> PASS_COUNTS = new HashMap<>();
    private static final Map<String, Integer> PLAY_COUNTS = new HashMap<>();
    private static final long PRESENCE_STALE_TICKS = 40L;
    private static TextAreaVolumeIndex areaIndex = TextAreaVolumeIndex.empty();
    private static UUID insideAreaId;
    private static BlockPos lastCheckedBlock;
    private static AreaTipClient.AreaView lastCheckedArea;
    private static long activeAreasRevision = -1L;
    private static long activeConfigRevision = -1L;
    private static long lastPresenceTick = -1L;
    private static Screen pendingEditorParent;
    private static boolean countsLoaded;

    private TextAreaHudClient() {
    }

    public static void initialize() {
        TextAreaResourceSyncClient.initialize();
        loadCounts();
        ClientCommandRegistrationCallback.EVENT.register(TextAreaHudClient::registerCommands);
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || TextGroupEditFragment.isActive()) {
                return;
            }
            renderPlaybackSessions(context, client);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingEditorParent != null && client.currentScreen == null) {
                Screen parent = pendingEditorParent;
                pendingEditorParent = null;
                TextGroupEditFragment.open(parent);
            }
            if (client.player == null || TextGroupEditFragment.isActive()) {
                return;
            }
            updateAreaPlayback(client);
        });
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, Object registryAccess) {
        dispatcher.register(ClientCommandManager.literal("monvhua-textarea-reset_文字区域重置")
                .executes(context -> {
                    resetPlaybackCounts();
                    context.getSource().sendFeedback(Text.literal("§a文字区域播放次数已刷新"));
                    return 1;
                }));
        dispatcher.register(ClientCommandManager.literal("monvhua-text-reset_文字重置")
                .executes(context -> {
                    resetPlaybackCounts();
                    context.getSource().sendFeedback(Text.literal("§a文字区域播放次数已刷新"));
                    return 1;
                }));
    }

    public static void openEditorAfterWorldFrame(Screen parent) {
        AreaTipClient.requestSync();
        pendingEditorParent = parent;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(null));
    }

    public static void resetPlayback() {
        PLAYBACKS.clear();
        resetPresence();
        activeConfigRevision = AreaTipClient.configRevision();
    }

    public static void resetPlaybackCounts() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            PASS_COUNTS.clear();
            PLAY_COUNTS.clear();
        } else {
            String prefix = client.player.getUuid().toString() + ":";
            PASS_COUNTS.keySet().removeIf(key -> key.startsWith(prefix));
            PLAY_COUNTS.keySet().removeIf(key -> key.startsWith(prefix));
        }
        saveCounts();
        resetPlayback();
    }

    private static void updateAreaPlayback(MinecraftClient client) {
        if (client.world == null) {
            PLAYBACKS.clear();
            resetPresence();
            return;
        }
        if (activeConfigRevision != AreaTipClient.configRevision()) {
            resetPlayback();
        }
        refreshAreaIndex();
        long now = client.world.getTime();
        boolean stalePresence = lastPresenceTick >= 0L && now - lastPresenceTick > PRESENCE_STALE_TICKS;
        if (stalePresence) {
            insideAreaId = null;
            lastCheckedBlock = null;
            lastCheckedArea = null;
        }
        lastPresenceTick = now;

        AreaTipClient.AreaView inside = currentArea(client);
        if (inside == null) {
            insideAreaId = null;
            return;
        }
        if (!inside.id().equals(insideAreaId)) {
            insideAreaId = inside.id();
            startPlayback(client, inside);
        }
    }

    private static AreaTipClient.AreaView currentArea(MinecraftClient client) {
        BlockPos playerBlock = client.player.getBlockPos();
        if (playerBlock.equals(lastCheckedBlock)) {
            return lastCheckedArea;
        }
        lastCheckedBlock = playerBlock.toImmutable();
        lastCheckedArea = areaIndex.areaAt(playerBlock).orElse(null);
        return lastCheckedArea;
    }

    private static void refreshAreaIndex() {
        long areasRevision = AreaTipClient.areasRevision();
        if (activeAreasRevision == areasRevision) {
            return;
        }
        areaIndex = TextAreaVolumeIndex.build(AreaTipClient.areas());
        activeAreasRevision = areasRevision;
        lastCheckedBlock = null;
        lastCheckedArea = null;
    }

    private static void resetPresence() {
        insideAreaId = null;
        lastCheckedBlock = null;
        lastCheckedArea = null;
        lastPresenceTick = -1L;
    }

    private static void startPlayback(MinecraftClient client, AreaTipClient.AreaView area) {
        AreaTipConfig.GroupConfig group = AreaTipConfig.getInstance().findGroup(area.groupId()).orElse(null);
        if (group == null || !group.hudVisible || group.hudTexts == null || group.hudTexts.isEmpty()) {
            return;
        }
        Set<String> entryIds = new HashSet<>();
        boolean changedCounts = false;
        for (AreaTipConfig.HudTextEntry entry : group.hudTexts) {
            if (entry == null || entry.id == null || entry.id.isBlank()) {
                continue;
            }
            String countKey = countKey(client, entry.id);
            int passCount = PASS_COUNTS.merge(countKey, 1, Integer::sum);
            changedCounts = true;
            if (passCount <= entry.passDelayCount) {
                continue;
            }
            int played = PLAY_COUNTS.getOrDefault(countKey, 0);
            if (entry.playOncePerPlayer && played > 0) {
                continue;
            }
            if (entry.playLimit > 0 && played >= entry.playLimit) {
                continue;
            }
            entryIds.add(entry.id);
            PLAY_COUNTS.put(countKey, played + 1);
            changedCounts = true;
        }
        if (changedCounts) {
            saveCounts();
        }
        if (!entryIds.isEmpty()) {
            PLAYBACKS.add(new PlaybackSession(area.id(), area.groupId(), client.world.getTime(), Set.copyOf(entryIds)));
        }
    }

    private static void renderPlaybackSessions(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {
        if (client.world == null || PLAYBACKS.isEmpty()) {
            return;
        }
        long now = client.world.getTime();
        Iterator<PlaybackSession> iterator = PLAYBACKS.iterator();
        while (iterator.hasNext()) {
            PlaybackSession session = iterator.next();
            AreaTipConfig.GroupConfig group = AreaTipConfig.getInstance().findGroup(session.groupId()).orElse(null);
            if (group == null || group.hudTexts == null) {
                iterator.remove();
                continue;
            }
            long elapsedTicks = Math.max(0L, now - session.startTick());
            long duration = playbackDuration(group, session.entryIds());
            if (duration <= 0L || elapsedTicks > duration) {
                iterator.remove();
                continue;
            }
            if (group.hudVisible) {
                TextGroupRenderer.renderGroup(context, group, false, false, elapsedTicks, session.entryIds());
            }
        }
    }

    private static long playbackDuration(AreaTipConfig.GroupConfig group, Set<String> entryIds) {
        long duration = 0L;
        for (AreaTipConfig.HudTextEntry entry : group.hudTexts) {
            if (entry != null && entryIds.contains(entry.id)) {
                duration = Math.max(duration, TextGroupRenderer.playbackTicks(entry));
            }
        }
        return duration;
    }

    private static String countKey(MinecraftClient client, String entryId) {
        String playerId = client.player == null ? "unknown" : client.player.getUuid().toString();
        return playerId + ":" + entryId;
    }

    private static void loadCounts() {
        if (countsLoaded) {
            return;
        }
        countsLoaded = true;
        if (!Files.isRegularFile(COUNTS_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(COUNTS_PATH, StandardCharsets.UTF_8)) {
            CountFile file = GSON.fromJson(reader, CountFile.class);
            if (file != null) {
                if (file.passCounts != null) {
                    PASS_COUNTS.putAll(file.passCounts);
                }
                if (file.playCounts != null) {
                    PLAY_COUNTS.putAll(file.playCounts);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void saveCounts() {
        try {
            Files.createDirectories(COUNTS_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(COUNTS_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(new CountFile(PASS_COUNTS, PLAY_COUNTS), writer);
            }
        } catch (IOException ignored) {
        }
    }

    private record PlaybackSession(UUID areaId, UUID groupId, long startTick, Set<String> entryIds) {
    }

    private record CountFile(Map<String, Integer> passCounts, Map<String, Integer> playCounts) {
    }
}
