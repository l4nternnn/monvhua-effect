package com.kuilunfuzhe.monvhua.features.hot_backpack_save;

import com.google.gson.JsonParser;
import com.kuilunfuzhe.monvhua.event.tag_pitch;
import com.kuilunfuzhe.monvhua.gui.CombinedConfigScreen;
import com.kuilunfuzhe.monvhua.network.hot_backpack_save.HotBackpackPackets;
import com.mojang.serialization.JsonOps;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.PopupWindow;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class HotBackpackSaveFragment extends Fragment {
    private static final int LEFT_WIDTH = 210;
    private static final int RIGHT_WIDTH = 270;
    private static final int SELF_PANEL_HEIGHT = 510;
    private static final int MIN_LEFT_WIDTH = 140;
    private static final int MIN_RIGHT_WIDTH = 170;
    private static final int MAX_SIDE_WIDTH = 620;
    private static final int MIN_SELF_PANEL_HEIGHT = 170;
    private static final int MAX_SELF_PANEL_HEIGHT = 760;
    private static final int RESIZE_HANDLE_SIZE = 6;
    private static final int SLOT_SIZE = 24;
    private static final int SLOT_GAP = 2;
    private static final int SLOT_PADDING = 4;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withLocale(Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static HotBackpackSaveFragment active;

    private final Screen parent;
    private final List<InventorySlotsView> slotGrids = new ArrayList<>();
    private CreativePickerView creativePicker;
    private HotBackpackState state = HotBackpackSaveClient.state();
    private String selectedUuid = "";
    private long selectedTimestamp;
    private String applyTargetUuid = "";
    private ItemStack cursorStack = ItemStack.EMPTY;
    private int leftPaneWidth = LEFT_WIDTH;
    private int rightPaneWidth = RIGHT_WIDTH;
    private int selfPaneHeight = SELF_PANEL_HEIGHT;

    private FrameLayout root;
    private View leftPane;
    private View centerPane;
    private View rightPaneView;
    private View archiveScrollView;
    private View selfScrollView;
    private LinearLayout.LayoutParams leftPaneParams;
    private LinearLayout.LayoutParams rightPaneParams;
    private LinearLayout.LayoutParams selfScrollParams;
    private LinearLayout playerList;
    private LinearLayout archiveSlots;
    private LinearLayout selfSlots;
    private LinearLayout rightPanel;
    private LinearLayout historyList;
    private TextView statusLabel;
    private TextView selectedLabel;
    private PopupWindow targetPopup;
    private PopupWindow creativeListPopup;
    private long resizeEwCursor;
    private long resizeNsCursor;

    public HotBackpackSaveFragment(Screen parent) {
        this.parent = parent;
    }

    public static void open(Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(MuiModApi.get().createScreen(new HotBackpackSaveFragment(parent), NON_PAUSING_CALLBACK, parent)));
        }
    }

    public static void receiveState(HotBackpackState nextState) {
        if (active != null) {
            active.state = nextState == null ? new HotBackpackState() : nextState;
            active.state.sanitize();
            active.ensureSelection();
            active.scheduleRebuildAll();
        }
    }

    private static final ScreenCallback NON_PAUSING_CALLBACK = new ScreenCallback() {
        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean hasDefaultBackground() {
            return false;
        }

        @Override
        public boolean shouldBlurBackground() {
            return false;
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context ctx = getContext();
        root = new FrameLayout(ctx);
        root.setBackground(new ColorDrawable(0xF00F1115));

        LinearLayout main = new LinearLayout(ctx);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setPadding(10, 10, 10, 10);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        leftPane = createLeftPanel(ctx);
        centerPane = createCenterPanel(ctx);
        rightPaneView = createRightPanel(ctx);
        leftPaneParams = new LinearLayout.LayoutParams(leftPaneWidth, -1);
        rightPaneParams = new LinearLayout.LayoutParams(rightPaneWidth, -1);
        main.addView(leftPane, leftPaneParams);
        main.addView(resizeHandle(ctx, ResizeKind.LEFT_WIDTH), new LinearLayout.LayoutParams(RESIZE_HANDLE_SIZE, -1));
        main.addView(centerPane, new LinearLayout.LayoutParams(0, -1, 1.0F));
        main.addView(resizeHandle(ctx, ResizeKind.RIGHT_WIDTH), new LinearLayout.LayoutParams(RESIZE_HANDLE_SIZE, -1));
        main.addView(rightPaneView, rightPaneParams);

        statusLabel = label(ctx, "", 11, 0xFFB6C0CC);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-2, -2, Gravity.LEFT | Gravity.BOTTOM);
        statusParams.setMargins(12, 0, 0, 8);
        root.addView(statusLabel, statusParams);

        ensureSelection();
        rebuildAll();
        HotBackpackSaveClient.requestState();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        active = this;
        HotBackpackSaveClient.requestState();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (active == this) {
            active = null;
        }
        dismissTargetPopup();
        dismissCreativeListPopup();
        resetResizeCursor();
    }

    private View createLeftPanel(Context ctx) {
        LinearLayout panel = panel(ctx);
        panel.addView(title(ctx, "玩家列表"), blockParams());
        ScrollView scroll = new ScrollView(ctx);
        scroll.setBackground(panelShape(0x441A2028, 0x553A4350));
        playerList = vertical(ctx);
        playerList.setPadding(4, 4, 4, 4);
        scroll.addView(playerList, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0F));
        return panel;
    }

    private View createCenterPanel(Context ctx) {
        LinearLayout panel = panel(ctx);
        selectedLabel = title(ctx, "存档预览");
        panel.addView(selectedLabel, blockParams());

        ScrollView archiveScroll = new ScrollView(ctx);
        archiveScroll.setBackground(panelShape(0x441A2028, 0x553A4350));
        archiveScroll.setClipChildren(true);
        archiveScroll.setClipToPadding(true);
        archiveScrollView = archiveScroll;
        archiveSlots = vertical(ctx);
        archiveSlots.setPadding(8, 8, 8, 8);
        archiveScroll.addView(archiveSlots, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(archiveScroll, new LinearLayout.LayoutParams(-1, 0, 1.0F));
        panel.addView(resizeHandle(ctx, ResizeKind.SELF_HEIGHT), new LinearLayout.LayoutParams(-1, RESIZE_HANDLE_SIZE));

        panel.addView(title(ctx, "我的背包"), blockParams());
        FrameLayout selfPanel = new FrameLayout(ctx);
        selfPanel.setBackground(panelShape(0x441A2028, 0x553A4350));
        selfPanel.setClipChildren(true);
        selfPanel.setClipToPadding(true);
        selfScrollView = selfPanel;
        selfSlots = vertical(ctx);
        selfSlots.setPadding(8, 8, 8, 8);
        selfPanel.addView(selfSlots, new FrameLayout.LayoutParams(-1, -2));
        selfScrollParams = new LinearLayout.LayoutParams(-1, selfPaneHeight);
        panel.addView(selfPanel, selfScrollParams);
        return panel;
    }

    private View createRightPanel(Context ctx) {
        LinearLayout panel = panel(ctx);
        rightPanel = panel;
        return panel;
    }

    private void ensureSelection() {
        state.sanitize();
        if (!selectedUuid.isBlank() && state.records.containsKey(selectedUuid)) {
            ensureTimestamp();
            return;
        }
        selectedUuid = "";
        selectedTimestamp = 0L;
        List<HotBackpackState.PlayerRecord> records = sortedRecords();
        if (!records.isEmpty()) {
            selectedUuid = records.get(0).uuid;
            ensureTimestamp();
        }
    }

    private void ensureTimestamp() {
        HotBackpackState.PlayerRecord record = state.records.get(selectedUuid);
        if (record == null || record.history.isEmpty()) {
            selectedTimestamp = 0L;
            return;
        }
        for (HotBackpackState.Snapshot snapshot : record.history) {
            if (snapshot.timestamp == selectedTimestamp) {
                return;
            }
        }
        selectedTimestamp = record.history.get(0).timestamp;
    }

    private void rebuildAll() {
        if (playerList == null) {
            return;
        }
        try {
            slotGrids.clear();
            rebuildPlayers();
            rebuildArchiveSlots();
            rebuildSelfSlots();
            rebuildRight();
            updateStatus("就绪");
        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("玩家存档界面刷新失败: " + e.getClass().getSimpleName());
        }
    }

    private void rebuildPlayers() {
        playerList.removeAllViews();
        Context ctx = getContext();
        for (HotBackpackState.PlayerRecord record : sortedRecords()) {
            String label = (record.online ? "● " : "○ ") + safeName(record) + roleSuffix(record);
            Button row = button(ctx, label);
            boolean selected = record.uuid.equals(selectedUuid);
            row.setTextColor(playerRowColor(record, selected));
            row.setBackground(panelShape(selected ? 0xAA3A5268 : 0x332A3038, 0x553A4350));
            row.setOnClickListener(v -> {
                selectedUuid = record.uuid;
                selectedTimestamp = 0L;
                ensureTimestamp();
                dismissTargetPopup();
                dismissCreativeListPopup();
                scheduleRebuildAll();
            });
            playerList.addView(row, blockParams());
        }
    }

    private void rebuildArchiveSlots() {
        archiveSlots.removeAllViews();
        HotBackpackState.Snapshot snapshot = selectedSnapshot();
        if (selectedLabel != null) {
            selectedLabel.setText(snapshot == null ? "存档预览" : safe(snapshot.name) + " / " + time(snapshot.timestamp));
        }
        if (snapshot == null) {
            archiveSlots.addView(label(getContext(), "当前玩家还没有存档", 12, 0xFFB6C0CC), blockParams());
            return;
        }
        InventorySlotsView grid = new InventorySlotsView(getContext(), true);
        slotGrids.add(grid);
        archiveSlots.addView(grid, new LinearLayout.LayoutParams(-1, grid.preferredHeight()));
        archiveSlots.addView(label(getContext(), "Tags: " + String.join(", ", snapshot.tags), 11, 0xFF9FB0C0), blockParams());
        archiveSlots.addView(label(getContext(), "状态: HP " + snapshot.health + " / 饥饿 " + snapshot.food + " / XP Lv." + snapshot.experienceLevel, 11, 0xFF9FB0C0), blockParams());
    }

    private void rebuildSelfSlots() {
        selfSlots.removeAllViews();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            selfSlots.addView(label(getContext(), "未进入世界", 12, 0xFFB6C0CC), blockParams());
            return;
        }
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        InventorySlotsView grid = new InventorySlotsView(getContext(), false);
        slotGrids.add(grid);
        row.addView(grid, new LinearLayout.LayoutParams(0, grid.preferredHeight(), 1.0F));
        creativePicker = new CreativePickerView(getContext());
        row.addView(creativePicker, new LinearLayout.LayoutParams(creativePicker.preferredWidth(), creativePicker.preferredHeight()));
        selfSlots.addView(row, new LinearLayout.LayoutParams(-1, Math.max(grid.preferredHeight(), creativePicker.preferredHeight())));
    }

    private void rebuildRight() {
        Context ctx = getContext();
        rightPanel.removeAllViews();
        rightPanel.addView(title(ctx, "功能区"), blockParams());

        Button refresh = button(ctx, "刷新");
        refresh.setOnClickListener(v -> HotBackpackSaveClient.requestState());
        rightPanel.addView(refresh, blockParams());

        Button saveSpecial = button(ctx, "保存特殊玩家");
        saveSpecial.setOnClickListener(v -> ClientPlayNetworking.send(new HotBackpackPackets.SaveSpecialPlayersC2S()));
        rightPanel.addView(saveSpecial, blockParams());

        Button saveAll = button(ctx, "存档当前所有玩家");
        saveAll.setOnClickListener(v -> ClientPlayNetworking.send(new HotBackpackPackets.SaveAllPlayersC2S()));
        rightPanel.addView(saveAll, blockParams());

        Button copy = button(ctx, "复制");
        copy.setOnClickListener(v -> updateStatus("已选中当前预览，可粘贴或应用"));
        rightPanel.addView(copy, blockParams());

        Button paste = button(ctx, "粘贴到自己");
        paste.setOnClickListener(v -> {
            HotBackpackState.Snapshot snapshot = selectedSnapshot();
            if (snapshot != null) {
                UUID sourceId = parseUuid(snapshot.uuid);
                if (sourceId != null) {
                    ClientPlayNetworking.send(new HotBackpackPackets.ApplySnapshotToSelfC2S(sourceId, snapshot.timestamp));
                }
            }
        });
        rightPanel.addView(paste, blockParams());

        Button applyCurrent = button(ctx, "应用当前玩家存档");
        applyCurrent.setOnClickListener(v -> {
            HotBackpackState.Snapshot snapshot = selectedSnapshot();
            if (snapshot != null) {
                UUID sourceId = parseUuid(snapshot.uuid);
                if (sourceId != null) {
                    ClientPlayNetworking.send(new HotBackpackPackets.ApplySnapshotC2S(sourceId, snapshot.timestamp, sourceId));
                }
            }
        });
        rightPanel.addView(applyCurrent, blockParams());

        Button applyTo = button(ctx, "应用当前存档到");
        applyTo.setOnClickListener(this::showTargetPopup);
        rightPanel.addView(applyTo, blockParams());

        Button undo = button(ctx, "撤回粘贴");
        undo.setOnClickListener(v -> {
            String target = applyTargetUuid.isBlank() ? selectedUuid : applyTargetUuid;
            UUID targetId = parseUuid(target);
            if (targetId != null) {
                ClientPlayNetworking.send(new HotBackpackPackets.UndoApplyC2S(targetId));
            }
        });
        rightPanel.addView(undo, blockParams());

        rightPanel.addView(title(ctx, "历史记录"), blockParams());
        ScrollView historyScroll = new ScrollView(ctx);
        historyScroll.setBackground(panelShape(0x441A2028, 0x553A4350));
        historyList = vertical(ctx);
        historyList.setPadding(4, 4, 4, 4);
        historyScroll.addView(historyList, new FrameLayout.LayoutParams(-1, -2));
        rightPanel.addView(historyScroll, new LinearLayout.LayoutParams(-1, 0, 1.0F));
        rebuildHistory();

        Button back = button(ctx, "返回");
        back.setOnClickListener(v -> closeToConfig());
        rightPanel.addView(back, blockParams());
    }

    private void showTargetPopup(View anchor) {
        HotBackpackState.Snapshot snapshot = selectedSnapshot();
        if (snapshot == null || root == null) {
            updateStatus("当前玩家还没有存档");
            return;
        }
        dismissTargetPopup();
        Context ctx = getContext();
        LinearLayout panel = panel(ctx);
        panel.addView(title(ctx, "应用当前存档到"), blockParams());
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout list = vertical(ctx);
        list.setPadding(4, 4, 4, 4);
        for (HotBackpackState.PlayerRecord record : sortedRecords()) {
            Button row = button(ctx, (record.uuid.equals(applyTargetUuid) ? "✓ " : "") + safeName(record));
            row.setTextColor(playerRowColor(record, false));
            row.setOnClickListener(v -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (!record.uuid.equals(applyTargetUuid)) {
                    applyTargetUuid = record.uuid;
                    row.setText("✓ " + safeName(record));
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("再次点击确认应用到 " + safeName(record)), true);
                    }
                    return;
                }
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("已应用到 " + safeName(record)), true);
                }
                UUID sourceId = parseUuid(snapshot.uuid);
                UUID targetId = parseUuid(record.uuid);
                if (sourceId != null && targetId != null) {
                    ClientPlayNetworking.send(new HotBackpackPackets.ApplySnapshotC2S(sourceId, snapshot.timestamp, targetId));
                }
                dismissTargetPopup();
            });
            list.addView(row, blockParams());
        }
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 320));

        Button close = button(ctx, "关闭");
        close.setOnClickListener(v -> dismissTargetPopup());
        panel.addView(close, blockParams());

        targetPopup = new PopupWindow(panel, 320, 410, true);
        targetPopup.setBackgroundDrawable(new ColorDrawable(0xF0181B22));
        targetPopup.setOutsideTouchable(true);
        targetPopup.setFocusable(true);
        targetPopup.setOnDismissListener(() -> targetPopup = null);
        targetPopup.showAtLocation(root, Gravity.CENTER, 0, 0);
    }

    private void dismissTargetPopup() {
        if (targetPopup != null) {
            PopupWindow popup = targetPopup;
            targetPopup = null;
            popup.dismiss();
        }
    }

    private void showCreativeListPopup(CreativePickerView picker) {
        if (root == null) {
            return;
        }
        dismissCreativeListPopup();
        Context ctx = getContext();
        LinearLayout panel = panel(ctx);
        panel.addView(title(ctx, "更多创造分类"), blockParams());
        panel.addView(label(ctx, "选择一个分类后在右侧列表中拿取物品", 11, 0xFFB6C0CC), blockParams());

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout list = vertical(ctx);
        list.setPadding(4, 4, 4, 4);
        List<CreativeCategory> popupCategories = new ArrayList<>(picker.overflowTabs());
        if (!popupCategories.contains(CreativeCategory.OTHER)) {
            popupCategories.add(CreativeCategory.OTHER);
        }
        for (CreativeCategory category : popupCategories) {
            if (category == CreativeCategory.MORE || category == CreativeCategory.MODDED) {
                continue;
            }
            Button row = button(ctx, category.label);
            row.setTextColor(picker.isCategorySelected(category) ? 0xFFFFD36A : 0xFFE8EDF4);
            row.setOnClickListener(v -> {
                picker.selectCategory(category);
                dismissCreativeListPopup();
            });
            list.addView(row, blockParams());
        }
        List<String> namespaces = modNamespaces();
        if (!namespaces.isEmpty()) {
            list.addView(label(ctx, "模组", 12, 0xFFB6C0CC), blockParams());
            for (String namespace : namespaces) {
                Button row = button(ctx, displayNameForNamespace(namespace));
                row.setTextColor(picker.isModNamespaceSelected(namespace) ? 0xFFFFD36A : 0xFFE8EDF4);
                row.setOnClickListener(v -> {
                    picker.selectModNamespace(namespace);
                    dismissCreativeListPopup();
                });
                list.addView(row, blockParams());
            }
        }
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 330));

        Button close = button(ctx, "关闭");
        close.setOnClickListener(v -> dismissCreativeListPopup());
        panel.addView(close, blockParams());

        creativeListPopup = new PopupWindow(panel, 300, 430, true);
        creativeListPopup.setBackgroundDrawable(new ColorDrawable(0xF0181B22));
        creativeListPopup.setOutsideTouchable(true);
        creativeListPopup.setFocusable(true);
        creativeListPopup.setOnDismissListener(() -> creativeListPopup = null);
        creativeListPopup.showAtLocation(root, Gravity.CENTER, 0, 0);
    }

    private void dismissCreativeListPopup() {
        if (creativeListPopup != null) {
            PopupWindow popup = creativeListPopup;
            creativeListPopup = null;
            popup.dismiss();
        }
    }

    private void takeCreativeStack(ItemStack stack, boolean right, boolean middle) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (right && cursorStack != null && !cursorStack.isEmpty()) {
            cursorStack = ItemStack.EMPTY;
            refreshSlotGrids();
            if (creativePicker != null) {
                creativePicker.invalidate();
            }
            updateStatus("手上为空");
            return;
        }
        ItemStack copy = stack.copy();
        copy.setCount(right && !middle ? 1 : copy.getMaxCount());
        cursorStack = copy;
        refreshSlotGrids();
        if (creativePicker != null) {
            creativePicker.invalidate();
        }
        updateStatus("手上: " + cursorStack.getName().getString());
    }

    private List<String> modNamespaces() {
        Set<String> namespaces = new LinkedHashSet<>();
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (id != null && !"minecraft".equals(id.getNamespace()) && !"air".equals(id.getPath())) {
                namespaces.add(id.getNamespace());
            }
        }
        List<String> sorted = new ArrayList<>(namespaces);
        sorted.sort(Comparator.comparing(this::displayNameForNamespace, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(namespace -> namespace, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private String displayNameForNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return "未知模组";
        }
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getName())
                .filter(name -> name != null && !name.isBlank())
                .orElse(namespace);
    }

    private void rebuildHistory() {
        historyList.removeAllViews();
        HotBackpackState.PlayerRecord record = state.records.get(selectedUuid);
        if (record == null || record.history.isEmpty()) {
            historyList.addView(label(getContext(), "暂无历史", 12, 0xFFB6C0CC), blockParams());
            return;
        }
        for (HotBackpackState.Snapshot snapshot : record.history) {
            Button row = button(getContext(), time(snapshot.timestamp));
            row.setTextColor(snapshot.timestamp == selectedTimestamp ? 0xFFFFD36A : 0xFFE8EDF4);
            row.setOnClickListener(v -> {
                selectedTimestamp = snapshot.timestamp;
                scheduleRebuildAll();
            });
            row.setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP && event.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                    selectedTimestamp = snapshot.timestamp;
                    scheduleRebuildAll();
                    return true;
                }
                return false;
            });
            historyList.addView(row, blockParams());
        }
    }

    private void handleSlotClick(boolean archive, int slot, boolean right) {
        try {
            if (archive) {
                HotBackpackState.Snapshot snapshot = selectedSnapshot();
                if (snapshot == null) {
                    return;
                }
                snapshot.sanitize();
                ItemStack next = applyClick(itemFromJson(itemJsonAt(snapshot.items, slot)), right);
                snapshot.items.set(slot, itemToJson(next));
                UUID sourceId = parseUuid(snapshot.uuid);
                if (sourceId != null) {
                    ClientPlayNetworking.send(new HotBackpackPackets.EditPreviewSlotC2S(sourceId, snapshot.timestamp, slot, itemJsonAt(snapshot.items, slot)));
                }
            } else {
                ItemStack next = applyClick(selfSlotStack(slot), right);
                setClientSelfSlot(slot, next);
                ClientPlayNetworking.send(new HotBackpackPackets.EditOwnSlotC2S(slot, itemToJson(next)));
            }
            refreshSlotGrids();
            updateStatus(cursorStack.isEmpty() ? "手上为空" : "手上: " + cursorStack.getName().getString());
        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("槽位点击失败: " + e.getClass().getSimpleName());
        }
    }

    private ItemStack applyClick(ItemStack rawSlotStack, boolean right) {
        ItemStack slotStack = rawSlotStack == null ? ItemStack.EMPTY : rawSlotStack.copy();
        if (cursorStack == null) {
            cursorStack = ItemStack.EMPTY;
        }
        if (cursorStack.isEmpty()) {
            if (slotStack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (right) {
                int taken = (slotStack.getCount() + 1) / 2;
                cursorStack = slotStack.copy();
                cursorStack.setCount(taken);
                slotStack.decrement(taken);
                return slotStack.isEmpty() ? ItemStack.EMPTY : slotStack;
            }
            cursorStack = slotStack.copy();
            return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) {
            if (right) {
                ItemStack placed = cursorStack.copy();
                placed.setCount(1);
                cursorStack.decrement(1);
                normalizeCursor();
                return placed;
            }
            ItemStack placed = cursorStack.copy();
            cursorStack = ItemStack.EMPTY;
            return placed;
        }

        if (ItemStack.areItemsAndComponentsEqual(slotStack, cursorStack)) {
            int limit = slotStack.getMaxCount();
            if (right) {
                if (slotStack.getCount() < limit) {
                    slotStack.increment(1);
                    cursorStack.decrement(1);
                    normalizeCursor();
                }
                return slotStack;
            }
            int moved = Math.min(cursorStack.getCount(), limit - slotStack.getCount());
            if (moved > 0) {
                slotStack.increment(moved);
                cursorStack.decrement(moved);
                normalizeCursor();
            }
            return slotStack;
        }

        if (!right) {
            ItemStack oldSlot = slotStack.copy();
            ItemStack placed = cursorStack.copy();
            cursorStack = oldSlot;
            return placed;
        }
        return slotStack;
    }

    private void normalizeCursor() {
        if (cursorStack == null || cursorStack.isEmpty()) {
            cursorStack = ItemStack.EMPTY;
        }
    }

    private HotBackpackState.Snapshot selectedSnapshot() {
        HotBackpackState.PlayerRecord record = state.records.get(selectedUuid);
        if (record == null) {
            return null;
        }
        for (HotBackpackState.Snapshot snapshot : record.history) {
            if (snapshot.timestamp == selectedTimestamp) {
                snapshot.sanitize();
                return snapshot;
            }
        }
        if (record.history.isEmpty()) {
            return null;
        }
        HotBackpackState.Snapshot snapshot = record.history.get(0);
        snapshot.sanitize();
        selectedTimestamp = snapshot.timestamp;
        return snapshot;
    }

    private ItemStack archiveSlotStack(int slot) {
        HotBackpackState.Snapshot snapshot = selectedSnapshot();
        return snapshot == null ? ItemStack.EMPTY : itemFromJson(itemJsonAt(snapshot.items, slot));
    }

    private ItemStack selfSlotStack(int slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return ItemStack.EMPTY;
        }
        if (slot >= 0 && slot < 36) {
            return client.player.getInventory().getStack(slot).copy();
        }
        return switch (slot) {
            case 36 -> client.player.getEquippedStack(EquipmentSlot.FEET).copy();
            case 37 -> client.player.getEquippedStack(EquipmentSlot.LEGS).copy();
            case 38 -> client.player.getEquippedStack(EquipmentSlot.CHEST).copy();
            case 39 -> client.player.getEquippedStack(EquipmentSlot.HEAD).copy();
            case 40 -> client.player.getOffHandStack().copy();
            default -> ItemStack.EMPTY;
        };
    }

    private void setClientSelfSlot(int slot, ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        ItemStack copy = stack == null ? ItemStack.EMPTY : stack.copy();
        if (slot >= 0 && slot < 36) {
            client.player.getInventory().setStack(slot, copy);
        } else {
            switch (slot) {
                case 36 -> client.player.equipStack(EquipmentSlot.FEET, copy);
                case 37 -> client.player.equipStack(EquipmentSlot.LEGS, copy);
                case 38 -> client.player.equipStack(EquipmentSlot.CHEST, copy);
                case 39 -> client.player.equipStack(EquipmentSlot.HEAD, copy);
                case 40 -> client.player.equipStack(EquipmentSlot.OFFHAND, copy);
                default -> {
                }
            }
        }
        client.player.getInventory().markDirty();
    }

    private void refreshSlotGrids() {
        for (InventorySlotsView grid : slotGrids) {
            grid.invalidate();
        }
    }

    private List<HotBackpackState.PlayerRecord> sortedRecords() {
        List<HotBackpackState.PlayerRecord> records = new ArrayList<>(state.records.values());
        records.sort(Comparator
                .comparing((HotBackpackState.PlayerRecord r) -> !r.online)
                .thenComparing(r -> safe(r.name), String.CASE_INSENSITIVE_ORDER));
        return records;
    }

    private static ItemStack itemFromJson(String json) {
        if (json == null || json.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String itemToJson(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack).result().map(HotBackpackSaveClient.GSON::toJson).orElse("");
    }

    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status == null ? "" : status);
        }
    }

    private static String time(long timestamp) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    private static String safeName(HotBackpackState.PlayerRecord record) {
        String name = safe(record.name);
        return name.isBlank() ? record.uuid : name;
    }

    private static String roleSuffix(HotBackpackState.PlayerRecord record) {
        return record.roleTag == null || record.roleTag.isBlank() ? "" : " (" + tag_pitch.nameForTag(record.roleTag) + ")";
    }

    private static int playerRowColor(HotBackpackState.PlayerRecord record, boolean selected) {
        if (selected) {
            return 0xFFFFD36A;
        }
        int fallback = record.online ? 0xFFE8EDF4 : 0xFF97A1AE;
        if (record.roleTag == null || record.roleTag.isBlank()) {
            return fallback;
        }
        int rgb = tag_pitch.colorForTag(record.roleTag, fallback & 0x00FFFFFF);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void scheduleRebuildAll() {
        if (root == null) {
            rebuildAll();
            return;
        }
        root.post(this::rebuildAll);
    }

    private void scheduleRebuildRight() {
        if (root == null) {
            rebuildRight();
            return;
        }
        root.post(this::rebuildRight);
    }

    private void closeToConfig() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(CombinedConfigScreen::open);
        }
    }

    private static String itemJsonAt(List<String> items, int slot) {
        return items == null || slot < 0 || slot >= items.size() ? "" : items.get(slot);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private LinearLayout panel(Context ctx) {
        LinearLayout panel = vertical(ctx);
        panel.setPadding(8, 8, 8, 8);
        panel.setBackground(panelShape(0xE0181B22, 0xFF343B46));
        return panel;
    }

    private LinearLayout vertical(Context ctx) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView title(Context ctx, String text) {
        return label(ctx, text, 15, 0xFFE8EDF4);
    }

    private TextView label(Context ctx, String text, int size, int color) {
        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private Button button(Context ctx, String text) {
        Button button = new Button(ctx);
        button.setText(text);
        button.setTextColor(0xFFE8EDF4);
        button.setTextSize(12);
        button.setPadding(6, 2, 6, 2);
        button.setMinHeight(24);
        button.setBackground(panelShape(0x55313A4A, 0xB4AAB7C8));
        return button;
    }

    private View resizeHandle(Context ctx, ResizeKind kind) {
        return new ResizeHandleView(ctx, kind);
    }

    private void setResizeCursor(ResizeKind kind) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        long cursor = kind == ResizeKind.SELF_HEIGHT ? resizeNsCursor : resizeEwCursor;
        if (cursor == 0L) {
            cursor = GLFW.glfwCreateStandardCursor(kind == ResizeKind.SELF_HEIGHT
                    ? GLFW.GLFW_RESIZE_NS_CURSOR
                    : GLFW.GLFW_RESIZE_EW_CURSOR);
            if (kind == ResizeKind.SELF_HEIGHT) {
                resizeNsCursor = cursor;
            } else {
                resizeEwCursor = cursor;
            }
        }
        if (cursor != 0L) {
            GLFW.glfwSetCursor(client.getWindow().getHandle(), cursor);
        }
    }

    private void resetResizeCursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getWindow() != null) {
            GLFW.glfwSetCursor(client.getWindow().getHandle(), 0L);
        }
    }

    private void requestOuterScrollLock(boolean locked) {
        if (root != null && root.getParent() != null) {
            root.getParent().requestDisallowInterceptTouchEvent(locked);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 5);
        return params;
    }

    private static ShapeDrawable panelShape(int fill, int stroke) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setShape(ShapeDrawable.RECTANGLE);
        shape.setColor(fill);
        shape.setStroke(1, stroke);
        shape.setCornerRadius(3.0F);
        return shape;
    }

    private enum ResizeKind {
        LEFT_WIDTH,
        RIGHT_WIDTH,
        SELF_HEIGHT
    }

    private final class ResizeHandleView extends View {
        private final ResizeKind kind;
        private boolean hovered;
        private boolean dragging;
        private float startX;
        private float startY;
        private int startLeftWidth;
        private int startRightWidth;
        private int startSelfHeight;

        private ResizeHandleView(Context ctx, ResizeKind kind) {
            super(ctx);
            this.kind = kind;
            setBackground(new ColorDrawable(0x223A4350));
            setOnHoverListener(this::handleHover);
            setOnTouchListener(this::handleTouch);
        }

        private boolean handleHover(View view, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
                hovered = true;
                setResizeCursor(kind);
                updateBackground();
                return true;
            }
            if (action == MotionEvent.ACTION_HOVER_EXIT && !dragging) {
                hovered = false;
                resetResizeCursor();
                updateBackground();
                return true;
            }
            return false;
        }

        private boolean handleTouch(View view, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS) {
                dragging = true;
                hovered = true;
                startX = event.getX();
                startY = event.getY();
                startLeftWidth = leftPaneWidth;
                startRightWidth = rightPaneWidth;
                startSelfHeight = selfPaneHeight;
                setResizeCursor(kind);
                requestOuterScrollLock(true);
                updateBackground();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && dragging) {
                int deltaX = Math.round(event.getX() - startX);
                int deltaY = Math.round(event.getY() - startY);
                if (kind == ResizeKind.LEFT_WIDTH) {
                    leftPaneWidth = clamp(startLeftWidth + deltaX, MIN_LEFT_WIDTH, MAX_SIDE_WIDTH);
                    if (leftPaneParams != null && leftPane != null) {
                        leftPaneParams.width = leftPaneWidth;
                        leftPane.setLayoutParams(leftPaneParams);
                    }
                } else if (kind == ResizeKind.RIGHT_WIDTH) {
                    rightPaneWidth = clamp(startRightWidth - deltaX, MIN_RIGHT_WIDTH, MAX_SIDE_WIDTH);
                    if (rightPaneParams != null && rightPaneView != null) {
                        rightPaneParams.width = rightPaneWidth;
                        rightPaneView.setLayoutParams(rightPaneParams);
                    }
                } else {
                    selfPaneHeight = clamp(startSelfHeight - deltaY, MIN_SELF_PANEL_HEIGHT, MAX_SELF_PANEL_HEIGHT);
                    if (selfScrollParams != null && selfScrollView != null) {
                        selfScrollParams.height = selfPaneHeight;
                        selfScrollView.setLayoutParams(selfScrollParams);
                    }
                }
                updateBackground();
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE || action == MotionEvent.ACTION_CANCEL) {
                dragging = false;
                requestOuterScrollLock(false);
                if (hovered && action != MotionEvent.ACTION_CANCEL) {
                    setResizeCursor(kind);
                } else {
                    resetResizeCursor();
                }
                updateBackground();
                return true;
            }
            return false;
        }

        private void updateBackground() {
            if (dragging) {
                setBackground(new ColorDrawable(0xCCFFD36A));
            } else if (hovered) {
                setBackground(new ColorDrawable(0x88E6C56C));
            } else {
                setBackground(new ColorDrawable(0x223A4350));
            }
            invalidate();
        }
    }

    private enum CreativeCategory {
        ALL("全部", false),
        BUILDING("建筑", true),
        NATURAL("自然", true),
        FUNCTIONAL("功能", true),
        REDSTONE("红石", true),
        TOOLS("工具", true),
        COMBAT("战斗", true),
        FOOD("食物", true),
        INGREDIENTS("原料", true),
        SPAWN_EGGS("刷怪蛋", false),
        MODDED("模组", false),
        OTHER("其它", false),
        MORE("更多", false);

        private final String label;
        private final boolean visibleTab;

        CreativeCategory(String label, boolean visibleTab) {
            this.label = label;
            this.visibleTab = visibleTab;
        }
    }

    private final class CreativePickerView extends MinecraftSurfaceView {
        private static final int PICKER_WIDTH = 343;
        private static final int PICKER_HEIGHT = 492;
        private static final int PAD = 6;
        private static final int TAB_MIN_W = 42;
        private static final int TAB_H = 18;
        private static final int SLOT = 22;
        private static final int STEP = 24;
        private static final int GRID_X = PAD;
        private static final int TAB_ROWS = 2;
        private static final int TAB_GAP = 2;
        private static final int GRID_Y = PAD + TAB_H * TAB_ROWS + 12;
        private static final int COLS = 13;
        private static final int SCROLL_W = 5;

        private final List<ItemStack> items = new ArrayList<>();
        private final int[] locationInWindow = new int[2];
        private CreativeCategory category = CreativeCategory.BUILDING;
        private String selectedModNamespace = "";
        private double guiScale = 1.0D;
        private int screenLeft;
        private int screenTop;
        private int guiWidth = PICKER_WIDTH;
        private int guiHeight = PICKER_HEIGHT;
        private int scrollRow;
        private boolean draggingScrollbar;
        private boolean lastRight;
        private boolean lastMiddle;

        private CreativePickerView(Context ctx) {
            super(ctx);
            setRenderer(new CreativePickerRenderer());
            setFocusable(true);
            setFocusableInTouchMode(true);
            setOnTouchListener(this::handleTouch);
            setOnGenericMotionListener(this::handleGenericMotion);
            rebuildItems();
        }

        private int preferredWidth() {
            return (int) Math.ceil(PICKER_WIDTH * currentGuiScale());
        }

        private int preferredHeight() {
            return (int) Math.ceil(PICKER_HEIGHT * currentGuiScale());
        }

        private void selectCategory(CreativeCategory nextCategory) {
            if (nextCategory == null || nextCategory == CreativeCategory.MORE) {
                return;
            }
            category = nextCategory;
            selectedModNamespace = "";
            scrollRow = 0;
            rebuildItems();
            invalidate();
        }

        private void selectModNamespace(String namespace) {
            if (namespace == null || namespace.isBlank()) {
                return;
            }
            category = CreativeCategory.MODDED;
            selectedModNamespace = namespace;
            scrollRow = 0;
            rebuildItems();
            invalidate();
        }

        private boolean isCategorySelected(CreativeCategory checkedCategory) {
            return category == checkedCategory && selectedModNamespace.isBlank();
        }

        private boolean isModNamespaceSelected(String namespace) {
            return category == CreativeCategory.MODDED && namespace != null && namespace.equals(selectedModNamespace);
        }

        private boolean isTopTabSelected(CreativeCategory checkedCategory) {
            if (checkedCategory == CreativeCategory.MORE) {
                return false;
            }
            if (checkedCategory == CreativeCategory.MODDED) {
                return category == CreativeCategory.MODDED;
            }
            return isCategorySelected(checkedCategory);
        }

        private String currentCategoryLabel() {
            if (category == CreativeCategory.MODDED && !selectedModNamespace.isBlank()) {
                return displayNameForNamespace(selectedModNamespace);
            }
            return category.label;
        }

        private void rebuildItems() {
            items.clear();
            for (Item item : Registries.ITEM) {
                Identifier id = Registries.ITEM.getId(item);
                if (id == null || "air".equals(id.getPath())) {
                    continue;
                }
                ItemStack stack = item.getDefaultStack();
                if (!stack.isEmpty() && matchesCategory(category, item, id)) {
                    items.add(stack);
                }
            }
            items.sort(Comparator
                    .comparing((ItemStack stack) -> !"minecraft".equals(itemId(stack).getNamespace()))
                    .thenComparing(stack -> itemId(stack).getNamespace())
                    .thenComparing(stack -> itemId(stack).getPath()));
            clampScroll();
        }

        private Identifier itemId(ItemStack stack) {
            return Registries.ITEM.getId(stack.getItem());
        }

        private boolean matchesCategory(CreativeCategory category, Item item, Identifier id) {
            if (category == CreativeCategory.ALL) {
                return true;
            }
            if (category == CreativeCategory.MODDED) {
                if (!selectedModNamespace.isBlank()) {
                    return selectedModNamespace.equals(id.getNamespace());
                }
                return !"minecraft".equals(id.getNamespace());
            }
            if (category == CreativeCategory.OTHER) {
                return !matchesListedCategory(item, id);
            }
            if (category == CreativeCategory.MORE) {
                return false;
            }
            return matchesSingleCategory(category, item, id);
        }

        private boolean matchesListedCategory(Item item, Identifier id) {
            return matchesSingleCategory(CreativeCategory.BUILDING, item, id)
                    || matchesSingleCategory(CreativeCategory.NATURAL, item, id)
                    || matchesSingleCategory(CreativeCategory.FUNCTIONAL, item, id)
                    || matchesSingleCategory(CreativeCategory.REDSTONE, item, id)
                    || matchesSingleCategory(CreativeCategory.TOOLS, item, id)
                    || matchesSingleCategory(CreativeCategory.COMBAT, item, id)
                    || matchesSingleCategory(CreativeCategory.FOOD, item, id)
                    || matchesSingleCategory(CreativeCategory.INGREDIENTS, item, id)
                    || matchesSingleCategory(CreativeCategory.SPAWN_EGGS, item, id);
        }

        private boolean matchesSingleCategory(CreativeCategory category, Item item, Identifier id) {
            String path = id.getPath();
            return switch (category) {
                case BUILDING -> item instanceof BlockItem
                        && !matchesSingleCategory(CreativeCategory.NATURAL, item, id)
                        && !matchesSingleCategory(CreativeCategory.REDSTONE, item, id)
                        && !matchesSingleCategory(CreativeCategory.FUNCTIONAL, item, id);
                case NATURAL -> containsAny(path, "stone", "dirt", "grass", "sand", "gravel", "log", "wood", "leaves",
                        "sapling", "flower", "mushroom", "coral", "kelp", "bamboo", "cactus", "vine", "ore", "deepslate");
                case FUNCTIONAL -> containsAny(path, "crafting_table", "furnace", "chest", "barrel", "bed", "anvil",
                        "enchanting", "brewing", "beacon", "banner", "sign", "door", "trapdoor", "ladder", "torch",
                        "lantern", "bell", "jukebox", "lectern", "cauldron", "composter", "shulker_box");
                case REDSTONE -> containsAny(path, "redstone", "piston", "observer", "repeater", "comparator", "hopper",
                        "dispenser", "dropper", "rail", "detector", "daylight", "target", "sculk_sensor", "lever", "button",
                        "pressure_plate", "tripwire");
                case TOOLS -> containsAny(path, "pickaxe", "shovel", "axe", "hoe", "bucket", "boat", "minecart", "compass",
                        "clock", "brush", "lead", "name_tag", "flint_and_steel", "fishing_rod", "spyglass", "shears");
                case COMBAT -> containsAny(path, "sword", "bow", "crossbow", "arrow", "trident", "shield", "helmet",
                        "chestplate", "leggings", "boots", "horse_armor", "mace");
                case FOOD -> containsAny(path, "apple", "bread", "beef", "pork", "chicken", "mutton", "rabbit", "cod",
                        "salmon", "cookie", "cake", "pie", "stew", "soup", "carrot", "potato", "beetroot", "melon",
                        "berries", "honey", "milk", "egg");
                case INGREDIENTS -> containsAny(path, "ingot", "nugget", "dust", "shard", "fragment", "stick", "string",
                        "leather", "feather", "paper", "book", "diamond", "emerald", "coal", "lapis", "quartz", "amethyst",
                        "raw_", "dye", "bone", "blaze", "pearl", "slime", "clay", "brick");
                case SPAWN_EGGS -> path.endsWith("_spawn_egg");
                default -> false;
            };
        }

        private boolean containsAny(String value, String... parts) {
            for (String part : parts) {
                if (value.contains(part)) {
                    return true;
                }
            }
            return false;
        }

        private CreativeCategory[] visibleTabs() {
            return visibleTabs(false).toArray(new CreativeCategory[0]);
        }

        private List<CreativeCategory> visibleTabs(boolean includeMore) {
            List<CreativeCategory> candidates = new ArrayList<>();
            for (CreativeCategory tab : CreativeCategory.values()) {
                if (tab == CreativeCategory.MORE) {
                    continue;
                }
                candidates.add(tab);
            }
            int capacity = tabCapacity(includeMore ? candidates.size() + 1 : candidates.size());
            int regularCapacity = includeMore && candidates.size() > capacity ? Math.max(0, capacity - 1) : capacity;
            List<CreativeCategory> tabs = new ArrayList<>();
            for (CreativeCategory tab : candidates) {
                if (tabs.size() >= regularCapacity) {
                    break;
                }
                tabs.add(tab);
            }
            if (includeMore && candidates.size() > regularCapacity) {
                tabs.add(CreativeCategory.MORE);
            }
            return tabs;
        }

        private List<CreativeCategory> overflowTabs() {
            List<CreativeCategory> shown = visibleTabs(true);
            Set<CreativeCategory> shownSet = new LinkedHashSet<>(shown);
            List<CreativeCategory> overflow = new ArrayList<>();
            for (CreativeCategory tab : CreativeCategory.values()) {
                if (tab != CreativeCategory.MORE && !shownSet.contains(tab)) {
                    overflow.add(tab);
                }
            }
            return overflow;
        }

        private int tabCapacity(int tabCount) {
            int tabWidth = tabWidth(tabCount);
            return Math.max(1, Math.min(tabCount, (PICKER_WIDTH - PAD * 2 + TAB_GAP) / Math.max(1, tabWidth + TAB_GAP) * TAB_ROWS));
        }

        private int tabWidth(int tabCount) {
            int columns = Math.max(1, (int) Math.ceil(tabCount / (float) TAB_ROWS));
            int available = PICKER_WIDTH - PAD * 2 - (columns - 1) * TAB_GAP;
            return Math.max(TAB_MIN_W, available / columns);
        }

        private boolean handleGenericMotion(View view, MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_SCROLL) {
                return false;
            }
            int localX = toLocalGuiCoord(event.getX());
            int localY = toLocalGuiCoord(event.getY());
            if (!containsLocalMouse(localX, localY)) {
                return false;
            }
            return scrollPicker(event);
        }

        private boolean scrollPicker(MotionEvent event) {
            float wheel = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (Math.abs(wheel) < 0.001F) {
                wheel = -event.getAxisValue(MotionEvent.AXIS_Y);
            }
            if (Math.abs(wheel) < 0.001F) {
                return false;
            }
            scrollRow += wheel < 0.0F ? 1 : -1;
            clampScroll();
            invalidate();
            return true;
        }

        private boolean handleTouch(View view, MotionEvent event) {
            int action = event.getActionMasked();
            int localX = toLocalGuiCoord(event.getX());
            int localY = toLocalGuiCoord(event.getY());
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS) {
                lastRight = event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)
                        || (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0;
                lastMiddle = event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)
                        || (event.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0;
                CreativeCategory tab = hitTab(localX, localY);
                if (tab != null) {
                    if (tab == CreativeCategory.MORE || tab == CreativeCategory.MODDED || tab == CreativeCategory.OTHER) {
                        showCreativeListPopup(this);
                    } else {
                        selectCategory(tab);
                    }
                    return true;
                }
                if (hitScrollbar(localX, localY)) {
                    draggingScrollbar = true;
                    requestOuterScrollLock(true);
                    updateScrollbarDrag(localY);
                    return true;
                }
                return true;
            }
            if (action == MotionEvent.ACTION_SCROLL) {
                return scrollPicker(event);
            }
            if (action == MotionEvent.ACTION_MOVE) {
                if (draggingScrollbar) {
                    requestOuterScrollLock(true);
                    updateScrollbarDrag(localY);
                    return true;
                }
                invalidate();
                return false;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                if (draggingScrollbar) {
                    draggingScrollbar = false;
                    lastRight = false;
                    lastMiddle = false;
                    requestOuterScrollLock(false);
                    return true;
                }
                ItemStack stack = hitStack(localX, localY);
                if (!stack.isEmpty()) {
                    boolean right = lastRight || event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)
                            || (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0;
                    boolean middle = lastMiddle || event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)
                            || (event.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0;
                    takeCreativeStack(stack, right, middle);
                }
                lastRight = false;
                lastMiddle = false;
                requestOuterScrollLock(false);
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                draggingScrollbar = false;
                lastRight = false;
                lastMiddle = false;
                requestOuterScrollLock(false);
            }
            return false;
        }

        private void requestOuterScrollLock(boolean locked) {
            icyllis.modernui.view.ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(locked);
            }
        }

        private CreativeCategory hitTab(int x, int y) {
            List<CreativeCategory> tabs = visibleTabs(true);
            int tabWidth = tabWidth(tabs.size());
            for (int i = 0; i < tabs.size(); i++) {
                int col = i / TAB_ROWS;
                int row = i % TAB_ROWS;
                int left = PAD + col * (tabWidth + TAB_GAP);
                int top = PAD + row * TAB_H;
                if (x >= left && x < left + tabWidth && y >= top && y < top + TAB_H - 2) {
                    return tabs.get(i);
                }
            }
            return null;
        }

        private ItemStack hitStack(int x, int y) {
            if (y < GRID_Y || x < GRID_X || x >= GRID_X + COLS * STEP) {
                return ItemStack.EMPTY;
            }
            int col = (x - GRID_X) / STEP;
            int row = (y - GRID_Y) / STEP;
            int slotX = GRID_X + col * STEP;
            int slotY = GRID_Y + row * STEP;
            if (x >= slotX + SLOT || y >= slotY + SLOT) {
                return ItemStack.EMPTY;
            }
            int index = (scrollRow + row) * COLS + col;
            return index >= 0 && index < items.size() ? items.get(index).copy() : ItemStack.EMPTY;
        }

        private boolean hitScrollbar(int x, int y) {
            return totalRows() > visibleRows() && x >= scrollbarX() && x < scrollbarX() + SCROLL_W
                    && y >= GRID_Y && y < PICKER_HEIGHT - PAD;
        }

        private void updateScrollbarDrag(int y) {
            int totalRows = totalRows();
            int visibleRows = visibleRows();
            int maxScroll = Math.max(0, totalRows - visibleRows);
            int trackTop = GRID_Y;
            int trackHeight = Math.max(1, PICKER_HEIGHT - PAD - GRID_Y);
            int thumbHeight = scrollbarThumbHeight(totalRows, visibleRows, trackHeight);
            int usable = Math.max(1, trackHeight - thumbHeight);
            int relative = Math.max(0, Math.min(usable, y - trackTop - thumbHeight / 2));
            scrollRow = Math.round(relative * (float) maxScroll / usable);
            clampScroll();
            invalidate();
        }

        private int scrollbarX() {
            return GRID_X + COLS * STEP + 4;
        }

        private int visibleRows() {
            return Math.max(1, (PICKER_HEIGHT - PAD - GRID_Y) / STEP);
        }

        private int totalRows() {
            return Math.max(1, (items.size() + COLS - 1) / COLS);
        }

        private int scrollbarThumbHeight(int totalRows, int visibleRows, int trackHeight) {
            return Math.max(18, Math.min(trackHeight, trackHeight * visibleRows / Math.max(visibleRows, totalRows)));
        }

        private void clampScroll() {
            int maxScroll = Math.max(0, totalRows() - visibleRows());
            scrollRow = Math.max(0, Math.min(maxScroll, scrollRow));
        }

        private double currentGuiScale() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return 1.0D;
            }
            return Math.max(1.0D, client.getWindow().getScaleFactor());
        }

        private void updateSurfaceBounds(double nextGuiScale) {
            guiScale = Math.max(1.0D, nextGuiScale);
            getLocationInWindow(locationInWindow);
            screenLeft = (int) Math.round(locationInWindow[0] / guiScale);
            screenTop = (int) Math.round(locationInWindow[1] / guiScale);
            guiWidth = Math.max(PICKER_WIDTH, (int) Math.round(getWidth() / guiScale));
            guiHeight = Math.max(PICKER_HEIGHT, (int) Math.round(getHeight() / guiScale));
        }

        private int toLocalGuiCoord(float value) {
            return (int) Math.floor(value / Math.max(1.0D, guiScale));
        }

        private int toLocalGuiMouseX(int screenMouseX) {
            return screenMouseX - screenLeft;
        }

        private int toLocalGuiMouseY(int screenMouseY) {
            return screenMouseY - screenTop;
        }

        private boolean containsLocalMouse(int localMouseX, int localMouseY) {
            return localMouseX >= 0 && localMouseY >= 0 && localMouseX < guiWidth && localMouseY < guiHeight;
        }

        private boolean enableSelfPanelScissor(DrawContext context) {
            if (selfScrollView == null) {
                context.enableScissor(0, 0, guiWidth, guiHeight);
                return true;
            }
            int[] clipLocation = new int[2];
            selfScrollView.getLocationInWindow(clipLocation);
            int clipLeft = (int) Math.round(clipLocation[0] / guiScale) - screenLeft;
            int clipTop = (int) Math.round(clipLocation[1] / guiScale) - screenTop;
            int clipRight = clipLeft + (int) Math.round(selfScrollView.getWidth() / guiScale);
            int clipBottom = clipTop + (int) Math.round(selfScrollView.getHeight() / guiScale);
            int left = Math.max(0, clipLeft);
            int top = Math.max(0, clipTop);
            int right = Math.min(guiWidth, clipRight);
            int bottom = Math.min(guiHeight, clipBottom);
            if (right <= left || bottom <= top) {
                return false;
            }
            context.enableScissor(left, top, right, bottom);
            return true;
        }

        private final class CreativePickerRenderer implements MinecraftSurfaceView.Renderer {
            @Override
            public void onSurfaceChanged(int w, int h) {
            }

            @Override
            public void onDraw(DrawContext context, int mouseX, int mouseY, float tick, double guiScale, float alpha) {
                MinecraftClient client = MinecraftClient.getInstance();
                updateSurfaceBounds(guiScale);
                if (!enableSelfPanelScissor(context)) {
                    return;
                }
                int localMouseX = toLocalGuiMouseX(mouseX);
                int localMouseY = toLocalGuiMouseY(mouseY);
                boolean mouseInside = containsLocalMouse(localMouseX, localMouseY);
                ItemStack hoveredStack = mouseInside ? hitStack(localMouseX, localMouseY) : ItemStack.EMPTY;

                context.fill(0, 0, PICKER_WIDTH, PICKER_HEIGHT, 0xCC12161D);
                context.drawBorder(0, 0, PICKER_WIDTH, PICKER_HEIGHT, 0xFF3A4350);
                drawTabs(context, client);
                drawItems(context, client, localMouseX, localMouseY);
                drawScrollbar(context);
                if (items.isEmpty()) {
                    context.drawText(client.textRenderer, "当前分类无物品", GRID_X, GRID_Y + 4, 0xFFB6C0CC, false);
                }
                if (mouseInside && !hoveredStack.isEmpty()) {
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(-screenLeft, -screenTop);
                    context.drawItemTooltip(client.textRenderer, hoveredStack, mouseX, mouseY);
                    context.getMatrices().popMatrix();
                }
                context.disableScissor();
            }

            private void drawTabs(DrawContext context, MinecraftClient client) {
                List<CreativeCategory> tabs = visibleTabs(true);
                int tabWidth = tabWidth(tabs.size());
                for (int i = 0; i < tabs.size(); i++) {
                    CreativeCategory tab = tabs.get(i);
                    int col = i / TAB_ROWS;
                    int row = i % TAB_ROWS;
                    int left = PAD + col * (tabWidth + TAB_GAP);
                    int top = PAD + row * TAB_H;
                    boolean selected = isTopTabSelected(tab);
                    context.fill(left, top, left + tabWidth, top + TAB_H - 2, selected ? 0xAA3A5268 : 0x55313A4A);
                    context.drawBorder(left, top, tabWidth, TAB_H - 2, selected ? 0xFFE6C56C : 0x887A8492);
                    context.drawText(client.textRenderer, tab.label, left + 4, top + 5, selected ? 0xFFFFD36A : 0xFFE8EDF4, false);
                }
                context.drawText(client.textRenderer, currentCategoryLabel() + "  " + items.size(), PAD, GRID_Y - 10, 0xFFB6C0CC, false);
            }

            private void drawItems(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
                int visibleRows = visibleRows();
                for (int row = 0; row < visibleRows; row++) {
                    for (int col = 0; col < COLS; col++) {
                        int index = (scrollRow + row) * COLS + col;
                        int x = GRID_X + col * STEP;
                        int y = GRID_Y + row * STEP;
                        boolean hovered = mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
                        context.fill(x, y, x + SLOT, y + SLOT, hovered ? 0xFF56606E : 0xFF222832);
                        context.drawBorder(x, y, SLOT, SLOT, hovered ? 0xFFE6C56C : 0xFF7A8492);
                        context.fill(x + 2, y + 2, x + SLOT - 2, y + SLOT - 2, 0xFF101419);
                        if (index >= 0 && index < items.size()) {
                            ItemStack stack = items.get(index);
                            context.drawItem(stack, x + 3, y + 3);
                            context.drawStackOverlay(client.textRenderer, stack, x + 3, y + 3);
                        }
                    }
                }
                if (!cursorStack.isEmpty() && mouseX >= 0 && mouseY >= 0 && mouseX < PICKER_WIDTH && mouseY < PICKER_HEIGHT) {
                    context.drawItem(cursorStack, mouseX - 8, mouseY - 8);
                    context.drawStackOverlay(client.textRenderer, cursorStack, mouseX - 8, mouseY - 8);
                }
            }

            private void drawScrollbar(DrawContext context) {
                int totalRows = totalRows();
                int visibleRows = visibleRows();
                if (totalRows <= visibleRows) {
                    return;
                }
                int trackTop = GRID_Y;
                int trackHeight = PICKER_HEIGHT - PAD - GRID_Y;
                int x = scrollbarX();
                int thumbHeight = scrollbarThumbHeight(totalRows, visibleRows, trackHeight);
                int maxScroll = Math.max(1, totalRows - visibleRows);
                int usable = Math.max(1, trackHeight - thumbHeight);
                int thumbTop = trackTop + Math.round(scrollRow * (float) usable / maxScroll);
                context.fill(x, trackTop, x + SCROLL_W, trackTop + trackHeight, 0x66242A33);
                context.fill(x, thumbTop, x + SCROLL_W, thumbTop + thumbHeight, draggingScrollbar ? 0xFFFFD36A : 0xFF8E99A8);
            }
        }
    }

    private final class InventorySlotsView extends MinecraftSurfaceView {
        private static final int STEP = SLOT_SIZE + SLOT_GAP;
        private static final int MAIN_X = SLOT_PADDING;
        private static final int MAIN_Y = SLOT_PADDING + 12;
        private static final int HOTBAR_Y = MAIN_Y + 3 * STEP + 8;
        private static final int SIDE_X = MAIN_X + 9 * STEP + 12;

        private final boolean archive;
        private final List<SlotBox> boxes = new ArrayList<>();
        private final int[] locationInWindow = new int[2];
        private double guiScale = 1.0D;
        private int screenLeft;
        private int screenTop;
        private int guiWidth;
        private int guiHeight;
        private boolean lastRight;

        private InventorySlotsView(Context ctx, boolean archive) {
            super(ctx);
            this.archive = archive;
            buildBoxes();
            setRenderer(new InventorySlotsRenderer());
            setFocusable(true);
            setFocusableInTouchMode(true);
            setOnTouchListener(this::handleTouch);
        }

        private int preferredHeight() {
            return (int) Math.ceil(contentHeight() * currentGuiScale());
        }

        private int contentWidth() {
            return SIDE_X + STEP + 4 + SLOT_SIZE + SLOT_PADDING;
        }

        private int contentHeight() {
            return HOTBAR_Y + SLOT_SIZE + SLOT_PADDING + 12;
        }

        private void buildBoxes() {
            boxes.clear();
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    boxes.add(new SlotBox(9 + row * 9 + col, MAIN_X + col * STEP, MAIN_Y + row * STEP));
                }
            }
            for (int col = 0; col < 9; col++) {
                boxes.add(new SlotBox(col, MAIN_X + col * STEP, HOTBAR_Y));
            }
            boxes.add(new SlotBox(39, SIDE_X, MAIN_Y));
            boxes.add(new SlotBox(38, SIDE_X, MAIN_Y + STEP));
            boxes.add(new SlotBox(37, SIDE_X, MAIN_Y + 2 * STEP));
            boxes.add(new SlotBox(36, SIDE_X, MAIN_Y + 3 * STEP));
            boxes.add(new SlotBox(40, SIDE_X + STEP + 4, MAIN_Y + 3 * STEP));
        }

        private boolean handleTouch(View view, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS) {
                lastRight = (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0;
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                int slot = hitSlot(toLocalGuiCoord(event.getX()), toLocalGuiCoord(event.getY()));
                if (slot >= 0) {
                    boolean right = lastRight || (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0;
                    handleSlotClick(archive, slot, right);
                }
                lastRight = false;
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                lastRight = false;
            }
            return false;
        }

        private int hitSlot(double x, double y) {
            for (SlotBox box : boxes) {
                if (x >= box.x && x < box.x + SLOT_SIZE && y >= box.y && y < box.y + SLOT_SIZE) {
                    return box.slot;
                }
            }
            return -1;
        }

        private double currentGuiScale() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return 1.0D;
            }
            return Math.max(1.0D, client.getWindow().getScaleFactor());
        }

        private void updateSurfaceBounds(double nextGuiScale) {
            guiScale = Math.max(1.0D, nextGuiScale);
            getLocationInWindow(locationInWindow);
            screenLeft = (int) Math.round(locationInWindow[0] / guiScale);
            screenTop = (int) Math.round(locationInWindow[1] / guiScale);
            guiWidth = Math.max(contentWidth(), (int) Math.round(getWidth() / guiScale));
            guiHeight = Math.max(contentHeight(), (int) Math.round(getHeight() / guiScale));
        }

        private int toLocalGuiCoord(float value) {
            return (int) Math.floor(value / Math.max(1.0D, guiScale));
        }

        private int toLocalGuiMouseX(int screenMouseX) {
            return screenMouseX - screenLeft;
        }

        private int toLocalGuiMouseY(int screenMouseY) {
            return screenMouseY - screenTop;
        }

        private boolean containsLocalMouse(int localMouseX, int localMouseY) {
            return localMouseX >= 0 && localMouseY >= 0 && localMouseX < guiWidth && localMouseY < guiHeight;
        }

        private boolean enablePanelScissor(DrawContext context) {
            View clipView = archive ? archiveScrollView : selfScrollView;
            if (clipView == null) {
                context.enableScissor(0, 0, guiWidth, guiHeight);
                return true;
            }
            int[] clipLocation = new int[2];
            clipView.getLocationInWindow(clipLocation);
            int clipLeft = (int) Math.round(clipLocation[0] / guiScale) - screenLeft;
            int clipTop = (int) Math.round(clipLocation[1] / guiScale) - screenTop;
            int clipRight = clipLeft + (int) Math.round(clipView.getWidth() / guiScale);
            int clipBottom = clipTop + (int) Math.round(clipView.getHeight() / guiScale);
            int left = Math.max(0, clipLeft);
            int top = Math.max(0, clipTop);
            int right = Math.min(guiWidth, clipRight);
            int bottom = Math.min(guiHeight, clipBottom);
            if (right <= left || bottom <= top) {
                return false;
            }
            context.enableScissor(left, top, right, bottom);
            return true;
        }

        private final class InventorySlotsRenderer implements MinecraftSurfaceView.Renderer {
            @Override
            public void onSurfaceChanged(int w, int h) {
            }

            @Override
            public void onDraw(DrawContext context, int mouseX, int mouseY, float tick, double guiScale, float alpha) {
                MinecraftClient client = MinecraftClient.getInstance();
                updateSurfaceBounds(guiScale);
                if (!enablePanelScissor(context)) {
                    return;
                }
                int localMouseX = toLocalGuiMouseX(mouseX);
                int localMouseY = toLocalGuiMouseY(mouseY);
                boolean mouseInside = containsLocalMouse(localMouseX, localMouseY);
                int hoveredSlot = mouseInside ? hitSlot(localMouseX, localMouseY) : -1;
                ItemStack hoveredStack = ItemStack.EMPTY;

                context.drawText(client.textRenderer, "背包", MAIN_X, SLOT_PADDING, 0xFFB6C0CC, false);
                context.drawText(client.textRenderer, "快捷栏", MAIN_X, HOTBAR_Y - 10, 0xFFB6C0CC, false);
                context.drawText(client.textRenderer, "盔甲", SIDE_X, SLOT_PADDING, 0xFFB6C0CC, false);
                context.drawText(client.textRenderer, "副手", SIDE_X + STEP + 4, MAIN_Y + 3 * STEP + SLOT_SIZE + 2, 0xFFB6C0CC, false);

                for (SlotBox box : boxes) {
                    ItemStack stack = archive ? archiveSlotStack(box.slot) : selfSlotStack(box.slot);
                    boolean hovered = box.slot == hoveredSlot;
                    drawSlot(context, client, box, stack, hovered);
                    if (hovered && !stack.isEmpty()) {
                        hoveredStack = stack;
                    }
                }
                if (mouseInside && !cursorStack.isEmpty()) {
                    context.drawItem(cursorStack, localMouseX - 8, localMouseY - 8);
                    context.drawStackOverlay(client.textRenderer, cursorStack, localMouseX - 8, localMouseY - 8);
                }
                if (mouseInside && !hoveredStack.isEmpty()) {
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(-screenLeft, -screenTop);
                    context.drawItemTooltip(client.textRenderer, hoveredStack, mouseX, mouseY);
                    context.getMatrices().popMatrix();
                }
                context.disableScissor();
            }

            private void drawSlot(DrawContext context, MinecraftClient client, SlotBox box, ItemStack stack, boolean hovered) {
                int fill = hovered ? 0xFF56606E : 0xFF222832;
                int border = hovered ? 0xFFE6C56C : 0xFF7A8492;
                context.fill(box.x, box.y, box.x + SLOT_SIZE, box.y + SLOT_SIZE, fill);
                context.drawBorder(box.x, box.y, SLOT_SIZE, SLOT_SIZE, border);
                context.fill(box.x + 2, box.y + 2, box.x + SLOT_SIZE - 2, box.y + SLOT_SIZE - 2, 0xFF101419);
                if (!stack.isEmpty()) {
                    context.drawItem(stack, box.x + 4, box.y + 4);
                    context.drawStackOverlay(client.textRenderer, stack, box.x + 4, box.y + 4);
                }
            }
        }

        private final class SlotBox {
            private final int slot;
            private final int x;
            private final int y;

            private SlotBox(int slot, int x, int y) {
                this.slot = slot;
                this.x = x;
                this.y = y;
            }
        }
    }
}
