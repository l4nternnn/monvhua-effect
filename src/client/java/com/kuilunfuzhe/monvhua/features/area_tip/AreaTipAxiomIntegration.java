package com.kuilunfuzhe.monvhua.features.area_tip;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import com.moulberry.axiom.clipboard.Selection;
import com.moulberry.axiom.clipboard.SelectionBuffer;
import com.moulberry.axiom.collections.PositionSet;
import com.moulberry.axiom.render.regions.ChunkedBooleanRegion;
import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.funcinterfaces.TriIntConsumer;
import com.moulberry.axiomclientapi.service.ToolRegistryService;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiKey;
import imgui.moulberry92.type.ImInt;
import imgui.moulberry92.type.ImString;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;

public final class AreaTipAxiomIntegration {
    public static final String AXIOM_TOOL_NAME = "Monvhua Area Tip";
    private static final long DELETE_EMPTY_CLEAR_DELAY_MS = 250L;

    private static boolean registered;
    private static volatile boolean deleteMode;
    private static DeleteSelectionKey lastDeleteSelectionKey;
    private static List<BlockPos> lastDeleteFilteredBlocks = List.of();
    private static long lastDeleteSelectionChangedAt;

    private AreaTipAxiomIntegration() {
    }

    public static boolean isDeleteModeEnabled() {
        return deleteMode;
    }

