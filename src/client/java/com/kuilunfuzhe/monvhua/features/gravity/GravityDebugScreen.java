package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class GravityDebugScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 132;
    private static final int BUTTON_HEIGHT = 20;

    private int panelX;
    private int panelY;

    public GravityDebugScreen() {
        super(Text.literal("重力调试棒"));
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        int x = panelX + 16;
        int y = panelY + 30;

        addModeButton(x, y, GravityDebugClient.ToolMode.NORMAL);
        addModeButton(x, y + 24, GravityDebugClient.ToolMode.IRREGULAR);
        addModeButton(x, y + 48, GravityDebugClient.ToolMode.SELECTION);
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), button -> close())
                .dimensions(x, y + 76, PANEL_WIDTH - 32, BUTTON_HEIGHT).build());
    }

    private void addModeButton(int x, int y, GravityDebugClient.ToolMode mode) {
        addDrawableChild(ButtonWidget.builder(Text.literal(mode.displayName() + "配置"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            GravityDebugClient.setMode(mode);
            client.setScreen(new GravityDebugModeScreen(mode));
        }).dimensions(x, y, PANEL_WIDTH - 32, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xE0202024);
        context.drawBorder(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFFE6D65A);
        context.drawCenteredTextWithShadow(textRenderer, "重力调试棒", panelX + PANEL_WIDTH / 2, panelY + 10, 0xFFFFF4A0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
