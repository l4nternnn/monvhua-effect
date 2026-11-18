package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Server-validated wheel selection. Standing up or moving the UI cursor does not retarget it. */
public final class CorpseInspectionContext {
    public record Selection(UUID target,String part,String dimension,Vec3d feet,Vec3d eye,Vec3d direction,long expires){
        public boolean matches(UUID target,String part,String dimension,Vec3d feet,long tick){
            return this.target.equals(target)&&this.part.equals(part)&&this.dimension.equals(dimension)
                &&tick<=expires&&this.feet.squaredDistanceTo(feet)<=.75*.75;
        }
    }
    private static final Map<UUID,Selection> selections=new HashMap<>();
    private CorpseInspectionContext(){}
    public static void clear(UUID player){selections.remove(player);}
    public static void clear(){selections.clear();}
    public static void remember(ServerPlayerEntity player,ItemDisplayEntity target,String part,Vec3d eye,Vec3d direction){
        selections.put(player.getUuid(),new Selection(target.getUuid(),part,player.getWorld().getRegistryKey().getValue().toString(),player.getPos(),eye,direction,player.getServer().getTicks()+600L));
    }
    public static Selection find(ServerPlayerEntity player,ItemDisplayEntity target,String part){
        var selection=selections.get(player.getUuid());
        return selection!=null&&selection.matches(target.getUuid(),part,player.getWorld().getRegistryKey().getValue().toString(),player.getPos(),player.getServer().getTicks())?selection:null;
    }
}
