package com.kuilunfuzhe.monvhua.renderer.body;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 皮肤外层面体素渲染器，将皮肤纹理中的像素转换为3D体素进行渲染。
 *
 * <h3>核心原理</h3>
 * <ol>
 *   <li>遍历皮肤纹理指定区域的每个像素</li>
 *   <li>检查Alpha值：Alpha &ge; 128 的像素生成体素方块</li>
 *   <li>检查相邻4个方向的像素透明度，若相邻像素透明则生成侧面</li>
 *   <li>通过叉积和点积判断法线朝向，决定顶点绕序（正面/背面）</li>
 *   <li>纹理像素数据被缓存在 {@link #PIXEL_CACHE} 中以提升性能</li>
 * </ol>
 *
 * <h3>公有方法</h3>
 * 提供8个render方法，分别对应身体各部位的独立渲染和PlayerEntityModel上下文渲染：
 * renderHeadHat、renderJacket、renderPlayerJacket、renderRightSleeve、
 * renderPlayerRightSleeve、renderLeftSleeve、renderPlayerLeftSleeve、
 * renderRightPants、renderPlayerRightPants、renderLeftPants、renderPlayerLeftPants
 */
public final class SkinOuterLayerVoxelRenderer {
    /** 每个体素像素的厚度（单位：模型空间，1个模型单位为16像素），用于将2D像素拉伸为3D体素 */
    private static final float PIXEL_SIZE = 0.5F;
    private static final float HEAD_SIZE = 1.0f;
    /** 纹理像素缓存：按纹理Identifier缓存解析后的像素数据，避免重复读取和解析纹理 */
    private static final Map<Identifier, SkinPixels> PIXEL_CACHE = new HashMap<>();

    private SkinOuterLayerVoxelRenderer() {
    }

    /** 渲染头部hat层（独立物品上下文，纹理坐标：x=32, y=0） */
    public static boolean renderHeadHat(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 32, 0, -4.0F, -8.0F, -4.0F, 8, 8, 8);
    }

    /** 渲染躯干jacket层（独立物品上下文，纹理坐标：x=16, y=32） */
    public static boolean renderJacket(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 16, 32, -4.0F, -12.0F, -2.0F, 8, 12, 4);
    }

    /** 渲染躯干jacket层（PlayerEntityModel上下文，纹理坐标：x=16, y=32，Y偏移归零） */
    public static boolean renderPlayerJacket(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 16, 32, -4.0F, 0.0F, -2.0F, 8, 12, 4);
    }

    public static boolean renderPlayerUpperJacket(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboidSegment(matrices, vertices, texture, light, overlay, 16, 32,
                0, -4.0F, 0.0F, -2.0F, 8, 6, 4, true, false);
    }

    public static boolean renderPlayerLowerJacket(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboidSegment(matrices, vertices, texture, light, overlay, 16, 32,
                6, -4.0F, 0.0F, -2.0F, 8, 6, 4, false, true);
    }

    /** 渲染右手sleeve层（独立物品上下文，纹理坐标：x=40, y=32，slim时宽度为3否则为4） */
    public static boolean renderRightSleeve(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay, boolean slim) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 40, 32, -4.0F, -12.0F, -4.0F, slim ? 3 : 4, 12, 4);
    }

    /** 渲染右手sleeve层（PlayerEntityModel上下文，纹理坐标：x=40, y=32，位置微调） */
    public static boolean renderPlayerRightSleeve(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay, boolean slim) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 40, 32, slim ? -2.0F : -3.0F, -2.0F, -2.0F, slim ? 3 : 4, 12, 4);
    }

    public static boolean renderPlayerUpperRightSleeve(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay, boolean slim) {
        return renderCuboidSegment(matrices, vertices, texture, light, overlay, 40, 32,
                0, slim ? -2.0F : -3.0F, -2.0F, -2.0F, slim ? 3 : 4, 6, 4, true, false);
    }

    public static boolean renderPlayerLowerRightSleeve(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay, boolean slim) {
        return renderCuboidSegment(matrices, vertices, texture, light, overlay, 40, 32,
                6, slim ? -2.0F : -3.0F, 0.0F, -2.0F, slim ? 3 : 4, 6, 4, false, true);
    }

    /** 渲染左手sleeve层（独立物品上下文，纹理坐标：x=48, y=48，slim时宽度为3否则为4） */
    public static boolean renderLeftSleeve(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay, boolean slim) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 48, 48, -4.0F, -12.0F, -4.0F, slim ? 3 : 4, 12, 4);
    }

    /** 渲染左手sleeve层（PlayerEntityModel上下文，纹理坐标：x=48, y=48，位置微调） */
    public static boolean renderPlayerLeftSleeve(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay, boolean slim) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 48, 48, -1.0F, -2.0F, -2.0F, slim ? 3 : 4, 12, 4);
    }

    public static boolean renderPlayerUpperLeftSleeve(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay, boolean slim) {
        return renderCuboidSegment(matrices, vertices, texture, light, overlay, 48, 48,
                0, -1.0F, -2.0F, -2.0F, slim ? 3 : 4, 6, 4, true, false);
    }

    public static boolean renderPlayerLowerLeftSleeve(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay, boolean slim) {
        return renderCuboidSegment(matrices, vertices, texture, light, overlay, 48, 48,
                6, -1.0F, 0.0F, -2.0F, slim ? 3 : 4, 6, 4, false, true);
    }

    /** 渲染右腿pants层（独立物品上下文，纹理坐标：x=0, y=48） */
    public static boolean renderRightPants(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 0, 48, -2.0F, -12.0F, -2.0F, 4, 12, 4);
    }

    /** 渲染右腿pants层（PlayerEntityModel上下文，纹理坐标：x=0, y=32，Y偏移归零） */
    public static boolean renderPlayerRightPants(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 0, 32, -2.0F, 0.0F, -2.0F, 4, 12, 4);
    }

    public static boolean renderPlayerUpperRightPants(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboidSegment(matrices, vertices, texture, light, overlay, 0, 32,
                0, -2.0F, 0.0F, -2.0F, 4, 6, 4, true, false);
    }

    public static boolean renderPlayerLowerRightPants(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboidSegment(matrices, vertices, texture, light, overlay, 0, 32,
                6, -2.0F, 0.0F, -2.0F, 4, 6, 4, false, true);
    }

    /** 渲染左腿pants层（独立物品上下文，纹理坐标：x=0, y=32） */
    public static boolean renderLeftPants(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 0, 32, -4.0F, -12.0F, -4.0F, 4, 12, 4);
    }

    /** 渲染左腿pants层（PlayerEntityModel上下文，纹理坐标：x=0, y=48，Y偏移归零） */
    public static boolean renderPlayerLeftPants(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboid(matrices, vertices, texture, light, overlay, 0, 48, -2.0F, 0.0F, -2.0F, 4, 12, 4);
    }

    public static boolean renderPlayerUpperLeftPants(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboidSegment(matrices, vertices, texture, light, overlay, 0, 48,
                0, -2.0F, 0.0F, -2.0F, 4, 6, 4, true, false);
    }

    public static boolean renderPlayerLowerLeftPants(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay) {
        return renderCuboidSegment(matrices, vertices, texture, light, overlay, 0, 48,
                6, -2.0F, 0.0F, -2.0F, 4, 6, 4, false, true);
    }

    /**
     * 渲染长方体区域：从皮肤纹理指定区域读取像素，生成6个面的体素几何体。
     * 6个面依次为：底面、顶面、左面、前面、右面、后面。
     *
     * @param textureX 纹理起始X坐标（像素）
     * @param textureY 纹理起始Y坐标（像素）
     * @param x,y,z    长方体在模型空间中的起始坐标
     * @param width    长方体X方向尺寸（纹理像素宽度）
     * @param height   长方体Y方向尺寸（纹理像素高度）
     * @param depth    长方体Z方向尺寸（纹理像素深度）
     */
    private static boolean renderCuboid(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay,
                                     int textureX, int textureY, float x, float y, float z, int width, int height, int depth) {
        SkinPixels pixels = getPixels(texture);
        if (pixels == null) {
            return false;
        }

        // 底面 — 法线向下
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth, textureY,
                width, depth, new Point(x, y, z + depth), new Point(1.0F, 0.0F, 0.0F), new Point(0.0F, 0.0F, -1.0F), new Point(0.0F, -1.0F, 0.0F));
        // 顶面 — 法线向上
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width, textureY,
                width, depth, new Point(x, y + height, z + depth), new Point(1.0F, 0.0F, 0.0F), new Point(0.0F, 0.0F, -1.0F), new Point(0.0F, 1.0F, 0.0F));
        // 左面 — 法线向左
        renderFace(matrices, vertices, pixels, light, overlay, textureX, textureY + depth,
                depth, height, new Point(x, y, z + depth), new Point(0.0F, 0.0F, -1.0F), new Point(0.0F, 1.0F, 0.0F), new Point(-1.0F, 0.0F, 0.0F));
        // 前面 — 法线向前（-Z方向，即朝向玩家）
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth, textureY + depth,
                width, height, new Point(x, y, z), new Point(1.0F, 0.0F, 0.0F), new Point(0.0F, 1.0F, 0.0F), new Point(0.0F, 0.0F, -1.0F));
        // 右面 — 法线向右
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width, textureY + depth,
                depth, height, new Point(x + width, y, z), new Point(0.0F, 0.0F, 1.0F), new Point(0.0F, 1.0F, 0.0F), new Point(1.0F, 0.0F, 0.0F));
        // 后面 — 法线向后
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width + depth, textureY + depth,
                width, height, new Point(x + width, y, z + depth), new Point(-1.0F, 0.0F, 0.0F), new Point(0.0F, 1.0F, 0.0F), new Point(0.0F, 0.0F, 1.0F));
        return true;
    }

    private static boolean renderCuboidSegment(MatrixStack matrices, VertexConsumer vertices, Identifier texture, int light, int overlay,
                                               int textureX, int textureY, int textureYSegment,
                                               float x, float y, float z, int width, int height, int depth,
                                               boolean renderStartCap, boolean renderEndCap) {
        SkinPixels pixels = getPixels(texture);
        if (pixels == null) {
            return false;
        }

        if (renderStartCap) {
            renderFace(matrices, vertices, pixels, light, overlay, textureX + depth, textureY,
                    width, depth, new Point(x, y, z + depth), new Point(1.0F, 0.0F, 0.0F), new Point(0.0F, 0.0F, -1.0F), new Point(0.0F, -1.0F, 0.0F));
        }
        if (renderEndCap) {
            renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width, textureY,
                    width, depth, new Point(x, y + height, z + depth), new Point(1.0F, 0.0F, 0.0F), new Point(0.0F, 0.0F, -1.0F), new Point(0.0F, 1.0F, 0.0F));
        }

        int sideTextureY = textureY + depth + textureYSegment;
        renderFace(matrices, vertices, pixels, light, overlay, textureX, sideTextureY,
                depth, height, new Point(x, y, z + depth), new Point(0.0F, 0.0F, -1.0F), new Point(0.0F, 1.0F, 0.0F), new Point(-1.0F, 0.0F, 0.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth, sideTextureY,
                width, height, new Point(x, y, z), new Point(1.0F, 0.0F, 0.0F), new Point(0.0F, 1.0F, 0.0F), new Point(0.0F, 0.0F, -1.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width, sideTextureY,
                depth, height, new Point(x + width, y, z), new Point(0.0F, 0.0F, 1.0F), new Point(0.0F, 1.0F, 0.0F), new Point(1.0F, 0.0F, 0.0F));
        renderFace(matrices, vertices, pixels, light, overlay, textureX + depth + width + depth, sideTextureY,
                width, height, new Point(x + width, y, z + depth), new Point(-1.0F, 0.0F, 0.0F), new Point(0.0F, 1.0F, 0.0F), new Point(0.0F, 0.0F, 1.0F));
        return true;
    }

    /**
     * 渲染一个面：遍历指定区域内的每个像素，对不透明像素生成正面四边形和侧面四边形。
     * <p>
     * 对于每个不透明像素：
     * <ol>
     *   <li>计算内层4个顶点（inner00, inner10, inner11, inner01）和外层4个顶点（沿法线偏移PIXEL_SIZE）</li>
     *   <li>生成正面四边形（outer层，带完整纹理坐标）</li>
     *   <li>检查上/右/下/左4个相邻像素：若相邻像素透明，则生成侧面四边形连接内外层（使用中心UV做纯色填充）</li>
     * </ol>
     *
     * @param origin    面起始点（内层左下角）
     * @param uStep     U方向步进向量（纹理水平方向在模型空间的映射）
     * @param vStep     V方向步进向量（纹理垂直方向在模型空间的映射）
     * @param normal    面法线方向（指向外侧）
     */
    private static void renderFace(MatrixStack matrices, VertexConsumer vertices, SkinPixels pixels, int light, int overlay,
                                   int textureX, int textureY, int width, int height,
                                   Point origin, Point uStep, Point vStep, Point normal) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int pixelX = textureX + col;
                int pixelY = textureY + row;
                // 仅渲染不透明像素（Alpha >= 128）
                if (!pixels.isOpaque(pixelX, pixelY)) {
                    continue;
                }

                // 计算内层4个顶点（在模型表面）
                Point inner00 = origin.add(uStep.multiply(col)).add(vStep.multiply(row));
                Point inner10 = inner00.add(uStep);
                Point inner11 = inner10.add(vStep);
                Point inner01 = inner00.add(vStep);
                // 外层顶点：沿法线方向偏移一个PIXEL_SIZE
                Point offset = normal.multiply(PIXEL_SIZE);
                Point outer00 = inner00.add(offset);
                Point outer10 = inner10.add(offset);
                Point outer11 = inner11.add(offset);
                Point outer01 = inner01.add(offset);

                // 正面四边形（外层，带完整纹理UV）
                float u0 = pixelX / (float) pixels.width();
                float v0 = pixelY / (float) pixels.height();
                float u1 = (pixelX + 1) / (float) pixels.width();
                float v1 = (pixelY + 1) / (float) pixels.height();
                emitTexturedQuad(matrices, vertices, light, overlay, outer00, outer10, outer11, outer01, normal, u0, v0, u1, v1);

                // 检查相邻4个方向的像素，若相邻像素透明则生成侧面（连接内层和外层的四边形）
                float centerU = (pixelX + 0.5F) / pixels.width();
                float centerV = (pixelY + 0.5F) / pixels.height();
                // 上方相邻像素
                if (!isFacePixelOpaque(pixels, textureX, textureY, width, height, col, row - 1)) {
                    emitSolidUvQuad(matrices, vertices, light, overlay, outer00, outer10, inner10, inner00, vStep.negate(), centerU, centerV);
                }
                // 右侧相邻像素
                if (!isFacePixelOpaque(pixels, textureX, textureY, width, height, col + 1, row)) {
                    emitSolidUvQuad(matrices, vertices, light, overlay, outer10, outer11, inner11, inner10, uStep, centerU, centerV);
                }
                // 下方相邻像素
                if (!isFacePixelOpaque(pixels, textureX, textureY, width, height, col, row + 1)) {
                    emitSolidUvQuad(matrices, vertices, light, overlay, outer11, outer01, inner01, inner11, vStep, centerU, centerV);
                }
                // 左侧相邻像素
                if (!isFacePixelOpaque(pixels, textureX, textureY, width, height, col - 1, row)) {
                    emitSolidUvQuad(matrices, vertices, light, overlay, outer01, outer00, inner00, inner01, uStep.negate(), centerU, centerV);
                }
            }
        }
    }

    /** 检查面上指定行列的像素是否为不透明（边界外视为透明） */
    private static boolean isFacePixelOpaque(SkinPixels pixels, int textureX, int textureY, int width, int height, int col, int row) {
        if (col < 0 || row < 0 || col >= width || row >= height) {
            return false;
        }
        return pixels.isOpaque(textureX + col, textureY + row);
    }

    /**
     * 输出带完整纹理坐标的四边形。
     * 通过叉积判断p0→p1和p0→p2的朝向与法线的关系：
     * 若与法线方向一致则为正面（逆时针绕序p0/p1/p2/p3），否则为背面（调换为p0/p3/p2/p1）。
     */
    private static void emitTexturedQuad(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                                         Point p0, Point p1, Point p2, Point p3, Point normal,
                                         float u0, float v0, float u1, float v1) {
        // 叉积与法线的点积判断正面/背面，决定顶点绕序
        if (p1.subtract(p0).cross(p2.subtract(p0)).dot(normal) >= 0.0F) {
            emitVertex(matrices, vertices, light, overlay, p0, normal, u0, v0);
            emitVertex(matrices, vertices, light, overlay, p1, normal, u1, v0);
            emitVertex(matrices, vertices, light, overlay, p2, normal, u1, v1);
            emitVertex(matrices, vertices, light, overlay, p3, normal, u0, v1);
        } else {
            emitVertex(matrices, vertices, light, overlay, p0, normal, u0, v0);
            emitVertex(matrices, vertices, light, overlay, p3, normal, u0, v1);
            emitVertex(matrices, vertices, light, overlay, p2, normal, u1, v1);
            emitVertex(matrices, vertices, light, overlay, p1, normal, u1, v0);
        }
    }

    /** 输出侧面四边形（纯色UV填充，所有顶点共享同一个纹理坐标，用于连接内外层的侧面） */
    private static void emitSolidUvQuad(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                                        Point p0, Point p1, Point p2, Point p3, Point normal, float u, float v) {
        if (p1.subtract(p0).cross(p2.subtract(p0)).dot(normal) >= 0.0F) {
            emitVertex(matrices, vertices, light, overlay, p0, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p1, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p2, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p3, normal, u, v);
        } else {
            emitVertex(matrices, vertices, light, overlay, p0, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p3, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p2, normal, u, v);
            emitVertex(matrices, vertices, light, overlay, p1, normal, u, v);
        }
    }

    /** 输出单个顶点：将Point坐标除以16转换为模型空间坐标，并设置颜色/纹理/光照/法线 */
    private static void emitVertex(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, Point point, Point normal, float u, float v) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        // Point内部坐标单位是1像素=1单位，需除以16转为模型空间（1模型单位=16像素）
        vertices.vertex(positionMatrix, point.x() / 16.0F, point.y() / 16.0F, point.z() / 16.0F)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(normal.x(), normal.y(), normal.z());
    }

    /**
     * 获取皮肤纹理的像素数据（带缓存）。
     * 优先从TextureManager中查找已加载的纹理，找不到时回退到ResourceManager加载。
     * 纹理尺寸需至少64x64（标准皮肤纹理最小尺寸），否则返回null。
     */
    private static SkinPixels getPixels(Identifier textureId) {
        SkinPixels cached = PIXEL_CACHE.get(textureId);
        if (cached != null) {
            return cached;
        }

        NativeImage ownedImage = null;
        NativeImage image = null;
        try {
            AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
            if (texture instanceof NativeImageBackedTexture nativeTexture) {
                image = nativeTexture.getImage();
            }
        } catch (Exception ignored) {
            // 纹理管理器加载失败，尝试从资源管理器回退加载
        }

        if (image == null) {
            try {
                var resource = MinecraftClient.getInstance().getResourceManager().getResource(textureId);
                if (resource.isPresent()) {
                    ownedImage = NativeImage.read(resource.get().getInputStream());
                    image = ownedImage;
                }
            } catch (IOException ignored) {
                return null;
            }
        }

        // 纹理尺寸不足64x64时视为无效（标准皮肤纹理最小为64x64）
        if (image == null || image.getWidth() < 64 || image.getHeight() < 64) {
            if (ownedImage != null) {
                ownedImage.close();
            }
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        // 将NativeImage提取为ARGB整型数组以便快速访问
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                argb[y * width + x] = image.getColorArgb(x, y);
            }
        }

        if (ownedImage != null) {
            ownedImage.close();
        }

        SkinPixels pixels = new SkinPixels(width, height, argb);
        PIXEL_CACHE.put(textureId, pixels);
        return pixels;
    }

    /** 皮肤像素数据记录，存储纹理尺寸和ARGB像素数组 */
    private record SkinPixels(int width, int height, int[] argb) {
        /** 判断指定坐标的像素是否为不透明（Alpha >= 128） */
        boolean isOpaque(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return false;
            }
            // 右移24位提取Alpha通道，与0xFF按位与后检查是否>=128
            return ((argb[y * width + x] >> 24) & 0xFF) >= 128;
        }
    }

    /** 三维点记录，提供向量运算方法（加、减、乘、取反、叉积、点积）用于体素几何计算 */
    private record Point(float x, float y, float z) {
        Point add(Point other) {
            return new Point(x + other.x, y + other.y, z + other.z);
        }

        Point subtract(Point other) {
            return new Point(x - other.x, y - other.y, z - other.z);
        }

        Point multiply(float scalar) {
            return new Point(x * scalar, y * scalar, z * scalar);
        }

        Point negate() {
            return new Point(-x, -y, -z);
        }

        Point cross(Point other) {
            return new Point(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        float dot(Point other) {
            return x * other.x + y * other.y + z * other.z;
        }

    }
}
