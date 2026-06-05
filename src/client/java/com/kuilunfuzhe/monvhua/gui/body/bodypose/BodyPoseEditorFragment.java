package com.kuilunfuzhe.monvhua.gui.body.bodypose;

import com.kuilunfuzhe.monvhua.features.block.body.BodyModelSelectionCatalog;
import com.kuilunfuzhe.monvhua.model.CombinedBodyModelData;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.network.bodypose.ApplySkeletalPoseC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePoseEditorItemsC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePosedBodyC2SPacket;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Modern UI 身体姿势编辑器 Fragment。
 * 三栏布局：左栏皮肤/玩家选择/操作 | 中栏3D预览 | 右栏部位/姿势控制。
 */
public class BodyPoseEditorFragment extends Fragment {

    // ═══════════════════════════════════════════════════════
    //  常量
    // ═══════════════════════════════════════════════════════

    private static final float ROTATION_STEP_DEGREES = 5.0F;
    private static final float PREVIEW_CHEST_PIVOT_Y = 8.0F;
    private static final float PREVIEW_Y_PIVOT = 1.601F;
    private static final float MODEL_PART_UNITS_PER_GRID = 16.0F;
    private static final float MODEL_OFFSET_MIN = -10.0F;
    private static final float MODEL_OFFSET_MAX = 10.0F;
    private static final float TRANSFORM_OFFSET_STEP = 0.25F;
    private static final float MODEL_SCALE_MIN = 0.02F;
    private static final float MODEL_SCALE_MAX = 12.0F;
    private static final float MODEL_SCALE_STEP = 0.05F;
    private static final float DEFAULT_PREVIEW_PAN_X = 0.0F;
    private static final float DEFAULT_PREVIEW_ZOOM = 0.65F;
    private static final float PREVIEW_SCALE_FACTOR = 0.36F;
    private static final float PREVIEW_ZOOM_MIN = 0.08F;
    private static final float PREVIEW_ZOOM_MAX = 10.0F;
    private static final float PREVIEW_ZOOM_STEP = 0.16F;
    private static final float ITEM_PREVIEW_BOUNDS_SCALE = 1.65F;
    private static final int ITEM_PREVIEW_ICON_PADDING = 8;
    private static final long TRANSFORM_REPEAT_DELAY_MS = 320L;
    private static final long TRANSFORM_REPEAT_INTERVAL_MS = 65L;
    private static final long WORLD_PREVIEW_KEY_DEBOUNCE_MS = 200L;
    private static final float MOVE_AXIS_LENGTH = 4.0F / 3.0F;
    private static final float MOVE_AXIS_HIT_RADIUS = 3.4F;
    private static final float ROTATION_RING_RADIUS = 2.45F / 3.0F;
    private static final float ROTATION_RING_HIT_RADIUS = 3.0F;
    private static final int ROTATION_RING_SEGMENTS = 48;
    private static final int GROUND_GRID_SIZE = 21;
    private static final float GROUND_GRID_HALF_SIZE = GROUND_GRID_SIZE * 0.5F;
    private static final float GROUND_GRID_CELL = 1.0F;
    private static final float GROUND_GRID_Y = 1.05F;
    private static final int RIGHT_PANEL_WIDTH = 500;
    private static final int PLAYER_LIST_VISIBLE_ROWS = 6;
    private static final int ITEM_LIST_VISIBLE_ROWS = 8;
    private static final int PREVIEW_ITEM_SELECTOR_WIDTH = 180;
    private static final int PREVIEW_ITEM_SELECTOR_HEIGHT = 18;
    private static final int PREVIEW_ITEM_ROW_HEIGHT = 18;

    // ═══════════════════════════════════════════════════════
    //  静态状态 — 跨会话保持
    // ═══════════════════════════════════════════════════════

    private static String selectedSkin = BodyModelSelectionCatalog.LOCAL_SKINS[0];
    private static String selectedPlayerName = "";
    private static SkinSource selectedSkinSource = SkinSource.LOCAL;
    private static String selectedPart = getDefaultSelectedPart();
    private static boolean slimModel = true;
    private static float modelOffsetX;
    private static float modelOffsetY;
    private static float modelOffsetZ;
    private static float modelPitch;
    private static float modelYaw;
    private static float modelRoll;
    private static float wholeBodyScale = 1.0F;
    private static PoseEditMode poseEditMode = PoseEditMode.STATIC_PART;
    private static EditorItemDisplayMode defaultItemDisplayMode = EditorItemDisplayMode.BLOCK;
    private static final List<EditorItemModel> EDITOR_ITEMS = new ArrayList<>();
    private static final Map<String, PartPose> PART_POSES = createPartPoses();
    private static final Map<String, PartPose> SKELETAL_POSES = createPartPoses();

    private static boolean worldPreviewEnabled = true;
    private static PreviewMode worldPreviewMode = PreviewMode.FOLLOW_PLAYER;
    private static double fixedWorldX;
    private static double fixedWorldY;
    private static double fixedWorldZ;
    private static long lastWorldPreviewModeToggleTimeMs;

    /** 当前活跃实例，供外部类（mixin、world renderer）访问 */
    public static BodyPoseEditorFragment activeInstance;

    // ═══════════════════════════════════════════════════════
    //  实例字段 — 编辑器状态
    // ═══════════════════════════════════════════════════════

    private View rootView;
    private MinecraftSurfaceView surfaceView;

    // 预览状态
    private float previewPitch = 0.0F;
    private float previewYaw;
    private float previewRoll;
    private float previewZoom = DEFAULT_PREVIEW_ZOOM;
    private static boolean showWholePreview = true;
    private float previewPanX = DEFAULT_PREVIEW_PAN_X;
    private float previewPanY;
    private static boolean showCoordinateAxes = true;
    private static boolean coordinateAxesMovable = true;

    // 鼠标交互
    private MoveAxis hoveredMoveAxis = MoveAxis.NONE;
    private MoveAxis draggingMoveAxis = MoveAxis.NONE;
    private RotationAxis hoveredRotationAxis = RotationAxis.NONE;
    private RotationAxis draggingRotationAxis = RotationAxis.NONE;
    private boolean draggingPreview;
    private boolean draggingRightPreview;
    private int activePreviewButton;
    private View repeatingTransformView;
    private Runnable repeatingTransformAction;
    private final Runnable repeatingTransformTick = new Runnable() {
        @Override
        public void run() {
            if (repeatingTransformView == null || repeatingTransformAction == null) return;
            repeatingTransformAction.run();
            if (surfaceView != null) surfaceView.invalidate();
            repeatingTransformView.postDelayed(this, TRANSFORM_REPEAT_INTERVAL_MS);
        }
    };

    // 列表状态
    private boolean playerListOpen;
    private boolean itemListOpen;
    private int itemListScroll;
    private int selectedEditorItemIndex = -1;

    // 模型实例（延迟创建）
    private PlayerEntityModel defaultPreviewModel;
    private PlayerEntityModel slimPreviewModel;
    private static PlayerEntityModel worldPreviewModelDefault;
    private static PlayerEntityModel worldPreviewModelSlim;

    // 渲染尺寸缓存（由 onDraw 更新）
    private float previewScale;
    private float previewCenterX;
    private float previewCenterY;
    private int previewSurfaceWidth;
    private int previewSurfaceHeight;
    private int previewAreaLeft;
    private int previewAreaRight;
    private int previewAreaTop;
    private int previewAreaBottom;
    private double previewGuiScale = 1.0D;
    private int previewScreenLeft;
    private int previewScreenTop;
    private final int[] previewSurfaceLocation = new int[2];

    // UI 引用
    private LinearLayout skinButtonsContainer;
    private LinearLayout partButtonsContainer;
    private LinearLayout poseControlsContainer;
    private LinearLayout playerListContainer;
    private LinearLayout itemListContainer;
    private Button playerButton;
    private Button modelTypeButton;
    private Button poseModeButton;
    private Button itemButton;
    private Button itemDisplayModeButton;
    private Button placeItemsButton;
    private Button clearSelectedItemButton;
    private Button clearAllItemsButton;
    private Button resetPoseButton;
    private Button resetAllPoseButton;
    private Button resetTransformButton;
    private Button showWholeButton;
    private Button coordToggleButton;
    private Button coordMovableButton;
    private Button worldPreviewToggleButton;
    private Button runCommandButton;
    private Button placeButton;
    private Button applySkeletalButton;
    private List<Button> partButtons = new ArrayList<>();
    private List<Button> poseButtons = new ArrayList<>();
    private List<Button> skinButtons = new ArrayList<>();
    private final List<NumericValueBinding> transformValueBindings = new ArrayList<>();
    private final List<NumericValueBinding> poseValueBindings = new ArrayList<>();

    // ═══════════════════════════════════════════════════════
    //  静态方法 — 打开编辑器
    // ═══════════════════════════════════════════════════════

