package com.kuilunfuzhe.monvhua.gui.imitate;

import com.kuilunfuzhe.monvhua.client.imitate.ImitateClientManager;
import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateSelectPacket;
import com.kuilunfuzhe.monvhua.network.imitate.SoundWavePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ImitateScreen extends Screen {

    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_SPACING = 4;
    private static final int COLUMNS = 5;
    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 50;
    private static final int PADDING = 10;
    private static final int DEFAULT_SOUND_WAVE_THRESHOLD = 60;
    private static final int DEFAULT_SILENCE_THRESHOLD = 70;

    private int panelWidth;
    private int panelHeight;
    private int panelX;
    private int panelY;
    private final int witchStage;
    private final int soundWaveThreshold;
    private final int silenceThreshold;

    public ImitateScreen(int witchStage) {
        super(Text.literal("模仿"));
        this.witchStage = witchStage;
        ImitateConfig config = ImitateConfig.getInstance();
        this.soundWaveThreshold = config != null ? config.getSoundWaveUnlockThreshold() : DEFAULT_SOUND_WAVE_THRESHOLD;
        this.silenceThreshold = config != null ? config.getSilenceUnlockThreshold() : DEFAULT_SILENCE_THRESHOLD;
    }

    @Override
    protected void init() {
        super.init();

        int totalButtons = ImitateManager.ROLES.length;
        int rows = (int) Math.ceil((double) totalButtons / COLUMNS);

        int extraFooterHeight = 60;

        panelWidth = COLUMNS * (BUTTON_WIDTH + BUTTON_SPACING) - BUTTON_SPACING + PADDING * 2;
        panelHeight = HEADER_HEIGHT + rows * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING + FOOTER_HEIGHT + PADDING * 2 + extraFooterHeight;

        int sw = this.client.getWindow().getScaledWidth();
        int sh = this.client.getWindow().getScaledHeight();
        panelX = (sw - panelWidth) / 2;
        panelY = (sh - panelHeight) / 2;

        TextWidget titleWidget = new TextWidget(
                panelX, panelY + 5, panelWidth, 20,
                Text.literal("§d✦ 选择模仿角色 §r✦"), textRenderer
        );
        titleWidget.alignCenter();
        addDrawableChild(titleWidget);

        int startX = panelX + PADDING;
        int startY = panelY + HEADER_HEIGHT;

        int col = 0;
        int row = 0;
        for (String role : ImitateManager.ROLES) {
            int x = startX + col * (BUTTON_WIDTH + BUTTON_SPACING);
            int y = startY + row * (BUTTON_HEIGHT + BUTTON_SPACING);

            Text coloredRoleName = ImitateManager.getColoredRoleName(role);
            ButtonWidget roleBtn = ButtonWidget.builder(coloredRoleName, button -> {
                        if (client != null && client.player != null) {
                            int cooldownRemaining = ImitateClientManager.getSwitchCooldownRemaining(client.player.getUuid());
                            if (cooldownRemaining > 0) {
                                client.player.sendMessage(Text.literal("§c魔法还在冷却中: ").append(Text.literal(cooldownRemaining + "秒").formatted(Formatting.RED)), true);
                                return;
                            }
                        }
                        ClientPlayNetworking.send(new ImitateSelectPacket(role));
                        if (client != null) {
                            client.setScreen(null);
                        }
                    })
                    .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            addDrawableChild(roleBtn);

            col++;
            if (col >= COLUMNS) {
                col = 0;
                row++;
            }
        }

        int footerY = startY + rows * (BUTTON_HEIGHT + BUTTON_SPACING) + 10;
        int buttonWidth = 100;
        int spacing = 20;
        int totalWidth = buttonWidth * 2 + spacing;
        int footerStartX = panelX + (panelWidth - totalWidth) / 2;

        ButtonWidget resetBtn = ButtonWidget.builder(Text.literal("§e⟳ 重置模仿"), button -> {
                    ClientPlayNetworking.send(new ImitateSelectPacket("reset"));
                    if (client != null) {
                        client.setScreen(null);
                    }
                })
                .dimensions(footerStartX, footerY, buttonWidth, BUTTON_HEIGHT)
                .build();
        addDrawableChild(resetBtn);

        ButtonWidget cancelBtn = ButtonWidget.builder(Text.literal("§c✖ 取消模仿"), button -> {
                    ClientPlayNetworking.send(new ImitateSelectPacket("cancel"));
                    if (client != null) {
                        client.setScreen(null);
                    }
                })
                .dimensions(footerStartX + buttonWidth + spacing, footerY, buttonWidth, BUTTON_HEIGHT)
                .build();
        addDrawableChild(cancelBtn);

        int soundWaveY = footerY + BUTTON_HEIGHT + 10;
        int soundWaveWidth = 220;
        int soundWaveX = panelX + (panelWidth - soundWaveWidth) / 2;

        boolean canUseSoundWave = witchStage >= soundWaveThreshold;
        Text soundWaveText = canUseSoundWave 
                ? Text.literal("§b♪ 声音震荡 §r♪") 
                : Text.literal("§7♪ 声音震荡 (需魔女化" + soundWaveThreshold + ") §r♪");
        ButtonWidget soundWaveBtn = ButtonWidget.builder(soundWaveText, button -> {
                    if (!canUseSoundWave) {
                        if (client != null && client.player != null) {
                            client.player.sendMessage(Text.literal("§c魔女化不足，需要达到 " + soundWaveThreshold + " 才能使用声音震荡"), true);
                        }
                        return;
                    }
                    ClientPlayNetworking.send(new SoundWavePacket());
                    if (client != null) {
                        client.setScreen(null);
                    }
                })
                .dimensions(soundWaveX, soundWaveY, soundWaveWidth, BUTTON_HEIGHT)
                .build();
        addDrawableChild(soundWaveBtn);

        int silenceY = footerY + BUTTON_HEIGHT + 10 + BUTTON_HEIGHT + 10;
        int silenceWidth = 220;
        int silenceX = panelX + (panelWidth - silenceWidth) / 2;

        boolean canUseSilence = witchStage >= silenceThreshold;
        Text silenceText = canUseSilence 
                ? Text.literal("§c🔇 屏蔽声音 §r🔇") 
                : Text.literal("§7🔇 屏蔽声音 (需魔女化" + silenceThreshold + ") §r🔇");
        ButtonWidget silenceBtn = ButtonWidget.builder(silenceText, button -> {
                    if (!canUseSilence) {
                        if (client != null && client.player != null) {
                            client.player.sendMessage(Text.literal("§c魔女化不足，需要达到 " + silenceThreshold + " 才能使用屏蔽声音"), true);
                        }
                        return;
                    }
                    if (client != null && client.player != null) {
                        try {
                            int stage = witchStage;
                            ImitateConfig config = ImitateConfig.getInstance();
                            double radius = config != null ? config.getSilenceRadius(stage) : 10.0;
                            client.setScreen(new SilenceTargetScreen(radius));
                        } catch (Exception e) {
                            client.setScreen(new SilenceTargetScreen(10.0));
                        }
                    }
                })
                .dimensions(silenceX, silenceY, silenceWidth, BUTTON_HEIGHT)
                .build();
        addDrawableChild(silenceBtn);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xCC000000);

        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF555555);
        context.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF333333);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0222222);

        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF888888);

        context.fill(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT - 5, 0xFF333366);
        context.drawCenteredTextWithShadow(textRenderer, "§d✦ 模仿魔法 §r✦", panelX + panelWidth / 2, panelY + 8, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}