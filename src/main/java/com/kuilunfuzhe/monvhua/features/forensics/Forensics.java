package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.network.forensics.ForensicPackets;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import java.util.*;

public final class Forensics {
    public static final String BINDING = "monvhua:corpse";
    private static final String LINK = "monvhua_corpse_display:";
    public static final List<String> PARTS = List.of("head", "torso", "left_arm", "right_arm", "left_leg", "right_leg", "unknown");

    public static void initialize() {
        ForensicConfig.load();
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new WeaponProfiles());
        ForensicPackets.register();
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            if (!alive) ForensicStore.get(player.getServer()).newLife(player.getUuid());
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player.isAlive()) ForensicStore.get(server).ensureLivingLife(handler.player.getUuid());
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ProjectileEvidence projectile) projectile.monvhua$getEvidence();
            if (entity instanceof ItemDisplayEntity display) restoreInventory(display);
            if (entity instanceof InteractionEntity) {
                for (String tag : entity.getCommandTags()) if (tag.startsWith(LINK)) {
                    try {
                        UUID display = UUID.fromString(tag.substring(LINK.length()));
                        BodyPartManager.INTERACTION_TO_DISPLAY.put(entity.getUuid(), display);
                        BodyPartManager.DISPLAY_TO_INTERACTION.put(display, entity.getUuid());
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ItemDisplayEntity display) persistInventory(display);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            for (ServerWorld world : server.getWorlds()) for (UUID id : List.copyOf(BodyPartManager.BODY_PART_DISPLAY_INVENTORIES.keySet()))
                if (world.getEntity(id) instanceof ItemDisplayEntity display && hasBinding(display.getItemStack())) persistInventory(display);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerWorld world : server.getWorlds()) for (UUID id : List.copyOf(BodyPartManager.BODY_PART_DISPLAY_INVENTORIES.keySet()))
                if (world.getEntity(id) instanceof ItemDisplayEntity display) persistInventory(display);
        });
    }

    public static boolean enabled(ServerPlayerEntity player) {
        return ForensicConfig.current.enabled && (player.getCommandTags().contains("dead_body") || player.getCommandTags().contains("dead_part"));
    }

    public static void record(ServerPlayerEntity player, DamageSource source, float amount) {
        if (!enabled(player) || !(amount > 0) || !Float.isFinite(amount)) return;
        if (player.getHealth() > 0) ForensicStore.get(player.getServer()).ensureLivingLife(player.getUuid());
        NbtCompound hit = new NbtCompound();
        hit.putString("id", UUID.randomUUID().toString());
        hit.putLong("tick", player.getWorld().getTime());
        hit.putString("dimension", player.getWorld().getRegistryKey().getValue().toString());
        hit.putDouble("x", player.getX()); hit.putDouble("y", player.getY()); hit.putDouble("z", player.getZ());
        hit.putFloat("amount", amount);
        hit.putBoolean("potentially_lethal", amount >= player.getHealth());
        hit.putString("source", source.getTypeRegistryEntry().getKey().map(k -> k.getValue().toString()).orElse("unknown"));
        Entity attacker = source.getAttacker();
        Entity direct = source.getSource();
        if (attacker != null) hit.putString("attacker", attacker.getUuid().toString());
        String cause = source.getTypeRegistryEntry().getKey().map(k -> k.getValue().getNamespace().equals("minecraft") ? k.getValue().getPath() : "unknown").orElse("unknown");
        WeaponProfiles.Selection selection = null;
        boolean melee = (cause.equals("player_attack") || cause.equals("mob_attack") || cause.equals("mob_attack_no_aggro"))
                && direct == attacker && attacker instanceof LivingEntity;
        if (melee) {
            AttackContexts.Attack context = AttackContexts.current(attacker.getUuid());
            ItemStack weapon = context != null ? context.weapon() : ((LivingEntity) attacker).getWeaponStack();
            selection = context != null ? context.selection() : WeaponProfiles.select(weapon, "melee");
            if (weapon.isEmpty()) selection = new WeaponProfiles.Selection("unarmed", "strike", new WeaponProfiles.Mode("melee", List.of(new WeaponProfiles.Component("blunt", 1))));
            hit.putString("weapon", Registries.ITEM.getId(weapon.getItem()).toString());
            if (context != null) hit.putString("attack_id", context.id());
        }
        if (direct instanceof ProjectileEvidence projectile) {
            NbtCompound snapshot = projectile.monvhua$getEvidence();
            hit.put("projectile", snapshot.copy());
            selection = ProjectileEvidence.selection(snapshot);
        }
        if (selection != null) {
            hit.putString("profile", selection.profile());
            hit.putString("mode", selection.mode());
            NbtList components = new NbtList();
            for (var component : selection.definition().components()) {
                NbtCompound c = new NbtCompound(); c.putString("kind", component.kind()); c.putDouble("weight", component.weight()); components.add(c);
            }
            hit.put("components", components);
            hit.putString("kinds", String.join("+", selection.definition().components().stream().map(WeaponProfiles.Component::kind).toList()));
        } else hit.putString("kinds", fallback(cause));
        hit.putString("part", "unknown");
        hit.putString("direction", "unknown");
        hit.putString("precision", "unknown");
        // Only direct physical attacks have a meaningful attacker-relative direction.
        if (melee || direct instanceof ProjectileEntity) locate(player, attacker, direct, hit);
        ForensicStore.get(player.getServer()).append(player.getUuid(), hit);
    }

    public static String fallback(String cause) {
        return switch (cause) {
            case "arrow", "trident", "sting" -> "puncture";
            case "fall", "fly_into_wall", "falling_block", "falling_anvil", "falling_stalactite", "explosion", "player_explosion", "mob_attack", "mob_attack_no_aggro" -> "blunt";
            case "in_fire", "on_fire", "lava", "hot_floor", "fireball", "unattributed_fireball" -> "burn";
            case "drown", "in_wall", "out_of_world", "starve", "magic", "indirect_magic", "wither", "freeze" -> "other";
            default -> "unknown";
        };
    }

    private static void locate(ServerPlayerEntity victim, Entity attacker, Entity direct, NbtCompound hit) {
        Direction gravity = com.kuilunfuzhe.monvhua.features.gravity.GravityMagic.getSurfaceGravityDirection(victim);
        if (!BodyHitGeometry.normalGravity(gravity)) return;
        Vec3d relative = null;
        if (direct instanceof ProjectileEntity projectile && projectile.getVelocity().lengthSquared() > 0.0001)
            relative = projectile.getVelocity().negate();
        else if (attacker != null) relative = attacker.getPos().subtract(victim.getPos());
        if (relative != null && relative.horizontalLengthSquared() > 0.0001) {
            double yaw = Math.toRadians(victim.bodyYaw);
            double front = relative.x * -Math.sin(yaw) + relative.z * Math.cos(yaw);
            double right = relative.x * -Math.cos(yaw) + relative.z * -Math.sin(yaw);
            hit.putString("direction", Math.abs(front) >= Math.abs(right) ? (front >= 0 ? "front" : "back") : (right >= 0 ? "right" : "left"));
        }
        if (victim.getPose() != EntityPose.STANDING || victim.hasVehicle()) return;
        Vec3d from;
        Vec3d to;
        if (direct instanceof ProjectileEntity projectile) {
            from = projectile.getPos(); to = from.add(projectile.getVelocity());
        } else if (attacker != null) {
            from = attacker.getEyePos(); to = from.add(attacker.getRotationVec(1).multiply(6));
        } else return;
        var point = victim.getBoundingBox().raycast(from, to);
        if (point.isEmpty()) return;
        Vec3d p = point.get().subtract(victim.getPos());
        double height = p.y / victim.getHeight();
        double yaw = Math.toRadians(victim.bodyYaw);
        double right = p.x * -Math.cos(yaw) + p.z * -Math.sin(yaw);
        String part = BodyHitGeometry.part(height, right / victim.getWidth());
        hit.putString("part", part); hit.putString("precision", "approximate");
        hit.putDouble("local_right", right); hit.putDouble("local_y", p.y);
    }

    public static boolean beginDeath(ServerPlayerEntity player, DamageSource source) {
        if (!enabled(player)) return false;
        ForensicStore store = ForensicStore.get(player.getServer());
        NbtCompound life = store.life(player.getUuid());
        if (life.getBoolean("spawn_started", false)) return false;
        NbtList hits = life.getListOrEmpty("hits");
        if (!hits.isEmpty()) {
            NbtCompound last = hits.getCompound(hits.size() - 1).orElseThrow();
            String sourceId = source.getTypeRegistryEntry().getKey().map(k -> k.getValue().toString()).orElse("unknown");
            if (last.getLong("tick", -1) == player.getWorld().getTime() && last.getString("source", "").equals(sourceId)
                    && last.getBoolean("potentially_lethal", false)) {
                last.putBoolean("death_trigger", true);
                for (NbtElement entry : life.getListOrEmpty("inspection_hits"))
                    if (entry instanceof NbtCompound candidate && candidate.getString("id", "").equals(last.getString("id", "")))
                        candidate.putBoolean("death_trigger", true);
            }
        }
        store.freeze(player.getUuid(), player.getName().getString(), player.getWorld().getRegistryKey().getValue().toString(),
                player.getWorld().getTime(), player.getX(), player.getY(), player.getZ(), source.getName());
        life.putBoolean("spawn_started", true); store.markDirty();
        return true;
    }

    public static NbtCompound binding(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getCompoundOrEmpty(BINDING);
    }
    public static boolean hasBinding(ItemStack stack) { return !binding(stack).getString("id", "").isEmpty(); }
    public static void setBinding(ItemStack stack, NbtCompound binding) {
        NbtCompound root = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        root.put(BINDING, binding.copy()); stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }
    public static void attachDeath(ServerPlayerEntity player, ItemDisplayEntity display, String part) {
        if (display == null || !enabled(player)) return;
        String id = ForensicStore.get(player.getServer()).life(player.getUuid()).getString("corpse", "");
        if (id.isEmpty()) return;
        NbtCompound binding = new NbtCompound(); binding.putString("id", id); binding.putString("part", part);
        ItemStack stack = display.getItemStack().copy(); setBinding(stack, binding); display.setItemStack(stack);
        claim(display);
    }
    /** Called after every display/interaction creation, also makes legacy links reloadable. */
    public static void link(ItemDisplayEntity display, InteractionEntity interaction) {
        interaction.addCommandTag(LINK + display.getUuid());
    }
    public static void persistInventory(ItemDisplayEntity display) {
        var inventory = BodyPartManager.BODY_PART_DISPLAY_INVENTORIES.get(display.getUuid());
        if (inventory == null || !hasBinding(display.getItemStack())) return;
        NbtList list = new NbtList();
        for (ItemStack item : inventory) list.add(item.isEmpty() ? new NbtCompound()
                : ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, item).getOrThrow());
        ItemStack visual = display.getItemStack().copy();
        NbtCompound root = visual.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (list.equals(root.get("body_part_inv_items"))) return;
        root.put("body_part_inv_items", list); visual.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root)); display.setItemStack(visual);
    }
    private static void restoreInventory(ItemDisplayEntity display) {
        if (!hasBinding(display.getItemStack())) return;
        NbtCompound root = display.getItemStack().getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (!root.contains("body_part_inv_items")) return;
        var inventory = DefaultedList.ofSize(9, ItemStack.EMPTY);
        NbtList list = root.getListOrEmpty("body_part_inv_items");
        for (int i = 0; i < Math.min(9, list.size()); i++) {
            NbtCompound value = list.getCompound(i).orElseGet(NbtCompound::new);
            if (!value.isEmpty()) inventory.set(i, ItemStack.CODEC.parse(NbtOps.INSTANCE, value).result().orElse(ItemStack.EMPTY));
        }
        BodyPartManager.BODY_PART_DISPLAY_INVENTORIES.put(display.getUuid(), inventory);
    }
    public static void discardPair(ServerWorld world, ItemDisplayEntity display) {
        UUID interaction = BodyPartManager.DISPLAY_TO_INTERACTION.remove(display.getUuid());
        if (interaction != null) {
            BodyPartManager.INTERACTION_TO_DISPLAY.remove(interaction);
            Entity entity = world.getEntity(interaction); if (entity != null) entity.discard();
        }
        BodyPartManager.BODY_PART_DISPLAY_INVENTORIES.remove(display.getUuid()); display.discard();
    }
    public static boolean valid(ServerWorld world, ItemStack stack, String location) {
        NbtCompound b = binding(stack);
        if (!hasBinding(stack)) return true;
        return ForensicStore.get(world.getServer()).isCarrier(b.getString("id", ""), b.getString("part", "all"), b.getString("token", ""), location);
    }
    public static void claim(ItemDisplayEntity display) {
        if (!(display.getWorld() instanceof ServerWorld world) || !hasBinding(display.getItemStack())) return;
        transition(world, display.getItemStack(), display.getUuid().toString());
        display.setItemStack(display.getItemStack().copy());
    }
    public static void transition(ServerWorld world, ItemStack stack, String location) {
        NbtCompound b = binding(stack);
        String token = UUID.randomUUID().toString();
        if (ForensicStore.get(world.getServer()).claimCarrier(b.getString("id", ""), b.getString("part", "all"), token, location)) {
            b.putString("token", token); setBinding(stack, b);
        }
    }
    public static void retire(ServerWorld world, ItemStack stack) {
        NbtCompound b = binding(stack);
        ForensicStore.get(world.getServer()).retireCarrier(b.getString("id", ""), b.getString("part", "all"));
    }

    public static List<String> inspect(ServerPlayerEntity player, int entityId, String part) {
        if (!ForensicConfig.current.enabled || !player.isAlive() || player.isSpectator() || (!PARTS.contains(part) && !part.equals("__open"))) return List.of();
        ServerWorld world = (ServerWorld) player.getWorld();
        Entity entity = world.getEntityById(entityId);
        if (entity instanceof InteractionEntity) {
            UUID id = BodyPartManager.INTERACTION_TO_DISPLAY.get(entity.getUuid());
            entity = id == null ? null : world.getEntity(id);
        }
        if (!(entity instanceof ItemDisplayEntity display)) return List.of();
        Vec3d target = display.getPos();
        if (player.getEyePos().squaredDistanceTo(target) > ForensicConfig.current.inspectionDistance * ForensicConfig.current.inspectionDistance) return List.of();
        if (world.raycast(new RaycastContext(player.getEyePos(), target, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player)).getType() != HitResult.Type.MISS) return List.of();
        ItemStack stack = display.getItemStack();
        if (!hasBinding(stack)) return List.of("无可用伤情记录");
        if (!valid(world, stack, display.getUuid().toString())) return List.of();
        if (part.equals("__open")) return List.of("ready");
        NbtCompound b = binding(stack);
        if (!b.getString("part", "all").equals("all") && !b.getString("part", "").equals(part)) return List.of("此载体不包含所选身体部位");
        NbtCompound corpse = ForensicStore.get(player.getServer()).corpse(b.getString("id", ""));
        List<String> lines = new ArrayList<>();
        for (NbtCompound h : InspectionRecords.strongest(corpse.getListOrEmpty(corpse.contains("inspection_hits") ? "inspection_hits" : "hits"), part, 3)) {
            String kinds = h.getString("kinds", "unknown");
            String label = String.join("伴", Arrays.stream(kinds.split("\\+")).map(Forensics::kindLabel).toList());
            float amount = h.getFloat("amount", 0);
            String degree = amount < ForensicConfig.current.moderateThreshold ? "轻度" : amount < ForensicConfig.current.severeThreshold ? "中度" : "重度";
            String direction = switch (h.getString("direction", "unknown")) { case "front" -> "前侧"; case "back" -> "后侧"; case "left" -> "左侧"; case "right" -> "右侧"; default -> "不明"; };
            String line = label + "，" + degree + "；来向" + direction + (h.getString("precision", "unknown").equals("approximate") ? "（部位与方向为估算）" : "（部位无法定位）");
            if (kinds.equals("cut") || kinds.equals("puncture")) line += "；深度估计：" + (amount < ForensicConfig.current.moderateThreshold ? "浅表" : amount < ForensicConfig.current.severeThreshold ? "较深" : "深部");
            if (h.getBoolean("death_trigger", false)) line += "；疑似主要致命损伤";
            lines.add((lines.isEmpty() ? "最高伤害：" : "伤害记录 " + (lines.size() + 1) + "：") + line);
        }
        if (lines.isEmpty()) lines.add("该部位未记录到可定位伤情；不代表完全没有损伤");
        return lines;
    }
    public static String kindLabel(String kind) {
        return switch (kind) { case "cut" -> "切割伤"; case "puncture" -> "刺入伤"; case "blunt" -> "钝击伤"; case "burn" -> "烧伤"; case "other" -> "其他损伤（外伤特征不明）"; default -> "类型不明的损伤"; };
    }
}
