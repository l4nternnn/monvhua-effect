package com.kuilunfuzhe.monvhua.gui.commandpanel;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class CommandPopupEditScreen extends Screen {
    private static final int ROW_HEIGHT = 26;

    private final CommandPanelScreen parent;
    private final CommandPanelScreen.Popup popup;
    private final List<String> commands = new ArrayList<>();
    private final List<RowWidgets> rows = new ArrayList<>();
    private TextFieldWidget nameField;
    private String draftName;
    private int scrollRow;
    private int listTop;
    private int listBottom;
    private boolean draggingScrollbar;

    public CommandPopupEditScreen(CommandPanelScreen parent, CommandPanelScreen.Popup popup) {
        super(Text.literal("编辑弹窗"));
        this.parent = parent;
        this.popup = popup;
        draftName = popup.name;
        for (CommandPanelScreen.CommandLine line : popup.commands) commands.add(line.command == null ? "" : line.command);
        if (commands.isEmpty()) commands.add("");
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(900, width - 48);
        int left = (width - contentWidth) / 2;
        listTop = 76;
        listBottom = height - 52;

        nameField = new TextFieldWidget(textRenderer, left, 45, contentWidth, 20, Text.literal("名称"));
        nameField.setMaxLength(256);
        nameField.setText(draftName);
        addDrawableChild(nameField);

        rows.clear();
        for (int i = 0; i < commands.size(); i++) addRowWidgets(i, left, contentWidth);
        updateRows();

        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> saveAndClose())
                .dimensions(width / 2 - 50, height - 32, 100, 20).build());
        setInitialFocus(nameField);
    }

    private void addRowWidgets(int index, int left, int contentWidth) {
        ButtonWidget add = ButtonWidget.builder(Text.literal("+"), button -> addCommand())
                .dimensions(left, 0, 20, 20).build();
        TextFieldWidget field = new TextFieldWidget(textRenderer, left + 26, 0, contentWidth - 80, 20, Text.literal("指令 " + (index + 1)));
        field.setMaxLength(32767);
        field.setText(commands.get(index));
        ButtonWidget expand = ButtonWidget.builder(Text.literal("↗"), button -> openExpandedEditor(index))
                .dimensions(left + contentWidth - 48, 0, 20, 20)
                .tooltip(Tooltip.of(Text.literal("展开指令编辑器"))).build();
        ButtonWidget remove = ButtonWidget.builder(Text.literal("×"), button -> removeCommand(index))
                .dimensions(left + contentWidth - 20, 0, 20, 20).build();
        addDrawableChild(add);
        addDrawableChild(field);
        addDrawableChild(expand);
        addDrawableChild(remove);
        rows.add(new RowWidgets(index, add, field, expand, remove));
    }

    private void captureCommands() {
        if (nameField != null) draftName = nameField.getText();
        for (RowWidgets row : rows) commands.set(row.index, row.field.getText());
    }

    private void addCommand() {
        captureCommands();
        commands.add("");
        scrollRow = Math.max(0, commands.size() - visibleRowCount());
        clearAndInit();
    }

    private void removeCommand(int index) {
        captureCommands();
        if (commands.size() == 1) commands.set(0, "");
        else commands.remove(index);
        scrollRow = Math.min(scrollRow, Math.max(0, commands.size() - visibleRowCount()));
        clearAndInit();
    }

    private void openExpandedEditor(int index) {
        captureCommands();
        if (client != null) client.setScreen(CommandPanelCommandEditor.open(this, commands.get(index), value -> commands.set(index, normalizeCommand(value))));
    }

    private int visibleRowCount() {
        return Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
    }

    private void updateRows() {
        int last = commands.size() - 1;
        int visible = visibleRowCount();
        for (RowWidgets row : rows) {
            boolean shown = row.index >= scrollRow && row.index < scrollRow + visible;
            int y = listTop + (row.index - scrollRow) * ROW_HEIGHT;
            row.add.visible = shown && row.index == last;
            row.field.visible = shown;
            row.expand.visible = shown;
            row.remove.visible = shown;
            row.add.setY(y);
            row.field.setY(y);
            row.expand.setY(y);
            row.remove.setY(y);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= listTop && mouseY <= listBottom) {
            captureCommands();
            int max = Math.max(0, commands.size() - visibleRowCount());
            scrollRow = Math.max(0, Math.min(max, scrollRow - (int)Math.signum(vertical)));
            updateRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && commands.size() > visibleRowCount() && mouseX >= scrollbarX() - 3 && mouseX <= scrollbarX() + 6
                && mouseY >= listTop && mouseY <= listBottom) {
            draggingScrollbar = true;
            scrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0) {
            scrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void scrollFromMouse(double mouseY) {
        captureCommands();
        int max = Math.max(0, commands.size() - visibleRowCount());
        double ratio = (mouseY - listTop) / Math.max(1.0, listBottom - listTop);
        scrollRow = Math.max(0, Math.min(max, (int)Math.round(ratio * max)));
        updateRows();
    }

    private int scrollbarX() {
        return nameField.getX() + nameField.getWidth() + 4;
    }

    private static String normalizeCommand(String command) {
        return command.replace('\r', ' ').replace('\n', ' ');
    }

    private void saveAndClose() {
        captureCommands();
        popup.name = nameField.getText().isBlank() ? "Popup" : nameField.getText();
        popup.commands.clear();
        for (String command : commands) if (!command.isBlank()) popup.commands.add(new CommandPanelScreen.CommandLine(command));
        if (popup.commands.isEmpty()) popup.commands.add(new CommandPanelScreen.CommandLine(""));
        parent.savePanel();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xCC101014);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("名称"), nameField.getX(), 33, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("指令（从上到下，每条间隔 2 tick）"), nameField.getX(), listTop - 12, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        if (commands.size() > visibleRowCount()) {
            int barX = scrollbarX();
            int track = listBottom - listTop;
            int thumb = Math.max(12, track * visibleRowCount() / commands.size());
            int maxScroll = commands.size() - visibleRowCount();
            int thumbY = listTop + (track - thumb) * scrollRow / Math.max(1, maxScroll);
            context.fill(barX, listTop, barX + 3, listBottom, 0x55777777);
            context.fill(barX, thumbY, barX + 3, thumbY + thumb, 0xFFFFFFFF);
        }
    }

    private record RowWidgets(int index, ButtonWidget add, TextFieldWidget field, ButtonWidget expand, ButtonWidget remove) {}
}
