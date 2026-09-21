package com.kuilunfuzhe.monvhua.features.commandpanel;

import com.google.gson.*;
import com.kuilunfuzhe.monvhua.WitchRole;
import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItems;
import com.kuilunfuzhe.monvhua.network.commandpanel.PanelStatusS2C;
import com.kuilunfuzhe.monvhua.network.commandpanel.CommandPanelPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

public final class PanelStatusServer {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, PanelStatusS2C> LAST = new HashMap<>();
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static final Map<UUID, String> LAST_ROSTERS = new HashMap<>();
    private static final Map<UUID, Long> ROSTER_REVISIONS = new HashMap<>();
    private static final Set<String> ROLES = Set.of("ema","cero","nnk","leiya","sherry","yalisa","noa","anan","coco","milya","mago","hanna","mll","yuki","dream","Sunday","soft","Tide_tree","JKL","4NAN","perfect","Tsukiyo","Nihilum");
    private static final Set<String> CREATIVE_ONLY = Set.of("dream","Sunday","soft","Tide_tree","JKL","4NAN","perfect","Tsukiyo","Nihilum");
    private record View(long session, int request, String role, UUID target) {}

    private static boolean holdsPanel(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(CommandPanelItems.COMMAND_PANEL)
                || player.getOffHandStack().isOf(CommandPanelItems.COMMAND_PANEL);
    }

