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

    @Override
    public void render(CosmicBoxBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        VertexConsumer vertices = vertexConsumers.getBuffer(CosmicBoxRenderLayers.cosmicBoxForCurrentShaders());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        renderCube(matrix, vertices);
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
        quad(matrix, vertices, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F);
        quad(matrix, vertices, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F);
        quad(matrix, vertices, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        quad(matrix, vertices, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        quad(matrix, vertices, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        quad(matrix, vertices, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
    }

    private static void quad(Matrix4f matrix, VertexConsumer vertices,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4) {
        vertices.vertex(matrix, x1, y1, z1);
        vertices.vertex(matrix, x2, y2, z2);
        vertices.vertex(matrix, x3, y3, z3);
        vertices.vertex(matrix, x4, y4, z4);
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

    private record Basis(Vec3d side, Vec3d other) {
    }

    private enum BeamFormat {
        POSITION,
        BEACON
    }
}
