package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.forensics.ForensicConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

public final class BloodInspection {
    private BloodInspection() {}
    public static List<String> inspect(ServerPlayerEntity player) {
        if (!BloodRules.enabled() || !player.isAlive() || player.isSpectator()) return List.of();
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d eye = player.getEyePos();
        var hit = BloodSurfaceProjection.cast(world, eye, eye.add(player.getRotationVec(1).multiply(ForensicConfig.current.inspectionDistance)), player);
        if (hit == null) return List.of("此处没有可检查的血迹，请靠近并保持视线无遮挡");
        BloodTraceService.validate(world, hit.getBlockPos());
        var store = BloodTraceStore.get(world);
        var covering = store.at(hit.getBlockPos()).stream().filter(e -> e.patch().face() == hit.getSide() && e.patch().covers(hit.getPos())).toList();
        if (covering.isEmpty()) return List.of("此处未发现可检查的血迹");
        if (covering.stream().map(BloodTraceStore.Entry::event).distinct().count() > 1)
            return List.of("多处痕迹重叠，形态与方向难以判断");
        var chosen = covering.getFirst();
        if (chosen.patch().kind() == 1) return List.of("此处存在积聚血迹，边缘呈不规则扩展", "仅凭血泊无法确定死亡地点或精确停留时间");
        // Only infer an axis from nearby, coplanar, observable dots. No hidden attack vector is exposed.
        List<Vec3d> points = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            BlockPos pos = hit.getBlockPos().add(x, y, z);
            if (!world.isChunkLoaded(pos)) continue;
            for (var e : store.at(pos)) if (e.event().equals(chosen.event()) && e.patch().face() == hit.getSide()) {
                Vec3d point = e.patch().center().add(Vec3d.of(hit.getSide().getVector()).multiply(.003));
                if (eye.squaredDistanceTo(point) <= ForensicConfig.current.inspectionDistance * ForensicConfig.current.inspectionDistance
                        && world.raycast(new net.minecraft.world.RaycastContext(eye, point, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                        net.minecraft.world.RaycastContext.FluidHandling.ANY, player)).getType() == net.minecraft.util.hit.HitResult.Type.MISS) points.add(point);
            }
        }
        String direction = "落点不足以判断分布方向";
        if (chosen.directional() && points.size() >= 3) {
            double dx = points.stream().mapToDouble(p -> p.x).max().orElse(0) - points.stream().mapToDouble(p -> p.x).min().orElse(0);
            double dz = points.stream().mapToDouble(p -> p.z).max().orElse(0) - points.stream().mapToDouble(p -> p.z).min().orElse(0);
            if (dx > .3 && dx > dz * 1.8) direction = "落点主要沿东西向展开";
            else if (dz > .3 && dz > dx * 1.8) direction = "落点主要沿南北向展开";
        }
        return List.of("血迹呈散点喷溅状；" + direction, "分布只支持粗略判断，无法据此确定攻击者位置或精确力度");
    }
}