    public static void initialize() {
        PanelStatusS2C.register();
        CommandPanelPackets.PanelRosterS2C.register();
        CommandPanelPackets.PanelViewResultS2C.register();
        CommandPanelPackets.PanelViewC2S.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LAST.remove(handler.player.getUuid());
            VIEWS.remove(handler.player.getUuid());
            LAST_ROSTERS.remove(handler.player.getUuid());
            ROSTER_REVISIONS.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { LAST.clear(); VIEWS.clear(); LAST_ROSTERS.clear(); ROSTER_REVISIONS.clear(); });
        ServerPlayNetworking.registerGlobalReceiver(CommandPanelPackets.PanelViewC2S.ID, (packet, context) -> context.server().execute(() -> handleView(context.player(), packet)));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 2 != 0) return;
            for (var player : server.getPlayerManager().getPlayerList()) {
                if (!ServerPlayNetworking.canSend(player, PanelStatusS2C.ID)) continue;
                boolean holding = holdsPanel(player);
                View view = VIEWS.get(player.getUuid());
                if (!holding || view == null) continue;
                sendRosterIfChanged(player, view.session());
                ServerPlayerEntity target = view.target() == null ? null : server.getPlayerManager().getPlayer(view.target());
                if (view.target() != null && target == null) {
                    VIEWS.put(player.getUuid(), new View(view.session(), view.request(), "", null));
                    target = null;
                    view = VIEWS.get(player.getUuid());
                }
                if (target != null && view.target() != null && !view.role().isBlank() && !target.getCommandTags().contains(view.role())) {
                    VIEWS.put(player.getUuid(), new View(view.session(), view.request(), "", null));
                    target = null;
                    view = VIEWS.get(player.getUuid());
                }
                ServerPlayerEntity dataTarget = target == null ? player : target;
                var tags = new ArrayList<String>();
                var role = view.target() == null ? null : WitchRole.fromPlayer(dataTarget);
                if (view.target() != null && !view.role().isBlank()) tags.add(view.role());
                if (role != null) tags.add(role.id);
                if (view.target() != null) dataTarget.getCommandTags().stream().sorted().filter(t -> !tags.contains(t)).forEach(tags::add);
                String encoded = GSON.toJson(tags);
                if (encoded.length() > 8192) encoded = GSON.toJson(role == null ? List.of() : List.of(role.id));
                var objective = dataTarget.getScoreboard().getNullableObjective("monvhua");
                var scoreEntry = objective == null ? null : dataTarget.getScoreboard().getScore(dataTarget, objective);
                Integer score = scoreEntry == null ? null : scoreEntry.getScore();
                String hearts = dataTarget.hasStatusEffect(StatusEffects.POISON) ? "poisoned"
                        : dataTarget.hasStatusEffect(StatusEffects.WITHER) ? "withered"
                        : dataTarget.isFrozen() ? "frozen" : "normal";
                boolean hasTarget = view.target() != null;
                var packet = new PanelStatusS2C(view.session(), view.request(), view.role(), hasTarget, encoded, hasTarget ? target.getGameProfile().getName() : "无在线玩家", hasTarget && score != null,
                        score == null ? 0 : score, hasTarget ? target.getHealth() : 0, hasTarget ? target.getMaxHealth() : 0,
                        hasTarget ? target.getAbsorptionAmount() : 0, hasTarget && target.isDead(), hearts,
                        server.isHardcore(), hasTarget && target.hasStatusEffect(StatusEffects.REGENERATION));
                if (!packet.equals(LAST.get(player.getUuid())) || server.getTicks() % 20 == 0)
                    ServerPlayNetworking.send(player, packet);
                if (holding) LAST.put(player.getUuid(), packet);
                else LAST.remove(player.getUuid());
            }
        });
    }
    private static void handleView(ServerPlayerEntity viewer, CommandPanelPackets.PanelViewC2S packet) {
        if (!holdsPanel(viewer)) return;
        UUID viewerId = viewer.getUuid();
        View old = VIEWS.get(viewerId);
        byte operation = packet.operation();

        if (operation == 2) {
            if (old == null || packet.sessionId() != old.session() || packet.requestSeq() < old.request()) return;
            VIEWS.remove(viewerId);
            LAST.remove(viewerId);
            LAST_ROSTERS.remove(viewerId);
            ROSTER_REVISIONS.remove(viewerId);
            return;
        }

        if (operation == 0) {
            if (packet.sessionId() == 0 || packet.requestSeq() < 0) return;
            // A new open always starts a new view session and must receive a fresh roster.
            LAST.remove(viewerId);
            LAST_ROSTERS.remove(viewerId);
            ROSTER_REVISIONS.remove(viewerId);
            View opened = new View(packet.sessionId(), packet.requestSeq(), "", null);
            VIEWS.put(viewerId, opened);
            sendRosterIfChanged(viewer, opened.session(), true);
            sendViewResult(viewer, opened, true);
            return;
        }

        if (operation != 1 || old == null || packet.sessionId() != old.session()
                || packet.requestSeq() <= old.request()) return;

        String role = packet.roleTag();
        UUID target = null;
        if (!packet.playerUuid().isBlank()) {
            try {
                target = UUID.fromString(packet.playerUuid());
            } catch (IllegalArgumentException ignored) {
                sendViewResult(viewer, old, false);
                return;
            }
        }
        ServerPlayerEntity candidate = target == null ? null : viewer.getServer().getPlayerManager().getPlayer(target);
        boolean onlineTarget = candidate != null && ROLES.contains(role) && candidate.getCommandTags().contains(role)
                && (!CREATIVE_ONLY.contains(role) || viewer.isCreative());
        boolean creativePreview = target == null && viewer.isCreative() && CREATIVE_ONLY.contains(role);
        if (!onlineTarget && !creativePreview) {
            sendViewResult(viewer, old, false);
            return;
        }

        View selected = new View(old.session(), packet.requestSeq(), role, target);
        VIEWS.put(viewerId, selected);
        sendViewResult(viewer, selected, true);
    }

    private static void sendRosterIfChanged(ServerPlayerEntity viewer, long session) {
        sendRosterIfChanged(viewer, session, false);
    }

    private static void sendRosterIfChanged(ServerPlayerEntity viewer, long session, boolean force) {
        String encoded = roster(viewer);
        if (!force && encoded.equals(LAST_ROSTERS.get(viewer.getUuid()))) return;
        LAST_ROSTERS.put(viewer.getUuid(), encoded);
        long revision = ROSTER_REVISIONS.merge(viewer.getUuid(), 1L, Long::sum);
        ServerPlayNetworking.send(viewer, new CommandPanelPackets.PanelRosterS2C(session, revision, encoded));
    }

    private static void sendViewResult(ServerPlayerEntity viewer, View view, boolean accepted) {
        ServerPlayNetworking.send(viewer, new CommandPanelPackets.PanelViewResultS2C(
                view.session(), view.request(), accepted, accepted ? view.role() : "", accepted && view.target() != null ? view.target().toString() : ""));
    }
    private static String roster(ServerPlayerEntity viewer) {
        JsonArray array = new JsonArray();
        for (ServerPlayerEntity p : viewer.getServer().getPlayerManager().getPlayerList()) for (String role : ROLES.stream().sorted().toList())
            if (p.getCommandTags().contains(role) && (!CREATIVE_ONLY.contains(role) || viewer.isCreative())) {
                JsonObject e = new JsonObject(); e.addProperty("role", role); e.addProperty("uuid", p.getUuidAsString()); e.addProperty("name", p.getGameProfile().getName()); array.add(e);
            }
        if (viewer.isCreative()) for (String role : CREATIVE_ONLY.stream().sorted().toList()) if (array.toString().indexOf("\"role\":\""+role+"\"") < 0) {
            JsonObject e = new JsonObject(); e.addProperty("role", role); e.addProperty("uuid", ""); e.addProperty("name", "无在线玩家"); array.add(e);
        }
        return array.toString();
    }
    private PanelStatusServer() {}
}
