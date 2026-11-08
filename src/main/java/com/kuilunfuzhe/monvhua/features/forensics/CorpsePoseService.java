package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.util.math.*;
import net.minecraft.nbt.NbtCompound;

/** Validates a detached candidate before changing a live carrier's root and tracked data. */
public final class CorpsePoseService {
    private CorpsePoseService() {}
    public static void validateGeometry(ItemDisplayEntity candidate,NbtCompound data,Vec3d origin) {
        var world=(ServerWorld)candidate.getWorld();
        if(!CorpsePoseGeometry.clear(world,candidate,data,origin)||!CorpsePoseGeometry.supported(world,candidate,data,origin))
            throw new IllegalArgumentException("终态与当前地形碰撞或无支撑");
        if(CorpseGeometrySync.bounds(CorpseGeometrySync.placement(candidate,data),data)==null)
            throw new IllegalArgumentException("无效终态范围");
    }
    public static void commitStatic(ItemDisplayEntity display,NbtCompound data) {
        if(CorpseGeometrySync.bounds(CorpseGeometrySync.placement(display,data),data)==null)throw new IllegalArgumentException("无效姿态范围");
        var stack=display.getItemStack().copy();stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));
        display.setItemStack(stack);CorpseGeometrySync.update(display);
    }
    public static void applyGenerated(ItemDisplayEntity display,CorpsePoseRecord record,Vec3d origin) {
        var world=(ServerWorld)display.getWorld();var link=BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid());
        if(link==null||!(world.getEntity(link) instanceof InteractionEntity))throw new IllegalArgumentException("交互载体未加载");
        if(CorpseCarryService.isCarried(display))throw new IllegalArgumentException("载体正在搬运");
        var stack=display.getItemStack().copy();var data=CorpsePoseRecord.data(stack);
        data.put(CorpsePoseRecord.KEY,record.encode());data.putLong("corpse_pose_revision",record.revision());data.putString("arm_model",record.armModel());
        var candidate=net.minecraft.entity.EntityType.ITEM_DISPLAY.create(world,net.minecraft.entity.SpawnReason.TRIGGERED);
        if(candidate==null)throw new IllegalArgumentException("无法准备姿态");
        candidate.setPosition(origin);candidate.setItemStack(stack);
        validateGeometry(candidate,data,origin);
        display.setTransformation(new AffineTransformation(new org.joml.Matrix4f()));display.setYaw(0);display.setPitch(0);
        display.setItemDisplayContext(net.minecraft.item.ItemDisplayContext.NONE);display.setPosition(origin);
        commitStatic(display,data);
        var store=ForensicStore.get(world.getServer());var corpse=store.corpse(Forensics.binding(stack).getString("id",""));
        if(!corpse.contains("initial_generated_pose"))corpse.put("initial_generated_pose",record.encode());
        corpse.putLong("last_pose_revision",record.revision());corpse.putString("last_pose_operation","local_generated");store.markDirty();
    }
}
