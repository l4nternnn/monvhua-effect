package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.network.forensics.ForensicPackets;
import com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceService;
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
        EvidenceAnchorNetwork.initialize();
        ForensicShowcaseCompat.initialize();
        PostmortemWounds.initialize();
        BloodTraceService.initialize();
        CorpseCarryService.initialize();
        CorpsePoseCommands.initialize();
        com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsCoordinator.initialize();
        com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTransferService.initialize();
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            if (!alive) ForensicStore.get(player.getServer()).newLife(player.getUuid());
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player.isAlive()) ForensicStore.get(server).ensureLivingLife(handler.player.getUuid());
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ProjectileEvidence projectile) projectile.monvhua$getEvidence();
            if (entity instanceof ItemDisplayEntity display) { com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseSeparationService.reconcile(display);if(!display.isRemoved()){restoreInventory(display); PostmortemWounds.sync(display);} }
            if (entity instanceof InteractionEntity) {
                for (String tag : entity.getCommandTags()) if (tag.startsWith(LINK)) {
                    try {
                        UUID display = UUID.fromString(tag.substring(LINK.length()));
                        var existing=BodyPartManager.DISPLAY_TO_INTERACTION.get(display);
                        if(existing!=null&&!existing.equals(entity.getUuid())&&world.getEntity(existing) instanceof InteractionEntity&&world.getEntity(display) instanceof ItemDisplayEntity d&&CorpsePoseRecord.present(d.getItemStack())){entity.discard();return;}
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
            for (ServerWorld w : server.getWorlds()) for (UUID id : List.copyOf(BodyPartManager.DISPLAY_TO_INTERACTION.keySet()))
                if (w.getEntity(id) instanceof ItemDisplayEntity body) CorpseGeometrySync.update(body);
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
        if (com.kuilunfuzhe.monvhua.features.forensics.physics.KomDyingCompat.syntheticDeath(player,source)) return;
        com.kuilunfuzhe.monvhua.features.forensics.physics.DeathMotionContext.accepted(player,source);
        if (player.getHealth() > 0) ForensicStore.get(player.getServer()).ensureLivingLife(player.getUuid());
        NbtCompound hit = new NbtCompound();
        hit.putString("id", UUID.randomUUID().toString());
        hit.putLong("tick", player.getWorld().getTime());
        hit.putString("dimension", player.getWorld().getRegistryKey().getValue().toString());
        hit.putDouble("x", player.getX()); hit.putDouble("y", player.getY()); hit.putDouble("z", player.getZ());
        hit.putFloat("amount", amount);
        hit.putString("severity", woundSeverity(amount));
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
        BloodTraceService.hit(player, attacker, direct, melee, selection, hit);
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
        NbtCompound surface = WoundGeometry.hit(WoundGeometry.local(from, victim.getPos(), victim.bodyYaw, victim.getScale()),
                WoundGeometry.local(to, victim.getPos(), victim.bodyYaw, victim.getScale()));
        if (surface.isEmpty()) return;
        hit.putString("part", surface.getString("part", "unknown"));
        hit.putString("precision", "approximate");
        hit.put("wound_surface", surface);

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
        String corpseId = store.freeze(player.getUuid(), player.getName().getString(), player.getWorld().getRegistryKey().getValue().toString(),
                player.getWorld().getTime(), player.getX(), player.getY(), player.getZ(), source.getName());
        com.kuilunfuzhe.monvhua.features.forensics.physics.DeathMotionContext.freeze(player,source,corpseId);
        BloodTraceService.death(player, corpseId);
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
    private static String woundSeverity(float amount) {
        return amount < ForensicConfig.current.moderateThreshold ? "mild"
                : amount < ForensicConfig.current.severeThreshold ? "moderate" : "severe";
    }

    /** Keep visual evidence independent of editable paint and corpse binding changes. */
    public static void copyWoundVisuals(ItemStack from, ItemStack to) {
        NbtCompound source = from.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        NbtCompound target = to.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if(source.contains(CorpsePoseRecord.KEY)) {
            if(!net.minecraft.util.Identifier.of("monvhua","combined_body").equals(to.get(DataComponentTypes.ITEM_MODEL)))
                throw new IllegalArgumentException("Cannot copy full rigid pose to a partial body");
            var pose=CorpsePoseRecord.read(source).orElseThrow(()->new IllegalArgumentException("Invalid rigid pose"));
            target.put(CorpsePoseRecord.KEY,pose.encode());target.putLong("corpse_pose_revision",source.getLong("corpse_pose_revision",pose.revision()));
        }
        for (String key : new String[]{"corpse_wounds", "corpse_scale"})
            if (source.contains(key)) target.put(key, source.get(key).copy());
        to.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(target));
    }
    public static void attachDeath(ServerPlayerEntity player, ItemDisplayEntity display, String part) {
        if (display == null || !enabled(player)) return;
        String id = ForensicStore.get(player.getServer()).life(player.getUuid()).getString("corpse", "");
        if (id.isEmpty()) return;
        NbtCompound binding = new NbtCompound(); binding.putString("id", id); binding.putString("part", part);
        ItemStack stack = display.getItemStack().copy(); setBinding(stack, binding);
        NbtCompound root = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        root.putFloat("corpse_scale", player.getScale());
        NbtList wounds = new NbtList();
        for (NbtElement element : ForensicStore.get(player.getServer()).corpse(id).getListOrEmpty("hits")) {
            if (!(element instanceof NbtCompound h) || !h.contains("wound_surface")) continue;
            NbtCompound visual = h.getCompoundOrEmpty("wound_surface").copy();
            visual.putString("kinds", h.getString("kinds", "unknown"));
            visual.putString("severity", h.getString("severity", woundSeverity(h.getFloat("amount", 0))));
            wounds.add(visual);
        }
        root.put("corpse_wounds", wounds);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root)); display.setItemStack(stack);
        claim(display);
        CorpseGeometrySync.update(display);
        com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsCoordinator.onDeath(player,display,part);
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
        PostmortemWounds.sync(display);
        BloodTraceService.track(display);
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
        return inspect(player,entityId,part,player.getEyePos());
    }
    public static List<String> inspect(ServerPlayerEntity player, int entityId, String part,Vec3d eye) {
        Vec3d rayDirection=player.getRotationVec(1);
        if(part.equals("__open"))CorpseInspectionContext.clear(player.getUuid());
        if (!ForensicConfig.current.enabled || !player.isAlive() || player.isSpectator() || (!PARTS.contains(part) && !part.equals("__open"))) return List.of();
        if(part.equals("__open")&&(CorpseCarryService.isCarrying(player.getUuid())||com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseGrabController.status(player.getUuid())!=null))return List.of("release");
        ServerWorld world = (ServerWorld) player.getWorld();
        Entity entity = world.getEntityById(entityId);
        // The broad interaction box can cover empty space or overlap another body. Select the
        // nearest actual posed surface for every inspection entry point, including sneak-use.
        if(part.equals("__open")) {
            var from=eye;var to=from.add(player.getRotationVec(1).multiply(ForensicConfig.current.inspectionDistance));
            double nearest=Double.POSITIVE_INFINITY;ItemDisplayEntity selected=null;
            for(var candidate:world.getEntitiesByClass(ItemDisplayEntity.class,new net.minecraft.util.math.Box(from,to).expand(2),d->hasBinding(d.getItemStack()))) {
                var hit=CorpsePoseGeometry.pick(candidate,from,to);
                if(hit!=null&&hit.position().squaredDistanceTo(from)<nearest){nearest=hit.position().squaredDistanceTo(from);selected=candidate;}
            }
            entity=selected;
        }
        if (entity instanceof InteractionEntity) {
            UUID id = BodyPartManager.INTERACTION_TO_DISPLAY.get(entity.getUuid());
            entity = id == null ? null : world.getEntity(id);
        }
        if (!(entity instanceof ItemDisplayEntity display)) return List.of();
        if(!part.equals("__open")){
            var selection=CorpseInspectionContext.find(player,display,part);
            if(selection==null)return List.of("本次部位选择已失效，请重新打开检查轮盘");
            eye=selection.eye();rayDirection=selection.direction();
        }
        if(com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsCoordinator.preparing(display))return List.of("姿态正在生成，请稍候再搜查");
        Vec3d target = display.getPos();
        if(CorpsePoseRecord.present(display.getItemStack())) {
            if(CorpsePoseRecord.read(CorpsePoseRecord.data(display.getItemStack())).isEmpty())return List.of("姿态版本不可用，请管理员检查");
            var hit=CorpsePoseGeometry.pick(display,eye,eye.add(rayDirection.multiply(ForensicConfig.current.inspectionDistance)));
            if(hit==null)return List.of("请瞄准实际尸体表面");
            if(!part.equals("__open")&&!part.equals(hit.part()))return List.of("请瞄准所选身体部位再搜查");
            target=hit.position();
        }
        if (player.getEyePos().squaredDistanceTo(target) > ForensicConfig.current.inspectionDistance * ForensicConfig.current.inspectionDistance) return List.of();
        if (world.raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player)).getType() != HitResult.Type.MISS) return List.of();
        ItemStack stack = display.getItemStack();
        if (!hasBinding(stack)) return List.of("无可用伤情记录");
        if (!valid(world, stack, display.getUuid().toString())) return List.of();
        if (part.equals("__open")) {
            String aimed=binding(stack).getString("part","all");
            if(aimed.equals("all")){
                var hit=CorpsePoseGeometry.pick(display,eye,eye.add(rayDirection.multiply(ForensicConfig.current.inspectionDistance)));
                if(hit==null)return List.of("请瞄准实际尸体表面");aimed=hit.part();
            }
            boolean blocked=CorpsePoseRecord.read(CorpsePoseRecord.data(stack)).map(p->p.parts().size()==1&&!p.parts().containsKey("torso")).orElse(false)||player.getCommandTags().contains("monvhua:no_corpse_drag")
                ||player.getMainHandStack().isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ITEM,net.minecraft.util.Identifier.of("monvhua","prevents_corpse_drag")))
                ||player.getOffHandStack().isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ITEM,net.minecraft.util.Identifier.of("monvhua","prevents_corpse_drag")));
            boolean detached=CorpsePoseRecord.read(CorpsePoseRecord.data(stack)).map(p->p.parts().size()==1&&!p.parts().containsKey("torso")).orElse(false);
            CorpseInspectionContext.remember(player,display,aimed,eye,rayDirection);
            return List.of("ready",aimed,detached?"pickup":blocked?"blocked":"allowed",Integer.toString(display.getId()));
        }
        NbtCompound b = binding(stack);
        if(CorpsePoseRecord.read(CorpsePoseRecord.data(stack)).map(p->!p.parts().containsKey(part)).orElse(false))return List.of("此载体不包含所选身体部位");
        if (!b.getString("part", "all").equals("all") && !b.getString("part", "").equals(part)) return List.of("此载体不包含所选身体部位");
        NbtCompound corpse = ForensicStore.get(player.getServer()).corpse(b.getString("id", ""));
        List<String> lines = new ArrayList<>();
        for (NbtCompound h : InspectionRecords.strongest(corpse.getListOrEmpty(corpse.contains("inspection_hits") ? "inspection_hits" : "hits"), part, 3)) {
            String kinds = h.getString("kinds", "unknown");
            String label = String.join("伴", Arrays.stream(kinds.split("\\+")).map(Forensics::kindLabel).toList());
            float amount = h.getFloat("amount", 0);
            String degree = amount < ForensicConfig.current.moderateThreshold ? "轻度" : amount < ForensicConfig.current.severeThreshold ? "中度" : "重度";
            String direction = switch (h.getString("direction", "unknown")) { case "front" -> "前侧"; case "back" -> "后侧"; case "left" -> "左侧"; case "right" -> "右侧"; default -> "不明"; };
            String line = "看到" + label + "，" + degree + "；判断来向为" + direction + (h.getString("precision", "unknown").equals("approximate") ? "（只能估算部位与方向）" : "（没有获得可靠的位置记录）");
            if (kinds.equals("cut") || kinds.equals("puncture")) line += "；估计深度为" + (amount < ForensicConfig.current.moderateThreshold ? "浅表" : amount < ForensicConfig.current.severeThreshold ? "较深" : "深部");
            if (h.getBoolean("death_trigger", false)) line += "；判断这可能是主要致命损伤";
            lines.add((lines.isEmpty() ? "记录的最高伤害：" : "记录的伤害 " + (lines.size() + 1) + "：") + line);
        }
        List<NbtCompound> collected = InspectionRecords.strongest(corpse.getListOrEmpty(corpse.contains("inspection_hits") ? "inspection_hits" : "hits"), part, 3);
        ForensicStore.get(player.getServer()).collect(player.getUuid(), b.getString("id", ""), part, collected);
        EvidenceSnapshots.observe(player, display, part);
        com.kuilunfuzhe.monvhua.features.forensics.cards.ClueCardService.onWoundsObserved(player,b.getString("id", ""));
        if (lines.isEmpty()) lines.add("未在该部位记录到可定位伤情；这不代表完全没有损伤");
        if (com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTransferService.enabled()) {
            String slot = switch (part) { case "torso" -> "torso"; case "left_arm", "right_arm" -> "hands";
                case "left_leg" -> "left_sole"; case "right_leg" -> "right_sole"; default -> ""; };
            if (!slot.isEmpty() && com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTransferStore.get(player.getServer())
                    .visible(corpse.getString("id", "") + "/" + slot) > 0) lines.add("看到该区域有接触形成的血液沾染；不能仅凭沾染确认身份");
        }
        return lines;
    }

    /** Read-only summary projection. Collection permissions can be tightened later without changing the UI packet. */
    public static List<String> summary(ServerPlayerEntity player, String requestedCorpse, String part) {
        if (!ForensicConfig.current.enabled || !player.isAlive() || player.isSpectator()) return List.of();
        List<String> lines = new ArrayList<>();
        for (NbtCompound corpse : ForensicStore.get(player.getServer()).corpseSnapshots()) {
            String id = corpse.getString("corpse_id", "");
            if (!requestedCorpse.isEmpty() && !requestedCorpse.equals(id)) continue;
            String name = corpse.getString("name", "未知身份");
            List<NbtCompound> hits = ForensicStore.get(player.getServer()).collected(player.getUuid(), id, part);
            if (hits.isEmpty()) continue;
            lines.add("§e" + name + " §7[" + id.substring(0, Math.min(8, id.length())) + "]");
            for (NbtCompound hit : hits) {
                String kinds = hit.getString("kinds", "unknown");
                lines.add("  " + kindLabel(kinds.split("\\+")[0]) + " / "
                        + hit.getFloat("amount", 0) + " / " + hit.getString("direction", "不明")
                        + (hit.getBoolean("death_trigger", false) ? " / 疑似致命" : ""));
            }
        }
        return lines.isEmpty() ? List.of("还没有收集到该部位的线索") : lines;
    }
    public static String kindLabel(String kind) {
        return switch (kind) { case "cut" -> "切割伤"; case "puncture" -> "刺入伤"; case "blunt" -> "钝击伤"; case "burn" -> "烧伤"; case "other" -> "其他损伤（外伤特征不明）"; default -> "类型不明的损伤"; };
    }
}
