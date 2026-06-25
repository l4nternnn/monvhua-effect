package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class GravityDebugModeScreen extends Screen {
    private static final int PANEL_WIDTH = 310;
    private static final int PANEL_HEIGHT = 214;
    private static final int BUTTON_HEIGHT = 20;

    private final GravityDebugClient.ToolMode mode;
    private int panelX;
    private int panelY;

    public GravityDebugModeScreen(GravityDebugClient.ToolMode mode) {
        super(Text.literal((mode == null ? GravityDebugClient.ToolMode.NORMAL : mode).displayName() + "配置"));
        this.mode = mode == null ? GravityDebugClient.ToolMode.NORMAL : mode;
        GravityDebugClient.setMode(this.mode);
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        GravityDebugClient.setMode(mode);
        GravityDebugClient.Config config = GravityDebugClient.config(mode);
        int x = panelX + 12;
        int y = panelY + 30;

        if (mode == GravityDebugClient.ToolMode.NORMAL) {
            addDrawableChild(ButtonWidget.builder(Text.literal("形体: " + shapeName(config.shape())), button -> {
                config.cycleShape();
                rebuild();
            }).dimensions(x, y, 140, BUTTON_HEIGHT).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("范围: " + halfName(config.half())), button -> {
                config.cycleHalf();
                rebuild();
            }).dimensions(x + 146, y, 140, BUTTON_HEIGHT).build());
            y += 28;
        }

        y = addSizeRow(config, x, y, "X", 0);
        y = addSizeRow(config, x, y, "Y", 1);
        y = addSizeRow(config, x, y, "Z", 2);

        addDrawableChild(ButtonWidget.builder(Text.literal("持续: " + duration(config)), button -> {
            config.toggleInfinite();
            rebuild();
        }).dimensions(x, y, 92, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("-10s"), button -> {
            config.addSeconds(-10);
            rebuild();
        }).dimensions(x + 100, y, 48, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+10s"), button -> {
            config.addSeconds(10);
            rebuild();
        }).dimensions(x + 154, y, 48, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("g " + GravityMagic.formatGravityMultiplier(config.gravity())), button -> {
            config.addGravityMultiplier(0.1D);
            rebuild();
        }).dimensions(x + 210, y, 76, BUTTON_HEIGHT).build());

        y += 28;
        if (mode == GravityDebugClient.ToolMode.SELECTION) {
            addDrawableChild(ButtonWidget.builder(Text.literal("选区删除: Delete"), button -> {})
                    .dimensions(x, y, 92, BUTTON_HEIGHT).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("操作: " + GravityDebugClient.operation(mode).displayName()), button -> {
                GravityDebugClient.setOperation(mode, GravityDebugClient.operation(mode) == GravityDebugClient.Operation.PLACE
                        ? GravityDebugClient.Operation.MOVE_NEAREST
                        : GravityDebugClient.Operation.PLACE);
                rebuild();
            }).dimensions(x, y, 92, BUTTON_HEIGHT).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal(GravityDebugClient.renderPreview() ? "预览: 开" : "预览: 关"), button -> {
            GravityDebugClient.setMode(mode);
            GravityDebugClient.togglePreview();
            rebuild();
        }).dimensions(x + 100, y, 92, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> MinecraftClient.getInstance().setScreen(new GravityDebugScreen()))
                .dimensions(x + 200, y, 40, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("完成"), button -> close())
                .dimensions(x + 246, y, 40, BUTTON_HEIGHT).build());
    }

    private int addSizeRow(GravityDebugClient.Config config, int x, int y, String label, int axis) {
        int value = axis == 0 ? config.sizeX() : axis == 1 ? config.sizeY() : config.sizeZ();
        addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + value), button -> {})
                .dimensions(x, y, 92, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("-1"), button -> {
            config.addSize(axis, -1);
            rebuild();
        }).dimensions(x + 100, y, 48, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+1"), button -> {
            config.addSize(axis, 1);
            rebuild();
        }).dimensions(x + 154, y, 48, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("-5"), button -> {
            config.addSize(axis, -5);
            rebuild();
        }).dimensions(x + 208, y, 36, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+5"), button -> {
            config.addSize(axis, 5);
            rebuild();
        }).dimensions(x + 250, y, 36, BUTTON_HEIGHT).build());
        return y + 24;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xE0202024);
        context.drawBorder(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFFE6D65A);
        context.drawCenteredTextWithShadow(textRenderer, mode.displayName() + "配置", panelX + PANEL_WIDTH / 2, panelY + 10, 0xFFFFF4A0);
        context.drawTextWithShadow(textRenderer, footerText(), panelX + 12, panelY + PANEL_HEIGHT - 16, 0xFFCFCFCF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private String footerText() {
        return mode == GravityDebugClient.ToolMode.SELECTION
                ? "关闭界面后预览跟随准星  Delete删除选区  Ctrl+Z/Y/C/V"
                : "关闭界面后预览跟随准星  右键放置/移动  左键取消预览";
    }

    private static String shapeName(GravityAreaSpec.Shape shape) {
        return switch (shape) {
            case SPHERE -> "球体";
            case BOX -> "长方体";
            case CUBE -> "立方体";
        };
    }

    private static String halfName(GravityAreaSpec.Half half) {
        return switch (half) {
            case FULL -> "整体";
            case UPPER -> "上半";
            case LOWER -> "下半";
        };
    }

    private static String duration(GravityDebugClient.Config config) {
        return config.ticks() == GravityMagic.INFINITE_AREA_TICKS ? "无限" : config.ticks() / 20 + "s";
    }
}
