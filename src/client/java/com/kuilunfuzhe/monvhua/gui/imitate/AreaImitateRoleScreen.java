package com.kuilunfuzhe.monvhua.gui.imitate;

import com.kuilunfuzhe.monvhua.WitchStage;
import com.kuilunfuzhe.monvhua.client.imitate.ImitateClientManager;
import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.item.config.ImitateConfig;
import com.kuilunfuzhe.monvhua.network.imitate.AreaImitateSelectPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public class AreaImitateRoleScreen extends Screen {

    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_SPACING = 4;
    private static final int COLUMNS = 5;
    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 50;
    private static final int PADDING = 10;

    private int panelWidth;
    private int panelHeight;
    private int panelX;
    private int panelY;
    private final int witchScore;
    private final Vec3d center;
    private final double radius;

    public AreaImitateRoleScreen(int witchScore, Vec3d center, double radius) {
        super(Text.literal("区域模仿角色选择"));
        this.witchScore = witchScore;
        this.center = center;
        this.radius = radius;
    }

    @Override
    protected void init() {
        super.init();

        int totalButtons = ImitateManager.ROLES.length;
        int rows = (int) Math.ceil((double) totalButtons / COLUMNS);

        panelWidth = COLUMNS * (BUTTON_WIDTH + BUTTON_SPACING) - BUTTON_SPACING + PADDING * 2;
        panelHeight = HEADER_HEIGHT + rows * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING + FOOTER_HEIGHT + PADDING * 2;

        int sw = this.client.getWindow().getScaledWidth();
        int sh = this.client.getWindow().getScaledHeight();
        panelX = (sw - panelWidth) / 2;
        panelY = (sh - panelHeight) / 2;

        TextWidget titleWidget = new TextWidget(
                panelX, panelY + 5, panelWidth, 20,
                Text.literal("§d✦ 选择模仿角色 (区域模式) §r✦"), textRenderer
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
                        ClientPlayNetworking.send(new AreaImitateSelectPacket(role, center.x, center.y, center.z, radius));
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

        ButtonWidget cancelBtn = ButtonWidget.builder(Text.literal("§c✖ 取消"), button -> {
                    if (client != null) {
                        client.setScreen(new ImitateScreen(witchScore));
                    }
                })
                .dimensions(footerStartX + buttonWidth + spacing, footerY, buttonWidth, BUTTON_HEIGHT)
                .build();
        addDrawableChild(cancelBtn);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xCC000000);

        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF555555);
        context.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF333333);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0222222);

        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF888888);

        context.fill(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT - 5, 0xFF333366);
        context.drawCenteredTextWithShadow(textRenderer, "§d✦ 区域模仿 §r✦", panelX + panelWidth / 2, panelY + 8, 0xFFFFFF);

        String posText = String.format("§7中心: %.1f, %.1f, %.1f §r§7范围: %.1f格", center.x, center.y, center.z, radius);
        context.drawCenteredTextWithShadow(textRenderer, posText, panelX + panelWidth / 2, panelY + HEADER_HEIGHT + 5, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}