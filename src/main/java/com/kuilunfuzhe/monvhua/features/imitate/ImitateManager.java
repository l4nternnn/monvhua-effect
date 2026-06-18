package com.kuilunfuzhe.monvhua.features.imitate;

import com.kuilunfuzhe.monvhua.WitchStage;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes.configManager;

public class ImitateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("monvhua-imitate");

    private static final Map<UUID, String> imitateMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> imitateEndTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> switchCooldownEnd = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> soundWaveCooldownEnd = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> silenceCooldownEnd = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> notifiedImitateEnd = new ConcurrentHashMap<>();

    public static final String[] ROLES = {
            "樱羽艾玛",
            "二阶堂希罗",
            "泽渡可可",
            "橘雪莉",
            "远野汉娜",
            "夏目安安",
            "城崎诺亚",
            "莲见蕾雅",
            "佐伯米利亚",
            "黑部奈叶香",
            "宝生玛格",
            "紫藤亚里沙",
            "冰上梅露露",
            "典狱长",
            "月代雪"
    };

    private static final Map<String, Text> ROLE_COLORS = new ConcurrentHashMap<>();

    static {
        ROLE_COLORS.put("樱羽艾玛", Text.literal("◆ ").withColor(0xfc8eac).append(Text.literal("樱羽艾玛").withColor(0xfc8eac)));
        ROLE_COLORS.put("二阶堂希罗", Text.literal("◆ ").withColor(0x8b0000).append(Text.literal("二阶堂希罗").withColor(0x8b0000)));
        ROLE_COLORS.put("泽渡可可", Text.literal("◆ ").withColor(0xff6700).append(Text.literal("泽渡可可").withColor(0xff6700)));
        ROLE_COLORS.put("橘雪莉", Text.literal("◆ ").withColor(0x1e90ff).append(Text.literal("橘雪莉").withColor(0x1e90ff)));
        ROLE_COLORS.put("远野汉娜", Text.literal("◆ ").withColor(0x5f9e3f).append(Text.literal("远野汉娜").withColor(0x5f9e3f)));
        ROLE_COLORS.put("夏目安安", Text.literal("◆ ").withColor(0x240090).append(Text.literal("夏目安安").withColor(0x240090)));
        ROLE_COLORS.put("城崎诺亚", Text.literal("◆ ").formatted(Formatting.AQUA)
                .append(Text.literal("城").formatted(Formatting.AQUA))
                .append(Text.literal("崎").formatted(Formatting.RED))
                .append(Text.literal("诺").formatted(Formatting.YELLOW))
                .append(Text.literal("亚").formatted(Formatting.LIGHT_PURPLE)));
        ROLE_COLORS.put("莲见蕾雅", Text.literal("◆ ").formatted(Formatting.GOLD).append(Text.literal("莲见蕾雅").formatted(Formatting.GOLD)));
        ROLE_COLORS.put("佐伯米利亚", Text.literal("◆ ").formatted(Formatting.YELLOW).append(Text.literal("佐伯米利亚").formatted(Formatting.YELLOW)));
        ROLE_COLORS.put("黑部奈叶香", Text.literal("◆ ").formatted(Formatting.DARK_GRAY).append(Text.literal("黑部奈叶香").formatted(Formatting.DARK_GRAY)));
        ROLE_COLORS.put("宝生玛格", Text.literal("◆ ").formatted(Formatting.DARK_PURPLE).append(Text.literal("宝生玛格").formatted(Formatting.DARK_PURPLE)));
        ROLE_COLORS.put("紫藤亚里沙", Text.literal("◆ ").withColor(0xB1B7AC).append(Text.literal("紫藤亚里沙").withColor(0xB1B7AC)));
        ROLE_COLORS.put("冰上梅露露", Text.literal("◆ ").withColor(0xddb6ff).append(Text.literal("冰上梅露露").withColor(0xddb6ff)));
        ROLE_COLORS.put("典狱长", Text.literal("◆ ").formatted(Formatting.WHITE).append(Text.literal("典狱长").formatted(Formatting.WHITE)));
        ROLE_COLORS.put("月代雪", Text.literal("◆ ").withColor(0xe0ffff).append(Text.literal("月代雪").withColor(0xe0ffff)));
    }

    public static WitchStage getWitchStage(ServerPlayerEntity player) {
        Scoreboard scoreboard = player.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("monvhua");
        if (objective == null) {
            return WitchStage.SANE;
        }
        var info = scoreboard.getScore(player, objective);
        int value = info == null ? 0 : info.getScore();
        return WitchStage.fromScore(value);
    }

    public static int getWitchScore(ServerPlayerEntity player) {
        Scoreboard scoreboard = player.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("monvhua");
        if (objective == null) {
            return 0;
        }
        var info = scoreboard.getScore(player, objective);
        return info == null ? 0 : info.getScore();
    }

    /**
     * 获取玩家的阶段值（1-8），用于查询对应阶段的配置参数。
     * @return 阶段值，若配置管理器未初始化则默认返回1
     */
    public static int getPlayerStage(ServerPlayerEntity player) {
        if (configManager == null) return 1;
        return com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_Eyes.getPlayerStage(player, configManager);
    }

    public static void setImitate(ServerPlayerEntity player, String roleName) {
        if (isInSwitchCooldown(player)) {
            player.sendMessage(Text.literal("§c模仿冷却中，请等待").append(Text.literal(String.valueOf(getSwitchCooldownRemaining(player))).formatted(Formatting.RED)).append(Text.literal("秒")), true);
            return;
        }

        imitateMap.put(player.getUuid(), roleName);

        int stage = getPlayerStage(player);
        ImitateConfig config = ImitateConfig.getInstance();
        int duration = config.getDuration(stage);
        int cooldown = config.getSwitchCooldown(stage);

        LOGGER.info("设置模仿: 玩家={}, 角色={}, 阶段={}, 持续时间={}, 切换冷却={}", 
            player.getName().getString(), roleName, stage, duration, cooldown);

        if (duration > 0) {
            long endTime = System.currentTimeMillis() + duration * 1000L;
            imitateEndTime.put(player.getUuid(), endTime);
        }

        if (cooldown > 0) {
            long cooldownEnd = System.currentTimeMillis() + cooldown * 1000L;
            switchCooldownEnd.put(player.getUuid(), cooldownEnd);
        }

        player.sendMessage(Text.literal("§a开始模仿 ").append(getColoredRoleName(roleName)), true);
        player.sendMessage(Text.literal("§a你已开始模仿 ").append(getColoredRoleName(roleName)).append(Text.literal("§a，持续时间: ").append(Text.literal(duration > 0 ? duration + "秒" : "无限")).formatted(Formatting.GREEN)), false);
        notifiedImitateEnd.remove(player.getUuid());
    }

    public static void clearImitate(ServerPlayerEntity player) {
        imitateMap.remove(player.getUuid());
        imitateEndTime.remove(player.getUuid());

        int stage = getPlayerStage(player);
        ImitateConfig config = ImitateConfig.getInstance();
        int cooldown = config.getSwitchCooldown(stage);

        if (cooldown > 0) {
            long cooldownEnd = System.currentTimeMillis() + cooldown * 1000L;
            switchCooldownEnd.put(player.getUuid(), cooldownEnd);
        }

        player.sendMessage(Text.literal("§a已取消模仿"), true);
    }

    public static String getImitateName(ServerPlayerEntity player) {
        return imitateMap.get(player.getUuid());
    }

    public static boolean isImitating(ServerPlayerEntity player) {
        return imitateMap.containsKey(player.getUuid());
    }

    public static boolean hasPlayerTag(ServerPlayerEntity player) {
        return player.getCommandTags().contains("player");
    }

    public static boolean isInSwitchCooldown(ServerPlayerEntity player) {
        Long endTime = switchCooldownEnd.get(player.getUuid());
        return endTime != null && System.currentTimeMillis() < endTime;
    }

    public static int getSwitchCooldownRemaining(ServerPlayerEntity player) {
        Long endTime = switchCooldownEnd.get(player.getUuid());
        if (endTime == null) return 0;
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int) remaining : 0;
    }

    public static boolean canUseSoundWave(ServerPlayerEntity player) {
        Long endTime = soundWaveCooldownEnd.get(player.getUuid());
        return endTime == null || System.currentTimeMillis() >= endTime;
    }

    public static void startSoundWaveCooldown(ServerPlayerEntity player) {
        int stage = getPlayerStage(player);
        ImitateConfig config = ImitateConfig.getInstance();
        int cooldown = config.getSoundWaveCooldown(stage);

        LOGGER.info("设置声波震荡冷却: 玩家={}, 阶段={}, 冷却={}", 
            player.getName().getString(), stage, cooldown);

        if (cooldown > 0) {
            long cooldownEnd = System.currentTimeMillis() + cooldown * 1000L;
            soundWaveCooldownEnd.put(player.getUuid(), cooldownEnd);
        }
    }

    public static int getSoundWaveCooldownRemaining(ServerPlayerEntity player) {
        Long endTime = soundWaveCooldownEnd.get(player.getUuid());
        if (endTime == null) return 0;
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int) remaining : 0;
    }

    public static long getImitateEndTime(UUID uuid) {
        return imitateEndTime.getOrDefault(uuid, 0L);
    }

    public static long getSwitchCooldownEndTime(UUID uuid) {
        return switchCooldownEnd.getOrDefault(uuid, 0L);
    }

    public static long getSoundWaveCooldownEndTime(UUID uuid) {
        return soundWaveCooldownEnd.getOrDefault(uuid, 0L);
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();

        imitateEndTime.forEach((uuid, endTime) -> {
            if (now >= endTime) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null && !notifiedImitateEnd.containsKey(uuid)) {
                    player.sendMessage(Text.literal("§a模仿已结束").formatted(Formatting.GREEN), false);
                    notifiedImitateEnd.put(uuid, true);
                }
                imitateMap.remove(uuid);
                imitateEndTime.remove(uuid);
            }
        });
    }

    public static Text getColoredRoleName(String roleName) {
        return ROLE_COLORS.getOrDefault(roleName, Text.literal("◆ " + roleName).formatted(Formatting.LIGHT_PURPLE));
    }

    public static boolean canUseSilence(ServerPlayerEntity player) {
        Long endTime = silenceCooldownEnd.get(player.getUuid());
        if (endTime == null) return true;
        return System.currentTimeMillis() >= endTime;
    }

    public static int getSilenceCooldownRemaining(ServerPlayerEntity player) {
        Long endTime = silenceCooldownEnd.get(player.getUuid());
        if (endTime == null) return 0;
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int) remaining : 0;
    }

    public static void startSilenceCooldown(ServerPlayerEntity player) {
        int stage = getPlayerStage(player);
        ImitateConfig config = ImitateConfig.getInstance();
        int cooldown = config.getSilenceCooldown(stage);

        LOGGER.info("设置静音冷却: 玩家={}, 阶段={}, 冷却={}", 
            player.getName().getString(), stage, cooldown);

        if (cooldown > 0) {
            long cooldownEnd = System.currentTimeMillis() + cooldown * 1000L;
            silenceCooldownEnd.put(player.getUuid(), cooldownEnd);
        }
    }

    public static long getSilenceCooldownEndTime(UUID uuid) {
        return silenceCooldownEnd.getOrDefault(uuid, 0L);
    }

    public static Text getFormattedName(ServerPlayerEntity player) {
        String imitateName = getImitateName(player);
        if (imitateName != null) {
            return getColoredRoleName(imitateName);
        }
        return null;
    }

    public static void resetCooldowns(UUID uuid) {
        switchCooldownEnd.remove(uuid);
        soundWaveCooldownEnd.remove(uuid);
        silenceCooldownEnd.remove(uuid);
        LOGGER.info("重置玩家冷却: {}", uuid);
    }

    public static void initialize() {
        LOGGER.info("ImitateManager initialized");
    }
}