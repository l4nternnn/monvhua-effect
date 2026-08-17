package com.kuilunfuzhe.monvhua.features.activity.emotion;

import com.kuilunfuzhe.monvhua.features.activity.EmotionCatalog;
import com.kuilunfuzhe.monvhua.features.activity.UiActivityClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

final class EmotionPickerOverlay {
    private static final int BUTTON_SIZE = 18;
    private static final int PANEL_GAP = 4;
    private static final int PANEL_MAX_WIDTH = 220;
    private static final int PANEL_MAX_HEIGHT = 240;
    private static final int PANEL_MIN_WIDTH = 96;
    private static final int PANEL_PADDING = 6;
    private static final int CELL_SIZE = 54;
    private static final int CELL_GAP = 5;
    private static final int CONFIRM_HEIGHT = 25;
    private static final long DOUBLE_CLICK_MILLIS = 350L;
    private static final long HOVER_ANIMATION_DELAY_MILLIS = 120L;

    private final ChatScreen screen;
    private final List<Integer> contentIds = new ArrayList<>();
    private boolean open;
    private double scroll;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private int candidateId;
    private int pendingConfirmId = -1;
    private int hoveredId = -1;
    private long hoverStartedAt;
    private int lastClickId = -1;
    private long lastClickAt;

    EmotionPickerOverlay(ChatScreen screen) {
        this.screen = screen;
        contentIds.add(0);
        for (EmotionCatalog.Entry entry : EmotionCatalog.entries()) {
            contentIds.add(entry.id());
        }
        candidateId = UiActivityClient.selectedContentId();
    }

