package com.kuilunfuzhe.monvhua.features.area_tip;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.service.ToolRegistryService;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.type.ImInt;
import imgui.moulberry92.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;

public final class AreaTipAxiomIntegration {
    private static boolean registered;

    private AreaTipAxiomIntegration() {
    }

    public static void initialize() {
        if (registered || !FabricLoader.getInstance().isModLoaded("axiom")) {
            return;
        }
        try {
            for (ToolRegistryService registry : ServiceLoader.load(ToolRegistryService.class)) {
                registry.register(new AreaTipAxiomTool());
                registered = true;
                MonvhuaMod.LOGGER.info("[Monvhua] Registered Axiom area tip tool");
                return;
            }
            MonvhuaMod.LOGGER.warn("[Monvhua] Axiom is loaded, but ToolRegistryService was not available");
        } catch (Throwable throwable) {
            MonvhuaMod.LOGGER.warn("[Monvhua] Failed to register Axiom area tip tool", throwable);
        }
    }

    private static final class AreaTipAxiomTool implements CustomTool {
        private static final String[] SHAPE_NAMES = {"Sphere", "Box", "Cube"};
        private static final String[] HALF_NAMES = {"Full", "Upper", "Lower"};
        private static final long SAVE_DELAY_MS = 700L;
        private static final long SYNC_INTERVAL_MS = 5000L;

        private final ImString nameInput = new ImString(128);
        private final ImString messageInput = new ImString(AreaTipConfig.MAX_MESSAGE_LENGTH + 16);
        private final float[] colorInput = new float[3];
        private UUID loadedGroupId;
        private int selectedIndex;
        private boolean dirty;
        private long dirtyAt;
        private long lastSyncRequest;

        @Override
        public String name() {
            return "Monvhua Area Tip";
        }

        @Override
        public boolean callUseTool() {
            return placeSelection();
        }

        @Override
        public boolean callConfirm() {
            return placeSelection();
        }

        @Override
        public void displayImguiOptions() {
            requestSyncIfNeeded();
            AreaTipConfig config = AreaTipConfig.getInstance();
            ensureGroup(config);
            AreaTipConfig.GroupConfig group = selectedGroup(config);
            if (loadedGroupId == null || !loadedGroupId.equals(group.uuid())) {
                loadGroup(group);
            }

            ImGui.text("Area Tip");
            SelectionBounds bounds = currentSelectionBounds();
            if (bounds == null) {
                ImGui.textDisabled("No Axiom selection");
            } else {
                ImGui.text("Selection: " + bounds.sizeX() + " x " + bounds.sizeY() + " x " + bounds.sizeZ());
                ImGui.textDisabled(bounds.minText() + " -> " + bounds.maxText());
            }
            if (ImGui.button("Place Selection")) {
                placeSelection(bounds);
            }
            ImGui.sameLine();
            if (ImGui.button("Sync")) {
                AreaTipClient.requestSync();
                lastSyncRequest = System.currentTimeMillis();
            }
            if (dirty) {
                ImGui.sameLine();
                ImGui.textColored(255, 210, 80, 255, "Unsaved");
            }

            ImGui.separator();
            drawGroupSelector(config);
            group = selectedGroup(config);
            drawGroupEditor(config, group);
            flushIfNeeded(config, false);
        }

        private void drawGroupSelector(AreaTipConfig config) {
            List<AreaTipConfig.GroupConfig> groups = config.groups;
            String[] names = new String[groups.size()];
            for (int i = 0; i < groups.size(); i++) {
                AreaTipConfig.GroupConfig group = groups.get(i);
                names[i] = safeName(group.name) + "###area_tip_group_" + group.id;
            }
            ImInt selected = new ImInt(selectedIndex);
            if (ImGui.combo("Group", selected, names)) {
                selectedIndex = Math.clamp(selected.get(), 0, groups.size() - 1);
                config.selectedGroupId = groups.get(selectedIndex).id;
                loadGroup(groups.get(selectedIndex));
                markDirty();
            }

            if (ImGui.button("New Group")) {
                AreaTipConfig.GroupConfig group = new AreaTipConfig.GroupConfig();
                group.id = UUID.randomUUID().toString();
                group.name = "Group " + (groups.size() + 1);
                group.color = colorFromInput();
                groups.add(group);
                selectedIndex = groups.size() - 1;
                config.selectedGroupId = group.id;
                loadGroup(group);
                markDirty();
            }
            ImGui.sameLine();
            if (groups.size() <= 1) {
                ImGui.beginDisabled(true);
            }
            if (ImGui.button("Delete Group") && groups.size() > 1) {
                groups.remove(selectedIndex);
                selectedIndex = Math.clamp(selectedIndex, 0, groups.size() - 1);
                config.selectedGroupId = groups.get(selectedIndex).id;
                loadGroup(groups.get(selectedIndex));
                markDirty();
            }
            if (groups.size() <= 1) {
                ImGui.endDisabled();
            }
        }

