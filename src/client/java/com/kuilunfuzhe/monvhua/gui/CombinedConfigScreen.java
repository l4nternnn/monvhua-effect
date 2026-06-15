package com.kuilunfuzhe.monvhua.gui;

import com.kuilunfuzhe.monvhua.item.config.GazeConfig;
import com.kuilunfuzhe.monvhua.item.config.FloatingConfig;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import com.kuilunfuzhe.monvhua.item.config.MirrorConfig;
import com.kuilunfuzhe.monvhua.item.config.PlantMagicConfig;
import com.kuilunfuzhe.monvhua.item.config.ThroughConfig;
import com.kuilunfuzhe.monvhua.item.config.SecretConfig;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.GlobalConfigS2C;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.RequestGlobalConfigC2S;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.UpdateGlobalConfigC2S;
import com.kuilunfuzhe.monvhua.network.gazeguidance.RequestConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.gazeguidance.UpdateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.floating.FloatingPackets;
import com.kuilunfuzhe.monvhua.network.imitate.RequestImitateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.imitate.UpdateImitateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets.ConfigUpdateC2S;
import com.kuilunfuzhe.monvhua.network.plant.PlantMagicPackets;
import com.kuilunfuzhe.monvhua.network.secret.SecretPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets.RequestConfigC2S;
import com.kuilunfuzhe.monvhua.network.through.RequestThroughConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.through.ThroughConfigUpdateC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合配置界面，通过顶部标签页切换管理五个子配置模块：
 * 千里眼（全局阶段配置）、视线诱导、镜子、穿墙、阶段区间。
 * 每个子模块都有独立的7阶段配置、缓存和网络收发逻辑。
 */
public class CombinedConfigScreen extends Screen {
    /** 配置类型枚举，对应顶部五个标签页 */
    private enum ConfigType { EVIL_EYES, GAZE_GUIDANCE, MIRROR, THROUGH, STAGE_RANGE, IMITATE, FLOATING, SECRET, PLANT }
    private ConfigType currentType = ConfigType.EVIL_EYES;

    private ButtonWidget btnEvilEyes, btnGaze, btnMirror, btnThrough, btnStageRange, btnImitate, btnFloating, btnSecret, btnPlant;
    private int panelX, panelY, panelWidth, panelHeight;
    private int leftWidth, rightWidth;

    // ===== 千里眼组件 =====
    private final List<ButtonWidget> stageButtons = new ArrayList<>();
    private int currentStage = 1;
    private TextFieldWidget dailyField, marksField, uiDrainField, watchDrainField, regenField, parrotDailyField;
    private ButtonWidget saveEvilButton;
    /** 已缓存的千里眼全局配置数据，索引1-7对应阶段1-7 */
    private static final GlobalConfigS2C.StageConfig[] CACHED_EVIL_CONFIGS = new GlobalConfigS2C.StageConfig[8];
    private final List<TextWidget> evilLabels = new ArrayList<>();

    // ===== 视线诱导组件 =====
    private final List<ButtonWidget> gazeStageButtons = new ArrayList<>();
    private int gazeCurrentStage = 1;
    private TextFieldWidget gazeDrainField, gazeRegenField, gazeRadiusField, gazeMarksField;
    private ButtonWidget saveGazeButton;
    private final List<TextWidget> gazeLabels = new ArrayList<>();
    private static GazeConfig cachedGazeConfig = null;

    // ===== 镜子配置组件 =====
    private final List<ButtonWidget> mirrorStageButtons = new ArrayList<>();
    private int mirrorCurrentStage = 1;
    private TextFieldWidget mirrorWatchTimeField, mirrorSuccessRateField, mirrorViewCountField, mirrorRadiusField, mirrorChargeTimeField;
    private ButtonWidget saveMirrorButton;
    private final List<TextWidget> mirrorLabels = new ArrayList<>();
    private static MirrorConfig cachedMirrorConfig = null;

    // ===== 阶段区间组件 =====
    private final List<ButtonWidget> stageRangeStageButtons = new ArrayList<>();
    private int stageRangeCurrentStage = 1;
    private TextFieldWidget stageRangeMinField, stageRangeMaxField;
    private ButtonWidget saveStageRangeButton;
    private final List<TextWidget> stageRangeLabels = new ArrayList<>();

    // ===== 穿墙配置组件 =====
    private final List<ButtonWidget> throughStageButtons = new ArrayList<>();
    private int throughCurrentStage = 1;
    private TextFieldWidget throughSpeedMultiplierField, throughDelayField;
    private ButtonWidget saveThroughButton;
    private final List<TextWidget> throughLabels = new ArrayList<>();
    private static ThroughConfig cachedThroughConfig = null;

    // ===== 模仿魔法配置组件 =====
    private final List<ButtonWidget> imitateStageButtons = new ArrayList<>();
    private int imitateCurrentStage = 1;
    private TextFieldWidget imitateDurationField, imitateSwitchCooldownField, imitateSoundWaveCooldownField;
    private TextFieldWidget imitateSoundWaveRadiusField, imitateSoundWaveEffectDurationField;
    private TextFieldWidget imitateSilenceRadiusField, imitateSilenceDurationField, imitateSilenceCooldownField;
    private ButtonWidget saveImitateButton;
    private final List<TextWidget> imitateLabels = new ArrayList<>();
    private static ImitateConfig cachedImitateConfig = null;

    private final List<ButtonWidget> floatingStageButtons = new ArrayList<>();
    private int floatingCurrentStage = 1;
    private TextFieldWidget floatingDrainField, floatingSpeedField, floatingRegenField;
    private ButtonWidget saveFloatingButton;
    private final List<TextWidget> floatingLabels = new ArrayList<>();
    private static FloatingConfig cachedFloatingConfig = null;

    private final List<ButtonWidget> plantStageButtons = new ArrayList<>();
    private int plantCurrentStage = 1;
    private TextFieldWidget plantMoveSpeedField, plantCoverageField, plantCooldownField;
    private ButtonWidget savePlantButton;
    private final List<TextWidget> plantLabels = new ArrayList<>();
    private static PlantMagicConfig cachedPlantMagicConfig = null;

    // ===== 窃密配置组件 =====
    private final List<ButtonWidget> secretStageButtons = new ArrayList<>();
    private int secretCurrentStage = 1;
    private TextFieldWidget secretRangeField, secretProbabilityField;
    private ButtonWidget saveSecretButton;
    private final List<TextWidget> secretLabels = new ArrayList<>();
    public static SecretConfig cachedSecretConfig = null;

    /**
     * 构造聚合配置界面，初始化时向服务器请求四种配置数据，
     * 本地缓存未就绪时使用默认值填充。
     */
    public CombinedConfigScreen() {
        super(Text.literal("物品配置"));
        ClientPlayNetworking.send(new RequestGlobalConfigC2S());
        ClientPlayNetworking.send(new RequestConfigC2SPacket());
        ClientPlayNetworking.send(new RequestConfigC2S());
        ClientPlayNetworking.send(new RequestThroughConfigC2SPacket());
        ClientPlayNetworking.send(new RequestImitateConfigC2SPacket());
        ClientPlayNetworking.send(new SecretPackets.RequestConfigC2S());
        ClientPlayNetworking.send(new FloatingPackets.RequestConfigC2S());
        ClientPlayNetworking.send(new PlantMagicPackets.RequestConfigC2S());
        if (cachedGazeConfig == null) {
            cachedGazeConfig = createDefaultGazeConfig();
        }
        if (cachedMirrorConfig == null) {
            cachedMirrorConfig = createDefaultMirrorConfig();
        }
        if (cachedThroughConfig == null) {
            cachedThroughConfig = createDefaultThroughConfig();
        }
        if (cachedImitateConfig == null) {
            cachedImitateConfig = createDefaultImitateConfig();
        }
        if (cachedFloatingConfig == null) {
            cachedFloatingConfig = new FloatingConfig();
        }
        if (cachedPlantMagicConfig == null) {
            cachedPlantMagicConfig = new PlantMagicConfig();
        }
        if (cachedSecretConfig == null) {
            cachedSecretConfig = new SecretConfig();
        }
    }

