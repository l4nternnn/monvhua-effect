package com.kuilunfuzhe.monvhua.gui;

import com.kuilunfuzhe.monvhua.item.config.GazeConfig;
import com.kuilunfuzhe.monvhua.item.config.MirrorConfig;
import com.kuilunfuzhe.monvhua.network.evil_eyes.GlobalConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.evil_eyes.RequestGlobalConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.evil_eyes.UpdateGlobalConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.gazeguidance.RequestConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.gazeguidance.UpdateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorConfigUpdateC2SPacket;
import com.kuilunfuzhe.monvhua.network.mirror.RequestMirrorConfigC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class CombinedConfigScreen extends Screen {
    private enum ConfigType { EVIL_EYES, GAZE_GUIDANCE, MIRROR, STAGE_RANGE }
    private ConfigType currentType = ConfigType.EVIL_EYES;

    private ButtonWidget btnEvilEyes, btnGaze, btnMirror, btnStageRange;
    private int panelX, panelY, panelWidth, panelHeight;
    private int leftWidth, rightWidth;

    // ===== 千里眼组件 =====
    private final List<ButtonWidget> stageButtons = new ArrayList<>();
    private int currentStage = 1;
    private TextFieldWidget dailyField, marksField, watchTimeField, parrotDailyField, maxActiveField;
    private ButtonWidget saveEvilButton;
    private static final GlobalConfigS2CPacket.StageConfig[] CACHED_EVIL_CONFIGS = new GlobalConfigS2CPacket.StageConfig[8];
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
    private TextFieldWidget mirrorWatchTimeField, mirrorSuccessRateField, mirrorViewCountField, mirrorRadiusField;
    private ButtonWidget saveMirrorButton;
    private final List<TextWidget> mirrorLabels = new ArrayList<>();
    private static MirrorConfig cachedMirrorConfig = null;

    // ===== 阶段区间组件 =====
    private final List<ButtonWidget> stageRangeStageButtons = new ArrayList<>();
    private int stageRangeCurrentStage = 1;
    private TextFieldWidget stageRangeMinField, stageRangeMaxField;
    private ButtonWidget saveStageRangeButton;
    private final List<TextWidget> stageRangeLabels = new ArrayList<>();

    public CombinedConfigScreen() {
        super(Text.literal("物品配置"));
        ClientPlayNetworking.send(new RequestGlobalConfigC2SPacket());
        ClientPlayNetworking.send(new RequestConfigC2SPacket());
        ClientPlayNetworking.send(new RequestMirrorConfigC2SPacket());
        if (cachedGazeConfig == null) {
            cachedGazeConfig = createDefaultGazeConfig();
        }
        if (cachedMirrorConfig == null) {
            cachedMirrorConfig = createDefaultMirrorConfig();
        }
    }

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

    @Override
    protected void init() {
        super.init();
        int sw = this.client.getWindow().getScaledWidth();
        int sh = this.client.getWindow().getScaledHeight();
        panelWidth = sw * 3 / 5;
        panelHeight = (int)(panelWidth * 9f / 16f);
        panelX = (sw - panelWidth) / 2 - 100;
        panelY = (sh - panelHeight) / 2;

        leftWidth = 140;
        rightWidth = panelWidth - leftWidth - 15;

        int btnWidth = 80, btnHeight = 20;
        int btnY = panelY - btnHeight - 5;
        int centerX = panelX + panelWidth / 2;

        btnEvilEyes = ButtonWidget.builder(Text.literal("千里眼"), btn -> switchTo(ConfigType.EVIL_EYES))
                .dimensions(centerX - btnWidth * 2 - 8, btnY, btnWidth, btnHeight).build();
        btnGaze = ButtonWidget.builder(Text.literal("视线诱导"), btn -> switchTo(ConfigType.GAZE_GUIDANCE))
                .dimensions(centerX - btnWidth - 3, btnY, btnWidth, btnHeight).build();
        btnMirror = ButtonWidget.builder(Text.literal("镜子"), btn -> switchTo(ConfigType.MIRROR))
                .dimensions(centerX + 2, btnY, btnWidth, btnHeight).build();
        btnStageRange = ButtonWidget.builder(Text.literal("阶段区间"), btn -> switchTo(ConfigType.STAGE_RANGE))
                .dimensions(centerX + btnWidth + 7, btnY, btnWidth, btnHeight).build();
        addDrawableChild(btnEvilEyes);
        addDrawableChild(btnGaze);
        addDrawableChild(btnMirror);
        addDrawableChild(btnStageRange);

        buildEvilEyesUI();
        buildGazeGuidanceUI();
        buildMirrorUI();
        buildStageRangeUI();

        switchTo(ConfigType.EVIL_EYES);
    }

    // ==================== 千里眼 UI ====================
    private void buildEvilEyesUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("阶段 " + i), button -> {
                currentStage = stage;
                loadEvilStageUI();
            }).dimensions(leftX, panelY + 10 + (i-1)*25, leftWidth - 10, 20).build();
            addDrawableChild(btn);
            stageButtons.add(btn);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 22;
        int labelWidth = 100;
        int inputWidth = 60;

        String[] labels = {
                "每日可使用次数:", "最大标记数量:", "观看时长(秒):", "每日锚点放置:", "锚点同存数:"
        };
        for (int i = 0; i < labels.length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 4, labelWidth, 9, Text.literal(labels[i]), textRenderer);
            addDrawableChild(label);
            evilLabels.add(label);
        }

        dailyField = createField(rightX + labelWidth, rowY, inputWidth);
        marksField = createField(rightX + labelWidth, rowY + rowHeight, inputWidth);
        watchTimeField = createField(rightX + labelWidth, rowY + 2*rowHeight, inputWidth);
        parrotDailyField = createField(rightX + labelWidth, rowY + 3*rowHeight, inputWidth);
        maxActiveField = createField(rightX + labelWidth, rowY + 4*rowHeight, inputWidth);

        saveEvilButton = ButtonWidget.builder(Text.literal("保存"), btn -> saveEvilConfig())
                .dimensions(rightX, rowY + 5*rowHeight + 10, 80, 20).build();
        addDrawableChild(saveEvilButton);
    }

    // ==================== 视线诱导 UI ====================
    private void buildGazeGuidanceUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("阶段 " + i), button -> {
                gazeCurrentStage = stage;
                loadGazeStageUI();
            }).dimensions(leftX, panelY + 10 + (i-1)*25, leftWidth - 10, 20).build();
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
            }).dimensions(leftX, panelY + 10 + (i-1)*25, leftWidth - 10, 20).build();
            addDrawableChild(btn);
            mirrorStageButtons.add(btn);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 22;
        int labelWidth = 110;
        int inputWidth = 60;

        String[] labels = {"观看时间(秒):", "成功概率(0-1):", "观看次数:", "触发半径:"};
        for (int i = 0; i < labels.length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 4, labelWidth, 9, Text.literal(labels[i]), textRenderer);
            addDrawableChild(label);
            mirrorLabels.add(label);
        }

        mirrorWatchTimeField = createField(rightX + labelWidth, rowY, inputWidth);
        mirrorSuccessRateField = createField(rightX + labelWidth, rowY + rowHeight, inputWidth);
        mirrorViewCountField = createField(rightX + labelWidth, rowY + 2*rowHeight, inputWidth);
        mirrorRadiusField = createField(rightX + labelWidth, rowY + 3*rowHeight, inputWidth);

        saveMirrorButton = ButtonWidget.builder(Text.literal("保存"), btn -> saveMirrorConfig())
                .dimensions(rightX, rowY + 3*rowHeight + 10, 80, 20).build();
        addDrawableChild(saveMirrorButton);
    }

    // ==================== 阶段区间 UI ====================
    private void buildStageRangeUI() {
        int leftX = panelX + 5;
        for (int i = 1; i <= 7; i++) {
            int stage = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("阶段 " + i), button -> {
                stageRangeCurrentStage = stage;
                loadStageRangeUI();
            }).dimensions(leftX, panelY + 10 + (i-1)*25, leftWidth - 10, 20).build();
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

    // ==================== 工具方法 ====================
    private TextFieldWidget createField(int x, int y, int width) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 18, Text.empty());
        field.setMaxLength(6);
        addDrawableChild(field);
        return field;
    }

    private void switchTo(ConfigType type) {
        currentType = type;
        btnEvilEyes.setMessage(currentType == ConfigType.EVIL_EYES ? Text.literal("§l§a千里眼") : Text.literal("千里眼"));
        btnGaze.setMessage(currentType == ConfigType.GAZE_GUIDANCE ? Text.literal("§l§a视线诱导") : Text.literal("视线诱导"));
        btnMirror.setMessage(currentType == ConfigType.MIRROR ? Text.literal("§l§a昔今之镜") : Text.literal("镜子"));
        btnStageRange.setMessage(currentType == ConfigType.STAGE_RANGE ? Text.literal("§l§a阶段区间") : Text.literal("阶段区间"));

        setEvilComponentsVisible(currentType == ConfigType.EVIL_EYES);
        setGazeComponentsVisible(currentType == ConfigType.GAZE_GUIDANCE);
        setMirrorComponentsVisible(currentType == ConfigType.MIRROR);
        setStageRangeComponentsVisible(currentType == ConfigType.STAGE_RANGE);

        switch (currentType) {
            case EVIL_EYES -> loadEvilStageUI();
            case GAZE_GUIDANCE -> loadGazeStageUI();
            case MIRROR -> loadMirrorStageUI();
            case STAGE_RANGE -> loadStageRangeUI();
        }
    }

    // ==================== 可见性控制 ====================
    private void setEvilComponentsVisible(boolean visible) {
        for (ButtonWidget btn : stageButtons) btn.visible = visible;
        for (TextWidget label : evilLabels) label.visible = visible;
        if (dailyField != null) dailyField.visible = visible;
        if (marksField != null) marksField.visible = visible;
        if (watchTimeField != null) watchTimeField.visible = visible;
        if (parrotDailyField != null) parrotDailyField.visible = visible;
        if (maxActiveField != null) maxActiveField.visible = visible;
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
        if (saveMirrorButton != null) saveMirrorButton.visible = visible;
    }

    private void setStageRangeComponentsVisible(boolean visible) {
        for (ButtonWidget btn : stageRangeStageButtons) btn.visible = visible;
        for (TextWidget label : stageRangeLabels) label.visible = visible;
        if (stageRangeMinField != null) stageRangeMinField.visible = visible;
        if (stageRangeMaxField != null) stageRangeMaxField.visible = visible;
        if (saveStageRangeButton != null) saveStageRangeButton.visible = visible;
    }

    // ========== 千里眼配置 ==========
    private void loadEvilStageUI() {
        GlobalConfigS2CPacket.StageConfig cfg = CACHED_EVIL_CONFIGS[currentStage];
        if (cfg == null) {
            dailyField.setText("10");
            marksField.setText("3");
            watchTimeField.setText("2");
            parrotDailyField.setText("5");
            maxActiveField.setText("1");
            return;
        }
        dailyField.setText(String.valueOf(cfg.dailyLimit()));
        marksField.setText(String.valueOf(cfg.maxMarks()));
        watchTimeField.setText(String.valueOf(cfg.watchRequiredTicks() / 20));
        parrotDailyField.setText(String.valueOf(cfg.parrotDailyLimit()));
        maxActiveField.setText(String.valueOf(cfg.maxActiveParrots()));
    }

    private void saveEvilConfig() {
        try {
            int daily = Integer.parseInt(dailyField.getText().trim());
            int marks = Integer.parseInt(marksField.getText().trim());
            int watchSec = Integer.parseInt(watchTimeField.getText().trim());
            int parrotDaily = Integer.parseInt(parrotDailyField.getText().trim());
            int maxActive = Integer.parseInt(maxActiveField.getText().trim());
            int watchTicks = watchSec * 20;

            // Preserve existing minScore/maxScore from the currently stored config
            GlobalConfigS2CPacket.StageConfig existing = CACHED_EVIL_CONFIGS[currentStage];
            int minScore = (existing != null) ? existing.minScore() : 0;
            int maxScore = (existing != null) ? existing.maxScore() : 5;

            CACHED_EVIL_CONFIGS[currentStage] = new GlobalConfigS2CPacket.StageConfig(
                    daily, marks, minScore, maxScore, watchTicks, parrotDaily, maxActive
            );

            ClientPlayNetworking.send(new UpdateGlobalConfigC2SPacket(
                    currentStage, daily, marks, minScore, maxScore, watchTicks, parrotDaily, maxActive
            ));

            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§a千里眼配置已提交"), true);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§c请输入有效的整数数字"), true);
        }
    }

    public void receiveEvilConfigs(GlobalConfigS2CPacket.StageConfig[] configsArray) {
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
            return;
        }
        mirrorWatchTimeField.setText(String.valueOf(cachedMirrorConfig.getWatchTime(mirrorCurrentStage)));
        mirrorSuccessRateField.setText(String.valueOf(cachedMirrorConfig.getSuccessRate(mirrorCurrentStage)));
        mirrorViewCountField.setText(String.valueOf(cachedMirrorConfig.getViewCount(mirrorCurrentStage)));
        mirrorRadiusField.setText(String.valueOf(cachedMirrorConfig.getRadius(mirrorCurrentStage)));
    }

    private void saveMirrorConfig() {
        try {
            int watchTime = Integer.parseInt(mirrorWatchTimeField.getText().trim());
            double successRate = Double.parseDouble(mirrorSuccessRateField.getText().trim());
            int viewCount = Integer.parseInt(mirrorViewCountField.getText().trim());
            double radius = Double.parseDouble(mirrorRadiusField.getText().trim());

            if (cachedMirrorConfig == null) cachedMirrorConfig = new MirrorConfig();
            cachedMirrorConfig.setWatchTime(mirrorCurrentStage, watchTime);
            cachedMirrorConfig.setSuccessRate(mirrorCurrentStage, successRate);
            cachedMirrorConfig.setViewCount(mirrorCurrentStage, viewCount);
            cachedMirrorConfig.setRadius(mirrorCurrentStage, radius);

            ClientPlayNetworking.send(new MirrorConfigUpdateC2SPacket(cachedMirrorConfig.toJson()));
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§e阶段 " + mirrorCurrentStage + " 镜子配置已提交，正在同步..."), true);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§c请输入有效的数字"), true);
        }
    }

    public void receiveMirrorConfig(MirrorConfig config) {
        if (config == null) return;
        cachedMirrorConfig = config;
        if (currentType == ConfigType.MIRROR) {
            loadMirrorStageUI();
        }
    }

    // ========== 阶段区间配置 ==========
    private void loadStageRangeUI() {
        GlobalConfigS2CPacket.StageConfig cfg = CACHED_EVIL_CONFIGS[stageRangeCurrentStage];
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

            GlobalConfigS2CPacket.StageConfig existing = CACHED_EVIL_CONFIGS[stageRangeCurrentStage];
            int daily = (existing != null) ? existing.dailyLimit() : 10;
            int marks = (existing != null) ? existing.maxMarks() : 3;
            int watchTicks = (existing != null) ? existing.watchRequiredTicks() : 40;
            int parrotDaily = (existing != null) ? existing.parrotDailyLimit() : 5;
            int maxActive = (existing != null) ? existing.maxActiveParrots() : 1;

            CACHED_EVIL_CONFIGS[stageRangeCurrentStage] = new GlobalConfigS2CPacket.StageConfig(
                    daily, marks, minScore, maxScore, watchTicks, parrotDaily, maxActive
            );

            ClientPlayNetworking.send(new UpdateGlobalConfigC2SPacket(
                    stageRangeCurrentStage, daily, marks, minScore, maxScore, watchTicks, parrotDaily, maxActive
            ));

            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§e阶段 " + stageRangeCurrentStage + " 区间配置已提交"), true);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.literal("§c请输入有效的整数数字"), true);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA000000);
        context.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF444444);
        context.fill(panelX + leftWidth, panelY, panelX + leftWidth + 1, panelY + panelHeight, 0xFF444444);
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
            case STAGE_RANGE -> {
                context.drawText(textRenderer, "阶段区间配置", panelX + 5, panelY + 5, 0xFFFFFF, false);
                context.drawText(textRenderer, "当前编辑: 阶段 " + stageRangeCurrentStage, panelX + leftWidth + 5, panelY + 5, 0xFFFFFF, false);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
