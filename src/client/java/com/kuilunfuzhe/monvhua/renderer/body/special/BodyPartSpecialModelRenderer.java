package com.kuilunfuzhe.monvhua.renderer.body.special;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.Set;

/**
 * 身体部件特殊模型渲染器的抽象基类，用于在物品展示/掉落物上下文中渲染自定义皮肤模型部件（头、躯干、手臂、腿）。
 * 子类只需覆写 {@link #renderModel} 和 {@link #collectVertices} 即可实现具体部件的渲染逻辑。
 *
 * <h3>Data获取优先级</h3>
 * {@link #getData} 按以下优先级解析渲染数据：
 * <ol>
 *   <li>先检查NBT中的 {@code local_skin} 字段（内置皮肤）</li>
 *   <li>再通过 {@code PROFILE} 组件获取玩家皮肤纹理</li>
 *   <li>均不存在时回退到分辨率皮肤</li>
 * </ol>
 */
public abstract class BodyPartSpecialModelRenderer implements SpecialModelRenderer<BodyPartSpecialModelRenderer.Data> {
    /**
     * 渲染数据记录：封装渲染层、纹理标识和手臂模型类型。
     * @param layer 渲染层（使用EntityCutoutNoCull避免裁剪）
     * @param texture 皮肤纹理的Identifier
     * @param armModel 手臂模型类型："default"为标准手臂，"slim"为纤细手臂
     */
    public record Data(RenderLayer layer, Identifier texture, String armModel, @Nullable NbtCompound customData) {
        public Data(RenderLayer layer, Identifier texture, String armModel) {
            this(layer, texture, armModel, null);
        }

        /** 根据SkinTextures构造默认的Data（armModel默认为"default"） */
        static Data of(SkinTextures textures) {
            return new Data(RenderLayer.getEntityCutoutNoCull(textures.texture()), textures.texture(), "default");
        }
    }

    /** 默认渲染数据，使用当前客户端玩家皮肤构造，作为ItemStack无数据时的回退值 */
    protected final Data defaultData;

    public BodyPartSpecialModelRenderer(LoadedEntityModels entityModels) {
        var session = MinecraftClient.getInstance().getSession();
        GameProfile profile = new GameProfile(session.getUuidOrNull(), session.getUsername());
        SkinTextures textures = MinecraftClient.getInstance().getSkinProvider().getSkinTextures(profile);
        this.defaultData = Data.of(textures);
    }

    /**
     * 执行渲染：设置物品展示坐标系（平移→旋转180°→镜像翻转），然后委托给子类的renderModel。
     */
    @Override
    public void render(@Nullable Data data, ItemDisplayContext displayContext, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, boolean leftHanded) {
        Data actualData = data != null ? data : this.defaultData;
        RenderLayer renderLayer = actualData.layer();

        matrices.push();
        // 将原点平移到方块中心（物品展示默认原点在方块中心）
        matrices.translate(0.5F, 0.0F, 0.5F);
        // 绕Y轴旋转180°使模型正面朝向玩家
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        // 镜像翻转（X和Y轴取反）以匹配物品展示时的坐标系
        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.translate(0.0F, 0.0F, 0.0F);

        renderModel(matrices, vertexConsumers, renderLayer, light, overlay, actualData);

        matrices.pop();
    }

    /** 由子类实现的模型渲染方法，在已设置好的坐标系中执行具体部件的绘制 */
    protected abstract void renderModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                        RenderLayer renderLayer, int light, int overlay, Data data);

    /**
     * 从ItemStack中解析渲染数据，优先级如下：
     * <ol>
     *   <li>NBT {@code CUSTOM_DATA.local_skin} → 内置皮肤纹理</li>
     *   <li>{@code PROFILE} 组件 → 玩家皮肤纹理（可附带arm_model）</li>
     *   <li>以上都不存在 → 返回null，调用方使用defaultData</li>
     * </ol>
     */
    @Nullable
    @Override
    public Data getData(ItemStack stack) {
        String armModel = "default";

        // 优先检查NBT中的local_skin字段——内置皮肤路径
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound nbt = customData.copyNbt();
            Optional<String> localSkin = nbt.getString("local_skin");
            if (localSkin.isPresent()) {
                armModel = nbt.getString("arm_model").orElse("default");
                Identifier localTexture = Identifier.of("monvhua", "textures/local_skin/" + localSkin.get() + ".png");
                return new Data(RenderLayer.getEntityCutoutNoCull(localTexture), localTexture, armModel, nbt);
            }
        }

        ProfileComponent profile = stack.get(DataComponentTypes.PROFILE);
        if (profile == null) return null;
        ProfileComponent resolved = profile.resolve();
        if (resolved == null) return null;
        SkinTextures textures = MinecraftClient.getInstance().getSkinProvider()
                .getSkinTextures(resolved.gameProfile(), null);
        if (textures == null) return null;

        if (customData != null) {
            String model = customData.copyNbt().getString("arm_model").orElse("");
            if (!model.isEmpty()) {
                armModel = model;
            }
        }

        return new Data(RenderLayer.getEntityCutoutNoCull(textures.texture()), textures.texture(), armModel, customData != null ? customData.copyNbt() : null);
    }

    @Override
    public abstract void collectVertices(Set<Vector3f> vertices);

    /** 未烘焙的渲染器，用于Codec序列化/反序列化和模型烘焙流程 */
    public abstract static class Unbaked implements SpecialModelRenderer.Unbaked {
        @Override
        public abstract SpecialModelRenderer<?> bake(LoadedEntityModels entityModels);
    }
}
