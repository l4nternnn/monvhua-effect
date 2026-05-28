package com.kuilunfuzhe.monvhua.gui.openback;

import com.kuilunfuzhe.monvhua.screen.OtherPlayerInventoryScreenHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class OtherPlayerInventoryScreen extends HandledScreen<OtherPlayerInventoryScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of("monvhua", "textures/gui/img_1.png");
    private static final int TEXTURE_WIDTH = 256;   // 根据你的纹理实际尺寸修改
    private static final int TEXTURE_HEIGHT = 276;
    private static final Text VIEWER_HOTBAR_TITLE = Text.literal("你的快捷栏");
    private static final int TEXT_COLOR = 0x404040;
    private static final int TITLE_TEXT_X = 8;
    private static final int TITLE_TEXT_Y = 6;
    private static final int VIEWER_HOTBAR_TEXT_X = 8;
    private static final int VIEWER_HOTBAR_TEXT_Y = 202;

    public OtherPlayerInventoryScreen(OtherPlayerInventoryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 276;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE, this.x, this.y, 0, 0,
                this.backgroundWidth, this.backgroundHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, TITLE_TEXT_X, TITLE_TEXT_Y, TEXT_COLOR, false);
        context.drawText(this.textRenderer, VIEWER_HOTBAR_TITLE, VIEWER_HOTBAR_TEXT_X, VIEWER_HOTBAR_TEXT_Y, TEXT_COLOR, false);
    }
}
