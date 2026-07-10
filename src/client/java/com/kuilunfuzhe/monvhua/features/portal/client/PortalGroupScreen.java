package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.network.portal.PortalPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class PortalGroupScreen extends Screen {
    private final BlockPos pos;
    private final String selectedGroup;
    private final String[] groups;
    private final int[] endpointCounts;
    private TextFieldWidget groupField;

    public PortalGroupScreen(BlockPos pos, String selectedGroup, String[] groups, int[] endpointCounts) {
        super(Text.literal("Light Portal Groups"));
        this.pos = pos.toImmutable();
        this.selectedGroup = selectedGroup == null ? "" : selectedGroup;
        this.groups = groups == null ? new String[0] : groups;
        this.endpointCounts = endpointCounts == null ? new int[0] : endpointCounts;
    }

    @Override
    protected void init() {
        int panelWidth = 260;
        int x = (width - panelWidth) / 2;
        int y = Math.max(28, height / 2 - 95);
        groupField = addDrawableChild(new TextFieldWidget(textRenderer, x + 10, y + 38, panelWidth - 20, 18, Text.literal("Group")));
        groupField.setMaxLength(32);
        groupField.setText(selectedGroup);
        addDrawableChild(ButtonWidget.builder(Text.literal("Create / Bind"), button -> bind(groupField.getText()))
                .dimensions(x + 10, y + 62, 118, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Delete Group"), button -> {
                    ClientPlayNetworking.send(new PortalPackets.DeleteGroupC2S(groupField.getText()));
                    close();
                })
                .dimensions(x + 132, y + 62, 118, 20)
                .build());
        int rowY = y + 92;
        for (int i = 0; i < Math.min(groups.length, 5); i++) {
            String group = groups[i];
            int count = i < endpointCounts.length ? endpointCounts[i] : 0;
            int buttonY = rowY + i * 22;
            addDrawableChild(ButtonWidget.builder(Text.literal(group + " (" + count + "/2)"), button -> bind(group))
                    .dimensions(x + 10, buttonY, panelWidth - 20, 20)
                    .build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(x + 70, y + 210, 120, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelWidth = 260;
        int panelHeight = 240;
        int x = (width - panelWidth) / 2;
        int y = Math.max(28, height / 2 - 95);
        context.fill(x, y, x + panelWidth, y + panelHeight, 0xE0101015);
        context.drawText(textRenderer, title, x + 10, y + 10, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.literal("Group name"), x + 10, y + 27, 0xFFB8DDE8, false);
        context.drawText(textRenderer, Text.literal("Existing groups"), x + 10, y + 84, 0xFFB8DDE8, false);
        super.render(context, mouseX, mouseY, delta);
    }

    private void bind(String group) {
        if (group != null && !group.trim().isEmpty()) {
            PortalFramebufferRenderer.requestPreviewCapture(pos);
            ClientPlayNetworking.send(new PortalPackets.BindGroupC2S(pos, group));
            close();
        }
    }
}
