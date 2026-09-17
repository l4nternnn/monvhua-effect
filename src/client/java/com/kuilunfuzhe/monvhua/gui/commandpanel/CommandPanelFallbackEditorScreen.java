package com.kuilunfuzhe.monvhua.gui.commandpanel;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

final class CommandPanelFallbackEditorScreen extends Screen {
    private final Screen parent;
    private final String initialCommand;
    private final Consumer<String> saved;
    private EditBoxWidget editor;

    CommandPanelFallbackEditorScreen(Screen parent, String initialCommand, Consumer<String> saved) {
        super(Text.literal("编辑指令"));
        this.parent = parent;
        this.initialCommand = initialCommand;
        this.saved = saved;
    }

    @Override
    protected void init() {
        int margin = 22;
        int top = 42;
        int bottom = height - 42;
        editor = EditBoxWidget.builder()
                .x(margin).y(top)
                .placeholder(Text.literal("输入指令"))
                .build(textRenderer, width - margin * 2, bottom - top, Text.literal("控制台命令"));
        editor.setMaxLength(32767);
        editor.setMaxLines(Integer.MAX_VALUE);
        editor.setText(initialCommand);
        addDrawableChild(editor);
        addDrawableChild(ButtonWidget.builder(Text.literal("完成"), button -> {
            saved.accept(editor.getText());
            if (client != null) client.setScreen(parent);
        }).dimensions(width / 2 - 104, height - 30, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> close())
                .dimensions(width / 2 + 4, height - 30, 100, 20).build());
        setInitialFocus(editor);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xCC101014);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
