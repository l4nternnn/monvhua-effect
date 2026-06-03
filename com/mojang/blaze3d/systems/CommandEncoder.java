package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.annotation.DeobfuscateClass;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang原版GPU命令编码器接口，封装了底层渲染API的GPU命令提交操作。
 * 提供渲染通道创建、纹理/缓冲区清空与写入、数据拷贝、纹理呈现和同步围栏等核心GPU操作。
 * 所有方法均为客户端专用（@Environment(EnvType.CLIENT)）。
 */
@Environment(EnvType.CLIENT)
@DeobfuscateClass
public interface CommandEncoder {
	/**
	 * 创建渲染通道，仅指定颜色附着。
	 * @param labelGetter 渲染通道标签的Supplier，用于GPU调试标记
	 * @param colorAttachment 颜色附着的纹理视图
	 * @param clearColor 可选的颜色清除值（RGBA整数），空表示不清除
	 * @return 新创建的RenderPass实例
	 */
	RenderPass createRenderPass(Supplier<String> labelGetter, GpuTextureView colorAttachment, OptionalInt clearColor);

	/**
	 * 创建渲染通道，同时指定颜色附着和深度附着。
	 * @param labelGetter 渲染通道标签的Supplier，用于GPU调试标记
	 * @param colorAttachment 颜色附着的纹理视图
	 * @param clearColor 可选的颜色清除值（RGBA整数），空表示不清除
	 * @param depthAttachment 深度附着的纹理视图，可为null
	 * @param clearDepth 可选的深度清除值，空表示不清除
	 * @return 新创建的RenderPass实例
	 */
	RenderPass createRenderPass(
		Supplier<String> labelGetter, GpuTextureView colorAttachment, OptionalInt clearColor, @Nullable GpuTextureView depthAttachment, OptionalDouble clearDepth
	);

	/**
	 * 将颜色纹理清空为指定颜色值。
	 * @param texture 目标颜色纹理
	 * @param color 清空颜色值（RGBA整数格式）
	 */
	void clearColorTexture(GpuTexture texture, int color);

	/**
	 * 同时清空颜色纹理和深度纹理。
	 * @param colorAttachment 颜色附着纹理
	 * @param color 颜色清空值（RGBA整数格式）
	 * @param depthAttachment 深度附着纹理
	 * @param depth 深度清空值
	 */
	void clearColorAndDepthTextures(GpuTexture colorAttachment, int color, GpuTexture depthAttachment, double depth);

	/**
	 * 同时清空颜色纹理和深度纹理，并指定裁剪矩形区域。
	 * @param colorAttachment 颜色附着纹理
	 * @param color 颜色清空值（RGBA整数格式）
	 * @param depthAttachment 深度附着纹理
	 * @param depth 深度清空值
	 * @param scissorX 裁剪矩形X坐标（像素）
	 * @param scissorY 裁剪矩形Y坐标（像素）
	 * @param scissorWidth 裁剪矩形宽度（像素）
	 * @param scissorHeight 裁剪矩形高度（像素）
	 */
	void clearColorAndDepthTextures(
		GpuTexture colorAttachment, int color, GpuTexture depthAttachment, double depth, int scissorX, int scissorY, int scissorWidth, int scissorHeight
	);

	/**
	 * 将深度纹理清空为指定深度值。
	 * @param texture 目标深度纹理
	 * @param depth 深度清空值（通常0.0~1.0）
	 */
	void clearDepthTexture(GpuTexture texture, double depth);

	/**
	 * 将CPU端ByteBuffer数据写入GPU缓冲区。
	 * @param slice GPU缓冲区切片，指定写入的目标区域
	 * @param source CPU端数据源
	 */
	void writeToBuffer(GpuBufferSlice slice, ByteBuffer source);

	/**
	 * 映射整个GPU缓冲区到CPU可访问的内存视图。
	 * @param buffer 目标GPU缓冲区
	 * @param read 是否允许CPU读取
	 * @param write 是否允许CPU写入
	 * @return 映射后的MappedView，用于CPU端读写
	 */
	GpuBuffer.MappedView mapBuffer(GpuBuffer buffer, boolean read, boolean write);

	/**
	 * 映射GPU缓冲区切片到CPU可访问的内存视图。
	 * @param slice GPU缓冲区切片，指定映射区域
	 * @param read 是否允许CPU读取
	 * @param write 是否允许CPU写入
	 * @return 映射后的MappedView，用于CPU端读写
	 */
	GpuBuffer.MappedView mapBuffer(GpuBufferSlice slice, boolean read, boolean write);

