package com.kuilunfuzhe.monvhua.features.swing;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;

/** Real vanilla shape regression fixtures; runs without opening a save or starting Minecraft's client. */
public final class SwingGeometryTest {
    private static boolean bootstrapped;
    private static void setup() {
        if (bootstrapped) return;
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        var registry = (net.minecraft.registry.SimpleRegistry<net.minecraft.block.Block>) net.minecraft.registry.Registries.BLOCK;
        registry.resetTagEntries();
        registry.freeze();
        bootstrapped = true;
    }
    @org.junit.jupiter.api.Test
    public void seatGeometry() {
        setup();
        // This fixture uses concrete trapdoors only; bind bootstrap tag placeholders without loading a world.
        int checks = 0;
        for (boolean zAxis : new boolean[]{false, true}) {
            var blocks = new ArrayList<SwingBlock>();
            for (int width = -2; width <= 2; width++) {
                var pos = zAxis ? new BlockPos(0, -4, width) : new BlockPos(width, -4, 0);
                blocks.add(new SwingBlock(pos, Blocks.CHERRY_TRAPDOOR.getDefaultState()
                        .with(TrapdoorBlock.HALF, BlockHalf.TOP).with(TrapdoorBlock.OPEN, false)));
            }
            blocks.add(new SwingBlock(new BlockPos(0, -3, 0), Blocks.CHERRY_TRAPDOOR.getDefaultState().with(TrapdoorBlock.OPEN, true)));
            var structure = new SwingStructure(blocks);
            require(structure.seatShapes().size() == 5, "Opened backrest must not count as seat");
            require(structure.seatSlots().size() == 5, "Each seat block must expose an independent seat slot");
            require(Math.abs(structure.seatBounds().maxY + 3.5) < 1e-9, "Top trapdoor surface must match rendered height");
            for (float angle : new float[]{0, .4f, -.4f}) {
                Vec3d pivot = new Vec3d(192.5, -54.5, 248.5);
                for (int width = -2; width <= 2; width++) {
                    Vec3d target = zAxis ? new Vec3d(0, -3.6, width) : new Vec3d(width, -3.6, 0);
                    Vec3d eye = target.add(zAxis ? new Vec3d(-3, 0, 0) : new Vec3d(0, 0, -3));
                    Vec3d worldEye = SwingTransform.localToWorld(eye, pivot, angle, zAxis);
                    Vec3d worldTarget = SwingTransform.localToWorld(target, pivot, angle, zAxis);
                    Vec3d start = SwingTransform.rotate(worldEye.subtract(pivot), -angle, zAxis);
                    Vec3d end = SwingTransform.rotate(worldTarget.subtract(pivot), -angle, zAxis);
                    require(structure.seatShapes().stream().anyMatch(box -> box.raycast(start, end).isPresent()), "Every seat across width must be targetable when rotated");
                    checks++;
                }
            }
            require(structure.seatShapes().stream().noneMatch(box -> box.raycast(new Vec3d(-3, -2, 0), new Vec3d(3, -2, 0)).isPresent()), "Air above seat must not allow mounting");
            checks += 3;
        }
        System.out.println("Swing geometry: " + checks + " checks passed");
    }

    @org.junit.jupiter.api.Test
    public void evenWidthSeatGeometry() {
        setup();
        var blocks = new ArrayList<SwingBlock>();
        for (int width = 0; width < 4; width++) {
            blocks.add(new SwingBlock(new BlockPos(0, -4, width), Blocks.CHERRY_TRAPDOOR.getDefaultState()
                    .with(TrapdoorBlock.HALF, BlockHalf.TOP).with(TrapdoorBlock.OPEN, false)));
        }
        var structure = new SwingStructure(blocks);
        require(structure.seatSlots().size() == 4, "Even-width swing must expose one slot per seat block");
        require(structure.seatBounds().minZ < 0 && structure.seatBounds().maxZ > 3, "Even-width seat bounds must retain both ends");
    }

    @org.junit.jupiter.api.Test
    public void longSuspensionCollisionGeometry() {
        setup();
        var blocks = new ArrayList<SwingBlock>();
        for (int y = 1; y <= 20; y++) {
            blocks.add(new SwingBlock(new BlockPos(-2, -y, 0), Blocks.CHAIN.getDefaultState()));
            blocks.add(new SwingBlock(new BlockPos(2, -y, 0), Blocks.CHAIN.getDefaultState()));
        }
        for (int x = -2; x <= 2; x++) blocks.add(new SwingBlock(new BlockPos(x, -21, 0), Blocks.OAK_SLAB.getDefaultState()));
        var structure = new SwingStructure(blocks);
        require(structure.localBounds().minY == -21.5, "Twenty-block suspension must retain its full local extent");
        require(!structure.collisionShapes().isEmpty(), "Long suspension must expose block collision geometry");
        require(structure.collisionShapes().stream().mapToDouble(box -> box.minY).min().orElseThrow() < -21.0,
                "Collision geometry must reach the lower seat instead of stopping near the pivot");
        require(structure.collisionShapes().size() >= 45,
                "Collision geometry must preserve separate chain and seat block shapes");
        require(structure.seatSlots().size() == 5, "Long suspension must retain all seat slots");
    }

    @org.junit.jupiter.api.Test
    public void swingInteractionMixinTargetLoads() throws ClassNotFoundException {
        setup();
        Class.forName("net.minecraft.server.network.ServerPlayNetworkHandler");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
