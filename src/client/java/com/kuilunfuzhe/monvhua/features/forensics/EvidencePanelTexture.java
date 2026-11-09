package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.mirror.FramebufferOverride;
import com.kuilunfuzhe.monvhua.features.portal.client.PortalFramebufferTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import java.util.List;

/** An independent GUI submission queue; never invokes the game's global GUI renderer. */
public final class EvidencePanelTexture implements AutoCloseable {
    public static final int WIDTH=960,HEIGHT=540;
    // Compact world layout: three texture pixels per UI unit even at resolution 1.
    public static final int LOGICAL_WIDTH=320,LOGICAL_HEIGHT=180;
    private static final int UI_SCALE=WIDTH/LOGICAL_WIDTH;
    public static final Identifier ID=Identifier.of("monvhua","dynamic/evidence_panel_preview");
    private static GuiRenderer active;
    private static int activeScale=1;
    private final int resolutionScale;
    private final GuiRenderState state=new GuiRenderState();
    private final BufferAllocator allocator=new BufferAllocator(512*1024);
    private final GuiRenderer renderer=new GuiRenderer(state,VertexConsumerProvider.immediate(allocator),List.of());
    private final SimpleFramebuffer target;
    public EvidencePanelTexture(int resolutionScale){
        if(resolutionScale<1||resolutionScale>4)throw new IllegalArgumentException("Resolution scale must be 1..4");
        this.resolutionScale=resolutionScale;
        target=new SimpleFramebuffer("evidence panel",WIDTH*resolutionScale,HEIGHT*resolutionScale,true);
        var texture=new PortalFramebufferTexture();texture.setFramebuffer(target);texture.setLinearFiltering();
        MinecraftClient.getInstance().getTextureManager().registerTexture(ID,texture);
    }
    public static int renderScale(){return activeScale;}
    public static boolean isRendering(Object renderer){return active==renderer;}
    public void render(EvidencePanel panel,int x,int y,float delta){
        var color=RenderSystem.outputColorTextureOverride;var depth=RenderSystem.outputDepthTextureOverride;
        var projection=RenderSystem.getProjectionMatrixBuffer();var type=RenderSystem.getProjectionType();
        var lights=RenderSystem.getShaderLights();var oldTarget=FramebufferOverride.getOverride();
        var stack=RenderSystem.getModelViewStack();stack.pushMatrix();stack.identity();
        active=renderer;activeScale=UI_SCALE*resolutionScale;
        try {
            FramebufferOverride.setOverride(target);
            RenderSystem.outputColorTextureOverride=target.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride=target.getDepthAttachmentView();
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorAttachment(),0xFF111821,target.getDepthAttachment(),1);
            renderer.incrementFrame();
            var context=new DrawContext(MinecraftClient.getInstance(),state);
            panel.render(context,x,y,delta);
            renderer.render(RenderSystem.getShaderFog());
        } finally {
            state.clear();active=null;activeScale=1;stack.popMatrix();
            if(oldTarget==null)FramebufferOverride.clearOverride();else FramebufferOverride.setOverride(oldTarget);
            RenderSystem.outputColorTextureOverride=color;RenderSystem.outputDepthTextureOverride=depth;
            RenderSystem.setProjectionMatrix(projection,type);RenderSystem.setShaderLights(lights);
        }
    }
    @Override public void close(){
        renderer.close();allocator.close();
        MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);target.delete();
    }
}