    /**
     * 创建视线诱导的默认配置（用于本地缓存未就绪时的占位数据）。
     * 各阶段的值按递增规则生成：阶段越高，消耗越少、范围越大、标记数越多。
     */
    private GazeConfig createDefaultGazeConfig() {
        GazeConfig config = new GazeConfig();
        for (int i = 0; i < 7; i++) {
            int stage = i + 1;
            config.stages[i] = new GazeConfig.StageConfig();
            config.stages[i].name = "阶段 " + stage;
            config.stages[i].energyDrainPerMark = stage;
            config.stages[i].energyRegenPerSecond = 2 + stage;
            config.stages[i].radius = 5 + (stage - 1) * 2.0;
            config.stages[i].maxMarks = stage <= 2 ? 3 : (stage <= 4 ? 6 : 15);
        }
        return config;
    }

    /**
     * 创建镜子的默认配置，各阶段参数随阶段递增而改善：
     * 观看时间越短、成功率越高、观看次数和触发半径越大。
     */
    private MirrorConfig createDefaultMirrorConfig() {
        MirrorConfig config = new MirrorConfig();
        for (int i = 0; i < 7; i++) {
            int stage = i + 1;
            config.stages[i] = new MirrorConfig.StageConfig();
            config.stages[i].watchTime = Math.max(1, 5 - stage);
            config.stages[i].successRate = 0.1 + stage * 0.1;
            config.stages[i].viewCount = stage <= 2 ? 1 : (stage <= 4 ? 3 : 5);
            config.stages[i].radius = 5.0 + stage * 2.0;
        }
        return config;
    }

    /**
     * 创建穿墙的默认配置，概率上限为1.0（100%），隐身延迟随阶段增加而减少。
     */
    private ThroughConfig createDefaultThroughConfig() {
        ThroughConfig config = new ThroughConfig();
        for (int i = 0; i < 7; i++) {
            int stage = i + 1;
            config.stages[i] = new ThroughConfig.StageConfig();
            config.stages[i].speedMultiplier = 0.1D;
            config.stages[i].vanishDelaySeconds = Math.max(1, 8 - stage);
        }
        return config;
    }

    private ImitateConfig createDefaultImitateConfig() {
        ImitateConfig config = new ImitateConfig();
        for (int i = 0; i < 7; i++) {
            int stage = i + 1;
            config.stages[i] = new ImitateConfig.StageConfig();
            config.stages[i].name = "阶段 " + stage;
            config.stages[i].durationSeconds = 0;
            config.stages[i].switchCooldownSeconds = 0;
            config.stages[i].soundWaveCooldownSeconds = 0;
        }
        return config;
    }

    @Override
    protected void init() {
        super.init();
        int sw = this.client.getWindow().getScaledWidth();
        int sh = this.client.getWindow().getScaledHeight();
        // 主面板占屏幕宽度的3/5，保持16:9比例
        panelWidth = sw * 3 / 5;
        panelHeight = (int)(panelWidth * 9f / 16f);
        // 向左偏移100px为左侧阶段按钮留空间
        panelX = (sw - panelWidth) / 2 - 100;
        panelY = (sh - panelHeight) / 2;

        // 左侧阶段选择栏宽度和右侧配置区宽度
        leftWidth = 140;
        rightWidth = panelWidth - leftWidth - 15;  // 15px 分隔线间距

        // 顶部标签按钮尺寸
        int btnWidth = 60, btnHeight = 20;
        // 按钮放在面板上方5px处
        int btnY = panelY - btnHeight - 5;
        int centerX = panelX + panelWidth / 2;
        int tabGap = 4;
        int tabCount = 9;
        int tabStartX = centerX - (btnWidth * tabCount + tabGap * (tabCount - 1)) / 2;

        btnEvilEyes = ButtonWidget.builder(Text.literal("千里眼"), btn -> switchTo(ConfigType.EVIL_EYES))
                .dimensions(tabStartX, btnY, btnWidth, btnHeight).build();
        btnGaze = ButtonWidget.builder(Text.literal("视线诱导"), btn -> switchTo(ConfigType.GAZE_GUIDANCE))
                .dimensions(tabStartX + (btnWidth + tabGap), btnY, btnWidth, btnHeight).build();
        btnMirror = ButtonWidget.builder(Text.literal("镜子"), btn -> switchTo(ConfigType.MIRROR))
                .dimensions(tabStartX + (btnWidth + tabGap) * 2, btnY, btnWidth, btnHeight).build();
        btnThrough = ButtonWidget.builder(Text.literal("穿墙"), btn -> switchTo(ConfigType.THROUGH))
                .dimensions(tabStartX + (btnWidth + tabGap) * 3, btnY, btnWidth, btnHeight).build();
        btnStageRange = ButtonWidget.builder(Text.literal("阶段区间"), btn -> switchTo(ConfigType.STAGE_RANGE))
                .dimensions(tabStartX + (btnWidth + tabGap) * 7, btnY, btnWidth, btnHeight).build();
        btnImitate = ButtonWidget.builder(Text.literal("模仿"), btn -> switchTo(ConfigType.IMITATE))
                .dimensions(tabStartX + (btnWidth + tabGap) * 4, btnY, btnWidth, btnHeight).build();
        btnSecret = ButtonWidget.builder(Text.literal("窃密"), btn -> switchTo(ConfigType.SECRET))
                .dimensions(tabStartX + (btnWidth + tabGap) * 5, btnY, btnWidth, btnHeight).build();
        btnFloating = ButtonWidget.builder(Text.literal("漂浮"), btn -> switchTo(ConfigType.FLOATING))
                .dimensions(tabStartX + (btnWidth + tabGap) * 6, btnY, btnWidth, btnHeight).build();
        btnPlant = ButtonWidget.builder(Text.literal("\u690d\u7269"), btn -> switchTo(ConfigType.PLANT))
                .dimensions(tabStartX + (btnWidth + tabGap) * 7, btnY, btnWidth, btnHeight).build();
        btnStageRange.setX(tabStartX + (btnWidth + tabGap) * 8);
        addDrawableChild(btnEvilEyes);
        addDrawableChild(btnGaze);
        addDrawableChild(btnMirror);
        addDrawableChild(btnThrough);
        addDrawableChild(btnStageRange);
        addDrawableChild(btnImitate);
        addDrawableChild(btnSecret);
        addDrawableChild(btnFloating);
        addDrawableChild(btnPlant);

        buildEvilEyesUI();
        buildGazeGuidanceUI();
        buildMirrorUI();
        buildThroughUI();
        buildStageRangeUI();
        buildImitateUI();
        buildFloatingUI();
        buildPlantMagicUI();
        buildSecretUI();
        switchTo(ConfigType.EVIL_EYES);
    }

    // ==================== 千里眼 UI ====================
    private int stageButtonY(int stage) {
        int top = panelY + 12;
        int bottom = panelY + panelHeight - 32;
        return top + (stage - 1) * (bottom - top) / 6;
    }

