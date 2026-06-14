package com.kuilunfuzhe.monvhua.gui.evil_eyes;

import com.kuilunfuzhe.monvhua.features.evil_eyes.ClairvoyanceViewportRenderer;
import com.kuilunfuzhe.monvhua.features.evil_eyes.Evil_EyesClient;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.SelectView;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets.UnmarkEntityC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 千里眼已标记实体列表界面。
 * 左侧显示已标记实体名称（点击可切换第二人称视角），
 * 右侧显示操作说明，每个实体旁边有删除按钮（✕）可取消标记。
 */
public class Evil_eyesScreen extends Screen {
    private static final int REFRESH_INTERVAL_TICKS = 40;
    /** 当前已标记的实体列表，UUID -> 标记时间戳 */
    private static Map<UUID, Long> currentMarks = new ConcurrentHashMap<>();
    private int panelWidth, panelHeight, panelX, panelY;
    private int leftWidth, rightWidth;
    private int previewX, previewY, previewWidth, previewHeight;
    private long nextAutoRefreshTick;

    public Evil_eyesScreen() {
        super(Text.empty());
    }

    /**
     * 用服务器下发的标记列表全量替换本地列表并刷新界面。
     * @param marks 新的标记实体映射（UUID -> 时间戳）
     */
    public static void updateMarkedList(Map<UUID, Long> marks) {
        currentMarks.clear();
        currentMarks.putAll(marks);
        refreshCurrentScreen();
    }

    // 立即从本地列表中移除指定实体并刷新GUI（无需等待服务器回包）
    public static void removeFromLocalList(UUID entityUuid) {
        currentMarks.remove(entityUuid);
        refreshCurrentScreen();
    }

    private static void refreshCurrentScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof Evil_eyesScreen screen) {
            screen.refreshEntityButtons();
        }
    }

    @Override
    protected void init() {
        super.init();
        int sw = this.client.getWindow().getScaledWidth();
        int sh = this.client.getWindow().getScaledHeight();
        // 面板占屏幕宽度的一半，保持16:9比例
        panelWidth = sw / 2;
        panelHeight = (int) (panelWidth * 9f / 16f);
        // 面板居中，左右平分
        panelX = (sw - panelWidth) / 2;
        panelY = (sh - panelHeight) / 2;
        leftWidth = panelWidth / 2;  // 左半边放实体列表
        rightWidth = panelWidth - leftWidth;
        previewX = panelX + leftWidth + 8;
        previewY = panelY + 34;
        previewWidth = Math.max(1, rightWidth - 16);
        previewHeight = Math.min(panelHeight - 46, Math.max(1, (int) (previewWidth * 9f / 16f)));
        nextAutoRefreshTick = 0L;

        addDrawableChild(new TextWidget(panelX + 5, panelY + 10, leftWidth - 10, 20,
                Text.literal("已标记实体列表"), textRenderer));
        if (Evil_EyesClient.isViewportMode()) {
            addDrawableChild(new TextWidget(panelX + leftWidth + 5, panelY + 10, rightWidth - 10, 20,
                    Text.literal("预览视角"), textRenderer));
        } else {
            addDrawableChild(new TextWidget(panelX + leftWidth + 5, panelY + 10, rightWidth - 10, 20,
                    Text.literal("点击左侧实体名称"), textRenderer));
            addDrawableChild(new TextWidget(panelX + leftWidth + 5, panelY + 35, rightWidth - 10, 20,
                    Text.literal("即可切换到该实体的"), textRenderer));
            addDrawableChild(new TextWidget(panelX + leftWidth + 5, panelY + 60, rightWidth - 10, 20,
                    Text.literal("第二人称视角"), textRenderer));
        }

        refreshEntityButtons();
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
            refreshEntityButtons();
        }
    }

    /**
     * 根据 currentMarks 重新生成实体按钮列表。
     * 先隐藏并移除所有旧按钮，再为每个已标记实体创建名称按钮和删除按钮。
     */
    private void refreshEntityButtons() {
        // 隐藏旧的按钮并从children移除（drawables无法直接访问，通过visible=false避免渲染）
        java.util.List<Element> oldBtns = new ArrayList<>();
        for (Element child : children()) {
            if (child instanceof ButtonWidget btn) {
                btn.visible = false;
                oldBtns.add(child);
            }
        }
        children().removeAll(oldBtns);
        if (Evil_EyesClient.isViewportMode()) {
            UUID selected = ClairvoyanceViewportRenderer.getSelectedTarget();
            if (selected == null || !currentMarks.containsKey(selected)) {
                ClairvoyanceViewportRenderer.setSelectedTarget(currentMarks.keySet().stream().findFirst().orElse(null));
            }
        }
        int yOffset = 40;
        for (UUID uuid : currentMarks.keySet()) {
            String name = getEntityName(uuid);
            int btnWidth = leftWidth - 10 - 20;
            // 实体名称按钮 - 点击选择观看
            ButtonWidget nameBtn = ButtonWidget.builder(Text.literal(name), button -> {
                        if (Evil_EyesClient.isViewportMode()) {
                            ClairvoyanceViewportRenderer.setSelectedTarget(uuid);
                            if (client != null && client.player != null) {
                                client.player.sendMessage(Text.literal("\u00a7aPreview: " + name), true);
                            }
                            return;
                        }
                        ClientPlayNetworking.send(new SelectView(uuid));
                        if (client != null && client.player != null) {
                            client.player.sendMessage(Text.literal("§a正在切换到 " + name), true);
                        }
                    })
                    .dimensions(panelX + 5, panelY + yOffset, btnWidth, 20)
                    .build();
            addDrawableChild(nameBtn);
            // 删除按钮 - 点击取消标记（立即本地移除 + 服务端同步）
            ButtonWidget delBtn = ButtonWidget.builder(Text.literal("✕"), button -> {
                        ClairvoyanceViewportRenderer.clearSelectedTarget(uuid);
                        removeFromLocalList(uuid);
                        ClientPlayNetworking.send(new UnmarkEntityC2S(uuid));
                    })
                    .dimensions(panelX + 5 + btnWidth + 2, panelY + yOffset, 18, 20)
                    .build();
            addDrawableChild(delBtn);
            yOffset += 25;
            if (yOffset > panelHeight - 30) break;
        }
    }

    private String getEntityName(UUID uuid) {
        if (client != null && client.world != null) {
            var entity = client.world.getEntity(uuid);
            if (entity != null) return entity.getName().getString();
        }
        return "未知实体";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 半透明黑色遮罩
        context.fill(0, 0, width, height, 0xAA000000);
        // 面板外边框
        context.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF444444);
        // 左右分栏分隔线
        context.fill(panelX + leftWidth, panelY, panelX + leftWidth + 1, panelY + panelHeight, 0xFF444444);
        // 左右两侧背景
        context.fill(panelX, panelY, panelX + leftWidth, panelY + panelHeight, 0xAA222222);
        context.fill(panelX + leftWidth + 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xAA222222);
        super.render(context, mouseX, mouseY, delta);
        if (Evil_EyesClient.isViewportMode()) {
            context.fill(previewX - 1, previewY - 1, previewX + previewWidth + 1, previewY + previewHeight + 1, 0xFF555555);
            ClairvoyanceViewportRenderer.renderPreviewRect(context, previewX, previewY, previewWidth, previewHeight);
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
}
