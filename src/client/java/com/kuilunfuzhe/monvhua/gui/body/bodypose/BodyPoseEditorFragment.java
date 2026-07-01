package com.kuilunfuzhe.monvhua.gui.body.bodypose;

import com.kuilunfuzhe.monvhua.features.block.body.BodyModelSelectionCatalog;
import com.kuilunfuzhe.monvhua.config.BodyPoseDefaultsConfig;
import com.kuilunfuzhe.monvhua.model.CombinedBodyModelData;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.TorsoBendFollower;
import com.kuilunfuzhe.monvhua.network.bodypose.ApplySkeletalPoseC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePoseEditorItemsC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePosedBodyC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlaceTrueSkeletalBodyC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.UpdateBodyPoseDefaultsC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.UpdatePlacedBodyPoseC2SPacket;
import com.kuilunfuzhe.monvhua.renderer.bodypose.skeletal.BodyPoseSkeletalPreviewRenderer;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.R;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.SoundEffectConstants;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

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
    private static final float TRUE_SKELETAL_OFFSET_MIN = -128.0F;
    private static final float TRUE_SKELETAL_OFFSET_MAX = 128.0F;
    private static final float TRUE_SKELETAL_OFFSET_STEP = 0.25F;
    private static final float MODEL_SCALE_MIN = 0.02F;
    private static final float MODEL_SCALE_MAX = 12.0F;
    private static final float MODEL_SCALE_STEP = 0.05F;
    private static final float DEFAULT_PREVIEW_PAN_X = 0.0F;
    private static final float DEFAULT_PREVIEW_ZOOM = 0.65F;
    private static final float PREVIEW_SCALE_FACTOR = 0.36F;
    private static final float PREVIEW_CAMERA_DISTANCE = 24.0F;
    private static final float PREVIEW_NEAR_DEPTH = 3.0F;
    private static final float PREVIEW_MIN_PERSPECTIVE_SCALE = 0.38F;
    private static final float PREVIEW_MAX_PERSPECTIVE_SCALE = 2.25F;
    private static final float PREVIEW_ITEM_MIN_PERSPECTIVE_SCALE = 0.55F;
    private static final float PREVIEW_ITEM_MAX_PERSPECTIVE_SCALE = 1.85F;
    private static final double REEDIT_TARGET_DISTANCE = 8.0D;
    private static final double REEDIT_DISPLAY_PICK_EXPAND = 0.45D;
    private static final double REEDIT_INTERACTION_DISPLAY_RADIUS_SQUARED = 9.0D;
    private static final Identifier COMBINED_BODY_MODEL_ID = Identifier.of("monvhua", "combined_body");
    private static final String[] POSE_PART_ORDER = {
            "head", "torso", "left_arm", "right_arm", "left_leg", "right_leg"
    };
    private static final float PREVIEW_PITCH_BOUNDS_EXTRA_SCALE = 3.2F;
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
    private static final int POSE_HISTORY_MAX_ENTRIES = 200;
    private static final int PREVIEW_ITEM_SELECTOR_WIDTH = 180;
    private static final int PREVIEW_ITEM_SELECTOR_HEIGHT = 18;
    private static final int PREVIEW_ITEM_ROW_HEIGHT = 18;
    private static final int BUTTON_FILL_COLOR = 0x55313A4A;
    private static final int BUTTON_PRESSED_FILL_COLOR = 0x88566A84;
    private static final int BUTTON_DISABLED_FILL_COLOR = 0x33262A32;
    private static final int BUTTON_BORDER_COLOR = 0xB4AAB7C8;
    private static final int BUTTON_PRESSED_BORDER_COLOR = 0xFFE8F1FF;
    private static final int BUTTON_DISABLED_BORDER_COLOR = 0x55747A84;
    private static final int ROOT_BACKGROUND_COLOR = 0xFF171A20;
    private static final int PANEL_BACKGROUND_COLOR = 0xF020242B;
    private static final int PANEL_BORDER_COLOR = 0xFF343B46;
    private static final int PANEL_SECTION_FILL_COLOR = 0xBB1A1D23;
    private static final int VIEWPORT_BACKGROUND_COLOR = 0xFF20242B;
    private static final int VIEWPORT_HEADER_COLOR = 0xFF171A20;
    private static final int VIEWPORT_GRID_DARK_COLOR = 0x1E9AA3AD;
    private static final int VIEWPORT_GRID_LIGHT_COLOR = 0x2EAFB8C2;
    private static final int VIEWPORT_BORDER_COLOR = 0xFF48515E;
    private static final int OUTLINER_ROW_FILL_COLOR = 0x55303943;
    private static final int OUTLINER_ROW_SELECTED_FILL_COLOR = 0xD0554720;
    private static final int OUTLINER_GROUP_TEXT_COLOR = 0xFF8D98A6;
    private static final int OUTLINER_TEXT_COLOR = 0xFFD4D9E1;
    private static final int OUTLINER_SELECTED_TEXT_COLOR = 0xFFFFD36A;
    private static final int NUMERIC_ROW_FILL_COLOR = 0x332A3038;
    private static final int NUMERIC_FIELD_FILL_COLOR = 0xDD151920;
    private static final int NUMERIC_FIELD_BORDER_COLOR = 0xFF3A4350;
    private static final int PARAM_X_COLOR = 0xFFFF5252;
    private static final int PARAM_Y_COLOR = 0xFF59D66B;
    private static final int PARAM_Z_COLOR = 0xFF5A76FF;
    private static final int PARAM_SCALE_COLOR = 0xFFFFD36A;
    private static final float BUTTON_CORNER_RADIUS = 4.0F;
    private static final float BUTTON_PRESSED_ALPHA = 0.82F;
    private static final float BUTTON_NORMAL_ALPHA = 1.0F;
    private static final float BUTTON_PRESSED_SCALE = 0.985F;
    private static final float BUTTON_NORMAL_SCALE = 1.0F;
    private static final String DEFAULT_PRESET_NAME = "预设";

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
    private static PoseEditMode poseEditMode = PoseEditMode.SKELETAL;
    private static EditorItemDisplayMode defaultItemDisplayMode = EditorItemDisplayMode.BLOCK;
    private static final List<EditorItemModel> EDITOR_ITEMS = new ArrayList<>();
    private static final Map<String, PartPose> PART_POSES = createPartPoses();
    private static final Map<String, PartPose> SKELETAL_POSES = createPartPoses();
    private static final Map<String, PartPose> TRUE_SKELETAL_POSES = new HashMap<>();
    private static BodyPosePresetStore.StoreData presetStore = BodyPosePresetStore.load();
    private static int reeditTargetEntityId = -1;

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
    private boolean rotationGizmoMode;
    private boolean ctrlGizmoToggleKeyDown;
    private boolean ctrlShortcutUsed;
    private boolean draggingPreview;
    private boolean draggingRightPreview;
    private boolean previewTransformDirty;
    private boolean skipNextCtrlPollToggle;
    private volatile boolean previewInvalidationQueued;
    private int activePreviewButton;
    private View repeatingTransformView;
    private Runnable repeatingTransformAction;
    private final Runnable repeatingTransformTick = new Runnable() {
        @Override
        public void run() {
            if (repeatingTransformView == null || repeatingTransformAction == null) return;
            repeatingTransformAction.run();
            invalidatePreview();
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
    private Vector3f currentGizmoCenter = new Vector3f();
    private final Vector3f dragStartGizmoCenter = new Vector3f();
    private float dragStartMouseX;
    private float dragStartMouseY;
    private float dragStartMoveOffset;
    private float dragStartAxisScreenX;
    private float dragStartAxisScreenY;
    private float dragStartAxisScreenLength;

    // UI 引用
    private LinearLayout skinButtonsContainer;
    private LinearLayout partButtonsContainer;
    private LinearLayout poseControlsContainer;
    private LinearLayout playerListContainer;
    private LinearLayout itemListContainer;
    private LinearLayout pageTabsContainer;
    private LinearLayout presetListContainer;
    private LinearLayout renameContainer;
    private EditText renameField;
    private RenameTarget renameTarget = RenameTarget.NONE;
    private String renameTargetId = "";
    private final Map<Button, String> partButtonValues = new HashMap<>();
    private Button playerButton;
    private Button modelTypeButton;
    private Button setDefaultModelButton;
    private Button poseModeButton;
    private Button setDefaultPoseModeButton;
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
    private Button placeBackpackButton;
    private Button applySkeletalButton;
    private Button poseHistoryButton;
    private PopupWindow poseHistoryWindow;
    private PopupWindow poseHistoryConfirmWindow;
    private LinearLayout poseHistoryListContainer;
    private List<Button> partButtons = new ArrayList<>();
    private List<Button> poseButtons = new ArrayList<>();
    private List<Button> skinButtons = new ArrayList<>();
    private final List<NumericValueBinding> transformValueBindings = new ArrayList<>();
    private final List<NumericValueBinding> poseValueBindings = new ArrayList<>();
    private final List<PoseHistoryEntry> poseHistoryEntries = new ArrayList<>();
    private BodyPosePresetStore.EditorStateData poseHistoryOrigin;
    private BodyPosePresetStore.EditorStateData poseHistoryBaseline;
    private int poseHistorySequence;
    private int selectedPoseHistoryIndex = -1;
    private boolean suppressPoseHistory;

    // ═══════════════════════════════════════════════════════
    //  静态方法 — 打开编辑器
    // ═══════════════════════════════════════════════════════

    public static void open() {
        reeditTargetEntityId = -1;
        loadPresetStore();
        applyActivePageState();
        openEditorScreen();
    }

    public static boolean tryOpenFromTargetedEditorEntity() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        ReeditTarget target = findTargetedCombinedBodyStack(client);
        if (target == null || target.stack().isEmpty()) {
            return false;
        }

        loadPresetStore();
        resetEditorOpenState();
        reeditTargetEntityId = target.entityId();
        loadEditorStateFromCombinedBodyStack(target.stack());
        saveCurrentPageStateStatic();
        openEditorScreen();
        client.player.sendMessage(Text.literal("已载入已放置躯体姿态"), true);
        return true;
    }

    private static void resetEditorOpenState() {
        selectedPart = getDefaultSelectedPart();
        slimModel = BodyPoseDefaultsConfig.getDefaultSlimModel();
        poseEditMode = poseEditModeFromConfig(BodyPoseDefaultsConfig.getDefaultPoseMode());
        showWholePreview = true;
        worldPreviewEnabled = true;
        EDITOR_ITEMS.clear();
        reeditTargetEntityId = -1;
    }

    private static void openEditorScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        Screen screen = MuiModApi.get().createScreen(new BodyPoseEditorFragment());
        client.setScreen(screen);
    }

    private static ReeditTarget findTargetedCombinedBodyStack(MinecraftClient client) {
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(REEDIT_TARGET_DISTANCE));
        double nearestDistance = REEDIT_TARGET_DISTANCE;
        HitResult blockHit = client.player.raycast(REEDIT_TARGET_DISTANCE, 0.0F, false);
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            nearestDistance = blockHit.getPos().distanceTo(eye);
        }

        Entity targetedEntity = null;
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || !(entity instanceof ItemDisplayEntity || entity instanceof InteractionEntity)) {
                continue;
            }
            Box box = entity.getBoundingBox().expand(entity instanceof ItemDisplayEntity ? REEDIT_DISPLAY_PICK_EXPAND : 0.0D);
            Optional<Vec3d> hit = box.raycast(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = eye.distanceTo(hit.get());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                targetedEntity = entity;
            }
        }

        if (targetedEntity instanceof ItemDisplayEntity display && isCombinedBodyDisplay(display)) {
            return new ReeditTarget(display.getId(), display.getItemStack().copy());
        }
        if (targetedEntity instanceof InteractionEntity interaction) {
            ItemDisplayEntity display = findNearestCombinedBodyDisplay(client, interaction.getPos());
            return display != null ? new ReeditTarget(display.getId(), display.getItemStack().copy()) : null;
        }
        return null;
    }

    private static ItemDisplayEntity findNearestCombinedBodyDisplay(MinecraftClient client, Vec3d pos) {
        ItemDisplayEntity nearest = null;
        double nearestDistanceSquared = REEDIT_INTERACTION_DISPLAY_RADIUS_SQUARED;
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof ItemDisplayEntity display) || !isCombinedBodyDisplay(display)) {
                continue;
            }
            double distanceSquared = display.getPos().squaredDistanceTo(pos);
            if (distanceSquared <= nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = display;
            }
        }
        return nearest;
    }

    private static boolean isCombinedBodyDisplay(ItemDisplayEntity display) {
        return isCombinedBodyStack(display.getItemStack());
    }

    private static boolean isCombinedBodyStack(ItemStack stack) {
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        return COMBINED_BODY_MODEL_ID.equals(model);
    }

    private static void loadEditorStateFromCombinedBodyStack(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = customData != null ? customData.copyNbt() : new NbtCompound();

        loadSkinState(stack, nbt);
        resetPoseMap(PART_POSES);
        resetPoseMap(SKELETAL_POSES);
        resetTrueSkeletalPoseMap();
        loadPoseMap(PART_POSES, nbt);
        loadPoseMap(SKELETAL_POSES, nbt);
        loadBendMap(SKELETAL_POSES, nbt);
        if ("true_skeletal".equals(nbt.getString("body_pose_mode", ""))) {
            poseEditMode = PoseEditMode.TRUE_SKELETAL;
            loadTrueSkeletalPoseMap(nbt);
        } else {
            poseEditMode = PoseEditMode.SKELETAL;
        }
        loadPlacementState(nbt);
    }

    private static void loadSkinState(ItemStack stack, NbtCompound nbt) {
        Optional<String> localSkin = nbt.getString("local_skin");
        if (localSkin.isPresent() && !localSkin.get().isBlank()) {
            selectedSkin = localSkin.get();
            selectedSkinSource = SkinSource.LOCAL;
            selectedPlayerName = "";
        } else {
            ProfileComponent profile = stack.get(DataComponentTypes.PROFILE);
            String profileName = getProfileName(profile);
            if (!profileName.isBlank()) {
                selectedPlayerName = profileName;
                selectedSkinSource = SkinSource.PLAYER;
            }
        }
        slimModel = "slim".equals(nbt.getString("arm_model").orElse("default"));
        if (selectedSkinSource == SkinSource.PLAYER && !slimModel) {
            PlayerListEntry entry = getSelectedPlayerEntry();
            if (entry != null) {
                slimModel = entry.getSkinTextures().model() == SkinTextures.Model.SLIM;
            }
        }
    }

    private static String getProfileName(ProfileComponent profile) {
        if (profile == null) {
            return "";
        }
        Optional<String> name = profile.name();
        if (name.isPresent()) {
            return name.get();
        }
        return profile.gameProfile() != null && profile.gameProfile().getName() != null
                ? profile.gameProfile().getName() : "";
    }

    private static void resetPoseMap(Map<String, PartPose> poses) {
        poses.clear();
        for (String part : BodyModelSelectionCatalog.PARTS) {
            if (!part.equals("all")) {
                poses.put(part, new PartPose());
            }
        }
    }

    private static void resetTrueSkeletalPoseMap() {
        TRUE_SKELETAL_POSES.clear();
        ensureTrueSkeletalPoses();
    }

    private static void loadPoseMap(Map<String, PartPose> poses, NbtCompound nbt) {
        for (String part : POSE_PART_ORDER) {
            PartPose pose = poses.computeIfAbsent(part, ignored -> new PartPose());
            pose.pitch = clampPreview(nbt.getFloat("pose_" + part + "_pitch", 0.0F), -180.0F, 180.0F);
            pose.yaw = normalizeDegrees(nbt.getFloat("pose_" + part + "_yaw", 0.0F));
            pose.roll = normalizeDegrees(nbt.getFloat("pose_" + part + "_roll", 0.0F));
            pose.scale = clampPreview(nbt.getFloat("pose_" + part + "_scale", 1.0F), MODEL_SCALE_MIN, MODEL_SCALE_MAX);
        }
    }

    private static void loadBendMap(Map<String, PartPose> poses, NbtCompound nbt) {
        for (String part : POSE_PART_ORDER) {
            PartPose pose = poses.computeIfAbsent(part, ignored -> new PartPose());
            pose.bendPitch = clampPreview(nbt.getFloat("bend_" + part + "_pitch", 0.0F), -180.0F, 180.0F);
            pose.bendYaw = normalizeDegrees(nbt.getFloat("bend_" + part + "_yaw", 0.0F));
            pose.bendRoll = normalizeDegrees(nbt.getFloat("bend_" + part + "_roll", 0.0F));
        }
    }

    private static void loadTrueSkeletalPoseMap(NbtCompound nbt) {
        ensureTrueSkeletalPoses();
        NbtList bones = nbt.getListOrEmpty("true_skeletal_bones");
        for (int i = 0; i < bones.size(); i++) {
            NbtCompound bone = bones.getCompound(i).orElse(null);
            if (bone == null) {
                continue;
            }
            String name = bone.getString("name", "");
            if (name.isEmpty()) {
                continue;
            }
            PartPose pose = TRUE_SKELETAL_POSES.computeIfAbsent(name, ignored -> new PartPose());
            pose.pitch = clampPreview(bone.getFloat("pitch", 0.0F), -180.0F, 180.0F);
            pose.yaw = normalizeDegrees(bone.getFloat("yaw", 0.0F));
            pose.roll = normalizeDegrees(bone.getFloat("roll", 0.0F));
            pose.offsetX = clampPreview(bone.getFloat("offset_x", 0.0F), TRUE_SKELETAL_OFFSET_MIN, TRUE_SKELETAL_OFFSET_MAX);
            pose.offsetY = clampPreview(bone.getFloat("offset_y", 0.0F), TRUE_SKELETAL_OFFSET_MIN, TRUE_SKELETAL_OFFSET_MAX);
            pose.offsetZ = clampPreview(bone.getFloat("offset_z", 0.0F), TRUE_SKELETAL_OFFSET_MIN, TRUE_SKELETAL_OFFSET_MAX);
            pose.scale = clampPreview(bone.getFloat("scale", 1.0F), MODEL_SCALE_MIN, MODEL_SCALE_MAX);
            pose.visible = bone.getBoolean("visible", true);
        }
    }

    private static void loadPlacementState(NbtCompound nbt) {
        modelOffsetX = clampPreview(nbt.getFloat("pose_model_offset_x", 0.0F), MODEL_OFFSET_MIN, MODEL_OFFSET_MAX);
        modelOffsetY = clampPreview(nbt.getFloat("pose_model_offset_y", 0.0F), MODEL_OFFSET_MIN, MODEL_OFFSET_MAX);
        modelOffsetZ = clampPreview(nbt.getFloat("pose_model_offset_z", 0.0F), MODEL_OFFSET_MIN, MODEL_OFFSET_MAX);
        modelPitch = clampPreview(nbt.getFloat("pose_model_pitch", 0.0F), -180.0F, 180.0F);
        modelYaw = normalizeDegrees(nbt.getFloat("pose_model_yaw", 0.0F));
        modelRoll = normalizeDegrees(nbt.getFloat("pose_model_roll", 0.0F));
        wholeBodyScale = clampPreview(nbt.getFloat("pose_model_scale", 1.0F), MODEL_SCALE_MIN, MODEL_SCALE_MAX);
    }

    // ═══════════════════════════════════════════════════════
    //  Fragment 生命周期
    // ═══════════════════════════════════════════════════════

    private static void loadPresetStore() {
        presetStore = BodyPosePresetStore.load();
    }

    private static BodyPosePresetStore.PageData getActivePage() {
        if (presetStore == null) {
            loadPresetStore();
        }
        if (presetStore.pages == null || presetStore.pages.isEmpty()) {
            presetStore.pages = new ArrayList<>();
            presetStore.pages.add(new BodyPosePresetStore.PageData(
                    BodyPosePresetStore.newId(), "界面 1", captureEditorState("界面 1")));
            presetStore.activePageIndex = 0;
        }
        presetStore.activePageIndex = Math.max(0, Math.min(presetStore.activePageIndex, presetStore.pages.size() - 1));
        return presetStore.pages.get(presetStore.activePageIndex);
    }

    private static void applyActivePageState() {
        BodyPosePresetStore.PageData page = getActivePage();
        if (page.state == null) {
            resetEditorOpenState();
            page.state = captureEditorState(page.name);
        } else {
            applyEditorState(page.state);
        }
    }

    private static void saveCurrentPageStateStatic() {
        BodyPosePresetStore.PageData page = getActivePage();
        page.state = captureEditorState(page.name);
        page.state.name = page.name;
        BodyPosePresetStore.save(presetStore);
    }

    private static BodyPosePresetStore.EditorStateData captureEditorState(String name) {
        BodyPosePresetStore.EditorStateData state = new BodyPosePresetStore.EditorStateData(name);
        state.selectedSkin = selectedSkin;
        state.selectedPlayerName = selectedPlayerName;
        state.selectedSkinSource = selectedSkinSource.name();
        state.selectedPart = selectedPart;
        state.slimModel = slimModel;
        state.modelOffsetX = modelOffsetX;
        state.modelOffsetY = modelOffsetY;
        state.modelOffsetZ = modelOffsetZ;
        state.modelPitch = modelPitch;
        state.modelYaw = modelYaw;
        state.modelRoll = modelRoll;
        state.wholeBodyScale = wholeBodyScale;
        state.poseEditMode = poseEditMode.name();
        state.defaultItemDisplayMode = defaultItemDisplayMode.name();
        state.showWholePreview = showWholePreview;
        state.showCoordinateAxes = showCoordinateAxes;
        state.coordinateAxesMovable = coordinateAxesMovable;
        state.partPoses = capturePoseMap(PART_POSES);
        state.skeletalPoses = capturePoseMap(SKELETAL_POSES);
        ensureTrueSkeletalPoses();
        state.trueSkeletalPoses = capturePoseMap(TRUE_SKELETAL_POSES);
        state.editorItems = captureEditorItems();
        return state;
    }

    private static Map<String, BodyPosePresetStore.PoseData> capturePoseMap(Map<String, PartPose> poses) {
        Map<String, BodyPosePresetStore.PoseData> data = new LinkedHashMap<>();
        for (Map.Entry<String, PartPose> entry : poses.entrySet()) {
            data.put(entry.getKey(), capturePose(entry.getValue()));
        }
        return data;
    }

    private static BodyPosePresetStore.PoseData capturePose(PartPose pose) {
        BodyPosePresetStore.PoseData data = new BodyPosePresetStore.PoseData();
        if (pose == null) {
            return data;
        }
        data.pitch = pose.pitch;
        data.yaw = pose.yaw;
        data.roll = pose.roll;
        data.bendPitch = pose.bendPitch;
        data.bendYaw = pose.bendYaw;
        data.bendRoll = pose.bendRoll;
        data.offsetX = pose.offsetX;
        data.offsetY = pose.offsetY;
        data.offsetZ = pose.offsetZ;
        data.scale = pose.scale;
        data.visible = pose.visible;
        return data;
    }

    private static List<BodyPosePresetStore.EditorItemData> captureEditorItems() {
        List<BodyPosePresetStore.EditorItemData> items = new ArrayList<>();
        for (EditorItemModel item : EDITOR_ITEMS) {
            Identifier itemId = Registries.ITEM.getId(item.stack.getItem());
            if (itemId == null) {
                continue;
            }
            BodyPosePresetStore.EditorItemData data = new BodyPosePresetStore.EditorItemData();
            data.itemId = itemId.toString();
            data.offsetX = item.offsetX;
            data.offsetY = item.offsetY;
            data.offsetZ = item.offsetZ;
            data.pitch = item.pitch;
            data.yaw = item.yaw;
            data.roll = item.roll;
            data.displayMode = item.displayMode.name();
            items.add(data);
        }
        return items;
    }

    private static void applyEditorState(BodyPosePresetStore.EditorStateData state) {
        if (state == null) {
            resetEditorOpenState();
            return;
        }
        selectedSkin = state.selectedSkin != null && !state.selectedSkin.isBlank()
                ? state.selectedSkin : BodyModelSelectionCatalog.LOCAL_SKINS[0];
        selectedPlayerName = state.selectedPlayerName != null ? state.selectedPlayerName : "";
        selectedSkinSource = enumValue(SkinSource.class, state.selectedSkinSource, SkinSource.LOCAL);
        selectedPart = state.selectedPart != null && !state.selectedPart.isBlank()
                ? state.selectedPart : getDefaultSelectedPart();
        slimModel = state.slimModel;
        modelOffsetX = clampPreview(state.modelOffsetX, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX);
        modelOffsetY = clampPreview(state.modelOffsetY, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX);
        modelOffsetZ = clampPreview(state.modelOffsetZ, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX);
        modelPitch = clampPreview(state.modelPitch, -180.0F, 180.0F);
        modelYaw = normalizeDegrees(state.modelYaw);
        modelRoll = normalizeDegrees(state.modelRoll);
        wholeBodyScale = clampPreview(state.wholeBodyScale, MODEL_SCALE_MIN, MODEL_SCALE_MAX);
        poseEditMode = enumValue(PoseEditMode.class, state.poseEditMode, PoseEditMode.SKELETAL);
        defaultItemDisplayMode = enumValue(EditorItemDisplayMode.class, state.defaultItemDisplayMode, EditorItemDisplayMode.BLOCK);
        showWholePreview = state.showWholePreview;
        showCoordinateAxes = state.showCoordinateAxes;
        coordinateAxesMovable = state.coordinateAxesMovable;
        applyPoseMap(PART_POSES, state.partPoses, false);
        applyPoseMap(SKELETAL_POSES, state.skeletalPoses, false);
        applyPoseMap(TRUE_SKELETAL_POSES, state.trueSkeletalPoses, true);
        applyEditorItems(state.editorItems);
        ensureValidSelectedPartForMode();
    }

    private static void applyPoseMap(Map<String, PartPose> target,
                                     Map<String, BodyPosePresetStore.PoseData> source,
                                     boolean trueSkeletal) {
        target.clear();
        if (trueSkeletal) {
            ensureTrueSkeletalPoses();
        } else {
            for (String part : BodyModelSelectionCatalog.PARTS) {
                if (!"all".equals(part)) {
                    target.put(part, new PartPose());
                }
            }
        }
        if (source == null) {
            return;
        }
        for (Map.Entry<String, BodyPosePresetStore.PoseData> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            target.put(entry.getKey(), applyPose(entry.getValue(), trueSkeletal));
        }
    }

    private static PartPose applyPose(BodyPosePresetStore.PoseData data, boolean trueSkeletal) {
        PartPose pose = new PartPose();
        pose.pitch = clampPreview(data.pitch, -180.0F, 180.0F);
        pose.yaw = normalizeDegrees(data.yaw);
        pose.roll = normalizeDegrees(data.roll);
        pose.bendPitch = clampPreview(data.bendPitch, -180.0F, 180.0F);
        pose.bendYaw = normalizeDegrees(data.bendYaw);
        pose.bendRoll = normalizeDegrees(data.bendRoll);
        pose.offsetX = clampPartOffset(data.offsetX, trueSkeletal);
        pose.offsetY = clampPartOffset(data.offsetY, trueSkeletal);
        pose.offsetZ = clampPartOffset(data.offsetZ, trueSkeletal);
        pose.scale = clampPreview(data.scale <= 0.0F ? 1.0F : data.scale, MODEL_SCALE_MIN, MODEL_SCALE_MAX);
        pose.visible = data.visible;
        return pose;
    }

    private static void applyEditorItems(List<BodyPosePresetStore.EditorItemData> items) {
        EDITOR_ITEMS.clear();
        if (items == null) {
            return;
        }
        for (BodyPosePresetStore.EditorItemData data : items) {
            if (data == null || data.itemId == null || data.itemId.isBlank()) {
                continue;
            }
            Identifier id = Identifier.tryParse(data.itemId);
            if (id == null) {
                continue;
            }
            Item item = Registries.ITEM.get(id);
            if (item == Items.AIR) {
                continue;
            }
            EditorItemModel model = new EditorItemModel(new ItemStack(item));
            model.offsetX = data.offsetX;
            model.offsetY = data.offsetY;
            model.offsetZ = data.offsetZ;
            model.pitch = data.pitch;
            model.yaw = data.yaw;
            model.roll = data.roll;
            model.displayMode = enumValue(EditorItemDisplayMode.class, data.displayMode, defaultItemDisplayMode);
            EDITOR_ITEMS.add(model);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    @Override
    public View onCreateView(icyllis.modernui.view.LayoutInflater inflater, ViewGroup container,
                             icyllis.modernui.util.DataSet savedInstanceState) {
        Context ctx = getContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackground(new ColorDrawable(0xCC000000));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener((view, keyCode, event) -> handleEditorKey(keyCode, event));

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
        resetPoseHistoryBaseline();
        return root;
    }

    private boolean handleEditorKey(int keyCode, KeyEvent event) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (!ctrlShortcutUsed && ctrlGizmoToggleKeyDown) {
                    toggleGizmoMode(true);
                }
                ctrlGizmoToggleKeyDown = false;
                ctrlShortcutUsed = false;
                skipNextCtrlPollToggle = false;
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                ctrlGizmoToggleKeyDown = true;
                ctrlShortcutUsed = false;
                skipNextCtrlPollToggle = true;
                return true;
            }
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) {
            return false;
        }
        if (isCtrlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z) {
                ctrlShortcutUsed = true;
                undoPoseHistory();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                ctrlShortcutUsed = true;
                redoPoseHistory();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEY_P) {
            toggleWorldPreviewMode();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F2) {
            beginRenameSelectedTarget();
            return true;
        }
        return false;
    }

    @Override
    public void onViewCreated(View view, icyllis.modernui.util.DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.requestFocus();
        view.post(() -> {
            refreshButtonLabels();
            refreshNumericValueBindings();
            view.requestLayout();
            invalidateView(view);
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
        saveCurrentPageState();
        activeInstance = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        saveCurrentPageState();
        dismissPoseHistoryWindows();
        stopRepeatingTransform();
        rootView = null;
        surfaceView = null;
        pageTabsContainer = null;
        presetListContainer = null;
        poseHistoryListContainer = null;
        renameContainer = null;
        renameField = null;
        previewSurfaceWidth = 0;
        previewSurfaceHeight = 0;
        activeInstance = null;
    }

    // ═══════════════════════════════════════════════════════
    //  左栏 — 皮肤 / 玩家选择
    // ═══════════════════════════════════════════════════════

    private View createLeftPanel(Context ctx) {
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setBackground(new ColorDrawable(ROOT_BACKGROUND_COLOR));

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        stylePanelContainer(panel);

        // 标题
        TextView title = new TextView(ctx);
        title.setText("模型编辑器");
        title.setTextColor(0xFFE8E8E8);
        title.setTextSize(18);
        title.setPadding(0, 0, 0, 8);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        addPresetControls(panel, ctx);

        // ── 玩家选择 ──
        TextView playerLabel = new TextView(ctx);
        playerLabel.setText("玩家");
        playerLabel.setTextSize(11);
        playerLabel.setTextColor(0xFF888888);
        panel.addView(playerLabel, new LinearLayout.LayoutParams(-1, -2));

        playerButton = createStyledButton(ctx);
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

    private void addPresetControls(LinearLayout panel, Context ctx) {
        addSectionLabel(panel, ctx, "姿势预设");

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button savePreset = createStyledButton(ctx);
        savePreset.setText("保存");
        savePreset.setOnClickListener(v -> saveCurrentAsPreset());
        row.addView(savePreset, new LinearLayout.LayoutParams(0, -2, 1f));

        Button newPreset = createStyledButton(ctx);
        newPreset.setText("新建");
        newPreset.setOnClickListener(v -> createPresetFromCurrent());
        row.addView(newPreset, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));

        renameContainer = new LinearLayout(ctx);
        renameContainer.setOrientation(LinearLayout.VERTICAL);
        renameContainer.setVisibility(View.GONE);
        renameField = new EditText(ctx);
        renameField.setTextColor(0xFFE8E8E8);
        renameField.setTextSize(12);
        installEditorKeyHandler(renameField);
        renameContainer.addView(renameField, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout renameButtons = new LinearLayout(ctx);
        renameButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button confirm = createStyledButton(ctx);
        confirm.setText("确认");
        confirm.setOnClickListener(v -> confirmRename());
        renameButtons.addView(confirm, new LinearLayout.LayoutParams(0, -2, 1f));
        Button cancel = createStyledButton(ctx);
        cancel.setText("取消");
        cancel.setOnClickListener(v -> cancelRename());
        renameButtons.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        renameContainer.addView(renameButtons, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(renameContainer, new LinearLayout.LayoutParams(-1, -2));

        presetListContainer = new LinearLayout(ctx);
        presetListContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(presetListContainer, new LinearLayout.LayoutParams(-1, -2));
        rebuildPresetList();
    }

    private void rebuildPageTabs() {
        if (pageTabsContainer == null || getContext() == null) return;
        Context ctx = getContext();
        pageTabsContainer.removeAllViews();

        Button add = createStyledButton(ctx);
        add.setText("+");
        add.setOnClickListener(v -> addEditorPage());
        pageTabsContainer.addView(add, new LinearLayout.LayoutParams(28, -2));

        for (int i = 0; i < presetStore.pages.size(); i++) {
            BodyPosePresetStore.PageData page = presetStore.pages.get(i);
            boolean selected = i == presetStore.activePageIndex;
            LinearLayout tab = new LinearLayout(ctx);
            tab.setOrientation(LinearLayout.HORIZONTAL);

            Button nameButton = createStyledButton(ctx);
            nameButton.setText((selected ? "> " : "") + truncate(page.name, 12));
            nameButton.setTextColor(selected ? 0xFFFFDD66 : 0xFFE8E8E8);
            int pageIndex = i;
            nameButton.setOnClickListener(v -> switchEditorPage(pageIndex));
            nameButton.setOnTouchListener((view, event) -> {
                applyButtonFeedback(view, event);
                if (isSecondaryPress(event)) {
                    beginRenamePage(pageIndex);
                    return true;
                }
                return false;
            });
            tab.addView(nameButton, new LinearLayout.LayoutParams(0, -2, 1f));

            Button close = createStyledButton(ctx);
            close.setText("x");
            close.setOnClickListener(v -> deleteEditorPage(pageIndex));
            tab.addView(close, new LinearLayout.LayoutParams(24, -2));

            pageTabsContainer.addView(tab, new LinearLayout.LayoutParams(0, -2, 1f));
        }
    }

    private void rebuildPresetList() {
        if (presetListContainer == null || getContext() == null) return;
        Context ctx = getContext();
        presetListContainer.removeAllViews();
        if (presetStore.presets.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("暂无已保存预设");
            empty.setTextColor(0xFF888888);
            empty.setPadding(4, 4, 4, 4);
            presetListContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        for (BodyPosePresetStore.PresetData preset : presetStore.presets) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout presetColumn = new LinearLayout(ctx);
            presetColumn.setOrientation(LinearLayout.VERTICAL);

            Button button = createStyledButton(ctx);
            boolean selected = preset.id != null && preset.id.equals(presetStore.selectedPresetId);
            button.setText((selected ? "> " : "") + truncate(preset.name, 13) + "  " + getPresetSummary(preset.state));
            button.setTextColor(selected ? 0xFFFFDD66 : 0xFFE8E8E8);
            button.setOnClickListener(v -> applyPresetToCurrentPage(preset.id));
            button.setOnTouchListener((view, event) -> {
                applyButtonFeedback(view, event);
                if (isSecondaryPress(event)) {
                    beginRenamePreset(preset.id);
                    return true;
                }
                return false;
            });
            presetColumn.addView(button, new LinearLayout.LayoutParams(-1, -2));

            PresetPreviewView preview = new PresetPreviewView(ctx, preset.state);
            presetColumn.addView(preview, new LinearLayout.LayoutParams(-1, 48));
            row.addView(presetColumn, new LinearLayout.LayoutParams(0, -2, 1f));

            Button del = createStyledButton(ctx);
            del.setText("x");
            del.setOnClickListener(v -> deletePreset(preset.id));
            row.addView(del, new LinearLayout.LayoutParams(24, -2));

            presetListContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private String getPresetSummary(BodyPosePresetStore.EditorStateData state) {
        if (state == null) {
            return "空";
        }
        int poseCount = countChangedPoses(state.partPoses) + countChangedPoses(state.skeletalPoses) + countChangedPoses(state.trueSkeletalPoses);
        String skin = "PLAYER".equals(state.selectedSkinSource) && state.selectedPlayerName != null && !state.selectedPlayerName.isBlank()
                ? state.selectedPlayerName : state.selectedSkin;
        return truncate(skin, 10) + " | " + state.poseEditMode + " | " + poseCount + " 处";
    }

    private int countChangedPoses(Map<String, BodyPosePresetStore.PoseData> poses) {
        if (poses == null) return 0;
        int count = 0;
        for (BodyPosePresetStore.PoseData pose : poses.values()) {
            if (pose == null) continue;
            if (pose.pitch != 0.0F || pose.yaw != 0.0F || pose.roll != 0.0F
                    || pose.bendPitch != 0.0F || pose.bendYaw != 0.0F || pose.bendRoll != 0.0F
                    || pose.offsetX != 0.0F || pose.offsetY != 0.0F || pose.offsetZ != 0.0F
                    || pose.scale != 1.0F || !pose.visible) {
                count++;
            }
        }
        return count;
    }

    private static boolean isSecondaryPress(MotionEvent event) {
        int action = event.getActionMasked();
        return (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_DOWN)
                && (event.getActionButton() == MotionEvent.BUTTON_SECONDARY
                || event.isButtonPressed(MotionEvent.BUTTON_SECONDARY));
    }

    private void saveCurrentPageState() {
        if (presetStore == null) {
            return;
        }
        saveCurrentPageStateStatic();
    }

    private void refreshPresetUi() {
        rebuildPageTabs();
        rebuildPresetList();
        refreshButtonLabels();
        refreshNumericValueBindings();
        invalidatePreview();
        if (rootView != null) {
            View view = rootView;
            postToUiThread(() -> {
                view.requestLayout();
                view.invalidate();
            });
        }
    }

    private void refreshEditorStructure() {
        rebuildPartButtons();
        rebuildPoseControls();
        refreshPresetUi();
    }

    private void resetPoseHistoryBaseline() {
        poseHistoryEntries.clear();
        poseHistorySequence = 0;
        selectedPoseHistoryIndex = -1;
        poseHistoryOrigin = captureEditorState("history");
        poseHistoryBaseline = poseHistoryOrigin;
        rebuildPoseHistoryList();
        refreshButtonLabels();
    }

    private void recordPoseHistoryStep(String source) {
        if (suppressPoseHistory) {
            return;
        }
        BodyPosePresetStore.EditorStateData current = captureEditorState("history");
        if (poseHistoryBaseline == null) {
            poseHistoryBaseline = current;
            return;
        }
        List<String> changes = describeEditorStateChanges(poseHistoryBaseline, current);
        if (changes.isEmpty()) {
            poseHistoryBaseline = current;
            return;
        }
        truncatePoseHistoryRedoBranch();
        poseHistorySequence++;
        poseHistoryEntries.add(new PoseHistoryEntry(poseHistorySequence, source, summarizeHistoryChanges(changes), changes, current));
        while (poseHistoryEntries.size() > POSE_HISTORY_MAX_ENTRIES) {
            poseHistoryEntries.remove(0);
        }
        selectedPoseHistoryIndex = poseHistoryEntries.size() - 1;
        poseHistoryBaseline = current;
        rebuildPoseHistoryList();
        refreshButtonLabels();
    }

    private void truncatePoseHistoryRedoBranch() {
        if (poseHistoryEntries.isEmpty()) {
            return;
        }
        if (selectedPoseHistoryIndex < 0) {
            poseHistoryEntries.clear();
            poseHistorySequence = 0;
            return;
        }
        while (poseHistoryEntries.size() > selectedPoseHistoryIndex + 1) {
            poseHistoryEntries.remove(poseHistoryEntries.size() - 1);
        }
        poseHistorySequence = poseHistoryEntries.isEmpty()
                ? 0
                : poseHistoryEntries.get(poseHistoryEntries.size() - 1).step;
    }

    private void undoPoseHistory() {
        if (selectedPoseHistoryIndex < 0 || poseHistoryEntries.isEmpty()) {
            showActionbar("没有可撤回的历史记录");
            return;
        }
        int targetIndex = selectedPoseHistoryIndex - 1;
        if (targetIndex >= 0) {
            applyPoseHistorySnapshot(poseHistoryEntries.get(targetIndex).snapshot,
                    targetIndex, "已撤回到历史步骤 #" + poseHistoryEntries.get(targetIndex).step);
            return;
        }
        BodyPosePresetStore.EditorStateData origin = poseHistoryOrigin != null
                ? poseHistoryOrigin
                : captureEditorState("history");
        applyPoseHistorySnapshot(origin, -1, "已撤回到历史起点");
    }

    private void redoPoseHistory() {
        if (poseHistoryEntries.isEmpty()) {
            showActionbar("没有可恢复的历史记录");
            return;
        }
        int targetIndex = selectedPoseHistoryIndex + 1;
        if (targetIndex < 0 || targetIndex >= poseHistoryEntries.size()) {
            showActionbar("没有可恢复的历史记录");
            return;
        }
        PoseHistoryEntry entry = poseHistoryEntries.get(targetIndex);
        applyPoseHistorySnapshot(entry.snapshot, targetIndex, "已恢复到历史步骤 #" + entry.step);
    }

    private void openPoseHistoryWindow() {
        if (rootView == null || getContext() == null) {
            return;
        }
        if (poseHistoryBaseline == null) {
            poseHistoryBaseline = captureEditorState("history");
        }
        dismissPoseHistoryWindows();

        Context ctx = getContext();
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(10, 10, 10, 10);
        panel.setBackground(new ColorDrawable(0xF0111118));

        TextView title = new TextView(ctx);
        title.setText("姿势历史记录");
        title.setTextColor(0xFFE8E8E8);
        title.setTextSize(14);
        title.setPadding(0, 0, 0, 6);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scrollView = new ScrollView(ctx);
        poseHistoryListContainer = new LinearLayout(ctx);
        poseHistoryListContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(poseHistoryListContainer, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scrollView, new LinearLayout.LayoutParams(-1, 380));

        LinearLayout footer = new LinearLayout(ctx);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        Button close = createStyledButton(ctx);
        close.setText("关闭");
        close.setOnClickListener(v -> dismissPoseHistoryWindows());
        footer.addView(close, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        poseHistoryWindow = new PopupWindow(panel, 480, 470, true);
        poseHistoryWindow.setBackgroundDrawable(new ColorDrawable(0xF0111118));
        poseHistoryWindow.setOutsideTouchable(true);
        poseHistoryWindow.setFocusable(true);
        poseHistoryWindow.setOnDismissListener(() -> {
            poseHistoryListContainer = null;
            poseHistoryWindow = null;
        });
        poseHistoryWindow.showAtLocation(rootView, Gravity.CENTER, 0, 0);
        rebuildPoseHistoryList();
    }

    private void rebuildPoseHistoryList() {
        if (poseHistoryListContainer == null || getContext() == null) {
            return;
        }
        Context ctx = getContext();
        poseHistoryListContainer.removeAllViews();
        if (poseHistoryEntries.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("暂无调整记录");
            empty.setTextColor(0xFF888888);
            empty.setPadding(4, 4, 4, 4);
            poseHistoryListContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        for (int i = 0; i < poseHistoryEntries.size(); i++) {
            PoseHistoryEntry entry = poseHistoryEntries.get(i);
            LinearLayout entryBox = new LinearLayout(ctx);
            entryBox.setOrientation(LinearLayout.VERTICAL);
            Button row = createStyledButton(ctx);
            boolean selected = i == selectedPoseHistoryIndex;
            row.setText((selected ? "> " : "  ") + "#" + entry.step + " " + entry.source + "  " + entry.summary);
            row.setTextColor(selected ? 0xFFFFDD66 : 0xFFE8E8E8);
            int index = i;
            row.setOnClickListener(v -> {
                selectedPoseHistoryIndex = index;
                rebuildPoseHistoryList();
            });
            row.setOnTouchListener((view, event) -> {
                applyButtonFeedback(view, event);
                if (isSecondaryPress(event)) {
                    selectedPoseHistoryIndex = index;
                    rebuildPoseHistoryList();
                    showPoseHistoryJumpConfirm(view, index);
                    return true;
                }
                return false;
            });
            entryBox.addView(row, new LinearLayout.LayoutParams(-1, -2));
            if (selected) {
                TextView detail = new TextView(ctx);
                detail.setText(String.join("\n", entry.changes));
                detail.setTextColor(0xFFB8C7D8);
                detail.setTextSize(11);
                detail.setPadding(8, 2, 4, 6);
                entryBox.addView(detail, new LinearLayout.LayoutParams(-1, -2));
            }
            poseHistoryListContainer.addView(entryBox, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void showPoseHistoryJumpConfirm(View anchor, int index) {
        if (index < 0 || index >= poseHistoryEntries.size() || getContext() == null) {
            return;
        }
        if (poseHistoryConfirmWindow != null && poseHistoryConfirmWindow.isShowing()) {
            poseHistoryConfirmWindow.dismiss();
        }
        Context ctx = getContext();
        PoseHistoryEntry entry = poseHistoryEntries.get(index);
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(8, 8, 8, 8);
        panel.setBackground(new ColorDrawable(0xF0181822));

        TextView title = new TextView(ctx);
        title.setText("跳转到 #" + entry.step + " ?");
        title.setTextColor(0xFFE8E8E8);
        title.setPadding(0, 0, 0, 4);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button jump = createStyledButton(ctx);
        jump.setText("跳转");
        jump.setOnClickListener(v -> jumpToPoseHistory(index));
        buttons.addView(jump, new LinearLayout.LayoutParams(0, -2, 1f));
        Button cancel = createStyledButton(ctx);
        cancel.setText("取消");
        cancel.setOnClickListener(v -> {
            if (poseHistoryConfirmWindow != null) {
                poseHistoryConfirmWindow.dismiss();
            }
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        poseHistoryConfirmWindow = new PopupWindow(panel, 220, 96, true);
        poseHistoryConfirmWindow.setBackgroundDrawable(new ColorDrawable(0xF0181822));
        poseHistoryConfirmWindow.setOutsideTouchable(true);
        poseHistoryConfirmWindow.setFocusable(true);
        poseHistoryConfirmWindow.setOnDismissListener(() -> poseHistoryConfirmWindow = null);
        poseHistoryConfirmWindow.showAsDropDown(anchor, 16, -anchor.getHeight());
    }

    private void jumpToPoseHistory(int index) {
        if (index < 0 || index >= poseHistoryEntries.size()) {
            return;
        }
        PoseHistoryEntry entry = poseHistoryEntries.get(index);
        applyPoseHistorySnapshot(entry.snapshot, index, "已跳转到历史步骤 #" + entry.step);
        if (poseHistoryConfirmWindow != null) {
            poseHistoryConfirmWindow.dismiss();
        }
    }

    private void applyPoseHistorySnapshot(BodyPosePresetStore.EditorStateData snapshot, int selectedIndex, String message) {
        if (snapshot == null) {
            return;
        }
        suppressPoseHistory = true;
        try {
            applyEditorState(snapshot);
            selectedEditorItemIndex = -1;
            saveCurrentPageState();
            poseHistoryBaseline = captureEditorState("history");
            selectedPoseHistoryIndex = selectedIndex;
        } finally {
            suppressPoseHistory = false;
        }
        refreshEditorStructure();
        rebuildPoseHistoryList();
        showActionbar(message);
    }

    private void dismissPoseHistoryWindows() {
        if (poseHistoryConfirmWindow != null && poseHistoryConfirmWindow.isShowing()) {
            poseHistoryConfirmWindow.dismiss();
        }
        poseHistoryConfirmWindow = null;
        if (poseHistoryWindow != null && poseHistoryWindow.isShowing()) {
            poseHistoryWindow.dismiss();
        }
        poseHistoryWindow = null;
        poseHistoryListContainer = null;
    }

    private List<String> describeEditorStateChanges(BodyPosePresetStore.EditorStateData before,
                                                    BodyPosePresetStore.EditorStateData after) {
        List<String> changes = new ArrayList<>();
        if (before == null || after == null) {
            return changes;
        }
        addFloatHistoryChange(changes, "模型 X", before.modelOffsetX, after.modelOffsetX);
        addFloatHistoryChange(changes, "模型 Y", before.modelOffsetY, after.modelOffsetY);
        addFloatHistoryChange(changes, "模型 Z", before.modelOffsetZ, after.modelOffsetZ);
        addFloatHistoryChange(changes, "模型 Pitch", before.modelPitch, after.modelPitch);
        addFloatHistoryChange(changes, "模型 Yaw", before.modelYaw, after.modelYaw);
        addFloatHistoryChange(changes, "模型 Roll", before.modelRoll, after.modelRoll);
        addFloatHistoryChange(changes, "整体大小", before.wholeBodyScale, after.wholeBodyScale);
        describePoseMapChanges(changes, "静态", before.partPoses, after.partPoses);
        describePoseMapChanges(changes, "骨骼", before.skeletalPoses, after.skeletalPoses);
        describePoseMapChanges(changes, "真骨骼", before.trueSkeletalPoses, after.trueSkeletalPoses);
        describeEditorItemChanges(changes, before.editorItems, after.editorItems);
        return changes;
    }

    private void describePoseMapChanges(List<String> changes, String group,
                                        Map<String, BodyPosePresetStore.PoseData> before,
                                        Map<String, BodyPosePresetStore.PoseData> after) {
        Set<String> keys = new TreeSet<>();
        if (before != null) keys.addAll(before.keySet());
        if (after != null) keys.addAll(after.keySet());
        for (String key : keys) {
            BodyPosePresetStore.PoseData left = before != null ? before.get(key) : null;
            BodyPosePresetStore.PoseData right = after != null ? after.get(key) : null;
            if (left == null) left = new BodyPosePresetStore.PoseData();
            if (right == null) right = new BodyPosePresetStore.PoseData();
            String prefix = group + "/" + getPartButtonLabel(key) + " ";
            addFloatHistoryChange(changes, prefix + "Pitch", left.pitch, right.pitch);
            addFloatHistoryChange(changes, prefix + "Yaw", left.yaw, right.yaw);
            addFloatHistoryChange(changes, prefix + "Roll", left.roll, right.roll);
            addFloatHistoryChange(changes, prefix + "BendP", left.bendPitch, right.bendPitch);
            addFloatHistoryChange(changes, prefix + "BendY", left.bendYaw, right.bendYaw);
            addFloatHistoryChange(changes, prefix + "BendR", left.bendRoll, right.bendRoll);
            addFloatHistoryChange(changes, prefix + "X", left.offsetX, right.offsetX);
            addFloatHistoryChange(changes, prefix + "Y", left.offsetY, right.offsetY);
            addFloatHistoryChange(changes, prefix + "Z", left.offsetZ, right.offsetZ);
            addFloatHistoryChange(changes, prefix + "大小", left.scale, right.scale);
            if (left.visible != right.visible) {
                changes.add(prefix + "显示 " + (left.visible ? "开" : "关") + "->" + (right.visible ? "开" : "关"));
            }
        }
    }

    private void describeEditorItemChanges(List<String> changes,
                                           List<BodyPosePresetStore.EditorItemData> before,
                                           List<BodyPosePresetStore.EditorItemData> after) {
        int leftSize = before != null ? before.size() : 0;
        int rightSize = after != null ? after.size() : 0;
        if (leftSize != rightSize) {
            changes.add("物品数量 " + leftSize + "->" + rightSize);
        }
        int count = Math.min(leftSize, rightSize);
        for (int i = 0; i < count; i++) {
            BodyPosePresetStore.EditorItemData left = before.get(i);
            BodyPosePresetStore.EditorItemData right = after.get(i);
            String prefix = "物品" + (i + 1) + " ";
            if (!Objects.equals(left.itemId, right.itemId)) {
                changes.add(prefix + "类型");
            }
            addFloatHistoryChange(changes, prefix + "X", left.offsetX, right.offsetX);
            addFloatHistoryChange(changes, prefix + "Y", left.offsetY, right.offsetY);
            addFloatHistoryChange(changes, prefix + "Z", left.offsetZ, right.offsetZ);
            addFloatHistoryChange(changes, prefix + "Pitch", left.pitch, right.pitch);
            addFloatHistoryChange(changes, prefix + "Yaw", left.yaw, right.yaw);
            addFloatHistoryChange(changes, prefix + "Roll", left.roll, right.roll);
            if (!Objects.equals(left.displayMode, right.displayMode)) {
                changes.add(prefix + "显示 " + left.displayMode + "->" + right.displayMode);
            }
        }
    }

    private static void addFloatHistoryChange(List<String> changes, String label, float before, float after) {
        if (Math.abs(before - after) <= 0.0001F) {
            return;
        }
        changes.add(label + " " + formatOffset(before) + "->" + formatOffset(after));
    }

    private static String summarizeHistoryChanges(List<String> changes) {
        if (changes.isEmpty()) {
            return "";
        }
        int visible = Math.min(3, changes.size());
        String summary = String.join(", ", changes.subList(0, visible));
        if (changes.size() > visible) {
            summary += " +" + (changes.size() - visible);
        }
        return summary;
    }

    private void switchEditorPage(int index) {
        if (index < 0 || index >= presetStore.pages.size() || index == presetStore.activePageIndex) {
            return;
        }
        saveCurrentPageState();
        presetStore.activePageIndex = index;
        applyActivePageState();
        selectedEditorItemIndex = -1;
        BodyPosePresetStore.save(presetStore);
        refreshEditorStructure();
        resetPoseHistoryBaseline();
    }

    private void addEditorPage() {
        if (presetStore.pages.size() >= BodyPosePresetStore.MAX_PAGES) {
            showActionbar("最多只能创建6个模型界面");
            return;
        }
        saveCurrentPageState();
        int number = presetStore.pages.size() + 1;
        String name = "界面 " + number;
        BodyPosePresetStore.EditorStateData state = captureEditorState(name);
        presetStore.pages.add(new BodyPosePresetStore.PageData(BodyPosePresetStore.newId(), name, state));
        presetStore.activePageIndex = presetStore.pages.size() - 1;
        BodyPosePresetStore.save(presetStore);
        refreshPresetUi();
        resetPoseHistoryBaseline();
        beginRenameActivePage();
    }

    private void deleteEditorPage(int index) {
        if (index < 0 || index >= presetStore.pages.size()) {
            return;
        }
        int oldActive = presetStore.activePageIndex;
        presetStore.pages.remove(index);
        if (presetStore.pages.isEmpty()) {
            presetStore.pages.add(new BodyPosePresetStore.PageData(
                    BodyPosePresetStore.newId(), "界面 1", captureEditorState("界面 1")));
            presetStore.activePageIndex = 0;
        } else if (index < oldActive) {
            presetStore.activePageIndex = oldActive - 1;
        } else if (index == oldActive) {
            presetStore.activePageIndex = Math.min(index, presetStore.pages.size() - 1);
        } else {
            presetStore.activePageIndex = oldActive;
        }
        presetStore.activePageIndex = Math.max(0, Math.min(presetStore.activePageIndex, presetStore.pages.size() - 1));
        applyActivePageState();
        selectedEditorItemIndex = -1;
        BodyPosePresetStore.save(presetStore);
        refreshEditorStructure();
        resetPoseHistoryBaseline();
    }

    private void saveCurrentAsPreset() {
        saveCurrentPageState();
        String selectedId = presetStore.selectedPresetId;
        BodyPosePresetStore.PresetData preset = findPreset(selectedId);
        if (preset == null) {
            createPresetFromCurrent();
            return;
        }
        preset.state = captureEditorState(preset.name);
        preset.state.name = preset.name;
        BodyPosePresetStore.save(presetStore);
        rebuildPresetList();
        showActionbar("姿势预设已保存");
    }

    private void createPresetFromCurrent() {
        BodyPosePresetStore.PageData page = getActivePage();
        String name = page.name == null || page.name.isBlank()
                ? DEFAULT_PRESET_NAME + " " + (presetStore.presets.size() + 1)
                : page.name + " 预设";
        BodyPosePresetStore.PresetData preset = new BodyPosePresetStore.PresetData(
                BodyPosePresetStore.newId(), name, captureEditorState(name));
        presetStore.presets.add(preset);
        presetStore.selectedPresetId = preset.id;
        BodyPosePresetStore.save(presetStore);
        rebuildPresetList();
        beginRenamePreset(preset.id);
    }

    private void applyPresetToCurrentPage(String presetId) {
        BodyPosePresetStore.PresetData preset = findPreset(presetId);
        if (preset == null || preset.state == null) {
            return;
        }
        applyEditorState(preset.state);
        presetStore.selectedPresetId = preset.id;
        BodyPosePresetStore.PageData page = getActivePage();
        page.state = captureEditorState(page.name);
        BodyPosePresetStore.save(presetStore);
        selectedEditorItemIndex = -1;
        refreshEditorStructure();
        resetPoseHistoryBaseline();
    }

    private void deletePreset(String presetId) {
        presetStore.presets.removeIf(preset -> Objects.equals(preset.id, presetId));
        if (Objects.equals(presetStore.selectedPresetId, presetId)) {
            presetStore.selectedPresetId = "";
        }
        BodyPosePresetStore.save(presetStore);
        rebuildPresetList();
    }

    private BodyPosePresetStore.PresetData findPreset(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            return null;
        }
        for (BodyPosePresetStore.PresetData preset : presetStore.presets) {
            if (Objects.equals(preset.id, presetId)) {
                return preset;
            }
        }
        return null;
    }

    private void beginRenameActivePage() {
        beginRenamePage(presetStore.activePageIndex);
    }

    private void beginRenameSelectedTarget() {
        if (findPreset(presetStore.selectedPresetId) != null) {
            beginRenamePreset(presetStore.selectedPresetId);
            return;
        }
        beginRenameActivePage();
    }

    private void beginRenamePage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= presetStore.pages.size()) {
            return;
        }
        BodyPosePresetStore.PageData page = presetStore.pages.get(pageIndex);
        renameTarget = RenameTarget.PAGE;
        renameTargetId = page.id;
        showRenameControls(page.name);
    }

    private void beginRenamePreset(String presetId) {
        BodyPosePresetStore.PresetData preset = findPreset(presetId);
        if (preset == null) {
            return;
        }
        renameTarget = RenameTarget.PRESET;
        renameTargetId = preset.id;
        showRenameControls(preset.name);
    }

    private void showRenameControls(String currentName) {
        if (renameContainer == null || renameField == null) {
            return;
        }
        renameField.setText(currentName == null ? "" : currentName);
        renameContainer.setVisibility(View.VISIBLE);
        renameField.requestFocus();
    }

    private void confirmRename() {
        String name = renameField != null && renameField.getText() != null
                ? renameField.getText().toString().trim() : "";
        if (name.isBlank()) {
            showActionbar("名称不能为空");
            return;
        }
        if (renameTarget == RenameTarget.PAGE) {
            for (BodyPosePresetStore.PageData page : presetStore.pages) {
                if (Objects.equals(page.id, renameTargetId)) {
                    page.name = name;
                    if (page.state != null) {
                        page.state.name = name;
                    }
                    break;
                }
            }
        } else if (renameTarget == RenameTarget.PRESET) {
            BodyPosePresetStore.PresetData preset = findPreset(renameTargetId);
            if (preset != null) {
                preset.name = name;
                if (preset.state != null) {
                    preset.state.name = name;
                }
            }
        }
        BodyPosePresetStore.save(presetStore);
        cancelRename();
        refreshPresetUi();
    }

    private void cancelRename() {
        renameTarget = RenameTarget.NONE;
        renameTargetId = "";
        if (renameContainer != null) {
            renameContainer.setVisibility(View.GONE);
        }
    }

    private static void showActionbar(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(message), true);
        }
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

            Button b = createStyledButton(ctx);
            b.setText((selected ? "> " : "  ") + name);
            b.setTextColor(selected ? 0xFFFFDD66 : 0xFFE8E8E8);
            int bgColor = selected ? 0x334466AA : 0x22000000;
            styleButton(b, bgColor);
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
            Button button = createStyledButton(ctx);
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

    private Button createStyledButton(Context ctx) {
        Button button = new Button(ctx);
        styleButton(button);
        installEditorKeyHandler(button);
        return button;
    }

    private void installEditorKeyHandler(View view) {
        view.setOnKeyListener((v, keyCode, event) -> handleEditorKey(keyCode, event));
    }

    private void styleButton(Button button) {
        styleButton(button, BUTTON_FILL_COLOR);
    }

    private void styleButton(Button button, int fillColor) {
        button.setBackground(createButtonBackground(fillColor));
        button.setPadding(6, 2, 6, 2);
        button.setMinHeight(22);
        button.setSoundEffectsEnabled(true);
        button.setOnTouchListener((view, event) -> {
            applyButtonFeedback(view, event);
            return false;
        });
    }

    private static StateListDrawable createButtonBackground(int fillColor) {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{-R.attr.state_enabled},
                createButtonShape(BUTTON_DISABLED_FILL_COLOR, BUTTON_DISABLED_BORDER_COLOR));
        background.addState(new int[]{R.attr.state_pressed},
                createButtonShape(BUTTON_PRESSED_FILL_COLOR, BUTTON_PRESSED_BORDER_COLOR));
        background.addState(new int[0],
                createButtonShape(fillColor, BUTTON_BORDER_COLOR));
        return background;
    }

    private static ShapeDrawable createButtonShape(int fillColor, int borderColor) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setShape(ShapeDrawable.RECTANGLE);
        shape.setColor(fillColor);
        shape.setStroke(1, borderColor);
        shape.setCornerRadius(BUTTON_CORNER_RADIUS);
        return shape;
    }

    private static ShapeDrawable createPanelShape(int fillColor, int borderColor) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setShape(ShapeDrawable.RECTANGLE);
        shape.setColor(fillColor);
        shape.setStroke(1, borderColor);
        shape.setCornerRadius(3.0F);
        return shape;
    }

    private static LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 4);
        return params;
    }

    private static void stylePanelContainer(LinearLayout panel) {
        panel.setBackground(createPanelShape(PANEL_BACKGROUND_COLOR, PANEL_BORDER_COLOR));
        panel.setPadding(8, 8, 8, 8);
    }

    private void addControlGroupLabel(LinearLayout parent, Context ctx, String text) {
        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(11);
        label.setTextColor(OUTLINER_GROUP_TEXT_COLOR);
        label.setPadding(2, 7, 0, 2);
        parent.addView(label, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addPanelDivider(LinearLayout parent, Context ctx) {
        TextView divider = new TextView(ctx);
        divider.setText("");
        divider.setBackground(new ColorDrawable(0xFF303742));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 1);
        params.setMargins(0, 6, 0, 4);
        parent.addView(divider, params);
    }

    private static void styleNumericRow(LinearLayout row) {
        row.setPadding(2, 1, 2, 1);
        row.setBackground(createPanelShape(NUMERIC_ROW_FILL_COLOR, 0x223A4350));
    }

    private static void styleNumericLabel(TextView labelView, int width) {
        labelView.setTextColor(0xFFB6C0CC);
        labelView.setTextSize(11);
        labelView.setWidth(width);
        labelView.setGravity(Gravity.CENTER_VERTICAL);
    }

    private static void styleNumericField(EditText valueField) {
        valueField.setTextColor(0xFFE8EDF4);
        valueField.setTextSize(12);
        valueField.setPadding(4, 1, 4, 1);
        valueField.setBackground(createPanelShape(NUMERIC_FIELD_FILL_COLOR, NUMERIC_FIELD_BORDER_COLOR));
    }

    private static void applyButtonFeedback(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS -> setButtonPressedFeedback(view, true);
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_BUTTON_RELEASE -> setButtonPressedFeedback(view, false);
            default -> {
            }
        }
    }

    private static String getPoseModeLabel() {
        return switch (poseEditMode) {
            case STATIC_PART -> "Static-普通模型";
            case SKELETAL -> "Skeletal-可弯曲模型";
            case TRUE_SKELETAL -> "TrueSkeletal-真骨骼";
        };
    }

    private static String getPoseModeConfigId() {
        return switch (poseEditMode) {
            case STATIC_PART -> BodyPoseDefaultsConfig.MODE_STATIC_PART;
            case SKELETAL -> BodyPoseDefaultsConfig.MODE_SKELETAL;
            case TRUE_SKELETAL -> BodyPoseDefaultsConfig.MODE_TRUE_SKELETAL;
        };
    }

    private static PoseEditMode poseEditModeFromConfig(String value) {
        return switch (BodyPoseDefaultsConfig.normalizePoseMode(value)) {
            case BodyPoseDefaultsConfig.MODE_STATIC_PART -> PoseEditMode.STATIC_PART;
            case BodyPoseDefaultsConfig.MODE_SKELETAL -> PoseEditMode.SKELETAL;
            default -> PoseEditMode.TRUE_SKELETAL;
        };
    }

    private static PoseEditMode nextPoseEditMode() {
        return switch (poseEditMode) {
            case STATIC_PART -> PoseEditMode.SKELETAL;
            case SKELETAL -> PoseEditMode.TRUE_SKELETAL;
            case TRUE_SKELETAL -> PoseEditMode.STATIC_PART;
        };
    }

    private static void setButtonPressedFeedback(View view, boolean pressed) {
        if (!view.isEnabled()) {
            pressed = false;
        }
        view.setAlpha(pressed ? BUTTON_PRESSED_ALPHA : BUTTON_NORMAL_ALPHA);
        view.setScaleX(pressed ? BUTTON_PRESSED_SCALE : BUTTON_NORMAL_SCALE);
        view.setScaleY(pressed ? BUTTON_PRESSED_SCALE : BUTTON_NORMAL_SCALE);
    }

    private void addLeftActionControls(LinearLayout panel, Context ctx) {
        addSectionLabel(panel, ctx, "操作");

        resetPoseButton = createStyledButton(ctx);
        panel.addView(resetPoseButton, new LinearLayout.LayoutParams(-1, -2));

        resetAllPoseButton = createStyledButton(ctx);
        panel.addView(resetAllPoseButton, new LinearLayout.LayoutParams(-1, -2));

        resetTransformButton = createStyledButton(ctx);
        panel.addView(resetTransformButton, new LinearLayout.LayoutParams(-1, -2));

        placeButton = createStyledButton(ctx);
        panel.addView(placeButton, new LinearLayout.LayoutParams(-1, -2));

        placeBackpackButton = createStyledButton(ctx);
        panel.addView(placeBackpackButton, new LinearLayout.LayoutParams(-1, -2));

        runCommandButton = createStyledButton(ctx);
        panel.addView(runCommandButton, new LinearLayout.LayoutParams(-1, -2));

        applySkeletalButton = createStyledButton(ctx);
        panel.addView(applySkeletalButton, new LinearLayout.LayoutParams(-1, -2));

        poseHistoryButton = createStyledButton(ctx);
        panel.addView(poseHistoryButton, new LinearLayout.LayoutParams(-1, -2));

        installLeftActionHandlers();
    }

    private void installLeftActionHandlers() {
        if (resetPoseButton != null) {
            resetPoseButton.setOnClickListener(v -> {
                resetSelectedPose();
                refreshNumericValueBindings();
                invalidatePreview();
                recordPoseHistoryStep("重置当前");
            });
        }
        if (resetAllPoseButton != null) {
            resetAllPoseButton.setOnClickListener(v -> {
                resetAllPartPoses();
                refreshNumericValueBindings();
                invalidatePreview();
                recordPoseHistoryStep("重置全部");
            });
        }
        if (resetTransformButton != null) {
            resetTransformButton.setOnClickListener(v -> resetActiveTransform());
        }
        if (placeButton != null) {
            placeButton.setOnClickListener(v -> {
                if (reeditTargetEntityId >= 0) {
                    updatePlacedBodyPose();
                } else {
                    placePosedBody(false);
                }
            });
        }
        if (placeBackpackButton != null) {
            placeBackpackButton.setOnClickListener(v -> placePosedBody(true));
        }
        if (runCommandButton != null) {
            runCommandButton.setOnClickListener(v -> runGiveCommand());
        }
        if (applySkeletalButton != null) {
            applySkeletalButton.setOnClickListener(v -> applySkeletalPose());
        }
        if (poseHistoryButton != null) {
            poseHistoryButton.setOnClickListener(v -> openPoseHistoryWindow());
            poseHistoryButton.setOnTouchListener((view, event) -> {
                applyButtonFeedback(view, event);
                int action = event.getActionMasked();
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE)
                        && event.getActionButton() != MotionEvent.BUTTON_SECONDARY) {
                    openPoseHistoryWindow();
                    return true;
                }
                return action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS;
            });
        }
    }

    private View createCenterPanel(Context ctx) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(4, 0, 4, 0);
        panel.setBackground(new ColorDrawable(ROOT_BACKGROUND_COLOR));

        // 信息栏
        TextView infoBar = new TextView(ctx);
        infoBar.setId(View.generateViewId());
        infoBar.setText("Viewport");
        infoBar.setTextColor(0xFFCAD3DE);
        infoBar.setTextSize(12);
        infoBar.setPadding(6, 5, 0, 3);
        panel.addView(infoBar, blockParams());

        pageTabsContainer = new LinearLayout(ctx);
        pageTabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        pageTabsContainer.setPadding(2, 2, 2, 2);
        pageTabsContainer.setBackground(createPanelShape(PANEL_SECTION_FILL_COLOR, PANEL_BORDER_COLOR));
        panel.addView(pageTabsContainer, blockParams());
        rebuildPageTabs();

        // 3D 预览 (MinecraftSurfaceView)
        surfaceView = new MinecraftSurfaceView(ctx);
        surfaceView.setRenderer(new PreviewRenderer());
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.setOnKeyListener((view, keyCode, event) -> handleEditorKey(keyCode, event));
        surfaceView.setOnTouchListener(new PreviewTouchListener());
        surfaceView.setOnGenericMotionListener(this::handlePreviewGenericMotion);
        panel.addView(surfaceView, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 预览控制按钮栏
        LinearLayout ctrlBar = new LinearLayout(ctx);
        ctrlBar.setOrientation(LinearLayout.HORIZONTAL);
        ctrlBar.setPadding(2, 2, 2, 2);
        ctrlBar.setBackground(createPanelShape(PANEL_SECTION_FILL_COLOR, PANEL_BORDER_COLOR));

        showWholeButton = createStyledButton(ctx);
        ctrlBar.addView(showWholeButton, new LinearLayout.LayoutParams(0, -2, 1f));

        coordToggleButton = createStyledButton(ctx);
        ctrlBar.addView(coordToggleButton, new LinearLayout.LayoutParams(0, -2, 1f));

        coordMovableButton = createStyledButton(ctx);
        ctrlBar.addView(coordMovableButton, new LinearLayout.LayoutParams(0, -2, 1f));

        worldPreviewToggleButton = createStyledButton(ctx);
        ctrlBar.addView(worldPreviewToggleButton, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(ctrlBar, blockParams());

        installPreviewControlHandlers();

        // 关闭按钮
        Button doneBtn = createStyledButton(ctx);
        doneBtn.setText("关闭-一般直接按esc");
        doneBtn.setOnClickListener(v -> closeEditorScreen());
        panel.addView(doneBtn, blockParams());

        refreshButtonLabels();
        return panel;
    }

    private void installPreviewControlHandlers() {
        if (showWholeButton != null) {
            showWholeButton.setOnClickListener(v -> {
                showWholePreview = !showWholePreview;
                refreshButtonLabels();
            });
        }
        if (coordToggleButton != null) {
            coordToggleButton.setOnClickListener(v -> {
                showCoordinateAxes = !showCoordinateAxes;
                refreshButtonLabels();
            });
        }
        if (coordMovableButton != null) {
            coordMovableButton.setOnClickListener(v -> {
                coordinateAxesMovable = !coordinateAxesMovable;
                refreshButtonLabels();
            });
        }
        if (worldPreviewToggleButton != null) {
            worldPreviewToggleButton.setOnClickListener(v -> toggleWorldPreviewMode());
        }
    }

    // ═══════════════════════════════════════════════════════
    //  右栏 — 部位 / 姿势控制
    // ═══════════════════════════════════════════════════════

    private View createRightPanel(Context ctx) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        stylePanelContainer(panel);
        panel.setBackground(createPanelShape(PANEL_BACKGROUND_COLOR, PANEL_BORDER_COLOR));

        // ── 模型类型 ──
        addSectionLabel(panel, ctx, "模型");
        LinearLayout modelTypeRow = new LinearLayout(ctx);
        modelTypeRow.setOrientation(LinearLayout.HORIZONTAL);
        modelTypeButton = createStyledButton(ctx);
        modelTypeRow.addView(modelTypeButton, new LinearLayout.LayoutParams(0, -2, 2f));
        setDefaultModelButton = createStyledButton(ctx);
        modelTypeRow.addView(setDefaultModelButton, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(modelTypeRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout poseModeRow = new LinearLayout(ctx);
        poseModeRow.setOrientation(LinearLayout.HORIZONTAL);
        poseModeButton = createStyledButton(ctx);
        poseModeRow.addView(poseModeButton, new LinearLayout.LayoutParams(0, -2, 2f));
        setDefaultPoseModeButton = createStyledButton(ctx);
        poseModeRow.addView(setDefaultPoseModeButton, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(poseModeRow, new LinearLayout.LayoutParams(-1, -2));

        installModelModeHandlers();

        addPanelDivider(panel, ctx);
        ScrollView inspectorScroll = new ScrollView(ctx);
        inspectorScroll.setBackground(createPanelShape(0x33181C22, PANEL_BORDER_COLOR));
        LinearLayout inspectorPanel = new LinearLayout(ctx);
        inspectorPanel.setOrientation(LinearLayout.VERTICAL);
        inspectorPanel.setPadding(2, 2, 2, 2);
        addSectionLabel(panel, ctx, "Inspector");
        poseControlsContainer = new LinearLayout(ctx);
        poseControlsContainer.setOrientation(LinearLayout.VERTICAL);
        poseControlsContainer.setPadding(2, 2, 2, 2);
        poseControlsContainer.setBackground(createPanelShape(0x33181C22, PANEL_BORDER_COLOR));
        inspectorPanel.addView(poseControlsContainer, blockParams());
        rebuildPoseControls();

        addSectionLabel(inspectorPanel, ctx, "Transform");
        addTransformControls(inspectorPanel, ctx);
        inspectorScroll.addView(inspectorPanel, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(inspectorScroll, new LinearLayout.LayoutParams(-1, 0, 1.15f));

        addPanelDivider(panel, ctx);
        addSectionLabel(panel, ctx, "Outliner");
        ScrollView outlinerScroll = new ScrollView(ctx);
        outlinerScroll.setBackground(createPanelShape(0x44181C22, PANEL_BORDER_COLOR));
        partButtonsContainer = new LinearLayout(ctx);
        partButtonsContainer.setOrientation(LinearLayout.VERTICAL);
        partButtonsContainer.setPadding(2, 2, 2, 2);
        partButtonsContainer.setBackground(createPanelShape(0x44181C22, PANEL_BORDER_COLOR));
        outlinerScroll.addView(partButtonsContainer, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(outlinerScroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        rebuildPartButtons();

        // 模式提示
        if (panel.getChildCount() > 0) {
            TextView modeHint = new TextView(ctx);
            panel.addView(modeHint, new LinearLayout.LayoutParams(-1, -2));
        }

        refreshButtonLabels();
        refreshNumericValueBindings();
        return panel;
    }

    private void installModelModeHandlers() {
        if (modelTypeButton != null) {
            modelTypeButton.setOnClickListener(v -> {
                slimModel = !slimModel;
                refreshButtonLabels();
            });
        }
        if (setDefaultModelButton != null) {
            setDefaultModelButton.setOnClickListener(v -> saveDefaultSlimModel());
        }
        if (poseModeButton != null) {
            poseModeButton.setOnClickListener(v -> {
                poseEditMode = nextPoseEditMode();
                ensureValidSelectedPartForMode();
                clearGizmoInteractionState();
                rebuildPartButtons();
                rebuildPoseControls();
                refreshButtonLabels();
                refreshNumericValueBindings();
                invalidatePreview();
                recordPoseHistoryStep("模式切换");
            });
        }
        if (setDefaultPoseModeButton != null) {
            setDefaultPoseModeButton.setOnClickListener(v -> saveDefaultPoseMode());
        }
    }

    private void addSectionLabel(LinearLayout parent, Context ctx, String text) {
        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(12);
        label.setTextColor(0xFFD7DDE6);
        label.setPadding(2, 8, 0, 3);
        parent.addView(label, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addTransformControls(LinearLayout parent, Context ctx) {
        transformValueBindings.clear();
        addControlGroupLabel(parent, ctx, "Position");
        parent.addView(createActivePositionVectorRow(ctx));
        addControlGroupLabel(parent, ctx, "Rotation");
        parent.addView(createActiveRotationVectorRow(ctx));
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
        styleNumericRow(row);

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        styleNumericLabel(labelView, 62);
        row.addView(labelView, new LinearLayout.LayoutParams(-2, -2));

        NumericValueBinding binding = new NumericValueBinding(getter, setter, min, max, wrap);
        installNumericFieldScroll(row, binding, step);

        Button minusBtn = createStyledButton(ctx);
        minusBtn.setText("-");
        installRepeatingTransformButton(minusBtn, () -> binding.add(-step));
        row.addView(minusBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        EditText valueField = new EditText(ctx);
        styleNumericField(valueField);
        installEditorKeyHandler(valueField);
        binding.attach(valueField);
        installNumericFieldScroll(valueField, binding, step);
        row.addView(valueField, new LinearLayout.LayoutParams(0, -2, 1.25f));
        bindings.add(binding);

        Button plusBtn = createStyledButton(ctx);
        plusBtn.setText("+");
        installRepeatingTransformButton(plusBtn, () -> binding.add(step));
        row.addView(plusBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private LinearLayout createCompactNumericRow(Context ctx,
                                                 List<NumericValueBinding> bindings,
                                                 NumericAxisSpec... specs) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        styleNumericRow(row);
        for (NumericAxisSpec spec : specs) {
            addCompactNumericCell(row, ctx, bindings, spec);
        }
        return row;
    }

    private void addCompactNumericCell(LinearLayout row, Context ctx,
                                       List<NumericValueBinding> bindings,
                                       NumericAxisSpec spec) {
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(3, 1, 3, 2);
        cell.setBackground(createPanelShape(0x441A2028, 0x333A4350));

        TextView marker = new TextView(ctx);
        marker.setText("◢");
        marker.setTextColor(spec.color);
        marker.setTextSize(8);
        marker.setPadding(0, 0, 0, 0);
        cell.addView(marker, new LinearLayout.LayoutParams(-1, -2));

        NumericValueBinding binding = new NumericValueBinding(spec.getter, spec.setter, spec.min, spec.max, spec.wrap);
        installNumericFieldScroll(cell, binding, spec.step);

        EditText valueField = new EditText(ctx);
        styleNumericField(valueField);
        installEditorKeyHandler(valueField);
        binding.attach(valueField);
        installNumericFieldScroll(valueField, binding, spec.step);
        cell.addView(valueField, new LinearLayout.LayoutParams(-1, -2));
        bindings.add(binding);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(1, 0, 1, 0);
        row.addView(cell, params);
    }

    private LinearLayout createActivePositionVectorRow(Context ctx) {
        return createCompactNumericRow(ctx, transformValueBindings,
                activeOffsetSpec(MoveAxis.X, PARAM_X_COLOR),
                activeOffsetSpec(MoveAxis.Y, PARAM_Y_COLOR),
                activeOffsetSpec(MoveAxis.Z, PARAM_Z_COLOR));
    }

    private LinearLayout createActiveRotationVectorRow(Context ctx) {
        return createCompactNumericRow(ctx, transformValueBindings,
                activeRotationSpec(Axis.PITCH, PARAM_X_COLOR),
                activeRotationSpec(Axis.YAW, PARAM_Y_COLOR),
                activeRotationSpec(Axis.ROLL, PARAM_Z_COLOR));
    }

    private LinearLayout createPartPositionVectorRow(Context ctx, String part) {
        return createCompactNumericRow(ctx, poseValueBindings,
                partPositionSpec(part, MoveAxis.X, PARAM_X_COLOR),
                partPositionSpec(part, MoveAxis.Y, PARAM_Y_COLOR),
                partPositionSpec(part, MoveAxis.Z, PARAM_Z_COLOR));
    }

    private LinearLayout createPartRotationVectorRow(Context ctx, String part) {
        return createCompactNumericRow(ctx, poseValueBindings,
                partRotationSpec(part, Axis.PITCH, PARAM_X_COLOR),
                partRotationSpec(part, Axis.YAW, PARAM_Y_COLOR),
                partRotationSpec(part, Axis.ROLL, PARAM_Z_COLOR));
    }

    private LinearLayout createPartBendVectorRow(Context ctx, String part) {
        return createCompactNumericRow(ctx, poseValueBindings,
                partBendSpec(part, Axis.PITCH, PARAM_X_COLOR),
                partBendSpec(part, Axis.YAW, PARAM_Y_COLOR),
                partBendSpec(part, Axis.ROLL, PARAM_Z_COLOR));
    }

    private LinearLayout createPartScaleCompactRow(Context ctx, String part) {
        return createCompactNumericRow(ctx, poseValueBindings,
                new NumericAxisSpec(
                        () -> getPartScale(part),
                        value -> setPartScale(part, value),
                        MODEL_SCALE_STEP, MODEL_SCALE_MIN, MODEL_SCALE_MAX, false, PARAM_SCALE_COLOR));
    }

    private LinearLayout createWholeScaleCompactRow(Context ctx) {
        return createCompactNumericRow(ctx, poseValueBindings,
                new NumericAxisSpec(
                        () -> wholeBodyScale,
                        BodyPoseEditorFragment::setWholeBodyScale,
                        MODEL_SCALE_STEP, MODEL_SCALE_MIN, MODEL_SCALE_MAX, false, PARAM_SCALE_COLOR));
    }

    private NumericAxisSpec activeOffsetSpec(MoveAxis axis, int color) {
        return new NumericAxisSpec(
                () -> getActiveOffset(axis),
                value -> setActiveOffset(axis, value),
                TRANSFORM_OFFSET_STEP, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX, false, color);
    }

    private NumericAxisSpec activeRotationSpec(Axis axis, int color) {
        return new NumericAxisSpec(
                () -> getActiveRotation(axis),
                value -> setActiveRotation(axis, value),
                ROTATION_STEP_DEGREES, -180.0F, 180.0F, axis != Axis.PITCH, color);
    }

    private NumericAxisSpec partPositionSpec(String part, MoveAxis axis, int color) {
        boolean trueSkeletal = poseEditMode == PoseEditMode.TRUE_SKELETAL;
        float min = trueSkeletal ? TRUE_SKELETAL_OFFSET_MIN : MODEL_OFFSET_MIN;
        float max = trueSkeletal ? TRUE_SKELETAL_OFFSET_MAX : MODEL_OFFSET_MAX;
        return new NumericAxisSpec(
                () -> getPartPoseOffset(part, axis),
                value -> setPartPoseOffset(part, axis, value),
                TRUE_SKELETAL_OFFSET_STEP, min, max, false, color);
    }

    private NumericAxisSpec partRotationSpec(String part, Axis axis, int color) {
        return new NumericAxisSpec(
                () -> getPartPoseValue(part, axis),
                value -> setPartPoseValue(part, axis, value),
                ROTATION_STEP_DEGREES, -180.0F, 180.0F, axis != Axis.PITCH, color);
    }

    private NumericAxisSpec partBendSpec(String part, Axis axis, int color) {
        return new NumericAxisSpec(
                () -> getPartBendValue(part, axis),
                value -> setPartBendValue(part, axis, value),
                ROTATION_STEP_DEGREES, -180.0F, 180.0F, axis != Axis.PITCH, color);
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
                    if (!view.isEnabled()) {
                        return false;
                    }
                    view.setPressed(true);
                    setButtonPressedFeedback(view, true);
                    view.playSoundEffect(SoundEffectConstants.CLICK);
                    startRepeatingTransform(view, action);
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_BUTTON_RELEASE -> {
                    view.setPressed(false);
                    setButtonPressedFeedback(view, false);
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
        MinecraftSurfaceView view = surfaceView;
        if (view == null || previewInvalidationQueued) {
            return;
        }
        previewInvalidationQueued = true;
        boolean posted = postToUiThread(() -> {
            previewInvalidationQueued = false;
            if (surfaceView != view) {
                return;
            }
            view.invalidate();
        });
        if (!posted) {
            previewInvalidationQueued = false;
        }
    }

    private void invalidateView(View view) {
        if (view == null) {
            return;
        }
        postToUiThread(view::invalidate);
    }

    private boolean postToUiThread(Runnable action) {
        if (action == null) {
            return false;
        }
        if (Core.isOnUiThread()) {
            action.run();
            return true;
        }
        if (Core.getUiHandlerAsync() != null) {
            Core.postOnUiThread(action);
            return true;
        }
        View view = rootView != null ? rootView : surfaceView;
        if (view != null && view.post(action)) {
            return true;
        }
        if (surfaceView != null && surfaceView != view && surfaceView.post(action)) {
            return true;
        }
        return false;
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
        partButtonValues.clear();

        if (poseEditMode == PoseEditMode.TRUE_SKELETAL) {
            rebuildTrueSkeletalPartButtons(ctx);
            return;
        }

        for (String part : getSelectableParts()) {
            addPartButton(partButtonsContainer, ctx, part, part, -1.0F);
        }
    }

    private void rebuildTrueSkeletalPartButtons(Context ctx) {
        addControlGroupLabel(partButtonsContainer, ctx, "Scene");
        addPartButton(partButtonsContainer, ctx, "all", getPartButtonLabel("all"), -1.0F);
        addTrueSkeletalPartGroup(ctx, "Head", "head", "eye", "eye_left", "eye_right",
                "eyelid", "eyelid_left", "eyelid_right",
                "eye_highlight", "eye_highlight_left", "eye_highlight_right");
        addTrueSkeletalPartGroup(ctx, "Torso", "torso", "torso_on", "torso_midium", "torso_low");
        addTrueSkeletalPartGroup(ctx, "Left Arm", "left_arm", "left_arm_on", "left_arm_low");
        addTrueSkeletalPartGroup(ctx, "Right Arm", "right_arm", "right_arm_on", "right_arm_low");
        addTrueSkeletalPartGroup(ctx, "Left Leg", "left_leg", "left_leg_on", "left_leg_low");
        addTrueSkeletalPartGroup(ctx, "Right Leg", "right_leg", "right_leg_on", "right_leg_low");
    }

    private void addTrueSkeletalPartGroup(Context ctx, String groupLabel, String mainPart, String... subParts) {
        addControlGroupLabel(partButtonsContainer, ctx, groupLabel);
        addPartButton(partButtonsContainer, ctx, mainPart, getPartButtonLabel(mainPart), -1.0F);
        for (String subPart : subParts) {
            addPartButton(partButtonsContainer, ctx, subPart, getPartButtonLabel(subPart), -1.0F);
        }
    }

    private void addPartButton(LinearLayout parent, Context ctx, String partId, String label, float weight) {
        Button button = createStyledButton(ctx);
        boolean selected = partId.equals(selectedPart);
        stylePartButton(button, partId, selected);
        button.setOnClickListener(v -> {
            selectedPart = partId;
            clearGizmoInteractionState();
            rebuildPoseControls();
            refreshButtonLabels();
            refreshNumericValueBindings();
            invalidatePreview();
        });
        if (weight > 0.0F) {
            parent.addView(button, new LinearLayout.LayoutParams(0, -2, weight));
        } else {
            parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        }
        partButtons.add(button);
        partButtonValues.put(button, partId);
    }

    private void stylePartButton(Button button, String partId, boolean selected) {
        styleButton(button, selected ? OUTLINER_ROW_SELECTED_FILL_COLOR : OUTLINER_ROW_FILL_COLOR);
        button.setText(formatPartButtonText(partId, selected));
        button.setTextColor(selected ? OUTLINER_SELECTED_TEXT_COLOR : OUTLINER_TEXT_COLOR);
    }

    private static String formatPartButtonText(String partId, boolean selected) {
        int indent = getPartButtonIndent(partId);
        String marker = selected ? "> " : "  ";
        return " ".repeat(Math.max(0, indent * 2)) + marker + getPartButtonLabel(partId);
    }

    private static int getPartButtonIndent(String partId) {
        if (poseEditMode != PoseEditMode.TRUE_SKELETAL) {
            return 0;
        }
        return switch (partId) {
            case "all", "head", "torso", "left_arm", "right_arm", "left_leg", "right_leg" -> 0;
            case "eye", "eyelid", "eye_highlight",
                    "torso_on", "torso_midium", "torso_low",
                    "left_arm_on", "left_arm_low", "right_arm_on", "right_arm_low",
                    "left_leg_on", "left_leg_low", "right_leg_on", "right_leg_low" -> 1;
            default -> 2;
        };
    }

    private static String getPartButtonLabel(String part) {
        if (poseEditMode == PoseEditMode.TRUE_SKELETAL) {
            return switch (part) {
                case "all" -> "Model";
                case "head" -> "头";
                case "torso" -> "躯干";
                case "left_arm" -> "左臂";
                case "right_arm" -> "右臂";
                case "left_leg" -> "左腿";
                case "right_leg" -> "右腿";
                case "eye" -> "眼睛";
                case "eye_left" -> "眼睛 R";
                case "eye_right" -> "眼睛 L";
                case "eyelid" -> "眼皮";
                case "eyelid_left" -> "眼皮 R";
                case "eyelid_right" -> "眼皮 L";
                case "eye_highlight" -> "高光";
                case "eye_highlight_left" -> "高光 R";
                case "eye_highlight_right" -> "高光 L";
                case "torso_low" -> "躯干 下";
                case "torso_midium" -> "躯干 中";
                case "torso_on" -> "躯干 上";
                case "left_arm_on" -> "左臂 上";
                case "left_arm_low" -> "左臂 下";
                case "right_arm_on" -> "右臂 上";
                case "right_arm_low" -> "右臂 下";
                case "left_leg_on" -> "左腿 上";
                case "left_leg_low" -> "左腿 下";
                case "right_leg_on" -> "右腿 上";
                case "right_leg_low" -> "右腿 下";
                default -> getTrueSkeletalPartShortLabel(part);
            };
        }
        return part;
    }

    private static String getTrueSkeletalPartShortLabel(String part) {
        return switch (part) {
            case "torso_low" -> "下";
            case "torso_midium" -> "中";
            case "torso_on" -> "上";
            case "left_arm_on", "right_arm_on", "left_leg_on", "right_leg_on" -> "上";
            case "left_arm_low", "right_arm_low", "left_leg_low", "right_leg_low" -> "下";
            case "eye_left", "eyelid_left", "eye_highlight_left" -> "R";
            case "eye_right", "eyelid_right", "eye_highlight_right" -> "L";
            case "eye_highlight" -> "HL";
            default -> part;
        };
    }

    private static boolean isEyeTarget(String part) {
        return "eye".equals(part) || "eye_left".equals(part) || "eye_right".equals(part);
    }

    private static boolean isEyelidTarget(String part) {
        return "eyelid".equals(part) || "eyelid_left".equals(part) || "eyelid_right".equals(part);
    }

    private static boolean isEyeHighlightTarget(String part) {
        return "eye_highlight".equals(part) || "eye_highlight_left".equals(part) || "eye_highlight_right".equals(part);
    }

    private static String matchingEyelidTarget(String part) {
        return switch (part) {
            case "eye_left" -> "eyelid_left";
            case "eye_right" -> "eyelid_right";
            default -> "eyelid";
        };
    }

    private static String matchingHighlightTarget(String part) {
        return switch (part) {
            case "eye_left" -> "eye_highlight_left";
            case "eye_right" -> "eye_highlight_right";
            default -> "eye_highlight";
        };
    }

    private void addSelectedInspectorHeader(Context ctx) {
        TextView selectedLabel = new TextView(ctx);
        selectedLabel.setText("Selected: " + getPartButtonLabel(selectedPart));
        selectedLabel.setTextColor(OUTLINER_SELECTED_TEXT_COLOR);
        selectedLabel.setTextSize(12);
        selectedLabel.setPadding(4, 3, 4, 4);
        selectedLabel.setBackground(createPanelShape(0x66323A45, 0x663A4350));
        poseControlsContainer.addView(selectedLabel, blockParams());
    }

    private void rebuildPoseControls() {
        if (poseControlsContainer == null || getContext() == null) return;
        ensureValidSelectedPartForMode();
        Context ctx = getContext();
        poseControlsContainer.removeAllViews();
        poseButtons.clear();
        poseValueBindings.clear();

        boolean editingAll = selectedPart.equals("all");

        addSelectedInspectorHeader(ctx);
        addControlGroupLabel(poseControlsContainer, ctx, "Global");
        poseControlsContainer.addView(createWholeScaleCompactRow(ctx));

        if (!editingAll && poseEditMode == PoseEditMode.TRUE_SKELETAL
                && !isEyeTarget(selectedPart) && !isEyelidTarget(selectedPart) && !isEyeHighlightTarget(selectedPart)) {
            addControlGroupLabel(poseControlsContainer, ctx, "Position");
            poseControlsContainer.addView(createPartPositionVectorRow(ctx, selectedPart));
        }

        if (editingAll) {
            addAllBendControls(ctx);
            return;
        }

        if (poseEditMode == PoseEditMode.TRUE_SKELETAL) {
            if (isEyeTarget(selectedPart)) {
                addEyeControls(ctx);
                return;
            }
            if (isEyelidTarget(selectedPart)) {
                addEyelidControls(ctx);
                return;
            }
            if (isEyeHighlightTarget(selectedPart)) {
                addEyeHighlightControls(ctx);
                return;
            }
        }

        addControlGroupLabel(poseControlsContainer, ctx, "Scale");
        poseControlsContainer.addView(createPartScaleCompactRow(ctx, selectedPart));

        addControlGroupLabel(poseControlsContainer, ctx, "Rotation");
        poseControlsContainer.addView(createPartRotationVectorRow(ctx, selectedPart));

        if (hasSelectedBendControls()) {
            addControlGroupLabel(poseControlsContainer, ctx, "Bend");
            poseControlsContainer.addView(createPartBendVectorRow(ctx, selectedPart));
        }
    }

    private void addEyeControls(Context ctx) {
        String eyePart = selectedPart;

        addControlGroupLabel(poseControlsContainer, ctx, "Eye");
        poseControlsContainer.addView(createVisibilityRow(ctx, "Visible", eyePart));
        poseControlsContainer.addView(createScaleRow(ctx, "Size", eyePart));
        addControlGroupLabel(poseControlsContainer, ctx, "Rotation");
        addPoseRows(ctx, "Eye", eyePart);
        addControlGroupLabel(poseControlsContainer, ctx, "Position");
        addPositionRows(ctx, "Eye", eyePart);
    }

    private void addEyelidControls(Context ctx) {
        addControlGroupLabel(poseControlsContainer, ctx, "Eyelid");
        poseControlsContainer.addView(createVisibilityRow(ctx, "Visible", selectedPart));
        addControlGroupLabel(poseControlsContainer, ctx, "Rotation");
        addPoseRows(ctx, "Lid", selectedPart);
        addControlGroupLabel(poseControlsContainer, ctx, "Position");
        addPositionRows(ctx, "Lid", selectedPart);
    }

    private void addEyeHighlightControls(Context ctx) {
        addControlGroupLabel(poseControlsContainer, ctx, "Highlight");
        poseControlsContainer.addView(createVisibilityRow(ctx, "Visible", selectedPart));
        poseControlsContainer.addView(createScaleRow(ctx, "Size", selectedPart));
        addControlGroupLabel(poseControlsContainer, ctx, "Rotation");
        addPoseRows(ctx, "HL", selectedPart);
        addControlGroupLabel(poseControlsContainer, ctx, "Position");
        addPositionRows(ctx, "HL", selectedPart);
    }

    private void addPoseRows(Context ctx, String prefix, String part) {
        poseControlsContainer.addView(createPartRotationVectorRow(ctx, part));
    }

    private void addPositionRows(Context ctx, String prefix, String part) {
        poseControlsContainer.addView(createPartPositionVectorRow(ctx, part));
    }

    private LinearLayout createScaleRow(Context ctx, String label, String part) {
        return createPartScaleCompactRow(ctx, part);
    }

    private LinearLayout createVisibilityRow(Context ctx, String label, String part) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        styleNumericRow(row);

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        styleNumericLabel(labelView, 88);
        row.addView(labelView, new LinearLayout.LayoutParams(-2, -2));

        Button toggle = createStyledButton(ctx);
        toggle.setText(isPartVisible(part) ? "Show" : "Hide");
        toggle.setOnClickListener(v -> {
            setPartVisible(part, !isPartVisible(part));
            rebuildPoseControls();
            refreshButtonLabels();
            invalidatePreview();
            recordPoseHistoryStep("可见性");
        });
        row.addView(toggle, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private LinearLayout createPoseRow(Context ctx, String label, Axis axis) {
        return createPoseRow(ctx, label, selectedPart, axis);
    }

    private LinearLayout createPoseRow(Context ctx, String label, String part, Axis axis) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        styleNumericRow(row);

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        styleNumericLabel(labelView, 64);
        row.addView(labelView, new LinearLayout.LayoutParams(-2, -2));

        NumericValueBinding binding = new NumericValueBinding(
                () -> getPartPoseValue(part, axis),
                value -> setPartPoseValue(part, axis, value),
                -180.0F, 180.0F, axis != Axis.PITCH);
        installNumericFieldScroll(row, binding, ROTATION_STEP_DEGREES);

        Button minusBtn = createStyledButton(ctx);
        minusBtn.setText("-");
        installRepeatingTransformButton(minusBtn, () -> binding.add(-ROTATION_STEP_DEGREES));
        row.addView(minusBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        poseButtons.add(minusBtn);

        EditText valueField = new EditText(ctx);
        styleNumericField(valueField);
        installEditorKeyHandler(valueField);
        binding.attach(valueField);
        installNumericFieldScroll(valueField, binding, ROTATION_STEP_DEGREES);
        row.addView(valueField, new LinearLayout.LayoutParams(0, -2, 1.25f));
        poseValueBindings.add(binding);

        Button plusBtn = createStyledButton(ctx);
        plusBtn.setText("+");
        installRepeatingTransformButton(plusBtn, () -> binding.add(ROTATION_STEP_DEGREES));
        row.addView(plusBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        poseButtons.add(plusBtn);

        return row;
    }

    private LinearLayout createBendRow(Context ctx, String label, Axis axis) {
        return createBendRow(ctx, label, selectedPart, axis);
    }

    private LinearLayout createBendRow(Context ctx, String label, String part, Axis axis) {
        return createPartBendRow(ctx, label, part, axis, true);
    }

    private LinearLayout createPositionRow(Context ctx, String label, MoveAxis axis) {
        return createPositionRow(ctx, label, selectedPart, axis);
    }

    private LinearLayout createPositionRow(Context ctx, String label, String part, MoveAxis axis) {
        boolean trueSkeletal = poseEditMode == PoseEditMode.TRUE_SKELETAL;
        float min = trueSkeletal ? TRUE_SKELETAL_OFFSET_MIN : MODEL_OFFSET_MIN;
        float max = trueSkeletal ? TRUE_SKELETAL_OFFSET_MAX : MODEL_OFFSET_MAX;
        return createNumericRow(ctx, label,
                () -> getPartPoseOffset(part, axis),
                value -> setPartPoseOffset(part, axis, value),
                TRUE_SKELETAL_OFFSET_STEP, min, max, false,
                poseValueBindings);
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
        poseControlsContainer.addView(createPartBendVectorRow(ctx, part));
    }

    private LinearLayout createPartBendRow(Context ctx, String label, String part, Axis axis, boolean trackPoseButtons) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        styleNumericRow(row);

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        styleNumericLabel(labelView, 88);
        row.addView(labelView, new LinearLayout.LayoutParams(-2, -2));

        NumericValueBinding binding = new NumericValueBinding(
                () -> getPartBendValue(part, axis),
                value -> setPartBendValue(part, axis, value),
                -180.0F, 180.0F, axis != Axis.PITCH);
        installNumericFieldScroll(row, binding, ROTATION_STEP_DEGREES);

        Button minusBtn = createStyledButton(ctx);
        minusBtn.setText("-");
        installRepeatingTransformButton(minusBtn, () -> binding.add(-ROTATION_STEP_DEGREES));
        row.addView(minusBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        if (trackPoseButtons) poseButtons.add(minusBtn);

        EditText valueField = new EditText(ctx);
        styleNumericField(valueField);
        installEditorKeyHandler(valueField);
        binding.attach(valueField);
        installNumericFieldScroll(valueField, binding, ROTATION_STEP_DEGREES);
        row.addView(valueField, new LinearLayout.LayoutParams(0, -2, 1.25f));
        poseValueBindings.add(binding);

        Button plusBtn = createStyledButton(ctx);
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

            Button b = createStyledButton(ctx);
            b.setText(stack.getName().getString());
            b.setOnClickListener(v -> {
                EditorItemModel model = new EditorItemModel(stack.copyWithCount(1));
                EDITOR_ITEMS.add(model);
                selectedEditorItemIndex = EDITOR_ITEMS.size() - 1;
                clearGizmoInteractionState();
                itemListOpen = false;
                itemListContainer.setVisibility(View.GONE);
                refreshButtonLabels();
                refreshNumericValueBindings();
                recordPoseHistoryStep("添加物品");
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

            renderBlockbenchViewportBackground(dCtx, pWidth, pHeight);

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
            updateGizmoModeToggleKey();
            updateGizmoCenter();

            // 更新 hover 状态
            float localMouseX = mouseX - previewScreenLeft;
            float localMouseY = mouseY - previewScreenTop;
            boolean rotationMode = isRotationGizmoMode();
            if (rotationMode) {
                hoveredMoveAxis = MoveAxis.NONE;
                hoveredRotationAxis = draggingRotationAxis != RotationAxis.NONE
                        ? draggingRotationAxis
                        : findRotationRing(localMouseX, localMouseY);
            } else {
                hoveredRotationAxis = RotationAxis.NONE;
                hoveredMoveAxis = draggingMoveAxis != MoveAxis.NONE
                        ? draggingMoveAxis
                        : findMoveAxis(localMouseX, localMouseY);
            }

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

    private void renderBlockbenchViewportBackground(DrawContext context, int width, int height) {
        context.fill(previewAreaLeft, previewAreaTop, previewAreaRight, previewAreaBottom, VIEWPORT_BACKGROUND_COLOR);
        context.fill(previewAreaLeft, previewAreaTop, previewAreaRight, previewAreaTop + 28, VIEWPORT_HEADER_COLOR);

        int gridLeft = previewAreaLeft + 1;
        int gridTop = previewAreaTop + 28;
        int gridRight = previewAreaRight - 1;
        int gridBottom = previewAreaBottom - 1;
        for (int x = gridLeft; x < gridRight; x += 24) {
            context.fill(x, gridTop, x + 1, gridBottom, VIEWPORT_GRID_DARK_COLOR);
        }
        for (int y = gridTop; y < gridBottom; y += 24) {
            context.fill(gridLeft, y, gridRight, y + 1, VIEWPORT_GRID_DARK_COLOR);
        }
        for (int x = gridLeft + 12; x < gridRight; x += 24) {
            context.fill(x, gridTop, x + 1, gridBottom, VIEWPORT_GRID_LIGHT_COLOR);
        }
        for (int y = gridTop + 12; y < gridBottom; y += 24) {
            context.fill(gridLeft, y, gridRight, y + 1, VIEWPORT_GRID_LIGHT_COLOR);
        }

        int frameLeft = previewAreaLeft + 5;
        int frameTop = previewAreaTop + 32;
        int frameWidth = Math.max(1, width - 10);
        int frameHeight = Math.max(1, height - 38);
        context.drawBorder(frameLeft, frameTop, frameWidth, frameHeight, 0x553A4350);
        context.drawBorder(previewAreaLeft, previewAreaTop, width, height, VIEWPORT_BORDER_COLOR);
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

    private boolean isRotationGizmoMode() {
        if (draggingRotationAxis != RotationAxis.NONE) return true;
        if (draggingMoveAxis != MoveAxis.NONE) return false;
        return rotationGizmoMode;
    }

    private void toggleGizmoMode(boolean invalidate) {
        rotationGizmoMode = !rotationGizmoMode;
        hoveredMoveAxis = MoveAxis.NONE;
        hoveredRotationAxis = RotationAxis.NONE;
        clearActiveGizmoDrag();
        draggingPreview = false;
        draggingRightPreview = false;
        previewTransformDirty = false;
        activePreviewButton = 0;
        updateGizmoCenter();
        if (invalidate) {
            invalidatePreview();
        }
    }

    private void updateGizmoModeToggleKey() {
        boolean down = isCtrlDown();
        if (!down) {
            ctrlGizmoToggleKeyDown = false;
            ctrlShortcutUsed = false;
            skipNextCtrlPollToggle = false;
        }
    }

    private static boolean isCtrlDown() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private boolean usesSelectedPartGizmoTarget() {
        return !hasSelectedItemModel()
                && !selectedPart.equals("all");
    }

    private void updateGizmoCenter() {
        currentGizmoCenter = resolveGizmoCenter();
    }

    private void beginMoveGizmoDrag(MoveAxis axis, float mouseX, float mouseY) {
        if (axis == MoveAxis.NONE) {
            return;
        }
        captureGizmoDragStart(mouseX, mouseY);
        dragStartMoveOffset = usesSelectedPartGizmoTarget()
                ? getSelectedPoseOffset(axis)
                : getActiveOffset(axis);
        captureMoveAxisScreenVector(axis);
        draggingMoveAxis = axis;
        draggingRotationAxis = RotationAxis.NONE;
        draggingPreview = false;
        draggingRightPreview = false;
    }

    private void beginRotationGizmoDrag(RotationAxis axis, float mouseX, float mouseY) {
        if (axis == RotationAxis.NONE) {
            return;
        }
        captureGizmoDragStart(mouseX, mouseY);
        draggingRotationAxis = axis;
        draggingMoveAxis = MoveAxis.NONE;
        draggingPreview = false;
        draggingRightPreview = false;
    }

    private void captureGizmoDragStart(float mouseX, float mouseY) {
        updateGizmoCenter();
        dragStartGizmoCenter.set(currentGizmoCenter);
        dragStartMouseX = mouseX;
        dragStartMouseY = mouseY;
    }

    private void clearActiveGizmoDrag() {
        draggingMoveAxis = MoveAxis.NONE;
        draggingRotationAxis = RotationAxis.NONE;
        dragStartMoveOffset = 0.0F;
        dragStartAxisScreenX = 0.0F;
        dragStartAxisScreenY = 0.0F;
        dragStartAxisScreenLength = 0.0F;
    }

    private void captureMoveAxisScreenVector(MoveAxis axis) {
        ScreenPoint center = projectGizmoPoint(dragStartGizmoCenter.x, dragStartGizmoCenter.y, dragStartGizmoCenter.z);
        ScreenPoint end = switch (axis) {
            case X -> projectGizmoPoint(dragStartGizmoCenter.x + MOVE_AXIS_LENGTH,
                    dragStartGizmoCenter.y, dragStartGizmoCenter.z);
            case Y -> projectGizmoPoint(dragStartGizmoCenter.x,
                    dragStartGizmoCenter.y + MOVE_AXIS_LENGTH, dragStartGizmoCenter.z);
            case Z -> projectGizmoPoint(dragStartGizmoCenter.x,
                    dragStartGizmoCenter.y, dragStartGizmoCenter.z + MOVE_AXIS_LENGTH);
            default -> center;
        };
        dragStartAxisScreenX = end.x - center.x;
        dragStartAxisScreenY = end.y - center.y;
        dragStartAxisScreenLength = (float) Math.sqrt(
                dragStartAxisScreenX * dragStartAxisScreenX + dragStartAxisScreenY * dragStartAxisScreenY);
    }

    private Vector3f resolveGizmoCenter() {
        if (usesSelectedPartGizmoTarget()) {
            if (poseEditMode == PoseEditMode.TRUE_SKELETAL) {
                Vector3f center = BodyPoseSkeletalPreviewRenderer.getPoseTargetRenderPosition(selectedPart,
                        getWorldSkeletalBoneRotations(), getWorldSkeletalBoneOffsets(), getWorldSkeletalBoneScales());
                if (center != null) {
                    return center;
                }
                center = BodyPoseSkeletalPreviewRenderer.getPoseTargetBaseRenderPosition(selectedPart);
                if (center != null) {
                    return center.add(
                            trueSkeletalPreviewOffset(getSelectedPoseOffset(MoveAxis.X)),
                            trueSkeletalPreviewOffset(getSelectedPoseOffset(MoveAxis.Y)),
                            trueSkeletalPreviewOffset(getSelectedPoseOffset(MoveAxis.Z)));
                }
            } else {
                return getStaticPartGizmoCenter(selectedPart);
            }
        }
        return new Vector3f(getActiveOffsetX(), getActiveOffsetY(), getActiveOffsetZ());
    }

    private Vector3f getStaticPartGizmoCenter(String part) {
        Vector3f center = switch (part) {
            case "head" -> new Vector3f(0.0F, 1.0F, 0.0F);
            case "torso" -> new Vector3f(0.0F, 0.25F, 0.0F);
            case "left_arm" -> new Vector3f(0.375F, 0.25F, 0.0F);
            case "right_arm" -> new Vector3f(-0.375F, 0.25F, 0.0F);
            case "left_leg" -> new Vector3f(0.125F, -0.75F, 0.0F);
            case "right_leg" -> new Vector3f(-0.125F, -0.75F, 0.0F);
            default -> new Vector3f(0.0F, 0.0F, 0.0F);
        };
        PartPose pose = getActivePoseMap().computeIfAbsent(part, ignored -> new PartPose());
        center.add(pose.offsetX, pose.offsetY, pose.offsetZ);
        return center;
    }

    private void clearGizmoInteractionState() {
        hoveredMoveAxis = MoveAxis.NONE;
        hoveredRotationAxis = RotationAxis.NONE;
        clearActiveGizmoDrag();
        draggingPreview = false;
        draggingRightPreview = false;
        previewTransformDirty = false;
        activePreviewButton = 0;
    }

    private void cancelGizmoStep() {
        hoveredMoveAxis = MoveAxis.NONE;
        hoveredRotationAxis = RotationAxis.NONE;
        clearActiveGizmoDrag();
        previewTransformDirty = false;
        invalidatePreview();
    }

    private boolean hasActiveGizmoStep() {
        return draggingMoveAxis != MoveAxis.NONE
                || draggingRotationAxis != RotationAxis.NONE;
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
                    if (handlePreviewItemSelectorClick(x, y)) {
                        return true;
                    }
                    lastX = x;
                    lastY = y;
                    activePreviewButton = getPreviewButton(event, action);
                    updateGizmoCenter();

                    if (activePreviewButton == MotionEvent.BUTTON_SECONDARY) {
                        if (hasActiveGizmoStep()) {
                            cancelGizmoStep();
                            return true;
                        }
                        draggingPreview = false;
                        draggingRightPreview = true;
                        return true;
                    }

                    if (activePreviewButton == MotionEvent.BUTTON_PRIMARY) {
                        if (isRotationGizmoMode()) {
                            RotationAxis rotationAxis = findRotationRing(x, y);
                            if (rotationAxis != RotationAxis.NONE) {
                                beginRotationGizmoDrag(rotationAxis, x, y);
                                return true;
                            }
                        } else {
                            MoveAxis moveAxis = findMoveAxis(x, y);
                            if (moveAxis != MoveAxis.NONE) {
                                beginMoveGizmoDrag(moveAxis, x, y);
                                return true;
                            }
                        }
                        draggingPreview = true;
                        draggingRightPreview = false;
                        clearActiveGizmoDrag();
                        return true;
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    float dX = x - lastX;
                    float dY = y - lastY;
                    boolean primaryDown = activePreviewButton == MotionEvent.BUTTON_PRIMARY
                            || event.isButtonPressed(MotionEvent.BUTTON_PRIMARY);
                    boolean secondaryDown = activePreviewButton == MotionEvent.BUTTON_SECONDARY
                            || event.isButtonPressed(MotionEvent.BUTTON_SECONDARY);

                    if (secondaryDown && hasActiveGizmoStep()) {
                        cancelGizmoStep();
                        return true;
                    }

                    if (primaryDown && draggingRotationAxis != RotationAxis.NONE) {
                        dragGizmoRotation(draggingRotationAxis, x, y, dX, dY);
                    } else if (primaryDown && draggingMoveAxis != MoveAxis.NONE) {
                        dragGizmoOffset(draggingMoveAxis, x, y);
                    } else if (primaryDown && draggingPreview) {
                        previewYaw -= dX * 0.65F;
                        previewPitch = clampPreview(previewPitch - dY * 0.65F, -60.0F, 60.0F);
                    } else if (secondaryDown && draggingRightPreview) {
                        previewPanX += dX;
                        previewPanY += dY;
                    }

                    updateGizmoCenter();
                    if (isRotationGizmoMode()) {
                        hoveredMoveAxis = MoveAxis.NONE;
                        hoveredRotationAxis = draggingRotationAxis != RotationAxis.NONE
                                ? draggingRotationAxis
                                : findRotationRing(x, y);
                    } else {
                        hoveredRotationAxis = RotationAxis.NONE;
                        hoveredMoveAxis = draggingMoveAxis != MoveAxis.NONE
                                ? draggingMoveAxis
                                : findMoveAxis(x, y);
                    }
                    lastX = x;
                    lastY = y;
                    invalidatePreview();
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE,
                        MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_BUTTON_RELEASE -> {
                    boolean changed = previewTransformDirty;
                    boolean movedAxis = draggingMoveAxis != MoveAxis.NONE;
                    boolean rotatedAxis = draggingRotationAxis != RotationAxis.NONE;
                    draggingPreview = false;
                    draggingRightPreview = false;
                    if (draggingMoveAxis != MoveAxis.NONE || draggingRotationAxis != RotationAxis.NONE) {
                        clearActiveGizmoDrag();
                    }
                    if (changed) {
                        saveCurrentPageState();
                        refreshNumericValueBindings();
                        recordPoseHistoryStep(movedAxis ? "移动轴" : rotatedAxis ? "旋转轴" : "调整");
                    }
                    previewTransformDirty = false;
                    activePreviewButton = 0;
                    updateGizmoCenter();
                    if (isRotationGizmoMode()) {
                        hoveredMoveAxis = MoveAxis.NONE;
                        hoveredRotationAxis = findRotationRing(x, y);
                    } else {
                        hoveredRotationAxis = RotationAxis.NONE;
                        hoveredMoveAxis = findMoveAxis(x, y);
                    }
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

        if (!isTrueSkeletalPoseMode()) {
            preparePreviewModel(model);
        }
        float scale = previewScale > 0.0F
                ? previewScale
                : getPreviewBaseScale(previewAreaRight - previewAreaLeft, previewAreaBottom - previewAreaTop) * previewZoom;
        ScreenPoint modelCenter = projectPreviewPoint(modelOffsetX, modelOffsetY, modelOffsetZ);
        float renderScale = scale * modelCenter.scale;
        int modelWidth = Math.max(previewAreaRight - previewAreaLeft, Math.round(renderScale * 5.2F));
        float pitchSin = Math.abs((float) Math.sin(Math.toRadians(previewPitch)));
        int modelHeight = Math.max(previewAreaBottom - previewAreaTop, Math.round(renderScale * 5.8F))
                + Math.round(renderScale * PREVIEW_PITCH_BOUNDS_EXTRA_SCALE * pitchSin);
        int x1 = Math.round(modelCenter.x - modelWidth * 0.5F);
        int x2 = x1 + modelWidth;
        int y2 = Math.round(modelCenter.y + renderScale * PREVIEW_Y_PIVOT);
        int y1 = y2 - modelHeight;
        Identifier texture = getPreviewTexture();

        context.enableScissor(previewAreaLeft, previewAreaTop, previewAreaRight, previewAreaBottom);
        try {
            context.addPlayerSkin(model, texture, renderScale, previewPitch, -previewYaw, PREVIEW_Y_PIVOT,
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
            int color = major ? argb(115, 152, 164, 178) : argb(70, 112, 122, 134);
            drawProjectedLine(context,
                    projectPreviewPoint(-GROUND_GRID_HALF_SIZE, GROUND_GRID_Y, coord),
                    projectPreviewPoint(GROUND_GRID_HALF_SIZE, GROUND_GRID_Y, coord),
                    color);
            drawProjectedLine(context,
                    projectPreviewPoint(coord, GROUND_GRID_Y, -GROUND_GRID_HALF_SIZE),
                    projectPreviewPoint(coord, GROUND_GRID_Y, GROUND_GRID_HALF_SIZE),
                    color);
        }
        renderPreviewSideGrid(context);
        renderPreviewBoundsBox(context);
        drawProjectedLine(context,
                projectPreviewPoint(-GROUND_GRID_HALF_SIZE, GROUND_GRID_Y, 0.0F),
                projectPreviewPoint(GROUND_GRID_HALF_SIZE, GROUND_GRID_Y, 0.0F),
                0xD0E05252);
        drawProjectedLine(context,
                projectPreviewPoint(0.0F, GROUND_GRID_Y, -GROUND_GRID_HALF_SIZE),
                projectPreviewPoint(0.0F, GROUND_GRID_Y, GROUND_GRID_HALF_SIZE),
                0xD05A76FF);
        drawProjectedLine(context,
                projectPreviewPoint(0.0F, GROUND_GRID_Y, 0.0F),
                projectPreviewPoint(0.0F, GROUND_GRID_Y + 6.0F, 0.0F),
                0xD05AD970);
    }

    private void renderPreviewSideGrid(DrawContext context) {
        float side = -GROUND_GRID_HALF_SIZE;
        float height = 8.0F;
        for (int i = 0; i <= GROUND_GRID_SIZE; i += 2) {
            float coord = -GROUND_GRID_HALF_SIZE + i * GROUND_GRID_CELL;
            float y = GROUND_GRID_Y + height * i / (float) GROUND_GRID_SIZE;
            int color = i % 10 == 0 ? argb(75, 128, 138, 150) : argb(38, 92, 102, 114);
            drawProjectedLine(context,
                    projectPreviewPoint(coord, GROUND_GRID_Y, side),
                    projectPreviewPoint(coord, GROUND_GRID_Y + height, side),
                    color);
            drawProjectedLine(context,
                    projectPreviewPoint(side, GROUND_GRID_Y, coord),
                    projectPreviewPoint(side, GROUND_GRID_Y + height, coord),
                    color);
            drawProjectedLine(context,
                    projectPreviewPoint(-GROUND_GRID_HALF_SIZE, y, side),
                    projectPreviewPoint(GROUND_GRID_HALF_SIZE, y, side),
                    color);
            drawProjectedLine(context,
                    projectPreviewPoint(side, y, -GROUND_GRID_HALF_SIZE),
                    projectPreviewPoint(side, y, GROUND_GRID_HALF_SIZE),
                    color);
        }
    }

    private void renderPreviewBoundsBox(DrawContext context) {
        float min = -GROUND_GRID_HALF_SIZE;
        float max = GROUND_GRID_HALF_SIZE;
        float bottom = GROUND_GRID_Y;
        float top = GROUND_GRID_Y + 8.0F;
        int color = argb(125, 126, 139, 155);
        drawPreviewBoxEdge(context, min, bottom, min, max, bottom, min, color);
        drawPreviewBoxEdge(context, max, bottom, min, max, bottom, max, color);
        drawPreviewBoxEdge(context, max, bottom, max, min, bottom, max, color);
        drawPreviewBoxEdge(context, min, bottom, max, min, bottom, min, color);
        drawPreviewBoxEdge(context, min, top, min, max, top, min, color);
        drawPreviewBoxEdge(context, max, top, min, max, top, max, color);
        drawPreviewBoxEdge(context, max, top, max, min, top, max, color);
        drawPreviewBoxEdge(context, min, top, max, min, top, min, color);
        drawPreviewBoxEdge(context, min, bottom, min, min, top, min, color);
        drawPreviewBoxEdge(context, max, bottom, min, max, top, min, color);
        drawPreviewBoxEdge(context, max, bottom, max, max, top, max, color);
        drawPreviewBoxEdge(context, min, bottom, max, min, top, max, color);
    }

    private void drawPreviewBoxEdge(DrawContext context,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    int color) {
        drawProjectedLine(context,
                projectPreviewPoint(x1, y1, z1),
                projectPreviewPoint(x2, y2, z2),
                color);
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
        clearGizmoInteractionState();
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }

        String label = hasSelectedItemModel() ? "Item " + (selectedEditorItemIndex + 1) : "Player";
        if (usesSelectedPartGizmoTarget()) {
            label = selectedPart;
        }
        float readoutOffsetX = usesSelectedPartGizmoTarget() ? getSelectedPoseOffset(MoveAxis.X) : getActiveOffsetX();
        float readoutOffsetY = usesSelectedPartGizmoTarget() ? getSelectedPoseOffset(MoveAxis.Y) : getActiveOffsetY();
        float readoutOffsetZ = usesSelectedPartGizmoTarget() ? getSelectedPoseOffset(MoveAxis.Z) : getActiveOffsetZ();
        float readoutPitch = usesSelectedPartGizmoTarget() ? getSelectedPoseValue(Axis.PITCH) : getActivePitch();
        float readoutYaw = usesSelectedPartGizmoTarget() ? getSelectedPoseValue(Axis.YAW) : getActiveYaw();
        float readoutRoll = usesSelectedPartGizmoTarget() ? getSelectedPoseValue(Axis.ROLL) : getActiveRoll();
        float readoutScale = usesSelectedPartGizmoTarget() ? getPartScale(selectedPart) : wholeBodyScale;
        String line1 = label + "  pos "
                + formatOffset(readoutOffsetX) + " "
                + formatOffset(readoutOffsetY) + " "
                + formatOffset(readoutOffsetZ);
        String line2 = "rot "
                + formatDegrees(readoutPitch) + " "
                + formatDegrees(readoutYaw) + " "
                + formatDegrees(readoutRoll);
        String line3 = "scale " + formatOffset(readoutScale);

        int x = previewAreaLeft + 6;
        int y = Math.max(previewAreaTop + 6, previewAreaBottom - 34);
        int width = Math.min(previewAreaRight - previewAreaLeft - 12, 156);
        context.fill(x - 3, y - 3, x + width, y + 29, 0xA8141820);
        context.drawBorder(x - 3, y - 3, width + 3, 32, 0x664D5B6B);
        drawPreviewReadoutText(context, client, line1, x, y, 0xFFDDE6F3);
        drawPreviewReadoutText(context, client, line2, x, y + 10, 0xFFBFD2FF);
        drawPreviewReadoutText(context, client, line3, x, y + 20, 0xFFC8FACC);
    }

    private static void drawPreviewReadoutText(DrawContext context, MinecraftClient client,
                                               String text, int x, int y, int color) {
        try {
            context.drawText(client.textRenderer, sanitizePreviewText(text), x, y, color, false);
        } catch (RuntimeException ignored) {
        }
    }

    private static String sanitizePreviewText(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            builder.append(c >= 32 && c < 127 ? c : '?');
        }
        return builder.toString();
    }

    private void renderEditorItemPreviews(DrawContext context) {
        context.enableScissor(previewAreaLeft, previewAreaTop, previewAreaRight, previewAreaBottom);
        try {
            for (int i = 0; i < EDITOR_ITEMS.size(); i++) {
                EditorItemModel item = EDITOR_ITEMS.get(i);
                ScreenPoint point = projectPreviewPoint(item.offsetX, item.offsetY, item.offsetZ);
                renderEditorItemPreview(context, item, point);
                int size = getEditorItemPreviewBoundsSize(point);
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
        int boundsSize = getEditorItemPreviewBoundsSize(point);
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
        if (isRotationGizmoMode()) {
            renderRotationRing(context, RotationAxis.PITCH, 0xD0FF4646);
            renderRotationRing(context, RotationAxis.YAW, 0xD046E646);
            renderRotationRing(context, RotationAxis.ROLL, 0xD05A6EFF);
        } else {
            renderMoveAxis(context, MoveAxis.X, 0xF5FF3232);
            renderMoveAxis(context, MoveAxis.Y, 0xF532DC32);
            renderMoveAxis(context, MoveAxis.Z, 0xF53C50FF);
        }

        ScreenPoint center = projectGizmoPoint(currentGizmoCenter.x, currentGizmoCenter.y, currentGizmoCenter.z);
        drawHandle(context, center, 3, 0xFFFFFFFF);
    }

    private void renderMoveAxis(DrawContext context, MoveAxis axis, int color) {
        float ox = currentGizmoCenter.x;
        float oy = currentGizmoCenter.y;
        float oz = currentGizmoCenter.z;
        ScreenPoint center = projectGizmoPoint(ox, oy, oz);
        ScreenPoint end = switch (axis) {
            case X -> projectGizmoPoint(ox + MOVE_AXIS_LENGTH, oy, oz);
            case Y -> projectGizmoPoint(ox, oy + MOVE_AXIS_LENGTH, oz);
            case Z -> projectGizmoPoint(ox, oy, oz + MOVE_AXIS_LENGTH);
            default -> center;
        };

        boolean highlighted = axis == draggingMoveAxis
                || (draggingMoveAxis == MoveAxis.NONE && axis == hoveredMoveAxis);
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

    private ScreenPoint projectGizmoPoint(float x, float y, float z) {
        if (usesSelectedPartGizmoTarget()) {
            return projectSelectedPartPoint(x, y, z);
        }
        return projectModelPoint(x, y, z);
    }

    private ScreenPoint projectSelectedPartPoint(float x, float y, float z) {
        if (poseEditMode == PoseEditMode.TRUE_SKELETAL) {
            return projectTrueSkeletalEditorPoint(x, y, z);
        }
        return projectWholeModelPoint(x, y, z);
    }

    private ScreenPoint projectTrueSkeletalEditorPoint(float x, float y, float z) {
        float scale = wholeBodyScale;
        float scaledX = x * scale;
        float scaledY = -y * scale;
        float scaledZ = z * scale;

        float rollRad = (float) Math.toRadians(modelRoll);
        float rollCos = (float) Math.cos(rollRad);
        float rollSin = (float) Math.sin(rollRad);
        float rollX = scaledX * rollCos - scaledY * rollSin;
        float rollY = scaledX * rollSin + scaledY * rollCos;
        float rollZ = scaledZ;

        float yawRad = (float) Math.toRadians(-modelYaw);
        float yawCos = (float) Math.cos(yawRad);
        float yawSin = (float) Math.sin(yawRad);
        float yawX = rollX * yawCos + rollZ * yawSin;
        float yawZ = rollZ * yawCos - rollX * yawSin;
        float yawY = rollY;

        float pitchRad = (float) Math.toRadians(modelPitch);
        float pitchCos = (float) Math.cos(pitchRad);
        float pitchSin = (float) Math.sin(pitchRad);
        float pitchY = yawY * pitchCos - yawZ * pitchSin;
        float pitchZ = yawZ * pitchCos + yawY * pitchSin;
        float pitchX = yawX;

        return projectPreviewPoint(
                modelOffsetX + pitchX,
                modelOffsetY + pitchY,
                modelOffsetZ + pitchZ);
    }

    private ScreenPoint projectWholeModelPoint(float x, float y, float z) {
        float yawRad = (float) Math.toRadians(-modelYaw);
        float yawCos = (float) Math.cos(yawRad);
        float yawSin = (float) Math.sin(yawRad);
        float yawX = x * yawCos + z * yawSin;
        float yawZ = z * yawCos - x * yawSin;

        float pitchRad = (float) Math.toRadians(modelPitch);
        float pitchCos = (float) Math.cos(pitchRad);
        float pitchSin = (float) Math.sin(pitchRad);
        float pitchY = y * pitchCos - yawZ * pitchSin;
        float pitchZ = yawZ * pitchCos + y * pitchSin;
        float pitchX = yawX;

        float rollRad = (float) Math.toRadians(modelRoll);
        float rollCos = (float) Math.cos(rollRad);
        float rollSin = (float) Math.sin(rollRad);
        float rollX = pitchX * rollCos - pitchY * rollSin;
        float rollY = pitchX * rollSin + pitchY * rollCos;

        return projectPreviewPoint(modelOffsetX + rollX, modelOffsetY + rollY, modelOffsetZ + pitchZ);
    }

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
        float pitchZ = yawZ * pitchCos + y * pitchSin;
        float pitchX = yawX;

        float rollRad = (float) Math.toRadians(previewRoll);
        float rollCos = (float) Math.cos(rollRad);
        float rollSin = (float) Math.sin(rollRad);
        float rollX = pitchX * rollCos - pitchY * rollSin;
        float rollY = pitchX * rollSin + pitchY * rollCos;
        float depth = Math.max(PREVIEW_NEAR_DEPTH, PREVIEW_CAMERA_DISTANCE - pitchZ);
        float perspectiveScale = clampPreview(PREVIEW_CAMERA_DISTANCE / depth,
                PREVIEW_MIN_PERSPECTIVE_SCALE, PREVIEW_MAX_PERSPECTIVE_SCALE);

        return new ScreenPoint(
                previewCenterX + rollX * previewScale * perspectiveScale,
                previewCenterY + rollY * previewScale * perspectiveScale,
                perspectiveScale,
                depth);
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
        float ox = currentGizmoCenter.x;
        float oy = currentGizmoCenter.y;
        float oz = currentGizmoCenter.z;
        ScreenPoint center = projectGizmoPoint(ox, oy, oz);
        double bestDist = MOVE_AXIS_HIT_RADIUS;
        MoveAxis best = MoveAxis.NONE;

        double d = distanceToSegment(px, py, center, projectGizmoPoint(ox + MOVE_AXIS_LENGTH, oy, oz));
        if (d <= bestDist) { bestDist = d; best = MoveAxis.X; }
        d = distanceToSegment(px, py, center, projectGizmoPoint(ox, oy + MOVE_AXIS_LENGTH, oz));
        if (d <= bestDist) { bestDist = d; best = MoveAxis.Y; }
        d = distanceToSegment(px, py, center, projectGizmoPoint(ox, oy, oz + MOVE_AXIS_LENGTH));
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
        float ox = currentGizmoCenter.x;
        float oy = currentGizmoCenter.y;
        float oz = currentGizmoCenter.z;
        return switch (axis) {
            case PITCH -> projectGizmoPoint(ox, oy + cos, oz + sin);
            case YAW -> projectGizmoPoint(ox + cos, oy, oz + sin);
            case ROLL -> projectGizmoPoint(ox + cos, oy + sin, oz);
            default -> projectGizmoPoint(ox, oy, oz);
        };
    }

    private void dragGizmoOffset(MoveAxis axis, double mouseX, double mouseY) {
        if (axis == MoveAxis.NONE) {
            return;
        }
        double axisLength = dragStartAxisScreenLength;
        if (axisLength < 0.001D) {
            return;
        }
        double deltaX = mouseX - dragStartMouseX;
        double deltaY = mouseY - dragStartMouseY;
        float deltaUnits = (float) ((deltaX * dragStartAxisScreenX + deltaY * dragStartAxisScreenY)
                / (axisLength * axisLength) * MOVE_AXIS_LENGTH);
        deltaUnits *= getMoveAxisDragSign(axis);
        if (usesSelectedPartGizmoTarget() && poseEditMode == PoseEditMode.TRUE_SKELETAL) {
            deltaUnits = trueSkeletalModelOffset(deltaUnits);
        }
        float previous = usesSelectedPartGizmoTarget()
                ? getSelectedPoseOffset(axis)
                : getActiveOffset(axis);
        float target = dragStartMoveOffset + deltaUnits;
        if (usesSelectedPartGizmoTarget()) {
            setSelectedPoseOffset(axis, target);
        } else {
            setActiveOffset(axis, target);
        }
        float next = usesSelectedPartGizmoTarget()
                ? getSelectedPoseOffset(axis)
                : getActiveOffset(axis);
        if (Math.abs(next - previous) > 0.0001F) {
            previewTransformDirty = true;
        }
    }

    private void dragGizmoRotation(RotationAxis axis, double mouseX, double mouseY, double deltaX, double deltaY) {
        if (axis == RotationAxis.NONE) {
            return;
        }
        ScreenPoint center = projectGizmoPoint(dragStartGizmoCenter.x, dragStartGizmoCenter.y, dragStartGizmoCenter.z);
        double previousAngle = Math.atan2(mouseY - deltaY - center.y, mouseX - deltaX - center.x);
        double currentAngle = Math.atan2(mouseY - center.y, mouseX - center.x);
        float degrees = -normalizeDegrees((float) Math.toDegrees(currentAngle - previousAngle));
        degrees *= getRotationAxisDragSign(axis);
        if (usesSelectedPartGizmoTarget()) {
            switch (axis) {
                case PITCH -> setSelectedPoseValue(Axis.PITCH, getSelectedPoseValue(Axis.PITCH) - degrees);
                case YAW -> setSelectedPoseValue(Axis.YAW, getSelectedPoseValue(Axis.YAW) + degrees);
                case ROLL -> setSelectedPoseValue(Axis.ROLL, getSelectedPoseValue(Axis.ROLL) - degrees);
                default -> {
                }
            }
        } else {
            switch (axis) {
                case PITCH -> setActiveRotation(Axis.PITCH, getActiveRotation(Axis.PITCH) - degrees);
                case YAW -> setActiveRotation(Axis.YAW, getActiveRotation(Axis.YAW) + degrees);
                case ROLL -> setActiveRotation(Axis.ROLL, getActiveRotation(Axis.ROLL) - degrees);
                default -> {
                }
            }
        }
        previewTransformDirty = true;
    }

    private float getMoveAxisDragSign(MoveAxis axis) {
        if (poseEditMode == PoseEditMode.TRUE_SKELETAL && usesSelectedPartGizmoTarget()) {
            return 1.0F;
        }
        return switch (axis) {
            case Y, Z -> -1.0F;
            default -> 1.0F;
        };
    }

    private static float getRotationAxisDragSign(RotationAxis axis) {
        return switch (axis) {
            case YAW, ROLL -> -1.0F;
            default -> 1.0F;
        };
    }

    private int findPreviewItem(double px, double py) {
        for (int i = EDITOR_ITEMS.size() - 1; i >= 0; i--) {
            EditorItemModel item = EDITOR_ITEMS.get(i);
            ScreenPoint pt = projectPreviewPoint(item.offsetX, item.offsetY, item.offsetZ);
            int halfSize = Math.max(12, getEditorItemPreviewBoundsSize(pt) / 2);
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

    private int getEditorItemPreviewBoundsSize(ScreenPoint point) {
        float perspectiveScale = clampPreview(point.scale,
                PREVIEW_ITEM_MIN_PERSPECTIVE_SCALE, PREVIEW_ITEM_MAX_PERSPECTIVE_SCALE);
        return Math.max(28, Math.round(getEditorItemPreviewBoundsSize() * perspectiveScale));
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
        applyPreviewPoses(model, previewPoses, showAll);
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
        applyPreviewPoses(model, previewPoses, showAll);

        return model;
    }

    private static void applyPreviewPoses(PlayerEntityModel model, Map<String, PartPose> previewPoses, boolean showAll) {
        if (showAll || selectedPart.equals("torso")) {
            applyTorsoPartPose(model, previewPoses.get("torso"));
        } else {
            applyPartPose("torso", model.body, previewPoses.get("torso"));
        }
        applyPartPose("head", model.head, previewPoses.get("head"));
        applyPartPose("left_arm", model.leftArm, previewPoses.get("left_arm"));
        applyPartPose("right_arm", model.rightArm, previewPoses.get("right_arm"));
        applyPartPose("left_leg", model.leftLeg, previewPoses.get("left_leg"));
        applyPartPose("right_leg", model.rightLeg, previewPoses.get("right_leg"));
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

    private static void applyTorsoPartPose(PlayerEntityModel model, PartPose pose) {
        ModelPart blendPart = getBlendPart("torso", model.body);
        if (blendPart != null) {
            blendPart.visible = false;
        }
        if (pose != null) {
            TorsoBendFollower.applyPose(model, pose.pitch, pose.yaw, pose.roll, pose.scale);
            if (poseEditMode == PoseEditMode.SKELETAL && hasBendPose(pose)) {
                if (blendPart != null) {
                    blendPart.visible = true;
                }
                TorsoBendFollower.apply(model, pose.bendPitch, pose.bendYaw, pose.bendRoll);
            }
        }
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
            String part = partButtonValues.get(btn);
            if (part == null) {
                continue;
            }
            boolean isSelected = part.equals(selectedPart);
            stylePartButton(btn, part, isSelected);
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
        }
        if (setDefaultModelButton != null) {
            setDefaultModelButton.setText("设为默认");
        }
        if (poseModeButton != null) {
            poseModeButton.setText("Pose Mode: " + getPoseModeLabel());
        }
        if (setDefaultPoseModeButton != null) {
            setDefaultPoseModeButton.setText("设为默认");
        }
        if (resetPoseButton != null) {
            resetPoseButton.setText("重置当前姿势");
            resetPoseButton.setEnabled(!selectedPart.equals("all"));
        }
        if (resetAllPoseButton != null) {
            resetAllPoseButton.setText("重置全部肢体");
        }
        if (runCommandButton != null) {
            runCommandButton.setText("给予肢体");
        }
        if (placeButton != null) {
            placeButton.setText(reeditTargetEntityId >= 0 ? "更新姿态" : "放置模型");
        }
        if (placeBackpackButton != null) {
            placeBackpackButton.setText("\u653e\u7f6e\u6a21\u578b(\u80cc\u5305)");
            placeBackpackButton.setEnabled(reeditTargetEntityId < 0);
        }
        if (applySkeletalButton != null) {
            applySkeletalButton.setText("应用骨骼姿势");
            applySkeletalButton.setEnabled(poseEditMode == PoseEditMode.SKELETAL);
        }
        if (poseHistoryButton != null) {
            poseHistoryButton.setText("历史记录 (" + poseHistoryEntries.size() + ")");
        }
        if (itemButton != null) {
            itemButton.setText("物品: " + getSelectedItemLabel());
        }
        if (itemDisplayModeButton != null) {
            itemDisplayModeButton.setText("物品显示: " + getActiveItemDisplayMode().label);
        }
        if (placeItemsButton != null) {
            placeItemsButton.setText("放置物品模型(" + EDITOR_ITEMS.size() + ")");
            placeItemsButton.setEnabled(!EDITOR_ITEMS.isEmpty());
        }
        if (clearSelectedItemButton != null) {
            clearSelectedItemButton.setText("清除选中");
            clearSelectedItemButton.setEnabled(hasSelectedItemModel());
        }
        if (clearAllItemsButton != null) {
            clearAllItemsButton.setText("清除全部");
            clearAllItemsButton.setEnabled(!EDITOR_ITEMS.isEmpty());
        }
        if (resetTransformButton != null) {
            resetTransformButton.setText("重置当前偏移");
        }
        if (showWholeButton != null) {
            showWholeButton.setText("整体 " + (showWholePreview ? "开" : "关"));
        }
        if (coordToggleButton != null) {
            coordToggleButton.setText("坐标 " + (showCoordinateAxes ? "开" : "关"));
        }
        if (coordMovableButton != null) {
            coordMovableButton.setText("跟随 " + (coordinateAxesMovable ? "开" : "关"));
        }
        if (worldPreviewToggleButton != null) {
            worldPreviewToggleButton.setText("模式 " + (worldPreviewMode == PreviewMode.FOLLOW_PLAYER ? "预览" : "放置"));
        }
    }

    // ═══════════════════════════════════════════════════════
    //  动作命令 / 网络
    // ═══════════════════════════════════════════════════════

    private void saveDefaultSlimModel() {
        BodyPoseDefaultsConfig.setDefaultSlimModel(slimModel);
        sendDefaultsToServer();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("默认体型已保存: " + (slimModel ? "slim" : "default")), true);
        }
    }

    private void saveDefaultPoseMode() {
        BodyPoseDefaultsConfig.setDefaultPoseMode(getPoseModeConfigId());
        sendDefaultsToServer();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("默认模型已保存: " + getPoseModeLabel()), true);
        }
    }

    private static void sendDefaultsToServer() {
        try {
            if (ClientPlayNetworking.canSend(UpdateBodyPoseDefaultsC2SPacket.ID)) {
                ClientPlayNetworking.send(new UpdateBodyPoseDefaultsC2SPacket(
                        BodyPoseDefaultsConfig.getDefaultSlimModel(),
                        BodyPoseDefaultsConfig.getDefaultPoseMode()));
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {
        }
    }

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

    private void placePosedBody(boolean backpackEnabled) {
        saveCurrentPageState();
        if (poseEditMode == PoseEditMode.TRUE_SKELETAL) {
            placeTrueSkeletalBody(backpackEnabled);
            return;
        }
        boolean skeletalMode = poseEditMode == PoseEditMode.SKELETAL;
        float[] poseValues = skeletalMode ? createSkeletalPoseValueArray() : createStaticPoseValueArray();
        float[] bendValues = skeletalMode ? createSkeletalBendValueArray() : null;
        Vec3d fixedBase = getFixedPlacementBaseForPacket();
        if (fixedBase != null) {
            ClientPlayNetworking.send(new PlacePosedBodyC2SPacket(selectedSkin, slimModel,
                    poseValues, bendValues,
                    selectedSkinSource == SkinSource.PLAYER, selectedPlayerName,
                    modelOffsetX, modelOffsetY, modelOffsetZ,
                    modelPitch, modelYaw, modelRoll, wholeBodyScale,
                    fixedBase.x, fixedBase.y, fixedBase.z, backpackEnabled));
            closeEditorScreen();
            return;
        }
        ClientPlayNetworking.send(new PlacePosedBodyC2SPacket(selectedSkin, slimModel,
                poseValues, bendValues,
                selectedSkinSource == SkinSource.PLAYER, selectedPlayerName,
                modelOffsetX, modelOffsetY, modelOffsetZ,
                modelPitch, modelYaw, modelRoll, wholeBodyScale, backpackEnabled));
        closeEditorScreen();
    }

    private void placeTrueSkeletalBody(boolean backpackEnabled) {
        saveCurrentPageState();
        List<PlaceTrueSkeletalBodyC2SPacket.BonePose> bones = createTrueSkeletalBonePoseList();
        Vec3d fixedBase = getFixedPlacementBaseForPacket();
        if (fixedBase != null) {
            ClientPlayNetworking.send(new PlaceTrueSkeletalBodyC2SPacket(selectedSkin, slimModel,
                    selectedSkinSource == SkinSource.PLAYER, selectedPlayerName, bones,
                    modelOffsetX, modelOffsetY, modelOffsetZ,
                    modelPitch, modelYaw, modelRoll, wholeBodyScale,
                    fixedBase.x, fixedBase.y, fixedBase.z, backpackEnabled));
            closeEditorScreen();
            return;
        }
        ClientPlayNetworking.send(new PlaceTrueSkeletalBodyC2SPacket(selectedSkin, slimModel,
                selectedSkinSource == SkinSource.PLAYER, selectedPlayerName, bones,
                modelOffsetX, modelOffsetY, modelOffsetZ,
                modelPitch, modelYaw, modelRoll, wholeBodyScale, backpackEnabled));
        closeEditorScreen();
    }

    private void applySkeletalPose() {
        ClientPlayNetworking.send(new ApplySkeletalPoseC2SPacket(
                createSkeletalPoseValueArray(),
                createSkeletalBendValueArray()));
    }

    private void updatePlacedBodyPose() {
        if (reeditTargetEntityId < 0) {
            return;
        }
        saveCurrentPageState();
        boolean trueSkeletal = poseEditMode == PoseEditMode.TRUE_SKELETAL;
        float[] poseValues = poseEditMode == PoseEditMode.STATIC_PART
                ? createStaticPoseValueArray()
                : createSkeletalPoseValueArray();
        float[] bendValues = poseEditMode == PoseEditMode.SKELETAL ? createSkeletalBendValueArray() : null;
        List<PlaceTrueSkeletalBodyC2SPacket.BonePose> bones = trueSkeletal ? createTrueSkeletalBonePoseList() : List.of();
        ClientPlayNetworking.send(new UpdatePlacedBodyPoseC2SPacket(reeditTargetEntityId,
                trueSkeletal ? "true_skeletal" : "skeletal",
                poseValues, bendValues, bones,
                modelOffsetX, modelOffsetY, modelOffsetZ,
                modelPitch, modelYaw, modelRoll, wholeBodyScale));
        closeEditorScreen();
    }

    private void placeEditorItems() {
        if (EDITOR_ITEMS.isEmpty()) return;
        saveCurrentPageState();
        List<PlacePoseEditorItemsC2SPacket.ItemPlacement> placements = new ArrayList<>();
        for (EditorItemModel item : EDITOR_ITEMS) {
            Identifier itemId = Registries.ITEM.getId(item.stack.getItem());
            if (itemId != null) {
                placements.add(new PlacePoseEditorItemsC2SPacket.ItemPlacement(
                        itemId, item.offsetX, item.offsetY, item.offsetZ,
                        item.pitch, item.yaw, item.roll, item.displayMode.context));
            }
        }
        Vec3d fixedBase = getFixedPlacementBaseForPacket();
        if (fixedBase != null) {
            ClientPlayNetworking.send(new PlacePoseEditorItemsC2SPacket(placements, fixedBase.x, fixedBase.y, fixedBase.z));
            return;
        }
        ClientPlayNetworking.send(new PlacePoseEditorItemsC2SPacket(placements));
    }

    private static Vec3d getFixedPlacementBaseForPacket() {
        if (worldPreviewMode != PreviewMode.FIXED) {
            return null;
        }
        return new Vec3d(fixedWorldX, fixedWorldY, fixedWorldZ);
    }

    private static void closeEditorScreen() {
        BodyPoseEditorFragment inst = activeInstance;
        if (inst != null) {
            inst.saveCurrentPageState();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(null));
        }
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

    public static boolean isTrueSkeletalPoseMode() {
        return poseEditMode == PoseEditMode.TRUE_SKELETAL;
    }

    public static Map<String, float[]> getWorldSkeletalBoneRotations() {
        if (poseEditMode == PoseEditMode.TRUE_SKELETAL) {
            ensureTrueSkeletalPoses();
            Map<String, float[]> rotations = new HashMap<>();
            for (Map.Entry<String, PartPose> entry : TRUE_SKELETAL_POSES.entrySet()) {
                PartPose pose = entry.getValue();
                putTrueSkeletalPoseRotation(rotations, entry.getKey(), pose);
            }
            return rotations;
        }
        Map<String, float[]> rotations = new HashMap<>();
        Map<String, PartPose> poses = getActivePoseMap();
        putPoseRotation(rotations, "head", poses.get("head"));

        PartPose torso = poses.get("torso");
        putPoseRotation(rotations, "torso_low", torso);
        if (poseEditMode == PoseEditMode.SKELETAL && torso != null) {
            putRotation(rotations, "torso_midium", torso.bendPitch * 0.5F, torso.bendYaw * 0.5F, torso.bendRoll * 0.5F);
            putRotation(rotations, "torso_on", torso.bendPitch * 0.5F, torso.bendYaw * 0.5F, torso.bendRoll * 0.5F);
        }

        putLimbRotation(rotations, "left_arm_on", "left_arm_low", poses.get("left_arm"));
        putLimbRotation(rotations, "right_arm_on", "right_arm_low", poses.get("right_arm"));
        putLimbRotation(rotations, "left_leg_on", "left_leg_low", poses.get("left_leg"));
        putLimbRotation(rotations, "right_leg_on", "right_leg_low", poses.get("right_leg"));
        return rotations;
    }

    public static Map<String, Float> getWorldSkeletalBoneScales() {
        if (poseEditMode != PoseEditMode.TRUE_SKELETAL) {
            return Map.of();
        }
        ensureTrueSkeletalPoses();
        Map<String, Float> scales = new HashMap<>();
        for (Map.Entry<String, PartPose> entry : TRUE_SKELETAL_POSES.entrySet()) {
            putTrueSkeletalPoseScale(scales, entry.getKey(), entry.getValue());
        }
        return scales;
    }

    public static Map<String, float[]> getWorldSkeletalBoneOffsets() {
        if (poseEditMode != PoseEditMode.TRUE_SKELETAL) {
            return Map.of();
        }
        ensureTrueSkeletalPoses();
        Map<String, float[]> offsets = new HashMap<>();
        for (Map.Entry<String, PartPose> entry : TRUE_SKELETAL_POSES.entrySet()) {
            putTrueSkeletalPoseOffset(offsets, entry.getKey(), entry.getValue());
        }
        return offsets;
    }

    public static Set<String> getWorldVisibleSkeletalParts() {
        boolean showAll = showWholePreview || selectedPart.equals("all");
        if (showAll) {
            return Set.of();
        }
        if (poseEditMode == PoseEditMode.TRUE_SKELETAL) {
            return Set.of(BodyPoseSkeletalPreviewRenderer.getPartNameForBone(selectedPart));
        }
        return Set.of(selectedPart);
    }

    public static Set<String> getWorldHiddenSkeletalMeshes() {
        if (poseEditMode != PoseEditMode.TRUE_SKELETAL) {
            return Set.of();
        }
        ensureTrueSkeletalPoses();
        Set<String> hidden = new HashSet<>();
        for (Map.Entry<String, PartPose> entry : TRUE_SKELETAL_POSES.entrySet()) {
            PartPose pose = entry.getValue();
            if (pose != null && !pose.visible) {
                hidden.addAll(BodyPoseSkeletalPreviewRenderer.getHiddenMeshNamesForPoseTarget(entry.getKey()));
            }
        }
        return hidden;
    }

    private static void putLimbRotation(Map<String, float[]> rotations, String upperBone, String lowerBone, PartPose pose) {
        putPoseRotation(rotations, upperBone, pose);
        if (poseEditMode == PoseEditMode.SKELETAL && pose != null) {
            putRotation(rotations, lowerBone, pose.bendPitch, pose.bendYaw, pose.bendRoll);
        }
    }

    private static void putPoseRotation(Map<String, float[]> rotations, String bone, PartPose pose) {
        if (pose != null) {
            putRotation(rotations, bone, pose.pitch, pose.yaw, pose.roll);
        }
    }

    private static void putRotation(Map<String, float[]> rotations, String bone, float pitch, float yaw, float roll) {
        if (pitch != 0.0F || yaw != 0.0F || roll != 0.0F) {
            rotations.put(bone, new float[] { pitch, yaw, roll });
        }
    }

    private static void putTrueSkeletalPoseRotation(Map<String, float[]> rotations, String part, PartPose pose) {
        if (pose == null || (pose.pitch == 0.0F && pose.yaw == 0.0F && pose.roll == 0.0F)) {
            return;
        }
        for (String bone : getTrueSkeletalRotationTargets(part)) {
            addRotation(rotations, bone, pose.pitch, pose.yaw, pose.roll);
        }
    }

    private static void addRotation(Map<String, float[]> rotations, String bone, float pitch, float yaw, float roll) {
        float[] existing = rotations.computeIfAbsent(bone, ignored -> new float[] { 0.0F, 0.0F, 0.0F });
        existing[0] += pitch;
        existing[1] += yaw;
        existing[2] += roll;
    }

    private static void putTrueSkeletalPoseScale(Map<String, Float> scales, String part, PartPose pose) {
        if (pose == null || pose.scale == 1.0F) {
            return;
        }
        for (String bone : getTrueSkeletalScaleTargets(part)) {
            scales.merge(bone, pose.scale, (left, right) -> left * right);
        }
    }

    private static void putTrueSkeletalPoseOffset(Map<String, float[]> offsets, String part, PartPose pose) {
        if (pose == null || (pose.offsetX == 0.0F && pose.offsetY == 0.0F && pose.offsetZ == 0.0F)) {
            return;
        }
        for (String bone : getTrueSkeletalOffsetTargets(part)) {
            addOffset(offsets, bone, pose.offsetX, pose.offsetY, pose.offsetZ);
        }
    }

    private static void addOffset(Map<String, float[]> offsets, String bone, float x, float y, float z) {
        float[] existing = offsets.computeIfAbsent(bone, ignored -> new float[] { 0.0F, 0.0F, 0.0F });
        existing[0] += x;
        existing[1] += y;
        existing[2] += z;
    }

    private static List<String> getTrueSkeletalRotationTargets(String part) {
        return switch (part) {
            case "torso" -> List.of("torso_low");
            case "left_arm" -> List.of("left_arm_on");
            case "right_arm" -> List.of("right_arm_on");
            case "left_leg" -> List.of("left_leg_on");
            case "right_leg" -> List.of("right_leg_on");
            case "eye_highlight" -> List.of("eye_highlight_left", "eye_highlight_right");
            default -> List.of(part);
        };
    }

    private static List<String> getTrueSkeletalScaleTargets(String part) {
        return switch (part) {
            case "torso" -> List.of("torso_low", "torso_midium", "torso_on");
            case "left_arm" -> List.of("left_arm_on", "left_arm_low");
            case "right_arm" -> List.of("right_arm_on", "right_arm_low");
            case "left_leg" -> List.of("left_leg_on", "left_leg_low");
            case "right_leg" -> List.of("right_leg_on", "right_leg_low");
            case "eye_highlight" -> List.of("eye_highlight_left", "eye_highlight_right");
            default -> List.of(part);
        };
    }

    private static List<String> getTrueSkeletalOffsetTargets(String part) {
        return switch (part) {
            case "head" -> List.of("head");
            case "torso" -> List.of("torso_low");
            case "left_arm" -> List.of("left_arm_on");
            case "right_arm" -> List.of("right_arm_on");
            case "left_leg" -> List.of("left_leg_on");
            case "right_leg" -> List.of("right_leg_on");
            case "eye_highlight" -> List.of("eye_highlight_left", "eye_highlight_right");
            default -> List.of(part);
        };
    }

    public static String getStaticHighlightedMoveAxis() {
        BodyPoseEditorFragment inst = activeInstance;
        if (inst == null) return "";
        MoveAxis axis = inst.draggingMoveAxis != MoveAxis.NONE
                ? inst.draggingMoveAxis
                : inst.hoveredMoveAxis;
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
        RotationAxis axis = inst.draggingRotationAxis != RotationAxis.NONE
                ? inst.draggingRotationAxis
                : inst.hoveredRotationAxis;
        return switch (axis) {
            case PITCH -> "pitch";
            case YAW -> "yaw";
            case ROLL -> "roll";
            default -> "";
        };
    }

    public static boolean isStaticRotationGizmoMode() {
        BodyPoseEditorFragment inst = activeInstance;
        return inst != null && inst.isRotationGizmoMode();
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
        MoveAxis axis = draggingMoveAxis != MoveAxis.NONE
                ? draggingMoveAxis
                : hoveredMoveAxis;
        return switch (axis) {
            case X -> "x";
            case Y -> "y";
            case Z -> "z";
            default -> "";
        };
    }

    public String getHighlightedRotationAxis() {
        RotationAxis axis = draggingRotationAxis != RotationAxis.NONE
                ? draggingRotationAxis
                : hoveredRotationAxis;
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
        recordPoseHistoryStep("物品显示");
    }

    private void clearSelectedItemModel() {
        if (!hasSelectedItemModel()) return;
        EDITOR_ITEMS.remove(selectedEditorItemIndex);
        selectedEditorItemIndex = Math.min(selectedEditorItemIndex, EDITOR_ITEMS.size() - 1);
        clearGizmoInteractionState();
        refreshButtonLabels();
        refreshNumericValueBindings();
        recordPoseHistoryStep("清除物品");
    }

    private void clearAllItemModels() {
        EDITOR_ITEMS.clear();
        selectedEditorItemIndex = -1;
        clearGizmoInteractionState();
        refreshButtonLabels();
        refreshNumericValueBindings();
        recordPoseHistoryStep("清除全部物品");
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
        recordPoseHistoryStep("重置偏移");
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
        for (String part : getSelectableParts()) {
            if (!part.equals("all")) poses.put(part, new PartPose());
        }
        return poses;
    }

    private static List<String> getSelectableParts() {
        if (poseEditMode != PoseEditMode.TRUE_SKELETAL) {
            return Arrays.asList(BodyModelSelectionCatalog.PARTS);
        }
        ensureTrueSkeletalPoses();
        List<String> parts = new ArrayList<>();
        parts.addAll(Arrays.asList(BodyModelSelectionCatalog.PARTS));
        for (String bone : BodyPoseSkeletalPreviewRenderer.getEditableBoneNames()) {
            if (!parts.contains(bone)) {
                parts.add(bone);
            }
        }
        for (String target : BodyPoseSkeletalPreviewRenderer.getExtraEditablePoseTargetNames()) {
            if (!parts.contains(target)) {
                parts.add(target);
            }
        }
        return parts;
    }

    private static void ensureTrueSkeletalPoses() {
        for (String part : BodyModelSelectionCatalog.PARTS) {
            if (!"all".equals(part) && !TRUE_SKELETAL_POSES.containsKey(part)) {
                TRUE_SKELETAL_POSES.put(part, new PartPose());
            }
        }
        List<String> bones = new ArrayList<>(BodyPoseSkeletalPreviewRenderer.getEditableBoneNames());
        for (String bone : bones) {
            if (!TRUE_SKELETAL_POSES.containsKey(bone)) {
                TRUE_SKELETAL_POSES.put(bone, new PartPose());
            }
        }
        for (String target : BodyPoseSkeletalPreviewRenderer.getExtraEditablePoseTargetNames()) {
            if (!TRUE_SKELETAL_POSES.containsKey(target)) {
                TRUE_SKELETAL_POSES.put(target, new PartPose());
            }
        }
    }

    private static void ensureValidSelectedPartForMode() {
        if (getSelectableParts().contains(selectedPart)) {
            return;
        }
        selectedPart = "all";
    }

    private static Map<String, PartPose> getActivePoseMap() {
        if (poseEditMode == PoseEditMode.TRUE_SKELETAL) {
            ensureTrueSkeletalPoses();
            return TRUE_SKELETAL_POSES;
        }
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
        return getPartPoseValue(selectedPart, axis);
    }

    private static float getPartPoseValue(String part, Axis axis) {
        if (part == null || part.equals("all")) return 0.0F;
        PartPose pose = getActivePoseMap().computeIfAbsent(part, k -> new PartPose());
        switch (axis) {
            case PITCH -> { return pose.pitch; }
            case YAW -> { return pose.yaw; }
            case ROLL -> { return pose.roll; }
        }
        return 0.0F;
    }

    private static void setSelectedPoseValue(Axis axis, float value) {
        setPartPoseValue(selectedPart, axis, value);
    }

    private static void setPartPoseValue(String part, Axis axis, float value) {
        if (part == null || part.equals("all")) return;
        PartPose pose = getActivePoseMap().computeIfAbsent(part, k -> new PartPose());
        switch (axis) {
            case PITCH -> pose.pitch = clampPreview(value, -180.0F, 180.0F);
            case YAW -> pose.yaw = normalizeDegrees(value);
            case ROLL -> pose.roll = normalizeDegrees(value);
        }
    }

    private static float getSelectedPoseOffset(MoveAxis axis) {
        return getPartPoseOffset(selectedPart, axis);
    }

    private static float getPartPoseOffset(String part, MoveAxis axis) {
        if (part == null || part.equals("all")) return 0.0F;
        PartPose pose = getActivePoseMap().computeIfAbsent(part, k -> new PartPose());
        return switch (axis) {
            case X -> pose.offsetX;
            case Y -> pose.offsetY;
            case Z -> pose.offsetZ;
            default -> 0.0F;
        };
    }

    private static void setSelectedPoseOffset(MoveAxis axis, float value) {
        setPartPoseOffset(selectedPart, axis, value);
    }

    private static void setPartPoseOffset(String part, MoveAxis axis, float value) {
        if (part == null || part.equals("all")) return;
        PartPose pose = getActivePoseMap().computeIfAbsent(part, k -> new PartPose());
        float next = clampPartOffset(value, poseEditMode == PoseEditMode.TRUE_SKELETAL);
        switch (axis) {
            case X -> pose.offsetX = next;
            case Y -> pose.offsetY = next;
            case Z -> pose.offsetZ = next;
            default -> {
            }
        }
    }

    private static float clampPartOffset(float value, boolean trueSkeletal) {
        return clampPreview(value,
                trueSkeletal ? TRUE_SKELETAL_OFFSET_MIN : MODEL_OFFSET_MIN,
                trueSkeletal ? TRUE_SKELETAL_OFFSET_MAX : MODEL_OFFSET_MAX);
    }

    private static float trueSkeletalPreviewOffset(float value) {
        return value / MODEL_PART_UNITS_PER_GRID;
    }

    private static float trueSkeletalModelOffset(float value) {
        return value * MODEL_PART_UNITS_PER_GRID;
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
        return getPartScale(selectedPart);
    }

    private static void setSelectedPartScale(float value) {
        setPartScale(selectedPart, value);
    }

    private static float getPartScale(String part) {
        if (part == null || part.equals("all")) return 1.0F;
        return getActivePoseMap().computeIfAbsent(part, k -> new PartPose()).scale;
    }

    private static void setPartScale(String part, float value) {
        if (part == null || part.equals("all")) return;
        getActivePoseMap().computeIfAbsent(part, k -> new PartPose()).scale = clampPreview(value, MODEL_SCALE_MIN, MODEL_SCALE_MAX);
    }

    private static boolean isPartVisible(String part) {
        if (part == null || part.equals("all")) return true;
        return getActivePoseMap().computeIfAbsent(part, k -> new PartPose()).visible;
    }

    private static void setPartVisible(String part, boolean visible) {
        if (part == null || part.equals("all")) return;
        getActivePoseMap().computeIfAbsent(part, k -> new PartPose()).visible = visible;
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
        pose.offsetX = 0.0F;
        pose.offsetY = 0.0F;
        pose.offsetZ = 0.0F;
        pose.scale = 1.0F;
        pose.visible = true;
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

    private static List<PlaceTrueSkeletalBodyC2SPacket.BonePose> createTrueSkeletalBonePoseList() {
        Map<String, float[]> rotations = getWorldSkeletalBoneRotations();
        Map<String, float[]> offsets = getWorldSkeletalBoneOffsets();
        Map<String, Float> scales = getWorldSkeletalBoneScales();
        Set<String> names = new LinkedHashSet<>();
        names.addAll(rotations.keySet());
        names.addAll(offsets.keySet());
        names.addAll(scales.keySet());
        ensureTrueSkeletalPoses();
        for (Map.Entry<String, PartPose> entry : TRUE_SKELETAL_POSES.entrySet()) {
            PartPose pose = entry.getValue();
            if (pose != null && !pose.visible) {
                names.add(entry.getKey());
            }
        }
        List<PlaceTrueSkeletalBodyC2SPacket.BonePose> poses = new ArrayList<>();
        for (String name : names) {
            float[] rotation = rotations.get(name);
            float[] offset = offsets.get(name);
            float scale = scales.getOrDefault(name, 1.0F);
            PartPose pose = TRUE_SKELETAL_POSES.get(name);
            poses.add(new PlaceTrueSkeletalBodyC2SPacket.BonePose(name,
                    rotation != null && rotation.length > 0 ? rotation[0] : 0.0F,
                    rotation != null && rotation.length > 1 ? rotation[1] : 0.0F,
                    rotation != null && rotation.length > 2 ? rotation[2] : 0.0F,
                    offset != null && offset.length > 0 ? offset[0] : 0.0F,
                    offset != null && offset.length > 1 ? offset[1] : 0.0F,
                    offset != null && offset.length > 2 ? offset[2] : 0.0F,
                    scale,
                    pose == null || pose.visible));
        }
        return poses;
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
    private enum PoseEditMode { STATIC_PART, SKELETAL, TRUE_SKELETAL }
    private enum RenameTarget { NONE, PAGE, PRESET }

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

    private record NumericAxisSpec(Supplier<Float> getter,
                                   Consumer<Float> setter,
                                   float step,
                                   float min,
                                   float max,
                                   boolean wrap,
                                   int color) {
    }

    private static final class PoseHistoryEntry {
        private final int step;
        private final String source;
        private final String summary;
        private final List<String> changes;
        private final BodyPosePresetStore.EditorStateData snapshot;

        private PoseHistoryEntry(int step, String source, String summary, List<String> changes,
                                 BodyPosePresetStore.EditorStateData snapshot) {
            this.step = step;
            this.source = source;
            this.summary = summary;
            this.changes = List.copyOf(changes);
            this.snapshot = snapshot;
        }
    }

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
            float before = getter.get();
            float next = wrap ? normalizeDegrees(value) : clampPreview(value, min, max);
            setter.accept(next);
            if (syncField) {
                sync();
            }
            invalidatePreview();
            if (Math.abs(before - next) > 0.0001F) {
                recordPoseHistoryStep("数值调整");
            }
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

    private static final class PresetPreviewView extends View {
        private final BodyPosePresetStore.EditorStateData state;

        private PresetPreviewView(Context context, BodyPosePresetStore.EditorStateData state) {
            super(context);
            this.state = state;
            setWillNotDraw(false);
            setBackground(new ColorDrawable(0x22111118));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            Paint paint = Paint.obtain();
            try {
                paint.setStroke(true);
                paint.setStrokeWidth(1.3F);
                paint.setStrokeCap(Paint.CAP_ROUND);
                paint.setColor(0x883A4658);
                canvas.drawRect(0, 0, width, height, paint);

                paint.setColor(0xFFFFDD66);
                float cx = width * 0.5F;
                float headY = height * 0.18F;
                float neckY = height * 0.34F;
                float hipY = height * 0.62F;
                float limb = Math.max(8.0F, Math.min(width, height) * 0.26F);

                canvas.drawCircle(cx, headY, Math.max(3.5F, height * 0.08F), paint);
                canvas.drawLine(cx, neckY, cx, hipY, paint);

                drawLimb(canvas, paint, cx, neckY, limb, 210.0F + poseYaw("left_arm"), posePitch("left_arm") * 0.35F);
                drawLimb(canvas, paint, cx, neckY, limb, -30.0F + poseYaw("right_arm"), posePitch("right_arm") * 0.35F);
                drawLimb(canvas, paint, cx, hipY, limb, 120.0F + poseYaw("left_leg"), posePitch("left_leg") * 0.25F);
                drawLimb(canvas, paint, cx, hipY, limb, 60.0F + poseYaw("right_leg"), posePitch("right_leg") * 0.25F);
            } finally {
                paint.recycle();
            }
        }

        private void drawLimb(Canvas canvas, Paint paint, float x, float y, float length, float angleDegrees, float yBias) {
            double radians = Math.toRadians(angleDegrees);
            float endX = x + (float) Math.cos(radians) * length;
            float endY = y + (float) Math.sin(radians) * length + yBias;
            canvas.drawLine(x, y, endX, endY, paint);
        }

        private float posePitch(String part) {
            BodyPosePresetStore.PoseData pose = getPose(part);
            return pose != null ? pose.pitch : 0.0F;
        }

        private float poseYaw(String part) {
            BodyPosePresetStore.PoseData pose = getPose(part);
            return pose != null ? pose.yaw : 0.0F;
        }

        private BodyPosePresetStore.PoseData getPose(String part) {
            if (state == null) {
                return null;
            }
            Map<String, BodyPosePresetStore.PoseData> poses = switch (state.poseEditMode) {
                case "STATIC_PART" -> state.partPoses;
                case "TRUE_SKELETAL" -> state.trueSkeletalPoses;
                default -> state.skeletalPoses;
            };
            return poses != null ? poses.get(part) : null;
        }
    }

    private static final class PartPose {
        private float pitch;
        private float yaw;
        private float roll;
        private float bendPitch;
        private float bendYaw;
        private float bendRoll;
        private float offsetX;
        private float offsetY;
        private float offsetZ;
        private float scale = 1.0F;
        private boolean visible = true;
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

    private record ReeditTarget(int entityId, ItemStack stack) {
    }

    private static final class ScreenPoint {
        private final float x;
        private final float y;
        private final float scale;
        private final float depth;

        private ScreenPoint(float x, float y) {
            this(x, y, 1.0F, PREVIEW_CAMERA_DISTANCE);
        }

        private ScreenPoint(float x, float y, float scale, float depth) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.depth = depth;
        }
    }
}
