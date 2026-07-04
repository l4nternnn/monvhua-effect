package com.kuilunfuzhe.monvhua.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kuilunfuzhe.monvhua.features.hot_backpack_save.HotBackpackSaveClient;
import com.kuilunfuzhe.monvhua.features.textarea.TextAreaHudClient;
import com.kuilunfuzhe.monvhua.gui.stage.general_stage;
import com.kuilunfuzhe.monvhua.item.config.FloatingConfig;
import com.kuilunfuzhe.monvhua.item.config.GazeConfig;
import com.kuilunfuzhe.monvhua.item.config.GravityConfig;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import com.kuilunfuzhe.monvhua.item.config.InjuredBleedingConfig;
import com.kuilunfuzhe.monvhua.item.config.MirrorConfig;
import com.kuilunfuzhe.monvhua.item.config.PaintConfig;
import com.kuilunfuzhe.monvhua.item.config.PlantMagicConfig;
import com.kuilunfuzhe.monvhua.item.config.SecretConfig;
import com.kuilunfuzhe.monvhua.item.config.ThroughConfig;
import com.kuilunfuzhe.monvhua.network.floating.FloatingPackets;
import com.kuilunfuzhe.monvhua.network.gazeguidance.RequestConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.gazeguidance.UpdateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.general_stage.GeneralStagePackets.GlobalConfigS2C;
import com.kuilunfuzhe.monvhua.network.general_stage.GeneralStagePackets.RequestGlobalConfigC2S;
import com.kuilunfuzhe.monvhua.network.gravity.GravityPackets;
import com.kuilunfuzhe.monvhua.network.imitate.RequestImitateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.imitate.UpdateImitateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.injured_and_bleeding.InjuredBleedingPackets;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets;
import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
import com.kuilunfuzhe.monvhua.network.plant.PlantMagicPackets;
import com.kuilunfuzhe.monvhua.network.secret.SecretPackets;
import com.kuilunfuzhe.monvhua.network.through.RequestThroughConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.through.ThroughConfigUpdateC2SPacket;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class CombinedConfigScreen {
    private static final int STAGES = 7;
    private static final GlobalConfigS2C.StageConfig[] CACHED_EVIL_CONFIGS = new GlobalConfigS2C.StageConfig[8];
    private static GazeConfig cachedGazeConfig;
    private static MirrorConfig cachedMirrorConfig;
    private static ThroughConfig cachedThroughConfig;
    private static ImitateConfig cachedImitateConfig;
    private static FloatingConfig cachedFloatingConfig;
    private static PlantMagicConfig cachedPlantMagicConfig;
    public static SecretConfig cachedSecretConfig;
    private static PaintConfig cachedPaintConfig;
    private static GravityConfig cachedGravityConfig;
    private static InjuredBleedingConfig cachedInjuredBleedingConfig;
    private static CombinedConfigFragment activeFragment;
    private static final ScreenUiState UI_STATE = ScreenUiState.load();
    private static float uiScale = UI_STATE.uiScale;

    private CombinedConfigScreen() {
    }

    public static void open() {
        ensureDefaults();
        requestAllConfigs();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(MuiModApi.get().createScreen(new CombinedConfigFragment(), NON_PAUSING_CALLBACK)));
        }
    }

    public static boolean isActive() {
        return activeFragment != null;
    }

    private static void refreshActiveFragment() {
        CombinedConfigFragment fragment = activeFragment;
        if (fragment == null) {
            return;
        }
        Runnable refresh = () -> {
            if (activeFragment == fragment) {
                fragment.refreshFromExternalConfig();
            }
        };
        if (Core.isOnUiThread()) {
            refresh.run();
            return;
        }
        if (Core.getUiHandlerAsync() != null) {
            Core.postOnUiThread(refresh);
            return;
        }
        if (fragment.rootLayout != null) {
            fragment.rootLayout.post(refresh);
        }
    }

    public static void receiveEvilConfigs(GlobalConfigS2C.StageConfig[] configsArray) {
        if (configsArray == null) return;
        for (int i = 0; i < configsArray.length && i + 1 < CACHED_EVIL_CONFIGS.length; i++) {
            CACHED_EVIL_CONFIGS[i + 1] = configsArray[i];
        }
        refreshActiveFragment();
    }

    public static void receiveGazeConfig(GazeConfig config) {
        if (config == null) return;
        cachedGazeConfig = config;
        refreshActiveFragment();
    }

    public static void receiveMirrorConfig(MirrorConfig config) {
        if (config == null) return;
        cachedMirrorConfig = config;
        refreshActiveFragment();
    }

    public static void receiveThroughConfig(ThroughConfig config) {
        if (config == null) return;
        cachedThroughConfig = config;
        refreshActiveFragment();
    }

    public static void receiveImitateConfig(ImitateConfig config) {
        if (config == null) return;
        cachedImitateConfig = config;
        refreshActiveFragment();
    }

    public static void receiveFloatingConfig(FloatingConfig config) {
        if (config == null) return;
        cachedFloatingConfig = config;
        refreshActiveFragment();
    }

    public static void receivePlantMagicConfig(PlantMagicConfig config) {
        if (config == null) return;
        cachedPlantMagicConfig = config;
        refreshActiveFragment();
    }

    public static void receiveSecretConfig(SecretConfig config) {
        if (config == null) return;
        cachedSecretConfig = config;
        refreshActiveFragment();
    }

    public static void receivePaintConfig(PaintConfig config) {
        if (config == null) return;
        cachedPaintConfig = config;
        refreshActiveFragment();
    }

    public static void receiveGravityConfig(GravityConfig config) {
        if (config == null) return;
        cachedGravityConfig = config;
        refreshActiveFragment();
    }

    public static void receiveInjuredBleedingConfig(InjuredBleedingConfig config) {
        if (config == null) return;
        cachedInjuredBleedingConfig = config;
        refreshActiveFragment();
    }

    private static void ensureDefaults() {
        if (cachedGazeConfig == null) cachedGazeConfig = new GazeConfig();
        if (cachedMirrorConfig == null) cachedMirrorConfig = new MirrorConfig();
        if (cachedThroughConfig == null) cachedThroughConfig = new ThroughConfig();
        if (cachedImitateConfig == null) cachedImitateConfig = new ImitateConfig();
        if (cachedFloatingConfig == null) cachedFloatingConfig = new FloatingConfig();
        if (cachedPlantMagicConfig == null) cachedPlantMagicConfig = new PlantMagicConfig();
        if (cachedSecretConfig == null) cachedSecretConfig = new SecretConfig();
        if (cachedPaintConfig == null) cachedPaintConfig = new PaintConfig();
        if (cachedGravityConfig == null) cachedGravityConfig = new GravityConfig();
        if (cachedInjuredBleedingConfig == null) cachedInjuredBleedingConfig = new InjuredBleedingConfig();
    }

    private static void requestAllConfigs() {
        ClientPlayNetworking.send(new RequestGlobalConfigC2S());
        ClientPlayNetworking.send(new RequestConfigC2SPacket());
        ClientPlayNetworking.send(new MirrorPackets.RequestConfigC2S());
        ClientPlayNetworking.send(new RequestThroughConfigC2SPacket());
        ClientPlayNetworking.send(new RequestImitateConfigC2SPacket());
        ClientPlayNetworking.send(new SecretPackets.RequestConfigC2S());
        ClientPlayNetworking.send(new FloatingPackets.RequestConfigC2S());
        ClientPlayNetworking.send(new PlantMagicPackets.RequestConfigC2S());
        ClientPlayNetworking.send(new PaintOverlayPackets.RequestPaintConfigC2S());
        ClientPlayNetworking.send(new GravityPackets.RequestConfigC2S());
        ClientPlayNetworking.send(new InjuredBleedingPackets.RequestConfigC2S());
    }

    private static final ScreenCallback NON_PAUSING_CALLBACK = new ScreenCallback() {
        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean hasDefaultBackground() {
            return false;
        }

        @Override
        public boolean shouldBlurBackground() {
            return false;
        }
    };

    private enum ConfigType {
        EVIL_EYES("千里眼"),
        GAZE_GUIDANCE("视线诱导"),
        MIRROR("镜子"),
        THROUGH("穿墙"),
        IMITATE("模仿"),
        FLOATING("漂浮"),
        SECRET("窃密"),
        PLANT("植物"),
        PAINT("绘制"),
        GRAVITY("重力");

        final String label;

        ConfigType(String label) {
            this.label = label;
        }
    }

    public static final class CombinedConfigFragment extends Fragment {
        private ConfigType currentType = ConfigType.EVIL_EYES;
        private boolean injuredPage;
        private int stage = 1;
        private int rangeStage = 1;
        private LinearLayout navList;
        private LinearLayout centerPanel;
        private LinearLayout rightPanel;
        private LinearLayout rootLayout;
        private EditText uiScaleField;
        private TextView entitySelectorHint;
        private List<String> entitySelectorSuggestions = List.of();
        private final Map<String, EditText> fields = new LinkedHashMap<>();
        private final Map<String, EditText> sideFields = new LinkedHashMap<>();
        private boolean rebuilding;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
            ensureDefaults();
            Context ctx = getContext();
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setPadding(s(10), s(10), s(10), s(10));
            root.setBackground(new ColorDrawable(0xE80F1115));
            rootLayout = root;

            addColumns(root, ctx);
            return root;
        }

        private void addColumns(LinearLayout root, Context ctx) {
            root.addView(createLeftColumn(ctx), new LinearLayout.LayoutParams(s(180), -1));
            root.addView(createCenterColumn(ctx), new LinearLayout.LayoutParams(0, -1, 1.0F));
            root.addView(createRightColumn(ctx), new LinearLayout.LayoutParams(s(300), -1));
        }

        @Override
        public void onResume() {
            super.onResume();
            activeFragment = this;
            requestAllConfigs();
        }

        @Override
        public void onPause() {
            super.onPause();
            if (activeFragment == this) activeFragment = null;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            if (activeFragment == this) activeFragment = null;
            rootLayout = null;
        }

        private View createLeftColumn(Context ctx) {
            LinearLayout panel = panel(ctx);
            panel.addView(title(ctx, "配置"), blockParams());
            ScrollView scroll = scroll(ctx);
            navList = vertical(ctx);
            navList.setPadding(s(4), s(4), s(4), s(4));
            scroll.addView(navList, new FrameLayout.LayoutParams(-1, -2));
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0F));
            rebuildNav();
            return panel;
        }

        private View createCenterColumn(Context ctx) {
            LinearLayout panel = panel(ctx);
            ScrollView scroll = scroll(ctx);
            centerPanel = vertical(ctx);
            centerPanel.setPadding(s(8), s(8), s(8), s(8));
            scroll.addView(centerPanel, new FrameLayout.LayoutParams(-1, -2));
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
            rebuildCenter();
            return panel;
        }

        private View createRightColumn(Context ctx) {
            LinearLayout panel = panel(ctx);
            ScrollView scroll = scroll(ctx);
            rightPanel = vertical(ctx);
            rightPanel.setPadding(s(8), s(8), s(8), s(8));
            scroll.addView(rightPanel, new FrameLayout.LayoutParams(-1, -2));
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0F));
            panel.addView(createUiScalePanel(ctx), new LinearLayout.LayoutParams(-1, -2));
            rebuildRight();
            return panel;
        }

        private View createUiScalePanel(Context ctx) {
            LinearLayout panel = vertical(ctx);
            panel.setPadding(s(8), s(8), s(8), s(8));
            panel.setBackground(panelShape(0xF01A2028, 0xFF4A5564));

            panel.addView(section(ctx, "UI大小"), blockParams());
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(s(2), s(2), s(2), s(2));

            uiScaleField = edit(ctx, String.valueOf(uiScale), ignored -> {});
            uiScaleField.setSingleLine(true);
            row.addView(uiScaleField, new LinearLayout.LayoutParams(0, s(28), 1.0F));

            Button confirm = button(ctx, "确认");
            confirm.setOnClickListener(v -> applyUiScaleFromField());
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(s(72), s(28));
            buttonParams.setMargins(s(6), 0, 0, 0);
            row.addView(confirm, buttonParams);
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
            return panel;
        }

        private void refreshFromExternalConfig() {
            if (rebuilding) return;
            if (centerPanel != null) rebuildCenter();
            if (rightPanel != null) rebuildRight();
        }

        private void rebuildNav() {
            navList.removeAllViews();
            for (ConfigType type : ConfigType.values()) {
                if (!isLeftNavType(type)) continue;
                Button button = button(getContext(), type.label);
                boolean selected = type == currentType && !injuredPage;
                selectedButton(button, selected);
                button.setOnClickListener(v -> {
                    if (!stageCurrentFields()) {
                        return;
                    }
                    currentType = type;
                    injuredPage = false;
                    stage = 1;
                    rebuildNav();
                    rebuildCenter();
                    rebuildRight();
                });
                navList.addView(button, blockParams());
            }
        }

        private void rebuildCenter() {
            rebuilding = true;
            fields.clear();
            centerPanel.removeAllViews();
            if (injuredPage) {
                centerPanel.addView(title(getContext(), "受伤血迹配置"), blockParams());
                buildInjuredBleeding(centerPanel);
                rebuilding = false;
                return;
            }
            centerPanel.addView(title(getContext(), currentType.label + "配置"), blockParams());
            if (usesStage(currentType)) addStageSelector(centerPanel, false);
            switch (currentType) {
                case EVIL_EYES -> buildEvil(centerPanel);
                case GAZE_GUIDANCE -> buildGaze(centerPanel);
                case MIRROR -> buildMirror(centerPanel);
                case THROUGH -> buildThrough(centerPanel);
                case IMITATE -> buildImitate(centerPanel);
                case FLOATING -> buildFloating(centerPanel);
                case SECRET -> buildSecret(centerPanel);
                case PLANT -> buildPlant(centerPanel);
                case PAINT -> buildPaint(centerPanel);
                case GRAVITY -> buildGravity(centerPanel);
            }
            rebuilding = false;
        }

        private void rebuildRight() {
            rebuilding = true;
            sideFields.clear();
            rightPanel.removeAllViews();
            rightPanel.addView(title(getContext(), "固定配置"), blockParams());
            buildStageRange(rightPanel);
            addDivider(rightPanel);
            Button textArea = button(getContext(), "文字区域界面");
            textArea.setOnClickListener(v -> TextAreaHudClient.openEditorAfterWorldFrame(MinecraftClient.getInstance().currentScreen));
            rightPanel.addView(textArea, blockParams());
            addDivider(rightPanel);
            addRightConfigButton("绘制配置", ConfigType.PAINT, false);
            addRightConfigButton("受伤血迹配置", currentType, true);
            Button playerArchive = button(getContext(), "玩家存档");
            playerArchive.setOnClickListener(v -> HotBackpackSaveClient.openAfterWorldFrame(MinecraftClient.getInstance().currentScreen));
            rightPanel.addView(playerArchive, blockParams());
            rebuilding = false;
        }

        private void addRightConfigButton(String label, ConfigType type, boolean injured) {
            Button button = button(getContext(), label);
            boolean selected = injured ? injuredPage : currentType == type && !injuredPage;
            selectedButton(button, selected);
            button.setOnClickListener(v -> {
                if (!stageCurrentFields()) {
                    return;
                }
                currentType = type;
                injuredPage = injured;
                stage = 1;
                rebuildNav();
                rebuildCenter();
                rebuildRight();
            });
            rightPanel.addView(button, blockParams());
        }

        private void buildEvil(LinearLayout parent) {
            GlobalConfigS2C.StageConfig cfg = evil(stage);
            addField(parent, "daily", "每日上限", cfg.dailyLimit());
            addField(parent, "marks", "最大标记", cfg.maxMarks());
            addField(parent, "uiDrain", "界面消耗/s", cfg.uiDrainRate());
            addField(parent, "watchDrain", "注视消耗/s", cfg.watchDrainRate());
            addField(parent, "regen", "恢复/s", cfg.regenRate());
            addField(parent, "parrotDaily", "鹦鹉每日", cfg.parrotDailyLimit());
            addSave(parent, "保存千里眼", this::saveEvil);
        }

        private void buildGaze(LinearLayout parent) {
            addField(parent, "drain", "每标记消耗", cachedGazeConfig.getEnergyDrain(stage));
            addField(parent, "regen", "恢复/s", cachedGazeConfig.getEnergyRegen(stage));
            addField(parent, "radius", "半径", cachedGazeConfig.getRadius(stage));
            addField(parent, "marks", "最大标记", cachedGazeConfig.getMaxMarks(stage));
            addSave(parent, "保存视线诱导", this::saveGaze);
        }

        private void buildMirror(LinearLayout parent) {
            addField(parent, "watch", "观察时间", cachedMirrorConfig.getWatchTime(stage));
            addField(parent, "success", "成功率", cachedMirrorConfig.getSuccessRate(stage));
            addField(parent, "count", "查看次数", cachedMirrorConfig.getViewCount(stage));
            addField(parent, "radius", "触发半径", cachedMirrorConfig.getRadius(stage));
            addField(parent, "charge", "充能tick", cachedMirrorConfig.getChargeTime(stage));
            addSave(parent, "保存镜子", this::saveMirror);
        }

        private void buildThrough(LinearLayout parent) {
            addField(parent, "speed", "速度倍率", cachedThroughConfig.getSpeedMultiplier(stage));
            addField(parent, "delay", "消失延迟/s", cachedThroughConfig.getVanishDelaySeconds(stage));
            addSave(parent, "保存穿墙", this::saveThrough);
        }

        private void buildImitate(LinearLayout parent) {
            addField(parent, "duration", "持续/s", cachedImitateConfig.getDuration(stage));
            addField(parent, "switchCooldown", "切换冷却/s", cachedImitateConfig.getSwitchCooldown(stage));
            addField(parent, "soundCooldown", "声波冷却/s", cachedImitateConfig.getSoundWaveCooldown(stage));
            addField(parent, "soundRadius", "声波半径", cachedImitateConfig.getSoundWaveRadius(stage));
            addField(parent, "soundDuration", "声波持续/s", cachedImitateConfig.getSoundWaveEffectDuration(stage));
            addField(parent, "silenceRadius", "沉默半径", cachedImitateConfig.getSilenceRadius(stage));
            addField(parent, "silenceDuration", "沉默持续/s", cachedImitateConfig.getSilenceDuration(stage));
            addField(parent, "silenceCooldown", "沉默冷却/s", cachedImitateConfig.getSilenceCooldown(stage));
            addField(parent, "imitateRadius", "模仿半径", cachedImitateConfig.getImitateRadius(stage));
            addField(parent, "soundUnlock", "声波解锁%", cachedImitateConfig.getSoundWaveUnlockThreshold());
            addField(parent, "silenceUnlock", "沉默解锁%", cachedImitateConfig.getSilenceUnlockThreshold());
            addField(parent, "areaUnlock", "区域选择解锁%", cachedImitateConfig.getAreaSelectUnlockThreshold());
            addSave(parent, "保存模仿", this::saveImitate);
        }

        private void buildFloating(LinearLayout parent) {
            addField(parent, "drain", "消耗/s", cachedFloatingConfig.getEnergyDrain(stage));
            addField(parent, "speed", "飞行速度", cachedFloatingConfig.getFlightSpeed(stage));
            addField(parent, "regen", "恢复/s", cachedFloatingConfig.getEnergyRegen(stage));
            addSave(parent, "保存漂浮", this::saveFloating);
        }

        private void buildSecret(LinearLayout parent) {
            addField(parent, "range", "范围", cachedSecretConfig.getRange(stage));
            addField(parent, "probability", "概率", cachedSecretConfig.getProbability(stage));
            addSave(parent, "保存窃密", this::saveSecret);
        }

        private void buildPlant(LinearLayout parent) {
            addField(parent, "speed", "叶片速度", cachedPlantMagicConfig.getLeafMoveSpeed(stage));
            addField(parent, "coverage", "覆盖角度", cachedPlantMagicConfig.getShellCoverageDegrees(stage));
            addField(parent, "cooldown", "冷却/s", cachedPlantMagicConfig.getCooldownSeconds(stage));
            addSave(parent, "保存植物", this::savePlant);
        }

        private void buildPaint(LinearLayout parent) {
            addField(parent, "consumption", "每像素消耗倍率", cachedPaintConfig.brushConsumptionMultiplier);
            addField(parent, "bucketLoads", "油漆桶取色次数", cachedPaintConfig.bucketBrushLoads);
            addSave(parent, "保存绘制", this::savePaint);
        }

        private void buildGravity(LinearLayout parent) {
            addField(parent, "maxForce", "力上限/xG", cachedGravityConfig.getMaxForce(stage));
            addField(parent, "duration", "作用时间/s", cachedGravityConfig.forceDurationSeconds);
            addField(parent, "damage", "每半心kJ", cachedGravityConfig.damageKilojoulesPerHalfHeart);
            addField(parent, "blockCount", "可抓取方块数", cachedGravityConfig.getMaxPickBlocks(stage));
            addField(parent, "hardness", "最高方块硬度", cachedGravityConfig.getMaxPickHardness(stage));
            addField(parent, "selfDrain", "自身受力消耗/s", cachedGravityConfig.getSelfForceDrain(stage));
            addField(parent, "regen", "能量恢复/s", cachedGravityConfig.getEnergyRegen(stage));
            addField(parent, "extractDrain", "方块提取消耗/s", cachedGravityConfig.getBlockExtractDrain(stage));
            addField(parent, "holdDrain", "方块悬浮消耗/s", cachedGravityConfig.getBlockHoldDrain(stage));
            addField(parent, "extractTicks", "提取时长/tick", cachedGravityConfig.getBlockExtractTicks(stage));
            addSave(parent, "保存重力", this::saveGravity);
        }

        private void buildStageRange(LinearLayout parent) {
            parent.addView(section(getContext(), "阶段区间配置"), blockParams());
            addStageSelector(parent, true);
            GlobalConfigS2C.StageConfig cfg = evil(rangeStage);
            addSideField(parent, "min", "触发区间下限", cfg.minScore());
            addSideField(parent, "max", "触发区间上限", cfg.maxScore());
            Button save = button(getContext(), "保存阶段区间");
            save.setOnClickListener(v -> saveStageRange());
            parent.addView(save, blockParams());
        }

        private void buildInjuredBleeding(LinearLayout parent) {
            if (parent == centerPanel || injuredPage) {
                addField(parent, "sprayRampUpSeconds", "喷洒加速时间/s", cachedInjuredBleedingConfig.sprayRampUpSeconds);
                addField(parent, "sprayDecaySeconds", "喷洒衰减时间/s", cachedInjuredBleedingConfig.sprayDecaySeconds);
                addField(parent, "particlesPerSecond", "每秒喷洒粒子数量", cachedInjuredBleedingConfig.particlesPerSecond);
                addField(parent, "bloodSpotFadeSeconds", "落地血点消失时间/s", cachedInjuredBleedingConfig.bloodSpotFadeSeconds);
                addField(parent, "bloodSpotButterflyChancePercent", "Blood butterfly chance/%", cachedInjuredBleedingConfig.bloodSpotButterflyChancePercent);
                addField(parent, "bloodButterflyLifetimeSeconds", "Blood butterfly lifetime/s", cachedInjuredBleedingConfig.bloodButterflyLifetimeSeconds);
                addEntitySelectorField(parent);
                Button save = button(getContext(), "保存受伤配置");
                save.setOnClickListener(v -> saveInjuredBleeding());
                parent.addView(save, blockParams());
                return;
            }
            parent.addView(section(getContext(), "受伤血迹配置"), blockParams());
            addSideField(parent, "sprayTicks", "喷洒时间/tick", cachedInjuredBleedingConfig.sprayTicks);
            Button save = button(getContext(), "保存受伤配置");
            save.setOnClickListener(v -> saveInjuredBleeding());
            parent.addView(save, blockParams());
        }
        private void addStageSelector(LinearLayout parent, boolean side) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, 0, s(5));
            for (int i = 1; i <= STAGES; i++) {
                int selected = side ? rangeStage : stage;
                int next = i;
                Button b = button(getContext(), String.valueOf(i));
                selectedButton(b, next == selected);
                b.setOnClickListener(v -> {
                    if (side) {
                        rangeStage = next;
                        rebuildRight();
                    } else {
                        if (!stageCurrentFields()) {
                            return;
                        }
                        stage = next;
                        rebuildCenter();
                    }
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, s(28), 1.0F);
                params.setMargins(0, 0, s(4), 0);
                row.addView(b, params);
            }
            parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        private void addField(LinearLayout parent, String key, String label, Object value) {
            if (currentType == ConfigType.GRAVITY && !canConfigureGravityBlockGroup() && isGravityBlockGroupField(key)) {
                return;
            }
            fields.put(key, addFieldView(parent, label, value));
        }

        private boolean canConfigureGravityBlockGroup() {
            return stage >= 6;
        }

        private static boolean isGravityBlockGroupField(String key) {
            return "blockCount".equals(key)
                    || "hardness".equals(key)
                    || "extractDrain".equals(key)
                    || "holdDrain".equals(key)
                    || "extractTicks".equals(key);
        }

        private void addSideField(LinearLayout parent, String key, String label, Object value) {
            sideFields.put(key, addFieldView(parent, label, value));
        }

        private void addEntitySelectorField(LinearLayout parent) {
            EditText field = addFieldView(parent, "Entity selector", cachedInjuredBleedingConfig.entitySelector);
            fields.put("entitySelector", field);
            entitySelectorHint = label(getContext(), "", 11, 0xFF9FB2C8);
            entitySelectorHint.setPadding(s(136), 0, s(4), s(6));
            parent.addView(entitySelectorHint, new LinearLayout.LayoutParams(-1, -2));
            field.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    updateEntitySelectorPreview(s.toString());
                }
            });
            field.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode == GLFW.GLFW_KEY_TAB && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    applyEntitySelectorSuggestion(field);
                    return true;
                }
                return false;
            });
            updateEntitySelectorPreview(text(field));
        }

        private void updateEntitySelectorPreview(String input) {
            entitySelectorSuggestions = entitySelectorSuggestions(input);
            if (entitySelectorHint == null) {
                return;
            }
            String text = InjuredBleedingConfig.normalizeEntitySelector(input);
            if (text.isEmpty()) {
                entitySelectorHint.setText("Preview: all living entities | Tab: @e[type=]");
                entitySelectorHint.setTextColor(0xFF9FB2C8);
                return;
            }
            SelectorPreview preview = previewEntitySelector(text);
            if (preview.error() != null) {
                entitySelectorHint.setText("Error: " + preview.error());
                entitySelectorHint.setTextColor(0xFFFF8A8A);
                return;
            }
            String suggestionText = entitySelectorSuggestions.isEmpty()
                    ? ""
                    : " | Tab: " + String.join("  ", entitySelectorSuggestions.subList(0, Math.min(4, entitySelectorSuggestions.size())));
            entitySelectorHint.setText("Preview: " + preview.summary() + suggestionText);
            entitySelectorHint.setTextColor(0xFF9FE3A7);
        }

        private void applyEntitySelectorSuggestion(EditText field) {
            if (entitySelectorSuggestions.isEmpty()) {
                updateEntitySelectorPreview(text(field));
                return;
            }
            String current = text(field);
            String completed = applySuggestion(current, entitySelectorSuggestions.get(0));
            field.setText(completed);
            field.setSelection(completed.length());
            updateEntitySelectorPreview(completed);
        }

        private static String applySuggestion(String input, String suggestion) {
            String text = input == null ? "" : input;
            int start = completionStart(text);
            return text.substring(0, start) + suggestion;
        }

        private static int completionStart(String input) {
            if (input == null || input.isEmpty()) {
                return 0;
            }
            int comma = input.lastIndexOf(',');
            int bracket = input.lastIndexOf('[');
            int equals = input.lastIndexOf('=');
            int start = Math.max(comma + 1, bracket + 1);
            if (equals >= start) {
                start = equals + 1;
            }
            while (start < input.length() && input.charAt(start) == ' ') {
                start++;
            }
            return start;
        }

        private static SelectorPreview previewEntitySelector(String input) {
            if (!input.startsWith("@")) {
                return new SelectorPreview(null, "Selector must start with @");
            }
            int open = input.indexOf('[');
            int close = input.lastIndexOf(']');
            String root = open >= 0 ? input.substring(0, open) : input;
            if (!List.of("@p", "@a", "@r", "@s", "@e", "@n").contains(root)) {
                return new SelectorPreview(null, "Unknown selector root " + root);
            }
            if (open >= 0 && close < open) {
                return new SelectorPreview(null, "Missing closing ]");
            }
            String args = open >= 0 && close > open ? input.substring(open + 1, close) : "";
            String type = optionValue(args, "type");
            String name = optionValue(args, "name");
            String tag = optionValue(args, "tag");
            List<String> parts = new ArrayList<>();
            parts.add(root);
            if (!type.isBlank()) parts.add("type=" + type);
            if (!name.isBlank()) parts.add("name=" + name);
            if (!tag.isBlank()) parts.add("tag=" + tag);
            return new SelectorPreview(String.join(" ", parts), null);
        }

        private static String optionValue(String args, String key) {
            if (args == null || args.isBlank()) {
                return "";
            }
            for (String part : args.split(",")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(key + "=")) {
                    return trimmed.substring(key.length() + 1);
                }
            }
            return "";
        }

        private static List<String> entitySelectorSuggestions(String input) {
            String text = input == null ? "" : input;
            int start = completionStart(text);
            String prefix = text.substring(start).trim();
            if (!text.startsWith("@")) {
                return filter(List.of("@e[type=", "@p", "@a", "@s"), prefix);
            }
            int open = text.lastIndexOf('[');
            int equals = text.lastIndexOf('=');
            if (open >= 0 && equals >= open && equals >= text.lastIndexOf(',')) {
                String key = selectorKeyBeforeEquals(text, equals);
                if ("type".equals(key)) {
                    return Registries.ENTITY_TYPE.getIds().stream()
                            .map(Identifier::toString)
                            .filter(id -> id.startsWith(prefix) || id.startsWith("minecraft:" + prefix))
                            .sorted(Comparator.naturalOrder())
                            .limit(16)
                            .collect(Collectors.toList());
                }
                if ("sort".equals(key)) {
                    return filter(List.of("nearest", "furthest", "random", "arbitrary"), prefix);
                }
                return List.of();
            }
            if (open >= 0) {
                return filter(List.of("type=", "name=", "tag=", "distance=", "sort=", "limit="), prefix);
            }
            return filter(List.of("@e[type=", "@p", "@a", "@r", "@s"), prefix);
        }

        private static String selectorKeyBeforeEquals(String text, int equals) {
            int comma = text.lastIndexOf(',', equals);
            int bracket = text.lastIndexOf('[', equals);
            int start = Math.max(comma, bracket) + 1;
            return text.substring(start, equals).trim();
        }

        private static List<String> filter(List<String> values, String prefix) {
            if (prefix == null || prefix.isBlank()) {
                return values;
            }
            return values.stream().filter(value -> value.startsWith(prefix)).collect(Collectors.toList());
        }
        private EditText addFieldView(LinearLayout parent, String labelText, Object value) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(s(2), s(2), s(2), s(2));
            row.setMinimumHeight(s(28));
            row.setBackground(panelShape(0x332A3038, 0x223A4350));
            TextView label = label(getContext(), labelText, 12, 0xFFB6C0CC);
            label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            label.setSingleLine(false);
            label.setMinLines(1);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(s(132), -2);
            labelParams.setMargins(0, 0, s(8), 0);
            row.addView(label, labelParams);
            EditText field = edit(getContext(), String.valueOf(value), ignored -> {});
            field.setSingleLine(false);
            field.setMinLines(1);
            field.setMaxLines(4);
            field.setMinHeight(s(28));
            row.addView(field, new LinearLayout.LayoutParams(0, -2, 0.6F));
            row.addView(new View(getContext()), new LinearLayout.LayoutParams(0, s(1), 0.4F));
            parent.addView(row, blockParams());
            return field;
        }

        private void addSave(LinearLayout parent, String label, Runnable action) {
            Button save = button(getContext(), label);
            save.setOnClickListener(v -> action.run());
            parent.addView(save, blockParams());
        }

        private boolean stageCurrentFields() {
            if (rebuilding || injuredPage || fields.isEmpty()) {
                return true;
            }
            try {
                switch (currentType) {
                    case EVIL_EYES -> stageEvilFields();
                    case GAZE_GUIDANCE -> stageGazeFields();
                    case MIRROR -> stageMirrorFields();
                    case THROUGH -> stageThroughFields();
                    case IMITATE -> stageImitateFields();
                    case FLOATING -> stageFloatingFields();
                    case SECRET -> stageSecretFields();
                    case PLANT -> stagePlantFields();
                    case PAINT -> stagePaintFields();
                    case GRAVITY -> stageGravityFields();
                }
                return true;
            } catch (NumberFormatException e) {
                invalid();
                return false;
            }
        }

        private void stageEvilFields() {
            GlobalConfigS2C.StageConfig old = evil(stage);
            CACHED_EVIL_CONFIGS[stage] = new GlobalConfigS2C.StageConfig(
                    intField("daily"), intField("marks"), old.minScore(), old.maxScore(),
                    old.watchRequiredTicks(), intField("parrotDaily"), old.maxActiveParrots(),
                    doubleField("uiDrain"), doubleField("watchDrain"), doubleField("regen"));
        }

        private void stageGazeFields() {
            cachedGazeConfig.setEnergyDrain(stage, doubleField("drain"));
            cachedGazeConfig.setEnergyRegen(stage, doubleField("regen"));
            cachedGazeConfig.setRadius(stage, doubleField("radius"));
            cachedGazeConfig.setMaxMarks(stage, intField("marks"));
        }

        private void stageMirrorFields() {
            cachedMirrorConfig.setWatchTime(stage, intField("watch"));
            cachedMirrorConfig.setSuccessRate(stage, doubleField("success"));
            cachedMirrorConfig.setViewCount(stage, intField("count"));
            cachedMirrorConfig.setRadius(stage, doubleField("radius"));
            cachedMirrorConfig.setChargeTime(stage, intField("charge"));
        }

        private void stageThroughFields() {
            cachedThroughConfig.setSpeedMultiplier(stage, doubleField("speed"));
            cachedThroughConfig.setVanishDelaySeconds(stage, intField("delay"));
        }

        private void stageImitateFields() {
            cachedImitateConfig.setDuration(stage, intField("duration"));
            cachedImitateConfig.setSwitchCooldown(stage, intField("switchCooldown"));
            cachedImitateConfig.setSoundWaveCooldown(stage, intField("soundCooldown"));
            cachedImitateConfig.setSoundWaveRadius(stage, doubleField("soundRadius"));
            cachedImitateConfig.setSoundWaveEffectDuration(stage, intField("soundDuration"));
            cachedImitateConfig.setSilenceRadius(stage, doubleField("silenceRadius"));
            cachedImitateConfig.setSilenceDuration(stage, intField("silenceDuration"));
            cachedImitateConfig.setSilenceCooldown(stage, intField("silenceCooldown"));
            cachedImitateConfig.setImitateRadius(stage, doubleField("imitateRadius"));
            cachedImitateConfig.setSoundWaveUnlockThreshold(intField("soundUnlock"));
            cachedImitateConfig.setSilenceUnlockThreshold(intField("silenceUnlock"));
            cachedImitateConfig.setAreaSelectUnlockThreshold(intField("areaUnlock"));
        }

        private void stageFloatingFields() {
            cachedFloatingConfig.setEnergyDrain(stage, doubleField("drain"));
            cachedFloatingConfig.setFlightSpeed(stage, (float) doubleField("speed"));
            cachedFloatingConfig.setEnergyRegen(stage, doubleField("regen"));
        }

        private void stageSecretFields() {
            cachedSecretConfig.setRange(stage, intField("range"));
            cachedSecretConfig.setProbability(stage, doubleField("probability"));
        }

        private void stagePlantFields() {
            cachedPlantMagicConfig.setLeafMoveSpeed(stage, doubleField("speed"));
            cachedPlantMagicConfig.setShellCoverageDegrees(stage, doubleField("coverage"));
            cachedPlantMagicConfig.setCooldownSeconds(stage, intField("cooldown"));
        }

        private void stagePaintFields() {
            PaintConfig config = new PaintConfig();
            config.brushConsumptionMultiplier = doubleField("consumption");
            config.bucketBrushLoads = intField("bucketLoads");
            cachedPaintConfig = PaintConfig.fromJson(config.toJson());
        }

        private void stageGravityFields() {
            cachedGravityConfig.forceDurationSeconds = intField("duration");
            cachedGravityConfig.damageKilojoulesPerHalfHeart = doubleField("damage");
            cachedGravityConfig.setMaxForce(stage, doubleField("maxForce"));
            cachedGravityConfig.setSelfForceDrain(stage, doubleField("selfDrain"));
            cachedGravityConfig.setEnergyRegen(stage, doubleField("regen"));
            if (canConfigureGravityBlockGroup()) {
                cachedGravityConfig.setMaxPickBlocks(stage, intField("blockCount"));
                cachedGravityConfig.setMaxPickHardness(stage, doubleField("hardness"));
                cachedGravityConfig.setBlockExtractDrain(stage, doubleField("extractDrain"));
                cachedGravityConfig.setBlockHoldDrain(stage, doubleField("holdDrain"));
                cachedGravityConfig.setBlockExtractTicks(stage, intField("extractTicks"));
            }
        }

        private void saveEvil() {
            try {
                GlobalConfigS2C.StageConfig old = evil(stage);
                GlobalConfigS2C.StageConfig updated = new GlobalConfigS2C.StageConfig(
                        intField("daily"), intField("marks"), old.minScore(), old.maxScore(),
                        old.watchRequiredTicks(), intField("parrotDaily"), old.maxActiveParrots(),
                        doubleField("uiDrain"), doubleField("watchDrain"), doubleField("regen"));
                CACHED_EVIL_CONFIGS[stage] = updated;
                for (int i = 1; i <= STAGES; i++) {
                    ClientPlayNetworking.send(general_stage.toUpdatePacket(i, evil(i)));
                }
                message("千里眼配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void saveGaze() {
            try {
                stageGazeFields();
                ClientPlayNetworking.send(new UpdateConfigC2SPacket(cachedGazeConfig.toJson()));
                message("视线诱导配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void saveMirror() {
            try {
                stageMirrorFields();
                ClientPlayNetworking.send(new MirrorPackets.ConfigUpdateC2S(cachedMirrorConfig.toJson()));
                message("镜子配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void saveThrough() {
            try {
                stageThroughFields();
                ClientPlayNetworking.send(new ThroughConfigUpdateC2SPacket(cachedThroughConfig.toJson()));
                message("穿墙配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void saveImitate() {
            try {
                stageImitateFields();
                ClientPlayNetworking.send(new UpdateImitateConfigC2SPacket(cachedImitateConfig.toJson()));
                message("模仿配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void saveFloating() {
            try {
                stageFloatingFields();
                ClientPlayNetworking.send(new FloatingPackets.UpdateConfigC2S(cachedFloatingConfig.toJson()));
                message("漂浮配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void saveSecret() {
            try {
                stageSecretFields();
                ClientPlayNetworking.send(new SecretPackets.UpdateConfigC2S(cachedSecretConfig.toJson()));
                message("窃密配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void savePlant() {
            try {
                stagePlantFields();
                ClientPlayNetworking.send(new PlantMagicPackets.UpdateConfigC2S(cachedPlantMagicConfig.toJson()));
                message("植物配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void savePaint() {
            try {
                stagePaintFields();
                ClientPlayNetworking.send(new PaintOverlayPackets.UpdatePaintConfigC2S(cachedPaintConfig.toJson()));
                message("绘制配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void saveGravity() {
            try {
                stageGravityFields();
                cachedGravityConfig = GravityConfig.fromJson(cachedGravityConfig.toJson());
                ClientPlayNetworking.send(new GravityPackets.UpdateConfigC2S(cachedGravityConfig.toJson()));
                message("重力配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void saveStageRange() {
            try {
                int min = sideInt("min");
                int max = sideInt("max");
                GlobalConfigS2C.StageConfig updated = general_stage.withScoreRange(evil(rangeStage), min, max);
                CACHED_EVIL_CONFIGS[rangeStage] = updated;
                general_stage.sendUpdate(rangeStage, updated);
                message("阶段区间配置已提交");
                rebuildRight();
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void saveInjuredBleeding() {
            try {
                InjuredBleedingConfig config = new InjuredBleedingConfig();
                config.sprayRampUpSeconds = doubleField("sprayRampUpSeconds");
                config.sprayDecaySeconds = doubleField("sprayDecaySeconds");
                config.spraySeconds = config.sprayRampUpSeconds + config.sprayDecaySeconds;
                config.particlesPerSecond = intField("particlesPerSecond");
                config.bloodSpotFadeSeconds = doubleField("bloodSpotFadeSeconds");
                config.bloodSpotButterflyChancePercent = doubleField("bloodSpotButterflyChancePercent");
                config.bloodButterflyLifetimeSeconds = doubleField("bloodButterflyLifetimeSeconds");
                config.entitySelector = text(fields.get("entitySelector"));
                cachedInjuredBleedingConfig = InjuredBleedingConfig.fromJson(config.toJson());
                ClientPlayNetworking.send(new InjuredBleedingPackets.UpdateConfigC2S(cachedInjuredBleedingConfig.toJson()));
                message("受伤血迹配置已提交");
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void applyUiScaleFromField() {
            try {
                uiScale = Math.clamp(Float.parseFloat(text(uiScaleField)), 0.6F, 1.6F);
                UI_STATE.uiScale = uiScale;
                UI_STATE.save();
                if (uiScaleField != null) uiScaleField.setText(String.valueOf(uiScale));
                rebuildScaledLayout();
            } catch (NumberFormatException e) {
                invalid();
            }
        }

        private void rebuildScaledLayout() {
            if (rootLayout == null) return;
            rootLayout.removeAllViews();
            rootLayout.setPadding(s(10), s(10), s(10), s(10));
            addColumns(rootLayout, getContext());
        }

        private GlobalConfigS2C.StageConfig evil(int stage) {
            GlobalConfigS2C.StageConfig cfg = CACHED_EVIL_CONFIGS[Math.clamp(stage, 1, STAGES)];
            if (cfg == null) {
                return new GlobalConfigS2C.StageConfig(10, 3, 0, 5, 40, 5, 1, 1.0D, 8.0D, 2.0D);
            }
            return cfg;
        }

        private int intField(String key) {
            return Integer.parseInt(text(fields.get(key)));
        }

        private double doubleField(String key) {
            return Double.parseDouble(text(fields.get(key)));
        }

        private int sideInt(String key) {
            return Integer.parseInt(text(sideFields.get(key)));
        }

        private static String text(EditText field) {
            return field == null ? "" : field.getText().toString().trim();
        }

        private static boolean usesStage(ConfigType type) {
            return type != ConfigType.PAINT;
        }

        private static boolean isLeftNavType(ConfigType type) {
            return type != ConfigType.PAINT;
        }

        private void message(String text) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) client.player.sendMessage(Text.literal("§a" + text), true);
        }

        private void invalid() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) client.player.sendMessage(Text.literal("§c请输入有效数字"), true);
        }

        private record SelectorPreview(String summary, String error) {
        }
    }

    private static LinearLayout panel(Context ctx) {
        LinearLayout panel = vertical(ctx);
        panel.setPadding(s(8), s(8), s(8), s(8));
        panel.setBackground(panelShape(0xE0181B22, 0xFF343B46));
        return panel;
    }

    private static ScrollView scroll(Context ctx) {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setBackground(panelShape(0x441A2028, 0x553A4350));
        return scroll;
    }

    private static Button button(Context ctx, String text) {
        Button button = new Button(ctx);
        button.setText(text);
        button.setTextColor(0xFFE8EDF4);
        button.setTextSize(sf(12));
        button.setPadding(s(6), s(2), s(6), s(2));
        button.setMinHeight(s(26));
        button.setBackground(panelShape(0x55313A4A, 0xB4AAB7C8));
        return button;
    }

    private static void selectedButton(Button button, boolean selected) {
        button.setTextColor(selected ? 0xFFB8FAFF : 0xFFE8EDF4);
        button.setBackground(panelShape(selected ? 0xCC155A66 : 0x55313A4A, selected ? 0xFF3FE9F4 : 0x663A4350));
    }

    private static EditText edit(Context ctx, String value, Consumer<String> consumer) {
        EditText field = new EditText(ctx);
        field.setText(value == null ? "" : value);
        field.setTextColor(0xFFE8EDF4);
        field.setTextSize(sf(12));
        field.setSingleLine(true);
        field.setPadding(s(5), s(2), s(5), s(2));
        field.setBackground(panelShape(0xDD151920, 0xFF3A4350));
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                consumer.accept(s.toString());
            }
        });
        return field;
    }

    private static TextView title(Context ctx, String text) {
        TextView label = label(ctx, text, 16, 0xFFE8EDF4);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private static TextView section(Context ctx, String text) {
        TextView label = label(ctx, text, 13, 0xFFFFD36A);
        label.setPadding(s(2), s(6), 0, s(2));
        return label;
    }

    private static TextView label(Context ctx, String text, int size, int color) {
        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(sf(size));
        label.setTextColor(color);
        return label;
    }

    private static LinearLayout vertical(Context ctx) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, s(6));
        return params;
    }

    private static void addDivider(LinearLayout parent) {
        TextView divider = new TextView(parent.getContext());
        divider.setText("");
        divider.setBackground(new ColorDrawable(0xFF303742));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, s(1));
        params.setMargins(0, s(8), 0, s(8));
        parent.addView(divider, params);
    }

    private static ShapeDrawable panelShape(int fill, int stroke) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setShape(ShapeDrawable.RECTANGLE);
        shape.setColor(fill);
        shape.setStroke(1, stroke);
        shape.setCornerRadius(sf(3.0F));
        return shape;
    }

    private static int s(int value) {
        return Math.max(1, Math.round(value * uiScale));
    }

    private static float sf(float value) {
        return value * uiScale;
    }

    private static final class ScreenUiState {
        private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("monvhua_combined_config_screen.json");
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

        private float uiScale = 1.0F;

        private static ScreenUiState load() {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                    ScreenUiState state = GSON.fromJson(reader, ScreenUiState.class);
                    if (state != null) {
                        state.uiScale = clampUiScale(state.uiScale);
                        return state;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            ScreenUiState state = new ScreenUiState();
            state.save();
            return state;
        }

        private void save() {
            uiScale = clampUiScale(uiScale);
            try {
                Files.createDirectories(CONFIG_PATH.getParent());
                try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                    GSON.toJson(this, writer);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static float clampUiScale(float value) {
            if (!Float.isFinite(value)) {
                return 1.0F;
            }
            return Math.clamp(value, 0.6F, 1.6F);
        }
    }
}
