package com.kuilunfuzhe.monvhua.gui.openback;

import com.kuilunfuzhe.monvhua.screen.OtherPlayerInventoryScreenHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 查看其他玩家背包的 GUI 界面。
 * 使用自定义纹理（256x276），渲染目标玩家的背包槽位和查看者自己的快捷栏。
 */
public class OtherPlayerInventoryScreen extends HandledScreen<OtherPlayerInventoryScreenHandler> {
    /** 背包GUI纹理资源 */
    private static final Identifier TEXTURE = Identifier.of("monvhua", "textures/gui/img_1.png");
    /** 纹理文件实际宽度 */
    private static final int TEXTURE_WIDTH = 256;   // 根据你的纹理实际尺寸修改
    /** 纹理文件实际高度 */
    private static final int TEXTURE_HEIGHT = 276;
    /** 查看者快捷栏的标题文字 */
    private static final Text VIEWER_HOTBAR_TITLE = Text.literal("你的快捷栏");
    /** 前景文字颜色（深灰） */
    private static final int TEXT_COLOR = 0x404040;
    /** 标题文字左上角X偏移 */
    private static final int TITLE_TEXT_X = 8;
    /** 标题文字左上角Y偏移 */
    private static final int TITLE_TEXT_Y = 6;
    /** 快捷栏标题X偏移 */
    private static final int VIEWER_HOTBAR_TEXT_X = 8;
    /** 快捷栏标题Y偏移（位于底部快捷栏上方） */
    private static final int VIEWER_HOTBAR_TEXT_Y = 202;

    public OtherPlayerInventoryScreen(OtherPlayerInventoryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // 背景高度与纹理实际高度一致
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
