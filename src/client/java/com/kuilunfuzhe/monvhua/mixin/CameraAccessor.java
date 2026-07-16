package com.kuilunfuzhe.monvhua.mixin;

import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Camera的Accessor接口，通过Mixin的@Invoker机制访问Camera类中的私有方法。
 * 用于在镜面渲染等功能中动态修改相机的位置和朝向。
 */
@Mixin(Camera.class)
public interface CameraAccessor {
	/**
	 * 调用Camera的私有setPos方法，设置相机世界坐标。
	 * @param x X轴坐标
	 * @param y Y轴坐标
	 * @param z Z轴坐标
	 */
	@Invoker("setPos")
	void invokeSetPos(double x, double y, double z);

	/**
	 * 调用Camera的私有setRotation方法，设置相机朝向。
	 * @param yaw 水平偏航角（度）
	 * @param pitch 垂直俯仰角（度）
	 */
	@Invoker("setRotation")
	void invokeSetRotation(float yaw, float pitch);

	@Accessor("rotation")
	Quaternionf monvhua$getRotation();

	@Accessor("horizontalPlane")
	Vector3f monvhua$getHorizontalPlane();

	@Accessor("verticalPlane")
	Vector3f monvhua$getVerticalPlane();

	@Accessor("diagonalPlane")
	Vector3f monvhua$getDiagonalPlane();
}
