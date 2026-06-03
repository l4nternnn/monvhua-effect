package com.kuilunfuzhe.monvhua.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.kuilunfuzhe.monvhua.features.mirror.MirrorViewportRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WorldRenderer渲染钩子Mixin，在WorldRenderer.render方法的尾部（TAIL）注入镜面渲染逻辑。
 * 在所有常规渲染完成后调用MirrorViewportRenderer绘制全屏镜面效果。
 */
@Mixin(WorldRenderer.class)
public class WorldRendererHookMixin {
	/**
	 * 在WorldRenderer.render尾部注入，执行全屏镜面渲染。
	 * @param allocator 对象分配器，用于渲染期间的临时对象分配
	 * @param tickCounter 渲染帧计数器，包含当前帧的时间信息
	 * @param renderBlockOutline 是否渲染方块轮廓线（F3+B调试模式）
	 * @param camera 当前渲染使用的相机
	 * @param positionMatrix 视图变换矩阵（4x4）
	 * @param projectionMatrix 投影变换矩阵（4x4）
	 * @param fog GPU雾效缓冲区切片
	 * @param fogColor 雾颜色（RGBA四分量向量）
	 * @param shouldRenderSky 是否渲染天空
	 * @param ci 回调信息
	 */
	@Inject(method = "render", at = @At("TAIL"))
	private void onRenderStart(
		ObjectAllocator allocator,
		RenderTickCounter tickCounter,
		boolean renderBlockOutline,
		Camera camera,
		Matrix4f positionMatrix,
		Matrix4f projectionMatrix,
		GpuBufferSlice fog,
		Vector4f fogColor,
		boolean shouldRenderSky,
		CallbackInfo ci
	) {
		MirrorViewportRenderer.renderFullScreenMirror(tickCounter, fog, fogColor, camera);
	}
}
