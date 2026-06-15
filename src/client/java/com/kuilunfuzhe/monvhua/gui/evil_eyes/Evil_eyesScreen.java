package com.kuilunfuzhe.monvhua.gui.evil_eyes;

import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.features.evil_eyes.ClairvoyanceViewportRenderer;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_EyesClient;
import com.kuilunfuzhe.monvhua.gui.layout.DraggableResizableLayout;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.SelectView;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.UnmarkEntityC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Evil_eyesScreen extends Screen {
    private static final int REFRESH_INTERVAL_TICKS = 40;
    private static final Identifier MAIN_BACKGROUND = Identifier.of("monvhua", "textures/gui/clairvoyance_gui/main_background.png");
    private static final Identifier VIEW_PART_BACKGROUND = Identifier.of("monvhua", "textures/gui/clairvoyance_gui/view_part.png");
    private static final int MAIN_TEXTURE_WIDTH = 2564;
    private static final int MAIN_TEXTURE_HEIGHT = 1441;
    private static final int VIEW_PART_TEXTURE_WIDTH = 2560;
    private static final int VIEW_PART_TEXTURE_HEIGHT = 334;
    private static final int VIEW_SLOT_TEXTURE_X = 570;
    private static final int VIEW_SLOT_TEXTURE_Y = 41;
    private static final int VIEW_SLOT_TEXTURE_WIDTH = 222;
    private static final int VIEW_SLOT_TEXTURE_HEIGHT = 160;
    private static final int VIEW_SLOT_TEXTURE_GAP = 14;
    private static final int TEXT_MAIN = 0xFFF5E6D3;
    private static final int TEXT_MUTED = 0xFF9CA3AF;
    private static final int DANGER = 0xFFB91C1C;
    private static final int ACCENT = 0xFFB8860B;
    private static final int ARCANE_BLUE = 0xFF3B82F6;
    private static final int AMETHYST = 0xFFA855F7;
    private static final int PALE_GOLD = 0xFFD4A373;
    private static final int CARD_BG = 0x881E1530;
    private static final int CARD_BG_HOVER = 0x66A855F7;
    private static final int CARD_BG_SELECTED = 0x883B82F6;
    private static final int ACCENT_SOFT = 0x88B8860B;
    private static final int CORNER = 7;
    private static final double CORNER_TAN = Math.tan(Math.toRadians(35.0D));

    private static final Map<UUID, Long> currentMarks = new ConcurrentHashMap<>();

    private final List<MarkedRow> markedRows = new ArrayList<>();
    private DraggableResizableLayout layout;
    private DraggableResizableLayout.Bounds mainBounds = DraggableResizableLayout.Bounds.EMPTY;
    private DraggableResizableLayout.Bounds listBounds = DraggableResizableLayout.Bounds.EMPTY;
    private DraggableResizableLayout.Bounds viewBounds = DraggableResizableLayout.Bounds.EMPTY;
    private DraggableResizableLayout.Bounds viewSlot0Bounds = DraggableResizableLayout.Bounds.EMPTY;
    private DraggableResizableLayout.Bounds viewSlot1Bounds = DraggableResizableLayout.Bounds.EMPTY;
    private long nextAutoRefreshTick;

    public Evil_eyesScreen() {
        super(Text.empty());
    }

    public static void updateMarkedList(Map<UUID, Long> marks) {
        currentMarks.clear();
        currentMarks.putAll(marks);
        refreshCurrentScreen();
    }

    public static void removeFromLocalList(UUID entityUuid) {
        currentMarks.remove(entityUuid);
        Evil_EyesClient.localMarkedEntityNames.remove(entityUuid);
        refreshCurrentScreen();
    }

    private static void refreshCurrentScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof Evil_eyesScreen screen) {
            screen.refreshEntityRows();
        }
    }

    @Override
    protected void init() {
        super.init();
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        int panelWidth = Math.min(sw - 24, Math.max(480, (int) (sw * 0.72F)));
        int panelHeight = Math.min(sh - 24, Math.max(240, panelWidth / 2));
        int panelX = (sw - panelWidth) / 2;
        int panelY = (sh - panelHeight) / 2;
        int leftWidth = Math.max(200, (int) (panelWidth * 0.38F));
        int rightWidth = panelWidth - leftWidth;

        int availablePreviewWidth = Math.max(220, rightWidth - 24);
        int availablePreviewHeight = Math.max(80, panelHeight - 64);
        float viewAspect = VIEW_PART_TEXTURE_WIDTH / (float) VIEW_PART_TEXTURE_HEIGHT;
        int viewWidth = Math.min(availablePreviewWidth, Math.round(availablePreviewHeight * viewAspect));
        int viewHeight = Math.min(availablePreviewHeight, Math.round(viewWidth / viewAspect));
        int viewX = panelX + leftWidth + Math.max(8, (rightWidth - viewWidth) / 2);
        int viewY = panelY + 34;

        layout = new DraggableResizableLayout("clairvoyance", sw, sh);
        mainBounds = layout.element("main_background", panelX, panelY, panelWidth, panelHeight, 240, 120);
        listBounds = layout.element("target_list", panelX + 14, panelY + 42, leftWidth - 28, panelHeight - 70, 120, 60);
        viewBounds = layout.element("view_part", viewX, viewY, viewWidth, viewHeight, 180, 32);
        int slot0X = defaultViewSlotX(viewX, viewWidth, 0);
        int slot1X = defaultViewSlotX(viewX, viewWidth, 1);
        int slotY = defaultViewSlotY(viewY, viewHeight);
        int slotWidth = defaultViewSlotWidth(viewWidth);
        int slotHeight = defaultViewSlotHeight(viewHeight);
        viewSlot0Bounds = layout.element("view_slot_0", slot0X, slotY, slotWidth, slotHeight, 48, 36);
        viewSlot1Bounds = layout.element("view_slot_1", slot1X, slotY, slotWidth, slotHeight, 48, 36);

        nextAutoRefreshTick = 0L;
        refreshEntityRows();
    }

    @Override
    public void tick() {
        super.tick();
        if (client == null || client.world == null) {
            return;
        }
        long now = client.world.getTime();
        if (now < nextAutoRefreshTick) {
            return;
        }
        nextAutoRefreshTick = now + REFRESH_INTERVAL_TICKS;
        if (Evil_EyesClient.pruneLocalMarkedEntities(client)) {
            updateMarkedList(Evil_EyesClient.localMarkedEntities);
        } else {
            refreshEntityRows();
        }
    }

    private void syncLayoutBounds() {
        if (layout == null) {
            return;
        }
        mainBounds = layout.bounds("main_background");
        listBounds = layout.bounds("target_list");
        viewBounds = layout.bounds("view_part");
        viewSlot0Bounds = layout.bounds("view_slot_0");
        viewSlot1Bounds = layout.bounds("view_slot_1");
    }

    private static int defaultViewSlotX(int boardX, int boardWidth, int slot) {
        int slotTextureX = VIEW_SLOT_TEXTURE_X + slot * (VIEW_SLOT_TEXTURE_WIDTH + VIEW_SLOT_TEXTURE_GAP);
        return boardX + Math.round(boardWidth * (slotTextureX / (float) VIEW_PART_TEXTURE_WIDTH));
    }

    private static int defaultViewSlotY(int boardY, int boardHeight) {
        return boardY + Math.round(boardHeight * (VIEW_SLOT_TEXTURE_Y / (float) VIEW_PART_TEXTURE_HEIGHT));
    }

    private static int defaultViewSlotWidth(int boardWidth) {
        return Math.round(boardWidth * (VIEW_SLOT_TEXTURE_WIDTH / (float) VIEW_PART_TEXTURE_WIDTH));
    }

    private static int defaultViewSlotHeight(int boardHeight) {
        return Math.round(boardHeight * (VIEW_SLOT_TEXTURE_HEIGHT / (float) VIEW_PART_TEXTURE_HEIGHT));
    }

    private void refreshEntityRows() {
        syncLayoutBounds();
        markedRows.clear();
        if (Evil_EyesClient.isViewportMode()) {
            ClairvoyanceViewportRenderer.syncPreviewTargets(currentMarks.keySet());
        }

        int rowHeight = 24;
        int rowGap = 5;
        int rowWidth = listBounds.width;
        int yOffset = 20;
        for (UUID uuid : currentMarks.keySet()) {
            String name = getEntityName(uuid);
            markedRows.add(new MarkedRow(uuid, name, listBounds.x, listBounds.y + yOffset, rowWidth, rowHeight));
            yOffset += rowHeight + rowGap;
            if (yOffset + rowHeight > listBounds.height) {
                break;
            }
        }
    }

    private String getEntityName(UUID uuid) {
        String cachedName = Evil_EyesClient.localMarkedEntityNames.get(uuid);
        if (cachedName != null && !cachedName.isBlank()) {
            return tag_pitch.replaceTags(cachedName);
        }
        if (client != null && client.world != null) {
            var entity = client.world.getEntity(uuid);
            if (entity != null) {
                return tag_pitch.entityDisplayName(entity);
            }
        }
        return "Unknown";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        syncLayoutBounds();
        drawRotatedScaledTexture(context, MAIN_BACKGROUND, mainBounds, MAIN_TEXTURE_WIDTH, MAIN_TEXTURE_HEIGHT);
        drawHeader(context);
        drawMarkedRows(context, mouseX, mouseY);

        if (Evil_EyesClient.isViewportMode()) {
            drawRotatedScaledTexture(context, VIEW_PART_BACKGROUND, viewBounds, VIEW_PART_TEXTURE_WIDTH, VIEW_PART_TEXTURE_HEIGHT);
            renderPreviewSlot(context, 0, viewSlot0Bounds);
            renderPreviewSlot(context, 1, viewSlot1Bounds);
        } else {
            drawHintPanel(context);
        }
        if (layout != null) {
            layout.drawEditHandles(context);
        }
    }

    private void drawHeader(DrawContext context) {
        String count = "Clairvoyance  " + currentMarks.size() + " marks";
        context.drawTextWithShadow(textRenderer, count, mainBounds.x + 16, mainBounds.y + 14, PALE_GOLD);
    }

    private void drawMarkedRows(DrawContext context, int mouseX, int mouseY) {
        if (markedRows.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No marked targets", listBounds.x + 6, listBounds.y + 22, TEXT_MUTED);
            context.drawTextWithShadow(textRenderer, "Marked targets will appear here", listBounds.x + 6, listBounds.y + 36, TEXT_MUTED);
            return;
        }

        UUID selected = ClairvoyanceViewportRenderer.getSelectedTarget();
        for (MarkedRow row : markedRows) {
            boolean hover = row.contains(mouseX, mouseY);
            boolean selectedRow = row.uuid.equals(selected);
            int fill = selectedRow ? CARD_BG_SELECTED : (hover ? CARD_BG_HOVER : CARD_BG);
            int border = selectedRow ? ARCANE_BLUE : (hover ? AMETHYST : ACCENT_SOFT);
            drawRoundedPanel(context, row.x, row.y, row.width, row.height, 6, fill, border);

            context.fill(row.x + 8, row.y + 9, row.x + 12, row.y + 13, selectedRow ? ARCANE_BLUE : ACCENT);
            String label = trimToWidth(row.name, row.width - 52);
            context.drawTextWithShadow(textRenderer, label, row.x + 18, row.y + 7, selectedRow ? TEXT_MAIN : 0xFFE8D7C2);

            boolean deleteHover = row.containsDelete(mouseX, mouseY);
            drawRoundedPanel(context, row.deleteX(), row.y + 4, 16, 16, 5, deleteHover ? 0x66B91C1C : 0x334A3B2C, deleteHover ? DANGER : ACCENT);
            context.drawCenteredTextWithShadow(textRenderer, "x", row.deleteX() + 8, row.y + 8, deleteHover ? DANGER : TEXT_MUTED);
        }
    }

    private void renderPreviewSlot(DrawContext context, int slot, DraggableResizableLayout.Bounds bounds) {
        withRotation(context, bounds, () -> ClairvoyanceViewportRenderer.renderPreviewRect(context, slot, bounds.x, bounds.y, bounds.width, bounds.height));
    }

    private void drawScaledTexture(DrawContext context, Identifier texture, int x, int y, int width, int height, int textureWidth, int textureHeight) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    private void drawRotatedScaledTexture(DrawContext context, Identifier texture, DraggableResizableLayout.Bounds bounds, int textureWidth, int textureHeight) {
        withRotation(context, bounds, () -> drawScaledTexture(context, texture, bounds.x, bounds.y, bounds.width, bounds.height, textureWidth, textureHeight));
    }

    private void withRotation(DrawContext context, DraggableResizableLayout.Bounds bounds, Runnable renderAction) {
        if (Math.abs(bounds.rotationDegrees) < 0.001F) {
            renderAction.run();
            return;
        }
        Matrix3x2fStack matrices = context.getMatrices();
        float centerX = bounds.x + bounds.width * 0.5F;
        float centerY = bounds.y + bounds.height * 0.5F;
        matrices.pushMatrix();
        try {
            matrices.translate(centerX, centerY);
            matrices.rotate((float) Math.toRadians(bounds.rotationDegrees));
            matrices.translate(-centerX, -centerY);
            renderAction.run();
        } finally {
            matrices.popMatrix();
        }
    }

    private void drawHintPanel(DrawContext context) {
        int h = Math.min(72, viewBounds.height);
        drawRoundedPanel(context, viewBounds.x, viewBounds.y, viewBounds.width, h, 7, 0x661E1530, ACCENT_SOFT);
        context.drawTextWithShadow(textRenderer, "Click a marked target", viewBounds.x + 14, viewBounds.y + 14, TEXT_MAIN);
        context.drawTextWithShadow(textRenderer, "Switch to that target's view.", viewBounds.x + 14, viewBounds.y + 32, TEXT_MUTED);
    }

    private void drawRoundedPanel(DrawContext context, int x, int y, int width, int height, int radius, int fill, int border) {
        if (width <= 0 || height <= 0) {
            return;
        }
        radius = MathHelper.clamp(radius, 0, Math.min(width, height) / 2);
        fillRounded(context, x, y, width, height, radius, border);
        fillRounded(context, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fill);
    }

    private void fillRounded(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (radius <= 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        int cornerHeight = Math.max(1, (int) Math.round(radius * CORNER_TAN));
        cornerHeight = Math.min(cornerHeight, height / 2);
        int cornerWidth = Math.min(radius, width / 2);
        context.fill(x + cornerWidth, y, x + width - cornerWidth, y + height, color);
        context.fill(x, y + cornerHeight, x + width, y + height - cornerHeight, color);
        for (int i = 0; i < cornerHeight; i++) {
            int inset = MathHelper.clamp((int) Math.ceil(cornerWidth - (i / CORNER_TAN)), 0, cornerWidth);
            int topY = y + i;
            int bottomY = y + height - i - 1;
            context.fill(x + inset, topY, x + width - inset, topY + 1, color);
            context.fill(x + inset, bottomY, x + width - inset, bottomY + 1, color);
        }
    }

    private String trimToWidth(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int target = Math.max(0, maxWidth - textRenderer.getWidth(ellipsis));
        return textRenderer.trimToWidth(text, target) + ellipsis;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (layout != null && layout.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            return clickMarkedRow(mouseX, mouseY);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (layout != null && layout.mouseDragged(mouseX, mouseY, button)) {
            syncLayoutBounds();
            refreshEntityRows();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (layout != null && layout.isEditing()) {
            DraggableResizableLayout.DragResult result = layout.mouseReleased(mouseX, mouseY, button);
            syncLayoutBounds();
            refreshEntityRows();
            return result.moved() || clickMarkedRow(mouseX, mouseY);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean clickMarkedRow(double mouseX, double mouseY) {
        for (MarkedRow row : markedRows) {
            if (!row.contains(mouseX, mouseY)) {
                continue;
            }
            if (row.containsDelete(mouseX, mouseY)) {
                ClairvoyanceViewportRenderer.clearSelectedTarget(row.uuid);
                removeFromLocalList(row.uuid);
                ClientPlayNetworking.send(new UnmarkEntityC2S(row.uuid));
            } else {
                selectRow(row);
            }
            return true;
        }
        return false;
    }

    private void selectRow(MarkedRow row) {
        if (Evil_EyesClient.isViewportMode()) {
            ClairvoyanceViewportRenderer.setSelectedTarget(row.uuid);
            ClairvoyanceViewportRenderer.syncPreviewTargets(currentMarks.keySet());
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("\u00a7aPreview: " + row.name), true);
            }
            return;
        }
        ClientPlayNetworking.send(new SelectView(row.uuid));
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("\u00a7aViewing " + row.name), true);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        ClairvoyanceViewportRenderer.cleanup();
    }

    private record MarkedRow(UUID uuid, String name, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        boolean containsDelete(double mouseX, double mouseY) {
            int dx = deleteX();
            return mouseX >= dx && mouseX < dx + 16 && mouseY >= y + 4 && mouseY < y + 20;
        }

        int deleteX() {
            return x + width - 21;
        }
    }
}
