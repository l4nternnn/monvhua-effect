package com.kuilunfuzhe.monvhua.gui.body.bodyback;

import com.kuilunfuzhe.monvhua.screen.BodyPartScreenHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 身体部位 3x3 容器 GUI 界面。
 * 使用自定义 256x256 纹理（非原版 176x166 通用容器纹理），
 * 整体渲染位置向右下偏移20px以适配纹理中的槽位位置。
 */
public class BodyPartScreen extends HandledScreen<BodyPartScreenHandler> {
    // 自定义3x3容器纹理（已替换原版通用容器纹理）
        private static final Identifier TEXTURE = Identifier.of("monvhua", "textures/gui/img_2.png");
//    private static final Identifier TEXTURE = Identifier.of("minecraft", "textures/gui/container/generic_54.png");  // 旧版已废弃
    /** 纹理的实际宽度和高度（当前为 256x256，非原版 176x166） */
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    public BodyPartScreen(BodyPartScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // 背景大小与256x256纹理一致
        this.backgroundWidth = 256;
        this.backgroundHeight = 256;
        // 标题坐标：原版默认(8,6)基础上+20偏移，适配纹理中的槽位位置
        this.titleX = 8+20;
        this.titleY = 6+20;
        // 玩家背包标题X同理偏移+20，Y在底部往上94px处
        this.playerInventoryTitleX = 8 + 20;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // 居中后+20偏移，使纹理中的槽位与实际交互区域对齐
        int x = (this.width - this.backgroundWidth) / 2+20;
        int y = (this.height - this.backgroundHeight) / 2+20;
        // 使用完整256x256纹理渲染
        context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0,
                this.backgroundWidth, this.backgroundHeight,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void init() {
        super.init();
        // 以下代码已注释，标题坐标改为在构造函数中直接设置
//        this.titleX = (this.backgroundWidth - this.textRenderer.getWidth(this.title)) / 2;
    }

    // 以下注释代码保留供参考：原版白色阴影文字渲染方式
//    @Override
//    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
//        // 使用白色带阴影的文字
//        context.drawTextWithShadow(this.textRenderer, this.title, this.titleX, this.titleY, 0xFFFFFF);
////        context.drawTextWithShadow(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0xFFFFFF);
//    }
}