    public static void open() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        selectedPart = getDefaultSelectedPart();
        showWholePreview = true;
        worldPreviewEnabled = true;
        EDITOR_ITEMS.clear();
        Screen screen = MuiModApi.get().createScreen(new BodyPoseEditorFragment());
        client.setScreen(screen);
    }

    // ═══════════════════════════════════════════════════════
    //  Fragment 生命周期
    // ═══════════════════════════════════════════════════════

    @Override
    public View onCreateView(icyllis.modernui.view.LayoutInflater inflater, ViewGroup container,
                             icyllis.modernui.util.DataSet savedInstanceState) {
        Context ctx = getContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackground(new ColorDrawable(0xCC000000));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEY_P
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) {
                toggleWorldPreviewMode();
                return true;
            }
            return false;
        });

        // 左栏：皮肤/玩家选择
        View left = createLeftPanel(ctx);
        root.addView(left, new LinearLayout.LayoutParams(255, -1));

        // 中栏：3D 预览 + 控制
        View center = createCenterPanel(ctx);
        root.addView(center, new LinearLayout.LayoutParams(0, -1, 1f));

        // 右栏：部位/姿势控制
        View right = createRightPanel(ctx);
        root.addView(right, new LinearLayout.LayoutParams(RIGHT_PANEL_WIDTH, -1));

        rootView = root;
        return root;
    }

    @Override
    public void onViewCreated(View view, icyllis.modernui.util.DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.requestFocus();
        view.post(() -> {
            refreshButtonLabels();
            refreshNumericValueBindings();
            view.requestLayout();
            view.invalidate();
            invalidatePreview();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        activeInstance = this;
    }

    @Override
    public void onPause() {
        super.onPause();
        activeInstance = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRepeatingTransform();
        rootView = null;
        surfaceView = null;
        previewSurfaceWidth = 0;
        previewSurfaceHeight = 0;
        activeInstance = null;
    }

    // ═══════════════════════════════════════════════════════
    //  左栏 — 皮肤 / 玩家选择
    // ═══════════════════════════════════════════════════════

    private View createLeftPanel(Context ctx) {
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setBackground(new ColorDrawable(0xDD1A1A2E));

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(8, 8, 8, 8);

        // 标题
        TextView title = new TextView(ctx);
        title.setText("模型编辑器");
        title.setTextColor(0xFFE8E8E8);
        title.setTextSize(18);
        title.setPadding(0, 0, 0, 8);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        // ── 玩家选择 ──
        TextView playerLabel = new TextView(ctx);
        playerLabel.setText("玩家");
        playerLabel.setTextSize(11);
        playerLabel.setTextColor(0xFF888888);
        panel.addView(playerLabel, new LinearLayout.LayoutParams(-1, -2));

        playerButton = new Button(ctx);
        playerButton.setText("玩家: 选择");
        playerButton.setOnClickListener(v -> togglePlayerList());
        panel.addView(playerButton, new LinearLayout.LayoutParams(-1, -2));

        // 玩家列表容器
        playerListContainer = new LinearLayout(ctx);
        playerListContainer.setOrientation(LinearLayout.VERTICAL);
        playerListContainer.setVisibility(View.GONE);
        panel.addView(playerListContainer, new LinearLayout.LayoutParams(-1, -2));

        // ── 皮肤网格 ──
        TextView skinLabel = new TextView(ctx);
        skinLabel.setText("皮肤");
        skinLabel.setTextSize(11);
        skinLabel.setTextColor(0xFF888888);
        skinLabel.setPadding(0, 8, 0, 2);
        panel.addView(skinLabel, new LinearLayout.LayoutParams(-1, -2));

        skinButtonsContainer = new LinearLayout(ctx);
        skinButtonsContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(skinButtonsContainer, new LinearLayout.LayoutParams(-1, -2));

        rebuildSkinButtons();
        addLeftActionControls(panel, ctx);

        scrollView.addView(panel, new FrameLayout.LayoutParams(-1, -2));
        refreshButtonLabels();
        refreshNumericValueBindings();
        return scrollView;
    }

    private void togglePlayerList() {
        playerListOpen = !playerListOpen;
        if (playerListOpen) {
            rebuildPlayerList();
        }
        playerListContainer.setVisibility(playerListOpen ? View.VISIBLE : View.GONE);
    }

    private void rebuildPlayerList() {
        playerListContainer.removeAllViews();
        Context ctx = getContext();
        if (ctx == null) return;

        List<PlayerListEntry> entries = getPlayerEntries();
        if (entries.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("当前没有玩家");
            empty.setTextColor(0xFF888888);
            empty.setPadding(8, 4, 8, 4);
            playerListContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        int visibleRows = Math.min(PLAYER_LIST_VISIBLE_ROWS, Math.max(1, entries.size()));
        for (int row = 0; row < visibleRows; row++) {
            int index = row; // 简化：不实现滚动以保持简单
            if (index >= entries.size()) break;
            PlayerListEntry entry = entries.get(index);
            String name = getPlayerName(entry);
            boolean selected = selectedSkinSource == SkinSource.PLAYER && name.equals(selectedPlayerName);

            Button b = new Button(ctx);
            b.setText((selected ? "> " : "  ") + name);
            b.setTextColor(selected ? 0xFFFFDD66 : 0xFFE8E8E8);
            int bgColor = selected ? 0x334466AA : 0x22000000;
            b.setBackground(new ColorDrawable(bgColor));
            b.setOnClickListener(v -> {
                selectedPlayerName = name;
                selectedSkinSource = SkinSource.PLAYER;
                slimModel = entry.getSkinTextures().model() == SkinTextures.Model.SLIM;
                playerListOpen = false;
                playerListContainer.setVisibility(View.GONE);
                refreshButtonLabels();
            });
            playerListContainer.addView(b, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void rebuildSkinButtons() {
        if (skinButtonsContainer == null || getContext() == null) return;
        Context ctx = getContext();
        skinButtonsContainer.removeAllViews();
        skinButtons.clear();

        List<String> localSkins = getLocalSkins();
        if (!localSkins.contains(selectedSkin) && !localSkins.isEmpty()) {
            selectedSkin = localSkins.get(0);
        }

        // 每行2个皮肤按钮
        int cols = 2;
        LinearLayout currentRow = null;
        for (int i = 0; i < localSkins.size(); i++) {
            if (i % cols == 0) {
                currentRow = new LinearLayout(ctx);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                skinButtonsContainer.addView(currentRow, new LinearLayout.LayoutParams(-1, -2));
            }

            String skin = localSkins.get(i);
            Button button = new Button(ctx);
            boolean selected = selectedSkinSource == SkinSource.LOCAL && skin.equals(selectedSkin);
            button.setText(truncate(skin, 10));
            button.setTextColor(selected ? 0xFFFFDD66 : 0xFFE8E8E8);
            button.setOnClickListener(v -> {
                selectedSkin = skin;
                selectedSkinSource = SkinSource.LOCAL;
                playerListOpen = false;
                if (playerListContainer != null) playerListContainer.setVisibility(View.GONE);
                refreshButtonLabels();
            });
            currentRow.addView(button, new LinearLayout.LayoutParams(0, -2, 1f));
            skinButtons.add(button);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  中栏 — 3D 预览 + 控制
    // ═══════════════════════════════════════════════════════

    private void addLeftActionControls(LinearLayout panel, Context ctx) {
        addSectionLabel(panel, ctx, "操作");

        resetPoseButton = new Button(ctx);
        panel.addView(resetPoseButton, new LinearLayout.LayoutParams(-1, -2));

        resetAllPoseButton = new Button(ctx);
        panel.addView(resetAllPoseButton, new LinearLayout.LayoutParams(-1, -2));

        resetTransformButton = new Button(ctx);
        panel.addView(resetTransformButton, new LinearLayout.LayoutParams(-1, -2));

        placeButton = new Button(ctx);
        panel.addView(placeButton, new LinearLayout.LayoutParams(-1, -2));

        runCommandButton = new Button(ctx);
        panel.addView(runCommandButton, new LinearLayout.LayoutParams(-1, -2));

        applySkeletalButton = new Button(ctx);
        panel.addView(applySkeletalButton, new LinearLayout.LayoutParams(-1, -2));
    }

    private View createCenterPanel(Context ctx) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(4, 0, 4, 0);
        panel.setBackground(new ColorDrawable(0xDD0D0D1A));

        // 信息栏
        TextView infoBar = new TextView(ctx);
        infoBar.setId(View.generateViewId());
        infoBar.setPadding(0, 4, 0, 2);
        panel.addView(infoBar, new LinearLayout.LayoutParams(-1, -2));

        // 3D 预览 (MinecraftSurfaceView)
        surfaceView = new MinecraftSurfaceView(ctx);
        surfaceView.setRenderer(new PreviewRenderer());
        surfaceView.setOnTouchListener(new PreviewTouchListener());
        surfaceView.setOnGenericMotionListener(this::handlePreviewGenericMotion);
        panel.addView(surfaceView, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 预览控制按钮栏
        LinearLayout ctrlBar = new LinearLayout(ctx);
        ctrlBar.setOrientation(LinearLayout.HORIZONTAL);
        ctrlBar.setPadding(0, 2, 0, 2);

        showWholeButton = new Button(ctx);
        ctrlBar.addView(showWholeButton, new LinearLayout.LayoutParams(0, -2, 1f));

        coordToggleButton = new Button(ctx);
        ctrlBar.addView(coordToggleButton, new LinearLayout.LayoutParams(0, -2, 1f));

        coordMovableButton = new Button(ctx);
        ctrlBar.addView(coordMovableButton, new LinearLayout.LayoutParams(0, -2, 1f));

        worldPreviewToggleButton = new Button(ctx);
        ctrlBar.addView(worldPreviewToggleButton, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(ctrlBar, new LinearLayout.LayoutParams(-1, -2));

        // 关闭按钮
        Button doneBtn = new Button(ctx);
        doneBtn.setText("关闭-一般直接按esc");
        doneBtn.setOnClickListener(v -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) client.execute(() -> client.setScreen(null));
        });
        panel.addView(doneBtn, new LinearLayout.LayoutParams(-1, -2));

        refreshButtonLabels();
        return panel;
    }

    // ═══════════════════════════════════════════════════════
    //  右栏 — 部位 / 姿势控制
    // ═══════════════════════════════════════════════════════

    private View createRightPanel(Context ctx) {
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setBackground(new ColorDrawable(0xDD1A1A2E));

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(8, 8, 8, 8);

        // ── 模型类型 ──
        addSectionLabel(panel, ctx, "模型");
        modelTypeButton = new Button(ctx);
        panel.addView(modelTypeButton, new LinearLayout.LayoutParams(-1, -2));

        poseModeButton = new Button(ctx);
        panel.addView(poseModeButton, new LinearLayout.LayoutParams(-1, -2));

        // ── 部位选择 ──
        addSectionLabel(panel, ctx, "部位");
        partButtonsContainer = new LinearLayout(ctx);
        partButtonsContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(partButtonsContainer, new LinearLayout.LayoutParams(-1, -2));
        rebuildPartButtons();

        // ── 姿势控制 ──
        addSectionLabel(panel, ctx, "姿态");
        poseControlsContainer = new LinearLayout(ctx);
        poseControlsContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(poseControlsContainer, new LinearLayout.LayoutParams(-1, -2));
        rebuildPoseControls();

        addSectionLabel(panel, ctx, "变换");
        addTransformControls(panel, ctx);

        // 模式提示
        if (panel.getChildCount() > 0) {
            TextView modeHint = new TextView(ctx);
            panel.addView(modeHint, new LinearLayout.LayoutParams(-1, -2));
        }

        scrollView.addView(panel, new FrameLayout.LayoutParams(-1, -2));
        refreshButtonLabels();
        refreshNumericValueBindings();
        return scrollView;
    }

    private void addSectionLabel(LinearLayout parent, Context ctx, String text) {
        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(13);
        label.setTextColor(0xFFCCCCCC);
        label.setPadding(0, 10, 0, 2);
        parent.addView(label, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addTransformControls(LinearLayout parent, Context ctx) {
        transformValueBindings.clear();
        parent.addView(createMoveRow(ctx, "X", MoveAxis.X));
        parent.addView(createMoveRow(ctx, "Y", MoveAxis.Y));
        parent.addView(createMoveRow(ctx, "Z", MoveAxis.Z));
        parent.addView(createRotationRow(ctx, "Pitch", Axis.PITCH));
        parent.addView(createRotationRow(ctx, "Yaw", Axis.YAW));
        parent.addView(createRotationRow(ctx, "Roll", Axis.ROLL));
    }

    private LinearLayout createMoveRow(Context ctx, String label, MoveAxis axis) {
        return createNumericRow(ctx, label,
                () -> getActiveOffset(axis),
                value -> setActiveOffset(axis, value),
                TRANSFORM_OFFSET_STEP, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX, false,
                transformValueBindings);
    }

    private LinearLayout createRotationRow(Context ctx, String label, Axis axis) {
        return createNumericRow(ctx, label,
                () -> getActiveRotation(axis),
                value -> setActiveRotation(axis, value),
                ROTATION_STEP_DEGREES, -180.0F, 180.0F, axis != Axis.PITCH,
                transformValueBindings);
    }

    private LinearLayout createNumericRow(Context ctx, String label,
                                          Supplier<Float> getter,
                                          Consumer<Float> setter,
                                          float step,
                                          float min,
                                          float max,
                                          boolean wrap,
                                          List<NumericValueBinding> bindings) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        labelView.setTextColor(0xFFAAAAAA);
        labelView.setWidth(58);
        row.addView(labelView, new LinearLayout.LayoutParams(-2, -2));

        NumericValueBinding binding = new NumericValueBinding(getter, setter, min, max, wrap);
        installNumericFieldScroll(row, binding, step);

        Button minusBtn = new Button(ctx);
        minusBtn.setText("-");
        installRepeatingTransformButton(minusBtn, () -> binding.add(-step));
        row.addView(minusBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        EditText valueField = new EditText(ctx);
        valueField.setTextColor(0xFFE8E8E8);
        valueField.setTextSize(12);
        binding.attach(valueField);
        installNumericFieldScroll(valueField, binding, step);
        row.addView(valueField, new LinearLayout.LayoutParams(0, -2, 1.25f));
        bindings.add(binding);

        Button plusBtn = new Button(ctx);
        plusBtn.setText("+");
        installRepeatingTransformButton(plusBtn, () -> binding.add(step));
        row.addView(plusBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private void installNumericFieldScroll(View control, NumericValueBinding binding, float step) {
        control.setOnGenericMotionListener((view, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_SCROLL) return false;
            float scroll = getScrollAmount(event);
            if (Math.abs(scroll) >= 0.001F) {
                binding.add(scroll > 0.0F ? step : -step);
            }
            return true;
        });
    }

    private void installRepeatingTransformButton(Button button, Runnable action) {
        button.setOnClickListener(v -> {
            action.run();
            invalidatePreview();
        });
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS -> {
                    startRepeatingTransform(view, action);
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_BUTTON_RELEASE -> {
                    stopRepeatingTransform();
                    return true;
                }
                default -> {
                    return false;
                }
            }
        });
    }

    private void startRepeatingTransform(View view, Runnable action) {
        stopRepeatingTransform();
        repeatingTransformView = view;
        repeatingTransformAction = action;
        action.run();
        invalidatePreview();
        view.postDelayed(repeatingTransformTick, TRANSFORM_REPEAT_DELAY_MS);
    }

    private void stopRepeatingTransform() {
        if (repeatingTransformView != null) {
            repeatingTransformView.removeCallbacks(repeatingTransformTick);
        }
        repeatingTransformView = null;
        repeatingTransformAction = null;
    }

    private void invalidatePreview() {
        if (surfaceView != null) surfaceView.invalidate();
    }

    private void adjustActiveOffset(MoveAxis axis, float amount) {
        setActiveOffset(axis, getActiveOffset(axis) + amount);
        invalidatePreview();
        refreshNumericValueBindings();
    }

    private void adjustActiveRotation(Axis axis, float amount) {
        setActiveRotation(axis, getActiveRotation(axis) + amount);
        invalidatePreview();
        refreshNumericValueBindings();
    }

    private float getActiveOffset(MoveAxis axis) {
        switch (axis) {
            case X -> { return getActiveOffsetX(); }
            case Y -> { return getActiveOffsetY(); }
            case Z -> { return getActiveOffsetZ(); }
            default -> { return 0.0F; }
        }
    }

    private void setActiveOffset(MoveAxis axis, float value) {
        switch (axis) {
            case X -> setActiveOffsetX(clampPreview(value, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX));
            case Y -> setActiveOffsetY(clampPreview(value, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX));
            case Z -> setActiveOffsetZ(clampPreview(value, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX));
            default -> {
            }
        }
    }

    private float getActiveRotation(Axis axis) {
        return switch (axis) {
            case PITCH -> getActivePitch();
            case YAW -> getActiveYaw();
            case ROLL -> getActiveRoll();
        };
    }

    private void setActiveRotation(Axis axis, float value) {
        switch (axis) {
            case PITCH -> setActivePitch(clampPreview(value, -180.0F, 180.0F));
            case YAW -> setActiveYaw(normalizeDegrees(value));
            case ROLL -> setActiveRoll(normalizeDegrees(value));
        }
    }

    private void refreshNumericValueBindings() {
        for (NumericValueBinding binding : transformValueBindings) {
            binding.sync();
        }
        for (NumericValueBinding binding : poseValueBindings) {
            binding.sync();
        }
    }

    private void rebuildPartButtons() {
        if (partButtonsContainer == null || getContext() == null) return;
        Context ctx = getContext();
        partButtonsContainer.removeAllViews();
        partButtons.clear();

        for (String part : BodyModelSelectionCatalog.PARTS) {
            Button button = new Button(ctx);
            boolean selected = part.equals(selectedPart);
            String prefix = selected ? "> " : "  ";
            button.setText(prefix + part);
            button.setTextColor(selected ? 0xFFFFDD66 : 0xFFE8E8E8);
            button.setOnClickListener(v -> {
                selectedPart = part;
                rebuildPoseControls();
                refreshButtonLabels();
                refreshNumericValueBindings();
                invalidatePreview();
            });
            partButtonsContainer.addView(button, new LinearLayout.LayoutParams(-1, -2));
            partButtons.add(button);
        }
    }

    private void rebuildPoseControls() {
        if (poseControlsContainer == null || getContext() == null) return;
        Context ctx = getContext();
        poseControlsContainer.removeAllViews();
        poseButtons.clear();
        poseValueBindings.clear();

        boolean editingAll = selectedPart.equals("all");

        poseControlsContainer.addView(createNumericRow(ctx, "整体大小",
                () -> wholeBodyScale,
                BodyPoseEditorFragment::setWholeBodyScale,
                MODEL_SCALE_STEP, MODEL_SCALE_MIN, MODEL_SCALE_MAX, false,
                poseValueBindings));

        if (editingAll) {
            addAllBendControls(ctx);
            return;
        }

        poseControlsContainer.addView(createNumericRow(ctx, "部位大小",
                BodyPoseEditorFragment::getSelectedPartScale,
                BodyPoseEditorFragment::setSelectedPartScale,
                MODEL_SCALE_STEP, MODEL_SCALE_MIN, MODEL_SCALE_MAX, false,
                poseValueBindings));

        // 俯仰 (Pitch)
        poseControlsContainer.addView(createPoseRow(ctx, "俯仰", Axis.PITCH));

        // 偏转 (Yaw)
        poseControlsContainer.addView(createPoseRow(ctx, "偏转", Axis.YAW));

        // 翻滚 (Roll)
        poseControlsContainer.addView(createPoseRow(ctx, "翻滚", Axis.ROLL));

        if (hasSelectedBendControls()) {
            String bendLabel = getBendLabelPrefix();
            poseControlsContainer.addView(createBendRow(ctx, bendLabel + " Pitch", Axis.PITCH));
            poseControlsContainer.addView(createBendRow(ctx, bendLabel + " Yaw", Axis.YAW));
            poseControlsContainer.addView(createBendRow(ctx, bendLabel + " Roll", Axis.ROLL));
        }
    }

    private LinearLayout createPoseRow(Context ctx, String label, Axis axis) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        labelView.setTextColor(0xFFAAAAAA);
        labelView.setWidth(50);
        row.addView(labelView, new LinearLayout.LayoutParams(-2, -2));

        NumericValueBinding binding = new NumericValueBinding(
                () -> getSelectedPoseValue(axis),
                value -> setSelectedPoseValue(axis, value),
                -180.0F, 180.0F, axis != Axis.PITCH);
        installNumericFieldScroll(row, binding, ROTATION_STEP_DEGREES);

        Button minusBtn = new Button(ctx);
        minusBtn.setText("-");
        installRepeatingTransformButton(minusBtn, () -> binding.add(-ROTATION_STEP_DEGREES));
        row.addView(minusBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        poseButtons.add(minusBtn);

        EditText valueField = new EditText(ctx);
        valueField.setTextColor(0xFFE8E8E8);
        valueField.setTextSize(12);
        binding.attach(valueField);
        installNumericFieldScroll(valueField, binding, ROTATION_STEP_DEGREES);
        row.addView(valueField, new LinearLayout.LayoutParams(0, -2, 1.25f));
        poseValueBindings.add(binding);

        Button plusBtn = new Button(ctx);
        plusBtn.setText("+");
        installRepeatingTransformButton(plusBtn, () -> binding.add(ROTATION_STEP_DEGREES));
        row.addView(plusBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        poseButtons.add(plusBtn);

        return row;
    }

    private LinearLayout createBendRow(Context ctx, String label, Axis axis) {
        return createPartBendRow(ctx, label, selectedPart, axis, true);
    }

    private void addAllBendControls(Context ctx) {
        if (poseEditMode != PoseEditMode.SKELETAL) return;
        addSectionLabel(poseControlsContainer, ctx, "子肢体旋转");
        addBendGroup(ctx, "腰", "torso");
        addBendGroup(ctx, "左肘", "left_arm");
        addBendGroup(ctx, "右肘", "right_arm");
        addBendGroup(ctx, "左膝", "left_leg");
        addBendGroup(ctx, "右膝", "right_leg");
    }

    private void addBendGroup(Context ctx, String label, String part) {
        TextView groupLabel = new TextView(ctx);
        groupLabel.setText(label);
        groupLabel.setTextColor(0xFFAAE0FF);
        groupLabel.setTextSize(12);
        groupLabel.setPadding(0, 6, 0, 0);
        poseControlsContainer.addView(groupLabel, new LinearLayout.LayoutParams(-1, -2));
        poseControlsContainer.addView(createPartBendRow(ctx, "Pitch", part, Axis.PITCH, false));
        poseControlsContainer.addView(createPartBendRow(ctx, "Yaw", part, Axis.YAW, false));
        poseControlsContainer.addView(createPartBendRow(ctx, "Roll", part, Axis.ROLL, false));
    }

    private LinearLayout createPartBendRow(Context ctx, String label, String part, Axis axis, boolean trackPoseButtons) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        labelView.setTextColor(0xFFAAE0FF);
        labelView.setWidth(88);
        row.addView(labelView, new LinearLayout.LayoutParams(-2, -2));

        NumericValueBinding binding = new NumericValueBinding(
                () -> getPartBendValue(part, axis),
                value -> setPartBendValue(part, axis, value),
                -180.0F, 180.0F, axis != Axis.PITCH);
        installNumericFieldScroll(row, binding, ROTATION_STEP_DEGREES);

        Button minusBtn = new Button(ctx);
        minusBtn.setText("-");
        installRepeatingTransformButton(minusBtn, () -> binding.add(-ROTATION_STEP_DEGREES));
        row.addView(minusBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        if (trackPoseButtons) poseButtons.add(minusBtn);

        EditText valueField = new EditText(ctx);
        valueField.setTextColor(0xFFE8E8E8);
        valueField.setTextSize(12);
        binding.attach(valueField);
        installNumericFieldScroll(valueField, binding, ROTATION_STEP_DEGREES);
        row.addView(valueField, new LinearLayout.LayoutParams(0, -2, 1.25f));
        poseValueBindings.add(binding);

        Button plusBtn = new Button(ctx);
        plusBtn.setText("+");
        installRepeatingTransformButton(plusBtn, () -> binding.add(ROTATION_STEP_DEGREES));
        row.addView(plusBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        if (trackPoseButtons) poseButtons.add(plusBtn);

        return row;
    }

    private void toggleItemList() {
        itemListOpen = !itemListOpen;
        if (itemListOpen) {
            rebuildItemList();
        }
        clampItemListScroll(getAvailableItemStacks().size());
        invalidatePreview();
    }

    private void rebuildItemList() {
        if (itemListContainer == null) {
            clampItemListScroll(getAvailableItemStacks().size());
            return;
        }
        itemListContainer.removeAllViews();
        Context ctx = getContext();
        if (ctx == null) return;

        List<ItemStack> stacks = getAvailableItemStacks();
        if (stacks.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("无可选物品");
            empty.setTextColor(0xFF888888);
            empty.setPadding(8, 4, 8, 4);
            itemListContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, stacks.size()));
        for (int row = 0; row < visibleRows; row++) {
            int index = row;
            if (index >= stacks.size()) break;
            ItemStack stack = stacks.get(index);

            Button b = new Button(ctx);
            b.setText(stack.getName().getString());
            b.setOnClickListener(v -> {
                EditorItemModel model = new EditorItemModel(stack.copyWithCount(1));
                EDITOR_ITEMS.add(model);
                selectedEditorItemIndex = EDITOR_ITEMS.size() - 1;
                itemListOpen = false;
                itemListContainer.setVisibility(View.GONE);
                refreshButtonLabels();
                refreshNumericValueBindings();
            });
            itemListContainer.addView(b, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    // ═══════════════════════════════════════════════════════
    //  预览渲染器 (MinecraftSurfaceView)
    // ═══════════════════════════════════════════════════════

    private class PreviewRenderer implements MinecraftSurfaceView.Renderer {
        @Override
        public void onSurfaceChanged(int w, int h) {
            previewSurfaceWidth = w;
            previewSurfaceHeight = h;
        }

        @Override
        public void onDraw(DrawContext dCtx, int mouseX, int mouseY, float tick, double guiScale, float alpha) {
            previewGuiScale = Math.max(1.0D, guiScale);
            int w = Math.max(1, (int) Math.round(previewSurfaceWidth / previewGuiScale));
            int h = Math.max(1, (int) Math.round(previewSurfaceHeight / previewGuiScale));
            if (w <= 0 || h <= 0) return;
            updatePreviewScreenOrigin();
            // 计算预览区域（填充整个视图带边距）
            int pad = 4;
            previewAreaLeft = pad;
            previewAreaRight = w - pad;
            previewAreaTop = pad;
            previewAreaBottom = h - pad;
            int pWidth = previewAreaRight - previewAreaLeft;
            int pHeight = previewAreaBottom - previewAreaTop;

            // 背景
            dCtx.fill(previewAreaLeft, previewAreaTop, previewAreaRight, previewAreaBottom, 0x66000000);
            dCtx.drawBorder(previewAreaLeft, previewAreaTop, pWidth, pHeight, 0x88FFFFFF);

            // 标题
            String titleText = getSelectedSkinLabel() + " / " + (slimModel ? "slim" : "default");
            dCtx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    titleText, (previewAreaLeft + previewAreaRight) / 2, previewAreaTop + 6, 0xFFFFFF);
            dCtx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    selectedPart, (previewAreaLeft + previewAreaRight) / 2, previewAreaTop + 18, 0xB8B8B8);

            // 缩放与中心
            previewScale = getPreviewBaseScale(pWidth, pHeight) * previewZoom;
            previewCenterX = (previewAreaLeft + previewAreaRight) * 0.5F + previewPanX;
            previewCenterY = (previewAreaTop + previewAreaBottom) * 0.5F + previewPanY;

            // 更新 hover 状态
            float localMouseX = mouseX - previewScreenLeft;
            float localMouseY = mouseY - previewScreenTop;
            hoveredMoveAxis = draggingMoveAxis == MoveAxis.NONE ? findMoveAxis(localMouseX, localMouseY) : draggingMoveAxis;
            hoveredRotationAxis = draggingRotationAxis == RotationAxis.NONE ? findRotationRing(localMouseX, localMouseY) : draggingRotationAxis;

            // 地面网格
            if (showCoordinateAxes) {
                renderPreviewGroundGrid(dCtx);
            }

            // 偏移读数
            renderModelOffsetReadout(dCtx);

            // 玩家模型
            renderPlayerPreview(dCtx);

            if (showCoordinateAxes) {
                renderModelTransformGizmos(dCtx);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  预览触摸事件处理
    // ═══════════════════════════════════════════════════════

    private boolean handlePreviewGenericMotion(View view, MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_SCROLL) return false;
        return handlePreviewScroll(event);
    }

    private boolean handlePreviewScroll(MotionEvent event) {
        float scroll = getScrollAmount(event);
        if (Math.abs(scroll) < 0.001F) return false;
        float x = toPreviewGuiCoord(event.getX());
        float y = toPreviewGuiCoord(event.getY());
        if (itemListOpen && isInsidePreviewItemList(x, y)) {
            itemListScroll += scroll > 0.0F ? -1 : 1;
            clampItemListScroll(getAvailableItemStacks().size());
            invalidatePreview();
            return true;
        }
        previewZoom = clampPreview(previewZoom + scroll * PREVIEW_ZOOM_STEP, PREVIEW_ZOOM_MIN, PREVIEW_ZOOM_MAX);
        invalidatePreview();
        return true;
    }

    private static float getScrollAmount(MotionEvent event) {
        float scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (Math.abs(scroll) < 0.001F) {
            scroll = -event.getAxisValue(MotionEvent.AXIS_Y);
        }
        return scroll;
    }

    // ═══════════════════════════════════════════════════════
    //  预览渲染方法
    // ═══════════════════════════════════════════════════════

    private class PreviewTouchListener implements View.OnTouchListener {
        private float lastX;
        private float lastY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            float x = toPreviewGuiCoord(event.getX());
            float y = toPreviewGuiCoord(event.getY());

            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_SCROLL -> {
                    return handlePreviewScroll(event);
                }
                case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS -> {
                    lastX = x;
                    lastY = y;
                    activePreviewButton = getPreviewButton(event, action);
                    draggingPreview = activePreviewButton == MotionEvent.BUTTON_PRIMARY;
                    draggingRightPreview = activePreviewButton == MotionEvent.BUTTON_SECONDARY;
                    draggingMoveAxis = MoveAxis.NONE;
                    draggingRotationAxis = RotationAxis.NONE;
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    float dX = x - lastX;
                    float dY = y - lastY;
                    boolean primaryDown = activePreviewButton == MotionEvent.BUTTON_PRIMARY
                            || event.isButtonPressed(MotionEvent.BUTTON_PRIMARY);
                    boolean secondaryDown = activePreviewButton == MotionEvent.BUTTON_SECONDARY
                            || event.isButtonPressed(MotionEvent.BUTTON_SECONDARY);

                    if (primaryDown && draggingPreview) {
                        previewYaw -= dX * 0.65F;
                        previewPitch = clampPreview(previewPitch - dY * 0.65F, -60.0F, 60.0F);
                    } else if (secondaryDown && draggingRightPreview) {
                        previewPanX += dX;
                        previewPanY += dY;
                    }

                    hoveredRotationAxis = findRotationRing(x, y);
                    hoveredMoveAxis = findMoveAxis(x, y);
                    lastX = x;
                    lastY = y;
                    invalidatePreview();
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_BUTTON_RELEASE -> {
                    draggingPreview = false;
                    draggingRightPreview = false;
                    draggingMoveAxis = MoveAxis.NONE;
                    draggingRotationAxis = RotationAxis.NONE;
                    activePreviewButton = 0;
                    hoveredRotationAxis = findRotationRing(x, y);
                    hoveredMoveAxis = findMoveAxis(x, y);
                    invalidatePreview();
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private int getPreviewButton(MotionEvent event, int action) {
            int actionButton = event.getActionButton();
            if (actionButton == MotionEvent.BUTTON_PRIMARY || actionButton == MotionEvent.BUTTON_SECONDARY) {
                return actionButton;
            }
            if (event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) {
                return MotionEvent.BUTTON_PRIMARY;
            }
            if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                return MotionEvent.BUTTON_SECONDARY;
            }
            return action == MotionEvent.ACTION_DOWN ? MotionEvent.BUTTON_PRIMARY : 0;
        }
    }

    private void renderPlayerPreview(DrawContext context) {
        PlayerEntityModel model = getPreviewModel();
        if (model == null) return;

        preparePreviewModel(model);
        float scale = previewScale > 0.0F
                ? previewScale
                : getPreviewBaseScale(previewAreaRight - previewAreaLeft, previewAreaBottom - previewAreaTop) * previewZoom;
        ScreenPoint modelCenter = projectPreviewPoint(modelOffsetX, modelOffsetY, modelOffsetZ);
        int modelWidth = Math.max(previewAreaRight - previewAreaLeft, Math.round(scale * 5.2F));
        int modelHeight = Math.max(previewAreaBottom - previewAreaTop, Math.round(scale * 5.8F));
        float pitchCos = (float) Math.cos(Math.toRadians(previewPitch));
        int x1 = Math.round(modelCenter.x - modelWidth * 0.5F);
        int x2 = x1 + modelWidth;
        int y1 = Math.round(modelCenter.y + scale * PREVIEW_Y_PIVOT - modelHeight * pitchCos);
        int y2 = y1 + modelHeight;
        Identifier texture = getPreviewTexture();

        context.enableScissor(previewAreaLeft, previewAreaTop, previewAreaRight, previewAreaBottom);
        try {
            context.addPlayerSkin(model, texture, scale, previewPitch, -previewYaw, PREVIEW_Y_PIVOT,
                    previewScreenLeft + x1, previewScreenTop + y1,
                    previewScreenLeft + x2, previewScreenTop + y2);
        } finally {
            context.disableScissor();
        }
    }

    private void renderPreviewGroundGrid(DrawContext context) {
        if (!showCoordinateAxes) return;
        for (int i = 0; i <= GROUND_GRID_SIZE; i++) {
            float coord = -GROUND_GRID_HALF_SIZE + i * GROUND_GRID_CELL;
            boolean major = i == 0 || i == GROUND_GRID_SIZE || Math.abs(coord) < 0.001F || i % 5 == 0;
            int color = major ? argb(155, 150, 235, 150) : argb(95, 105, 185, 110);
            drawProjectedLine(context,
                    projectPreviewPoint(-GROUND_GRID_HALF_SIZE, GROUND_GRID_Y, coord),
                    projectPreviewPoint(GROUND_GRID_HALF_SIZE, GROUND_GRID_Y, coord),
                    color);
            drawProjectedLine(context,
                    projectPreviewPoint(coord, GROUND_GRID_Y, -GROUND_GRID_HALF_SIZE),
                    projectPreviewPoint(coord, GROUND_GRID_Y, GROUND_GRID_HALF_SIZE),
                    color);
        }
    }

    private void renderPreviewItemSelector(DrawContext context, float mouseX, float mouseY) {
        int x = getPreviewItemSelectorX();
        int y = getPreviewItemSelectorY();
        int width = getPreviewItemSelectorWidth();
        boolean hovered = isInsidePreviewItemSelector(mouseX, mouseY);
        int bg = hovered ? 0xEE6F7A8A : 0xDD56616F;
        context.fill(x, y, x + width, y + PREVIEW_ITEM_SELECTOR_HEIGHT, bg);
        context.drawBorder(x, y, width, PREVIEW_ITEM_SELECTOR_HEIGHT, 0xCCFFFFFF);
        String label = "Item: " + truncate(getSelectedItemPreviewLabel(), 22);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                label, x + 5, y + 5, 0xFFE2E8F0);

        if (!itemListOpen) return;

        List<ItemStack> stacks = getAvailableItemStacks();
        clampItemListScroll(stacks.size());
        int listY = getPreviewItemListTop();
        int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, stacks.size()));
        int listHeight = visibleRows * PREVIEW_ITEM_ROW_HEIGHT + 2;
        context.fill(x, listY, x + width, listY + listHeight, 0xF05E6876);
        context.drawBorder(x, listY, width, listHeight, 0xFFFFFFFF);
        if (stacks.isEmpty()) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    "No items", x + 6, listY + 6, 0xFFE2E8F0);
            return;
        }

        context.enableScissor(x + 1, listY + 1, x + width - 1, listY + listHeight - 1);
        try {
            for (int row = 0; row < visibleRows; row++) {
                int index = itemListScroll + row;
                if (index >= stacks.size()) break;
                ItemStack stack = stacks.get(index);
                int rowTop = listY + 1 + row * PREVIEW_ITEM_ROW_HEIGHT;
                boolean rowHovered = mouseX >= x + 1 && mouseX <= x + width - 1
                        && mouseY >= rowTop && mouseY < rowTop + PREVIEW_ITEM_ROW_HEIGHT;
                context.fill(x + 1, rowTop, x + width - 1, rowTop + PREVIEW_ITEM_ROW_HEIGHT,
                        rowHovered ? 0xE08796AA : (row % 2 == 0 ? 0xE0727D8C : 0xE0677180));
                context.drawItem(stack, x + 3, rowTop + 1);
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        truncate(getItemPreviewLabel(stack), 22), x + 23, rowTop + 5, 0xFFE2E8F0);
            }
        } finally {
            context.disableScissor();
        }
    }

    private boolean handlePreviewItemSelectorClick(float mouseX, float mouseY) {
        if (isInsidePreviewItemSelector(mouseX, mouseY)) {
            toggleItemList();
            return true;
        }
        if (!itemListOpen) {
            return false;
        }
        if (!isInsidePreviewItemList(mouseX, mouseY)) {
            itemListOpen = false;
            invalidatePreview();
            return true;
        }

        List<ItemStack> stacks = getAvailableItemStacks();
        if (stacks.isEmpty()) {
            return true;
        }
        int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, stacks.size()));
        int row = (int) ((mouseY - getPreviewItemListTop() - 1) / PREVIEW_ITEM_ROW_HEIGHT);
        if (row < 0 || row >= visibleRows) {
            return true;
        }
        int index = itemListScroll + row;
        if (index >= stacks.size()) {
            return true;
        }
        EditorItemModel model = new EditorItemModel(stacks.get(index).copyWithCount(1));
        EDITOR_ITEMS.add(model);
        selectedEditorItemIndex = EDITOR_ITEMS.size() - 1;
        itemListOpen = false;
        refreshButtonLabels();
        refreshNumericValueBindings();
        invalidatePreview();
        return true;
    }

    private int getPreviewItemSelectorX() {
        return previewAreaLeft + 4;
    }

    private int getPreviewItemSelectorY() {
        return previewAreaTop + 32;
    }

    private int getPreviewItemSelectorWidth() {
        return Math.min(PREVIEW_ITEM_SELECTOR_WIDTH,
                Math.max(96, previewAreaRight - previewAreaLeft - 8));
    }

    private int getPreviewItemListTop() {
        return getPreviewItemSelectorY() + PREVIEW_ITEM_SELECTOR_HEIGHT + 2;
    }

    private boolean isInsidePreviewItemSelector(float mouseX, float mouseY) {
        int x = getPreviewItemSelectorX();
        int y = getPreviewItemSelectorY();
        int width = getPreviewItemSelectorWidth();
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + PREVIEW_ITEM_SELECTOR_HEIGHT;
    }

    private boolean isInsidePreviewItemList(float mouseX, float mouseY) {
        List<ItemStack> stacks = getAvailableItemStacks();
        int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, stacks.size()));
        int x = getPreviewItemSelectorX();
        int y = getPreviewItemListTop();
        int width = getPreviewItemSelectorWidth();
        int height = visibleRows * PREVIEW_ITEM_ROW_HEIGHT + 2;
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    private void renderModelOffsetReadout(DrawContext context) {
        int x = previewAreaLeft + 4;
        int y = previewAreaTop + 56;
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                getActiveModelLabel(), x, y, 0xFFE2E8F0);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                "X " + formatOffset(getActiveOffsetX()), x, y + 12, 0xFFFF7777);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                "Y " + formatOffset(getActiveOffsetY()), x, y + 24, 0xFF77FF77);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                "Z " + formatOffset(getActiveOffsetZ()), x, y + 36, 0xFF8CA0FF);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                "Rot P " + formatDegrees(getActivePitch()), x, y + 54, 0xFFFF7777);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                "Rot Y " + formatDegrees(getActiveYaw()), x, y + 66, 0xFF77FF77);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                "Rot R " + formatDegrees(getActiveRoll()), x, y + 78, 0xFF8CA0FF);
    }

    private void renderEditorItemPreviews(DrawContext context) {
        context.enableScissor(previewAreaLeft, previewAreaTop, previewAreaRight, previewAreaBottom);
        try {
            for (int i = 0; i < EDITOR_ITEMS.size(); i++) {
                EditorItemModel item = EDITOR_ITEMS.get(i);
                ScreenPoint point = projectPreviewPoint(item.offsetX, item.offsetY, item.offsetZ);
                renderEditorItemPreview(context, item, point);
                int size = getEditorItemPreviewBoundsSize();
                int ix = Math.round(point.x) - size / 2;
                int iy = Math.round(point.y) - size / 2;
                context.drawBorder(ix, iy, size, size,
                        i == selectedEditorItemIndex ? 0xFFFFFF55 : 0x99FFFFFF);
            }
        } finally {
            context.disableScissor();
        }
    }

    private void renderEditorItemPreview(DrawContext context, EditorItemModel item, ScreenPoint point) {
        int boundsSize = getEditorItemPreviewBoundsSize();
        renderEditorItemIconPreview(context, item, point, boundsSize);
        renderEditorItemDisplayPreview(context, item, point, boundsSize);
    }

    private void renderEditorItemIconPreview(DrawContext context, EditorItemModel item, ScreenPoint point, int boundsSize) {
        int iconSize = Math.max(16, boundsSize - ITEM_PREVIEW_ICON_PADDING * 2);
        float iconScale = iconSize / 16.0F;
        int x = Math.round(point.x - iconSize * 0.5F);
        int y = Math.round(point.y - iconSize * 0.5F);

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(x, y);
            matrices.scale(iconScale, iconScale);
            context.drawItem(item.stack, 0, 0);
        } finally {
            matrices.popMatrix();
        }
    }

    private void renderEditorItemDisplayPreview(DrawContext context, EditorItemModel item, ScreenPoint point, int boundsSize) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || item.stack.isEmpty()) return;

        ItemDisplayEntityRenderState renderState = new ItemDisplayEntityRenderState();
        renderState.entityType = EntityType.ITEM_DISPLAY;
        renderState.width = 1.0F;
        renderState.height = 1.0F;
        renderState.standingEyeHeight = 0.5F;
        renderState.displayRenderState = new DisplayEntity.RenderState(
                DisplayEntity.AbstractInterpolator.constant(AffineTransformation.identity()),
                DisplayEntity.BillboardMode.FIXED,
                -1,
                DisplayEntity.FloatLerper.constant(0.0F),
                DisplayEntity.FloatLerper.constant(0.0F),
                -1);
        renderState.lerpProgress = 1.0F;

        try {
            client.getItemModelManager().clearAndUpdate(renderState.itemRenderState,
                    item.stack, item.displayMode.context, client.world, null, 0);
        } catch (RuntimeException ignored) {
            return;
        }
        if (renderState.itemRenderState.isEmpty()) return;

        int x1 = Math.round(point.x - boundsSize * 0.5F);
        int y1 = Math.round(point.y - boundsSize * 0.5F);
        int x2 = x1 + boundsSize;
        int y2 = y1 + boundsSize;
        context.addEntity(renderState, getEditorItemDisplayPreviewScale(boundsSize),
                new Vector3f(0.0F, 0.0F, 0.0F),
                createEditorItemPreviewRotation(item), null,
                previewScreenLeft + x1, previewScreenTop + y1,
                previewScreenLeft + x2, previewScreenTop + y2);
    }

    private float getEditorItemDisplayPreviewScale(int boundsSize) {
        return Math.max(18.0F, boundsSize * 0.44F);
    }

    private Quaternionf createEditorItemPreviewRotation(EditorItemModel item) {
        float r = (float) (Math.PI / 180.0D);
        return new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateX(previewPitch * r)
                .rotateY(-previewYaw * r)
                .rotateZ(previewRoll * r)
                .rotateX(item.pitch * r)
                .rotateY(-item.yaw * r)
                .rotateZ(item.roll * r);
    }

    private void renderModelTransformGizmos(DrawContext context) {
        renderRotationRing(context, RotationAxis.PITCH, 0xD0FF4646);
        renderRotationRing(context, RotationAxis.YAW, 0xD046E646);
        renderRotationRing(context, RotationAxis.ROLL, 0xD05A6EFF);

        renderMoveAxis(context, MoveAxis.X, 0xF5FF3232);
        renderMoveAxis(context, MoveAxis.Y, 0xF532DC32);
        renderMoveAxis(context, MoveAxis.Z, 0xF53C50FF);

        ScreenPoint center = projectModelPoint(getActiveOffsetX(), getActiveOffsetY(), getActiveOffsetZ());
        drawHandle(context, center, 3, 0xFFFFFFFF);
    }

    private void renderMoveAxis(DrawContext context, MoveAxis axis, int color) {
        float ox = getActiveOffsetX();
        float oy = getActiveOffsetY();
        float oz = getActiveOffsetZ();
        ScreenPoint center = projectModelPoint(ox, oy, oz);
        ScreenPoint end = switch (axis) {
            case X -> projectModelPoint(ox + MOVE_AXIS_LENGTH, oy, oz);
            case Y -> projectModelPoint(ox, oy + MOVE_AXIS_LENGTH, oz);
            case Z -> projectModelPoint(ox, oy, oz + MOVE_AXIS_LENGTH);
            default -> center;
        };

        boolean highlighted = axis == draggingMoveAxis || (draggingMoveAxis == MoveAxis.NONE && axis == hoveredMoveAxis);
        int drawColor = highlighted ? 0xFFFFFF5A : color;
        drawProjectedLine(context, center, end, drawColor);
        if (highlighted) {
            drawProjectedLineOffset(context, center, end, drawColor, 1.0F);
            drawProjectedLineOffset(context, center, end, drawColor, -1.0F);
        }
        drawHandle(context, end, highlighted ? 4 : 3, drawColor);
    }

    private void renderRotationRing(DrawContext context, RotationAxis axis, int color) {
        boolean highlighted = axis == draggingRotationAxis
                || (draggingRotationAxis == RotationAxis.NONE && axis == hoveredRotationAxis);
        int drawColor = highlighted ? 0xFFFFFF5A : color;
        ScreenPoint previous = projectRotationRingPoint(axis, 0);
        for (int i = 1; i <= ROTATION_RING_SEGMENTS; i++) {
            ScreenPoint current = projectRotationRingPoint(axis, i);
            drawProjectedLine(context, previous, current, drawColor);
            if (highlighted) {
                drawProjectedLineOffset(context, previous, current, drawColor, 1.0F);
            }
            previous = current;
        }
    }

    private static void drawProjectedLineOffset(DrawContext context, ScreenPoint start, ScreenPoint end, int color, float offset) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5D) return;
        float nx = (float) (-dy / length * offset);
        float ny = (float) (dx / length * offset);
        drawProjectedLine(context,
                new ScreenPoint(start.x + nx, start.y + ny),
                new ScreenPoint(end.x + nx, end.y + ny),
                color);
    }

    private static void drawHandle(DrawContext context, ScreenPoint point, int radius, int color) {
        int x = Math.round(point.x);
        int y = Math.round(point.y);
        context.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
        context.drawBorder(x - radius, y - radius, radius * 2 + 1, radius * 2 + 1, 0xCC000000);
    }

    private static void drawProjectedLine(DrawContext context, ScreenPoint start, ScreenPoint end, int color) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5D) return;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(start.x, start.y);
            matrices.rotate((float) Math.atan2(dy, dx));
            context.fill(0, 0, Math.max(1, (int) Math.ceil(length)), 1, color);
        } finally {
            matrices.popMatrix();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  3D 投影数学
    // ═══════════════════════════════════════════════════════

    private ScreenPoint projectModelPoint(float x, float y, float z) {
        float offsetX = getActiveOffsetX();
        float offsetY = getActiveOffsetY();
        float offsetZ = getActiveOffsetZ();
        float localX = x - offsetX;
        float localY = y - offsetY;
        float localZ = z - offsetZ;

        float yawRad = (float) Math.toRadians(-getActiveYaw());
        float yawCos = (float) Math.cos(yawRad);
        float yawSin = (float) Math.sin(yawRad);
        float yawX = localX * yawCos + localZ * yawSin;
        float yawZ = localZ * yawCos - localX * yawSin;

        float pitchRad = (float) Math.toRadians(getActivePitch());
        float pitchCos = (float) Math.cos(pitchRad);
        float pitchSin = (float) Math.sin(pitchRad);
        float pitchY = localY * pitchCos - yawZ * pitchSin;
        float pitchZ = yawZ * pitchCos + localY * pitchSin;
        float pitchX = yawX;

        float rollRad = (float) Math.toRadians(getActiveRoll());
        float rollCos = (float) Math.cos(rollRad);
        float rollSin = (float) Math.sin(rollRad);
        float rollX = pitchX * rollCos - pitchY * rollSin;
        float rollY = pitchX * rollSin + pitchY * rollCos;
        float rollZ = pitchZ;

        return projectPreviewPoint(offsetX + rollX, offsetY + rollY, offsetZ + rollZ);
    }

    private ScreenPoint projectPreviewPoint(float x, float y, float z) {
        float yawRad = (float) Math.toRadians(-previewYaw);
        float yawCos = (float) Math.cos(yawRad);
        float yawSin = (float) Math.sin(yawRad);
        float yawX = x * yawCos + z * yawSin;
        float yawZ = z * yawCos - x * yawSin;

        float pitchRad = (float) Math.toRadians(previewPitch);
        float pitchCos = (float) Math.cos(pitchRad);
        float pitchSin = (float) Math.sin(pitchRad);
        float pitchY = y * pitchCos - yawZ * pitchSin;
        float pitchX = yawX;

        float rollRad = (float) Math.toRadians(previewRoll);
        float rollCos = (float) Math.cos(rollRad);
        float rollSin = (float) Math.sin(rollRad);
        float rollX = pitchX * rollCos - pitchY * rollSin;
        float rollY = pitchX * rollSin + pitchY * rollCos;

        return new ScreenPoint(
                previewCenterX + rollX * previewScale,
                previewCenterY + rollY * previewScale);
    }

    // ═══════════════════════════════════════════════════════
    //  Hit 检测
    // ═══════════════════════════════════════════════════════

    private boolean isInsidePreviewArea(float px, float py) {
        return px >= previewAreaLeft + 4 && px <= previewAreaRight - 4
                && py >= previewAreaTop + 28 && py <= previewAreaBottom - 4;
    }

    private MoveAxis findMoveAxis(double px, double py) {
        if (!showCoordinateAxes) return MoveAxis.NONE;
        if (!isInsidePreviewArea((float) px, (float) py)) return MoveAxis.NONE;
        float ox = getActiveOffsetX();
        float oy = getActiveOffsetY();
        float oz = getActiveOffsetZ();
        ScreenPoint center = projectModelPoint(ox, oy, oz);
        double bestDist = MOVE_AXIS_HIT_RADIUS;
        MoveAxis best = MoveAxis.NONE;

        double d = distanceToSegment(px, py, center, projectModelPoint(ox + MOVE_AXIS_LENGTH, oy, oz));
        if (d <= bestDist) { bestDist = d; best = MoveAxis.X; }
        d = distanceToSegment(px, py, center, projectModelPoint(ox, oy + MOVE_AXIS_LENGTH, oz));
        if (d <= bestDist) { bestDist = d; best = MoveAxis.Y; }
        d = distanceToSegment(px, py, center, projectModelPoint(ox, oy, oz + MOVE_AXIS_LENGTH));
        if (d <= bestDist) { best = MoveAxis.Z; }
        return best;
    }

    private RotationAxis findRotationRing(double px, double py) {
        if (!showCoordinateAxes) return RotationAxis.NONE;
        if (!isInsidePreviewArea((float) px, (float) py)) return RotationAxis.NONE;
        double bestDist = ROTATION_RING_HIT_RADIUS;
        RotationAxis best = RotationAxis.NONE;

        double d = distanceToRotationRing(px, py, RotationAxis.PITCH);
        if (d <= bestDist) { bestDist = d; best = RotationAxis.PITCH; }
        d = distanceToRotationRing(px, py, RotationAxis.YAW);
        if (d <= bestDist) { bestDist = d; best = RotationAxis.YAW; }
        d = distanceToRotationRing(px, py, RotationAxis.ROLL);
        if (d <= bestDist) { best = RotationAxis.ROLL; }
        return best;
    }

    private double distanceToRotationRing(double px, double py, RotationAxis axis) {
        double best = Double.MAX_VALUE;
        ScreenPoint prev = projectRotationRingPoint(axis, 0);
        for (int i = 1; i <= ROTATION_RING_SEGMENTS; i++) {
            ScreenPoint cur = projectRotationRingPoint(axis, i);
            best = Math.min(best, distanceToSegment(px, py, prev, cur));
            prev = cur;
        }
        return best;
    }

    private ScreenPoint projectRotationRingPoint(RotationAxis axis, int segment) {
        float angle = (float) (Math.PI * 2.0 * segment / ROTATION_RING_SEGMENTS);
        float cos = (float) Math.cos(angle) * ROTATION_RING_RADIUS;
        float sin = (float) Math.sin(angle) * ROTATION_RING_RADIUS;
        float ox = getActiveOffsetX();
        float oy = getActiveOffsetY();
        float oz = getActiveOffsetZ();
        return switch (axis) {
            case PITCH -> projectModelPoint(ox, oy + cos, oz + sin);
            case YAW -> projectModelPoint(ox + cos, oy, oz + sin);
            case ROLL -> projectModelPoint(ox + cos, oy + sin, oz);
            default -> projectModelPoint(ox, oy, oz);
        };
    }

    private int findPreviewItem(double px, double py) {
        for (int i = EDITOR_ITEMS.size() - 1; i >= 0; i--) {
            EditorItemModel item = EDITOR_ITEMS.get(i);
            ScreenPoint pt = projectPreviewPoint(item.offsetX, item.offsetY, item.offsetZ);
            int halfSize = Math.max(12, getEditorItemPreviewBoundsSize() / 2);
            if (px >= pt.x - halfSize && px <= pt.x + halfSize && py >= pt.y - halfSize && py <= pt.y + halfSize) {
                return i;
            }
        }
        return -1;
    }

    private static double distanceToSegment(double px, double py, ScreenPoint s, ScreenPoint e) {
        double lineX = e.x - s.x;
        double lineY = e.y - s.y;
        double lenSq = lineX * lineX + lineY * lineY;
        if (lenSq < 0.001) {
            double dx = px - s.x, dy = py - s.y;
            return Math.sqrt(dx * dx + dy * dy);
        }
        double t = ((px - s.x) * lineX + (py - s.y) * lineY) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = s.x + t * lineX, cy = s.y + t * lineY;
        double dx = px - cx, dy = py - cy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private float getPreviewPixelsPerGrid() {
        if (previewScale > 0.0F) return previewScale;
        int w = previewAreaRight - previewAreaLeft - 20;
        int h = previewAreaBottom - previewAreaTop - 58;
        if (w <= 0 || h <= 0) return 24.0F;
        return getPreviewBaseScale(w, h) * previewZoom;
    }

    private static float getPreviewBaseScale(int width, int height) {
        return Math.max(24.0F, Math.min(width, height) * PREVIEW_SCALE_FACTOR);
    }

    private int getEditorItemPreviewBoundsSize() {
        return Math.max(42, Math.round(getPreviewPixelsPerGrid() * ITEM_PREVIEW_BOUNDS_SCALE));
    }

    private void updatePreviewScreenOrigin() {
        if (surfaceView == null) {
            previewScreenLeft = 0;
            previewScreenTop = 0;
            return;
        }
        surfaceView.getLocationInWindow(previewSurfaceLocation);
        previewScreenLeft = (int) Math.round(previewSurfaceLocation[0] / previewGuiScale);
        previewScreenTop = (int) Math.round(previewSurfaceLocation[1] / previewGuiScale);
    }

    private float toPreviewGuiCoord(float value) {
        return (float) (value / previewGuiScale);
    }

    // ═══════════════════════════════════════════════════════
    //  模型管理
    // ═══════════════════════════════════════════════════════

    private PlayerEntityModel getPreviewModel() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return null;
        if (slimModel) {
            if (slimPreviewModel == null) {
                slimPreviewModel = new PlayerEntityModel(
                        client.getLoadedEntityModels().getModelPart(ModModelLayers.COMBINED_BODY_SLIM), true);
            }
            return slimPreviewModel;
        }
        if (defaultPreviewModel == null) {
            defaultPreviewModel = new PlayerEntityModel(
                    client.getLoadedEntityModels().getModelPart(ModModelLayers.COMBINED_BODY), false);
        }
        return defaultPreviewModel;
    }

    private Identifier getPreviewTexture() {
        PlayerListEntry entry = getSelectedPlayerEntry();
        if (selectedSkinSource == SkinSource.PLAYER && entry != null) {
            return entry.getSkinTextures().texture();
        }
        return Identifier.of("monvhua", "textures/local_skin/" + selectedSkin + ".png");
    }

    private void preparePreviewModel(PlayerEntityModel model) {
        for (ModelPart part : model.getRootPart().traverse()) {
            part.resetTransform();
            part.visible = true;
            part.hidden = false;
        }
        ModelPart root = model.getRootPart();
        root.originX = 0.0F;
        root.originY = 0.0F;
        root.originZ = 0.0F;
        root.pitch = (float) Math.toRadians(modelPitch);
        root.yaw = (float) Math.toRadians(-modelYaw);
        root.roll = (float) Math.toRadians(modelRoll);
        setUniformScale(root, wholeBodyScale);

        boolean showAll = showWholePreview || selectedPart.equals("all");
        model.head.visible = showAll || selectedPart.equals("head");
        model.hat.visible = model.head.visible;
        model.body.visible = showAll || selectedPart.equals("torso");
        model.jacket.visible = model.body.visible;
        model.leftArm.visible = showAll || selectedPart.equals("left_arm");
        model.leftSleeve.visible = model.leftArm.visible;
        model.rightArm.visible = showAll || selectedPart.equals("right_arm");
        model.rightSleeve.visible = model.rightArm.visible;
        model.leftLeg.visible = showAll || selectedPart.equals("left_leg");
        model.leftPants.visible = model.leftLeg.visible;
        model.rightLeg.visible = showAll || selectedPart.equals("right_leg");
        model.rightPants.visible = model.rightLeg.visible;

        if (!showAll) {
            centerSelectedPart(model);
        } else {
            moveWholeModelToChestPivot(model);
        }

        Map<String, PartPose> previewPoses = getActivePoseMap();
        applyPartPose("head", model.head, previewPoses.get("head"));
        applyPartPose("torso", model.body, previewPoses.get("torso"));
        applyPartPose("left_arm", model.leftArm, previewPoses.get("left_arm"));
        applyPartPose("right_arm", model.rightArm, previewPoses.get("right_arm"));
        applyPartPose("left_leg", model.leftLeg, previewPoses.get("left_leg"));
        applyPartPose("right_leg", model.rightLeg, previewPoses.get("right_leg"));
    }

    public static PlayerEntityModel getPreparedWorldPreviewModel() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return null;

        PlayerEntityModel model;
        if (slimModel) {
            if (worldPreviewModelSlim == null) {
                worldPreviewModelSlim = new PlayerEntityModel(
                        client.getLoadedEntityModels().getModelPart(ModModelLayers.COMBINED_BODY_SLIM), true);
            }
            model = worldPreviewModelSlim;
        } else {
            if (worldPreviewModelDefault == null) {
                worldPreviewModelDefault = new PlayerEntityModel(
                        client.getLoadedEntityModels().getModelPart(ModModelLayers.COMBINED_BODY), false);
            }
            model = worldPreviewModelDefault;
        }

        for (ModelPart part : model.getRootPart().traverse()) {
            part.resetTransform();
            part.visible = true;
            part.hidden = false;
        }
        boolean showAll = showWholePreview || selectedPart.equals("all");
        model.head.visible = showAll || selectedPart.equals("head");
        model.hat.visible = model.head.visible;
        model.body.visible = showAll || selectedPart.equals("torso");
        model.jacket.visible = model.body.visible;
        model.leftArm.visible = showAll || selectedPart.equals("left_arm");
        model.leftSleeve.visible = model.leftArm.visible;
        model.rightArm.visible = showAll || selectedPart.equals("right_arm");
        model.rightSleeve.visible = model.rightArm.visible;
        model.leftLeg.visible = showAll || selectedPart.equals("left_leg");
        model.leftPants.visible = model.leftLeg.visible;
        model.rightLeg.visible = showAll || selectedPart.equals("right_leg");
        model.rightPants.visible = model.rightLeg.visible;

        if (!showAll) {
            centerSelectedPart(model);
        } else {
            moveWholeModelToChestPivot(model);
        }

        Map<String, PartPose> previewPoses = getActivePoseMap();
        applyPartPose("head", model.head, previewPoses.get("head"));
        applyPartPose("torso", model.body, previewPoses.get("torso"));
        applyPartPose("left_arm", model.leftArm, previewPoses.get("left_arm"));
        applyPartPose("right_arm", model.rightArm, previewPoses.get("right_arm"));
        applyPartPose("left_leg", model.leftLeg, previewPoses.get("left_leg"));
        applyPartPose("right_leg", model.rightLeg, previewPoses.get("right_leg"));

        return model;
    }

    private static void centerSelectedPart(PlayerEntityModel model) {
        switch (selectedPart) {
            case "head" -> movePart(model.head, 0.0F, 12.0F, 0.0F);
            case "torso" -> movePart(model.body, 0.0F, 2.0F, 0.0F);
            case "left_arm" -> movePart(model.leftArm, 0.0F, 4.0F, 0.0F);
            case "right_arm" -> movePart(model.rightArm, 0.0F, 4.0F, 0.0F);
            case "left_leg" -> movePart(model.leftLeg, 0.0F, 2.0F, 0.0F);
            case "right_leg" -> movePart(model.rightLeg, 0.0F, 2.0F, 0.0F);
        }
    }

    private static void moveWholeModelToChestPivot(PlayerEntityModel model) {
        movePart(model.head, model.head.originX, model.head.originY - PREVIEW_CHEST_PIVOT_Y, model.head.originZ);
        movePart(model.body, model.body.originX, model.body.originY - PREVIEW_CHEST_PIVOT_Y, model.body.originZ);
        movePart(model.leftArm, model.leftArm.originX, model.leftArm.originY - PREVIEW_CHEST_PIVOT_Y, model.leftArm.originZ);
        movePart(model.rightArm, model.rightArm.originX, model.rightArm.originY - PREVIEW_CHEST_PIVOT_Y, model.rightArm.originZ);
        movePart(model.leftLeg, model.leftLeg.originX, model.leftLeg.originY - PREVIEW_CHEST_PIVOT_Y, model.leftLeg.originZ);
        movePart(model.rightLeg, model.rightLeg.originX, model.rightLeg.originY - PREVIEW_CHEST_PIVOT_Y, model.rightLeg.originZ);
    }

    private static void movePart(ModelPart part, float x, float y, float z) {
        part.originX = x;
        part.originY = y;
        part.originZ = z;
    }

    private static void applyPartPose(String partName, ModelPart part, PartPose pose) {
        applyPose(part, pose);
        ModelPart blendPart = getBlendPart(partName, part);
        if (blendPart != null) {
            blendPart.visible = false;
        }
        ModelPart bendPart = getBendPart(partName, part);
        if (bendPart != null && pose != null && poseEditMode == PoseEditMode.SKELETAL) {
            applyBendPose(bendPart, pose);
            if (blendPart != null && hasBendPose(pose)) {
                blendPart.visible = true;
                applyHalfBendPose(blendPart, pose);
            }
        }
    }

    private static ModelPart getBendPart(String partName, ModelPart part) {
        String childName = switch (partName) {
            case "torso" -> CombinedBodyModelData.WAIST;
            case "left_arm" -> CombinedBodyModelData.LEFT_FOREARM;
            case "right_arm" -> CombinedBodyModelData.RIGHT_FOREARM;
            case "left_leg" -> CombinedBodyModelData.LEFT_LOWER_LEG;
            case "right_leg" -> CombinedBodyModelData.RIGHT_LOWER_LEG;
            default -> null;
        };
        return childName != null && part.hasChild(childName) ? part.getChild(childName) : null;
    }

    private static ModelPart getBlendPart(String partName, ModelPart part) {
        String childName = switch (partName) {
            case "torso" -> CombinedBodyModelData.WAIST_BLEND;
            case "left_arm" -> CombinedBodyModelData.LEFT_ELBOW_BLEND;
            case "right_arm" -> CombinedBodyModelData.RIGHT_ELBOW_BLEND;
            case "left_leg" -> CombinedBodyModelData.LEFT_KNEE_BLEND;
            case "right_leg" -> CombinedBodyModelData.RIGHT_KNEE_BLEND;
            default -> null;
        };
        return childName != null && part.hasChild(childName) ? part.getChild(childName) : null;
    }

    private static void applyPose(ModelPart part, PartPose pose) {
        if (pose == null) return;
        float r = (float) (Math.PI / 180.0);
        part.pitch += pose.pitch * r;
        part.yaw += pose.yaw * r;
        part.roll += pose.roll * r;
        setUniformScale(part, pose.scale);
    }

    private static void applyBendPose(ModelPart part, PartPose pose) {
        float r = (float) (Math.PI / 180.0);
        part.pitch += pose.bendPitch * r;
        part.yaw += pose.bendYaw * r;
        part.roll += pose.bendRoll * r;
    }

    private static void applyHalfBendPose(ModelPart part, PartPose pose) {
        float r = (float) (Math.PI / 180.0);
        part.pitch += pose.bendPitch * 0.5F * r;
        part.yaw += pose.bendYaw * 0.5F * r;
        part.roll += pose.bendRoll * 0.5F * r;
    }

    private static boolean hasBendPose(PartPose pose) {
        return pose.bendPitch != 0.0F || pose.bendYaw != 0.0F || pose.bendRoll != 0.0F;
    }

    private static void setUniformScale(ModelPart part, float scale) {
        part.xScale *= scale;
        part.yScale *= scale;
        part.zScale *= scale;
    }

    // ═══════════════════════════════════════════════════════
    //  按钮标签刷新
    // ═══════════════════════════════════════════════════════

    private void refreshButtonLabels() {
        // 皮肤按钮
        for (Button btn : skinButtons) {
            String text = btn.getText().toString().replace("> ", "").trim();
            boolean isSelected = selectedSkinSource == SkinSource.LOCAL && text.equals(selectedSkin);
            btn.setText(truncate(text, 10));
            btn.setTextColor(isSelected ? 0xFFFFDD66 : 0xFFE8E8E8);
        }
        // 部位按钮
        for (Button btn : partButtons) {
            String text = btn.getText().toString().replace("> ", "").replace("  ", "").trim();
            boolean isSelected = text.equals(selectedPart);
            btn.setText((isSelected ? "> " : "  ") + text);
            btn.setTextColor(isSelected ? 0xFFFFDD66 : 0xFFE8E8E8);
        }
        // 姿势按钮启用状态
        boolean canEditPose = !selectedPart.equals("all");
        for (Button btn : poseButtons) {
            btn.setEnabled(canEditPose);
        }

        if (playerButton != null) {
            String name = selectedSkinSource == SkinSource.PLAYER && !selectedPlayerName.isBlank()
                    ? selectedPlayerName : "选择";
            playerButton.setText("玩家: " + name);
        }
        if (modelTypeButton != null) {
            modelTypeButton.setText("Model: " + (slimModel ? "slim-默认" : "default"));
            modelTypeButton.setOnClickListener(v -> {
                slimModel = !slimModel;
                refreshButtonLabels();
            });
        }
        if (poseModeButton != null) {
            poseModeButton.setText("Pose Mode: " + (poseEditMode == PoseEditMode.STATIC_PART ? "Static-普通模型" : "Skeletal-可弯曲模型"));
            poseModeButton.setOnClickListener(v -> {
                poseEditMode = poseEditMode == PoseEditMode.STATIC_PART ? PoseEditMode.SKELETAL : PoseEditMode.STATIC_PART;
                rebuildPoseControls();
                refreshButtonLabels();
                refreshNumericValueBindings();
                invalidatePreview();
            });
        }
        if (resetPoseButton != null) {
            resetPoseButton.setText("重置当前姿势");
            resetPoseButton.setEnabled(!selectedPart.equals("all"));
            resetPoseButton.setOnClickListener(v -> {
                resetSelectedPose();
                refreshNumericValueBindings();
                invalidatePreview();
            });
        }
        if (resetAllPoseButton != null) {
            resetAllPoseButton.setText("重置全部肢体");
            resetAllPoseButton.setOnClickListener(v -> {
                resetAllPartPoses();
                refreshNumericValueBindings();
                invalidatePreview();
            });
        }
        if (runCommandButton != null) {
            runCommandButton.setText("给予肢体");
            runCommandButton.setOnClickListener(v -> runGiveCommand());
        }
        if (placeButton != null) {
            placeButton.setText("放置模型");
            placeButton.setOnClickListener(v -> placePosedBody());
        }
        if (applySkeletalButton != null) {
            applySkeletalButton.setText("应用骨骼姿势");
            applySkeletalButton.setEnabled(poseEditMode == PoseEditMode.SKELETAL);
            applySkeletalButton.setOnClickListener(v -> applySkeletalPose());
        }
        if (itemButton != null) {
            itemButton.setText("物品: " + getSelectedItemLabel());
        }
        if (itemDisplayModeButton != null) {
            itemDisplayModeButton.setText("物品显示: " + getActiveItemDisplayMode().label);
            itemDisplayModeButton.setOnClickListener(v -> toggleActiveItemDisplayMode());
        }
        if (placeItemsButton != null) {
            placeItemsButton.setText("放置物品模型(" + EDITOR_ITEMS.size() + ")");
            placeItemsButton.setEnabled(!EDITOR_ITEMS.isEmpty());
            placeItemsButton.setOnClickListener(v -> placeEditorItems());
        }
        if (clearSelectedItemButton != null) {
            clearSelectedItemButton.setText("清除选中");
            clearSelectedItemButton.setEnabled(hasSelectedItemModel());
            clearSelectedItemButton.setOnClickListener(v -> clearSelectedItemModel());
        }
        if (clearAllItemsButton != null) {
            clearAllItemsButton.setText("清除全部");
            clearAllItemsButton.setEnabled(!EDITOR_ITEMS.isEmpty());
            clearAllItemsButton.setOnClickListener(v -> clearAllItemModels());
        }
        if (resetTransformButton != null) {
            resetTransformButton.setText("重置当前偏移");
            resetTransformButton.setOnClickListener(v -> resetActiveTransform());
        }
        if (showWholeButton != null) {
            showWholeButton.setText("整体 " + (showWholePreview ? "开" : "关"));
            showWholeButton.setOnClickListener(v -> {
                showWholePreview = !showWholePreview;
                refreshButtonLabels();
            });
        }
        if (coordToggleButton != null) {
            coordToggleButton.setText("坐标 " + (showCoordinateAxes ? "开" : "关"));
            coordToggleButton.setOnClickListener(v -> {
                showCoordinateAxes = !showCoordinateAxes;
                refreshButtonLabels();
            });
        }
        if (coordMovableButton != null) {
            coordMovableButton.setText("跟随 " + (coordinateAxesMovable ? "开" : "关"));
            coordMovableButton.setOnClickListener(v -> {
                coordinateAxesMovable = !coordinateAxesMovable;
                refreshButtonLabels();
            });
        }
        if (worldPreviewToggleButton != null) {
            worldPreviewToggleButton.setText("模式 " + (worldPreviewMode == PreviewMode.FOLLOW_PLAYER ? "预览" : "放置"));
            worldPreviewToggleButton.setOnClickListener(v -> {
                toggleWorldPreviewMode();
            });
        }
    }

    // ═══════════════════════════════════════════════════════
    //  动作命令 / 网络
    // ═══════════════════════════════════════════════════════

    private void runGiveCommand() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.networkHandler == null) return;
        String command;
        if (selectedSkinSource == SkinSource.PLAYER && !selectedPlayerName.isBlank()) {
            command = "clairvoyance-肢体|获取 @s " + selectedPlayerName + " " + selectedPart;
        } else {
            command = "clairvoyance-肢体|获取 @s localskin " + selectedSkin + " " + selectedPart;
        }
        if (slimModel && (selectedSkinSource != SkinSource.PLAYER || !selectedPart.equals("head"))) {
            command += " slim";
        }
        client.player.networkHandler.sendChatCommand(command);
    }

    private void placePosedBody() {
        boolean skeletalMode = poseEditMode == PoseEditMode.SKELETAL;
        ClientPlayNetworking.send(new PlacePosedBodyC2SPacket(selectedSkin, slimModel,
                skeletalMode ? createSkeletalPoseValueArray() : createStaticPoseValueArray(),
                skeletalMode ? createSkeletalBendValueArray() : null,
                selectedSkinSource == SkinSource.PLAYER, selectedPlayerName,
                modelOffsetX, modelOffsetY, modelOffsetZ,
                modelPitch, modelYaw, modelRoll, wholeBodyScale));
    }

    private void applySkeletalPose() {
        ClientPlayNetworking.send(new ApplySkeletalPoseC2SPacket(
                createSkeletalPoseValueArray(),
                createSkeletalBendValueArray()));
    }

    private void placeEditorItems() {
        if (EDITOR_ITEMS.isEmpty()) return;
        List<PlacePoseEditorItemsC2SPacket.ItemPlacement> placements = new ArrayList<>();
        for (EditorItemModel item : EDITOR_ITEMS) {
            Identifier itemId = Registries.ITEM.getId(item.stack.getItem());
            if (itemId != null) {
                placements.add(new PlacePoseEditorItemsC2SPacket.ItemPlacement(
                        itemId, item.offsetX, item.offsetY, item.offsetZ,
                        item.pitch, item.yaw, item.roll, item.displayMode.context));
            }
        }
        ClientPlayNetworking.send(new PlacePoseEditorItemsC2SPacket(placements));
    }

    private void toggleWorldPreviewMode() {
        toggleWorldPreviewModeStatic(true);
        refreshButtonLabels();
    }

    private static void toggleWorldPreviewModeStatic(boolean closeScreenOnFixed) {
        MinecraftClient client = MinecraftClient.getInstance();
        lastWorldPreviewModeToggleTimeMs = System.currentTimeMillis();
        if (worldPreviewMode == PreviewMode.FOLLOW_PLAYER) {
            worldPreviewMode = PreviewMode.FIXED;
            if (client != null && client.player != null) {
                fixedWorldX = client.player.getX();
                fixedWorldY = client.player.getY();
                fixedWorldZ = client.player.getZ();
            }
            worldPreviewEnabled = true;
            if (closeScreenOnFixed && client != null && client.currentScreen != null) {
                client.execute(() -> client.setScreen(null));
            }
        } else {
            worldPreviewMode = PreviewMode.FOLLOW_PLAYER;
            if (activeInstance == null) {
                worldPreviewEnabled = false;
            }
        }
    }

    public static void toggleWorldPreviewModeFromKey() {
        long now = System.currentTimeMillis();
        if (now - lastWorldPreviewModeToggleTimeMs < WORLD_PREVIEW_KEY_DEBOUNCE_MS) {
            return;
        }
        BodyPoseEditorFragment inst = activeInstance;
        if (inst == null && worldPreviewMode != PreviewMode.FIXED) {
            return;
        }
        toggleWorldPreviewModeStatic(true);
        if (inst != null && inst.rootView != null) {
            inst.refreshButtonLabels();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  外部访问 API（静态方法，供 BodyPoseWorldPreviewRenderer 等使用）
    // ═══════════════════════════════════════════════════════

    public static boolean isWorldPreviewActive() {
        return worldPreviewEnabled && (activeInstance != null || worldPreviewMode == PreviewMode.FIXED);
    }

    public static PreviewMode getWorldPreviewMode() { return worldPreviewMode; }
    public static double getFixedWorldX() { return fixedWorldX; }
    public static double getFixedWorldY() { return fixedWorldY; }
    public static double getFixedWorldZ() { return fixedWorldZ; }

    public static boolean isWorldAxesShown() {
        return isWorldPreviewActive() && showCoordinateAxes;
    }

    public static boolean isWorldAxesMovable() {
        return coordinateAxesMovable;
    }

    public static float getWorldModelOffsetX() { return modelOffsetX; }
    public static float getWorldModelOffsetY() { return modelOffsetY; }
    public static float getWorldModelOffsetZ() { return modelOffsetZ; }
    public static float getWorldModelPitch() { return modelPitch; }
    public static float getWorldModelYaw() { return modelYaw; }
    public static float getWorldModelRoll() { return modelRoll; }
    public static float getWorldBodyScale() { return wholeBodyScale; }

    public static String getStaticHighlightedMoveAxis() {
        BodyPoseEditorFragment inst = activeInstance;
        if (inst == null) return "";
        MoveAxis axis = inst.draggingMoveAxis != MoveAxis.NONE ? inst.draggingMoveAxis : inst.hoveredMoveAxis;
        return switch (axis) {
            case X -> "x";
            case Y -> "y";
            case Z -> "z";
            default -> "";
        };
    }

    public static String getStaticHighlightedRotationAxis() {
        BodyPoseEditorFragment inst = activeInstance;
        if (inst == null) return "";
        RotationAxis axis = inst.draggingRotationAxis != RotationAxis.NONE ? inst.draggingRotationAxis : inst.hoveredRotationAxis;
        return switch (axis) {
            case PITCH -> "pitch";
            case YAW -> "yaw";
            case ROLL -> "roll";
            default -> "";
        };
    }

    public static Identifier getWorldSkinTexture() {
        PlayerListEntry entry = getSelectedPlayerEntry();
        if (selectedSkinSource == SkinSource.PLAYER && entry != null) {
            return entry.getSkinTextures().texture();
        }
        return Identifier.of("monvhua", "textures/local_skin/" + selectedSkin + ".png");
    }

    public static boolean isWorldSlimModel() { return slimModel; }

    public static float getPreviewYaw() {
        BodyPoseEditorFragment inst = activeInstance;
        return inst != null ? inst.previewYaw : 0;
    }

    public static float getPreviewPitch() {
        BodyPoseEditorFragment inst = activeInstance;
        return inst != null ? inst.previewPitch : 0;
    }

    public static float getPreviewRoll() {
        BodyPoseEditorFragment inst = activeInstance;
        return inst != null ? inst.previewRoll : 0;
    }

    public static List<EditorItemPreview> getWorldEditorItemPreviews() {
        if (!isWorldPreviewActive() || EDITOR_ITEMS.isEmpty()) {
            return List.of();
        }
        List<EditorItemPreview> previews = new ArrayList<>(EDITOR_ITEMS.size());
        for (EditorItemModel item : EDITOR_ITEMS) {
            previews.add(new EditorItemPreview(item.stack.copyWithCount(1),
                    item.offsetX, item.offsetY, item.offsetZ,
                    item.pitch, item.yaw, item.roll,
                    item.displayMode.context));
        }
        return previews;
    }

    // ── 实例方法（供 mixin 通过 activeInstance 访问） ──

    public boolean isShowingCoordinateAxes() { return showCoordinateAxes; }
    public boolean isCoordinateAxesMovable() { return coordinateAxesMovable; }
    public boolean isEditingPlayerModel() { return !hasSelectedItemModel(); }

    public float getModelOffsetX() { return getActiveOffsetX(); }
    public float getModelOffsetY() { return getActiveOffsetY(); }
    public float getModelOffsetZ() { return getActiveOffsetZ(); }

    public String getHighlightedMoveAxis() {
        MoveAxis axis = draggingMoveAxis != MoveAxis.NONE ? draggingMoveAxis : hoveredMoveAxis;
        return switch (axis) {
            case X -> "x";
            case Y -> "y";
            case Z -> "z";
            default -> "";
        };
    }

    public String getHighlightedRotationAxis() {
        RotationAxis axis = draggingRotationAxis != RotationAxis.NONE ? draggingRotationAxis : hoveredRotationAxis;
        return switch (axis) {
            case PITCH -> "pitch";
            case YAW -> "yaw";
            case ROLL -> "roll";
            default -> "";
        };
    }

    // ═══════════════════════════════════════════════════════
    //  物品 / 变换管理
    // ═══════════════════════════════════════════════════════

    private boolean hasSelectedItemModel() {
        return selectedEditorItemIndex >= 0 && selectedEditorItemIndex < EDITOR_ITEMS.size();
    }

    private String getSelectedItemLabel() {
        return hasSelectedItemModel() ? EDITOR_ITEMS.get(selectedEditorItemIndex).stack.getName().getString() : "玩家模型";
    }

    private String getSelectedItemPreviewLabel() {
        return hasSelectedItemModel() ? getItemPreviewLabel(EDITOR_ITEMS.get(selectedEditorItemIndex).stack) : "player";
    }

    private static String getItemPreviewLabel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id == null ? "item" : id.getPath();
    }

    private String getActiveModelLabel() {
        return hasSelectedItemModel() ? "Item " + (selectedEditorItemIndex + 1) : "Player";
    }

    private EditorItemDisplayMode getActiveItemDisplayMode() {
        return hasSelectedItemModel() ? EDITOR_ITEMS.get(selectedEditorItemIndex).displayMode : defaultItemDisplayMode;
    }

    private void toggleActiveItemDisplayMode() {
        if (hasSelectedItemModel()) {
            EditorItemModel item = EDITOR_ITEMS.get(selectedEditorItemIndex);
            item.displayMode = item.displayMode.next();
        } else {
            defaultItemDisplayMode = defaultItemDisplayMode.next();
        }
        refreshButtonLabels();
        invalidatePreview();
    }

    private void clearSelectedItemModel() {
        if (!hasSelectedItemModel()) return;
        EDITOR_ITEMS.remove(selectedEditorItemIndex);
        selectedEditorItemIndex = Math.min(selectedEditorItemIndex, EDITOR_ITEMS.size() - 1);
        refreshButtonLabels();
        refreshNumericValueBindings();
    }

    private void clearAllItemModels() {
        EDITOR_ITEMS.clear();
        selectedEditorItemIndex = -1;
        refreshButtonLabels();
        refreshNumericValueBindings();
    }

    private void resetActiveTransform() {
        if (hasSelectedItemModel()) {
            EditorItemModel item = EDITOR_ITEMS.get(selectedEditorItemIndex);
            item.offsetX = 0.0F; item.offsetY = 0.0F; item.offsetZ = 0.0F;
            item.pitch = 0.0F; item.yaw = 0.0F; item.roll = 0.0F;
        } else {
            modelOffsetX = 0.0F; modelOffsetY = 0.0F; modelOffsetZ = 0.0F;
            modelPitch = 0.0F; modelYaw = 0.0F; modelRoll = 0.0F;
        }
        refreshNumericValueBindings();
        invalidatePreview();
    }

    // ── 活动偏移/旋转（根据是否选中物品而不同） ──

    private float getActiveOffsetX() { return hasSelectedItemModel() ? EDITOR_ITEMS.get(selectedEditorItemIndex).offsetX : modelOffsetX; }
    private float getActiveOffsetY() { return hasSelectedItemModel() ? EDITOR_ITEMS.get(selectedEditorItemIndex).offsetY : modelOffsetY; }
    private float getActiveOffsetZ() { return hasSelectedItemModel() ? EDITOR_ITEMS.get(selectedEditorItemIndex).offsetZ : modelOffsetZ; }
    private float getActivePitch() { return hasSelectedItemModel() ? EDITOR_ITEMS.get(selectedEditorItemIndex).pitch : modelPitch; }
    private float getActiveYaw() { return hasSelectedItemModel() ? EDITOR_ITEMS.get(selectedEditorItemIndex).yaw : modelYaw; }
    private float getActiveRoll() { return hasSelectedItemModel() ? EDITOR_ITEMS.get(selectedEditorItemIndex).roll : modelRoll; }

    private void setActiveOffsetX(float v) { if (hasSelectedItemModel()) { EDITOR_ITEMS.get(selectedEditorItemIndex).offsetX = v; } else { modelOffsetX = v; } }
    private void setActiveOffsetY(float v) { if (hasSelectedItemModel()) { EDITOR_ITEMS.get(selectedEditorItemIndex).offsetY = v; } else { modelOffsetY = v; } }
    private void setActiveOffsetZ(float v) { if (hasSelectedItemModel()) { EDITOR_ITEMS.get(selectedEditorItemIndex).offsetZ = v; } else { modelOffsetZ = v; } }
    private void setActivePitch(float v) { if (hasSelectedItemModel()) { EDITOR_ITEMS.get(selectedEditorItemIndex).pitch = v; } else { modelPitch = v; } }
    private void setActiveYaw(float v) { if (hasSelectedItemModel()) { EDITOR_ITEMS.get(selectedEditorItemIndex).yaw = v; } else { modelYaw = v; } }
    private void setActiveRoll(float v) { if (hasSelectedItemModel()) { EDITOR_ITEMS.get(selectedEditorItemIndex).roll = v; } else { modelRoll = v; } }

    // ═══════════════════════════════════════════════════════
    //  姿势管理
    // ═══════════════════════════════════════════════════════

    private static Map<String, PartPose> createPartPoses() {
        Map<String, PartPose> poses = new HashMap<>();
        for (String part : BodyModelSelectionCatalog.PARTS) {
            if (!part.equals("all")) poses.put(part, new PartPose());
        }
        return poses;
    }

    private static Map<String, PartPose> getActivePoseMap() {
        return poseEditMode == PoseEditMode.SKELETAL ? SKELETAL_POSES : PART_POSES;
    }

    private static PartPose getSelectedPose() {
        return getActivePoseMap().computeIfAbsent(selectedPart, k -> new PartPose());
    }

    private static void adjustSelectedPose(Axis axis, float amount) {
        if (selectedPart.equals("all")) return;
        setSelectedPoseValue(axis, getSelectedPoseValue(axis) + amount);
    }

    private static float getSelectedPoseValue(Axis axis) {
        if (selectedPart.equals("all")) return 0.0F;
        PartPose pose = getSelectedPose();
        switch (axis) {
            case PITCH -> { return pose.pitch; }
            case YAW -> { return pose.yaw; }
            case ROLL -> { return pose.roll; }
        }
        return 0.0F;
    }

    private static void setSelectedPoseValue(Axis axis, float value) {
        if (selectedPart.equals("all")) return;
        PartPose pose = getSelectedPose();
        switch (axis) {
            case PITCH -> pose.pitch = clampPreview(value, -180.0F, 180.0F);
            case YAW -> pose.yaw = normalizeDegrees(value);
            case ROLL -> pose.roll = normalizeDegrees(value);
        }
    }

    private static boolean hasSelectedBendControls() {
        return poseEditMode == PoseEditMode.SKELETAL && hasBendControlsForPart(selectedPart);
    }

    private static boolean hasBendControlsForPart(String part) {
        return switch (part) {
            case "torso", "left_arm", "right_arm", "left_leg", "right_leg" -> true;
            default -> false;
        };
    }

    private static String getBendLabelPrefix() {
        return switch (selectedPart) {
            case "torso" -> "Waist";
            case "left_arm", "right_arm" -> "Elbow";
            case "left_leg", "right_leg" -> "Knee";
            default -> "Bend";
        };
    }

    private static float getSelectedBendValue(Axis axis) {
        return getPartBendValue(selectedPart, axis);
    }

    private static float getPartBendValue(String part, Axis axis) {
        if (poseEditMode != PoseEditMode.SKELETAL || !hasBendControlsForPart(part)) return 0.0F;
        PartPose pose = getActivePoseMap().computeIfAbsent(part, k -> new PartPose());
        switch (axis) {
            case PITCH -> { return pose.bendPitch; }
            case YAW -> { return pose.bendYaw; }
            case ROLL -> { return pose.bendRoll; }
        }
        return 0.0F;
    }

    private static void setSelectedBendValue(Axis axis, float value) {
        setPartBendValue(selectedPart, axis, value);
    }

    private static void setPartBendValue(String part, Axis axis, float value) {
        if (poseEditMode != PoseEditMode.SKELETAL || !hasBendControlsForPart(part)) return;
        PartPose pose = getActivePoseMap().computeIfAbsent(part, k -> new PartPose());
        switch (axis) {
            case PITCH -> pose.bendPitch = clampPreview(value, -180.0F, 180.0F);
            case YAW -> pose.bendYaw = normalizeDegrees(value);
            case ROLL -> pose.bendRoll = normalizeDegrees(value);
        }
    }

    private static float getSelectedPartScale() {
        if (selectedPart.equals("all")) return 1.0F;
        return getSelectedPose().scale;
    }

    private static void setSelectedPartScale(float value) {
        if (selectedPart.equals("all")) return;
        getSelectedPose().scale = clampPreview(value, MODEL_SCALE_MIN, MODEL_SCALE_MAX);
    }

    private static void setWholeBodyScale(float value) {
        wholeBodyScale = clampPreview(value, MODEL_SCALE_MIN, MODEL_SCALE_MAX);
    }

    private static void resetSelectedPose() {
        if (selectedPart.equals("all")) return;
        PartPose pose = getSelectedPose();
        resetPartPose(pose);
    }

    private static void resetAllPartPoses() {
        if (poseEditMode == PoseEditMode.STATIC_PART) {
            wholeBodyScale = 1.0F;
        }
        for (PartPose pose : getActivePoseMap().values()) {
            resetPartPose(pose);
        }
    }

    private static void resetPartPose(PartPose pose) {
        pose.pitch = 0.0F;
        pose.yaw = 0.0F;
        pose.roll = 0.0F;
        pose.bendPitch = 0.0F;
        pose.bendYaw = 0.0F;
        pose.bendRoll = 0.0F;
        pose.scale = 1.0F;
    }

    private static float[] createStaticPoseValueArray() {
        return createPoseValueArray(PART_POSES);
    }

    private static float[] createSkeletalPoseValueArray() {
        return createPoseValueArray(SKELETAL_POSES);
    }

    private static float[] createSkeletalBendValueArray() {
        float[] values = new float[ApplySkeletalPoseC2SPacket.BEND_VALUE_COUNT];
        writeBend(values, 0, SKELETAL_POSES.get("head"));
        writeBend(values, 3, SKELETAL_POSES.get("torso"));
        writeBend(values, 6, SKELETAL_POSES.get("left_arm"));
        writeBend(values, 9, SKELETAL_POSES.get("right_arm"));
        writeBend(values, 12, SKELETAL_POSES.get("left_leg"));
        writeBend(values, 15, SKELETAL_POSES.get("right_leg"));
        return values;
    }

    private static float[] createPoseValueArray(Map<String, PartPose> poses) {
        float[] values = new float[PlacePosedBodyC2SPacket.POSE_VALUE_COUNT];
        writePose(values, 0, poses.get("head"));
        writePose(values, 4, poses.get("torso"));
        writePose(values, 8, poses.get("left_arm"));
        writePose(values, 12, poses.get("right_arm"));
        writePose(values, 16, poses.get("left_leg"));
        writePose(values, 20, poses.get("right_leg"));
        return values;
    }

    private static void writePose(float[] values, int offset, PartPose pose) {
        values[offset + 3] = 1.0F;
        if (pose == null) return;
        values[offset] = pose.pitch;
        values[offset + 1] = pose.yaw;
        values[offset + 2] = pose.roll;
        values[offset + 3] = pose.scale;
    }

    private static void writeBend(float[] values, int offset, PartPose pose) {
        if (pose == null) return;
        values[offset] = pose.bendPitch;
        values[offset + 1] = pose.bendYaw;
        values[offset + 2] = pose.bendRoll;
    }

    // ═══════════════════════════════════════════════════════
    //  玩家 / 皮肤工具
    // ═══════════════════════════════════════════════════════

    private static List<String> getLocalSkins() {
        List<String> skins = new ArrayList<>();
        for (String skin : BodyModelSelectionCatalog.LOCAL_SKINS) {
            addLocalSkin(skins, skin);
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            for (Identifier id : client.getResourceManager()
                    .findResources("textures/local_skin",
                            resourceId -> resourceId.getNamespace().equals("monvhua")
                                    && resourceId.getPath().endsWith(".png"))
                    .keySet()) {
                String path = id.getPath();
                String prefix = "textures/local_skin/";
                addLocalSkin(skins, path.substring(prefix.length(), path.length() - ".png".length()));
            }
        }
        skins.sort(String.CASE_INSENSITIVE_ORDER);
        return skins;
    }

    private static void addLocalSkin(List<String> skins, String skin) {
        if (skin != null && !skin.isBlank() && !skins.contains(skin)) {
            skins.add(skin);
        }
    }

    private static PlayerListEntry getSelectedPlayerEntry() {
        if (selectedSkinSource != SkinSource.PLAYER || selectedPlayerName.isBlank()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) return null;
        return client.getNetworkHandler().getPlayerListEntry(selectedPlayerName);
    }

    private static String getSelectedSkinLabel() {
        if (selectedSkinSource == SkinSource.PLAYER && !selectedPlayerName.isBlank()) {
            return "玩家:" + selectedPlayerName;
        }
        return selectedSkin;
    }

    private static String getPlayerName(PlayerListEntry entry) {
        String name = entry.getProfile().getName();
        return name != null ? name : "";
    }

    private List<PlayerListEntry> getPlayerEntries() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) return List.of();
        List<PlayerListEntry> entries = new ArrayList<>(client.getNetworkHandler().getPlayerList());
        entries.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(getPlayerName(a), getPlayerName(b)));
        return entries;
    }

    private List<ItemStack> getAvailableItemStacks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return List.of();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!stack.isEmpty()) addItemOption(stacks, stack.copyWithCount(1));
        }
        if (client.player.isCreative()) {
            for (Item item : Registries.ITEM) {
                if (item != Items.AIR && "minecraft".equals(Registries.ITEM.getId(item).getNamespace())) {
                    addItemOption(stacks, new ItemStack(item));
                }
            }
        }
        return stacks;
    }

    private static void addItemOption(List<ItemStack> stacks, ItemStack stack) {
        for (ItemStack existing : stacks) {
            if (existing.isOf(stack.getItem())) return;
        }
        stacks.add(stack);
    }

    // ═══════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════

    private static String getDefaultSelectedPart() {
        for (String part : BodyModelSelectionCatalog.PARTS) {
            if ("all".equals(part)) return part;
        }
        return BodyModelSelectionCatalog.PARTS.length > 0 ? BodyModelSelectionCatalog.PARTS[0] : "all";
    }

    private void clampItemListScroll(int itemCount) {
        int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, itemCount));
        int maxScroll = Math.max(0, itemCount - visibleRows);
        itemListScroll = Math.max(0, Math.min(maxScroll, itemListScroll));
    }

    private static String formatDegrees(float value) {
        return String.format("%.0f", value);
    }

    private static String formatOffset(float value) {
        return String.format("%.2f", value);
    }

    private static String formatNumericValue(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Float parseFloatOrNull(CharSequence text) {
        if (text == null) return null;
        String value = text.toString().trim();
        if (value.isEmpty() || value.equals("-") || value.equals(".") || value.equals("-.")) {
            return null;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static float clampPreview(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float normalizeDegrees(float value) {
        while (value > 180.0F) value -= 360.0F;
        while (value < -180.0F) value += 360.0F;
        return value;
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s == null ? "" : s;
        if (max <= 3) return s.substring(0, Math.max(0, max));
        return s.substring(0, max - 3) + "...";
    }

    // ═══════════════════════════════════════════════════════
    //  内部枚举
    // ═══════════════════════════════════════════════════════

    private enum Axis { PITCH, YAW, ROLL }
    private enum MoveAxis { NONE, X, Y, Z }
    private enum RotationAxis { NONE, PITCH, YAW, ROLL }
    private enum PoseEditMode { STATIC_PART, SKELETAL }

    public enum PreviewMode {
        FOLLOW_PLAYER,
        FIXED
    }

    public record EditorItemPreview(ItemStack stack,
                                    float offsetX, float offsetY, float offsetZ,
                                    float pitch, float yaw, float roll,
                                    ItemDisplayContext displayContext) {
    }

    private enum SkinSource {
        LOCAL,
        PLAYER
    }

    // ═══════════════════════════════════════════════════════
    //  内部数据类
    // ═══════════════════════════════════════════════════════

    private final class NumericValueBinding {
        private final Supplier<Float> getter;
        private final Consumer<Float> setter;
        private final float min;
        private final float max;
        private final boolean wrap;
        private EditText field;
        private boolean syncing;

        private NumericValueBinding(Supplier<Float> getter, Consumer<Float> setter, float min, float max, boolean wrap) {
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.wrap = wrap;
        }

        private void attach(EditText field) {
            this.field = field;
            sync();
            field.addTextChangedListener(new STWatcher(text -> {
                if (syncing) return;
                Float value = parseFloatOrNull(text);
                if (value != null) {
                    apply(value, false);
                }
            }));
        }

        private void add(float amount) {
            apply(getter.get() + amount, true);
        }

        private void apply(float value, boolean syncField) {
            float next = wrap ? normalizeDegrees(value) : clampPreview(value, min, max);
            setter.accept(next);
            if (syncField) {
                sync();
            }
            invalidatePreview();
        }

        private void sync() {
            if (field == null) return;
            String next = formatNumericValue(getter.get());
            CharSequence current = field.getText();
            if (current != null && next.contentEquals(current)) return;
            syncing = true;
            try {
                field.setText(next);
            } catch (RuntimeException ignored) {
                // Modern UI can throw while recalculating EditText layout.
            } finally {
                syncing = false;
            }
        }
    }

    private interface STListener {
        void onChange(CharSequence text);
    }

    private static final class STWatcher implements TextWatcher {
        private final STListener listener;

        private STWatcher(STListener listener) {
            this.listener = listener;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            listener.onChange(s);
        }
    }

    private static final class PartPose {
        private float pitch;
        private float yaw;
        private float roll;
        private float bendPitch;
        private float bendYaw;
        private float bendRoll;
        private float scale = 1.0F;
    }

    private enum EditorItemDisplayMode {
        HAND(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, "Hand"),
        BLOCK(ItemDisplayContext.FIXED, "Block");

        private final ItemDisplayContext context;
        private final String label;

        EditorItemDisplayMode(ItemDisplayContext context, String label) {
            this.context = context;
            this.label = label;
        }

        private EditorItemDisplayMode next() {
            return this == HAND ? BLOCK : HAND;
        }
    }

    private static final class EditorItemModel {
        private final ItemStack stack;
        private float offsetX;
        private float offsetY;
        private float offsetZ;
        private float pitch;
        private float yaw;
        private float roll;
        private EditorItemDisplayMode displayMode;

        private EditorItemModel(ItemStack stack) {
            this.stack = stack;
            this.displayMode = defaultItemDisplayMode;
        }
    }

    private static final class ScreenPoint {
        private final float x;
        private final float y;

        private ScreenPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
