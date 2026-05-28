package com.kuilunfuzhe.monvhua.model;

import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public class ModModelLayers {
    public static final EntityModelLayer COMBINED_BODY = new EntityModelLayer(Identifier.
            of("monvhua", "combined_body"), "main");

    public static final EntityModelLayer COMBINED_BODY_SLIM = new EntityModelLayer(Identifier.
            of("monvhua", "combined_body"), "slim");

    public static final EntityModelLayer TORSO = new EntityModelLayer(Identifier.
            of("monvhua", "torso"), "main");


    public static final EntityModelLayer LEFT_ARM = new EntityModelLayer(Identifier.
            of("monvhua", "left_arm"), "main");

    public static final EntityModelLayer RIGHT_ARM = new EntityModelLayer(Identifier.
            of("monvhua", "right_arm"), "main");

    public static final EntityModelLayer LEFT_LEG = new EntityModelLayer(Identifier.
            of("monvhua", "left_leg"), "main");

    public static final EntityModelLayer RIGHT_LEG = new EntityModelLayer(Identifier.
            of("monvhua", "right_leg"), "main");

    public static final EntityModelLayer LEFT_ARM_SLIM = new EntityModelLayer(Identifier.
            of("monvhua", "left_arm"), "slim");

    public static final EntityModelLayer RIGHT_ARM_SLIM = new EntityModelLayer(Identifier.
            of("monvhua", "right_arm"), "slim");

    public static final EntityModelLayer HEAD = new EntityModelLayer(Identifier.
            of("monvhua", "head"), "main");

}