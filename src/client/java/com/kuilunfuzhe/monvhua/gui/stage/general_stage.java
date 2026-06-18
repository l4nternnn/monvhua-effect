package com.kuilunfuzhe.monvhua.gui.stage;

import com.kuilunfuzhe.monvhua.network.general_stage.GeneralStagePackets.GlobalConfigS2C;
import com.kuilunfuzhe.monvhua.network.general_stage.GeneralStagePackets.UpdateGlobalConfigC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

public class general_stage {
    public static final int STAGES = 7;
    private static final String[] RANGE_LABELS = {
            "\u89e6\u53d1\u533a\u95f4(\u4e0b\u9650):",
            "\u89e6\u53d1\u533a\u95f4(\u4e0a\u9650):"
    };

    private final GlobalConfigS2C.StageConfig[] configs;
    private final List<ButtonWidget> stageButtons = new ArrayList<>();
    private final List<TextWidget> labels = new ArrayList<>();
    private int currentStage = 1;
    private TextFieldWidget minField;
    private TextFieldWidget maxField;
    private ButtonWidget saveButton;

    public general_stage(GlobalConfigS2C.StageConfig[] configs) {
        this.configs = configs;
    }

    public void build(WidgetAdder adder, TextRenderer textRenderer, int panelX, int panelY, int leftWidth, IntUnaryOperator stageButtonY) {
        int leftX = panelX + 5;
        for (int i = 1; i <= STAGES; i++) {
            int stage = i;
            ButtonWidget button = ButtonWidget.builder(Text.literal("\u9636\u6bb5 " + i), ignored -> {
                currentStage = stage;
                load();
            }).dimensions(leftX, stageButtonY.applyAsInt(i), leftWidth - 10, 20).build();
            adder.add(button);
            stageButtons.add(button);
        }

        int rightX = panelX + leftWidth + 5;
        int rowY = panelY + 10;
        int rowHeight = 22;
        int labelWidth = 100;
        int inputWidth = 60;

        for (int i = 0; i < RANGE_LABELS.length; i++) {
            TextWidget label = new TextWidget(rightX, rowY + i * rowHeight + 4, labelWidth, 9, Text.literal(RANGE_LABELS[i]), textRenderer);
            adder.add(label);
            labels.add(label);
        }

        minField = createField(adder, textRenderer, rightX + labelWidth, rowY, inputWidth);
        maxField = createField(adder, textRenderer, rightX + labelWidth, rowY + rowHeight, inputWidth);

        saveButton = ButtonWidget.builder(Text.literal("\u4fdd\u5b58"), ignored -> save(MinecraftClient.getInstance()))
                .dimensions(rightX, rowY + 2 * rowHeight + 10, 80, 20).build();
        adder.add(saveButton);
    }

    public void setVisible(boolean visible) {
        for (ButtonWidget button : stageButtons) button.visible = visible;
        for (TextWidget label : labels) label.visible = visible;
        if (minField != null) minField.visible = visible;
        if (maxField != null) maxField.visible = visible;
        if (saveButton != null) saveButton.visible = visible;
    }

    public void load() {
        if (minField == null || maxField == null) return;
        GlobalConfigS2C.StageConfig config = configs[currentStage];
        if (config == null) {
            minField.setText("0");
            maxField.setText("5");
            return;
        }
        minField.setText(String.valueOf(config.minScore()));
        maxField.setText(String.valueOf(config.maxScore()));
    }

    public void save(MinecraftClient client) {
        if (minField == null || maxField == null) return;
        try {
            int minScore = Integer.parseInt(minField.getText().trim());
            int maxScore = Integer.parseInt(maxField.getText().trim());
            GlobalConfigS2C.StageConfig updated = withScoreRange(configs[currentStage], minScore, maxScore);
            configs[currentStage] = updated;
            sendUpdate(currentStage, updated);

            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("\u00a7e\u9636\u6bb5 " + currentStage + " \u533a\u95f4\u914d\u7f6e\u5df2\u63d0\u4ea4"), true);
            }
        } catch (NumberFormatException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("\u00a7c\u8bf7\u8f93\u5165\u6709\u6548\u7684\u6574\u6570\u6570\u5b57"), true);
            }
        }
    }

    public void renderHeader(DrawContext context, TextRenderer textRenderer, int panelX, int panelY, int leftWidth) {
        context.drawText(textRenderer, "\u9636\u6bb5\u533a\u95f4\u914d\u7f6e", panelX + 5, panelY + 5, 0xFFFFFF, false);
        context.drawText(textRenderer, "\u5f53\u524d\u7f16\u8f91: \u9636\u6bb5 " + currentStage, panelX + leftWidth + 5, panelY + 5, 0xFFFFFF, false);
    }

    private static TextFieldWidget createField(WidgetAdder adder, TextRenderer textRenderer, int x, int y, int width) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 18, Text.empty());
        field.setMaxLength(6);
        adder.add(field);
        return field;
    }

    public static GlobalConfigS2C.StageConfig withScoreRange(GlobalConfigS2C.StageConfig existing, int minScore, int maxScore) {
        int daily = existing != null ? existing.dailyLimit() : 10;
        int marks = existing != null ? existing.maxMarks() : 3;
        int watchTicks = existing != null ? existing.watchRequiredTicks() : 40;
        int parrotDaily = existing != null ? existing.parrotDailyLimit() : 5;
        int maxActive = existing != null ? existing.maxActiveParrots() : 1;
        double uiDrain = existing != null ? existing.uiDrainRate() : 1.0D;
        double watchDrain = existing != null ? existing.watchDrainRate() : 8.0D;
        double regen = existing != null ? existing.regenRate() : 2.0D;

        return new GlobalConfigS2C.StageConfig(
                daily, marks, minScore, maxScore, watchTicks, parrotDaily, maxActive, uiDrain, watchDrain, regen
        );
    }

    public static UpdateGlobalConfigC2S toUpdatePacket(int stage, GlobalConfigS2C.StageConfig config) {
        return new UpdateGlobalConfigC2S(
                stage,
                config.dailyLimit(),
                config.maxMarks(),
                config.minScore(),
                config.maxScore(),
                config.watchRequiredTicks(),
                config.parrotDailyLimit(),
                config.maxActiveParrots(),
                config.uiDrainRate(),
                config.watchDrainRate(),
                config.regenRate()
        );
    }

    public static void sendUpdate(int stage, GlobalConfigS2C.StageConfig config) {
        ClientPlayNetworking.send(toUpdatePacket(stage, config));
    }

    @FunctionalInterface
    public interface WidgetAdder {
        void add(ClickableWidget widget);
    }
}
