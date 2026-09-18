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

    @org.junit.jupiter.api.Test
    public void localWorldPreservesNeighborsFluidsAndMultipartShapes() {
        setup();
        var fence = Blocks.OAK_FENCE.getDefaultState().with(net.minecraft.block.FenceBlock.EAST, true);
        var stair = Blocks.OAK_STAIRS.getDefaultState();
        var wet = Blocks.CHAIN.getDefaultState().with(net.minecraft.block.ChainBlock.WATERLOGGED, true);
        var structure = new SwingStructure(java.util.List.of(new SwingBlock(new BlockPos(0, -20, 0), fence),
                new SwingBlock(new BlockPos(1, -20, 0), stair), new SwingBlock(new BlockPos(0, -19, 0), wet)));
        var world = structure.blockWorld();
        require(world.getBlockState(new BlockPos(1, -20, 0)) == stair, "Local neighbor must be the captured block");
        require(world.getBlockState(new BlockPos(2, -20, 0)).isAir(), "Outside structure is air");
        require(!world.getFluidState(new BlockPos(0, -19, 0)).isEmpty(), "Waterlogged fluid state must survive capture");
        require(world.getBlockState(new BlockPos(0, -20, 0)).get(net.minecraft.block.FenceBlock.EAST), "Captured fence connections must survive");
        require(structure.collisionShapes().size() > 3, "Multipart shapes must not be collapsed to a single block box");
        require(structure.collisionShapes() == structure.collisionShapes(), "Immutable shape geometry should be reused");
        require(structure.geometry() == structure.geometry(), "Subdivided geometry should be cached");
        require(world.isInHeightLimit(-20), "Local negative heights must be valid");
    }

    @org.junit.jupiter.api.Test
    public void longSeatsAndChainHitAtBothRotationLimits() {
        setup();
        for (boolean zAxis : new boolean[]{false, true}) for (int length : new int[]{3, 13, 20}) {
            var blocks = new ArrayList<SwingBlock>();
            for (int x = -2; x <= 1; x++) blocks.add(new SwingBlock(
                    zAxis ? new BlockPos(0, -length - 1, x) : new BlockPos(x, -length - 1, 0),
                    Blocks.CHERRY_TRAPDOOR.getDefaultState().with(TrapdoorBlock.HALF, BlockHalf.TOP)));
            blocks.add(new SwingBlock(zAxis ? new BlockPos(0, -length, -2) : new BlockPos(-2, -length, 0), Blocks.CHAIN.getDefaultState()));
            var structure = new SwingStructure(blocks);
            for (float angle : new float[]{0, (float) Math.toRadians(75), (float) Math.toRadians(-75)}) {
                var space = new SwingStructureSpace(structure, new Vec3d(-98.5, -44.5, 265.5), angle, zAxis);
                for (int i = 0; i < 4; i++) {
                    Vec3d target = zAxis ? new Vec3d(0, -length - .6, i - 2) : new Vec3d(i - 2, -length - .6, 0);
                    Vec3d eye = target.add(zAxis ? new Vec3d(-2, 0, 0) : new Vec3d(0, 0, -2));
                    var hit = space.raycast(space.toWorld(eye), space.toWorld(target)).orElseThrow();
                    require(hit.seat() == i, "Even-width slots must match the clicked block at every angle and chain length");
                    require(space.toLocal(space.toWorld(target)).distanceTo(target) < 1.0e-6, "World/local round trip");
                    require(space.bounds().expand(1e-5).contains(hit.worldPoint()), "Hit must stay inside shared render/query bounds");
                }
                Vec3d chain = zAxis ? new Vec3d(0, -length, -2) : new Vec3d(-2, -length, 0);
                var chainHit = space.raycast(space.toWorld(chain.add(zAxis ? new Vec3d(-2, 0, 0) : new Vec3d(0, 0, -2))), space.toWorld(chain)).orElseThrow();
                require(chainHit.seat() == -1, "Chain must be selectable for dismantling without becoming a seat");
            }
        }
    }

    @org.junit.jupiter.api.Test
    public void nearerBackrestBlocksSeatAndEmptyStructureSpaceDoesNotHit() {
        setup();
        var structure = new SwingStructure(java.util.List.of(
                new SwingBlock(new BlockPos(0, -4, 0), Blocks.OAK_SLAB.getDefaultState()),
                new SwingBlock(new BlockPos(0, -4, -1), Blocks.STONE.getDefaultState())));
        var space = new SwingStructureSpace(structure, Vec3d.ZERO, 0, false);
        var hit = space.raycast(new Vec3d(0, -4.25, -3), new Vec3d(0, -4.25, .25)).orElseThrow();
        require(hit.block().equals(new BlockPos(0, -4, -1)) && hit.seat() < 0, "Nearest non-seat must occlude the seat");
        require(space.raycast(new Vec3d(0, -2, -3), new Vec3d(0, -2, 1)).isEmpty(), "Empty area of broad box must not be clickable");
    }

    @org.junit.jupiter.api.Test
    public void collisionUsesThinSeatSurfaceAndAllowsStanding() {
        setup();
        var structure = new SwingStructure(java.util.List.of(new SwingBlock(new BlockPos(0, -21, 0),
                Blocks.CHERRY_TRAPDOOR.getDefaultState().with(TrapdoorBlock.HALF, BlockHalf.TOP))));
        var flat = new SwingStructureSpace(structure, Vec3d.ZERO, 0, false);
        var player = new net.minecraft.util.math.Box(-.3, -20, -.3, .3, -18.2, .3);
        var shapes = flat.collisionBoxes(null).stream().map(net.minecraft.util.shape.VoxelShapes::cuboid).toList();
        double falling = net.minecraft.util.shape.VoxelShapes.calculateMaxOffset(net.minecraft.util.math.Direction.Axis.Y, player, shapes, -2);
        require(Math.abs(falling + .5) < 1e-7, "Falling player must stop on the actual top trapdoor surface");
        var tilted = new SwingStructureSpace(structure, Vec3d.ZERO, (float) Math.toRadians(45), false);
        var boxes = tilted.collisionBoxes(null);
        var hull = tilted.transformBounds(structure.seatBounds());
        Vec3d emptyCorner = new Vec3d(0, hull.maxY - .03, hull.minZ + .03);
        require(boxes.stream().noneMatch(b -> b.contains(emptyCorner)), "Tilted seat must not fill the empty corners of its AABB");
        Vec3d inside = tilted.toWorld(new Vec3d(0, -20.6, 0));
        require(boxes.stream().anyMatch(b -> b.contains(inside)), "Actual rotated seat still collides");
    }

    @org.junit.jupiter.api.Test
    public void sectionIndexTracksBottomAndRemovesOldPose() {
        var index = new SwingSectionIndex<Object>();
        Object swing = new Object();
        index.update(swing, new net.minecraft.util.math.Box(-2, -60, -2, 2, -39, 2));
        require(index.query(new net.minecraft.util.math.Box(-1, -60, -1, 1, -58, 1)).contains(swing), "Bottom section must contain the long swing");
        index.update(swing, new net.minecraft.util.math.Box(40, 0, 0, 44, 21, 2));
        require(index.query(new net.minecraft.util.math.Box(-1, -60, -1, 1, -58, 1)).isEmpty(), "Old sections must be removed after pose changes");
        require(index.query(new net.minecraft.util.math.Box(41, 0, 0, 42, 1, 1)).contains(swing), "New sections must find moved swing");
        index.remove(swing);
        require(index.query(new net.minecraft.util.math.Box(41, 0, 0, 42, 1, 1)).isEmpty(), "Unload must remove membership");
    }

    @org.junit.jupiter.api.Test
    public void motionTimelineInterpolatesAndStopsExtrapolatingOnPacketLoss() {
        var timeline = new SwingMotionTimeline();
        timeline.accept(100, 0, .1f, 20);
        timeline.accept(102, .2f, .1f, 22);
        require(Math.abs(timeline.sample(23, 0) - .1) < 1e-6, "Must interpolate on server tick timestamps");
        require(Math.abs(timeline.sample(1000, 0) - .7) < 1e-6, "Packet loss must stop extrapolation after five ticks");
        timeline.accept(101, -.5f, -.1f, 24);
        require(Math.abs(timeline.sample(23, 0) - .1) < 1e-6, "Out-of-order snapshots must not rewind the timeline");
        timeline.clear();
        require(timeline.sample(0, .4f) == .4f, "Reassembly resets timeline to the synchronized pose");
        timeline.accept(200, 1.3f, 1, 0);
        require(timeline.sample(100, 0) <= Math.toRadians(75) + 1e-6, "Prediction must respect 75-degree limit");
    }

    @org.junit.jupiter.api.Test
    public void oldPackedStructureRebuildsLocalWorldWithoutChangingSeatOrder() {
        setup();
        var original = new SwingStructure(java.util.List.of(
                new SwingBlock(new BlockPos(-1, -21, 0), Blocks.OAK_SLAB.getDefaultState()),
                new SwingBlock(new BlockPos(0, -21, 0), Blocks.CHERRY_TRAPDOOR.getDefaultState()),
                new SwingBlock(new BlockPos(-1, -20, 0), Blocks.CHAIN.getDefaultState())));
        var restored = SwingStructure.fromPacked(original.packedPositions(), original.stateIds());
        require(restored.blocks().equals(original.blocks()), "Existing serialized positions and state IDs must remain readable");
        require(restored.seatSlots().equals(original.seatSlots()), "Saved seat assignments must retain their slot order");
        require(restored.collisionShapes().equals(original.collisionShapes()), "Local world should regenerate the same collision shapes");
    }

    @org.junit.jupiter.api.Test
    public void newMotionSnapshotStillHasAOneTickDisplayDerivative() {
        var timeline = new SwingMotionTimeline();
        for (int tick = 100; tick <= 105; tick++) timeline.accept(tick, (tick - 100) * .1f, .1f, tick - 80);
        require(Math.abs(timeline.sample(25, 0) - timeline.sample(24, 0) - .1f) < 1e-6,
                "A packet arriving every tick must not suppress display velocity and add smoothing lag");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
