package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import com.kuilunfuzhe.monvhua.network.forensics.BloodTracePackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import org.slf4j.LoggerFactory;
import java.util.*;

public final class BloodTraceService {
    private static final Set<ItemDisplayEntity> SOURCES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long lastError;
    private static long lastCapacityWarning;
    private static int validationCursor;
    private BloodTraceService() {}
    public static long now(MinecraftServer server) { return server.getOverworld().getTime(); }
    public static void safe(Runnable work) {
        try { work.run(); } catch (RuntimeException error) {
            long time = System.nanoTime();
            if (time - lastError > 5_000_000_000L) { lastError = time; LoggerFactory.getLogger("monvhua-blood").error("Blood effect failed; normal combat/corpse processing continues", error); }
        }
    }
    public static void initialize() {
        BloodTracePackets.register();
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> { if (entity instanceof ItemDisplayEntity display) track(display); });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> { if (entity instanceof ItemDisplayEntity display) SOURCES.remove(display); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SOURCES.clear(); validationCursor = 0; lastError = 0; lastCapacityWarning = 0; });
        ServerTickEvents.END_SERVER_TICK.register(server -> safe(() -> {
            if (BloodRules.generating() && server.getTicks() % 10 == 0) {
                for (ItemDisplayEntity display : List.copyOf(SOURCES)) {
                    if (display.isRemoved()) { SOURCES.remove(display); continue; }
                    safe(() -> pool(display));
                }
            }
            if (server.getTicks() % 20 == 0) for (ServerWorld world : server.getWorlds()) {
                var store = BloodTraceStore.get(world); List<Long> keys = new ArrayList<>(store.chunkKeys()); Collections.sort(keys);
                if (!keys.isEmpty()) {
                    long key = keys.get(Math.floorMod(validationCursor, keys.size())); ChunkPos chunk = new ChunkPos(key);
                    if (world.isChunkLoaded(chunk.x, chunk.z)) store.chunk(key).stream().map(e -> e.patch().pos()).distinct().toList().forEach(p -> validate(world, p));
                }
            }
            if (server.getTicks() % 20 == 0) validationCursor++;
            BloodTracePackets.tick(server);
        }));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var target = CommandManager.argument("to", BlockPosArgumentType.blockPos());
            for (boolean preview : new boolean[]{true, false}) {
                target.then(CommandManager.literal(preview ? "preview" : "confirm").executes(c -> clear(
                        c.getSource().getWorld(), BlockPosArgumentType.getBlockPos(c, "from"),
                        BlockPosArgumentType.getBlockPos(c, "to"), preview, c.getSource())));
            }
            dispatcher.register(CommandManager.literal("monvhua-blood").requires(s -> s.hasPermissionLevel(2))
                    .then(CommandManager.literal("clear").then(CommandManager.argument("from", BlockPosArgumentType.blockPos()).then(target))));
        });
    }

    private static int clear(ServerWorld world, BlockPos from, BlockPos to, boolean preview, net.minecraft.server.command.ServerCommandSource source) {
        int count = BloodTraceStore.get(world).clear(from, to, preview);
        source.sendFeedback(() -> Text.literal((preview ? "将清除" : "已清除") + count + " 个血迹补丁"), false); return count;
    }
    public static void track(ItemDisplayEntity display) { if (Forensics.hasBinding(display.getItemStack())) SOURCES.add(display); }
    public static void validate(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return;
        var store = BloodTraceStore.get(world);
        for (var entry : List.copyOf(store.at(pos))) if (!BloodSurfaceProjection.supported(world, pos)
                || !world.getBlockState(pos).toString().equals(entry.support())) store.remove(entry.patch().id());
    }
    public static void blockChanged(ServerWorld world, BlockPos pos) { safe(() -> validate(world, pos)); }
    private static boolean submit(ServerWorld world, BloodTraceStore.Entry entry) {
        boolean accepted = BloodTraceStore.get(world).submit(entry);
        if (!accepted && System.nanoTime() - lastCapacityWarning > 30_000_000_000L) {
            lastCapacityWarning = System.nanoTime();
            LoggerFactory.getLogger("monvhua-blood").warn("Blood trace capacity reached or stale update rejected in {}; existing evidence retained", world.getRegistryKey().getValue());
        }
        return accepted;
    }
    public static void hit(ServerPlayerEntity victim, Entity attacker, Entity direct, boolean melee, WeaponProfiles.Selection selection, NbtCompound hit) {
        safe(() -> impact(victim, attacker, direct, melee, selection, hit));
    }
    private static void impact(ServerPlayerEntity victim, Entity attacker, Entity direct, boolean melee, WeaponProfiles.Selection selection, NbtCompound hit) {
        if (!BloodRules.generating()) return;
        var forensic = ForensicStore.get(victim.getServer()); NbtCompound life = forensic.life(victim.getUuid());
        NbtCompound ledger = BloodRules.ledger(life);
        double factor = BloodRules.factor(selection, hit.getString("kinds", "unknown"));
        hit.putInt("blood_schema", 1); hit.putBoolean("external_bleeding", factor > 0); hit.putDouble("bleeding_factor", factor);
        hit.putString("impact_precision", "unknown"); hit.putString("direction_precision", "unknown");
        if (factor <= 0) { forensic.markDirty(); return; }
        long time = now(victim.getServer());
        if (ledger.getString("last_hit", "").equals(hit.getString("id", ""))) return;
        ledger.putString("last_hit", hit.getString("id", ""));
        BloodRules.remember(ledger, hit, time); forensic.markDirty();
        if (!BodyHitGeometry.normalGravity(GravityMagic.getSurfaceGravityDirection(victim))) return;
        Vec3d origin = victim.getPos().add(0, victim.getHeight() * .55, 0);
        Vec3d direction = Vec3d.ZERO;
        if (direct instanceof ProjectileEntity projectile) {
            direction = projectile instanceof ProjectileEvidence evidence ? evidence.monvhua$impactVelocity() : projectile.getVelocity();
            var point = victim.getBoundingBox().raycast(projectile.getPos(), projectile.getPos().add(direction));
            if (point.isPresent()) { origin = point.get(); hit.putString("impact_precision", "approximate"); }
        } else if (melee && attacker != null) {
            var point = victim.getBoundingBox().raycast(attacker.getEyePos(), attacker.getEyePos().add(attacker.getRotationVec(1).multiply(6)));
            if (point.isPresent()) { origin = point.get(); hit.putString("impact_precision", "approximate"); }
            direction = origin.subtract(attacker.getEyePos());
        }
        if (!Double.isFinite(direction.lengthSquared()) || direction.lengthSquared() < .0001) return;
        direction = direction.normalize();
        hit.putDouble("impact_x", origin.x); hit.putDouble("impact_y", origin.y); hit.putDouble("impact_z", origin.z);
        hit.putDouble("travel_x", direction.x); hit.putDouble("travel_y", direction.y); hit.putDouble("travel_z", direction.z);
        hit.putString("direction_precision", "approximate");
        int cost = BloodRules.impactCost(hit.getFloat("amount", 0), factor, ledger.getInt("impact_remaining", 0));
        if (cost == 0) return;
        ServerWorld world = (ServerWorld) victim.getWorld(); long seed = new Random().nextLong(); Random random = new Random(seed);
        hit.putLong("blood_seed", seed); hit.putInt("blood_rule_version", 1);
        String event = hit.getString("id", ""); int landed = 0;
        int rays = Math.min(12, Math.max(3, cost));
        for (int i = 0; i < rays && landed < Math.min(8, cost); i++) {
            Vec3d ray = direction.add((random.nextDouble() - .5) * .9, (random.nextDouble() - .5) * .9, (random.nextDouble() - .5) * .9).normalize();
            var landing = BloodSurfaceProjection.cast(world, origin, origin.add(ray.multiply(3)), victim);
            if (landing == null) continue;
            BloodPatch patch = BloodSurfaceProjection.impact(landing, .035f + cost * .003f, random.nextLong());
            if (submit(world, new BloodTraceStore.Entry(patch, life.getString("id", ""), event, world.getBlockState(patch.pos()).toString(), true))) landed++;
        }
        if (landed > 0) ledger.putInt("impact_remaining", Math.max(0, ledger.getInt("impact_remaining", 0) - Math.min(cost, landed * 3)));
        forensic.markDirty();
    }
    public static void death(ServerPlayerEntity player, String corpse) {
        safe(() -> {
            if (!BloodRules.generating()) return;
            var store = ForensicStore.get(player.getServer());
            BloodRules.freeze(store.corpse(corpse), now(player.getServer()), BodyHitGeometry.normalGravity(GravityMagic.getSurfaceGravityDirection(player)));
            store.markDirty();
        });
    }
    private static void pool(ItemDisplayEntity display) {
        if (!(display.getWorld() instanceof ServerWorld world) || world.getEntity(display.getUuid()) != display
                || !world.isChunkLoaded(display.getBlockPos()) || !Forensics.valid(world, display.getItemStack(), display.getUuid().toString())) return;
        NbtCompound binding = Forensics.binding(display.getItemStack());
        String id = binding.getString("id", ""), part = binding.getString("part", "all");
        var forensic = ForensicStore.get(world.getServer()); NbtCompound corpse = forensic.corpse(id);
        NbtCompound ledger = corpse.getCompoundOrEmpty("blood");
        if (!BloodRules.active(ledger, now(world.getServer())) || BloodRules.remaining(ledger, part) <= 0) { SOURCES.remove(display); return; }
        // A complete carrier takes precedence; a staged split cannot spend the same shares.
        if (!part.equals("all") && !corpse.getCompoundOrEmpty("carriers").getCompoundOrEmpty("all").isEmpty()) return;
        var landing = BloodSurfaceProjection.cast(world, display.getPos().add(0, .05, 0), display.getPos().add(0, -1.25, 0), display);
        if (landing == null || landing.getSide() != Direction.UP) return;
        BlockPos anchor = landing.getBlockPos();
        String siteKey = world.getRegistryKey().getValue() + "/" + part + "/" + anchor.asLong();
        NbtCompound sites = ledger.getCompoundOrEmpty("sites");
        if (!sites.contains(siteKey) && sites.getKeys().size() >= 128) return;
        NbtCompound site = sites.getCompoundOrEmpty(siteKey);
        int used = site.getInt("used", 0), cost = Math.min(4, Math.min(40 - used, BloodRules.remaining(ledger, part)));
        if (cost <= 0) return;
        if (!site.contains("event")) {
            site.putString("event", UUID.randomUUID().toString()); site.putLong("seed", new Random().nextLong());
            site.putDouble("x", landing.getPos().x); site.putDouble("z", landing.getPos().z);
        }
        float radius = (float) (.12 + .73 * Math.sqrt((used + cost) / 40.0));
        boolean wrote = false;
        int revision = site.getInt("revision", 0) + 1;
        for (BlockPos pos : BloodSurfaceProjection.poolSupports(world, anchor)) {
            float u = (float) (site.getDouble("x", anchor.getX() + .5) - pos.getX());
            float v = (float) (site.getDouble("z", anchor.getZ() + .5) - pos.getZ());
            if (u + radius < 0 || u - radius > 1 || v + radius < 0 || v - radius > 1) continue;
            String patchKey = "patch_" + pos.asLong();
            String patchId = site.getString(patchKey, "");
            if (patchId.isEmpty()) { patchId = UUID.randomUUID().toString(); site.putString(patchKey, patchId); }
            BloodPatch patch = new BloodPatch(patchId, pos, Direction.UP, u, v, radius, 1, site.getLong("seed", 0), revision);
            wrote |= submit(world, new BloodTraceStore.Entry(patch, id, site.getString("event", ""), world.getBlockState(pos).toString(), false));
        }
        if (wrote) { BloodRules.consume(ledger, part, cost); site.putInt("used", used + cost); site.putInt("revision", revision); }
        sites.put(siteKey, site); ledger.put("sites", sites); forensic.markDirty();
    }
}