    public static void initialize() {
        if (registered || !FabricLoader.getInstance().isModLoaded("axiom")) {
            return;
        }
        try {
            for (ToolRegistryService registry : ServiceLoader.load(ToolRegistryService.class)) {
                registry.register(new AreaTipAxiomTool());
                ClientTickEvents.END_CLIENT_TICK.register(AreaTipAxiomIntegration::tickDeleteMode);
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
        private static final long SAVE_DELAY_MS = 700L;
        private static final int MAX_SELECTION_BLOCKS = GravityAreaSpec.MAX_RENDER_BLOCKS;

        private final ImString nameInput = new ImString(128);
        private final ImString messageInput = new ImString(AreaTipConfig.MAX_MESSAGE_LENGTH + 16);
        private final float[] colorInput = new float[3];
        private UUID loadedGroupId;
        private int selectedIndex;
        private boolean dirty;
        private long dirtyAt;
        private boolean requestedInitialSync;
        private boolean messageExpanded;

        @Override
        public String name() {
            return AXIOM_TOOL_NAME;
        }

        @Override
        public boolean callUseTool() {
            return false;
        }

        @Override
        public boolean callConfirm() {
            return placeSelection();
        }

        @Override
        public boolean callDelete() {
            if (!deleteMode) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§e先启用删除文字区域模式"), true);
                }
                return true;
            }
            return handleDeleteSelection();
        }

        @Override
        public void render(Camera camera, float tickDelta, long nanos, MatrixStack matrices, Matrix4f projection) {
            refreshAxiomPlacedVisibility();
        }

        @Override
        public void displayImguiOptions() {
            refreshAxiomPlacedVisibility();
            requestInitialSyncIfNeeded();
            AreaTipConfig config = AreaTipConfig.getInstance();
            ensureGroup(config);
            AreaTipConfig.GroupConfig group = selectedGroup(config);
            if (loadedGroupId == null || !loadedGroupId.equals(group.uuid())) {
                loadGroup(group);
            }

            ImGui.text("Area Tip");
            SelectionSummary selection = currentSelectionSummary();
            if (selection == null) {
                ImGui.textDisabled("No Axiom selection");
            } else {
                ImGui.text("Selection: " + selection.sizeX() + " x " + selection.sizeY() + " x " + selection.sizeZ()
                        + " / " + selection.totalBlocks() + " block(s)");
                if (selection.wasTruncated()) {
                    ImGui.textColored(255, 170, 70, 255, "Selection will be truncated to " + MAX_SELECTION_BLOCKS + " blocks");
                }
                ImGui.textDisabled(selection.minText() + " -> " + selection.maxText());
            }
            if (ImGui.button("Place Selection")) {
                placeSelection();
            }
            ImGui.sameLine();
            if (ImGui.button(deleteMode ? "Exit Delete Mode" : "Delete Mode")) {
                setDeleteMode(!deleteMode);
            }
            ImGui.sameLine();
            if (ImGui.button("Sync")) {
                AreaTipClient.requestSync();
                requestedInitialSync = true;
            }
            if (dirty) {
                ImGui.sameLine();
                ImGui.textColored(255, 210, 80, 255, "Unsaved");
            }
            if (deleteMode) {
                ImGui.sameLine();
                ImGui.textColored(255, 170, 70, 255, "Delete text area mode");
            }

            ImGui.separator();
            drawGroupSelector(config);
            group = selectedGroup(config);
            drawPlacedRanges(group);
            drawGroupEditor(config, group);
            flushIfNeeded(config, false);
        }

        private void refreshAxiomPlacedVisibility() {
            AreaTipConfig config = AreaTipConfig.getInstance();
            ensureGroup(config);
            AreaTipConfig.GroupConfig group = selectedGroup(config);
            AreaTipClient.keepAxiomGroupVisible(group.uuid());
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
            if (ImGui.button("Delete Group") && groups.size() > 1) {
                groups.remove(selectedIndex);
                selectedIndex = Math.clamp(selectedIndex, 0, groups.size() - 1);
                config.selectedGroupId = groups.get(selectedIndex).id;
                loadGroup(groups.get(selectedIndex));
                markDirty();
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

            ImGui.text("Message");
            float expandButtonX = ImGui.getCursorPosX() + Math.max(0.0F, ImGui.getContentRegionAvailX() - 34.0F);
            ImGui.sameLine(expandButtonX);
            if (ImGui.smallButton(messageExpanded ? "[-]##area_tip_message_expand" : "[+]##area_tip_message_expand")) {
                messageExpanded = !messageExpanded;
            }
            if (messageExpanded && ImGui.isKeyPressed(ImGuiKey.Escape)) {
                messageExpanded = false;
            }
            float messageWidth = messageExpanded ? Math.max(360.0F, ImGui.getContentRegionAvailX() * 2.0F) : 0.0F;
            float messageHeight = messageExpanded ? 240.0F : 120.0F;
            if (ImGui.inputTextMultiline("##area_tip_message", messageInput, messageWidth, messageHeight)) {
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

        private void drawPlacedRanges(AreaTipConfig.GroupConfig group) {
            List<AreaTipClient.AreaView> ranges = AreaTipClient.areasForGroup(group.uuid());
            ImGui.separatorText("Placed Ranges: " + ranges.size());
            if (ImGui.beginChild("##area_tip_ranges", 0.0F, 84.0F, true)) {
                int limit = Math.min(ranges.size(), 80);
                for (int i = 0; i < limit; i++) {
                    ImGui.textDisabled("#" + (i + 1) + "  " + ranges.get(i).boundsText());
                }
                if (ranges.size() > limit) {
                    ImGui.textDisabled("... " + (ranges.size() - limit) + " more");
                }
            }
            ImGui.endChild();
        }

        private boolean placeSelection() {
            return placeSelection(currentSelection());
        }

        private boolean placeSelection(SelectionBlocks selection) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (selection == null || selection.blocks().isEmpty()) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("\u00a7cNo Axiom selection"), true);
                }
                return false;
            }
            flushIfNeeded(AreaTipConfig.getInstance(), true);
            return AreaTipClient.placeCurrentGroupSelection(selection.blocks());
        }

        private boolean deleteSelection() {
            return deleteSelection(currentSelection());
        }

        private boolean deleteSelection(SelectionBlocks selection) {
            if (!deleteMode) {
                return false;
            }
            return handleDeleteSelection(selection);
        }

        private boolean handleDeleteSelection() {
            return handleDeleteSelection(currentSelection());
        }

