package com.kuilunfuzhe.monvhua.gui.imitate;

import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.network.imitate.RequestSilenceTargetsC2SPacket;
import com.kuilunfuzhe.monvhua.network.imitate.SilencePacket;
import com.kuilunfuzhe.monvhua.network.imitate.SilenceTargetsS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SilenceTargetScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("Monvhua/SilenceTarget");

    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_SPACING = 4;
    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 50;
    private static final int PADDING = 10;
    private static final int MAX_ROWS = 8;

    private int panelWidth;
    private int panelHeight;
    private int panelX;
    private int panelY;
    private final List<PlayerInfo> nearbyPlayers = new ArrayList<>();
    private final double radius;
    private boolean loadedTargets;

    public SilenceTargetScreen(double radius) {
        super(Text.literal("屏蔽声音"));
        this.radius = radius;
    }

    private static class PlayerInfo {
        final UUID uuid;
        final Text name;
        final String sortName;
        final double distance;

        PlayerInfo(UUID uuid, Text name, String sortName, double distance) {
            this.uuid = uuid;
            this.name = name;
            this.sortName = sortName;
            this.distance = distance;
        }
    }

    @Override
    protected void init() {
        super.init();
        requestTargets();
        rebuildWidgets();
    }

    public void receiveTargets(SilenceTargetsS2CPacket packet) {
        nearbyPlayers.clear();
        if (client != null && client.player != null && client.world != null) {
            UUID myUuid = client.player.getUuid();
            LOGGER.info("检测周围玩家，半径: {}", radius);
            for (PlayerEntity player : client.world.getPlayers()) {
                if (!player.getUuid().equals(myUuid)) {
                    double distance = client.player.distanceTo(player);
                    LOGGER.info("发现玩家: {}, 距离: {}", player.getName().getString(), distance);
                    if (distance <= radius) {
                        nearbyPlayers.add(new PlayerInfo(player.getUuid(), player.getName().getString(), distance));
                        LOGGER.info("玩家 {} 在范围内，添加到列表", player.getName().getString());
                    }
                }
            }
            nearbyPlayers.sort(Comparator.comparingDouble(p -> p.distance));
            LOGGER.info("共发现 {} 个可静音的玩家", nearbyPlayers.size());
        } else {
            LOGGER.warn("无法获取客户端世界或玩家");
        }
        loadedTargets = true;
        rebuildWidgets();
    }

    private void requestTargets() {
        if (client != null && client.player != null) {
            ClientPlayNetworking.send(new RequestSilenceTargetsC2SPacket(radius));
        }
    }

    private void rebuildWidgets() {
        clearChildren();

        int totalButtons = nearbyPlayers.size();
        int rows = Math.min(totalButtons, MAX_ROWS);

        panelWidth = BUTTON_WIDTH + PADDING * 2;
        panelHeight = HEADER_HEIGHT + rows * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING + FOOTER_HEIGHT + PADDING * 2;

        int sw = this.client.getWindow().getScaledWidth();
        int sh = this.client.getWindow().getScaledHeight();
        panelX = (sw - panelWidth) / 2;
        panelY = (sh - panelHeight) / 2;

        TextWidget titleWidget = new TextWidget(
                panelX, panelY + 5, panelWidth, 20,
                Text.literal("§c✦ 选择静音目标 §r✦"), textRenderer
        );
        titleWidget.alignCenter();
        addDrawableChild(titleWidget);

        int startX = panelX + PADDING;
        int startY = panelY + HEADER_HEIGHT;

        for (int i = 0; i < Math.min(nearbyPlayers.size(), MAX_ROWS); i++) {
            PlayerInfo info = nearbyPlayers.get(i);
            int y = startY + i * (BUTTON_HEIGHT + BUTTON_SPACING);

            Text displayText = Text.empty()
                    .append(info.name)
                    .append(Text.literal(" (" + String.format("%.1f", info.distance) + "m)").formatted(Formatting.GRAY));
            ButtonWidget playerBtn = ButtonWidget.builder(displayText, button -> {
                        ClientPlayNetworking.send(new SilencePacket(info.uuid));
                        if (client != null) {
                            client.setScreen(null);
                        }
                    })
                    .dimensions(startX, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            addDrawableChild(playerBtn);
        }

        int footerY = startY + rows * (BUTTON_HEIGHT + BUTTON_SPACING) + 10;
        int cancelButtonX = panelX + (panelWidth - BUTTON_WIDTH) / 2;

        ButtonWidget cancelBtn = ButtonWidget.builder(Text.literal("§c✖ 取消"), button -> {
                    if (client != null) {
                        client.setScreen(null);
                    }
                })
                .dimensions(cancelButtonX, footerY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        addDrawableChild(cancelBtn);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xCC000000);

        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF555555);
        context.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF333333);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0222222);

        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF888888);

        context.fill(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT - 5, 0xFF663333);

        if (nearbyPlayers.isEmpty()) {
            Text message = loadedTargets
                    ? Text.literal("§e附近没有可静音的玩家").formatted(Formatting.YELLOW)
                    : Text.literal("§7正在获取目标...").formatted(Formatting.GRAY);
            int textWidth = textRenderer.getWidth(message);
            int textX = panelX + (panelWidth - textWidth) / 2;
            int textY = panelY + HEADER_HEIGHT + 20;
            context.drawTextWithShadow(textRenderer, message, textX, textY, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
