package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceService;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryManager;
import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative entity carrying for evidence corpses. The evidence item is never rebuilt. */
public final class CorpseCarryService {
    private static final Map<UUID, Session> BY_CARRIER = new HashMap<>();
    private static final Map<UUID, UUID> BY_DISPLAY = new HashMap<>();
    private static final Map<UUID, ItemDisplayEntity> PENDING = new HashMap<>();
    private static final double FORWARD = 1.05;
    private static final double HEIGHT = 0.85;
    private static final double MAX_STEP = 0.65;
    private CorpseCarryService() {}

    private record Session(UUID carrier, UUID display, UUID interaction, String corpse, String part,
                           Vec3d lastSafe, String dimension, long poseRevision) {}

    public static void initialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemDisplayEntity display && Forensics.hasBinding(display.getItemStack()) && !isCarried(display)) {
                var b = Forensics.binding(display.getItemStack());
                if (ForensicStore.get(world.getServer()).corpse(b.getString("id", "")).contains("carry_recovery_" + b.getString("part", "all"))) PENDING.put(display.getUuid(), display);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            PENDING.remove(entity.getUuid());
            UUID carrier = BY_DISPLAY.get(entity.getUuid());
            Session s = carrier == null ? null : BY_CARRIER.get(carrier);
            if (s != null) {
                saveRecovery(world.getServer(), s);
                BY_DISPLAY.remove(s.display); BY_CARRIER.remove(carrier);
                if (entity instanceof ItemDisplayEntity d) BloodTraceService.endCarry(d, null);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(CorpseCarryService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session s = BY_CARRIER.get(handler.player.getUuid());
            if (s != null) release(server, s, s.lastSafe, true);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Session s : List.copyOf(BY_CARRIER.values())) release(server, s, s.lastSafe, true);
        });
        AttackEntityCallback.EVENT.register((p, w, h, e, hit) -> isCarrying(p.getUuid()) ? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { BY_CARRIER.clear(); BY_DISPLAY.clear(); PENDING.clear(); });
    }

    public static boolean isCarrying(UUID player) { return BY_CARRIER.containsKey(player)||com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseTransportController.carrying(player); }

    public static boolean isCarried(ItemDisplayEntity display) {
        return display != null && (BY_DISPLAY.containsKey(display.getUuid()) || PENDING.containsKey(display.getUuid()) || com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseTransportController.held(display.getUuid()));
    }

    public static String handle(ServerPlayerEntity player, int targetId, boolean release) {
        if(com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseTransportController.carrying(player.getUuid())){if(release){com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseTransportController.release(player.getUuid());return "已松开遗体";}return "请先放下遗体";}
        Session current = BY_CARRIER.get(player.getUuid());
        if (release) {
            if (current == null) return "你当前没有抱起遗体";
            release(player.getServer(), current, current.lastSafe, false);
            return PENDING.containsKey(current.display) ? "落点受阻，遗体等待安全恢复" : "已放下遗体";
        }
        if (current != null) {
            return "请使用放下遗体按钮或按键释放当前遗体";
        }
        if (!ForensicConfig.current.transfer.enabled || !ForensicConfig.current.transfer.carryEnabled) return "遗体搬运已停用";
        if (!com.kuilunfuzhe.monvhua.features.carryentity.CarryEffort.canMoveCorpse(player,
                com.kuilunfuzhe.monvhua.features.carryentity.CarryEffort.Mode.CARRY)) return "体力不足或不可用，无法抱起遗体";
        if (CarryManager.CARRIED_ENTITIES.containsKey(player) || CarryManager.CARRIED_BY.containsKey(player)
                || player.hasVehicle() || !BodyHitGeometry.normalGravity(GravityMagic.getSurfaceGravityDirection(player))) return "当前状态不能搬运遗体";
        Entity target = player.getWorld().getEntityById(targetId);
        if (target instanceof InteractionEntity interaction) {
            UUID displayId = BodyPartManager.INTERACTION_TO_DISPLAY.get(interaction.getUuid());
            target = displayId == null ? null : player.getWorld().getEntity(displayId);
        }
        if (!(target instanceof ItemDisplayEntity display) || !Forensics.hasBinding(display.getItemStack())) return "这不是可搬运的遗体";
        if (!(display.getWorld() instanceof ServerWorld world) || !Forensics.valid(world, display.getItemStack(), display.getUuid().toString())) return "遗体载体已失效";
        UUID linked = BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid());
        if (linked == null || !(world.getEntity(linked) instanceof InteractionEntity)) return "遗体交互载体尚未加载，请稍后重试";
        if(com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsCoordinator.pending(display))return "姿态正在生成，请稍候再搬运";
        if (isCarried(display) || BY_CARRIER.values().stream().anyMatch(s -> s.display.equals(display.getUuid()))) return "遗体正在被搬运";
        if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) return "需要空出双手";
        if (player.squaredDistanceTo(display) > ForensicConfig.current.inspectionDistance * ForensicConfig.current.inspectionDistance) return "离遗体太远";
        if (world.raycast(new net.minecraft.world.RaycastContext(player.getEyePos(), display.getPos(),
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, player)).getType()
                != net.minecraft.util.hit.HitResult.Type.MISS) return "遗体被遮挡";
        // The ground anchor can be inside the visual body's floor clearance. Validate the raised destination instead.
        if (!safeTarget(world, player, display, desired(player))) return "当前位置没有足够空间搬起遗体";
        for (ServerPlayerEntity viewer : world.getPlayers())
            if (viewer.currentScreenHandler instanceof com.kuilunfuzhe.monvhua.screen.BodyPartScreenHandler screen
                    && screen.views(BodyPartManager.BODY_PART_DISPLAY_INVENTORIES.get(display.getUuid()))) viewer.closeHandledScreen();
        var binding = Forensics.binding(display.getItemStack());
        Session session = new Session(player.getUuid(), display.getUuid(),
                BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid()), binding.getString("id", ""),
                binding.getString("part", "all"), display.getPos(), world.getRegistryKey().getValue().toString(), CorpsePoseRecord.data(display.getItemStack()).getLong("corpse_pose_revision",0));
        BY_CARRIER.put(player.getUuid(), session); BY_DISPLAY.put(display.getUuid(), player.getUuid());
        saveRecovery(player.getServer(), session);
        if(com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseResidentManager.contains(display.getUuid()))com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseResidentManager.remove(display.getUuid());
        BloodTraceService.beginCarry(display, player);
        move(world, player, display, session);
        return "已抱起遗体，移动时可能留下滴血";
    }

    /** Administrative deletion must not enqueue the removed body for carry recovery. */
    public static void forgetForCleanup(ItemDisplayEntity display) {
        var id=display.getUuid();var carrier=BY_DISPLAY.remove(id);
        var session=carrier==null?null:BY_CARRIER.remove(carrier);
        PENDING.remove(id);
        BloodTraceService.endCarry(display,session==null?null:display.getWorld().getServer().getPlayerManager().getPlayer(session.carrier));
        if(Forensics.hasBinding(display.getItemStack())){
            var store=ForensicStore.get(display.getWorld().getServer());var binding=Forensics.binding(display.getItemStack());
            store.corpse(binding.getString("id","")).remove("carry_recovery_"+binding.getString("part","all"));store.markDirty();
        }
    }
    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 == 0) for (ItemDisplayEntity display : List.copyOf(PENDING.values())) {
            if (display.isRemoved()) { PENDING.remove(display.getUuid()); continue; }
            recover((ServerWorld)display.getWorld(), display);
        }
        for (Session session : List.copyOf(BY_CARRIER.values())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.carrier);
            if (player == null || !(player.getWorld() instanceof ServerWorld world)
                    || !world.getRegistryKey().getValue().toString().equals(session.dimension)
                    || !player.isAlive() || player.isSpectator() || player.hasVehicle()
                    || !ForensicConfig.current.enabled || !ForensicConfig.current.transfer.enabled || !ForensicConfig.current.transfer.carryEnabled
                    || !BodyHitGeometry.normalGravity(GravityMagic.getSurfaceGravityDirection(player))) {
                release(server, session, session.lastSafe, true); continue;
            }
            Entity entity = world.getEntity(session.display);
            if (!(entity instanceof ItemDisplayEntity display) || display.isRemoved()) { release(server, session, session.lastSafe, true); continue; }
            if (!Forensics.valid(world, display.getItemStack(), display.getUuid().toString()) || player.squaredDistanceTo(display) > 25) {
                release(server, session, session.lastSafe, true); continue;
            }
            if (player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty()) {
                move(world, player, display, session);
            } else release(server, session, session.lastSafe, false);
        }
    }

    private static void move(ServerWorld world, ServerPlayerEntity player, ItemDisplayEntity display, Session session) {
        Vec3d desired = desired(player);
        Vec3d previous = display.getPos();
        Vec3d delta = desired.subtract(previous);
        if (delta.lengthSquared() > MAX_STEP * MAX_STEP) desired = previous.add(delta.normalize().multiply(MAX_STEP));
        if (!safeTarget(world, player, display, desired)) return;
        // Substeps prevent a narrow obstacle being skipped between two clear endpoints.
        for (double t = .2; t < 1; t += .2) if (!safeTarget(world, player, display, previous.lerp(desired, t))) return;
        UUID interactionId = session.interaction;
        if (interactionId != null && world.getEntity(interactionId) instanceof InteractionEntity interaction)
            interaction.setPosition(desired.add(interaction.getPos().subtract(previous)));
        display.setPosition(desired);
        BloodTraceService.moved(display, previous, desired, player);
        var landing = com.kuilunfuzhe.monvhua.features.forensics.blood.BloodSurfaceProjection.cast(world, desired, desired.add(0, -3, 0), display);
        if (landing != null && landing.getSide() == net.minecraft.util.math.Direction.UP) {
            Vec3d ground = landing.getPos().add(0, .05, 0);
            if(CorpsePoseRecord.present(display.getItemStack())) {
                var data=CorpsePoseRecord.data(display.getItemStack());
                double bottom=CorpsePoseGeometry.surfaces(display,data).stream().mapToDouble(s->CorpsePoseGeometry.bounds(s).minY).min().orElse(0);
                ground=landing.getPos().add(0,.01-bottom,0);
                if(!CorpsePoseGeometry.supported(world,display,data,ground))return;
            }
            if (safeTarget(world, player, display, ground)) {
                Session next = new Session(session.carrier, session.display, session.interaction, session.corpse, session.part, ground, session.dimension, session.poseRevision);
                BY_CARRIER.put(player.getUuid(), next); saveRecovery(world.getServer(), next);
            }
        }
    }

    private static Vec3d desired(ServerPlayerEntity player) {
        double yaw = Math.toRadians(player.getYaw());
        return player.getPos().add(-Math.sin(yaw) * FORWARD, HEIGHT, Math.cos(yaw) * FORWARD);
    }

    private static boolean safeTarget(ServerWorld world, ServerPlayerEntity carrier, ItemDisplayEntity display, Vec3d pos) {
        if(CorpsePoseRecord.present(display.getItemStack())) {
            var data=CorpsePoseRecord.data(display.getItemStack());
            return CorpsePoseRecord.read(data).isPresent()&&CorpsePoseGeometry.clear(world,display,data,pos);
        }
        Box box = new Box(pos.x - .75, pos.y + .01, pos.z - .75, pos.x + .75, pos.y + .55, pos.z + .75);
        return world.isChunkLoaded(net.minecraft.util.math.BlockPos.ofFloored(pos))
                && !world.getBlockCollisions(carrier, box).iterator().hasNext();
    }

    private static boolean supported(ItemDisplayEntity display,Vec3d pos) {
        return !CorpsePoseRecord.present(display.getItemStack()) || CorpsePoseGeometry.supported((ServerWorld)display.getWorld(),display,CorpsePoseRecord.data(display.getItemStack()),pos);
    }

    private static void release(MinecraftServer server, Session session, Vec3d preferred, boolean recovery) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.carrier);
        ServerWorld world = server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, net.minecraft.util.Identifier.of(session.dimension)));
        Entity entity = world == null ? null : world.getEntity(session.display);
        if (entity instanceof ItemDisplayEntity display) {
            Vec3d pos = preferred;
            if (CorpsePoseRecord.data(display.getItemStack()).getLong("corpse_pose_revision",0)!=session.poseRevision
                    || !safeTarget(world, player, display, pos) || !supported(display,pos)) {
                saveRecovery(server, session); PENDING.put(display.getUuid(), display);
                BY_CARRIER.remove(session.carrier); BY_DISPLAY.remove(session.display);
                BloodTraceService.endCarry(display, player);
                if (player != null) player.sendMessage(net.minecraft.text.Text.literal("安全落点受阻，遗体保留原位并等待恢复"), true);
                return;
            }
            if (session.interaction != null && world.getEntity(session.interaction) instanceof InteractionEntity interaction)
                interaction.setPosition(pos.add(interaction.getPos().subtract(display.getPos())));
            display.setPosition(pos);
            BloodTraceService.endCarry(display, player);
            ForensicStore.get(server).corpse(session.corpse).remove("carry_recovery_" + session.part);
            ForensicStore.get(server).markDirty();
        }
        BY_CARRIER.remove(session.carrier); BY_DISPLAY.remove(session.display);
        if (player != null && !recovery) player.sendMessage(net.minecraft.text.Text.literal("遗体已释放"), true);
    }

    private static void saveRecovery(MinecraftServer server, Session s) {
        NbtCompound n = new NbtCompound(); n.putString("display", s.display.toString()); n.putString("dimension", s.dimension);
        n.putLong("pose_revision",s.poseRevision);
        n.putDouble("x", s.lastSafe.x); n.putDouble("y", s.lastSafe.y); n.putDouble("z", s.lastSafe.z);
        ForensicStore.get(server).corpse(s.corpse).put("carry_recovery_" + s.part, n); ForensicStore.get(server).markDirty();
    }
    private static void recover(ServerWorld world, ItemDisplayEntity display) {
        if (!Forensics.valid(world, display.getItemStack(), display.getUuid().toString())) { PENDING.remove(display.getUuid()); return; }
        var b = Forensics.binding(display.getItemStack()); var fs = ForensicStore.get(world.getServer());
        var corpse = fs.corpse(b.getString("id", "")); String key = "carry_recovery_" + b.getString("part", "all");
        var n = corpse.getCompoundOrEmpty(key);
        if (!n.getString("display", "").equals(display.getUuid().toString())) { PENDING.remove(display.getUuid()); return; }
        if(n.getLong("pose_revision",0)!=CorpsePoseRecord.data(display.getItemStack()).getLong("corpse_pose_revision",0))return;
        Vec3d pos = new Vec3d(n.getDouble("x", display.getX()), n.getDouble("y", display.getY()), n.getDouble("z", display.getZ()));
        if (!safeTarget(world, null, display, pos) || !supported(display,pos)) return;
        UUID interactionId = BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid());
        if (interactionId == null || !(world.getEntity(interactionId) instanceof InteractionEntity interaction)) return;
        interaction.setPosition(pos.add(interaction.getPos().subtract(display.getPos())));
        display.setPosition(pos); corpse.remove(key); fs.markDirty(); PENDING.remove(display.getUuid()); BloodTraceService.track(display);
    }

}
