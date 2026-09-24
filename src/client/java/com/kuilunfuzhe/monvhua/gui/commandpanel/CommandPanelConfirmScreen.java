package com.kuilunfuzhe.monvhua.gui.commandpanel;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class CommandPanelConfirmScreen extends Screen {
    private final Screen parent;
    private final Text message;
    private final Runnable confirmed;

    CommandPanelConfirmScreen(Screen parent, Text title, Text message, Runnable confirmed) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.confirmed = confirmed;
    }

    @Override
    protected void init() {
        int y = height / 2 + 18;
        addDrawableChild(ButtonWidget.builder(Text.literal("确认删除"), button -> {
            confirmed.run();
            if (client != null && client.currentScreen == this) client.setScreen(parent);
        }).dimensions(width / 2 - 105, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> close())
                .dimensions(width / 2 + 5, y, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xCC101014);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 24, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, message, width / 2, height / 2, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
