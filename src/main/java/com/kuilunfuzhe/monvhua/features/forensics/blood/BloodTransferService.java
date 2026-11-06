package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

public final class BloodTransferService {
    public static final String ITEM_REF = "monvhua:blood_residue";
    private static final Map<UUID, Step> STEPS = new HashMap<>();
    private static final Map<UUID, Long> PICKUPS = new HashMap<>();
    private record Step(Vec3d position, String dimension, double distance, boolean left, long time) {}
    private BloodTransferService() {}
    public static boolean enabled() { return BloodRules.enabled() && ForensicConfig.current.transfer.enabled; }
    public static boolean generating() { return enabled() && BloodRules.generating() && ForensicConfig.current.transfer.contactTransfer; }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(s -> BloodTraceService.safe(() -> tick(s)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { STEPS.clear(); PICKUPS.clear(); });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> { STEPS.remove(h.player.getUuid()); PICKUPS.remove(h.player.getUuid()); });
    }
    public static String holder(ServerPlayerEntity p, String slot) {
        return ForensicStore.get(p.getServer()).life(p.getUuid()).getString("id", "") + "/" + slot;
    }
    public static String itemRef(ItemStack stack) { return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getString(ITEM_REF, ""); }
    public static boolean stained(ItemStack stack) { return !itemRef(stack).isEmpty(); }
    public static String prepareWeapon(ServerPlayerEntity p, ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxCount() != 1) return "";
        String id = itemRef(stack); if (id.isEmpty()) id = UUID.randomUUID().toString();
        if (!BloodTransferStore.get(p.getServer()).reserve(id)) return "";
        NbtCompound data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        data.putString(ITEM_REF, id); stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data)); return id;
    }
    public static void deposit(MinecraftServer server, String event, String source, int amount) {
        if (!generating() || amount <= 0) return;
        long now = BloodTraceService.now(server);
        BloodTransferStore.get(server).add(event, source, amount, now + ForensicConfig.current.transfer.wetTicks, now);
    }
    public static void contact(ItemDisplayEntity display, ServerPlayerEntity player) {
        if (!generating() || player == null || !(display.getWorld() instanceof ServerWorld world)) return;
        var b = Forensics.binding(display.getItemStack());
        if (!Forensics.valid(world, display.getItemStack(), display.getUuid().toString())) return;
        var fs = ForensicStore.get(player.getServer()); var corpse = fs.corpse(b.getString("id", ""));
        var ledger = corpse.getCompoundOrEmpty("blood"); String part = b.getString("part", "all");
        long now = BloodTraceService.now(player.getServer());
        if (!BloodRules.active(ledger, now)) return;
        var store = BloodTransferStore.get(player.getServer());
        for (String slot : List.of("hands", "torso")) {
            String target = holder(player, slot);
            if (BloodRules.remaining(ledger, part) <= 0 || store.visible(target) >= 8) continue;
            if (store.add(target, corpse.getString("id", "mixed"), 1,
                    Math.min(now + ForensicConfig.current.transfer.wetTicks, ledger.getLong("pool_start", now) + ForensicConfig.current.blood.poolTicks), now)) {
                BloodRules.consume(ledger, part, 1); fs.markDirty();
            }
        }
    }
    public static List<String> inspect(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        if (!enabled() || !viewer.isAlive() || viewer.isSpectator() || viewer.getWorld() != target.getWorld()
                || viewer.squaredDistanceTo(target) > ForensicConfig.current.inspectionDistance * ForensicConfig.current.inspectionDistance) return List.of();
        var world = (ServerWorld)viewer.getWorld();
        if (viewer != target && world.raycast(new net.minecraft.world.RaycastContext(viewer.getEyePos(), target.getEyePos(),
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, viewer)).getType() != net.minecraft.util.hit.HitResult.Type.MISS) return List.of();
        List<String> lines = new ArrayList<>(); var store = BloodTransferStore.get(viewer.getServer());
        String[] slots = {"hands", "torso", "left_sole", "right_sole"}, labels = {"手部", "衣物", "左鞋底", "右鞋底"};
        for (int i = 0; i < slots.length; i++) if (store.visible(holder(target, slots[i])) > 0) lines.add(labels[i] + "存在血液沾染，不能仅凭此确定接触方式");
        {
            if (store.visible(itemRef(target.getMainHandStack())) > 0) lines.add("主手物品存在血液残留");
            if (store.visible(itemRef(target.getOffHandStack())) > 0) lines.add("副手物品存在血液残留");
        }
        return lines.isEmpty() ? List.of("未发现可检查的血液沾染") : lines;
    }
    private static void tick(MinecraftServer server) {
        if (!enabled()) { STEPS.clear(); return; }
        var config = ForensicConfig.current.transfer;
        if (server.getTicks() % config.sampleTicks != 0) return;
        long now = BloodTraceService.now(server); var store = BloodTransferStore.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive() || player.isSpectator()) { STEPS.remove(player.getUuid()); continue; }
            ServerWorld world = (ServerWorld)player.getWorld(); Vec3d pos = player.getPos(); String dimension = world.getRegistryKey().getValue().toString();
            Step previous = STEPS.get(player.getUuid());
            if (previous == null || !previous.dimension.equals(dimension) || previous.position.distanceTo(pos) > config.discontinuityDistance) {
                STEPS.put(player.getUuid(), new Step(pos, dimension, 0, true, now)); continue;
            }
            double distance = previous.distance + previous.position.subtract(pos).horizontalLength(); boolean left = previous.left;
            if (!player.isOnGround() || player.hasVehicle() || !BodyHitGeometry.normalGravity(com.kuilunfuzhe.monvhua.features.gravity.GravityMagic.getSurfaceGravityDirection(player))) {
                STEPS.put(player.getUuid(), new Step(pos, dimension, 0, left, now)); continue;
            }
            if (generating()) {
                var landing = BloodSurfaceProjection.cast(world, pos.add(0, .15, 0), pos.add(0, -.25, 0), player);
                if (landing != null && landing.getSide() == Direction.UP) {
                    BloodTraceService.validate(world, landing.getBlockPos());
                    if (now - PICKUPS.getOrDefault(player.getUuid(), now - 20) >= 20) {
                        for (var entry : BloodTraceStore.get(world).at(landing.getBlockPos()).stream().limit(8).toList()) {
                            if (entry.patch().kind() == 3 || entry.patch().face() != Direction.UP || !entry.patch().covers(landing.getPos())) continue;
                            if (store.transfer(entry.event(), holder(player, left ? "left_sole" : "right_sole"), 2, config.soleCapacity, now) > 0) {
                                PICKUPS.put(player.getUuid(), now); break;
                            }
                        }
                    }
                    if (config.footprints && distance >= config.stepDistance && now - previous.time >= 5) {
                        String sole = holder(player, left ? "left_sole" : "right_sole");
                        if (store.available(sole, now) > 0) {
                            Vec3d delta = pos.subtract(previous.position);
                            double angle = Math.atan2(delta.z, delta.x);
                            long seed = ((long)Math.round((angle + Math.PI) / (2 * Math.PI) * 65535) & 65535L) | (left ? 1L << 16 : 0);
                            Vec3d foot = pos.add(-Math.sin(angle) * (left ? .12 : -.12), 0, Math.cos(angle) * (left ? .12 : -.12));
                            var footHit = BloodSurfaceProjection.cast(world, foot.add(0, .15, 0), foot.add(0, -.25, 0), player);
                            if (footHit == null || footHit.getSide() != Direction.UP) {
                                STEPS.put(player.getUuid(), new Step(pos, dimension, 0, !left, now)); continue;
                            }
                            BloodPatch base = BloodSurfaceProjection.impact(footHit, .16f, seed);
                            BloodPatch patch = new BloodPatch(base.id(), base.pos(), base.face(), base.u(), base.v(), base.radius(), 3, seed, 1);
                            if (BloodTraceStore.get(world).submit(new BloodTraceStore.Entry(patch, store.sourceSummary(sole, now), UUID.randomUUID().toString(), world.getBlockState(patch.pos()).toString(), true))) store.consume(sole, 1, now);
                        }
                        distance = 0; left = !left;
                        STEPS.put(player.getUuid(), new Step(pos, dimension, distance, left, now)); continue;
                    }
                }
            }
            // Short-lived particles are a model-independent proximity hint; the authoritative residue remains persistent.
            if (server.getTicks() % 40 == 0 && (store.visible(holder(player, "torso")) > 0 || store.visible(holder(player, "hands")) > 0
                    || store.visible(itemRef(player.getMainHandStack())) > 0))
                world.spawnParticles(new net.minecraft.particle.DustParticleEffect(0x800b12, .6f), pos.x, pos.y + 1, pos.z, 2, .15, .2, .15, 0);
            STEPS.put(player.getUuid(), new Step(pos, dimension, Math.min(distance, 3), left, previous.time));
        }
    }
}