    private void buildEvilEyesUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("阶段 " + i), button -> {
                currentStage = stage;
                loadEvilStageUI();
            }).dimensions(leftX, stageButtonY(i), leftWidth - 10, 20).build();
            addDrawableChild(btn);
            stageButtons.add(btn);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 22;
        int labelWidth = 100;
        int inputWidth = 60;

        for (int i = 0; i < evilEyesLabels().length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 4, labelWidth, 9, Text.literal(evilEyesLabels()[i]), textRenderer);
            addDrawableChild(label);
            evilLabels.add(label);
        }

        dailyField = createField(rightX + labelWidth, rowY, inputWidth);
        marksField = createField(rightX + labelWidth, rowY + rowHeight, inputWidth);
        uiDrainField = createField(rightX + labelWidth, rowY + 2*rowHeight, inputWidth);
        watchDrainField = createField(rightX + labelWidth, rowY + 3*rowHeight, inputWidth);
        regenField = createField(rightX + labelWidth, rowY + 4*rowHeight, inputWidth);
        parrotDailyField = createField(rightX + labelWidth, rowY + 5*rowHeight, inputWidth);

        saveEvilButton = ButtonWidget.builder(Text.literal("保存"), btn -> saveEvilConfig())
                .dimensions(rightX, rowY + 6*rowHeight + 10, 80, 20).build();
        addDrawableChild(saveEvilButton);
    }

    private static String[] evilEyesLabels() {
        return new String[]{
                "\u6bcf\u65e5\u6253\u5f00\u6b21\u6570:", "\u6700\u5927\u603b\u6807\u8bb0\u6570:", "\u754c\u9762\u6d88\u8017(/s):",
                "\u89c2\u770b\u6d88\u8017(/s):", "\u6062\u590d\u901f\u7387(/s):", "\u6bcf\u65e5\u951a\u70b9\u653e\u7f6e:"
        };
    }

    // ==================== 视线诱导 UI ====================
    private void buildGazeGuidanceUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("阶段 " + i), button -> {
                gazeCurrentStage = stage;
                loadGazeStageUI();
            }).dimensions(leftX, stageButtonY(i), leftWidth - 10, 20).build();
            addDrawableChild(btn);
            gazeStageButtons.add(btn);
        }

        int gazeRightX = panelX + leftWidth + 5;
        int gazeRowY = panelY + 10;
        int gazeRowHeight = 22;
        int gazeLabelWidth = 120;
        int gazeInputWidth = 80;

        String[] gazeLabelsArr = {"消耗速率(/秒/标记):", "回复速率(/秒):", "范围(格):", "最大标记数量:"};
        for (int i = 0; i < gazeLabelsArr.length; i++) {
            TextWidget label = new TextWidget(gazeRightX, gazeRowY + i * gazeRowHeight + 4, gazeLabelWidth, 9, Text.literal(gazeLabelsArr[i]), textRenderer);
            addDrawableChild(label);
            gazeLabels.add(label);
        }

        gazeDrainField = createField(gazeRightX + gazeLabelWidth, gazeRowY, gazeInputWidth);
        gazeRegenField = createField(gazeRightX + gazeLabelWidth, gazeRowY + gazeRowHeight, gazeInputWidth);
        gazeRadiusField = createField(gazeRightX + gazeLabelWidth, gazeRowY + 2*gazeRowHeight, gazeInputWidth);
        gazeMarksField = createField(gazeRightX + gazeLabelWidth, gazeRowY + 3*gazeRowHeight, gazeInputWidth);

        saveGazeButton = ButtonWidget.builder(Text.literal("保存"), btn -> saveGazeConfig())
                .dimensions(gazeRightX + 20, gazeRowY + 4*gazeRowHeight + 10, 80, 20).build();
        addDrawableChild(saveGazeButton);
    }

    // ==================== 镜子配置 UI ====================
    private void buildMirrorUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("阶段 " + i), button -> {
                mirrorCurrentStage = stage;
                loadMirrorStageUI();
            }).dimensions(leftX, stageButtonY(i), leftWidth - 10, 20).build();
            addDrawableChild(btn);
            mirrorStageButtons.add(btn);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 22;
        int labelWidth = 110;
        int inputWidth = 60;

        String[] labels = {"观看时间(秒):", "成功概率(0-1):", "观看次数:", "触发半径:", "充能时间(ticks):"};
        for (int i = 0; i < labels.length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 4, labelWidth, 9, Text.literal(labels[i]), textRenderer);
            addDrawableChild(label);
            mirrorLabels.add(label);
        }

        mirrorWatchTimeField = createField(rightX + labelWidth, rowY, inputWidth);
        mirrorSuccessRateField = createField(rightX + labelWidth, rowY + rowHeight, inputWidth);
        mirrorViewCountField = createField(rightX + labelWidth, rowY + 2*rowHeight, inputWidth);
        mirrorRadiusField = createField(rightX + labelWidth, rowY + 3*rowHeight, inputWidth);
        mirrorChargeTimeField = createField(rightX + labelWidth, rowY + 4*rowHeight, inputWidth);

        saveMirrorButton = ButtonWidget.builder(Text.literal("保存"), btn -> saveMirrorConfig())
                .dimensions(rightX, rowY + 4*rowHeight + 15, 80, 20).build();
        addDrawableChild(saveMirrorButton);
    }

    // ==================== 穿墙配置 UI ====================
    private void buildThroughUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("阶段 " + i), button -> {
                throughCurrentStage = stage;
                loadThroughStageUI();
            }).dimensions(leftX, stageButtonY(i), leftWidth - 10, 20).build();
            addDrawableChild(btn);
            throughStageButtons.add(btn);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 22;
        int labelWidth = 110;
        int inputWidth = 70;

        // 只有速度和延迟两个输入项
        String[] labels = {"速度倍率:", "穿墙准备(秒,-1禁用):"};
        for (int i = 0; i < labels.length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 4, labelWidth, 9, Text.literal(labels[i]), textRenderer);
            addDrawableChild(label);
            throughLabels.add(label);
        }

        throughSpeedMultiplierField = createField(rightX + labelWidth, rowY, inputWidth);
        throughDelayField = createField(rightX + labelWidth, rowY + rowHeight, inputWidth);

        saveThroughButton = ButtonWidget.builder(Text.literal("保存"), btn -> saveThroughConfig())
                .dimensions(rightX, rowY + 2*rowHeight + 10, 80, 20).build();
        addDrawableChild(saveThroughButton);
    }

    // ==================== 阶段区间 UI ====================
    private void buildStageRangeUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("阶段 " + i), button -> {
                stageRangeCurrentStage = stage;
                loadStageRangeUI();
            }).dimensions(leftX, stageButtonY(i), leftWidth - 10, 20).build();
            addDrawableChild(btn);
            stageRangeStageButtons.add(btn);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 22;
        int labelWidth = 100;
        int inputWidth = 60;

        String[] labels = {"触发区间(下限):", "触发区间(上限):"};
        for (int i = 0; i < labels.length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 4, labelWidth, 9, Text.literal(labels[i]), textRenderer);
            addDrawableChild(label);
            stageRangeLabels.add(label);
        }

        stageRangeMinField = createField(rightX + labelWidth, rowY, inputWidth);
        stageRangeMaxField = createField(rightX + labelWidth, rowY + rowHeight, inputWidth);

        saveStageRangeButton = ButtonWidget.builder(Text.literal("保存"), btn -> saveStageRangeConfig())
                .dimensions(rightX, rowY + 2*rowHeight + 10, 80, 20).build();
        addDrawableChild(saveStageRangeButton);
    }

    private void buildImitateUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("阶段 " + i), button -> {
                imitateCurrentStage = stage;
                loadImitateStageUI();
            }).dimensions(leftX, stageButtonY(i), leftWidth - 10, 20).build();
            addDrawableChild(btn);
            imitateStageButtons.add(btn);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 20;
        int labelWidth = 110;
        int inputWidth = 50;

        String[] labels = {
            "模仿持续时间(秒):",
            "切换冷却时间(秒):",
            "声波震荡冷却(秒):",
            "声波震荡半径:",
            "声波效果持续时间(秒):",
            "静音检测半径:",
            "静音持续时间(秒):",
            "静音冷却时间(秒):"
        };

        for (int i = 0; i < labels.length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 2, labelWidth, 9, Text.literal(labels[i]), textRenderer);
            addDrawableChild(label);
            imitateLabels.add(label);
        }

        imitateDurationField = createField(rightX + labelWidth, rowY, inputWidth);
        imitateSwitchCooldownField = createField(rightX + labelWidth, rowY + rowHeight, inputWidth);
        imitateSoundWaveCooldownField = createField(rightX + labelWidth, rowY + 2*rowHeight, inputWidth);
        imitateSoundWaveRadiusField = createField(rightX + labelWidth, rowY + 3*rowHeight, inputWidth);
        imitateSoundWaveEffectDurationField = createField(rightX + labelWidth, rowY + 4*rowHeight, inputWidth);
        imitateSilenceRadiusField = createField(rightX + labelWidth, rowY + 5*rowHeight, inputWidth);
        imitateSilenceDurationField = createField(rightX + labelWidth, rowY + 6*rowHeight, inputWidth);
        imitateSilenceCooldownField = createField(rightX + labelWidth, rowY + 7*rowHeight, inputWidth);

        saveImitateButton = ButtonWidget.builder(Text.literal("保存"), btn -> saveImitateConfig())
                .dimensions(rightX, rowY + 8*rowHeight + 10, 80, 20).build();
        addDrawableChild(saveImitateButton);
    }

    // ==================== 工具方法 ====================

    private void buildSecretUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("阶段 " + i), button -> {
                secretCurrentStage = stage;
                loadSecretStageUI();
            }).dimensions(leftX, stageButtonY(i), leftWidth - 10, 20).build();
            addDrawableChild(btn);
            secretStageButtons.add(btn);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 22;
        int labelWidth = 100;
        int inputWidth = 60;

        String[] labels = {"范围(格):", "触发概率(0-1):"};
        for (int i = 0; i < labels.length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 4, labelWidth, 9, Text.literal(labels[i]), textRenderer);
            addDrawableChild(label);
            secretLabels.add(label);
        }

        secretRangeField = createField(rightX + labelWidth, rowY, inputWidth);
        secretProbabilityField = createField(rightX + labelWidth, rowY + rowHeight, inputWidth);

        saveSecretButton = ButtonWidget.builder(Text.literal("保存"), btn -> saveSecretConfig())
                .dimensions(rightX, rowY + 2*rowHeight + 10, 80, 20).build();
        addDrawableChild(saveSecretButton);
    }

    private void buildFloatingUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("\u9636\u6bb5 " + i), button -> {
                floatingCurrentStage = stage;
                loadFloatingStageUI();
            }).dimensions(leftX, stageButtonY(i), leftWidth - 10, 20).build();
            addDrawableChild(btn);
            floatingStageButtons.add(btn);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 22;
        int labelWidth = 120;
        int inputWidth = 80;

        String[] labels = {
                "\u80fd\u91cf\u6d88\u8017(/s):",
                "\u98de\u884c\u901f\u5ea6:",
                "\u6062\u590d\u901f\u5ea6(/s):"
        };
        for (int i = 0; i < labels.length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 4, labelWidth, 9, Text.literal(labels[i]), textRenderer);
            addDrawableChild(label);
            floatingLabels.add(label);
        }

        floatingDrainField = createField(rightX + labelWidth, rowY, inputWidth);
        floatingSpeedField = createField(rightX + labelWidth, rowY + rowHeight, inputWidth);
        floatingRegenField = createField(rightX + labelWidth, rowY + 2 * rowHeight, inputWidth);

        saveFloatingButton = ButtonWidget.builder(Text.literal("\u4fdd\u5b58"), btn -> saveFloatingConfig())
                .dimensions(rightX, rowY + 3 * rowHeight + 10, 80, 20).build();
        addDrawableChild(saveFloatingButton);
    }

    private void buildPlantMagicUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("\u9636\u6bb5 " + i), button -> {
                plantCurrentStage = stage;
                loadPlantMagicStageUI();
            }).dimensions(leftX, stageButtonY(i), leftWidth - 10, 20).build();
            addDrawableChild(btn);
            plantStageButtons.add(btn);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 22;
        int labelWidth = 130;
        int inputWidth = 80;

        String[] labels = {
                "\u6811\u53f6\u79fb\u52a8\u901f\u5ea6(\u683c/s):",
                "\u7403\u58f3\u8986\u76d6\u89d2\u5ea6:",
                "\u51b7\u5374\u65f6\u95f4(s):"
        };
        for (int i = 0; i < labels.length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 4, labelWidth, 9, Text.literal(labels[i]), textRenderer);
            addDrawableChild(label);
            plantLabels.add(label);
        }

        plantMoveSpeedField = createField(rightX + labelWidth, rowY, inputWidth);
        plantCoverageField = createField(rightX + labelWidth, rowY + rowHeight, inputWidth);
        plantCooldownField = createField(rightX + labelWidth, rowY + 2 * rowHeight, inputWidth);

        savePlantButton = ButtonWidget.builder(Text.literal("\u4fdd\u5b58"), btn -> savePlantMagicConfig())
                .dimensions(rightX, rowY + 3 * rowHeight + 10, 80, 20).build();
        addDrawableChild(savePlantButton);
    }
    /**
     * 创建一个限制最大输入长度为6的文本输入框。
     */
    private TextFieldWidget createField(int x, int y, int width) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 18, Text.empty());
        field.setMaxLength(6);
        addDrawableChild(field);
        return field;
    }

    /**
     * 切换到指定配置类型的标签页，高亮当前标签（绿色加粗），并加载对应阶段的UI数据。
     */
    private void switchTo(ConfigType type) {
        currentType = type;
        btnEvilEyes.setMessage(currentType == ConfigType.EVIL_EYES ? Text.literal("§l§a千里眼") : Text.literal("千里眼"));
        btnGaze.setMessage(currentType == ConfigType.GAZE_GUIDANCE ? Text.literal("§l§a视线诱导") : Text.literal("视线诱导"));
        btnMirror.setMessage(currentType == ConfigType.MIRROR ? Text.literal("§l§a昔今之镜") : Text.literal("镜子"));
        btnThrough.setMessage(currentType == ConfigType.THROUGH ? Text.literal("§l§a穿墙") : Text.literal("穿墙"));
        btnStageRange.setMessage(currentType == ConfigType.STAGE_RANGE ? Text.literal("§l§a阶段区间") : Text.literal("阶段区间"));
        btnImitate.setMessage(currentType == ConfigType.IMITATE ? Text.literal("§l§a模仿") : Text.literal("模仿"));

        btnSecret.setMessage(currentType == ConfigType.SECRET ? Text.literal("§l§a窃密") : Text.literal("窃密"));
        btnFloating.setMessage(currentType == ConfigType.FLOATING ? Text.literal("§l§a漂浮") : Text.literal("漂浮"));

        btnPlant.setMessage(currentType == ConfigType.PLANT ? Text.literal("\u00a7l\u00a7a\u690d\u7269") : Text.literal("\u690d\u7269"));

        setEvilComponentsVisible(currentType == ConfigType.EVIL_EYES);
        setGazeComponentsVisible(currentType == ConfigType.GAZE_GUIDANCE);
        setMirrorComponentsVisible(currentType == ConfigType.MIRROR);
        setThroughComponentsVisible(currentType == ConfigType.THROUGH);
        setStageRangeComponentsVisible(currentType == ConfigType.STAGE_RANGE);
        setImitateComponentsVisible(currentType == ConfigType.IMITATE);
        setFloatingComponentsVisible(currentType == ConfigType.FLOATING);
        setPlantMagicComponentsVisible(currentType == ConfigType.PLANT);
        setSecretComponentsVisible(currentType == ConfigType.SECRET);

        switch (currentType) {
            case EVIL_EYES -> loadEvilStageUI();
            case GAZE_GUIDANCE -> loadGazeStageUI();
            case MIRROR -> loadMirrorStageUI();
            case THROUGH -> loadThroughStageUI();
            case STAGE_RANGE -> loadStageRangeUI();
            case IMITATE -> loadImitateStageUI();
            case FLOATING -> loadFloatingStageUI();
            case SECRET -> loadSecretStageUI();
            case PLANT -> loadPlantMagicStageUI();
        }
    }

    // ==================== 可见性控制 ====================
    private void setEvilComponentsVisible(boolean visible) {
        for (ButtonWidget btn : stageButtons) btn.visible = visible;
        for (TextWidget label : evilLabels) label.visible = visible;
        if (dailyField != null) dailyField.visible = visible;
        if (marksField != null) marksField.visible = visible;
        if (uiDrainField != null) uiDrainField.visible = visible;
        if (watchDrainField != null) watchDrainField.visible = visible;
        if (regenField != null) regenField.visible = visible;
        if (parrotDailyField != null) parrotDailyField.visible = visible;
        if (saveEvilButton != null) saveEvilButton.visible = visible;
    }

    private void setGazeComponentsVisible(boolean visible) {
        for (ButtonWidget btn : gazeStageButtons) btn.visible = visible;
        for (TextWidget label : gazeLabels) label.visible = visible;
        if (gazeDrainField != null) gazeDrainField.visible = visible;
        if (gazeRegenField != null) gazeRegenField.visible = visible;
        if (gazeRadiusField != null) gazeRadiusField.visible = visible;
        if (gazeMarksField != null) gazeMarksField.visible = visible;
        if (saveGazeButton != null) saveGazeButton.visible = visible;
    }

    private void setMirrorComponentsVisible(boolean visible) {
        for (ButtonWidget btn : mirrorStageButtons) btn.visible = visible;
        for (TextWidget label : mirrorLabels) label.visible = visible;
        if (mirrorWatchTimeField != null) mirrorWatchTimeField.visible = visible;
        if (mirrorSuccessRateField != null) mirrorSuccessRateField.visible = visible;
        if (mirrorViewCountField != null) mirrorViewCountField.visible = visible;
        if (mirrorRadiusField != null) mirrorRadiusField.visible = visible;
        if (mirrorChargeTimeField != null) mirrorChargeTimeField.visible = visible;
        if (saveMirrorButton != null) saveMirrorButton.visible = visible;
    }

    private void setThroughComponentsVisible(boolean visible) {
        for (ButtonWidget btn : throughStageButtons) btn.visible = visible;
        for (TextWidget label : throughLabels) label.visible = visible;
        if (throughSpeedMultiplierField != null) throughSpeedMultiplierField.visible = visible;
        if (throughDelayField != null) throughDelayField.visible = visible;
        if (saveThroughButton != null) saveThroughButton.visible = visible;
    }

    private void setStageRangeComponentsVisible(boolean visible) {
        for (ButtonWidget btn : stageRangeStageButtons) btn.visible = visible;
        for (TextWidget label : stageRangeLabels) label.visible = visible;
        if (stageRangeMinField != null) stageRangeMinField.visible = visible;
        if (stageRangeMaxField != null) stageRangeMaxField.visible = visible;
        if (saveStageRangeButton != null) saveStageRangeButton.visible = visible;
    }

    private void setImitateComponentsVisible(boolean visible) {
        for (ButtonWidget btn : imitateStageButtons) btn.visible = visible;
        for (TextWidget label : imitateLabels) label.visible = visible;
        if (imitateDurationField != null) imitateDurationField.visible = visible;
        if (imitateSwitchCooldownField != null) imitateSwitchCooldownField.visible = visible;
        if (imitateSoundWaveCooldownField != null) imitateSoundWaveCooldownField.visible = visible;
        if (imitateSoundWaveRadiusField != null) imitateSoundWaveRadiusField.visible = visible;
        if (imitateSoundWaveEffectDurationField != null) imitateSoundWaveEffectDurationField.visible = visible;
        if (imitateSilenceRadiusField != null) imitateSilenceRadiusField.visible = visible;
        if (imitateSilenceDurationField != null) imitateSilenceDurationField.visible = visible;
        if (imitateSilenceCooldownField != null) imitateSilenceCooldownField.visible = visible;
        if (saveImitateButton != null) saveImitateButton.visible = visible;
    }

    // ========== 千里眼配置 ==========
    private void setSecretComponentsVisible(boolean visible) {
        for (ButtonWidget btn : secretStageButtons) btn.visible = visible;
        for (TextWidget label : secretLabels) label.visible = visible;
        if (secretRangeField != null) secretRangeField.visible = visible;
        if (secretProbabilityField != null) secretProbabilityField.visible = visible;
        if (saveSecretButton != null) saveSecretButton.visible = visible;
    }

    private void setFloatingComponentsVisible(boolean visible) {
        for (ButtonWidget btn : floatingStageButtons) btn.visible = visible;
        for (TextWidget label : floatingLabels) label.visible = visible;
        if (floatingDrainField != null) floatingDrainField.visible = visible;
        if (floatingSpeedField != null) floatingSpeedField.visible = visible;
        if (floatingRegenField != null) floatingRegenField.visible = visible;
        if (saveFloatingButton != null) saveFloatingButton.visible = visible;
    }

    private void setPlantMagicComponentsVisible(boolean visible) {
        for (ButtonWidget btn : plantStageButtons) btn.visible = visible;
        for (TextWidget label : plantLabels) label.visible = visible;
        if (plantMoveSpeedField != null) plantMoveSpeedField.visible = visible;
        if (plantCoverageField != null) plantCoverageField.visible = visible;
        if (plantCooldownField != null) plantCooldownField.visible = visible;
        if (savePlantButton != null) savePlantButton.visible = visible;
    }

    private void loadEvilStageUI() {
        GlobalConfigS2C.StageConfig cfg = CACHED_EVIL_CONFIGS[currentStage];
        if (cfg == null) {
            dailyField.setText("10");
            marksField.setText("3");
            uiDrainField.setText("1.0");
            watchDrainField.setText("8.0");
            regenField.setText("2.0");
            parrotDailyField.setText("5");
            return;
        }
        dailyField.setText(String.valueOf(cfg.dailyLimit()));
        marksField.setText(String.valueOf(cfg.maxMarks()));
        uiDrainField.setText(String.valueOf(cfg.uiDrainRate()));
        watchDrainField.setText(String.valueOf(cfg.watchDrainRate()));
        regenField.setText(String.valueOf(cfg.regenRate()));
        parrotDailyField.setText(String.valueOf(cfg.parrotDailyLimit()));
    }

    private void saveEvilConfig() {
        try {
            int daily = Integer.parseInt(dailyField.getText().trim());
            int marks = Integer.parseInt(marksField.getText().trim());
            double uiDrain = Double.parseDouble(uiDrainField.getText().trim());
            double watchDrain = Double.parseDouble(watchDrainField.getText().trim());
            double regen = Double.parseDouble(regenField.getText().trim());
            int parrotDaily = Integer.parseInt(parrotDailyField.getText().trim());

            // Preserve existing minScore/maxScore from the currently stored config
            GlobalConfigS2C.StageConfig existing = CACHED_EVIL_CONFIGS[currentStage];
            int minScore = (existing != null) ? existing.minScore() : 0;
            int maxScore = (existing != null) ? existing.maxScore() : 5;
            int watchTicks = (existing != null) ? existing.watchRequiredTicks() : 40;
            int maxActive = (existing != null) ? existing.maxActiveParrots() : 1;

            CACHED_EVIL_CONFIGS[currentStage] = new GlobalConfigS2C.StageConfig(
                    daily, marks, minScore, maxScore, watchTicks, parrotDaily, maxActive, uiDrain, watchDrain, regen
            );

            ClientPlayNetworking.send(new UpdateGlobalConfigC2S(
                    currentStage, daily, marks, minScore, maxScore, watchTicks, parrotDaily, maxActive, uiDrain, watchDrain, regen
            ));

            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§a千里眼配置已提交"), true);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§c请输入有效的整数数字"), true);
        }
    }

    /**
     * 接收服务器发来的千里眼全局配置并更新缓存。
     * 如果当前正在查看千里眼或阶段区间标签页，同步刷新界面。
     */
    public void receiveEvilConfigs(GlobalConfigS2C.StageConfig[] configsArray) {
        if (configsArray == null) return;
        for (int i = 0; i < configsArray.length; i++) {
            CACHED_EVIL_CONFIGS[i + 1] = configsArray[i];
        }
        if (currentType == ConfigType.EVIL_EYES || currentType == ConfigType.STAGE_RANGE) {
            loadEvilStageUI();
            loadStageRangeUI();
        }
    }

    // ========== 视线诱导配置 ==========
    private void loadGazeStageUI() {
        if (cachedGazeConfig == null) {
            gazeDrainField.setText("");
            gazeRegenField.setText("");
            gazeRadiusField.setText("");
            gazeMarksField.setText("");
            return;
        }
        gazeDrainField.setText(String.valueOf(cachedGazeConfig.getEnergyDrain(gazeCurrentStage)));
        gazeRegenField.setText(String.valueOf(cachedGazeConfig.getEnergyRegen(gazeCurrentStage)));
        gazeRadiusField.setText(String.valueOf(cachedGazeConfig.getRadius(gazeCurrentStage)));
        gazeMarksField.setText(String.valueOf(cachedGazeConfig.getMaxMarks(gazeCurrentStage)));
    }

    private void saveGazeConfig() {
        try {
            double drain = Double.parseDouble(gazeDrainField.getText().trim());
            double regen = Double.parseDouble(gazeRegenField.getText().trim());
            double radius = Double.parseDouble(gazeRadiusField.getText().trim());
            int marks = Integer.parseInt(gazeMarksField.getText().trim());

            if (cachedGazeConfig == null) cachedGazeConfig = new GazeConfig();
            cachedGazeConfig.setEnergyDrain(gazeCurrentStage, drain);
            cachedGazeConfig.setEnergyRegen(gazeCurrentStage, regen);
            cachedGazeConfig.setRadius(gazeCurrentStage, radius);
            cachedGazeConfig.setMaxMarks(gazeCurrentStage, marks);

            ClientPlayNetworking.send(new UpdateConfigC2SPacket(cachedGazeConfig.toJson()));
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§e阶段 " + gazeCurrentStage + " 配置已提交，正在同步..."), true);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§c请输入有效的数字"), true);
        }
    }

    /**
     * 接收服务器发来的视线诱导配置并更新缓存。
     * 仅当当前标签页为视线诱导时才刷新UI，避免干扰其他标签页的编辑。
     */
    public void receiveGazeConfig(GazeConfig config) {
        if (config == null) return;
        cachedGazeConfig = config;
        if (currentType == ConfigType.GAZE_GUIDANCE) {
            loadGazeStageUI();
        }
    }

    // ========== 镜子配置 ==========
    private void loadMirrorStageUI() {
        if (cachedMirrorConfig == null) {
            mirrorWatchTimeField.setText("");
            mirrorSuccessRateField.setText("");
            mirrorViewCountField.setText("");
            mirrorRadiusField.setText("");
            mirrorChargeTimeField.setText("");
            return;
        }
        mirrorWatchTimeField.setText(String.valueOf(cachedMirrorConfig.getWatchTime(mirrorCurrentStage)));
        mirrorSuccessRateField.setText(String.valueOf(cachedMirrorConfig.getSuccessRate(mirrorCurrentStage)));
        mirrorViewCountField.setText(String.valueOf(cachedMirrorConfig.getViewCount(mirrorCurrentStage)));
        mirrorRadiusField.setText(String.valueOf(cachedMirrorConfig.getRadius(mirrorCurrentStage)));
        mirrorChargeTimeField.setText(String.valueOf(cachedMirrorConfig.getChargeTime(mirrorCurrentStage)));
    }

    private void saveMirrorConfig() {
        try {
            int watchTime = Integer.parseInt(mirrorWatchTimeField.getText().trim());
            double successRate = Double.parseDouble(mirrorSuccessRateField.getText().trim());
            int viewCount = Integer.parseInt(mirrorViewCountField.getText().trim());
            double radius = Double.parseDouble(mirrorRadiusField.getText().trim());
            int chargeTime = Integer.parseInt(mirrorChargeTimeField.getText().trim());

            if (cachedMirrorConfig == null) cachedMirrorConfig = new MirrorConfig();
            cachedMirrorConfig.setWatchTime(mirrorCurrentStage, watchTime);
            cachedMirrorConfig.setSuccessRate(mirrorCurrentStage, successRate);
            cachedMirrorConfig.setViewCount(mirrorCurrentStage, viewCount);
            cachedMirrorConfig.setRadius(mirrorCurrentStage, radius);
            cachedMirrorConfig.setChargeTime(mirrorCurrentStage, chargeTime);

            ClientPlayNetworking.send(new ConfigUpdateC2S(cachedMirrorConfig.toJson()));
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§e阶段 " + mirrorCurrentStage + " 镜子配置已提交，正在同步..."), true);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§c请输入有效的数字"), true);
        }
    }

    /**
     * 接收服务器发来的镜子配置并更新缓存。
     * 仅当当前标签页为镜子时才刷新UI。
     */
    public void receiveMirrorConfig(MirrorConfig config) {
        if (config == null) return;
        cachedMirrorConfig = config;
        if (currentType == ConfigType.MIRROR) {
            loadMirrorStageUI();
        }
    }

    // ========== 穿墙配置 ==========
    private void loadThroughStageUI() {
        if (cachedThroughConfig == null) {
            throughSpeedMultiplierField.setText("");
            throughDelayField.setText("");
            return;
        }
        throughSpeedMultiplierField.setText(String.valueOf(cachedThroughConfig.getSpeedMultiplier(throughCurrentStage)));
        throughDelayField.setText(String.valueOf(cachedThroughConfig.getVanishDelaySeconds(throughCurrentStage)));
    }

    private void saveThroughConfig() {
        try {
            double speedMultiplier = Double.parseDouble(throughSpeedMultiplierField.getText().trim());
            int delaySeconds = Integer.parseInt(throughDelayField.getText().trim());

            if (cachedThroughConfig == null) cachedThroughConfig = new ThroughConfig();
            cachedThroughConfig.setSpeedMultiplier(throughCurrentStage, speedMultiplier);
            cachedThroughConfig.setVanishDelaySeconds(throughCurrentStage, delaySeconds);

            ClientPlayNetworking.send(new ThroughConfigUpdateC2SPacket(cachedThroughConfig.toJson()));
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§e阶段 " + throughCurrentStage + " 穿墙配置已提交，正在同步..."), true);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§c请输入有效的数字"), true);
        }
    }

    /**
     * 接收服务器发来的穿墙配置并更新缓存。
     * 仅当当前标签页为穿墙时才刷新UI。
     */
    public void receiveThroughConfig(ThroughConfig config) {
        if (config == null) return;
        cachedThroughConfig = config;
        if (currentType == ConfigType.THROUGH) {
            loadThroughStageUI();
        }
    }

    // ========== 阶段区间配置 ==========
    private void loadStageRangeUI() {
        GlobalConfigS2C.StageConfig cfg = CACHED_EVIL_CONFIGS[stageRangeCurrentStage];
        if (cfg == null) {
            stageRangeMinField.setText("0");
            stageRangeMaxField.setText("5");
            return;
        }
        stageRangeMinField.setText(String.valueOf(cfg.minScore()));
        stageRangeMaxField.setText(String.valueOf(cfg.maxScore()));
    }

    private void saveStageRangeConfig() {
        try {
            int minScore = Integer.parseInt(stageRangeMinField.getText().trim());
            int maxScore = Integer.parseInt(stageRangeMaxField.getText().trim());

            GlobalConfigS2C.StageConfig existing = CACHED_EVIL_CONFIGS[stageRangeCurrentStage];
            int daily = (existing != null) ? existing.dailyLimit() : 10;
            int marks = (existing != null) ? existing.maxMarks() : 3;
            int watchTicks = (existing != null) ? existing.watchRequiredTicks() : 40;
            int parrotDaily = (existing != null) ? existing.parrotDailyLimit() : 5;
            int maxActive = (existing != null) ? existing.maxActiveParrots() : 1;
            double uiDrain = (existing != null) ? existing.uiDrainRate() : 1.0D;
            double watchDrain = (existing != null) ? existing.watchDrainRate() : 8.0D;
            double regen = (existing != null) ? existing.regenRate() : 2.0D;

            CACHED_EVIL_CONFIGS[stageRangeCurrentStage] = new GlobalConfigS2C.StageConfig(
                    daily, marks, minScore, maxScore, watchTicks, parrotDaily, maxActive, uiDrain, watchDrain, regen
            );

            ClientPlayNetworking.send(new UpdateGlobalConfigC2S(
                    stageRangeCurrentStage, daily, marks, minScore, maxScore, watchTicks, parrotDaily, maxActive, uiDrain, watchDrain, regen
            ));

            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§e阶段 " + stageRangeCurrentStage + " 区间配置已提交"), true);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§c请输入有效的整数数字"), true);
        }
    }

    private void loadImitateStageUI() {
        if (cachedImitateConfig == null) {
            imitateDurationField.setText("0");
            imitateSwitchCooldownField.setText("0");
            imitateSoundWaveCooldownField.setText("0");
            imitateSoundWaveRadiusField.setText("10");
            imitateSoundWaveEffectDurationField.setText("5");
            imitateSilenceRadiusField.setText("10");
            imitateSilenceDurationField.setText("10");
            imitateSilenceCooldownField.setText("0");
            return;
        }
        imitateDurationField.setText(String.valueOf(cachedImitateConfig.getDuration(imitateCurrentStage)));
        imitateSwitchCooldownField.setText(String.valueOf(cachedImitateConfig.getSwitchCooldown(imitateCurrentStage)));
        imitateSoundWaveCooldownField.setText(String.valueOf(cachedImitateConfig.getSoundWaveCooldown(imitateCurrentStage)));
        imitateSoundWaveRadiusField.setText(String.valueOf(cachedImitateConfig.getSoundWaveRadius(imitateCurrentStage)));
        imitateSoundWaveEffectDurationField.setText(String.valueOf(cachedImitateConfig.getSoundWaveEffectDuration(imitateCurrentStage)));
        imitateSilenceRadiusField.setText(String.valueOf(cachedImitateConfig.getSilenceRadius(imitateCurrentStage)));
        imitateSilenceDurationField.setText(String.valueOf(cachedImitateConfig.getSilenceDuration(imitateCurrentStage)));
        imitateSilenceCooldownField.setText(String.valueOf(cachedImitateConfig.getSilenceCooldown(imitateCurrentStage)));
    }

    private void saveImitateConfig() {
        try {
            int duration = Integer.parseInt(imitateDurationField.getText().trim());
            int switchCooldown = Integer.parseInt(imitateSwitchCooldownField.getText().trim());
            int soundWaveCooldown = Integer.parseInt(imitateSoundWaveCooldownField.getText().trim());
            double soundWaveRadius = Double.parseDouble(imitateSoundWaveRadiusField.getText().trim());
            int soundWaveEffectDuration = Integer.parseInt(imitateSoundWaveEffectDurationField.getText().trim());
            double silenceRadius = Double.parseDouble(imitateSilenceRadiusField.getText().trim());
            int silenceDuration = Integer.parseInt(imitateSilenceDurationField.getText().trim());
            int silenceCooldown = Integer.parseInt(imitateSilenceCooldownField.getText().trim());

            if (cachedImitateConfig == null) cachedImitateConfig = new ImitateConfig();
            cachedImitateConfig.setDuration(imitateCurrentStage, duration);
            cachedImitateConfig.setSwitchCooldown(imitateCurrentStage, switchCooldown);
            cachedImitateConfig.setSoundWaveCooldown(imitateCurrentStage, soundWaveCooldown);
            cachedImitateConfig.setSoundWaveRadius(imitateCurrentStage, soundWaveRadius);
            cachedImitateConfig.setSoundWaveEffectDuration(imitateCurrentStage, soundWaveEffectDuration);
            cachedImitateConfig.setSilenceRadius(imitateCurrentStage, silenceRadius);
            cachedImitateConfig.setSilenceDuration(imitateCurrentStage, silenceDuration);
            cachedImitateConfig.setSilenceCooldown(imitateCurrentStage, silenceCooldown);

            ClientPlayNetworking.send(new UpdateImitateConfigC2SPacket(cachedImitateConfig.toJson()));
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§e阶段 " + imitateCurrentStage + " 模仿配置已提交，正在同步..."), true);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§c请输入有效的整数数字"), true);
        }
    }

    public void receiveImitateConfig(ImitateConfig config) {
        if (config == null) return;
        cachedImitateConfig = config;
        if (currentType == ConfigType.IMITATE) {
            loadImitateStageUI();
        }
    }

    private void loadSecretStageUI() {
        SecretConfig config = cachedSecretConfig;
        if (config == null) {
            secretRangeField.setText("");
            secretProbabilityField.setText("");
            return;
        }
        secretRangeField.setText(String.valueOf(config.getRange(secretCurrentStage)));
        secretProbabilityField.setText(String.valueOf(config.getProbability(secretCurrentStage)));
    }

    private void loadFloatingStageUI() {
        if (cachedFloatingConfig == null) {
            floatingDrainField.setText("10");
            floatingSpeedField.setText("0.005");
            floatingRegenField.setText("10");
            return;
        }
        floatingDrainField.setText(String.valueOf(cachedFloatingConfig.getEnergyDrain(floatingCurrentStage)));
        floatingSpeedField.setText(String.valueOf(cachedFloatingConfig.getFlightSpeed(floatingCurrentStage)));
        floatingRegenField.setText(String.valueOf(cachedFloatingConfig.getEnergyRegen(floatingCurrentStage)));
    }

    private void loadPlantMagicStageUI() {
        if (cachedPlantMagicConfig == null) {
            plantMoveSpeedField.setText("2.0");
            plantCoverageField.setText("120");
            plantCooldownField.setText("10");
            return;
        }
        plantMoveSpeedField.setText(String.valueOf(cachedPlantMagicConfig.getLeafMoveSpeed(plantCurrentStage)));
        plantCoverageField.setText(String.valueOf(cachedPlantMagicConfig.getShellCoverageDegrees(plantCurrentStage)));
        plantCooldownField.setText(String.valueOf(cachedPlantMagicConfig.getCooldownSeconds(plantCurrentStage)));
    }

    private void saveSecretConfig() {
        try {
            int range = Integer.parseInt(secretRangeField.getText().trim());
            double probability = Double.parseDouble(secretProbabilityField.getText().trim());

            if (cachedSecretConfig == null) cachedSecretConfig = new SecretConfig();
            cachedSecretConfig.setRange(secretCurrentStage, range);
            cachedSecretConfig.setProbability(secretCurrentStage, probability);

            ClientPlayNetworking.send(new SecretPackets.UpdateConfigC2S(cachedSecretConfig.toJson()));
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§e阶段 " + secretCurrentStage + " 窃密配置已提交，正在同步..."), true);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§c请输入有效的数字"), true);
        }
    }

    private void saveFloatingConfig() {
        try {
            double drain = Double.parseDouble(floatingDrainField.getText().trim());
            float speed = Float.parseFloat(floatingSpeedField.getText().trim());
            double regen = Double.parseDouble(floatingRegenField.getText().trim());

            if (cachedFloatingConfig == null) cachedFloatingConfig = new FloatingConfig();
            cachedFloatingConfig.setEnergyDrain(floatingCurrentStage, drain);
            cachedFloatingConfig.setFlightSpeed(floatingCurrentStage, speed);
            cachedFloatingConfig.setEnergyRegen(floatingCurrentStage, regen);

            ClientPlayNetworking.send(new FloatingPackets.UpdateConfigC2S(cachedFloatingConfig.toJson()));
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("§aFloating config submitted"), true);
            }
        } catch (NumberFormatException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("§cInvalid number"), true);
            }
        }
    }

    private void savePlantMagicConfig() {
        try {
            double moveSpeed = Double.parseDouble(plantMoveSpeedField.getText().trim());
            double coverageDegrees = Double.parseDouble(plantCoverageField.getText().trim());
            int cooldownSeconds = Integer.parseInt(plantCooldownField.getText().trim());

            if (cachedPlantMagicConfig == null) cachedPlantMagicConfig = new PlantMagicConfig();
            cachedPlantMagicConfig.setLeafMoveSpeed(plantCurrentStage, moveSpeed);
            cachedPlantMagicConfig.setShellCoverageDegrees(plantCurrentStage, coverageDegrees);
            cachedPlantMagicConfig.setCooldownSeconds(plantCurrentStage, cooldownSeconds);

            ClientPlayNetworking.send(new PlantMagicPackets.UpdateConfigC2S(cachedPlantMagicConfig.toJson()));
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("\u00a7aPlant magic config submitted"), true);
            }
        } catch (NumberFormatException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("\u00a7cInvalid number"), true);
            }
        }
    }

    public void receiveSecretConfig(SecretConfig config) {
        if (config == null) return;
        cachedSecretConfig = config;
        if (currentType == ConfigType.SECRET) {
            loadSecretStageUI();
        }
    }

    public void receiveFloatingConfig(FloatingConfig config) {
        if (config == null) return;
        cachedFloatingConfig = config;
        if (currentType == ConfigType.FLOATING) {
            loadFloatingStageUI();
        }
    }

    public void receivePlantMagicConfig(PlantMagicConfig config) {
        if (config == null) return;
        cachedPlantMagicConfig = config;
        if (currentType == ConfigType.PLANT) {
            loadPlantMagicStageUI();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 半透明黑色遮罩背景
        context.fill(0, 0, width, height, 0xAA000000);
        // 面板外边框（深灰）
        context.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF444444);
        // 左右分栏分隔线
        context.fill(panelX + leftWidth, panelY, panelX + leftWidth + 1, panelY + panelHeight, 0xFF444444);
        // 左侧阶段列表背景和右侧配置区背景
        context.fill(panelX, panelY, panelX + leftWidth, panelY + panelHeight, 0xAA222222);
        context.fill(panelX + leftWidth + 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xAA222222);

        switch (currentType) {
            case EVIL_EYES -> {
                context.drawText(textRenderer, "千里眼阶段配置", panelX + 5, panelY + 5, 0xFFFFFF, false);
                context.drawText(textRenderer, "当前编辑: 阶段 " + currentStage, panelX + leftWidth + 5, panelY + 5, 0xFFFFFF, false);
            }
            case GAZE_GUIDANCE -> {
                context.drawText(textRenderer, "诱导阶段配置", panelX + 5, panelY + 5, 0xFFFFFF, false);
                context.drawText(textRenderer, "当前编辑: 阶段 " + gazeCurrentStage, panelX + leftWidth + 5, panelY + 5, 0xFFFFFF, false);
            }
            case MIRROR -> {
                context.drawText(textRenderer, "镜子阶段配置", panelX + 5, panelY + 5, 0xFFFFFF, false);
                context.drawText(textRenderer, "当前编辑: 阶段 " + mirrorCurrentStage, panelX + leftWidth + 5, panelY + 5, 0xFFFFFF, false);
            }
            case THROUGH -> {
                context.drawText(textRenderer, "穿墙阶段配置", panelX + 5, panelY + 5, 0x55FFFF, false);
                context.drawText(textRenderer, "当前编辑: 阶段 " + throughCurrentStage, panelX + leftWidth + 5, panelY + 5, 0x55FFFF, false);
            }
            case STAGE_RANGE -> {
                context.drawText(textRenderer, "阶段区间配置", panelX + 5, panelY + 5, 0xFFFFFF, false);
                context.drawText(textRenderer, "当前编辑: 阶段 " + stageRangeCurrentStage, panelX + leftWidth + 5, panelY + 5, 0xFFFFFF, false);
            }
            case IMITATE -> {
                context.drawText(textRenderer, "模仿魔法配置", panelX + 5, panelY + 5, 0xFFAAFF, false);
                context.drawText(textRenderer, "当前编辑: 阶段 " + imitateCurrentStage, panelX + leftWidth + 5, panelY + 5, 0xFFAAFF, false);
            }
            case SECRET -> {
                context.drawText(textRenderer, "窃密阶段配置", panelX + 5, panelY + 5, 0xFF5555, false);
                context.drawText(textRenderer, "当前编辑: 阶段 " + secretCurrentStage, panelX + leftWidth + 5, panelY + 5, 0xFF5555, false);
            }
        }

        if (currentType == ConfigType.FLOATING) {
            context.drawText(textRenderer, "浮动配置", panelX + 5, panelY + 5, 0x55AAFF, false);
            context.drawText(textRenderer, "当前编辑: 阶段 " + floatingCurrentStage, panelX + leftWidth + 5, panelY + 5, 0x55AAFF, false);
        }

        if (currentType == ConfigType.PLANT) {
            context.drawText(textRenderer, "\u690d\u7269\u9b54\u6cd5\u9636\u6bb5\u914d\u7f6e", panelX + 5, panelY + 5, 0x66FF88, false);
            context.drawText(textRenderer, "\u5f53\u524d\u7f16\u8f91: \u9636\u6bb5 " + plantCurrentStage, panelX + leftWidth + 5, panelY + 5, 0x66FF88, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