	/**
	 * 在GPU缓冲区之间拷贝数据（GPU端到GPU端）。
	 * @param gpuBufferSlice 源GPU缓冲区切片
	 * @param gpuBufferSlice2 目标GPU缓冲区切片
	 */
	void copyToBuffer(GpuBufferSlice gpuBufferSlice, GpuBufferSlice gpuBufferSlice2);

	/**
	 * 将NativeImage数据写入整个纹理。
	 * @param target 目标GPU纹理
	 * @param source CPU端图像数据源
	 */
	void writeToTexture(GpuTexture target, NativeImage source);

	/**
	 * 将NativeImage数据写入纹理的指定区域和mip层级。
	 * @param target 目标GPU纹理
	 * @param source CPU端图像数据源
	 * @param mipLevel mipmap层级（0为最高分辨率）
	 * @param depth 深度层索引（用于3D纹理或纹理数组）
	 * @param offsetX 目标纹理写入起始X坐标
	 * @param offsetY 目标纹理写入起始Y坐标
	 * @param width 写入宽度
	 * @param height 写入高度
	 * @param skipPixels 源数据中跳过的像素数（每行开头）
	 * @param skipRows 源数据中跳过的行数
	 */
	void writeToTexture(
		GpuTexture target, NativeImage source, int mipLevel, int depth, int offsetX, int offsetY, int width, int height, int skipPixels, int skipRows
	);

	/**
	 * 将IntBuffer格式的数据写入纹理指定区域。
	 * @param target 目标GPU纹理
	 * @param source CPU端整数缓冲区数据源
	 * @param format 像素格式（如RGBA、BGRA等）
	 * @param mipLevel mipmap层级（0为最高分辨率）
	 * @param depth 深度层索引
	 * @param offsetX 目标纹理写入起始X坐标
	 * @param offsetY 目标纹理写入起始Y坐标
	 * @param width 写入宽度
	 * @param height 写入高度
	 */
	void writeToTexture(GpuTexture target, IntBuffer source, NativeImage.Format format, int mipLevel, int depth, int offsetX, int offsetY, int width, int height);

	/**
	 * 将整个纹理数据拷贝到GPU缓冲区。
	 * @param source 源GPU纹理
	 * @param target 目标GPU缓冲区
	 * @param offset 缓冲区写入偏移量（字节）
	 * @param dataUploadedCallback 数据上传完成后的回调
	 * @param mipLevel 要拷贝的mipmap层级
	 */
	void copyTextureToBuffer(GpuTexture source, GpuBuffer target, int offset, Runnable dataUploadedCallback, int mipLevel);

	/**
	 * 将纹理指定区域拷贝到GPU缓冲区。
	 * @param source 源GPU纹理
	 * @param target 目标GPU缓冲区
	 * @param offset 缓冲区写入偏移量（字节）
	 * @param dataUploadedCallback 数据上传完成后的回调
	 * @param mipLevel 要拷贝的mipmap层级
	 * @param intoX 纹理源区域起始X坐标
	 * @param intoY 纹理源区域起始Y坐标
	 * @param width 拷贝宽度
	 * @param height 拷贝高度
	 */
	void copyTextureToBuffer(
		GpuTexture source, GpuBuffer target, int offset, Runnable dataUploadedCallback, int mipLevel, int intoX, int intoY, int width, int height
	);

	/**
	 * 在GPU纹理之间拷贝数据（GPU端到GPU端）。
	 * @param source 源GPU纹理
	 * @param target 目标GPU纹理
	 * @param mipLevel 要拷贝的mipmap层级
	 * @param intoX 目标纹理写入起始X坐标
	 * @param intoY 目标纹理写入起始Y坐标
	 * @param sourceX 源纹理读取起始X坐标
	 * @param sourceY 源纹理读取起始Y坐标
	 * @param width 拷贝宽度
	 * @param height 拷贝高度
	 */
	void copyTextureToTexture(GpuTexture source, GpuTexture target, int mipLevel, int intoX, int intoY, int sourceX, int sourceY, int width, int height);

	/**
	 * 将纹理呈现到屏幕（交换链present操作）。
	 * @param texture 要呈现的纹理视图
	 */
	void presentTexture(GpuTextureView texture);

	/**
	 * 创建GPU同步围栏，用于命令执行完成后的同步等待。
	 * @return 新创建的GpuFence实例
	 */
	GpuFence createFence();
}
