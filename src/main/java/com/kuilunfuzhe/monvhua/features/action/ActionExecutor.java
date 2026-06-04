package com.kuilunfuzhe.monvhua.features.action;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.network.action.ActionPoseS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

public class ActionExecutor {

    public enum ActionType {
        COMMAND, CHAT, SOUND, ATTRIBUTE, TELEPORT, GIVE_ITEM, POTION_EFFECT, TITLE, BODY_POSE;

        public static ActionType from(String s) {
            try { return valueOf(s.toUpperCase()); } catch (Exception e) { return CHAT; }
        }
    }

    public static void execute(ActionConfig.ActionDef action, ServerPlayerEntity player, Map<String, String> vars) {
        if (!action.enabled) return;
        if (player.hasPermissionLevel(action.requiredPermissionLevel)) {
            dispatch(ActionType.from(action.actionType), action.actionParams, player, vars);
        }
    }

    private static void dispatch(ActionType type, Map<String, Object> params, ServerPlayerEntity player, Map<String, String> vars) {
        switch (type) {
            case COMMAND -> execCommand(params, player, vars);
            case CHAT -> execChat(params, player, vars);
            case SOUND -> execSound(params, player, vars);
            case ATTRIBUTE -> execAttribute(params, player, vars);
            case TELEPORT -> execTeleport(params, player, vars);
            case GIVE_ITEM -> execGiveItem(params, player, vars);
            case POTION_EFFECT -> execPotionEffect(params, player, vars);
            case TITLE -> execTitle(params, player, vars);
            case BODY_POSE -> execBodyPose(params, player, vars);
        }
    }

