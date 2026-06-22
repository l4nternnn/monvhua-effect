package com.kuilunfuzhe.monvhua.client.imitate;

import com.kuilunfuzhe.monvhua.WitchStage;
import com.kuilunfuzhe.monvhua.gui.imitate.AreaImitateRoleScreen;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class AreaSelectClientManager {

    private static boolean isSelecting = false;
    private static int witchScore = 0;
    private static double radius = 5.0;

    public static void initialize() {
    }

    public static void startSelecting(int score) {
        isSelecting = true;
        witchScore = score;
        ImitateConfig config = ImitateConfig.getInstance();
        if (config != null) {
            int stage = WitchStage.fromScore(score).ordinal() + 1;
            radius = config.getImitateRadius(stage);
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("§d✦ 选择你想进行模仿声音的区域 §r✦"), true);
            client.player.sendMessage(Text.literal("§7右键点击方块选择中心位置，范围: " + radius + "格"), false);
        }
    }

    public static void stopSelecting() {
        isSelecting = false;
        witchScore = 0;
    }

    public static boolean isSelecting() {
        return isSelecting;
    }

    public static double getRadius() {
        return radius;
    }

    public static void tick(MinecraftClient client) {
        if (!isSelecting || client == null || client.player == null || client.world == null) {
            return;
        }
    }

    public static boolean onMouseClicked(int button) {
        if (!isSelecting) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        if (button == 0) {
            isSelecting = false;
            client.player.sendMessage(Text.literal("§c已取消区域选择"), true);
            return true;
        }

        if (button == 1) {
            Vec3d eyePos = client.player.getEyePos();
            Vec3d lookDir = client.player.getRotationVec(1.0f);
            
            double maxDistance = 100.0;
            HitResult hit = client.player.raycast(maxDistance, 0.0f, false);
            
            Vec3d selectedCenter;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                Vec3d hitPos = hit.getPos();
                selectedCenter = new Vec3d(hitPos.x, hitPos.y + 1, hitPos.z);
            } else {
                Vec3d farPoint = eyePos.add(lookDir.multiply(maxDistance));
                selectedCenter = farPoint.add(0, -eyePos.y + client.player.getY(), 0);
            }
            
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§a已选择区域中心: " + 
                    String.format("%.1f, %.1f, %.1f", selectedCenter.x, selectedCenter.y, selectedCenter.z) +
                    " §r范围: " + radius + "格"), true);
            }
            
            isSelecting = false;
            client.setScreen(new AreaImitateRoleScreen(witchScore, selectedCenter, radius));
            return true;
        }

        return false;
    }
}