        private void drawGroupEditor(AreaTipConfig config, AreaTipConfig.GroupConfig group) {
            if (ImGui.inputText("Name", nameInput)) {
                group.name = truncate(nameInput.get(), 64);
                if (!group.name.equals(nameInput.get())) {
                    nameInput.set(group.name);
                }
                markDirty();
            }

            if (ImGui.colorEdit3("Color", colorInput)) {
                group.color = colorFromInput();
                markDirty();
            }

            ImInt shape = new ImInt(Math.clamp(group.shape, 0, GravityAreaSpec.Shape.values().length - 1));
            if (ImGui.combo("Shape", shape, SHAPE_NAMES)) {
                group.shape = shape.get();
                applyCubeConstraint(group);
                markDirty();
            }

            ImInt half = new ImInt(Math.clamp(group.half, 0, GravityAreaSpec.Half.values().length - 1));
            if (ImGui.combo("Half", half, HALF_NAMES)) {
                group.half = half.get();
                markDirty();
            }

            if (GravityAreaSpec.Shape.byId(group.shape) == GravityAreaSpec.Shape.CUBE) {
                int[] cubeSize = {Math.max(group.sizeX, Math.max(group.sizeY, group.sizeZ))};
                if (ImGui.sliderInt("Cube Size", cubeSize, GravityAreaSpec.MIN_SIZE, GravityAreaSpec.MAX_SIZE)) {
                    group.sizeX = cubeSize[0];
                    group.sizeY = cubeSize[0];
                    group.sizeZ = cubeSize[0];
                    markDirty();
                }
            } else {
                int[] size = {group.sizeX, group.sizeY, group.sizeZ};
                if (ImGui.sliderInt3("Size X/Y/Z", size, GravityAreaSpec.MIN_SIZE, GravityAreaSpec.MAX_SIZE)) {
                    group.sizeX = size[0];
                    group.sizeY = size[1];
                    group.sizeZ = size[2];
                    markDirty();
                }
            }

            ImGui.text("Message");
            if (ImGui.inputTextMultiline("##area_tip_message", messageInput, 0.0F, 120.0F)) {
                group.message = truncate(messageInput.get(), AreaTipConfig.MAX_MESSAGE_LENGTH);
                if (!group.message.equals(messageInput.get())) {
                    messageInput.set(group.message);
                }
                markDirty();
            }

            if (ImGui.button("Save Area Tip Config")) {
                flushIfNeeded(config, true);
            }
        }

        private boolean placeSelection() {
            return placeSelection(currentSelectionBounds());
        }

