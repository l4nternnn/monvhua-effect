package com.kuilunfuzhe.monvhua.model;

import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/**
 * 注册本模组使用的所有{@link EntityModelLayer}，供渲染器通过{@link EntityModelLayerHelper}查找对应的模型定义。
 *
 * <p>注意：COMBINED_BODY和COMBINED_BODY_SLIM共享同一个纹理路径{@code combined_body}，
 * 通过第二个参数{@code "main"}/{@code "slim"}区分体型，而非创建两个独立路径。</p>
 */
public class ModModelLayers {

    /** 全身模型层（默认体型，手臂4像素宽） */
    public static final EntityModelLayer COMBINED_BODY = new EntityModelLayer(Identifier.
            of("monvhua", "combined_body"), "main");

    /** 全身模型层（Slim体型，手臂3像素宽），与COMBINED_BODY共享combined_body路径 */
    public static final EntityModelLayer COMBINED_BODY_SLIM = new EntityModelLayer(Identifier.
            of("monvhua", "combined_body"), "slim");

    /** 躯干模型层 */
    public static final EntityModelLayer TORSO = new EntityModelLayer(Identifier.
            of("monvhua", "torso"), "main");

    /** 左臂模型层（默认体型，4像素宽） */
    public static final EntityModelLayer LEFT_ARM = new EntityModelLayer(Identifier.
            of("monvhua", "left_arm"), "main");

    /** 右臂模型层（默认体型，4像素宽） */
    public static final EntityModelLayer RIGHT_ARM = new EntityModelLayer(Identifier.
            of("monvhua", "right_arm"), "main");

    /** 左腿模型层 */
    public static final EntityModelLayer LEFT_LEG = new EntityModelLayer(Identifier.
            of("monvhua", "left_leg"), "main");

    /** 右腿模型层 */
    public static final EntityModelLayer RIGHT_LEG = new EntityModelLayer(Identifier.
            of("monvhua", "right_leg"), "main");

    /** 左臂模型层（Slim体型，3像素宽），与LEFT_ARM共享left_arm路径 */
    public static final EntityModelLayer LEFT_ARM_SLIM = new EntityModelLayer(Identifier.
            of("monvhua", "left_arm"), "slim");

    /** 右臂模型层（Slim体型，3像素宽），与RIGHT_ARM共享right_arm路径 */
    public static final EntityModelLayer RIGHT_ARM_SLIM = new EntityModelLayer(Identifier.
            of("monvhua", "right_arm"), "slim");

    /** 头部模型层 */
    public static final EntityModelLayer HEAD = new EntityModelLayer(Identifier.
            of("monvhua", "head"), "main");

}