    void render(DrawContext context, int mouseX, int mouseY) {
        Layout layout = layout();
        renderButton(context, layout.button(), mouseX, mouseY);
        if (!open) {
            renderButtonTooltip(context, layout.button(), mouseX, mouseY);
            return;
        }

        context.fill(layout.panel().x(), layout.panel().y(), layout.panel().right(), layout.panel().bottom(), 0xE812151A);
        drawBorder(context, layout.panel(), 0xFF6D747E);

        int currentHover = contentAt(layout, mouseX, mouseY);
        long now = Util.getMeasuringTimeMs();
        if (currentHover != hoveredId) {
            hoveredId = currentHover;
            hoverStartedAt = now;
        }

        Rect viewport = layout.viewport();
        context.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        for (int index = 0; index < contentIds.size(); index++) {
            int contentId = contentIds.get(index);
            Rect cell = cellAt(layout, index);
            if (cell.bottom() < viewport.y() || cell.y() > viewport.bottom()) {
                continue;
            }
            boolean selected = contentId == UiActivityClient.selectedContentId();
            boolean candidate = contentId == candidateId;
            boolean hovered = contentId == hoveredId;
            int background = selected ? 0xFF354F45 : hovered ? 0xFF343A43 : 0xFF242930;
            context.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), background);
            drawBorder(context, cell, candidate ? 0xFFE5C46A : 0xFF4E555F);
        }
        context.createNewRootLayer();
        for (int index = 0; index < contentIds.size(); index++) {
            int contentId = contentIds.get(index);
            Rect cell = cellAt(layout, index);
            if (cell.bottom() < viewport.y() || cell.y() > viewport.bottom()) {
                continue;
            }
            boolean hovered = contentId == hoveredId;
            renderCellContent(context, cell, contentId,
                    hovered && now - hoverStartedAt >= HOVER_ANIMATION_DELAY_MILLIS);
        }
        context.disableScissor();

        renderScrollbar(context, layout);
        if (pendingConfirmId >= 0) {
            renderConfirmation(context, layout.confirmation(), mouseX, mouseY);
        }
        renderButtonTooltip(context, layout.button(), mouseX, mouseY);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout layout = layout();
        if (layout.button().contains(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                open = !open;
                pendingConfirmId = -1;
            }
            return true;
        }
        if (!open || !layout.panel().contains(mouseX, mouseY)) {
            return false;
        }

        if (pendingConfirmId >= 0 && layout.confirmation().contains(mouseX, mouseY)) {
            Rect confirmation = layout.confirmation();
            if (mouseX < confirmation.x() + confirmation.width() / 2.0D) {
                confirm(pendingConfirmId);
            } else {
                pendingConfirmId = -1;
            }
            return true;
        }

        Rect thumb = scrollbarThumb(layout);
        if (thumb != null && layout.scrollbar().contains(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingScrollbar = true;
            if (thumb.contains(mouseX, mouseY)) {
                scrollbarGrabOffset = mouseY - thumb.y();
            } else {
                scrollbarGrabOffset = thumb.height() / 2.0D;
                updateScrollFromScrollbar(layout, mouseY);
            }
            return true;
        }

        int contentId = contentAt(layout, mouseX, mouseY);
        if (contentId < 0) {
            return true;
        }
        candidateId = contentId;
        EmotionCatalog.Entry entry = EmotionCatalog.byId(contentId);
        if (contentId > 0 && entry != null && entry.type() != EmotionCatalog.Type.PROCEDURAL
                && entry.type() != EmotionCatalog.Type.BLOCK_DISPLAY) {
            EmotionTextureManager.textureFor(contentId, true, Util.getMeasuringTimeMs());
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            pendingConfirmId = contentId;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pendingConfirmId = -1;
            long now = Util.getMeasuringTimeMs();
            if (lastClickId == contentId && now - lastClickAt <= DOUBLE_CLICK_MILLIS) {
                confirm(contentId);
                lastClickId = -1;
            } else {
                lastClickId = contentId;
                lastClickAt = now;
            }
            return true;
        }
        return true;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!draggingScrollbar || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        updateScrollFromScrollbar(layout(), mouseY);
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        Layout layout = layout();
        if (!open || !layout.panel().contains(mouseX, mouseY)) {
            return false;
        }
        scroll = Math.clamp(scroll - amount * 28.0D, 0.0D, layout.maxScroll());
        return true;
    }

    private void confirm(int contentId) {
        UiActivityClient.selectContent(contentId);
        candidateId = contentId;
        pendingConfirmId = -1;
    }

    private void renderButton(DrawContext context, Rect button, int mouseX, int mouseY) {
        boolean hovered = button.contains(mouseX, mouseY);
        context.fill(button.x(), button.y(), button.right(), button.bottom(),
                open ? 0xE04A535E : hovered ? 0xD83B424C : 0xC82A3038);
        drawBorder(context, button, hovered || open ? 0xFFE2C66F : 0xFF69717C);
        int textX = button.x() + (button.width() - screen.getTextRenderer().getWidth(":)")) / 2;
        context.drawTextWithShadow(screen.getTextRenderer(), ":)", textX, button.y() + 5, 0xFFF4F4F4);
    }

    private void renderButtonTooltip(DrawContext context, Rect button, int mouseX, int mouseY) {
        if (button.contains(mouseX, mouseY)) {
            context.drawTooltip(screen.getTextRenderer(), Text.translatable("gui.monvhua.emotion_picker"), mouseX, mouseY);
        }
    }

    private void renderCellContent(DrawContext context, Rect cell, int contentId, boolean animate) {
        int inset = 4;
        if (contentId == 0) {
            int centerY = cell.y() + cell.height() / 2;
            int centerX = cell.x() + cell.width() / 2;
            for (int offset : new int[]{-12, 0, 12}) {
                context.fill(centerX + offset - 2, centerY - 2, centerX + offset + 3, centerY + 3, 0xFF111111);
            }
            return;
        }
        EmotionCatalog.Entry entry = EmotionCatalog.byId(contentId);
        if (entry != null && (entry.type() == EmotionCatalog.Type.PROCEDURAL
                || entry.type() == EmotionCatalog.Type.BLOCK_DISPLAY)) {
            int previewX = cell.x() + inset;
            int previewY = cell.y() + inset;
            int previewWidth = cell.width() - inset * 2;
            int previewHeight = cell.height() - inset * 2;
            if (entry.type() == EmotionCatalog.Type.PROCEDURAL && entry.resourceId() != null) {
                int sourceWidth = 1792;
                int sourceHeight = 1450;
                double scale = Math.min((double) previewWidth / sourceWidth, (double) previewHeight / sourceHeight);
                int drawWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
                int drawHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
                previewX += (previewWidth - drawWidth) / 2;
                previewY += (previewHeight - drawHeight) / 2;
                previewWidth = drawWidth;
                previewHeight = drawHeight;
                context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        entry.resourceId(),
                        previewX,
                        previewY,
                        128,
                        280,
                        previewWidth,
                        previewHeight,
                        sourceWidth,
                        sourceHeight,
                        2048,
                        2048
                );
            }
            EmotionProceduralPreviewRenderer.render(context, contentId, previewX, previewY,
                    previewWidth, previewHeight, Util.getMeasuringTimeMs(), animate);
            return;
        }
        Identifier texture = EmotionTextureManager.textureForPreview(contentId, animate, Util.getMeasuringTimeMs());
        if (texture == null) {
            context.drawTextWithShadow(screen.getTextRenderer(), "...", cell.x() + 18, cell.y() + 22, 0xFFB8BEC7);
            return;
        }
        EmotionTextureManager.PreviewRegion region = EmotionTextureManager.previewRegion(contentId);
        int availableWidth = cell.width() - inset * 2;
        int availableHeight = cell.height() - inset * 2;
        int drawWidth = availableWidth;
        int drawHeight = availableHeight;
        int drawX = cell.x() + inset;
        int drawY = cell.y() + inset;
        if (region != null) {
            double scale = Math.min(
                    (double) availableWidth / region.width(),
                    (double) availableHeight / region.height()
            );
            drawWidth = Math.max(1, (int) Math.round(region.width() * scale));
            drawHeight = Math.max(1, (int) Math.round(region.height() * scale));
            drawX = cell.x() + (cell.width() - drawWidth) / 2;
            drawY = cell.y() + (cell.height() - drawHeight) / 2;
        }
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                drawX,
                drawY,
                region == null ? 0 : region.x(),
                region == null ? 0 : region.y(),
                drawWidth,
                drawHeight,
                region == null ? 512 : region.width(),
                region == null ? 256 : region.height(),
                512,
                256
        );
    }

    private void renderConfirmation(DrawContext context, Rect confirmation, int mouseX, int mouseY) {
        context.fill(confirmation.x(), confirmation.y(), confirmation.right(), confirmation.bottom(), 0xF01D2127);
        drawBorder(context, confirmation, 0xFFE5C46A);
        int middle = confirmation.x() + confirmation.width() / 2;
        context.fill(middle, confirmation.y() + 2, middle + 1, confirmation.bottom() - 2, 0xFF5B626C);
        int okColor = mouseX < middle && confirmation.contains(mouseX, mouseY) ? 0xFF83D09B : 0xFFE7ECE9;
        int cancelColor = mouseX >= middle && confirmation.contains(mouseX, mouseY) ? 0xFFE28282 : 0xFFE7ECE9;
        context.drawCenteredTextWithShadow(screen.getTextRenderer(), "OK",
                confirmation.x() + confirmation.width() / 4, confirmation.y() + 8, okColor);
        context.drawCenteredTextWithShadow(screen.getTextRenderer(), "X",
                middle + confirmation.width() / 4, confirmation.y() + 8, cancelColor);
    }

    private void renderScrollbar(DrawContext context, Layout layout) {
        Rect thumb = scrollbarThumb(layout);
        if (thumb == null) {
            return;
        }
        Rect track = layout.scrollbar();
        context.fill(track.x(), track.y(), track.right(), track.bottom(), 0x80383E47);
        context.fill(thumb.x(), thumb.y(), thumb.right(), thumb.bottom(), 0xFFD3B95F);
    }

    private Rect scrollbarThumb(Layout layout) {
        if (layout.maxScroll() <= 0.0D) {
            return null;
        }
        Rect track = layout.scrollbar();
        int contentHeight = layout.contentHeight();
        int thumbHeight = Math.max(18, (int) Math.round(track.height() * (double) layout.viewport().height() / contentHeight));
        int travel = track.height() - thumbHeight;
        int thumbY = track.y() + (int) Math.round(travel * scroll / layout.maxScroll());
        return new Rect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private void updateScrollFromScrollbar(Layout layout, double mouseY) {
        Rect thumb = scrollbarThumb(layout);
        if (thumb == null) {
            scroll = 0.0D;
            return;
        }
        Rect track = layout.scrollbar();
        double travel = track.height() - thumb.height();
        if (travel <= 0.0D) {
            scroll = 0.0D;
            return;
        }
        double thumbTop = mouseY - scrollbarGrabOffset;
        double ratio = Math.clamp((thumbTop - track.y()) / travel, 0.0D, 1.0D);
        scroll = ratio * layout.maxScroll();
    }

    private int contentAt(Layout layout, double mouseX, double mouseY) {
        if (!layout.viewport().contains(mouseX, mouseY)) {
            return -1;
        }
        for (int index = 0; index < contentIds.size(); index++) {
            if (cellAt(layout, index).contains(mouseX, mouseY)) {
                return contentIds.get(index);
            }
        }
        return -1;
    }

    private Rect cellAt(Layout layout, int index) {
        int column = index % layout.columns();
        int row = index / layout.columns();
        int x = layout.viewport().x() + column * (CELL_SIZE + CELL_GAP);
        int y = layout.viewport().y() + row * (CELL_SIZE + CELL_GAP) - (int) Math.round(scroll);
        return new Rect(x, y, CELL_SIZE, CELL_SIZE);
    }

    private Layout layout() {
        MinecraftClient client = MinecraftClient.getInstance();
        ChatHud chatHud = client.inGameHud.getChatHud();
        int margin = 6;
        int desiredButtonX = 4 + chatHud.getWidth() + 6;
        int buttonX = Math.clamp(desiredButtonX, margin, Math.max(margin, screen.width - BUTTON_SIZE - margin));
        int buttonY = screen.height - 40 - BUTTON_SIZE;
        Rect button = new Rect(buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE);

        int rightSpace = screen.width - button.right() - PANEL_GAP - margin;
        int compactWidth = Math.max(1, screen.width - margin * 2);
        int panelWidth;
        int panelX;
        if (rightSpace >= PANEL_MIN_WIDTH) {
            panelX = button.right() + PANEL_GAP;
            panelWidth = Math.min(PANEL_MAX_WIDTH, rightSpace);
        } else {
            panelWidth = Math.min(PANEL_MAX_WIDTH, compactWidth);
            panelX = Math.clamp(button.right() + PANEL_GAP, margin, Math.max(margin, screen.width - panelWidth - margin));
        }
        int availableHeight = Math.max(1, screen.height - 46);
        int desiredPanelHeight = Math.min(PANEL_MAX_HEIGHT, Math.max(82, chatHud.getHeight()));
        int panelHeight = Math.min(desiredPanelHeight, availableHeight);
        int panelY = screen.height - 40 - panelHeight;
        Rect panel = new Rect(panelX, panelY, panelWidth, panelHeight);
        int confirmationSpace = pendingConfirmId >= 0 ? CONFIRM_HEIGHT + 4 : 0;
        int viewportWidth = Math.max(1, panel.width() - PANEL_PADDING * 2 - 7);
        int viewportHeight = Math.max(1, panel.height() - PANEL_PADDING * 2 - confirmationSpace);
        Rect viewport = new Rect(
                panel.x() + PANEL_PADDING,
                panel.y() + PANEL_PADDING,
                viewportWidth,
                viewportHeight
        );
        int columns = Math.max(1, (viewport.width() + CELL_GAP) / (CELL_SIZE + CELL_GAP));
        int rows = (contentIds.size() + columns - 1) / columns;
        int contentHeight = rows * CELL_SIZE + Math.max(0, rows - 1) * CELL_GAP;
        double maxScroll = Math.max(0, contentHeight - viewport.height());
        scroll = Math.clamp(scroll, 0.0D, maxScroll);
        Rect scrollbar = new Rect(panel.right() - 6, viewport.y(), 3, viewport.height());
        Rect confirmation = new Rect(
                panel.x() + PANEL_PADDING,
                panel.bottom() - PANEL_PADDING - CONFIRM_HEIGHT,
                panel.width() - PANEL_PADDING * 2,
                CONFIRM_HEIGHT
        );
        return new Layout(button, panel, viewport, scrollbar, confirmation, columns, contentHeight, maxScroll);
    }

    private static void drawBorder(DrawContext context, Rect rect, int color) {
        context.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, color);
        context.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
        context.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), color);
        context.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), color);
    }

    private record Layout(
            Rect button,
            Rect panel,
            Rect viewport,
            Rect scrollbar,
            Rect confirmation,
            int columns,
            int contentHeight,
            double maxScroll
    ) {
    }

    private record Rect(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }
}
