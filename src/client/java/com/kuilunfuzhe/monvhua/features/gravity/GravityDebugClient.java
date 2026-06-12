package com.kuilunfuzhe.monvhua.features.gravity;

import com.kuilunfuzhe.monvhua.item.gravity.GravityItems;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.gravity.GravityPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public final class GravityDebugClient {
    private static final double PREVIEW_REACH = 96.0D;

    public static final Config NORMAL = new Config(GravityAreaSpec.Shape.SPHERE, GravityAreaSpec.Half.LOWER, 8, 4, 8, GravityMagic.INFINITE_AREA_TICKS, GravityMagic.DEFAULT_GRAVITY);
    public static final Config IRREGULAR = new Config(GravityAreaSpec.Shape.BOX, GravityAreaSpec.Half.FULL, 5, 5, 5, GravityMagic.INFINITE_AREA_TICKS, GravityMagic.DEFAULT_GRAVITY);
    public static final Config SELECTION = new Config(GravityAreaSpec.Shape.BOX, GravityAreaSpec.Half.FULL, 5, 5, 5, GravityMagic.INFINITE_AREA_TICKS, GravityMagic.DEFAULT_GRAVITY);

    private static ToolMode mode = ToolMode.NORMAL;
    private static Operation normalOperation = Operation.PLACE;
    private static Operation irregularOperation = Operation.PLACE;
    private static Operation selectionOperation = Operation.PLACE;
    private static Config clipboard;
    private static boolean previewVisible = false;
    private static boolean lastUsePressed;
    private static boolean lastAttackPressed;
    private static boolean lastDeletePressed;
    private static boolean lastCtrlZPressed;
    private static boolean lastCtrlYPressed;
    private static boolean lastCtrlCPressed;
    private static boolean lastCtrlVPressed;

    private GravityDebugClient() {
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || !isHoldingDebugStick(client)) {
            previewVisible = false;
            resetKeyEdges();
            return;
        }
        if (client.currentScreen != null) {
            resetKeyEdges();
            return;
        }

        boolean usePressed = client.options.useKey.isPressed();
        boolean attackPressed = client.options.attackKey.isPressed();
        boolean deletePressed = isKeyPressed(client, GLFW.GLFW_KEY_DELETE);
        boolean ctrl = isCtrlDown(client);
        boolean ctrlZPressed = ctrl && isKeyPressed(client, GLFW.GLFW_KEY_Z);
        boolean ctrlYPressed = ctrl && isKeyPressed(client, GLFW.GLFW_KEY_Y);
        boolean ctrlCPressed = ctrl && isKeyPressed(client, GLFW.GLFW_KEY_C);
        boolean ctrlVPressed = ctrl && isKeyPressed(client, GLFW.GLFW_KEY_V);

        if (usePressed && !lastUsePressed) {
            handleRightClick(client);
            client.options.useKey.setPressed(false);
        }
        if (attackPressed && !lastAttackPressed) {
            if (previewVisible) {
                previewVisible = false;
                client.player.sendMessage(Text.literal("\u00a7e[Gravity Debug] Preview cancelled"), true);
            }
            client.options.attackKey.setPressed(false);
        }
        if (deletePressed && !lastDeletePressed) {
            sendAction(client, 2, currentConfig());
        }
        if (ctrlZPressed && !lastCtrlZPressed) {
            sendAction(client, 3, currentConfig());
        }
        if (ctrlYPressed && !lastCtrlYPressed) {
            sendAction(client, 4, currentConfig());
        }
        if (ctrlCPressed && !lastCtrlCPressed) {
            clipboard = currentConfig().copy();
            client.player.sendMessage(Text.literal("\u00a7e[Gravity Debug] Copied current " + mode.displayName + " config"), true);
        }
        if (ctrlVPressed && !lastCtrlVPressed && clipboard != null) {
            sendAction(client, 0, clipboard);
        }

        lastUsePressed = usePressed;
        lastAttackPressed = attackPressed;
        lastDeletePressed = deletePressed;
        lastCtrlZPressed = ctrlZPressed;
        lastCtrlYPressed = ctrlYPressed;
        lastCtrlCPressed = ctrlCPressed;
        lastCtrlVPressed = ctrlVPressed;
    }

    public static boolean tryOpenConfig(MinecraftClient client) {
        if (client.player == null || !isHoldingDebugStick(client)) {
            return false;
        }
        client.setScreen(new GravityDebugScreen());
        return true;
    }

    public static boolean isHoldingDebugStick(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().getItem() == GravityItems.GRAVITY_DEBUG_STICK;
    }

    public static ToolMode mode() {
        return mode;
    }

    public static void setMode(ToolMode value) {
        ToolMode next = value == null ? ToolMode.NORMAL : value;
        if (mode != next) {
            previewVisible = false;
        }
        mode = next;
    }

    public static void togglePreview() {
        previewVisible = !previewVisible;
    }

    public static Operation operation() {
        return operation(mode);
    }

    public static Operation operation(ToolMode value) {
        return switch (value == null ? ToolMode.NORMAL : value) {
            case NORMAL -> normalOperation;
            case IRREGULAR -> irregularOperation;
            case SELECTION -> selectionOperation;
        };
    }

    public static void setOperation(Operation value) {
        setOperation(mode, value);
    }

    public static void setOperation(ToolMode mode, Operation value) {
        Operation next = value == null ? Operation.PLACE : value;
        switch (mode == null ? ToolMode.NORMAL : mode) {
            case NORMAL -> normalOperation = next;
            case IRREGULAR -> irregularOperation = next;
            case SELECTION -> selectionOperation = next;
        }
    }

    public static Config currentConfig() {
        return config(mode);
    }

    public static Config config(ToolMode value) {
        return switch (value == null ? ToolMode.NORMAL : value) {
            case NORMAL -> NORMAL;
            case IRREGULAR -> IRREGULAR;
            case SELECTION -> SELECTION;
        };
    }

    public static GravityAreaSpec previewSpec() {
        Config config = currentConfig();
        return config.spec();
    }

    public static BlockPos previewCenter(MinecraftClient client) {
        if (!previewVisible) {
            return null;
        }
        return targetBlock(client);
    }

    public static boolean renderPreview() {
        return previewVisible;
    }

    private static void handleRightClick(MinecraftClient client) {
        BlockPos center = targetBlock(client);
        if (center == null) {
            return;
        }

        if (!previewVisible) {
            return;
        }
        if (mode == ToolMode.SELECTION) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("\u00a7e[Gravity Debug] Selection anchored"), true);
            }
            return;
        }
        sendAction(client, operation() == Operation.MOVE_NEAREST ? 1 : 0, currentConfig());
    }

    private static void sendAction(MinecraftClient client, int action, Config config) {
        BlockPos center = previewCenter(client);
        if (center == null && (action == 3 || action == 4)) {
            center = client.player == null ? null : client.player.getBlockPos();
        }
        if (center == null) {
            return;
        }
        GravityAreaSpec spec = config.spec();
        SafeClientNetworking.send(new GravityPackets.DebugAreaActionC2S(
                action,
                center,
                spec.shape().ordinal(),
                spec.half().ordinal(),
                spec.sizeX(),
                spec.sizeY(),
                spec.sizeZ(),
                config.ticks,
                config.gravity
        ));
    }

    private static BlockPos targetBlock(MinecraftClient client) {
        if (client.player == null) {
            return null;
        }
        HitResult hit = client.player.raycast(PREVIEW_REACH, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos().toImmutable();
        }
        return null;
    }

    private static boolean isCtrlDown(MinecraftClient client) {
        return isKeyPressed(client, GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyPressed(client, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isKeyPressed(MinecraftClient client, int key) {
        return GLFW.glfwGetKey(client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    private static void resetKeyEdges() {
        lastUsePressed = false;
        lastAttackPressed = false;
        lastDeletePressed = false;
        lastCtrlZPressed = false;
        lastCtrlYPressed = false;
        lastCtrlCPressed = false;
        lastCtrlVPressed = false;
    }

    public enum ToolMode {
        NORMAL("普通"),
        IRREGULAR("非常规"),
        SELECTION("选中");

        private final String displayName;

        ToolMode(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public enum Operation {
        PLACE("放置"),
        MOVE_NEAREST("移动最近场");

        private final String displayName;

        Operation(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public static final class Config {
        private GravityAreaSpec.Shape shape;
        private GravityAreaSpec.Half half;
        private int sizeX;
        private int sizeY;
        private int sizeZ;
        private int ticks;
        private double gravity;

        private Config(GravityAreaSpec.Shape shape, GravityAreaSpec.Half half, int sizeX, int sizeY, int sizeZ, int ticks, double gravity) {
            this.shape = shape;
            this.half = half;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.ticks = ticks;
            this.gravity = gravity;
        }

        public GravityAreaSpec spec() {
            return new GravityAreaSpec(shape, half, sizeX, sizeY, sizeZ);
        }

        public Config copy() {
            return new Config(shape, half, sizeX, sizeY, sizeZ, ticks, gravity);
        }

        public GravityAreaSpec.Shape shape() {
            return shape;
        }

        public void cycleShape() {
            GravityAreaSpec.Shape[] values = GravityAreaSpec.Shape.values();
            shape = values[(shape.ordinal() + 1) % values.length];
            if (shape == GravityAreaSpec.Shape.CUBE) {
                int size = Math.max(sizeX, Math.max(sizeY, sizeZ));
                sizeX = size;
                sizeY = size;
                sizeZ = size;
            }
        }

        public GravityAreaSpec.Half half() {
            return half;
        }

        public void cycleHalf() {
            GravityAreaSpec.Half[] values = GravityAreaSpec.Half.values();
            half = values[(half.ordinal() + 1) % values.length];
        }

        public int sizeX() {
            return sizeX;
        }

        public int sizeY() {
            return sizeY;
        }

        public int sizeZ() {
            return sizeZ;
        }

        public void addSize(int axis, int delta) {
            if (axis == 0) {
                sizeX = clamp(sizeX + delta);
            } else if (axis == 1) {
                sizeY = clamp(sizeY + delta);
            } else {
                sizeZ = clamp(sizeZ + delta);
            }
            if (shape == GravityAreaSpec.Shape.CUBE) {
                int size = Math.max(sizeX, Math.max(sizeY, sizeZ));
                sizeX = size;
                sizeY = size;
                sizeZ = size;
            }
        }

        public int ticks() {
            return ticks;
        }

        public void addSeconds(int seconds) {
            if (ticks == GravityMagic.INFINITE_AREA_TICKS) {
                ticks = Math.max(20, seconds * 20);
            } else {
                ticks = Math.max(20, ticks + seconds * 20);
            }
        }

        public void toggleInfinite() {
            ticks = ticks == GravityMagic.INFINITE_AREA_TICKS ? 20 * 60 : GravityMagic.INFINITE_AREA_TICKS;
        }

        public double gravity() {
            return gravity;
        }

        public void addGravity(double delta) {
            gravity = GravityMagic.clampGravity(gravity + delta);
        }

        private static int clamp(int value) {
            return Math.clamp(value, GravityAreaSpec.MIN_SIZE, GravityAreaSpec.MAX_SIZE);
        }
    }
}
