package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.kuilunfuzhe.monvhua.item.cosmic_box.CosmicBoxItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CosmicBoxBlockEntityRenderer implements BlockEntityRenderer<CosmicBoxBlockEntity> {
    private static final float MAIN_BEAM_RADIUS = 0.18F;
    private static final float MAIN_BEAM_GLOW_RADIUS = 0.38F;
    private static final float TARGET_BEAM_RADIUS = MAIN_BEAM_RADIUS * 0.5F;
    private static final float SUB_BEAM_RADIUS = 0.10F;
    private static final float SUB_BEAM_GLOW_RADIUS = 0.24F;
    private static final float CHILD_BEAM_RADIUS = 0.05F;
    private static final float CHILD_BEAM_GLOW_RADIUS = 0.10F;
    private static final double SUB_BEAM_LENGTH = 2.0D;
    private static final double CHILD_BEAM_LENGTH = 0.2D;
    private static final double SUB_ORBIT_MAX_DISTANCE = 8.0D;
    private static final double CHILD_ORBIT_MAX_DISTANCE = 0.5D;
    private static final int SUB_BEAM_COUNT = 6;
    private static final int CHILD_BEAM_COUNT = 4;
    private static final int CYLINDER_SIDES = 14;
    private static final double FIXED_SUB_BEAM_TARGET_HEIGHT = 100.0D;
    private static final double ANIMATION_TICKS = 280.0D;
    private static final double ANIMATION_INTERVAL_TICKS = 1000.0D;
    private static final double PHASE_ONE_END = 40.0D;
    private static final double PHASE_TWO_END = 80.0D;
    private static final double MAX_SPEED_START = 120.0D;
    private static final double RECOVERY_START = 200.0D;
    private static final int DOOR_CRACK_POINTS = 36;
    private static final int DOOR_FRAGMENT_COUNT = 18;
    private static final int DOOR_STAR_COUNT = 44;
    private static final Map<BlockPos, Double> DOOR_OPEN_START_TICKS = new HashMap<>();

    @Override
    public void render(CosmicBoxBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        boolean irisShaderPack = CosmicBoxIrisCompat.isShaderPackInUse();
        CubeFormat cubeFormat = irisShaderPack ? CubeFormat.TEXTURED_FULLBRIGHT : CubeFormat.POSITION;
        VertexConsumer vertices = vertexConsumers.getBuffer(CosmicBoxRenderLayers.cosmicBoxForCurrentShaders());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        renderCube(matrix, vertices, cubeFormat);
        if (entity.getCachedState().getBlock() == CosmicBoxItems.COSMIC_BOX_DOOR_BLOCK) {
            renderDoorCrack(entity, matrix, vertexConsumers, cameraPos, tickDelta);
        }
        renderBeams(entity, matrix, vertexConsumers, tickDelta);
    }

    @Override
    public boolean rendersOutsideBoundingBox() {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 1024;
    }

    private static void renderBeams(CosmicBoxBlockEntity box, Matrix4f matrix, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (box.getCachedState().getBlock() != CosmicBoxItems.COSMIC_BOX_BLOCK) {
            return;
        }
        if (!box.isBeamActive() || box.getWorld() == null || box.getTargets().isEmpty()) {
            return;
        }
        if (!CosmicBoxClientState.canSeeBeam()) {
            return;
        }

        BlockPos blockPos = box.getPos();
        double topY = box.getWorld().getBottomY() + box.getWorld().getHeight();
        double localTopY = Math.max(1.0D, topY - blockPos.getY());
        Vec3d base = new Vec3d(0.5D, 1.0D, 0.5D);
        Vec3d mainTop = new Vec3d(0.5D, localTopY, 0.5D);
        Vec3d fixedSubTarget = base.add(0.0D, FIXED_SUB_BEAM_TARGET_HEIGHT, 0.0D);
        BeamFormat beamFormat = CosmicBoxIrisCompat.isShaderPackInUse() ? BeamFormat.BEACON : BeamFormat.POSITION;
        VertexConsumer whiteVertices = vertexConsumers.getBuffer(
                beamFormat == BeamFormat.BEACON ? CosmicBoxRenderLayers.cosmicBeamForCurrentShaders() : CosmicBoxRenderLayers.cosmicBeamWhite()
        );
        VertexConsumer styleVertices = vertexConsumers.getBuffer(
                beamFormat == BeamFormat.BEACON ? CosmicBoxRenderLayers.cosmicBeamForCurrentShaders() : beamLayer(box.getBeamStyle())
        );
        double animationTick = animationTick(box, tickDelta);
        double cycle = animationTick % (ANIMATION_TICKS + ANIMATION_INTERVAL_TICKS);

        renderGlowingBeam(matrix, whiteVertices, beamFormat, base, mainTop, MAIN_BEAM_RADIUS, MAIN_BEAM_GLOW_RADIUS);
        renderSubBeamAnimation(matrix, whiteVertices, styleVertices, beamFormat, base, fixedSubTarget, box.getBeamStyle(), cycle, animationTick);

        if (shouldRenderGuidanceBeam(cycle)) {
            for (CosmicBoxBlockEntity.TargetRef targetRef : box.getTargets()) {
                Entity target = findTarget(targetRef.uuid());
                if (target == null) {
                    continue;
                }

                Vec3d targetPos = target.getPos().add(0.0D, target.getHeight() * 0.5D + 5.0D, 0.0D)
                        .subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                renderBeam(matrix, whiteVertices, beamFormat, mainTop, targetPos, TARGET_BEAM_RADIUS);
            }
        }
    }

    private static void renderSubBeamAnimation(Matrix4f matrix, VertexConsumer glowVertices, VertexConsumer coreVertices,
                                               BeamFormat beamFormat, Vec3d base, Vec3d fixedSubTarget, CosmicBoxBeamStyle style,
                                               double cycle, double animationTick) {
        if (cycle >= ANIMATION_TICKS) {
            return;
        }

        double seconds = animationTick / 20.0D;
        double distance = subBeamDistance(cycle);
        if (distance <= 0.001D) {
            return;
        }

        for (int i = 0; i < SUB_BEAM_COUNT; i++) {
            double angle = seconds * 0.45D + i * Math.PI * 2.0D / SUB_BEAM_COUNT;
            Vec3d radial = new Vec3d(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3d bottom = base.add(radial.multiply(distance));
            Vec3d direction = subBeamDirection(bottom, fixedSubTarget, cycle);
            Vec3d center = bottom.add(direction.multiply(SUB_BEAM_LENGTH * 0.5D));
            double selfRoll = seconds * 1.4D + i * Math.PI * 0.5D;

            renderGlowingCylinder(matrix, glowVertices, coreVertices, beamFormat, center, direction, SUB_BEAM_LENGTH,
                    SUB_BEAM_RADIUS, SUB_BEAM_GLOW_RADIUS, selfRoll, style);

            if (cycle >= PHASE_TWO_END && cycle < ANIMATION_TICKS) {
                renderChildBeams(matrix, glowVertices, coreVertices, beamFormat, bottom, direction, cycle, seconds, i, style);
            }

            if (cycle >= MAX_SPEED_START && cycle < ANIMATION_TICKS) {
                Vec3d subTop = bottom.add(direction.multiply(SUB_BEAM_LENGTH));
                renderExtendingBeam(matrix, glowVertices, beamFormat, subTop, fixedSubTarget, cycle);
            }
        }
    }

    private static double animationTick(CosmicBoxBlockEntity box, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return 0.0D;
        }
        return Math.max(0.0D, client.world.getTime() + tickDelta - box.getBeamActiveStartTick());
    }

    private static boolean shouldRenderGuidanceBeam(double cycle) {
        return cycle < PHASE_TWO_END || cycle >= RECOVERY_START;
    }

    private static double subBeamDistance(double cycle) {
        if (cycle < PHASE_ONE_END) {
            return SUB_ORBIT_MAX_DISTANCE * smooth(cycle / PHASE_ONE_END);
        }
        if (cycle < RECOVERY_START) {
            return SUB_ORBIT_MAX_DISTANCE;
        }
        return SUB_ORBIT_MAX_DISTANCE * (1.0D - smooth((cycle - RECOVERY_START) / (ANIMATION_TICKS - RECOVERY_START)));
    }

    private static Vec3d subBeamDirection(Vec3d bottom, Vec3d fixedSubTarget, double cycle) {
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d target = fixedSubTarget.subtract(bottom).normalize();
        if (cycle < PHASE_ONE_END) {
            return up;
        }
        if (cycle < PHASE_TWO_END) {
            double t = smooth((cycle - PHASE_ONE_END) / (PHASE_TWO_END - PHASE_ONE_END));
            return lerp(up, target, t).normalize();
        }
        return target;
    }

    private static void renderChildBeams(Matrix4f matrix, VertexConsumer glowVertices, VertexConsumer coreVertices,
                                         BeamFormat beamFormat, Vec3d subBottom, Vec3d subDirection, double cycle, double seconds,
                                         int subIndex, CosmicBoxBeamStyle style) {
        double childDistance = childBeamDistance(cycle);
        if (childDistance <= 0.001D) {
            return;
        }

        Basis basis = basisFor(subDirection);
        double elapsed = Math.max(0.0D, cycle - PHASE_TWO_END) / 20.0D;
        double speedRamp = smooth(Math.min(1.0D, (cycle - PHASE_TWO_END) / (MAX_SPEED_START - PHASE_TWO_END)));
        double childOrbitAngle = elapsed * (0.8D + 9.2D * speedRamp) + subIndex * 0.37D;
        if (cycle >= MAX_SPEED_START) {
            childOrbitAngle = elapsed * 10.0D + subIndex * 0.37D;
        }

        for (int j = 0; j < CHILD_BEAM_COUNT; j++) {
            double angle = childOrbitAngle + j * Math.PI * 0.5D;
            Vec3d offset = basis.side().multiply(Math.cos(angle) * childDistance)
                    .add(basis.other().multiply(Math.sin(angle) * childDistance));
            Vec3d childBottom = subBottom.add(offset);
            Vec3d childCenter = childBottom.add(subDirection.multiply(CHILD_BEAM_LENGTH * 0.5D));
            renderGlowingCylinder(matrix, glowVertices, coreVertices, beamFormat, childCenter, subDirection, CHILD_BEAM_LENGTH,
                    CHILD_BEAM_RADIUS, CHILD_BEAM_GLOW_RADIUS, angle, style);
        }
    }

    private static double childBeamDistance(double cycle) {
        if (cycle < PHASE_TWO_END) {
            return 0.0D;
        }
        if (cycle < 120.0D) {
            return CHILD_ORBIT_MAX_DISTANCE * smooth((cycle - PHASE_TWO_END) / 40.0D);
        }
        if (cycle < RECOVERY_START) {
            return CHILD_ORBIT_MAX_DISTANCE;
        }
        return CHILD_ORBIT_MAX_DISTANCE * (1.0D - smooth((cycle - RECOVERY_START) / (ANIMATION_TICKS - RECOVERY_START)));
    }

    private static void renderGlowingCylinder(Matrix4f matrix, VertexConsumer glowVertices, VertexConsumer coreVertices,
                                              BeamFormat beamFormat, Vec3d center, Vec3d direction, double length, float coreRadius,
                                              float glowRadius, double roll, CosmicBoxBeamStyle style) {
        renderCylinder(matrix, glowVertices, beamFormat, center, direction, length, glowRadius, roll);
        renderCylinder(matrix, glowVertices, beamFormat, center, direction, length, glowRadius * 0.72D, roll + 0.11D);
        renderCylinder(matrix, glowVertices, beamFormat, center, direction, length, glowRadius * 0.48D, roll + 0.23D);
        if (style == CosmicBoxBeamStyle.BEACON_RAINBOW) {
            renderCylinder(matrix, coreVertices, beamFormat, center, direction, length, coreRadius * 1.35D, roll);
            renderCylinder(matrix, coreVertices, beamFormat, center, direction, length, coreRadius * 0.72D, roll + 0.2D);
        } else {
            renderCylinder(matrix, coreVertices, beamFormat, center, direction, length, coreRadius, roll);
        }
    }

    private static void renderCylinder(Matrix4f matrix, VertexConsumer vertices, Vec3d center, Vec3d direction,
                                       double length, double radius, double roll) {
        renderCylinder(matrix, vertices, BeamFormat.POSITION, center, direction, length, radius, roll);
    }

    private static void renderCylinder(Matrix4f matrix, VertexConsumer vertices, BeamFormat beamFormat,
                                       Vec3d center, Vec3d direction, double length, double radius, double roll) {
        Vec3d axis = direction.normalize();
        Basis basis = basisFor(axis);
        Vec3d start = center.subtract(axis.multiply(length * 0.5D));
        Vec3d end = center.add(axis.multiply(length * 0.5D));

        Vec3d[] startRing = new Vec3d[CYLINDER_SIDES];
        Vec3d[] endRing = new Vec3d[CYLINDER_SIDES];
        Vec3d[] normals = new Vec3d[CYLINDER_SIDES];
        for (int i = 0; i < CYLINDER_SIDES; i++) {
            double angle = roll + Math.PI * 2.0D * i / CYLINDER_SIDES;
            Vec3d offset = basis.side().multiply(Math.cos(angle) * radius)
                    .add(basis.other().multiply(Math.sin(angle) * radius));
            startRing[i] = start.add(offset);
            endRing[i] = end.add(offset);
            normals[i] = offset.normalize();
        }

        for (int i = 0; i < CYLINDER_SIDES; i++) {
            int next = (i + 1) % CYLINDER_SIDES;
            float u1 = (float) i / CYLINDER_SIDES;
            float u2 = (float) next / CYLINDER_SIDES;
            beamVertex(matrix, vertices, beamFormat, startRing[i], u1, 1.0F, normals[i]);
            beamVertex(matrix, vertices, beamFormat, startRing[next], u2, 1.0F, normals[next]);
            beamVertex(matrix, vertices, beamFormat, endRing[next], u2, 0.0F, normals[next]);
            beamVertex(matrix, vertices, beamFormat, endRing[i], u1, 0.0F, normals[i]);
        }
    }

    private static Basis basisFor(Vec3d direction) {
        Vec3d axis = direction.normalize();
        Vec3d reference = Math.abs(axis.y) > 0.92D ? new Vec3d(1.0D, 0.0D, 0.0D) : new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d side = axis.crossProduct(reference).normalize();
        Vec3d other = axis.crossProduct(side).normalize();
        return new Basis(side, other);
    }

    private static double smooth(double value) {
        double t = Math.max(0.0D, Math.min(1.0D, value));
        return t * t * (3.0D - 2.0D * t);
    }

    private static Vec3d lerp(Vec3d from, Vec3d to, double t) {
        return from.multiply(1.0D - t).add(to.multiply(t));
    }

    private static RenderLayer beamLayer(CosmicBoxBeamStyle style) {
        if (style == CosmicBoxBeamStyle.BEACON_RAINBOW) {
            return CosmicBoxRenderLayers.cosmicBeamRainbow();
        }
        return CosmicBoxRenderLayers.cosmicBox();
    }

    private static Entity findTarget(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }
        return client.world.getEntity(uuid);
    }

    private static void renderGlowingBeam(Matrix4f matrix, VertexConsumer vertices, Vec3d start, Vec3d end,
                                          float coreRadius, float glowRadius) {
        renderGlowingBeam(matrix, vertices, BeamFormat.POSITION, start, end, coreRadius, glowRadius);
    }

    private static void renderGlowingBeam(Matrix4f matrix, VertexConsumer vertices, BeamFormat beamFormat,
                                          Vec3d start, Vec3d end, float coreRadius, float glowRadius) {
        renderBeam(matrix, vertices, beamFormat, start, end, glowRadius);
        renderBeam(matrix, vertices, beamFormat, start, end, glowRadius * 0.72F);
        renderBeam(matrix, vertices, beamFormat, start, end, glowRadius * 0.48F);
        renderBeam(matrix, vertices, beamFormat, start, end, coreRadius);
        renderBeam(matrix, vertices, beamFormat, start, end, coreRadius * 0.55F);
    }

    private static void renderExtendingBeam(Matrix4f matrix, VertexConsumer vertices, Vec3d start, Vec3d target, double cycle) {
        renderExtendingBeam(matrix, vertices, BeamFormat.POSITION, start, target, cycle);
    }

    private static void renderExtendingBeam(Matrix4f matrix, VertexConsumer vertices, BeamFormat beamFormat,
                                            Vec3d start, Vec3d target, double cycle) {
        Vec3d axis = target.subtract(start);
        if (axis.lengthSquared() < 0.0001D) {
            return;
        }

        if (cycle < RECOVERY_START) {
            double extend = smooth((cycle - MAX_SPEED_START) / (RECOVERY_START - MAX_SPEED_START));
            Vec3d end = start.add(axis.multiply(extend));
            renderGlowingBeam(matrix, vertices, beamFormat, start, end, 0.035F, 0.075F);
            return;
        }

        double retract = smooth((cycle - RECOVERY_START) / (ANIMATION_TICKS - RECOVERY_START));
        Vec3d visibleStart = start.add(axis.multiply(retract));
        if (visibleStart.squaredDistanceTo(target) > 0.0001D) {
            renderGlowingBeam(matrix, vertices, beamFormat, visibleStart, target, 0.035F, 0.075F);
        }
    }

    private static void renderBeam(Matrix4f matrix, VertexConsumer vertices, Vec3d start, Vec3d end, float radius) {
        renderBeam(matrix, vertices, BeamFormat.POSITION, start, end, radius);
    }

    private static void renderBeam(Matrix4f matrix, VertexConsumer vertices, BeamFormat beamFormat,
                                   Vec3d start, Vec3d end, float radius) {
        Vec3d axis = end.subtract(start);
        if (axis.lengthSquared() < 0.0001D) {
            return;
        }

        Vec3d direction = axis.normalize();
        Basis basis = basisFor(direction);
        Vec3d[] offsets = new Vec3d[] {
                basis.side().multiply(radius),
                basis.other().multiply(radius),
                basis.side().negate().multiply(radius),
                basis.other().negate().multiply(radius)
        };

        for (int i = 0; i < offsets.length; i++) {
            Vec3d a = offsets[i];
            Vec3d b = offsets[(i + 1) % offsets.length];
            Vec3d normal = a.add(b).normalize();
            beamVertex(matrix, vertices, beamFormat, start.add(a), 0.0F, 1.0F, normal);
            beamVertex(matrix, vertices, beamFormat, start.add(b), 1.0F, 1.0F, normal);
            beamVertex(matrix, vertices, beamFormat, end.add(b), 1.0F, 0.0F, normal);
            beamVertex(matrix, vertices, beamFormat, end.add(a), 0.0F, 0.0F, normal);
        }
    }

    private static void renderCube(Matrix4f matrix, VertexConsumer vertices) {
        renderCube(matrix, vertices, CubeFormat.POSITION);
    }

    private static void renderCube(Matrix4f matrix, VertexConsumer vertices, CubeFormat cubeFormat) {
        quad(matrix, vertices, cubeFormat, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        quad(matrix, vertices, cubeFormat, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F);
        quad(matrix, vertices, cubeFormat, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        quad(matrix, vertices, cubeFormat, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, -1.0F, 0.0F);
        quad(matrix, vertices, cubeFormat, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F);
        quad(matrix, vertices, cubeFormat, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F, -1.0F, 0.0F, 0.0F);
    }

    private static void quad(Matrix4f matrix, VertexConsumer vertices,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4) {
        quad(matrix, vertices, CubeFormat.POSITION, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, 0.0F, 1.0F, 0.0F);
    }

    private static void quad(Matrix4f matrix, VertexConsumer vertices, CubeFormat cubeFormat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float normalX, float normalY, float normalZ) {
        cubeVertex(matrix, vertices, cubeFormat, x1, y1, z1, 0.0F, 1.0F, normalX, normalY, normalZ);
        cubeVertex(matrix, vertices, cubeFormat, x2, y2, z2, 1.0F, 1.0F, normalX, normalY, normalZ);
        cubeVertex(matrix, vertices, cubeFormat, x3, y3, z3, 1.0F, 0.0F, normalX, normalY, normalZ);
        cubeVertex(matrix, vertices, cubeFormat, x4, y4, z4, 0.0F, 0.0F, normalX, normalY, normalZ);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer vertices, Vec3d pos) {
        vertices.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z);
    }

    private static void beamVertex(Matrix4f matrix, VertexConsumer vertices, BeamFormat beamFormat,
                                   Vec3d pos, float u, float v, Vec3d normal) {
        if (beamFormat == BeamFormat.BEACON) {
            vertices.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                    .color(255, 255, 255, 255)
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal((float) normal.x, (float) normal.y, (float) normal.z);
            return;
        }

        vertex(matrix, vertices, pos);
    }

    private static void cubeVertex(Matrix4f matrix, VertexConsumer vertices, CubeFormat cubeFormat,
                                   float x, float y, float z, float u, float v,
                                   float normalX, float normalY, float normalZ) {
        if (cubeFormat == CubeFormat.TEXTURED_FULLBRIGHT) {
            vertices.vertex(matrix, x, y, z)
                    .color(255, 255, 255, 255)
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal(normalX, normalY, normalZ);
            return;
        }

        vertices.vertex(matrix, x, y, z);
    }

    private static void renderDoorCrack(CosmicBoxBlockEntity entity, Matrix4f matrix,
                                        VertexConsumerProvider vertexConsumers, Vec3d cameraPos, float tickDelta) {
        VertexConsumer vertices = vertexConsumers.getBuffer(CosmicBoxRenderLayers.cosmicDoorCrack());
        Vec3d worldCenter = new Vec3d(entity.getPos().getX() + 0.5D, entity.getPos().getY() + 2.65D, entity.getPos().getZ() + 0.5D);
        Vec3d toCamera = cameraPos.subtract(worldCenter);
        Vec3d forward = new Vec3d(toCamera.x, 0.0D, toCamera.z);
        if (forward.lengthSquared() < 0.0001D) {
            forward = new Vec3d(0.0D, 0.0D, 1.0D);
        } else {
            forward = forward.normalize();
        }

        Vec3d right = new Vec3d(forward.z, 0.0D, -forward.x).normalize();
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);
        double slant = Math.toRadians(15.0D);
        Vec3d major = up.multiply(Math.cos(slant)).add(right.multiply(Math.sin(slant))).multiply(1.5D);
        Vec3d minor = right.multiply(Math.cos(slant)).subtract(up.multiply(Math.sin(slant))).multiply(0.42D);
        Vec3d center = new Vec3d(0.5D, 2.65D, 0.5D);

        double time = doorAnimationTime(entity, tickDelta);
        renderIrregularCrack(matrix, vertices, center, major, minor, time, forward);
    }

    private static double doorAnimationTime(CosmicBoxBlockEntity entity, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return 0.0D;
        }
        double now = client.world.getTime() + tickDelta;
        if ((client.world.getTime() & 255L) == 0L) {
            DOOR_OPEN_START_TICKS.entrySet().removeIf(entry -> now - entry.getValue() > 1200.0D);
        }
        double start = DOOR_OPEN_START_TICKS.computeIfAbsent(entity.getPos().toImmutable(), ignored -> now);
        return Math.min(240.0D, now - start);
    }

    private static void renderIrregularCrack(Matrix4f matrix, VertexConsumer edgeVertices,
                                             Vec3d center, Vec3d major, Vec3d minor, double time, Vec3d normal) {
        Vec3d animatedCenter = center;
        double age = doorOpenAge(time);
        double sphereProgress = smooth(age / 40.0D);
        double curveProgress = smooth((age - 40.0D) / 60.0D);
        double unfoldProgress = smooth((age - 100.0D) / 100.0D);
        boolean stable = age >= 200.0D;
        double sphereFade = age < 200.0D ? 1.0D : 1.0D - smooth((age - 200.0D) / 40.0D);

        Vec3d[] inner = new Vec3d[DOOR_CRACK_POINTS];
        Vec3d[] outer = new Vec3d[DOOR_CRACK_POINTS];
        Vec3d[] teeth = new Vec3d[DOOR_CRACK_POINTS];
        Vec3d majorDir = major.normalize();
        Vec3d minorDir = minor.normalize();
        for (int i = 0; i < DOOR_CRACK_POINTS; i++) {
            double angle = Math.PI * 2.0D * i / DOOR_CRACK_POINTS;
            double living = 1.0D + 0.035D * Math.sin(time * 0.043D + i * 1.91D)
                    + 0.026D * Math.cos(time * 0.067D + i * 4.37D);
            double jitter = (0.90D + 0.12D * Math.sin(i * 2.17D) + 0.08D * Math.cos(i * 5.31D)) * living;
            double tooth = 1.18D + 0.18D * Math.max(0.0D, Math.sin(i * 3.43D + 1.1D))
                    + 0.12D * Math.max(0.0D, Math.cos(i * 7.19D - 0.4D))
                    + 0.055D * Math.sin(time * 0.051D + i * 2.73D);
            Vec3d radial = major.multiply(Math.sin(angle)).add(minor.multiply(Math.cos(angle)));
            Vec3d waveOffset = doorLocalWaveOffset(radial, major, minor, time);
            Vec3d edgeDrift = majorDir.multiply(Math.sin(time * 0.038D + i * 2.41D) * 0.014D)
                    .add(minorDir.multiply(Math.cos(time * 0.052D + i * 3.17D) * 0.018D));
            inner[i] = animatedCenter.add(radial.multiply(jitter)).add(waveOffset).add(edgeDrift.multiply(0.45D));
            outer[i] = animatedCenter.add(radial.multiply(jitter * 1.09D)).add(waveOffset.multiply(1.10D)).add(edgeDrift);
            teeth[i] = animatedCenter.add(radial.multiply(jitter * tooth)).add(waveOffset.multiply(1.24D)).add(edgeDrift.multiply(1.45D));
        }

        if (sphereFade > 0.0D) {
            renderOpeningSphere(matrix, edgeVertices, animatedCenter.add(normal.multiply(0.010D)), major, minor, sphereProgress, sphereFade);
        }
        if (age >= 40.0D && age < 100.0D) {
            renderOpeningCrackLines(matrix, edgeVertices, animatedCenter, inner, curveProgress);
        }

        for (int i = 0; i < DOOR_CRACK_POINTS; i++) {
            int next = (i + 1) % DOOR_CRACK_POINTS;
            double sectorProgress = stable ? 1.0D : unfoldProgressForSector(i, unfoldProgress);
            if (sectorProgress <= 0.0D) {
                continue;
            }

            Vec3d a = animatedCenter.add(inner[i].subtract(animatedCenter).multiply(sectorProgress));
            Vec3d b = animatedCenter.add(inner[next].subtract(animatedCenter).multiply(sectorProgress));
            Vec3d c = animatedCenter.add(outer[next].subtract(animatedCenter).multiply(sectorProgress));
            Vec3d d = animatedCenter.add(outer[i].subtract(animatedCenter).multiply(sectorProgress));
            Vec3d ta = animatedCenter.add(teeth[i].subtract(animatedCenter).multiply(sectorProgress));
            Vec3d tb = animatedCenter.add(teeth[next].subtract(animatedCenter).multiply(sectorProgress));

            colorVertex(matrix, edgeVertices, animatedCenter, 0, 0, 0, 255);
            colorVertex(matrix, edgeVertices, a, 0, 0, 0, 255);
            colorVertex(matrix, edgeVertices, b, 0, 0, 0, 255);
            colorVertex(matrix, edgeVertices, animatedCenter, 0, 0, 0, 255);

            colorVertex(matrix, edgeVertices, a, 0, 0, 0, 245);
            colorVertex(matrix, edgeVertices, b, 0, 0, 0, 245);
            colorVertex(matrix, edgeVertices, c, 8, 0, 15, 184);
            colorVertex(matrix, edgeVertices, d, 8, 0, 15, 184);

            colorVertex(matrix, edgeVertices, d, 0, 0, 0, 230);
            colorVertex(matrix, edgeVertices, c, 0, 0, 0, 230);
            colorVertex(matrix, edgeVertices, tb, 0, 0, 0, 205);
            colorVertex(matrix, edgeVertices, ta, 0, 0, 0, 205);
        }

        renderDoorStars(matrix, edgeVertices, animatedCenter.add(normal.multiply(0.014D)), major, minor, normal, time, stable ? 1.0D : unfoldProgress);
        if (age >= 200.0D) {
            renderFloatingCrackFragments(matrix, edgeVertices, animatedCenter, major, minor, time, age);
        }
    }

    private static double doorOpenAge(double time) {
        return Math.min(240.0D, time);
    }

    private static double unfoldProgressForSector(int index, double unfoldProgress) {
        double angle = Math.PI * 2.0D * index / DOOR_CRACK_POINTS;
        double sectorOrder = (Math.sin(angle) + 1.0D) * 0.5D;
        double local = (unfoldProgress - sectorOrder * 0.42D) / 0.58D;
        return smooth(local);
    }

    private static void renderOpeningSphere(Matrix4f matrix, VertexConsumer vertices, Vec3d center,
                                            Vec3d major, Vec3d minor, double progress, double alphaScale) {
        double radius = 0.01D + 0.29D * progress;
        int alpha = (int) (245.0D * alphaScale);
        int centerAlpha = (int) (255.0D * alphaScale);
        int sides = 18;
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            double a1 = Math.PI * 2.0D * i / sides;
            double a2 = Math.PI * 2.0D * next / sides;
            Vec3d p1 = center.add(major.normalize().multiply(Math.sin(a1) * radius)).add(minor.normalize().multiply(Math.cos(a1) * radius));
            Vec3d p2 = center.add(major.normalize().multiply(Math.sin(a2) * radius)).add(minor.normalize().multiply(Math.cos(a2) * radius));
            colorVertex(matrix, vertices, center, 0, 0, 0, centerAlpha);
            colorVertex(matrix, vertices, p1, 0, 0, 0, alpha);
            colorVertex(matrix, vertices, p2, 0, 0, 0, alpha);
            colorVertex(matrix, vertices, center, 0, 0, 0, centerAlpha);
        }
    }

    private static void renderOpeningCrackLines(Matrix4f matrix, VertexConsumer vertices,
                                                Vec3d center, Vec3d[] inner, double progress) {
        int[] indices = new int[] {3, 16, 28};
        for (int i = 0; i < indices.length; i++) {
            Vec3d end = inner[indices[i]];
            Vec3d direction = end.subtract(center);
            if (direction.lengthSquared() < 0.0001D) {
                continue;
            }

            Vec3d bendAxis = new Vec3d(-direction.y, direction.x, direction.z * 0.18D).normalize();
            double bend = 0.10D + i * 0.035D;
            Vec3d p1 = center.add(direction.multiply(0.34D)).add(bendAxis.multiply(bend));
            Vec3d p2 = center.add(direction.multiply(0.68D)).subtract(bendAxis.multiply(bend * 0.75D));
            renderGrowingCrackLine(matrix, vertices, center, p1, p2, end, progress);
        }
    }

    private static void renderGrowingCrackLine(Matrix4f matrix, VertexConsumer vertices,
                                               Vec3d start, Vec3d bendA, Vec3d bendB, Vec3d end,
                                               double progress) {
        renderGrowingSegment(matrix, vertices, start, bendA, progress, 0.0D, 0.34D, 0.032D);
        renderGrowingSegment(matrix, vertices, bendA, bendB, progress, 0.34D, 0.68D, 0.030D);
        renderGrowingSegment(matrix, vertices, bendB, end, progress, 0.68D, 1.0D, 0.026D);
    }

    private static void renderGrowingSegment(Matrix4f matrix, VertexConsumer vertices,
                                             Vec3d start, Vec3d end, double progress,
                                             double fromProgress, double toProgress, double width) {
        if (progress <= fromProgress) {
            return;
        }

        double local = Math.min(1.0D, (progress - fromProgress) / (toProgress - fromProgress));
        Vec3d visibleEnd = lerp(start, end, smooth(local));
        renderLineAsQuad(matrix, vertices, start, visibleEnd, width, 0, 0, 0, 245);
    }

    private static void renderLineAsQuad(Matrix4f matrix, VertexConsumer vertices, Vec3d start, Vec3d end,
                                         double width, int red, int green, int blue, int alpha) {
        Vec3d axis = end.subtract(start);
        if (axis.lengthSquared() < 0.0001D) {
            return;
        }
        Vec3d side = new Vec3d(-axis.y, axis.x, 0.0D);
        if (side.lengthSquared() < 0.0001D) {
            side = new Vec3d(width, 0.0D, 0.0D);
        } else {
            side = side.normalize().multiply(width);
        }
        colorVertex(matrix, vertices, start.add(side), red, green, blue, alpha);
        colorVertex(matrix, vertices, start.subtract(side), red, green, blue, alpha);
        colorVertex(matrix, vertices, end.subtract(side), red, green, blue, alpha);
        colorVertex(matrix, vertices, end.add(side), red, green, blue, alpha);
    }

    private static Vec3d doorLocalWaveOffset(Vec3d radial, Vec3d major, Vec3d minor, double time) {
        double cycle = (time % 200.0D) / 20.0D;
        if (cycle >= 8.0D) {
            return Vec3d.ZERO;
        }

        Vec3d majorDir = major.normalize();
        Vec3d minorDir = minor.normalize();
        Vec3d travelDir = majorDir.add(minorDir).normalize();
        Vec3d waveDir = majorDir.subtract(minorDir).normalize();
        double extent = major.length() + minor.length();
        double coord = radial.dotProduct(travelDir) / Math.max(0.0001D, extent);
        double travel = cycle < 4.0D ? smooth(cycle / 4.0D) : 1.0D;
        double fade = cycle < 4.0D ? smooth(cycle / 4.0D) : 1.0D - smooth((cycle - 4.0D) / 4.0D);
        double head = 0.72D - travel * 1.44D;
        double distance = coord - head;
        double influence = Math.exp(-distance * distance * 30.0D);
        double ripple = Math.sin(distance * 24.0D - time * 0.42D) * influence * fade * 0.13D;
        return waveDir.multiply(ripple);
    }

    private static void renderDoorStars(Matrix4f matrix, VertexConsumer vertices, Vec3d center,
                                        Vec3d major, Vec3d minor, Vec3d normal, double time, double reveal) {
        if (reveal <= 0.02D) {
            return;
        }

        Vec3d majorDir = major.normalize();
        Vec3d minorDir = minor.normalize();
        for (int i = 0; i < DOOR_STAR_COUNT; i++) {
            double seed = i * 19.19D + 4.7D;
            double r = Math.sqrt(fract(Math.sin(seed) * 43758.5453D)) * 0.92D;
            double angle = Math.PI * 2.0D * fract(Math.sin(seed * 1.37D) * 24634.6345D);
            double order = r * 0.65D + 0.35D * fract(Math.sin(seed * 2.1D) * 173.31D);
            if (reveal < order) {
                continue;
            }
            double twinkle = 0.62D + 0.38D * Math.sin(time * 0.18D + seed);
            double size = (0.012D + 0.018D * fract(Math.sin(seed * 3.7D) * 831.17D)) * twinkle;
            Vec3d starCenter = center.add(major.multiply(Math.sin(angle) * r)).add(minor.multiply(Math.cos(angle) * r));
            double driftStrength = reveal * (0.018D + 0.022D * fract(Math.sin(seed * 5.3D) * 917.21D));
            starCenter = starCenter
                    .add(majorDir.multiply(Math.sin(time * (0.027D + seed * 0.0009D) + seed) * driftStrength))
                    .add(minorDir.multiply(Math.cos(time * (0.039D + seed * 0.0007D) + seed * 1.7D) * driftStrength * 0.85D));
            Vec3d a = majorDir.multiply(size);
            Vec3d b = minorDir.multiply(size);
            int alpha = (int) (185 + 55 * twinkle);
            colorVertex(matrix, vertices, starCenter.add(a), 255, 250, 222, alpha);
            colorVertex(matrix, vertices, starCenter.add(b), 255, 255, 255, alpha);
            colorVertex(matrix, vertices, starCenter.subtract(a), 180, 216, 255, alpha);
            colorVertex(matrix, vertices, starCenter.subtract(b), 255, 220, 245, alpha);
        }
    }

    private static double fract(double value) {
        return value - Math.floor(value);
    }

    private static void renderFloatingCrackFragments(Matrix4f matrix, VertexConsumer vertices,
                                                     Vec3d center, Vec3d major, Vec3d minor, double time, double age) {
        Vec3d majorDir = major.normalize();
        Vec3d minorDir = minor.normalize();
        double separation = smooth((age - 200.0D) / 40.0D);
        if (separation <= 0.0D) {
            return;
        }
        for (int i = 0; i < DOOR_FRAGMENT_COUNT; i++) {
            double seed = i * 12.9898D + 78.233D;
            double angle = i * 2.399963D + Math.sin(seed) * 0.38D;
            double edgeDistance = 0.98D + 0.08D * Math.sin(seed * 1.7D);
            double separatedDistance = 1.28D + 0.24D * Math.sin(seed * 1.7D) + 0.18D * Math.cos(seed * 2.3D);
            double distance = edgeDistance * (1.0D - separation) + separatedDistance * separation;
            double floatOffsetA = Math.sin(time * (0.041D + 0.002D * (i % 5)) + seed) * (0.045D + 0.035D * separation);
            double floatOffsetB = Math.cos(time * (0.057D + 0.0017D * (i % 7)) + seed * 1.37D) * (0.035D + 0.030D * separation);
            Vec3d base = center.add(major.multiply(Math.sin(angle) * distance))
                    .add(minor.multiply(Math.cos(angle) * distance));
            Vec3d radialDir = major.multiply(Math.sin(angle)).add(minor.multiply(Math.cos(angle))).normalize();
            Vec3d leaveOffset = radialDir.multiply(separation * (0.12D + 0.10D * fract(Math.sin(seed) * 513.7D)));
            Vec3d offset = majorDir.multiply(floatOffsetA).add(minorDir.multiply(floatOffsetB)).add(leaveOffset);
            Vec3d fragmentCenter = base.add(offset);
            double longRadius = 0.10D + 0.05D * Math.abs(Math.sin(seed * 0.31D));
            double shortRadius = 0.035D + 0.025D * Math.abs(Math.cos(seed * 0.47D));
            Vec3d a = majorDir.multiply(longRadius);
            Vec3d b = minorDir.multiply(shortRadius);
            if ((i & 2) != 0) {
                Vec3d swap = a;
                a = b.multiply(1.4D);
                b = swap.multiply(0.65D);
            }

            int alphaA = (int) (222.0D * separation);
            int alphaB = (int) (235.0D * separation);
            int alphaC = (int) (144.0D * separation);
            colorVertex(matrix, vertices, fragmentCenter.add(a), 0, 0, 0, alphaA);
            colorVertex(matrix, vertices, fragmentCenter.add(b), 2, 0, 8, alphaB);
            colorVertex(matrix, vertices, fragmentCenter.subtract(a.multiply(0.85D)), 0, 0, 0, alphaA);
            colorVertex(matrix, vertices, fragmentCenter.subtract(b.multiply(1.15D)), 18, 0, 32, alphaC);
        }
    }

    private static void colorVertex(Matrix4f matrix, VertexConsumer vertices, Vec3d pos,
                                    int red, int green, int blue, int alpha) {
        vertices.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(red, green, blue, alpha);
    }

    private record Basis(Vec3d side, Vec3d other) {
    }

    private enum CubeFormat {
        POSITION,
        TEXTURED_FULLBRIGHT
    }

    private enum BeamFormat {
        POSITION,
        BEACON
    }
}
