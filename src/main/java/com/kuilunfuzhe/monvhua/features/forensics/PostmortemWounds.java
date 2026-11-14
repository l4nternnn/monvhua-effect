package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsCoordinator;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseGrabController;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseDismemberment;
import com.kuilunfuzhe.monvhua.features.forensics.physics.CorpseResidentManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Melee injury on an existing corpse, with tagged two-strike joint separation. */
public final class PostmortemWounds {
    public static final int MAX_WOUNDS = 64;
    private static final Map<UUID, Long> LAST_ATTACK = new HashMap<>();
    private PostmortemWounds() {}
    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> LAST_ATTACK.remove(h.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> LAST_ATTACK.clear());
    }
    /** Returns true for a bound corpse, including rejected attacks: never fall through to destructive entity handling. */
    public static boolean attack(ServerPlayerEntity player, Entity target) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Entity resolved = target;
        if (target instanceof InteractionEntity) {
            UUID displayId = BodyPartManager.INTERACTION_TO_DISPLAY.get(target.getUuid());
            resolved = displayId == null ? null : world.getEntity(displayId);
        }
        if (!(resolved instanceof ItemDisplayEntity display) || !Forensics.hasBinding(display.getItemStack())) return false;
        if (!ForensicConfig.current.enabled || !ForensicConfig.current.postmortemWounds || !player.isAlive() || player.isSpectator()
                || display.isRemoved() || display.getWorld() != world || CorpseCarryService.isCarrying(player.getUuid())
                || CorpseGrabController.status(player.getUuid()) != null || CorpseCarryService.isCarried(display)
                || CorpsePhysicsCoordinator.simulating(display) || CorpsePhysicsCoordinator.pending(display)
                || !CorpseResidentManager.acceptsWounds(display)
                || !Forensics.valid(world, display.getItemStack(), display.getUuid().toString())) return true;
        NbtCompound binding = Forensics.binding(display.getItemStack());
        NbtCompound data = CorpsePoseRecord.data(display.getItemStack());
        // Separate limb renderers and arbitrary skeletal meshes do not use this body's surface adapter.
        if (!binding.getString("part", "").equals("all") && CorpsePoseRecord.read(data).isEmpty() || data.getString("body_pose_mode", "").equals("true_skeletal")
                || (data.contains(CorpsePoseRecord.KEY) && CorpsePoseRecord.read(data).isEmpty())) return true;
        long now = world.getServer().getOverworld().getTime();
        Long last = LAST_ATTACK.get(player.getUuid());
        if (last != null && now >= last && now - last < 10) return true;
        double reach = Math.min(6, player.getEntityInteractionRange());
        if (!Double.isFinite(reach) || reach <= 0) return true;
        Vec3d from = player.getEyePos(), to = from.add(player.getRotationVec(1).multiply(reach));
        var wall = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getPos();
        var hit = CorpsePoseGeometry.woundHit(CorpsePoseGeometry.surfaces(display, data), display.getPos(), from, to);
        if (hit == null || hit.position().squaredDistanceTo(from) > reach * reach) return true;
        // A forged target ID must not attack through another corpse/living entity on the same ray.
        double distance = from.squaredDistanceTo(hit.position());
        for (Entity e : world.getOtherEntities(player, player.getBoundingBox().union(display.getBoundingBox()).expand(reach),
                e -> e != target && e != display && (e instanceof net.minecraft.entity.LivingEntity || e instanceof InteractionEntity))) {
            UUID linked = BodyPartManager.INTERACTION_TO_DISPLAY.get(e.getUuid());
            if (display.getUuid().equals(linked)) continue;
            var obstruction = e.getBoundingBox().raycast(from, hit.position());
            if (e.getBoundingBox().contains(from) || (obstruction.isPresent() && from.squaredDistanceTo(obstruction.get()) < distance - .001)) return true;
        }
        float cooldown = player.getAttackCooldownProgress(.5f);
        float amount = damage(player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE), cooldown);
        if (amount <= 0) return true;
        var context = AttackContexts.current(player.getUuid());
        ItemStack weapon = context == null ? player.getWeaponStack().copy() : context.weapon();
        var selection = context == null ? WeaponProfiles.select(weapon, "melee") : context.selection();
        if (weapon.isEmpty()) selection = new WeaponProfiles.Selection("unarmed", "strike",
                new WeaponProfiles.Mode("melee", List.of(new WeaponProfiles.Component("blunt", 1))));
        NbtCompound record = record(hit.wound(), selection, amount);
        record.putLong("tick", world.getTime()); record.putString("dimension", world.getRegistryKey().getValue().toString());
        record.putDouble("x", hit.position().x); record.putDouble("y", hit.position().y); record.putDouble("z", hit.position().z);
        record.putString("attacker", player.getUuid().toString()); record.putString("weapon", Registries.ITEM.getId(weapon.getItem()).toString());
        if (context != null) record.putString("attack_id", context.id());
        String corpse = binding.getString("id", "");
        var store = ForensicStore.get(player.getServer());
        var pose=CorpsePoseRecord.read(data);
        String joint=null;
        if(pose.isPresent()&&CorpseDismemberment.eligible(player,weapon,cooldown))
            joint=CorpseDismemberment.connection(CorpsePoseGeometry.surfaces(display,data),display.getPos(),hit,pose.get().scale(),pose.get().armModel().equals("slim"));
        boolean appended=store.appendPostmortem(corpse,record);
        if (!appended&&joint==null) return true;
        LAST_ATTACK.put(player.getUuid(), now); player.resetLastAttackedTicks();
        if(appended){
            sync(display);
            CorpseResidentManager.woundsUpdated(display);
        }
        if(joint!=null)CorpseDismemberment.strike(player,display,joint,hit.position(),record.getString("id",""));
        return true;
    }
    public static float damage(double base, float cooldown) {
        if (!Double.isFinite(base) || !Float.isFinite(cooldown) || base <= 0) return 0;
        double c = Math.clamp(cooldown, 0, 1);
        return (float) Math.min(1024, base * (.2 + c * c * .8));
    }
    public static NbtCompound record(NbtCompound surface, WeaponProfiles.Selection selection, float amount) {
        NbtCompound hit = new NbtCompound();
        hit.putString("id", UUID.randomUUID().toString()); hit.putFloat("amount", amount);
        hit.putString("source", "minecraft:player_attack"); hit.putBoolean("postmortem", true);
        hit.putString("part", surface.getString("part", "unknown")); hit.putString("precision", "approximate");
        hit.putString("direction", "unknown"); hit.put("wound_surface", surface.copy());
        hit.putString("severity", amount < ForensicConfig.current.moderateThreshold ? "mild"
                : amount < ForensicConfig.current.severeThreshold ? "moderate" : "severe");
        if (selection == null) hit.putString("kinds", "unknown");
        else {
            hit.putString("profile", selection.profile()); hit.putString("mode", selection.mode());
            NbtList components = new NbtList();
            for (var c : selection.definition().components()) {
                NbtCompound n = new NbtCompound(); n.putString("kind", c.kind()); n.putDouble("weight", c.weight()); components.add(n);
            }
            hit.put("components", components);
            hit.putString("kinds", String.join("+", selection.definition().components().stream().map(WeaponProfiles.Component::kind).toList()));
        }
        return hit;
    }
    /** Deliberately excludes actor, time, and postmortem markers from synchronized item data. */
    public static NbtList visuals(NbtCompound corpse) {
        NbtList result = new NbtList();
        for (String key : List.of("hits", "postmortem_hits")) for (NbtElement element : corpse.getListOrEmpty(key)) {
            if (!(element instanceof NbtCompound h) || !h.contains("wound_surface")) continue;
            NbtCompound surface = h.getCompoundOrEmpty("wound_surface"), visual = new NbtCompound();
            for (String field : List.of("surface", "part", "face")) visual.putString(field, surface.getString(field, ""));
            visual.putDouble("u", surface.getDouble("u", .5)); visual.putDouble("v", surface.getDouble("v", .5));
            visual.putString("kinds", h.getString("kinds", "unknown"));
            float amount = h.getFloat("amount", 0);
            visual.putString("severity", h.getString("severity", amount < ForensicConfig.current.moderateThreshold ? "mild"
                    : amount < ForensicConfig.current.severeThreshold ? "moderate" : "severe"));
            result.add(visual);
        }
        return result;
    }
    /** Rehydrate from the authoritative corpse after pickup, merge and chunk reload, not stale item copies. */
    public static void sync(ItemDisplayEntity display) {
        if (!(display.getWorld() instanceof ServerWorld world) || !Forensics.hasBinding(display.getItemStack())
                || !Forensics.valid(world, display.getItemStack(), display.getUuid().toString())) return;
        var corpse = ForensicStore.get(world.getServer()).corpse(Forensics.binding(display.getItemStack()).getString("id", ""));
        if (corpse.getListOrEmpty("postmortem_hits").isEmpty()) return;
        ItemStack stack = display.getItemStack().copy(); var data = CorpsePoseRecord.data(stack);
        NbtList wounds = visuals(corpse);
        var pose=CorpsePoseRecord.read(data);
        if(pose.isPresent()){var filtered=new NbtList();for(var e:wounds)if(e instanceof NbtCompound n&&pose.get().parts().containsKey(n.getString("part","")))filtered.add(n.copy());wounds=filtered;}
        if (wounds.equals(data.get("corpse_wounds"))) return;
        data.put("corpse_wounds", wounds); stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data)); display.setItemStack(stack);
    }
}
