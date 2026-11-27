package com.kuilunfuzhe.monvhua.features.forensics.cards;

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
import java.util.UUID;

/** Captures all deferred snapshot renderers into one viewer-owned, immutable image. */
public final class ClueSnapshotTexture implements AutoCloseable {
    public static final int WIDTH=768,HEIGHT=576;
    public static final int SCALE=4;
    private static GuiRenderer active;
    private final Identifier id=Identifier.of("monvhua","dynamic/clue_image/"+UUID.randomUUID());
    private final GuiRenderState state=new GuiRenderState();
    private final BufferAllocator allocator=new BufferAllocator(512*1024);
    private final GuiRenderer renderer=new GuiRenderer(state,VertexConsumerProvider.immediate(allocator),List.of());
    private final SimpleFramebuffer target=new SimpleFramebuffer("clue image",WIDTH,HEIGHT,true);
    private boolean closed;
    public ClueSnapshotTexture(){
        var texture=new PortalFramebufferTexture();texture.setFramebuffer(target);texture.setLinearFiltering();
        MinecraftClient.getInstance().getTextureManager().registerTexture(id,texture);
    }
    public static boolean isRendering(Object renderer){return active==renderer;}
    public Identifier id(){return id;}
    public void draw(DrawContext context,double x,double y,double width,double height){
        // Framebuffer textures use bottom-left texture coordinates; GUI images use top-left.
        context.getMatrices().pushMatrix();
        try{
            context.getMatrices().translate((float)x,(float)y);
            context.getMatrices().scale((float)(width/WIDTH),(float)(height/HEIGHT));
            // Flip UVs rather than geometry: negative geometry scale is culled by GUI rendering.
            context.drawTexturedQuad(id,0,0,WIDTH,HEIGHT,0,1,1,0);
        }finally{context.getMatrices().popMatrix();}
    }
    public void capture(ClueCardPackets.Detail detail){
        var color=RenderSystem.outputColorTextureOverride;var depth=RenderSystem.outputDepthTextureOverride;
        var projection=RenderSystem.getProjectionMatrixBuffer();var type=RenderSystem.getProjectionType();
        var lights=RenderSystem.getShaderLights();var oldTarget=FramebufferOverride.getOverride();
        var previous=active;var stack=RenderSystem.getModelViewStack();stack.pushMatrix();stack.identity();
        active=renderer;
        try{
            FramebufferOverride.setOverride(target);
            RenderSystem.outputColorTextureOverride=target.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride=target.getDepthAttachmentView();
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorAttachment(),0xff554736,target.getDepthAttachment(),1);
            renderer.incrementFrame();
            ClueCardScreen.drawSnapshot(new DrawContext(MinecraftClient.getInstance(),state),detail,0,0,WIDTH/SCALE,HEIGHT/SCALE);
            renderer.render(RenderSystem.getShaderFog());
        }finally{
            state.clear();active=previous;stack.popMatrix();
            if(oldTarget==null)FramebufferOverride.clearOverride();else FramebufferOverride.setOverride(oldTarget);
            RenderSystem.outputColorTextureOverride=color;RenderSystem.outputDepthTextureOverride=depth;
            RenderSystem.setProjectionMatrix(projection,type);RenderSystem.setShaderLights(lights);
        }
    }
    @Override public void close(){
        if(closed)return;closed=true;
        try{renderer.close();}finally{try{allocator.close();}finally{
            MinecraftClient.getInstance().getTextureManager().destroyTexture(id);target.delete();
        }}
    }
}