    private static String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v == null ? "" : v.toString();
    }

    private static double num(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return 0;
    }

    private static int inum(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Number n) return n.intValue();
        return 0;
    }

    private static boolean bool(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Boolean b) return b;
        return false;
    }

    private static String resolve(String tpl, ServerPlayerEntity player, Map<String, String> vars) {
        if (tpl == null) return "";
        String s = tpl;
        s = s.replace("${player}", player.getName().getString());
        s = s.replace("${playerX}", String.format("%.1f", player.getX()));
        s = s.replace("${playerY}", String.format("%.1f", player.getY()));
        s = s.replace("${playerZ}", String.format("%.1f", player.getZ()));
        s = s.replace("${playerWorld}", player.getWorld().getRegistryKey().getValue().toString());
        s = s.replace("${playerHealth}", String.format("%.1f", player.getHealth()));
        s = s.replace("${playerUUID}", player.getUuid().toString());
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                s = s.replace("${" + e.getKey() + "}", e.getValue() != null ? e.getValue() : "");
            }
        }
        return s;
    }

    private static void execCommand(Map<String, Object> params, ServerPlayerEntity player, Map<String, String> vars) {
        String cmd = resolve(str(params, "command"), player, vars);
        if (cmd.isEmpty()) return;
        boolean asConsole = bool(params, "asConsole");
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (asConsole) {
            server.getCommandManager().executeWithPrefix(server.getCommandSource().withLevel(4), cmd);
        } else {
            server.getCommandManager().executeWithPrefix(player.getCommandSource(), cmd);
        }
    }

    private static void execChat(Map<String, Object> params, ServerPlayerEntity player, Map<String, String> vars) {
        String msg = resolve(str(params, "message"), player, vars);
        if (msg.isEmpty()) return;
        String target = str(params, "target");
        if ("global".equalsIgnoreCase(target)) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.getPlayerManager().broadcast(Text.literal(msg), false);
            }
        } else {
            player.sendMessage(Text.literal(msg), false);
        }
    }

    private static void execSound(Map<String, Object> params, ServerPlayerEntity player, Map<String, String> vars) {
        String soundStr = str(params, "sound");
        if (soundStr.isEmpty()) return;
        Identifier id = Identifier.tryParse(soundStr);
        if (id == null) return;
        SoundEvent soundEvent = Registries.SOUND_EVENT.get(id);
        if (soundEvent == null) return;
        float volume = (float) num(params, "volume");
        if (volume <= 0) volume = 1.0f;
        float pitch = (float) num(params, "pitch");
        if (pitch <= 0) pitch = 1.0f;
        player.playSoundToPlayer(soundEvent, SoundCategory.PLAYERS, volume, pitch);
    }

    private static void execAttribute(Map<String, Object> params, ServerPlayerEntity player, Map<String, String> vars) {
        String attr = str(params, "attribute").toLowerCase();
        String op = str(params, "operation").toLowerCase();
        double value = num(params, "value");

        switch (attr) {
            case "health" -> {
                if ("add".equals(op)) {
                    player.setHealth((float) (player.getHealth() + value));
                } else {
                    player.setHealth((float) value);
                }
            }
            case "max_health" -> {
                var inst = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                if (inst != null) {
                    if ("add".equals(op)) inst.setBaseValue(inst.getBaseValue() + value);
                    else inst.setBaseValue(value);
                }
            }
            case "hunger" -> {
                HungerManager hm = player.getHungerManager();
                if ("add".equals(op)) hm.setFoodLevel(hm.getFoodLevel() + (int) value);
                else hm.setFoodLevel((int) value);
            }
            case "saturation" -> {
                HungerManager hm = player.getHungerManager();
                if ("add".equals(op)) hm.setSaturationLevel(hm.getSaturationLevel() + (float) value);
                else hm.setSaturationLevel((float) value);
            }
            case "movement_speed" -> {
                var inst = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
                if (inst != null) {
                    if ("add".equals(op)) inst.setBaseValue(inst.getBaseValue() + value);
                    else inst.setBaseValue(value);
                }
            }
            case "attack_damage" -> {
                var inst = player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
                if (inst != null) {
                    if ("add".equals(op)) inst.setBaseValue(inst.getBaseValue() + value);
                    else inst.setBaseValue(value);
                }
            }
        }
    }

    private static void execTeleport(Map<String, Object> params, ServerPlayerEntity player, Map<String, String> vars) {
        double x = num(params, "x");
        double y = num(params, "y");
        double z = num(params, "z");
        boolean relative = !params.containsKey("x") && !params.containsKey("y") && !params.containsKey("z");
        if (relative) return;
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double tx = params.containsKey("x") ? x : px;
        double ty = params.containsKey("y") ? y : py;
        double tz = params.containsKey("z") ? z : pz;
        String worldStr = str(params, "world");
        if (!worldStr.isEmpty()) {
            Identifier worldId = Identifier.tryParse(worldStr);
            if (worldId != null) {
                MinecraftServer server = player.getServer();
                if (server != null) {
                    ServerWorld targetWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, worldId));
                    if (targetWorld != null) {
                        player.teleport(targetWorld, tx, ty, tz, java.util.Set.of(), player.getYaw(), player.getPitch(), false);
                        return;
                    }
                }
            }
        }
        player.teleport(tx, ty, tz, false);
    }

    private static void execGiveItem(Map<String, Object> params, ServerPlayerEntity player, Map<String, String> vars) {
        String itemStr = str(params, "item");
        if (itemStr.isEmpty()) return;
        Identifier id = Identifier.tryParse(itemStr);
        if (id == null) return;
        Item item = Registries.ITEM.get(id);
        if (item == null) return;
        int count = inum(params, "count");
        if (count <= 0) count = 1;
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    private static void execPotionEffect(Map<String, Object> params, ServerPlayerEntity player, Map<String, String> vars) {
        String effectStr = str(params, "effect");
        if (effectStr.isEmpty()) return;
        Identifier id = Identifier.tryParse(effectStr);
        if (id == null) return;
        RegistryEntry<net.minecraft.entity.effect.StatusEffect> entry = Registries.STATUS_EFFECT.getEntry(id).orElse(null);
        if (entry == null) return;
        int duration = inum(params, "duration");
        if (duration <= 0) duration = 200;
        int amplifier = inum(params, "amplifier");
        boolean ambient = bool(params, "ambient");
        boolean showParticles = params.containsKey("showParticles") ? bool(params, "showParticles") : true;
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                entry, duration, amplifier, ambient, showParticles, true));
    }

    private static void execTitle(Map<String, Object> params, ServerPlayerEntity player, Map<String, String> vars) {
        String title = resolve(str(params, "title"), player, vars);
        String subtitle = resolve(str(params, "subtitle"), player, vars);
        if (title.isEmpty() && subtitle.isEmpty()) return;
        int fadeIn = inum(params, "fadeIn");
        if (fadeIn <= 0) fadeIn = 10;
        int stay = inum(params, "stay");
        if (stay <= 0) stay = 70;
        int fadeOut = inum(params, "fadeOut");
        if (fadeOut <= 0) fadeOut = 20;
        if (!title.isEmpty()) {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(Text.literal(title)));
        }
        if (!subtitle.isEmpty()) {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(Text.literal(subtitle)));
        }
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(fadeIn, stay, fadeOut));
    }

    private static void execBodyPose(Map<String, Object> params, ServerPlayerEntity player, Map<String, String> vars) {
        String[] parts = {"head", "torso", "left_arm", "right_arm", "left_leg", "right_leg"};
        float[] poseValues = new float[18];
        for (int i = 0; i < 6; i++) {
            poseValues[i * 3] = (float) num(params, parts[i] + "_pitch");
            poseValues[i * 3 + 1] = (float) num(params, parts[i] + "_yaw");
            poseValues[i * 3 + 2] = (float) num(params, parts[i] + "_roll");
        }
        String skinName = str(params, "skin_name");
        if (skinName.isEmpty()) skinName = "ema";
        boolean slim = bool(params, "slim_model");
        int durationTicks = inum(params, "durationTicks");
        if (durationTicks <= 0) durationTicks = 40;
        ActionPoseS2CPacket packet = new ActionPoseS2CPacket(player.getId(), poseValues, durationTicks);
        if (player.getServer() != null) {
            for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(target, packet);
            }
        }
        if (bool(params, "spawn_model")) {
            BodyPartManager.createPosedCombinedDisplay(player, skinName, slim, poseValues);
        }
    }

    public static String executePreviewText(ActionConfig.ActionDef action, ServerPlayerEntity player) {
        if (action == null) return "";
        Map<String, String> vars = Map.of("trigger", "preview");

        String previewMsg = switch (ActionType.from(action.actionType.toUpperCase())) {
            case COMMAND -> {
                String cmd = resolve(str(action.actionParams, "command"), player, vars);
                boolean asConsole = bool(action.actionParams, "asConsole");
                yield asConsole ? "将以控制台执行: /" + cmd : "将以玩家身份执行: /" + cmd;
            }
            case GIVE_ITEM -> {
                String item = str(action.actionParams, "item");
                int count = inum(action.actionParams, "count");
                if (count <= 0) count = 1;
                yield "将给予物品: " + item + " x" + count;
            }
            case TELEPORT -> {
                double x = num(action.actionParams, "x");
                double y = num(action.actionParams, "y");
                double z = num(action.actionParams, "z");
                String world = str(action.actionParams, "world");
                yield "将传送至: " + String.format("%.1f, %.1f, %.1f", x, y, z)
                        + (world.isEmpty() ? "" : " 世界: " + world);
            }
            case ATTRIBUTE -> {
                String attr = str(action.actionParams, "attribute");
                String op = str(action.actionParams, "operation");
                double value = num(action.actionParams, "value");
                yield "将修改属性: " + attr + " " + (op.isEmpty() ? "set" : op) + " " + value;
            }
            case CHAT -> {
                String msg = resolve(str(action.actionParams, "message"), player, vars);
                String target = str(action.actionParams, "target");
                yield "将发送消息" + ("global".equalsIgnoreCase(target) ? "(全局)" : "") + ": " + msg;
            }
            case SOUND -> "将播放声音: " + str(action.actionParams, "sound");
            case POTION_EFFECT -> {
                String effect = str(action.actionParams, "effect");
                int duration = inum(action.actionParams, "duration");
                int amplifier = inum(action.actionParams, "amplifier");
                yield "将添加药水效果: " + effect + " 时长=" + duration + "t 等级=" + amplifier;
            }
            case TITLE -> {
                String title = resolve(str(action.actionParams, "title"), player, vars);
                String subtitle = resolve(str(action.actionParams, "subtitle"), player, vars);
                yield "将显示标题: " + title + (subtitle.isEmpty() ? "" : " / " + subtitle);
            }
            case BODY_POSE -> {
                StringBuilder sb = new StringBuilder("将设置身体姿势:");
                String[] parts = {"头部", "躯干", "左臂", "右臂", "左腿", "右腿"};
                String[] keys = {"head", "torso", "left_arm", "right_arm", "left_leg", "right_leg"};
                String[] axes = {"_pitch", "_yaw", "_roll"};
                String[] axisLabels = {"P", "Y", "R"};
                for (int i = 0; i < 6; i++) {
                    double p = num(action.actionParams, keys[i] + "_pitch");
                    double y = num(action.actionParams, keys[i] + "_yaw");
                    double r = num(action.actionParams, keys[i] + "_roll");
                    if (p != 0 || y != 0 || r != 0) {
                        sb.append("\n  ").append(parts[i]).append(": ")
                          .append("P").append(String.format("%.0f", p))
                          .append(" Y").append(String.format("%.0f", y))
                          .append(" R").append(String.format("%.0f", r));
                    }
                }
                yield sb.toString();
            }
        };

        StringBuilder sb = new StringBuilder();
        sb.append("动作: ").append(action.name).append("\n");
        sb.append("类型: ").append(action.actionType).append(" | 触发器: ").append(action.triggers.size()).append("\n");
        sb.append(previewMsg).append("\n");
        sb.append("--- 这只是预览，实际效果未执行 ---");
        return sb.toString();
    }

    public static void executePreview(ActionConfig.ActionDef action, ServerPlayerEntity player) {
        if (action == null) return;
        String text = executePreviewText(action, player);
        player.sendMessage(Text.literal("§e===== 动作预览: " + action.name + " ====="), false);
        player.sendMessage(Text.literal("§b" + text), false);
    }
}
