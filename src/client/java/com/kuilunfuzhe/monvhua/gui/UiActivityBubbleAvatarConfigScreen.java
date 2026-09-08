package com.kuilunfuzhe.monvhua.gui;

import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleAvatarCatalog;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleAvatarLayout;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityBubbleStyle;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityClient;
import com.kuilunfuzhe.monvhua.renderer.activity.UiActivityBubbleRenderer;
import com.kuilunfuzhe.monvhua.renderer.activity.UiActivityBubblePreviewTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/** Three-column editor for an avatar that may extend beyond the bubble. */
public final class UiActivityBubbleAvatarConfigScreen extends Screen {
    private static final int AVATAR_PAGE_SIZE = 6;
    // The SDF bubble includes a tail and transparent margins. These are the
    // measured visual-center offsets of the complete visible silhouette.
    private static final float BUBBLE_VISUAL_CENTER_X = 0.535F;
    private static final float BUBBLE_VISUAL_CENTER_Y = 0.516F;
    private final Screen parent;
    private final List<UiActivityBubbleAvatarCatalog.Definition> avatarDefinitions =
            UiActivityBubbleAvatarCatalog.definitions();
    private int selected = avatarDefinitions.isEmpty()
            ? UiActivityBubbleAvatarCatalog.NONE : avatarDefinitions.get(0).id();
    private int avatarPage;
    private final List<ButtonWidget> avatarButtons = new ArrayList<>();
    private ButtonWidget previousAvatarPage;
    private ButtonWidget nextAvatarPage;
    private int leftX, leftWidth, centerX, centerWidth, rightX, rightWidth;
    private int previewLeft, previewTop, previewWidth, previewHeight;
    private int bubbleLeft, bubbleTop, bubbleWidth, bubbleHeight;
    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget scaleField;
    private ButtonWidget styleButton;
    private boolean updating;
    private boolean layoutRequested;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    private UiActivityBubbleAvatarConfigScreen(Screen parent) {
        super(Text.literal("头像气泡配置"));
        this.parent = parent;
    }

