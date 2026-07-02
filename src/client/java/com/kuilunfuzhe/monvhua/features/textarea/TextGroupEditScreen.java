package com.kuilunfuzhe.monvhua.features.textarea;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.area_tip.AreaTipClient;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TextGroupEditScreen extends Screen {
    private static final Identifier SNAPSHOT_ID = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/text_area_editor_snapshot");
    private static final Identifier COLOR_MAP_TEXTURE_ID = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/text_area_color_map");
    private static final String[] FONT_OPTIONS = {
            "minecraft:default",
            "minecraft:uniform",
            "minecraft:alt",
            "modernui:source-han-sans-cn-medium",
            "modernui:mui-i18n-compat"
    };
    private static final int COLOR_MAP_SIZE = 132;
    private static final int HUE_WIDTH = 12;

    private final Screen parent;
    private AreaTipConfig original;
    private final AreaTipConfig working;
    private NativeImageBackedTexture snapshotTexture;
    private NativeImageBackedTexture colorMapTexture;
    private boolean hasSnapshot;
    private int selectedGroupIndex;
    private int selectedTextIndex;
    private int listX;
    private int listY;
    private int listWidth;
    private int editorX;
    private int editorWidth;
    private boolean leftHidden;
    private boolean rightHidden;
    private RichTextFieldWidget textField;
    private TextFieldWidget fontField;
    private TextFieldWidget sizeField;
    private TextFieldWidget wrapField;
    private TextFieldWidget offsetXField;
    private TextFieldWidget offsetYField;
    private TextFieldWidget delayField;
    private TextFieldWidget displayField;
    private TextFieldWidget fadeField;
    private TextFieldWidget priorityField;
    private ButtonWidget visibleButton;
    private ButtonWidget alignButton;
    private ButtonWidget fontButton;
    private boolean updatingFields;
    private boolean dirty;
    private boolean fontDropdownOpen;
    private int fontScroll;
    private boolean colorPickerOpen;
    private boolean colorPicking;
    private float hue = 0.13F;
    private float saturation = 1.0F;
    private float value = 1.0F;
    private float uploadedHue = -1.0F;
    private int pendingColor;
    private int savedTextSelectionStart = -1;
    private int savedTextSelectionEnd = -1;
    private GenericDragger dragger;

    public TextGroupEditScreen(Screen parent) {
        super(Text.literal("文字区域界面"));
        this.parent = parent;
        this.original = AreaTipConfig.getInstance();
        this.working = AreaTipConfig.fromJson(original.toJson());
        this.selectedGroupIndex = selectedIndexFromConfig();
    }

    public void receiveConfig(AreaTipConfig config) {
        if (dirty || config == null) {
            return;
        }
        AreaTipConfig refreshed = AreaTipConfig.fromJson(config.toJson());
        original = AreaTipConfig.fromJson(config.toJson());
        working.groups = refreshed.groups;
        working.selectedGroupId = refreshed.selectedGroupId;
        selectedGroupIndex = selectedIndexFromConfig();
        selectedTextIndex = 0;
        if (textField != null) {
            refreshFields();
        }
    }

    @Override
    protected void init() {
        captureSnapshot();
        relayout();
        int buttonY = height - 28;

        textField = addDrawableChild(new RichTextFieldWidget(textRenderer, editorX, 86, editorWidth, 18, Text.literal("文字")));
        textField.setMaxLength(AreaTipConfig.MAX_MESSAGE_LENGTH);
        textField.setChangedListener(value -> {
            if (!updatingFields) {
                dirty = true;
                AreaTipConfig.HudTextEntry entry = currentEntry();
                entry.text = value;
                entry.sanitizeFontSpans();
                clearSavedTextSelection();
            }
        });

        fontField = addDrawableChild(new TextFieldWidget(textRenderer, editorX, 144, editorWidth - 24, 18, Text.literal("字体")));
        fontField.setMaxLength(128);
        fontField.setChangedListener(value -> {
            if (!updatingFields) {
                dirty = true;
                applySelectedFont(value);
            }
        });

        fontButton = addDrawableChild(ButtonWidget.builder(Text.literal("⌄"), button -> {
                    rememberTextSelection();
                    fontDropdownOpen = !fontDropdownOpen;
                })
                .dimensions(editorX + editorWidth - 22, 144, 22, 18)
                .build());

        sizeField = addFloatField(editorX, 202, 52, value -> currentEntry().fontSize = Math.clamp(value, 0.25F, 8.0F));
        offsetXField = addNumberField(editorX + 62, 202, 52, value -> currentEntry().offsetX = clampInt(value, -4096, 4096));
        offsetYField = addNumberField(editorX + 124, 202, 52, value -> currentEntry().offsetY = clampInt(value, -4096, 4096));
        wrapField = addNumberField(editorX, 238, 70, value -> currentEntry().wrapWidth = clampInt(value, 16, 4096));
        delayField = addNumberField(editorX, 292, 58, value -> currentEntry().delayTicks = clampInt(value, 0, 72000));
        displayField = addNumberField(editorX + 66, 292, 58, value -> currentEntry().displayTicks = clampInt(value, 1, 72000));
        fadeField = addNumberField(editorX + 132, 292, 58, value -> currentEntry().fadeTicks = clampInt(value, 0, 72000));
        priorityField = addNumberField(editorX, 328, 58, value -> currentEntry().priority = clampInt(value, -100000, 100000));

        visibleButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            currentGroup().hudVisible = !currentGroup().hudVisible;
            dirty = true;
            refreshFields();
        }).dimensions(editorX, 46, 84, 20).build());
        alignButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            AreaTipConfig.HudTextEntry entry = currentEntry();
            entry.align = switch (entry.align) {
                case "left" -> "center";
                case "center" -> "right";
                default -> "left";
            };
            dirty = true;
            refreshFields();
        }).dimensions(editorX + 92, 46, 84, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("新增文本"), button -> addTextEntry())
                .dimensions(editorX, buttonY - 48, 82, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("删除当前"), button -> deleteSelectedTextEntry())
                .dimensions(editorX + 88, buttonY - 72, 76, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取色"), button -> openColorPicker())
                .dimensions(editorX + 88, buttonY - 48, 52, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("导入背景"), button -> MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(new TextAreaBackgroundImportScreen(this))))
                .dimensions(editorX, buttonY - 24, 82, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("复制样式"), button -> copyStyleFromPreviousGroup())
                .dimensions(editorX + 88, buttonY - 24, 76, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> saveAndClose())
                .dimensions(width - 178, buttonY, 52, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> cancelAndClose())
                .dimensions(width - 120, buttonY, 52, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> closeToParent())
                .dimensions(width - 62, buttonY, 52, 20)
                .build());

        dragger = new GenericDragger();
        dragger.enableDragging(true);
        refreshFields();
    }

    private void relayout() {
        listWidth = leftHidden ? 0 : Math.min(160, Math.max(118, width / 5));
        listX = 12;
        listY = 44;
        editorWidth = rightHidden ? 0 : Math.min(240, Math.max(205, width / 4));
        editorX = width - editorWidth - 12;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderSnapshot(context);
        TextGroupRenderer.renderGroup(context, currentGroup(), true, selectedTextIndex, -1L);
        renderPanels(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        if (fontDropdownOpen) {
            renderFontDropdown(context, mouseX, mouseY);
        }
        if (colorPickerOpen) {
            renderColorPicker(context);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            rememberTextSelection();
            if (colorPickerOpen) {
                clickColorPicker(mouseX, mouseY);
                return true;
            }
            if (fontDropdownOpen && clickFontDropdown(mouseX, mouseY)) {
                return true;
            }
            if (clickPanelToggles(mouseX, mouseY)) {
                return true;
            }
            int row = groupRowAt(mouseX, mouseY);
            if (row >= 0) {
                selectGroup(row);
                return true;
            }
            int textRow = textEntryRowAt(mouseX, mouseY);
            if (textRow >= 0) {
                selectedTextIndex = textRow;
                clearSavedTextSelection();
                refreshFields();
                return true;
            }
        }
        boolean textFieldClick = textField != null && isInside(textField.getX(), textField.getY(),
                textField.getWidth(), textField.getHeight(), mouseX, mouseY);
        if (super.mouseClicked(mouseX, mouseY, button)) {
            if (textFieldClick) {
                rememberTextSelection();
            }
            return true;
        }
        AreaTipConfig.HudTextEntry hit = hitEntry(mouseX, mouseY);
        if (hit != null) {
            selectedTextIndex = currentGroup().hudTexts.indexOf(hit);
            clearSavedTextSelection();
            refreshFields();
            dragger.attachTo(() -> transformOf(hit));
            dragger.setTransformCallback(transform -> {
                dirty = true;
                applyTransform(hit, transform);
            });
            return dragger.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && colorPickerOpen) {
            if (colorPicking) {
                pickColor(mouseX, mouseY);
            }
            return true;
        }
        return dragger != null && dragger.mouseDragged(mouseX, mouseY, button)
                || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && colorPicking) {
            colorPicking = false;
            return true;
        }
        if (button == 0 && colorPickerOpen) {
            return true;
        }
        boolean handled = dragger != null && dragger.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
        if (button == 0) {
            rememberTextSelection();
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (fontDropdownOpen && isInside(editorX, 164, editorWidth, 96, mouseX, mouseY)) {
            int maxScroll = Math.max(0, FONT_OPTIONS.length - 5);
            fontScroll = MathHelper.clamp(fontScroll - (int) Math.signum(verticalAmount), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        closeToParent();
    }

    @Override
    public void removed() {
        if (snapshotTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(SNAPSHOT_ID);
            snapshotTexture = null;
        }
        if (colorMapTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(COLOR_MAP_TEXTURE_ID);
            colorMapTexture = null;
        }
    }

    public void applyImportedBackground(String fileName, int imageWidth, int imageHeight) {
        AreaTipConfig.HudTextEntry entry = currentEntry();
        entry.background = fileName;
        int maxWidth = Math.max(64, width / 3);
        int maxHeight = Math.max(32, height / 3);
        double ratio = Math.min(maxWidth / (double) imageWidth, maxHeight / (double) imageHeight);
        entry.width = Math.max(32, (int) Math.round(imageWidth * ratio));
        entry.height = Math.max(16, (int) Math.round(imageHeight * ratio));
        entry.scale = 1.0F;
        entry.rotation = 0.0F;
        entry.x = (width - entry.width) * 0.5F;
        entry.y = (height - entry.height) * 0.5F;
        dirty = true;
        TextGroupRenderer.cleanupTextures();
    }

    private TextFieldWidget addNumberField(int x, int y, int w, IntConsumer consumer) {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal("数值")));
        field.setMaxLength(8);
        field.setChangedListener(value -> {
            if (updatingFields) {
                return;
            }
            try {
                consumer.accept(Integer.parseInt(value.trim()));
                dirty = true;
            } catch (NumberFormatException ignored) {
            }
        });
        return field;
    }

    private TextFieldWidget addFloatField(int x, int y, int w, FloatConsumer consumer) {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal("数值")));
        field.setMaxLength(8);
        field.setChangedListener(value -> {
            if (updatingFields) {
                return;
            }
            try {
                consumer.accept(Float.parseFloat(value.trim()));
                dirty = true;
            } catch (NumberFormatException ignored) {
            }
        });
        return field;
    }

    private void renderPanels(DrawContext context, int mouseX, int mouseY) {
        drawPanelToggle(context, 4, height / 2 - 12, leftHidden ? ">" : "<");
        drawPanelToggle(context, width - 18, height / 2 - 12, rightHidden ? "<" : ">");
        if (!leftHidden) {
            renderLeftPanel(context, mouseX, mouseY);
        }
        if (!rightHidden) {
            renderRightPanel(context, mouseX, mouseY);
        }
        context.drawText(textRenderer, Text.literal("左键拖动，右下角缩放，右键旋转"), 12, height - 18, 0xFFE0E0E0, true);
    }

    private void renderLeftPanel(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, listX + listWidth + 12, height, 0xAA101014);
        context.drawText(textRenderer, Text.literal("AreaTip 组"), listX, 20, 0xFFFFFFFF, true);
        for (int i = 0; i < working.groups.size(); i++) {
            AreaTipConfig.GroupConfig group = working.groups.get(i);
            int y = listY + i * 22;
            if (y > height - 54) {
                break;
            }
            int bg = i == selectedGroupIndex ? 0xCC375A7F : groupRowAt(mouseX, mouseY) == i ? 0x66375A7F : 0x33000000;
            context.fill(listX, y, listX + listWidth, y + 20, bg);
            context.fill(listX + 5, y + 5, listX + 15, y + 15, group.color);
            context.drawText(textRenderer, Text.literal(trimToWidth(group.name, listWidth - 26)), listX + 20, y + 6, 0xFFEFEFEF, false);
        }
    }

    private void renderRightPanel(DrawContext context, int mouseX, int mouseY) {
        context.fill(editorX - 12, 0, width, height, 0xAA101014);
        AreaTipConfig.HudTextEntry entry = currentEntry();
        AreaTipConfig.GroupConfig group = currentGroup();
        context.drawText(textRenderer, Text.literal("当前: " + trimToWidth(group.name, editorWidth - 40)), editorX, 20, 0xFFFFFFFF, true);
        context.drawText(textRenderer, Text.literal("文本条目"), editorX, 72, 0xFFB8B8C2, false);
        renderTextEntryTabs(context, mouseX, mouseY);
        context.drawText(textRenderer, Text.literal("HUD 文本"), editorX, 116, 0xFFB8B8C2, false);
        context.drawText(textRenderer, Text.literal("字体 ID"), editorX, 130, 0xFFB8B8C2, false);
        context.drawText(textRenderer, Text.literal("字号 / X / Y"), editorX, 190, 0xFFB8B8C2, false);
        context.drawText(textRenderer, Text.literal("换行宽度"), editorX, 226, 0xFFB8B8C2, false);
        context.drawText(textRenderer, Text.literal("延迟 / 显示 / 消失 tick"), editorX, 280, 0xFFB8B8C2, false);
        context.drawText(textRenderer, Text.literal("优先级"), editorX, 316, 0xFFB8B8C2, false);
        context.fill(editorX, 354, editorX + 18, 372, entry.color);
        drawBorder(context, editorX, 354, 18, 18);
        context.drawText(textRenderer, Text.literal(String.format(Locale.ROOT, "#%06X", entry.color & 0xFFFFFF)), editorX + 26, 359, 0xFFE0E0E0, false);
        context.drawText(textRenderer, Text.literal(String.format(Locale.ROOT, "位置 %.0f, %.0f  缩放 %.2f  旋转 %.0f",
                entry.x, entry.y, entry.scale, entry.rotation)), editorX, 382, 0xFFE0E0E0, false);
    }

    private void renderTextEntryTabs(DrawContext context, int mouseX, int mouseY) {
        List<AreaTipConfig.HudTextEntry> entries = currentGroup().hudTexts;
        int rowY = 100;
        for (int i = 0; i < Math.min(entries.size(), 5); i++) {
            int x = editorX + i * 38;
            boolean selected = i == selectedTextIndex;
            boolean hover = isInside(x, rowY, 34, 16, mouseX, mouseY);
            context.fill(x, rowY, x + 34, rowY + 16, selected ? 0xCC375A7F : hover ? 0x66375A7F : 0x55202028);
            context.drawText(textRenderer, Text.literal(Integer.toString(i + 1)), x + 13, rowY + 4, 0xFFFFFFFF, false);
        }
    }

    private void renderFontDropdown(DrawContext context, int mouseX, int mouseY) {
        int x = editorX;
        int y = 164;
        int rows = Math.min(5, FONT_OPTIONS.length);
        context.fill(x, y, x + editorWidth, y + rows * 20 + 4, 0xF0101015);
        drawBorder(context, x, y, editorWidth, rows * 20 + 4);
        for (int i = 0; i < rows; i++) {
            int index = fontScroll + i;
            if (index >= FONT_OPTIONS.length) {
                break;
            }
            int rowY = y + 2 + i * 20;
            boolean hover = isInside(x + 2, rowY, editorWidth - 4, 18, mouseX, mouseY);
            context.fill(x + 2, rowY, x + editorWidth - 2, rowY + 18, hover ? 0x66375A7F : 0x00000000);
            context.drawText(textRenderer, Text.literal(FONT_OPTIONS[index]), x + 6, rowY + 5, 0xFFEFEFEF, false);
        }
    }

    private void renderColorPicker(DrawContext context) {
        uploadColorMapIfNeeded();
        int panelW = COLOR_MAP_SIZE + HUE_WIDTH + 126;
        int panelH = COLOR_MAP_SIZE + 64;
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int mapX = panelX + 14;
        int mapY = panelY + 34;
        int hueX = mapX + COLOR_MAP_SIZE + 8;
        int controlX = hueX + HUE_WIDTH + 16;
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0101015);
        context.drawText(textRenderer, Text.literal("文本颜色"), panelX + 14, panelY + 10, 0xFFFFFFFF, false);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, COLOR_MAP_TEXTURE_ID, mapX, mapY, 0.0F, 0.0F,
                COLOR_MAP_SIZE, COLOR_MAP_SIZE, COLOR_MAP_SIZE, COLOR_MAP_SIZE);
        drawBorder(context, mapX, mapY, COLOR_MAP_SIZE, COLOR_MAP_SIZE);
        for (int y = 0; y < COLOR_MAP_SIZE; y++) {
            int color = 0xFF000000 | hsvToRgb((float) y / (COLOR_MAP_SIZE - 1), 1.0F, 1.0F);
            context.fill(hueX, mapY + y, hueX + HUE_WIDTH, mapY + y + 1, color);
        }
        drawBorder(context, hueX, mapY, HUE_WIDTH, COLOR_MAP_SIZE);
        drawColorCursors(context, mapX, mapY, hueX);
        context.fill(controlX, mapY, controlX + 32, mapY + 32, pendingColor);
        drawBorder(context, controlX, mapY, 32, 32);
        context.drawText(textRenderer, Text.literal(String.format(Locale.ROOT, "#%06X", pendingColor & 0xFFFFFF)), controlX, mapY + 42, 0xFFEFEFEF, false);
        drawButton(context, controlX, mapY + 72, 54, 18, "应用", true);
        drawButton(context, controlX + 60, mapY + 72, 54, 18, "关闭", true);
    }

    private boolean clickColorPicker(double mouseX, double mouseY) {
        int panelW = COLOR_MAP_SIZE + HUE_WIDTH + 126;
        int panelH = COLOR_MAP_SIZE + 64;
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int mapX = panelX + 14;
        int mapY = panelY + 34;
        int hueX = mapX + COLOR_MAP_SIZE + 8;
        int controlX = hueX + HUE_WIDTH + 16;
        if (pickColor(mouseX, mouseY)) {
            colorPicking = true;
            return true;
        }
        if (isInside(controlX, mapY + 72, 54, 18, mouseX, mouseY)) {
            applySelectedColor(pendingColor);
            dirty = true;
            colorPickerOpen = false;
            refreshFieldsPreservingTextSelection();
            return true;
        }
        if (isInside(controlX + 60, mapY + 72, 54, 18, mouseX, mouseY)) {
            colorPickerOpen = false;
            return true;
        }
        return isInside(panelX, panelY, panelW, panelH, mouseX, mouseY);
    }

    private boolean pickColor(double mouseX, double mouseY) {
        int panelW = COLOR_MAP_SIZE + HUE_WIDTH + 126;
        int panelH = COLOR_MAP_SIZE + 64;
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int mapX = panelX + 14;
        int mapY = panelY + 34;
        int hueX = mapX + COLOR_MAP_SIZE + 8;
        if (mouseX >= mapX && mouseX < mapX + COLOR_MAP_SIZE && mouseY >= mapY && mouseY < mapY + COLOR_MAP_SIZE) {
            saturation = MathHelper.clamp((float) ((mouseX - mapX) / (COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            value = MathHelper.clamp(1.0F - (float) ((mouseY - mapY) / (COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            pendingColor = 0xFF000000 | hsvToRgb(hue, saturation, value);
            return true;
        }
        if (mouseX >= hueX && mouseX < hueX + HUE_WIDTH && mouseY >= mapY && mouseY < mapY + COLOR_MAP_SIZE) {
            hue = MathHelper.clamp((float) ((mouseY - mapY) / (COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            uploadedHue = -1.0F;
            pendingColor = 0xFF000000 | hsvToRgb(hue, saturation, value);
            return true;
        }
        return false;
    }

    private void drawColorCursors(DrawContext context, int mapX, int mapY, int hueX) {
        int sx = mapX + Math.round(saturation * (COLOR_MAP_SIZE - 1));
        int sy = mapY + Math.round((1.0F - value) * (COLOR_MAP_SIZE - 1));
        context.fill(sx - 4, sy - 1, sx + 5, sy + 1, 0xFFFFFFFF);
        context.fill(sx - 1, sy - 4, sx + 1, sy + 5, 0xFFFFFFFF);
        int hy = mapY + Math.round(hue * (COLOR_MAP_SIZE - 1));
        context.fill(hueX - 3, hy - 2, hueX + HUE_WIDTH + 3, hy + 2, 0xFFFFFFFF);
        context.fill(hueX - 2, hy - 1, hueX + HUE_WIDTH + 2, hy + 1, 0xFF000000);
    }

    private boolean clickFontDropdown(double mouseX, double mouseY) {
        if (!isInside(editorX, 164, editorWidth, Math.min(5, FONT_OPTIONS.length) * 20 + 4, mouseX, mouseY)) {
            fontDropdownOpen = false;
            return false;
        }
        int row = ((int) mouseY - 166) / 20;
        int index = fontScroll + row;
        if (index >= 0 && index < FONT_OPTIONS.length) {
            applySelectedFont(FONT_OPTIONS[index]);
            dirty = true;
            fontDropdownOpen = false;
            refreshFieldsPreservingTextSelection();
        }
        return true;
    }

    private void applySelectedFont(String font) {
        AreaTipConfig.HudTextEntry entry = currentEntry();
        int[] range = selectedTextRange();
        if (range == null) {
            entry.font = font;
            return;
        }
        entry.applyFontToRange(range[0], range[1], font);
    }

    private void applySelectedColor(int color) {
        AreaTipConfig.HudTextEntry entry = currentEntry();
        int[] range = selectedTextRange();
        if (range == null) {
            entry.color = color;
            entry.useGroupColor = false;
            return;
        }
        entry.applyColorToRange(range[0], range[1], color);
    }

    private int[] selectedTextRange() {
        if (textField == null) {
            return null;
        }
        rememberTextSelection();
        if (!hasSavedTextSelection()) {
            return null;
        }
        int cursor = savedTextSelectionStart;
        int selectionEnd = savedTextSelectionEnd;
        int start = Math.min(cursor, selectionEnd);
        int end = Math.max(cursor, selectionEnd);
        return end > start ? new int[]{start, end} : null;
    }

    private void refreshFieldsPreservingTextSelection() {
        int[] range = selectedTextRange();
        refreshFields();
        if (range != null && textField != null) {
            int length = textField.getText().length();
            int start = Math.clamp(range[0], 0, length);
            int end = Math.clamp(range[1], 0, length);
            textField.setSelection(start, end);
            savedTextSelectionStart = start;
            savedTextSelectionEnd = end;
        }
    }

    private void rememberTextSelection() {
        if (textField == null) {
            return;
        }
        int cursor = textField.getCursor();
        int selectionEnd = textField.getSelectionEnd();
        int start = Math.min(cursor, selectionEnd);
        int end = Math.max(cursor, selectionEnd);
        if (end <= start) {
            return;
        }
        savedTextSelectionStart = start;
        savedTextSelectionEnd = end;
    }

    private boolean hasSavedTextSelection() {
        return savedTextSelectionEnd > savedTextSelectionStart && savedTextSelectionStart >= 0;
    }

    private void clearSavedTextSelection() {
        savedTextSelectionStart = -1;
        savedTextSelectionEnd = -1;
    }

    private boolean clickPanelToggles(double mouseX, double mouseY) {
        if (isInside(4, height / 2 - 12, 14, 24, mouseX, mouseY)) {
            leftHidden = !leftHidden;
            return true;
        }
        if (isInside(width - 18, height / 2 - 12, 14, 24, mouseX, mouseY)) {
            rightHidden = !rightHidden;
            setRightWidgetsVisible(!rightHidden);
            return true;
        }
        return false;
    }

    private int textEntryRowAt(double mouseX, double mouseY) {
        if (rightHidden || mouseY < 100 || mouseY > 116) {
            return -1;
        }
        int row = (int) ((mouseX - editorX) / 38);
        return row >= 0 && row < Math.min(currentGroup().hudTexts.size(), 5) ? row : -1;
    }

    private void addTextEntry() {
        AreaTipConfig.GroupConfig group = currentGroup();
        AreaTipConfig.HudTextEntry entry = AreaTipConfig.HudTextEntry.fromGroup(group.color, group.message);
        entry.text = "";
        entry.x = (width - entry.width) * 0.5F + group.hudTexts.size() * 18.0F;
        entry.y = (height - entry.height) * 0.5F + group.hudTexts.size() * 18.0F;
        entry.offsetX = 8;
        entry.offsetY = 8;
        group.hudTexts.add(entry);
        selectedTextIndex = group.hudTexts.size() - 1;
        dirty = true;
        refreshFields();
    }

    private void deleteSelectedTextEntry() {
        AreaTipConfig.GroupConfig group = currentGroup();
        selectedTextIndex = Math.clamp(selectedTextIndex, 0, group.hudTexts.size() - 1);
        if (group.hudTexts.size() <= 1) {
            AreaTipConfig.HudTextEntry entry = group.hudTexts.get(selectedTextIndex);
            entry.text = "";
            entry.background = "";
        } else {
            group.hudTexts.remove(selectedTextIndex);
            selectedTextIndex = Math.clamp(selectedTextIndex, 0, group.hudTexts.size() - 1);
        }
        dirty = true;
        refreshFields();
    }

    private void openColorPicker() {
        loadColor(currentEntry().color & 0xFFFFFF);
        pendingColor = 0xFF000000 | (currentEntry().color & 0xFFFFFF);
        colorPickerOpen = true;
    }

    private void setRightWidgetsVisible(boolean visible) {
        textField.visible = visible;
        fontField.visible = visible;
        fontButton.visible = visible;
        sizeField.visible = visible;
        wrapField.visible = visible;
        offsetXField.visible = visible;
        offsetYField.visible = visible;
        delayField.visible = visible;
        displayField.visible = visible;
        fadeField.visible = visible;
        priorityField.visible = visible;
        visibleButton.visible = visible;
        alignButton.visible = visible;
    }

    private void drawPanelToggle(DrawContext context, int x, int y, String label) {
        context.fill(x, y, x + 14, y + 24, 0xCC202028);
        drawBorder(context, x, y, 14, 24);
        context.drawText(textRenderer, Text.literal(label), x + 4, y + 8, 0xFFFFFFFF, false);
    }

    private void drawButton(DrawContext context, int x, int y, int width, int height, String label, boolean enabled) {
        context.fill(x, y, x + width, y + height, enabled ? 0xFF2F4E61 : 0xFF303038);
        context.fill(x, y, x + width, y + 1, enabled ? 0xFF88B8D8 : 0xFF666670);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF101018);
        context.drawText(textRenderer, Text.literal(label), x + (width - textRenderer.getWidth(label)) / 2, y + 5,
                enabled ? 0xFFFFFFFF : 0xFF999999, false);
    }

    private void drawBorder(DrawContext context, int x, int y, int width, int height) {
        context.fill(x - 1, y - 1, x + width + 1, y, 0xFFB8B8C2);
        context.fill(x - 1, y + height, x + width + 1, y + height + 1, 0xFF111116);
        context.fill(x - 1, y - 1, x, y + height + 1, 0xFFB8B8C2);
        context.fill(x + width, y - 1, x + width + 1, y + height + 1, 0xFF111116);
    }

    private void refreshFields() {
        AreaTipConfig.GroupConfig group = currentGroup();
        AreaTipConfig.HudTextEntry entry = currentEntry();
        updatingFields = true;
        textField.setText(entry.text);
        fontField.setText(entry.font);
        sizeField.setText(String.format(Locale.ROOT, "%.2f", entry.fontSize));
        offsetXField.setText(Integer.toString(entry.offsetX));
        offsetYField.setText(Integer.toString(entry.offsetY));
        wrapField.setText(Integer.toString(entry.wrapWidth));
        delayField.setText(Integer.toString(entry.delayTicks));
        displayField.setText(Integer.toString(entry.displayTicks));
        fadeField.setText(Integer.toString(entry.fadeTicks));
        priorityField.setText(Integer.toString(entry.priority));
        visibleButton.setMessage(Text.literal(group.hudVisible ? "显示: 开" : "显示: 关"));
        alignButton.setMessage(Text.literal("对齐: " + alignName(entry.align)));
        updatingFields = false;
    }

    private void selectGroup(int index) {
        selectedGroupIndex = Math.clamp(index, 0, Math.max(0, working.groups.size() - 1));
        selectedTextIndex = 0;
        working.selectedGroupId = currentGroup().id;
        refreshFields();
    }

    private AreaTipConfig.GroupConfig currentGroup() {
        if (working.groups.isEmpty()) {
            working.groups.add(new AreaTipConfig.GroupConfig());
        }
        selectedGroupIndex = Math.clamp(selectedGroupIndex, 0, working.groups.size() - 1);
        AreaTipConfig.GroupConfig group = working.groups.get(selectedGroupIndex);
        if (group.hudTexts == null || group.hudTexts.isEmpty()) {
            group.hudTexts = new ArrayList<>();
            group.hudTexts.add(AreaTipConfig.HudTextEntry.fromGroup(group.color, group.message));
        }
        return group;
    }

    private AreaTipConfig.HudTextEntry currentEntry() {
        AreaTipConfig.GroupConfig group = currentGroup();
        selectedTextIndex = Math.clamp(selectedTextIndex, 0, group.hudTexts.size() - 1);
        AreaTipConfig.HudTextEntry entry = group.hudTexts.get(selectedTextIndex);
        if (entry.useGroupColor) {
            entry.color = 0xFF000000 | (group.color & 0xFFFFFF);
        }
        return entry;
    }

    private AreaTipConfig.HudTextEntry hitEntry(double mouseX, double mouseY) {
        List<AreaTipConfig.HudTextEntry> entries = currentGroup().hudTexts.stream()
                .sorted(java.util.Comparator.comparingInt(entry -> -entry.priority))
                .toList();
        for (AreaTipConfig.HudTextEntry entry : entries) {
            float width = entry.width * entry.scale;
            float height = entry.height * entry.scale;
            if (mouseX >= entry.x && mouseX <= entry.x + width && mouseY >= entry.y && mouseY <= entry.y + height) {
                return entry;
            }
        }
        return null;
    }

    private int groupRowAt(double mouseX, double mouseY) {
        if (leftHidden || mouseX < listX || mouseX > listX + listWidth || mouseY < listY || mouseY > height - 54) {
            return -1;
        }
        int row = (int) ((mouseY - listY) / 22);
        return row >= 0 && row < working.groups.size() ? row : -1;
    }

    private int selectedIndexFromConfig() {
        UUID selected = working.selectedGroup().map(AreaTipConfig.GroupConfig::uuid).orElse(null);
        if (selected == null) {
            return 0;
        }
        for (int i = 0; i < working.groups.size(); i++) {
            if (selected.equals(working.groups.get(i).uuid())) {
                return i;
            }
        }
        return 0;
    }

    private int indexOf(String id) {
        for (int i = 0; i < working.groups.size(); i++) {
            if (working.groups.get(i).id.equals(id)) {
                return i;
            }
        }
        return selectedGroupIndex;
    }

    private void copyStyleFromPreviousGroup() {
        if (working.groups.size() < 2) {
            return;
        }
        AreaTipConfig.GroupConfig source = working.groups.get((selectedGroupIndex + working.groups.size() - 1) % working.groups.size()).copy();
        AreaTipConfig.GroupConfig target = currentGroup();
        target.hudVisible = source.hudVisible;
        target.hudTexts = new ArrayList<>();
        for (AreaTipConfig.HudTextEntry entry : source.hudTexts) {
            AreaTipConfig.HudTextEntry copy = entry.copy();
            copy.useGroupColor = true;
            copy.color = target.color;
            target.hudTexts.add(copy);
        }
        selectedTextIndex = 0;
        dirty = true;
        refreshFields();
    }

    private void saveAndClose() {
        working.selectedGroupId = currentGroup().id;
        AreaTipConfig.setInstance(working);
        AreaTipClient.sendConfigUpdate(AreaTipConfig.getInstance());
        closeToParent();
    }

    private void cancelAndClose() {
        AreaTipConfig.syncInstance(original);
        closeToParent();
    }

    private void closeToParent() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private static GenericDragger.Transform transformOf(AreaTipConfig.HudTextEntry entry) {
        return new GenericDragger.Transform(entry.x, entry.y, entry.scale, entry.rotation, entry.width, entry.height);
    }

    private static void applyTransform(AreaTipConfig.HudTextEntry entry, GenericDragger.Transform transform) {
        entry.x = transform.x();
        entry.y = transform.y();
        entry.scale = transform.scale();
        entry.rotation = transform.rotation();
    }

    private void captureSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> {
            if (snapshotTexture != null) {
                client.getTextureManager().destroyTexture(SNAPSHOT_ID);
            }
            snapshotTexture = new NativeImageBackedTexture(() -> "monvhua text area editor snapshot", copyImage(image));
            client.getTextureManager().registerTexture(SNAPSHOT_ID, snapshotTexture);
            hasSnapshot = true;
            image.close();
        });
    }

    private void renderSnapshot(DrawContext context) {
        if (hasSnapshot) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, SNAPSHOT_ID, 0, 0, 0.0F, 0.0F,
                    width, height, width, height);
            context.fill(0, 0, width, height, 0x88000000);
        } else {
            context.fill(0, 0, width, height, 0xDD101014);
        }
    }

    private void uploadColorMapIfNeeded() {
        if (colorMapTexture != null && Math.abs(uploadedHue - hue) < 0.0001F) {
            return;
        }
        NativeImage image = new NativeImage(COLOR_MAP_SIZE, COLOR_MAP_SIZE, false);
        for (int y = 0; y < COLOR_MAP_SIZE; y++) {
            float v = 1.0F - (float) y / (COLOR_MAP_SIZE - 1);
            for (int x = 0; x < COLOR_MAP_SIZE; x++) {
                float s = (float) x / (COLOR_MAP_SIZE - 1);
                image.setColorArgb(x, y, 0xFF000000 | hsvToRgb(hue, s, v));
            }
        }
        if (colorMapTexture == null) {
            colorMapTexture = new NativeImageBackedTexture(() -> "monvhua text area color map", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(COLOR_MAP_TEXTURE_ID, colorMapTexture);
        } else {
            colorMapTexture.setImage(image);
            colorMapTexture.upload();
        }
        uploadedHue = hue;
    }

    private void loadColor(int rgb) {
        float r = ((rgb >>> 16) & 0xFF) / 255.0F;
        float g = ((rgb >>> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        value = max;
        saturation = max == 0.0F ? 0.0F : delta / max;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (max == r) {
            hue = ((g - b) / delta) / 6.0F;
        } else if (max == g) {
            hue = (2.0F + (b - r) / delta) / 6.0F;
        } else {
            hue = (4.0F + (r - g) / delta) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        uploadedHue = -1.0F;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (!result.isEmpty() && textRenderer.getWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private static NativeImage copyImage(NativeImage image) {
        NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), false);
        copy.copyFrom(image);
        return copy;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isInside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String alignName(String align) {
        return switch (align) {
            case "center" -> "居中";
            case "right" -> "右";
            default -> "左";
        };
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hue = (h - (float) Math.floor(h)) * 6.0F;
        int sector = (int) Math.floor(hue);
        float f = hue - sector;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }

    @FunctionalInterface
    private interface IntConsumer {
        void accept(int value);
    }

    @FunctionalInterface
    private interface FloatConsumer {
        void accept(float value);
    }
}
