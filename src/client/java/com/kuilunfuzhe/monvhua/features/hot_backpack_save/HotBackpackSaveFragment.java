package com.kuilunfuzhe.monvhua.features.hot_backpack_save;

import com.google.gson.JsonParser;
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
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HotBackpackSaveFragment extends Fragment {
    private static final int LEFT_WIDTH = 210;
    private static final int RIGHT_WIDTH = 270;
    private static final int SELF_PANEL_HEIGHT = 510;
    private static final int SLOT_SIZE = 24;
    private static final int SLOT_GAP = 2;
    private static final int SLOT_PADDING = 4;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withLocale(Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static HotBackpackSaveFragment active;

    private final Screen parent;
    private final List<InventorySlotsView> slotGrids = new ArrayList<>();
    private HotBackpackState state = HotBackpackSaveClient.state();
    private String selectedUuid = "";
    private long selectedTimestamp;
    private String applyTargetUuid = "";
    private boolean targetListOpen;
    private ItemStack cursorStack = ItemStack.EMPTY;

    private FrameLayout root;
    private LinearLayout playerList;
    private LinearLayout archiveSlots;
    private LinearLayout selfSlots;
    private LinearLayout rightPanel;
    private LinearLayout historyList;
    private TextView statusLabel;
    private TextView selectedLabel;

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

        main.addView(createLeftPanel(ctx), new LinearLayout.LayoutParams(LEFT_WIDTH, -1));
        main.addView(createCenterPanel(ctx), new LinearLayout.LayoutParams(0, -1, 1.0F));
        main.addView(createRightPanel(ctx), new LinearLayout.LayoutParams(RIGHT_WIDTH, -1));

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
        archiveSlots = vertical(ctx);
        archiveSlots.setPadding(8, 8, 8, 8);
        archiveScroll.addView(archiveSlots, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(archiveScroll, new LinearLayout.LayoutParams(-1, 0, 1.0F));

        panel.addView(title(ctx, "我的背包"), blockParams());
        ScrollView selfScroll = new ScrollView(ctx);
        selfScroll.setBackground(panelShape(0x441A2028, 0x553A4350));
        selfSlots = vertical(ctx);
        selfSlots.setPadding(8, 8, 8, 8);
        selfScroll.addView(selfSlots, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(selfScroll, new LinearLayout.LayoutParams(-1, SELF_PANEL_HEIGHT));
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
            row.setTextColor(selected ? 0xFFFFD36A : (record.online ? 0xFFE8EDF4 : 0xFF97A1AE));
            row.setBackground(panelShape(selected ? 0xAA3A5268 : 0x332A3038, 0x553A4350));
            row.setOnClickListener(v -> {
                selectedUuid = record.uuid;
                selectedTimestamp = 0L;
                ensureTimestamp();
                targetListOpen = false;
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
        InventorySlotsView grid = new InventorySlotsView(getContext(), false);
        slotGrids.add(grid);
        selfSlots.addView(grid, new LinearLayout.LayoutParams(-1, grid.preferredHeight()));
    }

    private void rebuildRight() {
        Context ctx = getContext();
        rightPanel.removeAllViews();
        rightPanel.addView(title(ctx, "功能区"), blockParams());

        Button refresh = button(ctx, "刷新");
        refresh.setOnClickListener(v -> HotBackpackSaveClient.requestState());
        rightPanel.addView(refresh, blockParams());

        Button save = button(ctx, "保存特殊玩家");
        save.setOnClickListener(v -> ClientPlayNetworking.send(new HotBackpackPackets.SaveSpecialPlayersC2S()));
        rightPanel.addView(save, blockParams());

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

        Button applyTo = button(ctx, targetListOpen ? "应用当前存档到: 收起" : "应用当前存档到");
        applyTo.setOnClickListener(v -> {
            targetListOpen = !targetListOpen;
            scheduleRebuildRight();
        });
        rightPanel.addView(applyTo, blockParams());
        if (targetListOpen) {
            addTargetList(ctx);
        }

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

    private void addTargetList(Context ctx) {
        for (HotBackpackState.PlayerRecord record : sortedRecords()) {
            Button row = button(ctx, (record.uuid.equals(applyTargetUuid) ? "✓ " : "") + safeName(record));
            row.setOnClickListener(v -> {
                applyTargetUuid = record.uuid;
                HotBackpackState.Snapshot snapshot = selectedSnapshot();
                if (snapshot != null) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("再次点击确认应用到 " + safeName(record)), true);
                    }
                    UUID sourceId = parseUuid(snapshot.uuid);
                    UUID targetId = parseUuid(record.uuid);
                    if (sourceId != null && targetId != null) {
                        ClientPlayNetworking.send(new HotBackpackPackets.ApplySnapshotC2S(sourceId, snapshot.timestamp, targetId));
                    }
                }
                targetListOpen = false;
                scheduleRebuildRight();
            });
            rightPanel.addView(row, blockParams());
        }
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
        return record.roleTag == null || record.roleTag.isBlank() ? "" : " [" + record.roleTag + "]";
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

        private final class InventorySlotsRenderer implements MinecraftSurfaceView.Renderer {
            @Override
            public void onSurfaceChanged(int w, int h) {
            }

            @Override
            public void onDraw(DrawContext context, int mouseX, int mouseY, float tick, double guiScale, float alpha) {
                MinecraftClient client = MinecraftClient.getInstance();
                updateSurfaceBounds(guiScale);
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