        private boolean handleDeleteSelection(SelectionBlocks selection) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (selection == null || selection.blocks().isEmpty()) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("\u00a7cNo Axiom selection"), true);
                }
                Selection.clearSelectionNoHistory();
                setDeleteMode(false);
                return true;
            }
            AreaTipConfig config = AreaTipConfig.getInstance();
            ensureGroup(config);
            AreaTipConfig.GroupConfig group = selectedGroup(config);
            List<BlockPos> filtered = filterDeleteSelection(group.uuid(), selection);
            if (filtered.isEmpty()) {
                Selection.clearSelectionNoHistory();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§e当前没有可删除的文字区域"), true);
                }
                setDeleteMode(false);
                return true;
            }
            if (!sameBlocks(selection.blocks(), filtered)) {
                setSelection(filtered);
            }
            flushIfNeeded(config, true);
            boolean sent = AreaTipClient.deleteCurrentGroupSelection(filtered);
            if (sent) {
                Selection.clearSelectionNoHistory();
                clearDeleteSelectionCache();
            }
            setDeleteMode(false);
            return sent;
        }

        private void requestInitialSyncIfNeeded() {
            if (!requestedInitialSync) {
                AreaTipClient.requestSync();
                requestedInitialSync = true;
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

    private static void tickDeleteMode(MinecraftClient client) {
        if (!deleteMode || client == null || client.player == null || client.world == null) {
            return;
        }
        AreaTipConfig config = AreaTipConfig.getInstance();
        ensureGroup(config);
        AreaTipConfig.GroupConfig group = selectedGroup(config);
        AreaTipClient.keepAxiomGroupVisible(group.uuid());

        SelectionBlocks selection = currentSelection();
        if (selection == null) {
            clearDeleteSelectionCache();
            return;
        }
        List<BlockPos> filtered = cachedFilterDeleteSelection(group.uuid(), selection);
        if (filtered.isEmpty()) {
            if (!selection.blocks().isEmpty()
                    && System.currentTimeMillis() - lastDeleteSelectionChangedAt >= DELETE_EMPTY_CLEAR_DELAY_MS) {
                Selection.clearSelectionNoHistory();
                clearDeleteSelectionCache();
            }
            return;
        }
        if (!sameBlocks(selection.blocks(), filtered)) {
            setSelection(filtered);
        }
    }

    public static void setDeleteMode(boolean enabled) {
        if (deleteMode == enabled) {
            return;
        }
        deleteMode = enabled;
        clearDeleteSelectionCache();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(enabled ? "§e当前为删除文字区域模式" : "§7已退出删除文字区域模式"), true);
        }
        if (enabled) {
            AreaTipClient.requestSync();
            tickDeleteMode(client);
        } else {
            Selection.clearSelectionNoHistory();
        }
    }

    public static boolean handleDeleteModeDelete() {
        if (!deleteMode) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            setDeleteMode(false);
            return true;
        }
        AreaTipConfig config = AreaTipConfig.getInstance();
        ensureGroup(config);
        AreaTipConfig.GroupConfig group = selectedGroup(config);
        AreaTipClient.keepAxiomGroupVisible(group.uuid());

        SelectionBlocks selection = currentSelection();
        if (selection == null || selection.blocks().isEmpty()) {
            client.player.sendMessage(Text.literal("§cNo Axiom selection"), true);
            Selection.clearSelectionNoHistory();
            setDeleteMode(false);
            return true;
        }
        List<BlockPos> filtered = filterDeleteSelection(group.uuid(), selection);
        if (filtered.isEmpty()) {
            client.player.sendMessage(Text.literal("§e当前没有可删除的文字区域"), true);
            Selection.clearSelectionNoHistory();
            setDeleteMode(false);
            return true;
        }
        if (!sameBlocks(selection.blocks(), filtered)) {
            setSelection(filtered);
        }
        boolean sent = AreaTipClient.deleteCurrentGroupSelection(filtered);
        if (sent) {
            Selection.clearSelectionNoHistory();
            clearDeleteSelectionCache();
        }
        setDeleteMode(false);
        return sent;
    }

    private static List<BlockPos> cachedFilterDeleteSelection(UUID groupId, SelectionBlocks selection) {
        DeleteSelectionKey key = DeleteSelectionKey.of(groupId, AreaTipClient.areasRevision(), selection);
        if (key.equals(lastDeleteSelectionKey)) {
            return lastDeleteFilteredBlocks;
        }
        lastDeleteSelectionKey = key;
        lastDeleteSelectionChangedAt = System.currentTimeMillis();
        lastDeleteFilteredBlocks = filterDeleteSelection(groupId, selection);
        return lastDeleteFilteredBlocks;
    }

    private static void clearDeleteSelectionCache() {
        lastDeleteSelectionKey = null;
        lastDeleteFilteredBlocks = List.of();
        lastDeleteSelectionChangedAt = 0L;
    }

    private static void setSelection(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            Selection.clearSelectionNoHistory();
            return;
        }
        PositionSet positionSet = new PositionSet();
        for (BlockPos block : blocks) {
            positionSet.add(block.getX(), block.getY(), block.getZ());
        }
        ChunkedBooleanRegion region = new ChunkedBooleanRegion(positionSet);
        SelectionBuffer buffer = new SelectionBuffer.Set(region);
        Selection.setBuffer(buffer);
    }

    private static List<BlockPos> filterDeleteSelection(UUID groupId, SelectionBlocks selection) {
        if (groupId == null || selection == null || selection.blocks().isEmpty()) {
            return List.of();
        }
        List<AreaTipClient.AreaView> ranges = AreaTipClient.areasForGroup(groupId);
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<BlockPos> result = new ArrayList<>();
        HashSet<BlockPos> seen = new HashSet<>();
        Set<BlockPos> selectedBlocks = new HashSet<>(selection.blocks());
        for (AreaTipClient.AreaView range : ranges) {
            if (!intersects(range.minBlock(), range.maxBlock(), selection.min(), selection.max())) {
                continue;
            }
            List<BlockPos> coveredBlocks = range.coveredBlocks();
            for (BlockPos block : coveredBlocks) {
                if (!selectedBlocks.contains(block)) {
                    continue;
                }
                if (result.size() >= AreaTipAxiomTool.MAX_SELECTION_BLOCKS) {
                    return List.copyOf(result);
                }
                if (seen.add(block)) {
                    result.add(block);
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean sameBlocks(List<BlockPos> first, List<BlockPos> second) {
        if (first == null || second == null || first.size() != second.size()) {
            return false;
        }
        return new HashSet<>(first).equals(new HashSet<>(second));
    }

    private static boolean intersectsAnyBlock(List<BlockPos> first, Set<BlockPos> second) {
        if (first == null || first.isEmpty() || second == null || second.isEmpty()) {
            return false;
        }
        for (BlockPos block : first) {
            if (second.contains(block)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersects(BlockPos firstMin, BlockPos firstMax, BlockPos secondMin, BlockPos secondMax) {
        return firstMin.getX() <= secondMax.getX() && firstMax.getX() >= secondMin.getX()
                && firstMin.getY() <= secondMax.getY() && firstMax.getY() >= secondMin.getY()
                && firstMin.getZ() <= secondMax.getZ() && firstMax.getZ() >= secondMin.getZ();
    }

    private static void ensureGroup(AreaTipConfig config) {
        if (config.groups.isEmpty()) {
            AreaTipConfig.GroupConfig group = new AreaTipConfig.GroupConfig();
            group.id = UUID.randomUUID().toString();
            group.name = "Default";
            config.groups.add(group);
            config.selectedGroupId = group.id;
        }
    }

    private static AreaTipConfig.GroupConfig selectedGroup(AreaTipConfig config) {
        ensureGroup(config);
        UUID selected = config.selectedGroup().map(AreaTipConfig.GroupConfig::uuid).orElse(null);
        if (selected != null) {
            for (AreaTipConfig.GroupConfig group : config.groups) {
                if (selected.equals(group.uuid())) {
                    return group;
                }
            }
        }
        return config.groups.get(0);
    }

    private static SelectionSummary currentSelectionSummary() {
        try {
            Class<?> selectionClass = Class.forName("com.moulberry.axiom.clipboard.Selection");
            Class<?> bufferClass = Class.forName("com.moulberry.axiom.clipboard.SelectionBuffer");
            Method getSelectionBuffer = selectionClass.getMethod("getSelectionBuffer");
            Object buffer = getSelectionBuffer.invoke(null);
            if (buffer == null || (boolean) bufferClass.getMethod("isEmpty").invoke(buffer)) {
                return null;
            }
            int totalBlocks = ((Number) bufferClass.getMethod("size").invoke(buffer)).intValue();
            BlockPos min = ((BlockPos) bufferClass.getMethod("min").invoke(buffer)).toImmutable();
            BlockPos max = ((BlockPos) bufferClass.getMethod("max").invoke(buffer)).toImmutable();
            return new SelectionSummary(min, max, totalBlocks);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static SelectionBlocks currentSelection() {
        try {
            Class<?> selectionClass = Class.forName("com.moulberry.axiom.clipboard.Selection");
            Class<?> bufferClass = Class.forName("com.moulberry.axiom.clipboard.SelectionBuffer");
            Method getSelectionBuffer = selectionClass.getMethod("getSelectionBuffer");
            Object buffer = getSelectionBuffer.invoke(null);
            if (buffer == null || (boolean) bufferClass.getMethod("isEmpty").invoke(buffer)) {
                return null;
            }
            int totalBlocks = ((Number) bufferClass.getMethod("size").invoke(buffer)).intValue();
            BlockPos min = ((BlockPos) bufferClass.getMethod("min").invoke(buffer)).toImmutable();
            BlockPos max = ((BlockPos) bufferClass.getMethod("max").invoke(buffer)).toImmutable();
            List<BlockPos> blocks = new ArrayList<>(Math.min(totalBlocks, AreaTipAxiomTool.MAX_SELECTION_BLOCKS));
            TriIntConsumer consumer = (x, y, z) -> {
                if (blocks.size() < AreaTipAxiomTool.MAX_SELECTION_BLOCKS) {
                    blocks.add(new BlockPos(x, y, z));
                }
            };
            bufferClass.getMethod("forEach", TriIntConsumer.class).invoke(buffer, consumer);
            return new SelectionBlocks(min, max, blocks, totalBlocks);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private record DeleteSelectionKey(UUID groupId, long areasRevision, BlockPos min, BlockPos max,
                                      int totalBlocks, int blockCount, int blocksHash) {
        private static DeleteSelectionKey of(UUID groupId, long areasRevision, SelectionBlocks selection) {
            return new DeleteSelectionKey(
                    groupId,
                    areasRevision,
                    selection.min(),
                    selection.max(),
                    selection.totalBlocks(),
                    selection.blocks().size(),
                    new HashSet<>(selection.blocks()).hashCode()
            );
        }
    }

    private record SelectionSummary(BlockPos min, BlockPos max, int totalBlocks) {
        private SelectionSummary {
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

        private boolean wasTruncated() {
            return totalBlocks > AreaTipAxiomTool.MAX_SELECTION_BLOCKS;
        }

        private static String posText(BlockPos pos) {
            return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }
    }

    private record SelectionBlocks(BlockPos min, BlockPos max, List<BlockPos> blocks, int totalBlocks) {
        private SelectionBlocks {
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
            blocks = sanitizeBlocks(blocks);
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

        private boolean wasTruncated() {
            return totalBlocks > blocks.size();
        }

        private static String posText(BlockPos pos) {
            return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }

        private static List<BlockPos> sanitizeBlocks(List<BlockPos> blocks) {
            if (blocks == null || blocks.isEmpty()) {
                return List.of();
            }
            List<BlockPos> sanitized = new ArrayList<>(Math.min(blocks.size(), AreaTipAxiomTool.MAX_SELECTION_BLOCKS));
            HashSet<BlockPos> seen = new HashSet<>();
            for (BlockPos block : blocks) {
                if (block == null) {
                    continue;
                }
                BlockPos immutable = block.toImmutable();
                if (seen.add(immutable)) {
                    sanitized.add(immutable);
                }
                if (sanitized.size() >= AreaTipAxiomTool.MAX_SELECTION_BLOCKS) {
                    break;
                }
            }
            return List.copyOf(sanitized);
        }
    }
}