        private boolean placeSelection(SelectionBounds bounds) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (bounds == null) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("\u00a7cNo Axiom selection"), true);
                }
                return false;
            }
            flushIfNeeded(AreaTipConfig.getInstance(), true);
            return AreaTipClient.placeCurrentGroupBounds(bounds.min(), bounds.max());
        }

        private void requestSyncIfNeeded() {
            long now = System.currentTimeMillis();
            if (lastSyncRequest == 0L || now - lastSyncRequest >= SYNC_INTERVAL_MS) {
                AreaTipClient.requestSync();
                lastSyncRequest = now;
            }
        }

        private AreaTipConfig.GroupConfig selectedGroup(AreaTipConfig config) {
            ensureGroup(config);
            selectedIndex = Math.clamp(selectedIndex, 0, config.groups.size() - 1);
            UUID selected = config.selectedGroup().map(AreaTipConfig.GroupConfig::uuid).orElse(null);
            if (selected != null) {
                for (int i = 0; i < config.groups.size(); i++) {
                    if (selected.equals(config.groups.get(i).uuid())) {
                        selectedIndex = i;
                        break;
                    }
                }
            }
            return config.groups.get(selectedIndex);
        }

        private void ensureGroup(AreaTipConfig config) {
            if (config.groups.isEmpty()) {
                AreaTipConfig.GroupConfig group = new AreaTipConfig.GroupConfig();
                group.id = UUID.randomUUID().toString();
                group.name = "Default";
                config.groups.add(group);
                config.selectedGroupId = group.id;
            }
        }

        private void loadGroup(AreaTipConfig.GroupConfig group) {
            loadedGroupId = group.uuid();
            nameInput.set(safeName(group.name));
            messageInput.set(group.message == null ? "" : truncate(group.message, AreaTipConfig.MAX_MESSAGE_LENGTH));
            colorInput[0] = ((group.color >>> 16) & 0xFF) / 255.0F;
            colorInput[1] = ((group.color >>> 8) & 0xFF) / 255.0F;
            colorInput[2] = (group.color & 0xFF) / 255.0F;
        }

        private void markDirty() {
            dirty = true;
            dirtyAt = System.currentTimeMillis();
        }

        private void flushIfNeeded(AreaTipConfig config, boolean force) {
            if (!dirty) {
                return;
            }
            long now = System.currentTimeMillis();
            if (!force && now - dirtyAt < SAVE_DELAY_MS) {
                return;
            }
            ensureGroup(config);
            config.selectedGroupId = config.groups.get(Math.clamp(selectedIndex, 0, config.groups.size() - 1)).id;
            AreaTipConfig.syncInstance(config);
            config.save();
            AreaTipClient.sendConfigUpdate(config);
            dirty = false;
        }

        private int colorFromInput() {
            int r = Math.clamp(Math.round(colorInput[0] * 255.0F), 0, 255);
            int g = Math.clamp(Math.round(colorInput[1] * 255.0F), 0, 255);
            int b = Math.clamp(Math.round(colorInput[2] * 255.0F), 0, 255);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        private static void applyCubeConstraint(AreaTipConfig.GroupConfig group) {
            if (GravityAreaSpec.Shape.byId(group.shape) != GravityAreaSpec.Shape.CUBE) {
                return;
            }
            int size = Math.max(group.sizeX, Math.max(group.sizeY, group.sizeZ));
            group.sizeX = size;
            group.sizeY = size;
            group.sizeZ = size;
        }

        private static String safeName(String value) {
            return value == null || value.isBlank() ? "Group" : truncate(value, 64);
        }

        private static String truncate(String value, int maxLength) {
            if (value == null) {
                return "";
            }
            return value.length() > maxLength ? value.substring(0, maxLength) : value;
        }
    }

    private static SelectionBounds currentSelectionBounds() {
        try {
            Class<?> selectionClass = Class.forName("com.moulberry.axiom.clipboard.Selection");
            Class<?> bufferClass = Class.forName("com.moulberry.axiom.clipboard.SelectionBuffer");
            Method getSelectionBuffer = selectionClass.getMethod("getSelectionBuffer");
            Object buffer = getSelectionBuffer.invoke(null);
            if (buffer == null || (boolean) bufferClass.getMethod("isEmpty").invoke(buffer)) {
                return null;
            }
            BlockPos min = ((BlockPos) bufferClass.getMethod("min").invoke(buffer)).toImmutable();
            BlockPos max = ((BlockPos) bufferClass.getMethod("max").invoke(buffer)).toImmutable();
            return new SelectionBounds(min, max);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private record SelectionBounds(BlockPos min, BlockPos max) {
        private SelectionBounds {
            BlockPos fixedMin = new BlockPos(
                    Math.min(min.getX(), max.getX()),
                    Math.min(min.getY(), max.getY()),
                    Math.min(min.getZ(), max.getZ())
            );
            BlockPos fixedMax = new BlockPos(
                    Math.max(min.getX(), max.getX()),
                    Math.max(min.getY(), max.getY()),
                    Math.max(min.getZ(), max.getZ())
            );
            min = fixedMin.toImmutable();
            max = fixedMax.toImmutable();
        }

        private int sizeX() {
            return max.getX() - min.getX() + 1;
        }

        private int sizeY() {
            return max.getY() - min.getY() + 1;
        }

        private int sizeZ() {
            return max.getZ() - min.getZ() + 1;
        }

        private String minText() {
            return posText(min);
        }

        private String maxText() {
            return posText(max);
        }

        private static String posText(BlockPos pos) {
            return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }
    }
}
