package com.kuilunfuzhe.monvhua.features.area_tip;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

public class AreaTipConfigScreen extends Screen {
    private static final int COLOR_MAP_SIZE = 116;
    private static final int HUE_WIDTH = 12;
    private static final Identifier COLOR_MAP_TEXTURE_ID = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/area_tip_color_map");

    private AreaTipConfig config;
    private int selectedIndex;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int leftWidth;
    private int mapX;
    private int mapY;
    private int hueX;
    private TextFieldWidget nameField;
    private TextFieldWidget messageField;
    private ButtonWidget shapeButton;
    private ButtonWidget halfButton;
    private NativeImageBackedTexture colorMapTexture;
    private float hue = 0.13F;
    private float saturation = 1.0F;
    private float value = 1.0F;
    private float uploadedHue = -1.0F;
    private boolean picking;
    private boolean updatingFields;

    public AreaTipConfigScreen() {
        super(Text.literal("Area Tip"));
        this.config = AreaTipConfig.getInstance();
        this.selectedIndex = selectedIndexFromConfig();
        loadColor(currentGroup().color);
    }

    public void receiveConfig(AreaTipConfig config) {
        this.config = config;
        this.selectedIndex = selectedIndexFromConfig();
        loadColor(currentGroup().color);
        uploadedHue = -1.0F;
        if (nameField != null) {
            refreshFields();
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 24, Math.max(380, width / 2));
        panelHeight = Math.min(height - 24, Math.max(230, height / 2));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        leftWidth = panelWidth / 3;

        int editorX = panelX + leftWidth + 12;
        int editorWidth = panelWidth - leftWidth - 24;
        nameField = addDrawableChild(new TextFieldWidget(textRenderer, editorX, panelY + 28, editorWidth / 2 - 6, 18, Text.literal("Name")));
        nameField.setMaxLength(64);
        nameField.setChangedListener(value -> {
            if (!updatingFields) {
                currentGroup().name = value;
            }
        });

        messageField = addDrawableChild(new TextFieldWidget(textRenderer, editorX, panelY + panelHeight - 42, editorWidth, 18, Text.literal("Message")));
        messageField.setMaxLength(AreaTipConfig.MAX_MESSAGE_LENGTH);
        messageField.setChangedListener(value -> {
            if (!updatingFields) {
                currentGroup().message = value;
            }
        });

        shapeButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> cycleShape())
                .dimensions(editorX, panelY + 58, 78, 18)
                .build());
        halfButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> cycleHalf())
                .dimensions(editorX + 84, panelY + 58, 78, 18)
                .build());

        int sizeY = panelY + 86;
        addSizeButton(editorX, sizeY, "X-", 0, -1);
        addSizeButton(editorX + 34, sizeY, "X+", 0, 1);
        addSizeButton(editorX, sizeY + 24, "Y-", 1, -1);
        addSizeButton(editorX + 34, sizeY + 24, "Y+", 1, 1);
        addSizeButton(editorX, sizeY + 48, "Z-", 2, -1);
        addSizeButton(editorX + 34, sizeY + 48, "Z+", 2, 1);

        addDrawableChild(ButtonWidget.builder(Text.literal("New"), button -> newGroup())
                .dimensions(panelX + 10, panelY + panelHeight - 48, 48, 18)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), button -> deleteGroup())
                .dimensions(panelX + 64, panelY + panelHeight - 48, 58, 18)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(panelX + panelWidth - 62, panelY + panelHeight - 22, 52, 18)
                .build());

        mapX = panelX + panelWidth - COLOR_MAP_SIZE - HUE_WIDTH - 28;
        mapY = panelY + 58;
        hueX = mapX + COLOR_MAP_SIZE + 8;
        refreshFields();
        uploadColorMapIfNeeded();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        uploadColorMapIfNeeded();
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0121218);
        context.fill(panelX + leftWidth, panelY, panelX + leftWidth + 1, panelY + panelHeight, 0xFF3C3C44);
        context.drawText(textRenderer, title, panelX + 10, panelY + 10, 0xFFFFFFFF, false);
        drawGroups(context, mouseX, mouseY);
        drawEditor(context);
        drawColorPicker(context);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int row = groupRowAt(mouseX, mouseY);
            if (row >= 0) {
                selectGroup(row);
                return true;
            }
            if (pick(mouseX, mouseY)) {
                picking = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && pick(mouseX, mouseY)) {
            picking = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && picking) {
            picking = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (AreaTipClient.isF2(keyCode)) {
            nameField.setFocused(true);
            setFocused(nameField);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        sendConfigUpdate();
        if (colorMapTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(COLOR_MAP_TEXTURE_ID);
            colorMapTexture = null;
        }
    }

    private void addSizeButton(int x, int y, String label, int axis, int delta) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
            AreaTipConfig.GroupConfig group = currentGroup();
            if (axis == 0) {
                group.sizeX = clampSize(group.sizeX + delta);
            } else if (axis == 1) {
                group.sizeY = clampSize(group.sizeY + delta);
            } else {
                group.sizeZ = clampSize(group.sizeZ + delta);
            }
            if (GravityAreaSpec.Shape.byId(group.shape) == GravityAreaSpec.Shape.CUBE) {
                int size = Math.max(group.sizeX, Math.max(group.sizeY, group.sizeZ));
                group.sizeX = size;
                group.sizeY = size;
                group.sizeZ = size;
            }
        }).dimensions(x, y, 30, 18).build());
    }

    private void drawGroups(DrawContext context, int mouseX, int mouseY) {
        int listX = panelX + 8;
        int listY = panelY + 30;
        int rowWidth = leftWidth - 16;
        for (int i = 0; i < config.groups.size(); i++) {
            int y = listY + i * 20;
            if (y + 18 > panelY + panelHeight - 54) {
                break;
            }
            AreaTipConfig.GroupConfig group = config.groups.get(i);
            int bg = i == selectedIndex ? 0xFF363F4D : (groupRowAt(mouseX, mouseY) == i ? 0x66363F4D : 0x00000000);
            context.fill(listX, y, listX + rowWidth, y + 18, bg);
            context.fill(listX + 4, y + 4, listX + 14, y + 14, group.color);
            context.drawText(textRenderer, Text.literal(group.name), listX + 20, y + 5, 0xFFE8E8EE, false);
        }
    }

    private void drawEditor(DrawContext context) {
        AreaTipConfig.GroupConfig group = currentGroup();
        int editorX = panelX + leftWidth + 12;
        context.drawText(textRenderer, Text.literal("Group"), editorX, panelY + 16, 0xFFB8B8C2, false);
        context.drawText(textRenderer, Text.literal("Size " + group.sizeX + " x " + group.sizeY + " x " + group.sizeZ),
                editorX + 78, panelY + 93, 0xFFE8E8EE, false);
        context.drawText(textRenderer, Text.literal("Text"), editorX, panelY + panelHeight - 54, 0xFFB8B8C2, false);
    }

    private void drawColorPicker(DrawContext context) {
        AreaTipConfig.GroupConfig group = currentGroup();
        context.drawText(textRenderer, Text.literal(String.format("#%06X", group.color & 0xFFFFFF)),
                mapX, mapY - 14, 0xFFE8E8EE, false);
        context.fill(mapX - 18, mapY - 16, mapX - 4, mapY - 2, group.color);
        drawBorder(context, mapX - 18, mapY - 16, 14, 14);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, COLOR_MAP_TEXTURE_ID,
                mapX, mapY, 0.0F, 0.0F, COLOR_MAP_SIZE, COLOR_MAP_SIZE, COLOR_MAP_SIZE, COLOR_MAP_SIZE);
        drawBorder(context, mapX, mapY, COLOR_MAP_SIZE, COLOR_MAP_SIZE);
        for (int y = 0; y < COLOR_MAP_SIZE; y++) {
            int color = 0xFF000000 | hsvToRgb((float) y / (COLOR_MAP_SIZE - 1), 1.0F, 1.0F);
            context.fill(hueX, mapY + y, hueX + HUE_WIDTH, mapY + y + 1, color);
        }
        drawBorder(context, hueX, mapY, HUE_WIDTH, COLOR_MAP_SIZE);
        drawCursors(context);
    }

    private void drawCursors(DrawContext context) {
        int sx = mapX + Math.round(saturation * (COLOR_MAP_SIZE - 1));
        int sy = mapY + Math.round((1.0F - value) * (COLOR_MAP_SIZE - 1));
        context.fill(sx - 4, sy - 1, sx + 5, sy + 1, 0xFFFFFFFF);
        context.fill(sx - 1, sy - 4, sx + 1, sy + 5, 0xFFFFFFFF);
        int hy = mapY + Math.round(hue * (COLOR_MAP_SIZE - 1));
        context.fill(hueX - 3, hy - 2, hueX + HUE_WIDTH + 3, hy + 2, 0xFFFFFFFF);
        context.fill(hueX - 2, hy - 1, hueX + HUE_WIDTH + 2, hy + 1, 0xFF000000);
    }

    private boolean pick(double mouseX, double mouseY) {
        if (mouseX >= mapX && mouseX < mapX + COLOR_MAP_SIZE && mouseY >= mapY && mouseY < mapY + COLOR_MAP_SIZE) {
            saturation = MathHelper.clamp((float) ((mouseX - mapX) / (COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            value = MathHelper.clamp(1.0F - (float) ((mouseY - mapY) / (COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            syncColor();
            return true;
        }
        if (mouseX >= hueX && mouseX < hueX + HUE_WIDTH && mouseY >= mapY && mouseY < mapY + COLOR_MAP_SIZE) {
            hue = MathHelper.clamp((float) ((mouseY - mapY) / (COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            uploadedHue = -1.0F;
            syncColor();
            return true;
        }
        return false;
    }

    private void syncColor() {
        currentGroup().color = 0xFF000000 | hsvToRgb(hue, saturation, value);
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
            colorMapTexture = new NativeImageBackedTexture(() -> "monvhua area tip color map", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(COLOR_MAP_TEXTURE_ID, colorMapTexture);
        } else {
            colorMapTexture.setImage(image);
            colorMapTexture.upload();
        }
        uploadedHue = hue;
    }

    private void refreshFields() {
        AreaTipConfig.GroupConfig group = currentGroup();
        updatingFields = true;
        nameField.setText(group.name);
        messageField.setText(group.message);
        updatingFields = false;
        shapeButton.setMessage(Text.literal("Shape: " + shapeName(group.shape)));
        halfButton.setMessage(Text.literal("Half: " + halfName(group.half)));
    }

    private void selectGroup(int index) {
        selectedIndex = Math.clamp(index, 0, config.groups.size() - 1);
        config.selectedGroupId = currentGroup().id;
        loadColor(currentGroup().color);
        uploadedHue = -1.0F;
        refreshFields();
    }

    private void newGroup() {
        AreaTipConfig.GroupConfig group = new AreaTipConfig.GroupConfig();
        group.id = UUID.randomUUID().toString();
        group.name = "Group " + (config.groups.size() + 1);
        group.color = 0xFF000000 | hsvToRgb(hue, saturation, value);
        config.groups.add(group);
        selectGroup(config.groups.size() - 1);
    }

    private void deleteGroup() {
        if (config.groups.size() <= 1) {
            return;
        }
        config.groups.remove(selectedIndex);
        selectGroup(Math.min(selectedIndex, config.groups.size() - 1));
        sendConfigUpdate();
    }

    private void sendConfigUpdate() {
        config.selectedGroupId = currentGroup().id;
        AreaTipClient.sendConfigUpdate(config);
    }

    private void cycleShape() {
        AreaTipConfig.GroupConfig group = currentGroup();
        group.shape = (group.shape + 1) % GravityAreaSpec.Shape.values().length;
        if (GravityAreaSpec.Shape.byId(group.shape) == GravityAreaSpec.Shape.CUBE) {
            int size = Math.max(group.sizeX, Math.max(group.sizeY, group.sizeZ));
            group.sizeX = size;
            group.sizeY = size;
            group.sizeZ = size;
        }
        refreshFields();
    }

    private void cycleHalf() {
        AreaTipConfig.GroupConfig group = currentGroup();
        group.half = (group.half + 1) % GravityAreaSpec.Half.values().length;
        refreshFields();
    }

    private int groupRowAt(double mouseX, double mouseY) {
        int listX = panelX + 8;
        int listY = panelY + 30;
        int rowWidth = leftWidth - 16;
        if (mouseX < listX || mouseX >= listX + rowWidth || mouseY < listY || mouseY >= panelY + panelHeight - 54) {
            return -1;
        }
        int row = (int) ((mouseY - listY) / 20);
        return row >= 0 && row < config.groups.size() ? row : -1;
    }

    private AreaTipConfig.GroupConfig currentGroup() {
        if (config.groups.isEmpty()) {
            config.groups.add(new AreaTipConfig.GroupConfig());
        }
        selectedIndex = Math.clamp(selectedIndex, 0, config.groups.size() - 1);
        return config.groups.get(selectedIndex);
    }

    private int selectedIndexFromConfig() {
        UUID selected = config.selectedGroup().map(AreaTipConfig.GroupConfig::uuid).orElse(null);
        if (selected == null) {
            return 0;
        }
        for (int i = 0; i < config.groups.size(); i++) {
            if (selected.equals(config.groups.get(i).uuid())) {
                return i;
            }
        }
        return 0;
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
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height) {
        context.fill(x - 1, y - 1, x + width + 1, y, 0xFFB8B8C2);
        context.fill(x - 1, y + height, x + width + 1, y + height + 1, 0xFF111116);
        context.fill(x - 1, y - 1, x, y + height + 1, 0xFFB8B8C2);
        context.fill(x + width, y - 1, x + width + 1, y + height + 1, 0xFF111116);
    }

    private static String shapeName(int shape) {
        return switch (GravityAreaSpec.Shape.byId(shape)) {
            case SPHERE -> "Sphere";
            case BOX -> "Box";
            case CUBE -> "Cube";
        };
    }

    private static String halfName(int half) {
        return switch (GravityAreaSpec.Half.byId(half)) {
            case FULL -> "Full";
            case UPPER -> "Upper";
            case LOWER -> "Lower";
        };
    }

    private static int clampSize(int value) {
        return Math.clamp(value, GravityAreaSpec.MIN_SIZE, GravityAreaSpec.MAX_SIZE);
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
                b = q;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }
}
