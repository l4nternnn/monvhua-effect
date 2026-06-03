package com.kuilunfuzhe.monvhua.renderer.body_skeletal;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalBodyPart;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalBodyPartBlockEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.UUID;

public class SkeletalBodyPartGeoModel<T extends SkeletalBodyPartBlockEntity> extends GeoModel<T> {
    public static final DataTicket<SkeletalBodyPartBlockEntity> BLOCK_ENTITY =
            DataTicket.create("monvhua_skeletal_body_part", SkeletalBodyPartBlockEntity.class);

    private static final Identifier MODEL = Identifier.of(MonvhuaMod.MOD_ID, "skeletal_body");
    private static final Identifier ANIMATION = Identifier.of(MonvhuaMod.MOD_ID, "skeletal_body");
    private static final Identifier FALLBACK_TEXTURE = Identifier.of(MonvhuaMod.MOD_ID, "textures/block/torso.png");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        SkeletalBodyPartBlockEntity entity = renderState.getOrDefaultGeckolibData(BLOCK_ENTITY, null);
        if (entity == null) {
            return FALLBACK_TEXTURE;
        }
        return getSkinTexture(entity);
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(AnimationState<T> animationState) {
        SkeletalBodyPartBlockEntity entity = animationState.getDataOrDefault(BLOCK_ENTITY, null);
        if (entity == null) {
            return;
        }

        String activeBone = entity.getPart().id();
        for (SkeletalBodyPart part : SkeletalBodyPart.values()) {
            applyPartPose(part, activeBone, entity);
        }
    }

    private void applyPartPose(SkeletalBodyPart part, String activeBone, SkeletalBodyPartBlockEntity entity) {
        String boneName = part.id();
        GeoBone bone = getBone(boneName).orElse(null);
        if (bone == null) {
            return;
        }

        boolean active = boneName.equals(activeBone);
        bone.setHidden(!active);
        bone.updateRotation(0.0F, 0.0F, 0.0F);

        GeoBone bendBone = getBendBone(part);
        if (bendBone != null) {
            bendBone.updateRotation(0.0F, 0.0F, 0.0F);
        }
        GeoBone blendBone = getBlendBone(part);
        if (blendBone != null) {
            blendBone.updateRotation(0.0F, 0.0F, 0.0F);
            blendBone.setHidden(true);
        }

        if (active) {
            bone.updateRotation(
                    degreesToRadians(entity.getJointPitch()),
                    degreesToRadians(entity.getJointYaw()),
                    degreesToRadians(entity.getJointRoll())
            );
            if (bendBone != null) {
                bendBone.updateRotation(
                        degreesToRadians(entity.getBendPitch()),
                        degreesToRadians(entity.getBendYaw()),
                        degreesToRadians(entity.getBendRoll())
                );
            }
            if (blendBone != null && hasBendPose(entity)) {
                blendBone.setHidden(false);
                blendBone.updateRotation(
                        degreesToRadians(entity.getBendPitch() * 0.5F),
                        degreesToRadians(entity.getBendYaw() * 0.5F),
                        degreesToRadians(entity.getBendRoll() * 0.5F)
                );
            }
        }
    }

    private GeoBone getBendBone(SkeletalBodyPart part) {
        String boneName = switch (part) {
            case TORSO -> "waist";
            case LEFT_ARM -> "left_forearm";
            case RIGHT_ARM -> "right_forearm";
            case LEFT_LEG -> "left_lower_leg";
            case RIGHT_LEG -> "right_lower_leg";
            default -> null;
        };
        return boneName == null ? null : getBone(boneName).orElse(null);
    }

    private GeoBone getBlendBone(SkeletalBodyPart part) {
        String boneName = switch (part) {
            case TORSO -> "waist_blend";
            case LEFT_ARM -> "left_elbow_blend";
            case RIGHT_ARM -> "right_elbow_blend";
            case LEFT_LEG -> "left_knee_blend";
            case RIGHT_LEG -> "right_knee_blend";
            default -> null;
        };
        return boneName == null ? null : getBone(boneName).orElse(null);
    }

    private static boolean hasBendPose(SkeletalBodyPartBlockEntity entity) {
        return entity.getBendPitch() != 0.0F || entity.getBendYaw() != 0.0F || entity.getBendRoll() != 0.0F;
    }

    private static float degreesToRadians(float degrees) {
        return degrees * ((float) Math.PI / 180.0F);
    }

    private static Identifier getSkinTexture(SkeletalBodyPartBlockEntity entity) {
        String localSkin = entity.getLocalSkin();
        if (localSkin != null) {
            return Identifier.of(MonvhuaMod.MOD_ID, "textures/local_skin/" + localSkin + ".png");
        }

        ProfileComponent owner = entity.getOwner();
        if (owner != null && owner.gameProfile() != null) {
            GameProfile profile = owner.gameProfile();
            if (!profile.getProperties().get("textures").isEmpty()) {
                return MinecraftClient.getInstance().getSkinProvider().getSkinTextures(profile).texture();
            }
        }

        UUID playerUuid = entity.getPlayerUuid();
        if (playerUuid != null) {
            return MinecraftClient.getInstance()
                    .getSkinProvider()
                    .getSkinTextures(new GameProfile(playerUuid, ""))
                    .texture();
        }

        return FALLBACK_TEXTURE;
    }
}