    public static void open(Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(new UiActivityBubbleAvatarConfigScreen(parent)));
        }
    }

    @Override
    protected void init() {
        computeColumns();
        for (UiActivityBubbleAvatarCatalog.Definition definition : avatarDefinitions) {
            UiActivityBubbleRenderer.prepareAvatarTexture(
                    MinecraftClient.getInstance(), definition.texture());
        }
        addAvatarButtons();
        int controlX = rightX + 12;
        xField = addDrawableChild(field(controlX, 72, "X"));
        yField = addDrawableChild(field(controlX, 112, "Y"));
        scaleField = addDrawableChild(field(controlX, 152, "Scale"));
        xField.setChangedListener(value -> updateDraftFromFields());
        yField.setChangedListener(value -> updateDraftFromFields());
        scaleField.setChangedListener(value -> updateDraftFromFields());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> resetSelected())
                .dimensions(controlX, 192, rightWidth - 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), button -> commitFields())
                .dimensions(controlX, 218, (rightWidth - 30) / 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(controlX + (rightWidth - 30) / 2 + 6, 218,
                        (rightWidth - 30) / 2, 20).build());
        styleButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> toggleStyle())
                .dimensions(controlX, 258, rightWidth - 24, 20).build());
        refreshFields();
        updateStyleButton();
    }

    private void computeColumns() {
        int usable = Math.max(480, width - 24);
        leftWidth = Math.max(150, Math.round(usable * 0.20F));
        rightWidth = Math.max(180, Math.round(usable * 0.20F));
        centerWidth = Math.max(180, usable - leftWidth - rightWidth);
        leftX = 8;
        centerX = leftX + leftWidth;
        rightX = centerX + centerWidth;
        // All preview geometry is derived from this viewport. Texture pixel
        // dimensions are only a quality choice and never participate here.
        previewLeft = centerX + 12;
        previewTop = 34;
        previewWidth = Math.max(160, centerWidth - 24);
        previewHeight = Math.max(150, height - previewTop - 18);
        float aspect = UiActivityBubbleRenderer.style() == UiActivityBubbleStyle.PIXEL
                ? 28.0F / 18.0F : (0.92F * 4.0F / 3.0F) / (0.62F * 4.0F / 3.0F);
        bubbleWidth = Math.max(120, Math.round(Math.min(
                previewWidth * 0.72F,
                previewHeight * 0.48F * aspect)));
        bubbleWidth = Math.min(bubbleWidth, previewWidth);
        bubbleHeight = Math.max(80, Math.round(bubbleWidth / aspect));
        bubbleHeight = Math.min(bubbleHeight, previewHeight);
        bubbleWidth = Math.min(bubbleWidth, Math.round(bubbleHeight * aspect));
        bubbleLeft = previewLeft + Math.round((previewWidth - bubbleWidth) / 2.0F
                - (BUBBLE_VISUAL_CENTER_X - 0.5F) * bubbleWidth);
        bubbleTop = previewTop + Math.round((previewHeight - bubbleHeight) / 2.0F
                - (BUBBLE_VISUAL_CENTER_Y - 0.5F) * bubbleHeight);
    }

    private void addAvatarButtons() {
        int buttonWidth = Math.max(100, leftWidth - 24);
        for (int i = 0; i < AVATAR_PAGE_SIZE; i++) {
            final int index = i;
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.empty(), ignored -> {
                int definitionIndex = avatarPage * AVATAR_PAGE_SIZE + index;
                if (definitionIndex < avatarDefinitions.size()) {
                    select(avatarDefinitions.get(definitionIndex).id());
                }
            }).dimensions(leftX + 12, 54 + i * 28, buttonWidth, 22).build());
            avatarButtons.add(button);
        }
        previousAvatarPage = addDrawableChild(ButtonWidget.builder(Text.literal("<"), ignored -> {
            avatarPage = Math.max(0, avatarPage - 1);
            refreshAvatarButtons();
        }).dimensions(leftX + 12, 54 + AVATAR_PAGE_SIZE * 28 + 4, (buttonWidth - 6) / 2, 20).build());
        nextAvatarPage = addDrawableChild(ButtonWidget.builder(Text.literal(">"), ignored -> {
            avatarPage = Math.min(maxAvatarPage(), avatarPage + 1);
            refreshAvatarButtons();
        }).dimensions(leftX + 18 + (buttonWidth - 6) / 2, 54 + AVATAR_PAGE_SIZE * 28 + 4,
                (buttonWidth - 6) / 2, 20).build());
        refreshAvatarButtons();
    }

    private int maxAvatarPage() {
        return Math.max(0, (avatarDefinitions.size() - 1) / AVATAR_PAGE_SIZE);
    }

    private void refreshAvatarButtons() {
        for (int i = 0; i < avatarButtons.size(); i++) {
            int definitionIndex = avatarPage * AVATAR_PAGE_SIZE + i;
            ButtonWidget button = avatarButtons.get(i);
            if (definitionIndex < avatarDefinitions.size()) {
                UiActivityBubbleAvatarCatalog.Definition definition = avatarDefinitions.get(definitionIndex);
                button.visible = true;
                button.active = definition.id() != selected;
                button.setMessage(Text.literal(definition.key()));
            } else {
                button.visible = false;
                button.active = false;
                button.setMessage(Text.empty());
            }
        }
        if (previousAvatarPage != null) {
            previousAvatarPage.active = avatarPage > 0;
            nextAvatarPage.active = avatarPage < maxAvatarPage();
        }
    }

    private TextFieldWidget field(int x, int y, String hint) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, Math.max(100, rightWidth - 24), 20,
                Text.literal(hint));
        field.setMaxLength(16);
        return field;
    }

    @Override
    public void tick() {
        super.tick();
        if (!layoutRequested) {
            layoutRequested = true;
            UiActivityClient.requestAvatarLayouts();
        }
    }

    private void select(int id) {
        commitFields();
        selected = id;
        refreshAvatarButtons();
        refreshFields();
    }

    private void resetSelected() {
        UiActivityBubbleAvatarLayout layout = UiActivityBubbleAvatarLayout.defaults(selected);
        setDraft(layout);
        commitFields();
    }

    private void refreshFields() {
        if (xField == null) return;
        setDraft(UiActivityClient.avatarLayout(selected));
    }

    private void setDraft(UiActivityBubbleAvatarLayout layout) {
        updating = true;
        xField.setText(String.format(java.util.Locale.ROOT, "%.4f", layout.centerX()));
        yField.setText(String.format(java.util.Locale.ROOT, "%.4f", layout.centerY()));
        scaleField.setText(String.format(java.util.Locale.ROOT, "%.4f", layout.scale()));
        updating = false;
    }

    private void updateDraftFromFields() {
        if (updating || xField == null) return;
        try {
            UiActivityBubbleAvatarLayout layout = UiActivityBubbleAvatarLayout.sanitize(
                    Float.parseFloat(xField.getText()), Float.parseFloat(yField.getText()),
                    Float.parseFloat(scaleField.getText()), selected);
            // Do not rewrite the fields from the change listener. TextFieldWidget
            // invokes it while editing; setText here would reset the caret and
            // selection, making Backspace/Delete/Ctrl+A unusable.
            UiActivityClient.previewAvatarLayout(selected, layout);
        } catch (NumberFormatException ignored) {
            // Empty and partially typed values are valid transient editor states.
        }
    }

    private void commitFields() {
        if (xField == null) return;
        try {
            UiActivityBubbleAvatarLayout layout = UiActivityBubbleAvatarLayout.sanitize(
                    Float.parseFloat(xField.getText()), Float.parseFloat(yField.getText()),
                    Float.parseFloat(scaleField.getText()), selected);
            UiActivityClient.updateAvatarLayout(selected, layout.centerX(), layout.centerY(), layout.scale());
            setDraft(layout);
        } catch (NumberFormatException ignored) {
            refreshFields();
        }
    }

    private void toggleStyle() {
        UiActivityBubbleStyle next = UiActivityBubbleRenderer.style() == UiActivityBubbleStyle.PIXEL
                ? UiActivityBubbleStyle.DEFAULT : UiActivityBubbleStyle.PIXEL;
        UiActivityClient.updateBubbleStyle(next);
        computeColumns();
        updateStyleButton();
    }

    private void updateStyleButton() {
        if (styleButton != null) {
            String label = UiActivityBubbleRenderer.style() == UiActivityBubbleStyle.PIXEL
                    ? "Bubble: Pixel" : "Bubble: Smooth";
            styleButton.setMessage(Text.literal(label));
        }
    }

    private float avatarAspect() {
        UiActivityBubbleAvatarCatalog.Definition definition =
                UiActivityBubbleAvatarCatalog.definition(UiActivityBubbleAvatarCatalog.key(selected));
        return definition == null || !Float.isFinite(definition.aspect()) || definition.aspect() <= 0.0F
                ? 1.0F : definition.aspect();
    }

    /** Matches the world renderer: avatar height is bubble height * layout scale. */
    private int avatarHeight(UiActivityBubbleAvatarLayout layout) {
        float logicalHeight = bubbleHeight * layout.scale();
        return Math.max(8, Math.round(logicalHeight));
    }

    private int[] avatarRect(UiActivityBubbleAvatarLayout layout) {
        int h = avatarHeight(layout);
        int w = Math.max(8, Math.round(h * avatarAspect()));
        int cx = Math.round(bubbleLeft + layout.centerX() * bubbleWidth);
        int cy = Math.round(bubbleTop + layout.centerY() * bubbleHeight);
        return new int[]{cx - w / 2, cy - h / 2, w, h};
    }

    /** Keeps the 1.21.8 GUI rectangle and UV conventions in one place. */
    private void drawPreviewTexture(DrawContext context, Identifier texture,
                                    int x, int y, int width, int height, boolean mirrorX) {
        context.drawTexturedQuad(texture, x, y, x + width, y + height,
                mirrorX ? 1.0F : 0.0F,
                mirrorX ? 0.0F : 1.0F,
                0.0F, 1.0F);
    }

    private int[] avatarInteractionRect(UiActivityBubbleAvatarLayout layout) {
        int[] visual = avatarRect(layout);
        int padding = 7;
        int width = Math.max(visual[2] + padding * 2, 22);
        int height = Math.max(visual[3] + padding * 2, 22);
        int centerX = visual[0] + visual[2] / 2;
        int centerY = visual[1] + visual[3] / 2;
        return new int[]{centerX - width / 2, centerY - height / 2, width, height};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int[] rect = avatarInteractionRect(UiActivityClient.avatarLayout(selected));
            if (mouseX >= rect[0] && mouseX <= rect[0] + rect[2]
                    && mouseY >= rect[1] && mouseY <= rect[1] + rect[3]) {
                int[] visual = avatarRect(UiActivityClient.avatarLayout(selected));
                dragging = true;
                dragOffsetX = mouseX - (visual[0] + visual[2] / 2.0);
                dragOffsetY = mouseY - (visual[1] + visual[3] / 2.0);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging || (button != 0 && button != -1)) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        float x = (float) ((mouseX - dragOffsetX - bubbleLeft) / bubbleWidth);
        float y = (float) ((mouseY - dragOffsetY - bubbleTop) / bubbleHeight);
        UiActivityBubbleAvatarLayout layout = UiActivityBubbleAvatarLayout.sanitize(
                MathHelper.clamp(x, UiActivityBubbleAvatarLayout.MIN_POSITION,
                        UiActivityBubbleAvatarLayout.MAX_POSITION),
                MathHelper.clamp(y, UiActivityBubbleAvatarLayout.MIN_POSITION,
                        UiActivityBubbleAvatarLayout.MAX_POSITION),
                UiActivityClient.avatarLayout(selected).scale(), selected);
        setDraft(layout);
        UiActivityClient.previewAvatarLayout(selected, layout);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            commitFields();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= bubbleLeft - 80 && mouseX <= bubbleLeft + bubbleWidth + 80
                && mouseY >= bubbleTop - 80 && mouseY <= bubbleTop + bubbleHeight + 80) {
            UiActivityBubbleAvatarLayout current = UiActivityClient.avatarLayout(selected);
            float scale = MathHelper.clamp(current.scale() + (float) vertical * 0.02F,
                    UiActivityBubbleAvatarLayout.MIN_SCALE, UiActivityBubbleAvatarLayout.MAX_SCALE);
            UiActivityBubbleAvatarLayout next = UiActivityBubbleAvatarLayout.sanitize(
                    current.centerX(), current.centerY(), scale, selected);
            setDraft(next);
            UiActivityClient.previewAvatarLayout(selected, next);
            commitFields();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void close() {
        commitFields();
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(parent));
    }

    @Override
    public void removed() {
        commitFields();
        UiActivityBubblePreviewTexture.clear(MinecraftClient.getInstance());
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        computeColumns();
        context.fill(0, 0, width, height, 0xB0101218);
        context.fill(leftX, 8, centerX, height - 8, 0xE8161B22);
        context.fill(centerX, 8, rightX, height - 8, 0xE810141A);
        context.fill(rightX, 8, width - 8, height - 8, 0xE8161B22);
        context.fill(previewLeft - 1, previewTop - 1,
                previewLeft + previewWidth + 1, previewTop + previewHeight + 1, 0xFF26313C);
        context.fill(previewLeft, previewTop,
                previewLeft + previewWidth, previewTop + previewHeight, 0xFF10151B);
        context.drawText(textRenderer, title, leftX + 12, 20, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.literal("Avatar"), leftX + 12, 36, 0xFFB5C4D6, false);
        context.drawText(textRenderer, Text.literal("Drag avatar outside the bubble"), centerX + 12, 20,
                0xFFB5C4D6, false);
        context.drawText(textRenderer, Text.literal("Layout"), rightX + 12, 38, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.literal("X"), rightX + 12, 62, 0xFFB5C4D6, false);
        context.drawText(textRenderer, Text.literal("Y"), rightX + 12, 102, 0xFFB5C4D6, false);
        context.drawText(textRenderer, Text.literal("Scale"), rightX + 12, 142, 0xFFB5C4D6, false);

        Identifier bubblePreview = UiActivityBubblePreviewTexture.texture(
                UiActivityBubbleRenderer.style(), MinecraftClient.getInstance());
        if (bubblePreview != null) {
            drawPreviewTexture(context, bubblePreview, bubbleLeft, bubbleTop,
                    bubbleWidth, bubbleHeight, false);
        }

        UiActivityBubbleAvatarLayout layout = UiActivityClient.avatarLayout(selected);
        int[] rect = avatarRect(layout);
        Identifier texture = UiActivityBubbleAvatarCatalog.textureId(selected);
        if (texture != null) {
            drawPreviewTexture(context, texture, rect[0], rect[1], rect[2], rect[3], false);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
