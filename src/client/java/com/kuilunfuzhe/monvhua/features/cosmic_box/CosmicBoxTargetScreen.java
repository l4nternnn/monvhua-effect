package com.kuilunfuzhe.monvhua.features.cosmic_box;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CosmicBoxTargetScreen extends Screen {
    private static final int BUTTON_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_SPACING = 4;
    private static final int ROWS_PER_PAGE = 9;
    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 64;
    private static final int PADDING = 10;

    private final BlockPos pos;
    private final List<CosmicBoxTargetListS2CPacket.TargetEntry> targets;
    private final Set<UUID> selected = new LinkedHashSet<>();
    private int page;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public CosmicBoxTargetScreen(BlockPos pos, List<CosmicBoxTargetListS2CPacket.TargetEntry> targets) {
        super(Text.literal("宇宙盒目标"));
        this.pos = pos;
        this.targets = targets;
    }

    @Override
    protected void init() {
        super.init();
        panelWidth = BUTTON_WIDTH + PADDING * 2;
        panelHeight = HEADER_HEIGHT + ROWS_PER_PAGE * (BUTTON_HEIGHT + BUTTON_SPACING) + FOOTER_HEIGHT + PADDING;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        TextWidget title = new TextWidget(
                panelX,
                panelY + 7,
                panelWidth,
                18,
                Text.literal("选择宇宙光束目标"),
                textRenderer
        );
        title.alignCenter();
        addDrawableChild(title);

        int start = page * ROWS_PER_PAGE;
        int end = Math.min(targets.size(), start + ROWS_PER_PAGE);
        int buttonX = panelX + PADDING;
        int buttonY = panelY + HEADER_HEIGHT;

        for (int i = start; i < end; i++) {
            CosmicBoxTargetListS2CPacket.TargetEntry target = targets.get(i);
            int row = i - start;
            ButtonWidget button = ButtonWidget.builder(labelFor(target), clicked -> {
                        toggle(target.uuid());
                        clicked.setMessage(labelFor(target));
                    })
                    .dimensions(buttonX, buttonY + row * (BUTTON_HEIGHT + BUTTON_SPACING), BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            addDrawableChild(button);
        }

        int footerY = panelY + panelHeight - FOOTER_HEIGHT + 8;
        int halfWidth = (BUTTON_WIDTH - BUTTON_SPACING) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("上一页"), button -> {
                    page = Math.max(0, page - 1);
                    clearAndInit();
                })
                .dimensions(buttonX, footerY, halfWidth, BUTTON_HEIGHT)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一页"), button -> {
                    int maxPage = Math.max(0, (targets.size() - 1) / ROWS_PER_PAGE);
                    page = Math.min(maxPage, page + 1);
                    clearAndInit();
                })
                .dimensions(buttonX + halfWidth + BUTTON_SPACING, footerY, halfWidth, BUTTON_HEIGHT)
                .build());

        int actionY = footerY + BUTTON_HEIGHT + BUTTON_SPACING;
        addDrawableChild(ButtonWidget.builder(Text.literal("确认选择"), button -> confirm())
                .dimensions(buttonX, actionY, halfWidth, BUTTON_HEIGHT)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("清空目标"), button -> {
                    selected.clear();
                    confirm();
                })
                .dimensions(buttonX + halfWidth + BUTTON_SPACING, actionY, halfWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA000000);
        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF3B5870);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF01A1D25);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT - 5, 0xFF153A56);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF7CCCF0);

        if (targets.isEmpty()) {
            Text empty = Text.literal("附近没有可选择的玩家或实体").formatted(Formatting.YELLOW);
            context.drawCenteredTextWithShadow(textRenderer, empty, panelX + panelWidth / 2, panelY + 72, 0xFFFFFF);
        } else {
            int maxPage = Math.max(0, (targets.size() - 1) / ROWS_PER_PAGE);
            String footer = (page + 1) + " / " + (maxPage + 1) + "  共 " + targets.size()
                    + " 个目标，已选 " + selected.size() + " 个";
            context.drawCenteredTextWithShadow(textRenderer, footer, panelX + panelWidth / 2, panelY + panelHeight - 14, 0xA7DFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void toggle(UUID uuid) {
        if (!selected.add(uuid)) {
            selected.remove(uuid);
        }
    }

    private Text labelFor(CosmicBoxTargetListS2CPacket.TargetEntry target) {
        String mark = selected.contains(target.uuid()) ? "[x] " : "[ ] ";
        String label = mark + "[" + target.kind() + "] " + target.name() + "  " + String.format("%.1fm", target.distance());
        return Text.literal(label);
    }

    private void confirm() {
        List<CosmicBoxSelectTargetC2SPacket.SelectedTarget> selectedTargets = new ArrayList<>();
        for (CosmicBoxTargetListS2CPacket.TargetEntry target : targets) {
            if (selected.contains(target.uuid())) {
                selectedTargets.add(new CosmicBoxSelectTargetC2SPacket.SelectedTarget(target.uuid(), target.name()));
            }
        }
        ClientPlayNetworking.send(new CosmicBoxSelectTargetC2SPacket(pos, selectedTargets));
        if (client != null) {
            client.setScreen(null);
        }
    }
}
