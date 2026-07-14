package com.kuilunfuzhe.monvhua.gui.body.bodypose;

import com.kuilunfuzhe.monvhua.features.block.body.BodyModelSelectionCatalog;
import com.kuilunfuzhe.monvhua.model.CombinedBodyModelData;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.TorsoBendFollower;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.kuilunfuzhe.monvhua.network.bodypose.ApplySkeletalPoseC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePoseEditorItemsC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePosedBodyC2SPacket;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BodyPoseEditorScreen extends Screen {
	private static final float ROTATION_STEP_DEGREES = 5.0F;
	private static final float PREVIEW_CHEST_PIVOT_Y = 8.0F;
	private static final float PREVIEW_Y_PIVOT = 1.601F;
	private static final float MODEL_PART_UNITS_PER_GRID = 16.0F;
	private static final float MODEL_OFFSET_MIN = -10.0F;
	private static final float MODEL_OFFSET_MAX = 10.0F;
	private static final float TRANSFORM_OFFSET_STEP = 0.25F;
	private static final float DEFAULT_PREVIEW_PAN_X = 32.0F;
	private static final float DEFAULT_PREVIEW_ZOOM = 0.82F;
	private static final float PREVIEW_SCALE_FACTOR = 0.44F;
	private static final float PREVIEW_ZOOM_MIN = 0.15F;
	private static final float PREVIEW_ZOOM_MAX = 8.0F;
	private static final float PREVIEW_ZOOM_STEP = 0.18F;
	private static final float MOVE_AXIS_LENGTH = 4.0F / 3.0F;
	private static final float MOVE_AXIS_HIT_RADIUS = 3.4F;
	private static final float ROTATION_RING_RADIUS = 2.45F / 3.0F;
	private static final float ROTATION_RING_HIT_RADIUS = 3.0F;
	private static final int ROTATION_RING_SEGMENTS = 48;
	private static final int GROUND_GRID_SIZE = 21;
	private static final float GROUND_GRID_HALF_SIZE = GROUND_GRID_SIZE * 0.5F;
	private static final float GROUND_GRID_CELL = 1.0F;
	private static final float GROUND_GRID_Y = 1.05F;
	private static final int PLAYER_LIST_ITEM_HEIGHT = 18;
	private static final int PLAYER_LIST_VISIBLE_ROWS = 6;
	private static final int PLAYER_SELECTOR_WIDTH = 152;
	private static final int ITEM_LIST_ITEM_HEIGHT = 18;
	private static final int ITEM_LIST_VISIBLE_ROWS = 8;
	private static final int ITEM_SELECTOR_WIDTH = 150;
	private static final int RIGHT_CONTROL_WIDTH = 250;
	private static final int RIGHT_CONTROL_MARGIN = 28;
	private static final int RIGHT_PREVIEW_GUTTER = 48;
	private static String selectedSkin = BodyModelSelectionCatalog.LOCAL_SKINS[0];
	private static String selectedPlayerName = "";
	private static SkinSource selectedSkinSource = SkinSource.LOCAL;
	private static String selectedPart = BodyModelSelectionCatalog.PARTS[0];
	private static boolean slimModel = true;
	private static float modelOffsetX;
	private static float modelOffsetY;
	private static float modelOffsetZ;
	private static float modelPitch;
	private static float modelYaw;
	private static float modelRoll;
	private static float wholeBodyScale = 1.0F;
	private static PoseEditMode poseEditMode = PoseEditMode.STATIC_PART;
	private static final List<EditorItemModel> EDITOR_ITEMS = new ArrayList<>();
	private static final Map<String, PartPose> PART_POSES = createPartPoses();
	private static final Map<String, PartPose> SKELETAL_POSES = createPartPoses();

	/** 世界3D预览是否启用 */
	private static boolean worldPreviewEnabled = true;
	/** 世界预览模式：FOLLOW_PLAYER（跟随玩家）或 FIXED（固定位置） */
	private static PreviewMode worldPreviewMode = PreviewMode.FOLLOW_PLAYER;
	/** 放置模式下固定的世界坐标X */
	private static double fixedWorldX;
	/** 放置模式下固定的世界坐标Y */
	private static double fixedWorldY;
	/** 放置模式下固定的世界坐标Z */
	private static double fixedWorldZ;

	private final List<ButtonWidget> skinButtons = new ArrayList<>();
	private final List<ButtonWidget> partButtons = new ArrayList<>();
	private final List<ButtonWidget> poseButtons = new ArrayList<>();
	private ButtonWidget modelButton;
	private ButtonWidget poseModeButton;
	private ButtonWidget runCommandButton;
	private ButtonWidget placeButton;
	private ButtonWidget placeBackpackButton;
	private ButtonWidget applySkeletalButton;
	private ButtonWidget showWholeButton;
	private ButtonWidget playerButton;
	private ButtonWidget itemButton;
	private ButtonWidget placeItemsButton;
	private ButtonWidget clearSelectedItemButton;
	private ButtonWidget clearAllItemsButton;
	private ButtonWidget resetTransformButton;
	private PlayerEntityModel defaultPreviewModel;
	private PlayerEntityModel slimPreviewModel;
	private PlayerEntityModel worldPreviewModelDefault;
	private PlayerEntityModel worldPreviewModelSlim;
	private ButtonWidget worldPreviewToggleButton;
	private float previewPitch = 24.0F;
	private float previewYaw = 0.0F;
	private float previewRoll = 0.0F;
	private float previewZoom = DEFAULT_PREVIEW_ZOOM;
	private boolean showWholePreview = true;
	private boolean draggingPreview;
	private boolean draggingRightPreview;
	private float previewPanX = DEFAULT_PREVIEW_PAN_X;
	private float previewPanY;
	private boolean showCoordinateAxes = true;
	private boolean coordinateAxesMovable = true;
	private ButtonWidget coordToggleButton;
	private ButtonWidget coordMovableButton;
	private boolean playerListOpen;
	private int playerListScroll;
	private MoveAxis hoveredMoveAxis = MoveAxis.NONE;
	private MoveAxis draggingMoveAxis = MoveAxis.NONE;
	private RotationAxis hoveredRotationAxis = RotationAxis.NONE;
	private RotationAxis draggingRotationAxis = RotationAxis.NONE;
	private boolean itemListOpen;
	private int itemListScroll;
	private int selectedEditorItemIndex = -1;

	public BodyPoseEditorScreen() {
		super(Text.literal("Body Pose Editor"));
	}

	@Override
	protected void init() {
		this.defaultPreviewModel = null;
		this.slimPreviewModel = null;
		this.clearChildren();
		this.skinButtons.clear();
		this.partButtons.clear();
		this.poseButtons.clear();

		int panelTop = 38;
		int skinX = 18;
		int skinButtonWidth = 74;
		int skinButtonHeight = 18;
		int skinGap = 4;
		List<String> localSkins = getLocalSkins();
		if (!localSkins.contains(selectedSkin) && !localSkins.isEmpty()) {
			selectedSkin = localSkins.get(0);
		}
		this.playerButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> {
					this.playerListOpen = !this.playerListOpen;
					clampPlayerListScroll(getPlayerEntries().size());
					refreshButtonLabels();
				})
				.size(PLAYER_SELECTOR_WIDTH, 20)
				.position(getPlayerSelectorX(), getPlayerSelectorY())
				.build());

		int skinGridTop = getPlayerListTop() + 4;
		for (int i = 0; i < localSkins.size(); i++) {
			String skin = localSkins.get(i);
			int col = i % 2;
			int row = i / 2;
			ButtonWidget button = ButtonWidget.builder(Text.literal(skin), pressed -> {
				selectedSkin = skin;
				selectedSkinSource = SkinSource.LOCAL;
				this.playerListOpen = false;
				refreshButtonLabels();
			}).size(skinButtonWidth, skinButtonHeight).position(skinX + col * (skinButtonWidth + skinGap), skinGridTop + row * (skinButtonHeight + skinGap)).build();
			this.skinButtons.add(this.addDrawableChild(button));
		}

		int rightX = getRightControlX();
		this.modelButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> {
			slimModel = !slimModel;
			refreshButtonLabels();
		}).size(RIGHT_CONTROL_WIDTH, 20).position(rightX, panelTop).build());

		this.poseModeButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> {
			poseEditMode = poseEditMode == PoseEditMode.STATIC_PART ? PoseEditMode.SKELETAL : PoseEditMode.STATIC_PART;
			refreshButtonLabels();
		}).size(RIGHT_CONTROL_WIDTH, 20).position(rightX, panelTop + 24).build());

		int partY = panelTop + 58;
		for (int i = 0; i < BodyModelSelectionCatalog.PARTS.length; i++) {
			String part = BodyModelSelectionCatalog.PARTS[i];
			ButtonWidget button = ButtonWidget.builder(Text.literal(part), pressed -> {
				selectedPart = part;
				refreshButtonLabels();
			}).size(RIGHT_CONTROL_WIDTH, 18).position(rightX, partY + i * 22).build();
			this.partButtons.add(this.addDrawableChild(button));
		}

		int poseY = getPoseControlsY(panelTop);
		addPoseButton(rightX + 58, poseY, "-俯仰", () -> adjustSelectedPose(Axis.PITCH, -ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 104, poseY, "+俯仰", () -> adjustSelectedPose(Axis.PITCH, ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 58, poseY + 24, "-偏转", () -> adjustSelectedPose(Axis.YAW, -ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 104, poseY + 24, "+偏转", () -> adjustSelectedPose(Axis.YAW, ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 58, poseY + 48, "-R", () -> adjustSelectedPose(Axis.ROLL, -ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 104, poseY + 48, "+R", () -> adjustSelectedPose(Axis.ROLL, ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 156, poseY, "-B P", () -> adjustSelectedBend(Axis.PITCH, -ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 202, poseY, "+B P", () -> adjustSelectedBend(Axis.PITCH, ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 156, poseY + 24, "-B Y", () -> adjustSelectedBend(Axis.YAW, -ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 202, poseY + 24, "+B Y", () -> adjustSelectedBend(Axis.YAW, ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 156, poseY + 48, "-B R", () -> adjustSelectedBend(Axis.ROLL, -ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 202, poseY + 48, "+B R", () -> adjustSelectedBend(Axis.ROLL, ROTATION_STEP_DEGREES));
		addPoseButton(rightX, poseY + 100, "重置", BodyPoseEditorScreen::resetSelectedPose, 72);

		this.runCommandButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> runGiveCommand())
				.size(72, 18)
				.position(rightX + 78, poseY + 100)
				.build());

		this.placeButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> placePosedBody(false))
				.size(RIGHT_CONTROL_WIDTH, 20)
				.position(rightX, poseY + 126)
				.build());

		this.placeBackpackButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> placePosedBody(true))
				.size(RIGHT_CONTROL_WIDTH, 20)
				.position(rightX, poseY + 150)
				.build());

		this.applySkeletalButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> applySkeletalPose())
				.size(RIGHT_CONTROL_WIDTH, 18)
				.position(rightX, poseY + 174)
				.build());

		this.itemButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> {
					this.itemListOpen = !this.itemListOpen;
					clampItemListScroll(getAvailableItemStacks().size());
					refreshButtonLabels();
				})
				.size(getItemSelectorWidth(), 20)
				.position(getItemSelectorX(), getItemSelectorY())
				.build());

		this.placeItemsButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> placeEditorItems())
				.size(RIGHT_CONTROL_WIDTH, 18)
				.position(rightX, poseY + 198)
				.build());

		this.clearSelectedItemButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> clearSelectedItemModel())
				.size(72, 18)
				.position(rightX, poseY + 220)
				.build());

		this.clearAllItemsButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> clearAllItemModels())
				.size(74, 18)
				.position(rightX + 76, poseY + 220)
				.build());

		this.resetTransformButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> resetActiveTransform())
				.size(RIGHT_CONTROL_WIDTH, 18)
				.position(rightX, poseY + 242)
				.build());

		this.showWholeButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> {
					this.showWholePreview = !this.showWholePreview;
					refreshButtonLabels();
				})
				.size(102, 18)
				.position(getPreviewRight() - 112, getPreviewBottom() - 28)
				.build());

		this.coordToggleButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> {
					this.showCoordinateAxes = !this.showCoordinateAxes;
					refreshButtonLabels();
				})
				.size(102, 18)
				.position(getPreviewRight() - 112, getPreviewBottom() - 50)
				.build());

		this.coordMovableButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> {
					this.coordinateAxesMovable = !this.coordinateAxesMovable;
					refreshButtonLabels();
				})
				.size(102, 18)
				.position(getPreviewRight() - 112, getPreviewBottom() - 72)
				.build());

		this.worldPreviewToggleButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> {
					worldPreviewEnabled = !worldPreviewEnabled;
					refreshButtonLabels();
				})
				.size(102, 18)
				.position(getPreviewRight() - 112, getPreviewBottom() - 94)
				.build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), pressed -> this.close())
				.size(70, 20)
				.position(this.width - 88, this.height - 26)
				.build());

		refreshButtonLabels();
	}

	private void refreshButtonLabels() {
		for (ButtonWidget button : this.skinButtons) {
			String skin = button.getMessage().getString().replace("> ", "").trim();
			button.setMessage(Text.literal((selectedSkinSource == SkinSource.LOCAL && skin.equals(selectedSkin) ? "> " : "  ") + skin));
		}
		for (ButtonWidget button : this.partButtons) {
			String part = button.getMessage().getString().replace("> ", "").trim();
			button.setMessage(Text.literal((part.equals(selectedPart) ? "> " : "  ") + part));
		}
		if (this.playerButton != null) {
			String name = selectedSkinSource == SkinSource.PLAYER && !selectedPlayerName.isBlank() ? selectedPlayerName : "选择";
			this.playerButton.setMessage(Text.literal("玩家: " + name));
		}
		if (this.modelButton != null) {
			this.modelButton.setMessage(Text.literal("Model: " + (slimModel ? "slim-细手臂模型" : "default-默认")));
		}
		if (this.poseModeButton != null) {
			this.poseModeButton.setMessage(Text.literal("模型-选择: " + (poseEditMode == PoseEditMode.STATIC_PART ? "Static" : "Skeletal")));
		}
		if (this.runCommandButton != null) {
			this.runCommandButton.setMessage(Text.literal("给予肢体"));
		}
		if (this.placeButton != null) {
			this.placeButton.setMessage(Text.literal("放置模型"));
		}
		if (this.placeBackpackButton != null) {
			this.placeBackpackButton.setMessage(Text.literal("\u653e\u7f6e\u6a21\u578b(\u80cc\u5305)"));
		}
		if (this.applySkeletalButton != null) {
			this.applySkeletalButton.setMessage(Text.literal("Apply Skeletal"));
		}
		if (this.itemButton != null) {
			this.itemButton.setMessage(Text.literal("物品: " + getSelectedItemLabel()));
		}
		if (this.placeItemsButton != null) {
			this.placeItemsButton.setMessage(Text.literal("放置物品模型(" + EDITOR_ITEMS.size() + ")"));
			this.placeItemsButton.active = !EDITOR_ITEMS.isEmpty();
		}
		if (this.clearSelectedItemButton != null) {
			this.clearSelectedItemButton.setMessage(Text.literal("清除选中"));
			this.clearSelectedItemButton.active = hasSelectedItemModel();
		}
		if (this.clearAllItemsButton != null) {
			this.clearAllItemsButton.setMessage(Text.literal("清除全部"));
			this.clearAllItemsButton.active = !EDITOR_ITEMS.isEmpty();
		}
		if (this.resetTransformButton != null) {
			this.resetTransformButton.setMessage(Text.literal("重置当前偏移"));
		}
		if (this.showWholeButton != null) {
			this.showWholeButton.setMessage(Text.literal("整体预览 " + (this.showWholePreview ? "开启" : "关闭")));
		}
		if (this.coordToggleButton != null) {
			this.coordToggleButton.setMessage(Text.literal("坐标系 " + (this.showCoordinateAxes ? "开启" : "关闭")));
		}
		if (this.coordMovableButton != null) {
			this.coordMovableButton.setMessage(Text.literal("坐标跟随 " + (this.coordinateAxesMovable ? "开启" : "关闭")));
		}
		if (this.worldPreviewToggleButton != null) {
			this.worldPreviewToggleButton.setMessage(Text.literal("3D预览 " + (worldPreviewEnabled ? "开启" : "关闭")));
		}
		boolean canEditPose = !selectedPart.equals("all");
		for (ButtonWidget button : this.poseButtons) {
			button.active = canEditPose;
		}
	}

	private void addPoseButton(int x, int y, String label, Runnable action) {
		addPoseButton(x, y, label, action, 44);
	}

	private void addPoseButton(int x, int y, String label, Runnable action, int width) {
		ButtonWidget button = ButtonWidget.builder(Text.literal(label), pressed -> action.run())
				.size(width, 18)
				.position(x, y)
				.build();
		this.poseButtons.add(this.addDrawableChild(button));
	}

	private void runGiveCommand() {
		if (this.client == null || this.client.player == null || this.client.player.networkHandler == null) {
			return;
		}
		String command;
		String partCommand = givePartCommandToken(selectedPart);
		if (selectedSkinSource == SkinSource.PLAYER && !selectedPlayerName.isBlank()) {
			command = "clairvoyance-body-give_肢体获取 @s " + selectedPlayerName + " " + partCommand;
		} else {
			command = "clairvoyance-body-give_肢体获取 @s localskin_内置 " + selectedSkin + " " + partCommand;
		}
		if (slimModel && (selectedSkinSource != SkinSource.PLAYER || !selectedPart.equals("head"))) {
			command += " slim_纤细";
		}
		this.client.player.networkHandler.sendChatCommand(command);
	}

	private static String givePartCommandToken(String part) {
		return switch (part) {
			case "all" -> "all_全部";
			case "torso" -> "torso_躯干";
			case "left_arm" -> "left_arm_左臂";
			case "right_arm" -> "right_arm_右臂";
			case "left_leg" -> "left_leg_左腿";
			case "right_leg" -> "right_leg_右腿";
			case "head" -> "head_头";
			default -> part;
		};
	}

	private void placePosedBody(boolean backpackEnabled) {
		boolean skeletalMode = poseEditMode == PoseEditMode.SKELETAL;
		ClientPlayNetworking.send(new PlacePosedBodyC2SPacket(selectedSkin, slimModel,
				skeletalMode ? createSkeletalPoseValueArray() : createStaticPoseValueArray(),
				skeletalMode ? createSkeletalBendValueArray() : null,
				selectedSkinSource == SkinSource.PLAYER, selectedPlayerName,
				modelOffsetX, modelOffsetY, modelOffsetZ,
				modelPitch, modelYaw, modelRoll, wholeBodyScale, backpackEnabled));
	}

	private void applySkeletalPose() {
		ClientPlayNetworking.send(new ApplySkeletalPoseC2SPacket(
				createSkeletalPoseValueArray(),
				createSkeletalBendValueArray()));
	}

	private void placeEditorItems() {
		if (EDITOR_ITEMS.isEmpty()) {
			return;
		}
		List<PlacePoseEditorItemsC2SPacket.ItemPlacement> placements = new ArrayList<>();
		for (EditorItemModel item : EDITOR_ITEMS) {
			Identifier itemId = Registries.ITEM.getId(item.stack.getItem());
			if (itemId != null) {
				placements.add(new PlacePoseEditorItemsC2SPacket.ItemPlacement(
						itemId,
						item.offsetX,
						item.offsetY,
						item.offsetZ,
						item.pitch,
						item.yaw,
						item.roll));
			}
		}
		ClientPlayNetworking.send(new PlacePoseEditorItemsC2SPacket(placements));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		int previewLeft = getPreviewLeft();
		int previewRight = getPreviewRight();
		int previewTop = getPreviewTop();
		int previewBottom = getPreviewBottom();

		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);
		context.drawTextWithShadow(this.textRenderer, "Skin", 18, 26, 0xD8D8D8);
		context.drawTextWithShadow(this.textRenderer, "Preview", previewLeft, 26, 0xD8D8D8);
		context.drawTextWithShadow(this.textRenderer, "Selection", getRightControlX(), 26, 0xD8D8D8);
		String modeText = worldPreviewMode == PreviewMode.FOLLOW_PLAYER ? "P: 预览模式" : "P: 放置模式";
		context.drawTextWithShadow(this.textRenderer, modeText, 18, this.height - 26, 0xFFB8B8FF);
		renderPoseReadout(context, getRightControlX(), getPoseControlsY(38));

		context.fill(previewLeft, previewTop, previewRight, previewBottom, 0x66000000);
		context.drawBorder(previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop, 0x88FFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, getSelectedSkinLabel() + " / " + (slimModel ? "slim" : "default"), (previewLeft + previewRight) / 2, previewTop + 16, 0xFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, selectedPart, (previewLeft + previewRight) / 2, previewTop + 30, 0xB8B8B8);
		this.hoveredMoveAxis = this.draggingMoveAxis == MoveAxis.NONE ? findMoveAxis(mouseX, mouseY) : this.draggingMoveAxis;
		this.hoveredRotationAxis = this.draggingRotationAxis == RotationAxis.NONE ? findRotationRing(mouseX, mouseY) : this.draggingRotationAxis;
		renderPreviewGroundGrid(context, previewLeft + 10, previewTop + 48, previewRight - 10, previewBottom - 10);
		renderModelOffsetReadout(context, previewLeft + 8, previewTop + 32);
		renderPlayerPreview(context, previewLeft + 10, previewTop + 48, previewRight - 10, previewBottom - 10);
		renderEditorItemPreviews(context);

		super.render(context, mouseX, mouseY, deltaTicks);
		renderPlayerList(context, mouseX, mouseY);
		renderItemList(context, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && this.itemListOpen) {
			if (selectItemFromList(mouseX, mouseY)) {
				return true;
			}
			if (isInsideItemList(mouseX, mouseY)) {
				return true;
			}
			if (!isInsideItemSelector(mouseX, mouseY)) {
				this.itemListOpen = false;
				refreshButtonLabels();
			}
		}
		if (button == 0 && this.playerListOpen) {
			if (selectPlayerFromList(mouseX, mouseY)) {
				return true;
			}
			if (isInsidePlayerList(mouseX, mouseY)) {
				return true;
			}
			if (!isInsidePlayerSelector(mouseX, mouseY)) {
				this.playerListOpen = false;
				refreshButtonLabels();
			}
		}
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button == 0 && isInsidePreview(mouseX, mouseY)) {
			int itemIndex = findPreviewItem(mouseX, mouseY);
			if (itemIndex >= 0) {
				this.selectedEditorItemIndex = itemIndex;
				refreshButtonLabels();
				return true;
			}
			RotationAxis rotationAxis = findRotationRing(mouseX, mouseY);
			if (rotationAxis != RotationAxis.NONE) {
				this.draggingPreview = false;
				this.draggingRotationAxis = rotationAxis;
				this.hoveredRotationAxis = rotationAxis;
				return true;
			}
			MoveAxis moveAxis = findMoveAxis(mouseX, mouseY);
			if (moveAxis != MoveAxis.NONE) {
				this.draggingPreview = false;
				this.draggingMoveAxis = moveAxis;
				this.hoveredMoveAxis = moveAxis;
				return true;
			}
			if (this.selectedEditorItemIndex != -1) {
				this.selectedEditorItemIndex = -1;
				refreshButtonLabels();
			}
			this.draggingPreview = true;
			return true;
		}
		if (button == 1 && isInsidePreview(mouseX, mouseY)) {
			this.draggingRightPreview = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && this.draggingRotationAxis != RotationAxis.NONE) {
			this.draggingRotationAxis = RotationAxis.NONE;
			this.hoveredRotationAxis = findRotationRing(mouseX, mouseY);
			return true;
		}
		if (button == 0 && this.draggingMoveAxis != MoveAxis.NONE) {
			this.draggingMoveAxis = MoveAxis.NONE;
			this.hoveredMoveAxis = findMoveAxis(mouseX, mouseY);
			return true;
		}
		if (button == 0 && this.draggingPreview) {
			this.draggingPreview = false;
			return true;
		}
		if (button == 1 && this.draggingRightPreview) {
			this.draggingRightPreview = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && this.draggingRotationAxis != RotationAxis.NONE) {
			dragModelRotation(this.draggingRotationAxis, mouseX, mouseY, deltaX, deltaY);
			return true;
		}
		if (button == 0 && this.draggingMoveAxis != MoveAxis.NONE) {
			dragModelOffset(this.draggingMoveAxis, deltaX, deltaY);
			return true;
		}
		if (button == 0 && this.draggingPreview
				&& this.draggingRotationAxis == RotationAxis.NONE
				&& this.draggingMoveAxis == MoveAxis.NONE) {
			this.previewYaw += (float) deltaX * 0.65F;
			this.previewPitch = clamp(this.previewPitch + (float) deltaY * 0.65F, -60.0F, 60.0F);
			return true;
		}
		if (button == 1 && this.draggingRightPreview) {
			this.previewPanX += (float) deltaX;
			this.previewPanY += (float) deltaY;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (this.itemListOpen && isInsideItemList(mouseX, mouseY)) {
			int direction = verticalAmount > 0.0D ? -1 : 1;
			this.itemListScroll += direction;
			clampItemListScroll(getAvailableItemStacks().size());
			return true;
		}
		if (this.playerListOpen && isInsidePlayerList(mouseX, mouseY)) {
			int direction = verticalAmount > 0.0D ? -1 : 1;
			this.playerListScroll += direction;
			clampPlayerListScroll(getPlayerEntries().size());
			return true;
		}
		if (isInsidePreview(mouseX, mouseY)) {
			this.previewZoom = clamp(this.previewZoom + (float) verticalAmount * PREVIEW_ZOOM_STEP, PREVIEW_ZOOM_MIN, PREVIEW_ZOOM_MAX);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_P) {
			toggleWorldPreviewMode();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_DELETE) {
			clearSelectedItemModel();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void toggleWorldPreviewMode() {
		if (worldPreviewMode == PreviewMode.FOLLOW_PLAYER) {
			worldPreviewMode = PreviewMode.FIXED;
			if (this.client != null && this.client.player != null) {
				fixedWorldX = this.client.player.getX();
				fixedWorldY = this.client.player.getY();
				fixedWorldZ = this.client.player.getZ();
			}
		} else {
			worldPreviewMode = PreviewMode.FOLLOW_PLAYER;
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void renderPoseReadout(DrawContext context, int x, int y) {
		context.drawTextWithShadow(this.textRenderer, "Pose", x, y - 14, 0xD8D8D8);
		if (selectedPart.equals("all")) {
			context.drawTextWithShadow(this.textRenderer, "Select a part", x, y + 2, 0x909090);
			context.drawTextWithShadow(this.textRenderer, "to edit pose", x, y + 14, 0x909090);
			return;
		}
		PartPose pose = getSelectedPose();
		context.drawTextWithShadow(this.textRenderer, "Pitch " + formatDegrees(pose.pitch), x, y + 4, 0xB8B8B8);
		context.drawTextWithShadow(this.textRenderer, "Yaw   " + formatDegrees(pose.yaw), x, y + 28, 0xB8B8B8);
		context.drawTextWithShadow(this.textRenderer, "Roll  " + formatDegrees(pose.roll), x, y + 52, 0xB8B8B8);
		if (hasSelectedBendControls()) {
			context.drawTextWithShadow(this.textRenderer, getBendLabelPrefix(), x + 156, y - 14, 0xAAE0FF);
			context.drawTextWithShadow(this.textRenderer, "P " + formatDegrees(pose.bendPitch), x + 156, y + 4, 0xAAE0FF);
			context.drawTextWithShadow(this.textRenderer, "Y " + formatDegrees(pose.bendYaw), x + 156, y + 28, 0xAAE0FF);
			context.drawTextWithShadow(this.textRenderer, "R " + formatDegrees(pose.bendRoll), x + 156, y + 52, 0xAAE0FF);
		}
	}

	private void renderPlayerPreview(DrawContext context, int x1, int y1, int x2, int y2) {
		PlayerEntityModel model = getPreviewModel();
		if (model == null) {
			return;
		}

		preparePreviewModel(model);
		int width = x2 - x1;
		int height = y2 - y1;
		float scale = getPreviewBaseScale(width, height) * this.previewZoom;
		int renderBottom = Math.max(y1 + 1, Math.round((y1 + y2) * 0.5F + scale * PREVIEW_Y_PIVOT));
		int panX = (int) this.previewPanX;
		int panY = (int) this.previewPanY;
		ScreenPoint offset = getPlayerModelScreenOffset();
		int offsetX = Math.round(offset.x);
		int offsetY = Math.round(offset.y);
		Identifier texture = getPreviewTexture();
		context.enableScissor(x1, y1, x2, y2);
		try {
			context.addPlayerSkin(model, texture, scale, this.previewPitch, this.previewYaw, PREVIEW_Y_PIVOT,
					x1 + panX + offsetX, y1 + panY + offsetY,
					x2 + panX + offsetX, renderBottom + panY + offsetY);
		} finally {
			context.disableScissor();
		}
	}

	private void renderPreviewGroundGrid(DrawContext context, int x1, int y1, int x2, int y2) {
		if (!this.showCoordinateAxes) {
			return;
		}
		context.enableScissor(x1, y1, x2, y2);
		try {
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
		} finally {
			context.disableScissor();
		}
	}

	private static void drawProjectedLine(DrawContext context, ScreenPoint start, ScreenPoint end, int color) {
		double dx = end.x - start.x;
		double dy = end.y - start.y;
		double length = Math.sqrt(dx * dx + dy * dy);
		if (length < 0.5D) {
			return;
		}
		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		try {
			matrices.translate((float) start.x, (float) start.y);
			matrices.rotate((float) Math.atan2(dy, dx));
			context.fill(0, 0, Math.max(1, (int) Math.ceil(length)), 1, color);
		} finally {
			matrices.popMatrix();
		}
	}

	private static int argb(int a, int r, int g, int b) {
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private void renderModelOffsetReadout(DrawContext context, int x, int y) {
		context.drawTextWithShadow(this.textRenderer, getActiveModelLabel(), x, y, 0xFFE2E8F0);
		context.drawTextWithShadow(this.textRenderer, "X " + formatOffset(getActiveOffsetX()), x, y + 12, 0xFFFF7777);
		context.drawTextWithShadow(this.textRenderer, "Y " + formatOffset(getActiveOffsetY()), x, y + 24, 0xFF77FF77);
		context.drawTextWithShadow(this.textRenderer, "Z " + formatOffset(getActiveOffsetZ()), x, y + 36, 0xFF8CA0FF);
		context.drawTextWithShadow(this.textRenderer, "Rot P " + formatDegrees(getActivePitch()), x, y + 54, 0xFFFF7777);
		context.drawTextWithShadow(this.textRenderer, "Rot Y " + formatDegrees(getActiveYaw()), x, y + 66, 0xFF77FF77);
		context.drawTextWithShadow(this.textRenderer, "Rot R " + formatDegrees(getActiveRoll()), x, y + 78, 0xFF8CA0FF);
	}

	public boolean isShowingCoordinateAxes() {
		return this.showCoordinateAxes;
	}

	public boolean isCoordinateAxesMovable() {
		return this.coordinateAxesMovable;
	}

	/** @return 世界3D预览是否活跃（启用且当前屏幕是编辑器） */
	public static boolean isWorldPreviewActive() {
		return worldPreviewEnabled && MinecraftClient.getInstance().currentScreen instanceof BodyPoseEditorScreen;
	}

	/** @return 当前世界预览模式 */
	public static PreviewMode getWorldPreviewMode() { return worldPreviewMode; }
	public static double getFixedWorldX() { return fixedWorldX; }
	public static double getFixedWorldY() { return fixedWorldY; }
	public static double getFixedWorldZ() { return fixedWorldZ; }

	/** @return 是否显示世界坐标轴 */
	public static boolean isWorldAxesShown() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen instanceof BodyPoseEditorScreen screen) {
			return screen.showCoordinateAxes;
		}
		return false;
	}

	/** @return 世界坐标轴是否跟随模型偏移 */
	public static boolean isWorldAxesMovable() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen instanceof BodyPoseEditorScreen screen) {
			return screen.coordinateAxesMovable;
		}
		return false;
	}

	public static float getWorldModelOffsetX() { return modelOffsetX; }
	public static float getWorldModelOffsetY() { return modelOffsetY; }
	public static float getWorldModelOffsetZ() { return modelOffsetZ; }
	public static float getWorldModelPitch() { return modelPitch; }
	public static float getWorldModelYaw() { return modelYaw; }
	public static float getWorldModelRoll() { return modelRoll; }

	public static String getStaticHighlightedMoveAxis() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen instanceof BodyPoseEditorScreen screen) {
			return screen.getHighlightedMoveAxis();
		}
		return "";
	}

	public static String getStaticHighlightedRotationAxis() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen instanceof BodyPoseEditorScreen screen) {
			return screen.getHighlightedRotationAxis();
		}
		return "";
	}

	public static Identifier getWorldSkinTexture() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen instanceof BodyPoseEditorScreen screen) {
			return screen.getPreviewTexture();
		}
		return Identifier.of("monvhua", "textures/local_skin/" + selectedSkin + ".png");
	}

	public static boolean isWorldSlimModel() { return slimModel; }

	public static float getPreviewYaw() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen instanceof BodyPoseEditorScreen screen) {
			return screen.previewYaw;
		}
		return 0;
	}

	public static float getPreviewPitch() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen instanceof BodyPoseEditorScreen screen) {
			return screen.previewPitch;
		}
		return 0;
	}

	public static float getPreviewRoll() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen instanceof BodyPoseEditorScreen screen) {
			return screen.previewRoll;
		}
		return 0;
	}

	public float getModelOffsetX() {
		return getActiveOffsetX();
	}

	public float getModelOffsetY() {
		return getActiveOffsetY();
	}

	public float getModelOffsetZ() {
		return getActiveOffsetZ();
	}

	public String getHighlightedMoveAxis() {
		MoveAxis axis = this.draggingMoveAxis != MoveAxis.NONE ? this.draggingMoveAxis : this.hoveredMoveAxis;
		return switch (axis) {
			case X -> "x";
			case Y -> "y";
			case Z -> "z";
			case NONE -> "";
		};
	}

	public String getHighlightedRotationAxis() {
		RotationAxis axis = this.draggingRotationAxis != RotationAxis.NONE ? this.draggingRotationAxis : this.hoveredRotationAxis;
		return switch (axis) {
			case PITCH -> "pitch";
			case YAW -> "yaw";
			case ROLL -> "roll";
			case NONE -> "";
		};
	}

	public boolean isEditingPlayerModel() {
		return !hasSelectedItemModel();
	}

	private PlayerEntityModel getPreviewModel() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return null;
		}
		if (slimModel) {
			if (this.slimPreviewModel == null) {
				this.slimPreviewModel = new PlayerEntityModel(client.getLoadedEntityModels().getModelPart(ModModelLayers.COMBINED_BODY_SLIM), true);
			}
			return this.slimPreviewModel;
		}
		if (this.defaultPreviewModel == null) {
			this.defaultPreviewModel = new PlayerEntityModel(client.getLoadedEntityModels().getModelPart(ModModelLayers.COMBINED_BODY), false);
		}
		return this.defaultPreviewModel;
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

		boolean showAll = this.showWholePreview || selectedPart.equals("all");
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
		if (pose == null) {
			return;
		}
		float degreesToRadians = (float) (Math.PI / 180.0);
		part.pitch += pose.pitch * degreesToRadians;
		part.yaw += pose.yaw * degreesToRadians;
		part.roll += pose.roll * degreesToRadians;
		setUniformScale(part, pose.scale);
	}

	private static void applyBendPose(ModelPart part, PartPose pose) {
		float degreesToRadians = (float) (Math.PI / 180.0);
		part.pitch += pose.bendPitch * degreesToRadians;
		part.yaw += pose.bendYaw * degreesToRadians;
		part.roll += pose.bendRoll * degreesToRadians;
	}

	private static void applyHalfBendPose(ModelPart part, PartPose pose) {
		float degreesToRadians = (float) (Math.PI / 180.0);
		part.pitch += pose.bendPitch * 0.5F * degreesToRadians;
		part.yaw += pose.bendYaw * 0.5F * degreesToRadians;
		part.roll += pose.bendRoll * 0.5F * degreesToRadians;
	}

	private static boolean hasBendPose(PartPose pose) {
		return pose.bendPitch != 0.0F || pose.bendYaw != 0.0F || pose.bendRoll != 0.0F;
	}

	private static void setUniformScale(ModelPart part, float scale) {
		part.xScale *= scale;
		part.yScale *= scale;
		part.zScale *= scale;
	}

	public PlayerEntityModel getPreparedWorldPreviewModel() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) return null;

		PlayerEntityModel model;
		if (slimModel) {
			if (this.worldPreviewModelSlim == null) {
				this.worldPreviewModelSlim = new PlayerEntityModel(
					client.getLoadedEntityModels().getModelPart(ModModelLayers.COMBINED_BODY_SLIM), true);
			}
			model = this.worldPreviewModelSlim;
		} else {
			if (this.worldPreviewModelDefault == null) {
				this.worldPreviewModelDefault = new PlayerEntityModel(
					client.getLoadedEntityModels().getModelPart(ModModelLayers.COMBINED_BODY), false);
			}
			model = this.worldPreviewModelDefault;
		}

		for (ModelPart part : model.getRootPart().traverse()) {
			part.resetTransform();
			part.visible = true;
			part.hidden = false;
		}

		boolean showAll = this.showWholePreview || selectedPart.equals("all");
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

	private static int getPoseControlsY(int panelTop) {
		return panelTop + 58 + BodyModelSelectionCatalog.PARTS.length * 22 + 16;
	}

	private int getPreviewLeft() {
		return 190;
	}

	private int getPreviewRight() {
		return Math.max(getPreviewLeft() + 140, getRightControlX() - RIGHT_PREVIEW_GUTTER);
	}

	private int getRightControlX() {
		return this.width - RIGHT_CONTROL_WIDTH - RIGHT_CONTROL_MARGIN;
	}

	private int getPreviewTop() {
		return 38;
	}

	private int getPreviewBottom() {
		return this.height - 36;
	}

	private boolean isInsidePreview(double mouseX, double mouseY) {
		return mouseX >= getPreviewLeft() + 10
				&& mouseX <= getPreviewRight() - 10
				&& mouseY >= getPreviewTop() + 48
				&& mouseY <= getPreviewBottom() - 10;
	}

	private MoveAxis findMoveAxis(double mouseX, double mouseY) {
		if (!this.showCoordinateAxes) {
			return MoveAxis.NONE;
		}
		if (!isInsidePreview(mouseX, mouseY)) {
			return MoveAxis.NONE;
		}
		float offsetX = getActiveOffsetX();
		float offsetY = getActiveOffsetY();
		float offsetZ = getActiveOffsetZ();
		ScreenPoint center = projectModelPoint(offsetX, offsetY, offsetZ);
		double bestDistance = MOVE_AXIS_HIT_RADIUS;
		MoveAxis bestAxis = MoveAxis.NONE;
		double distance = distanceToSegment(mouseX, mouseY, center, projectModelPoint(offsetX + MOVE_AXIS_LENGTH, offsetY, offsetZ));
		if (distance <= bestDistance) {
			bestDistance = distance;
			bestAxis = MoveAxis.X;
		}
		distance = distanceToSegment(mouseX, mouseY, center, projectModelPoint(offsetX, offsetY + MOVE_AXIS_LENGTH, offsetZ));
		if (distance <= bestDistance) {
			bestDistance = distance;
			bestAxis = MoveAxis.Y;
		}
		distance = distanceToSegment(mouseX, mouseY, center, projectModelPoint(offsetX, offsetY, offsetZ + MOVE_AXIS_LENGTH));
		if (distance <= bestDistance) {
			bestAxis = MoveAxis.Z;
		}
		return bestAxis;
	}

	private RotationAxis findRotationRing(double mouseX, double mouseY) {
		if (!this.showCoordinateAxes) {
			return RotationAxis.NONE;
		}
		if (!isInsidePreview(mouseX, mouseY)) {
			return RotationAxis.NONE;
		}
		double bestDistance = ROTATION_RING_HIT_RADIUS;
		RotationAxis bestAxis = RotationAxis.NONE;
		double distance = distanceToRotationRing(mouseX, mouseY, RotationAxis.PITCH);
		if (distance <= bestDistance) {
			bestDistance = distance;
			bestAxis = RotationAxis.PITCH;
		}
		distance = distanceToRotationRing(mouseX, mouseY, RotationAxis.YAW);
		if (distance <= bestDistance) {
			bestDistance = distance;
			bestAxis = RotationAxis.YAW;
		}
		distance = distanceToRotationRing(mouseX, mouseY, RotationAxis.ROLL);
		if (distance <= bestDistance) {
			bestAxis = RotationAxis.ROLL;
		}
		return bestAxis;
	}

	private double distanceToRotationRing(double mouseX, double mouseY, RotationAxis axis) {
		double bestDistance = Double.MAX_VALUE;
		ScreenPoint previous = projectRotationRingPoint(axis, 0);
		for (int i = 1; i <= ROTATION_RING_SEGMENTS; i++) {
			ScreenPoint current = projectRotationRingPoint(axis, i);
			bestDistance = Math.min(bestDistance, distanceToSegment(mouseX, mouseY, previous, current));
			previous = current;
		}
		return bestDistance;
	}

	private ScreenPoint projectRotationRingPoint(RotationAxis axis, int segment) {
		float angle = (float) (Math.PI * 2.0D * segment / ROTATION_RING_SEGMENTS);
		float cos = (float) Math.cos(angle) * ROTATION_RING_RADIUS;
		float sin = (float) Math.sin(angle) * ROTATION_RING_RADIUS;
		float offsetX = getActiveOffsetX();
		float offsetY = getActiveOffsetY();
		float offsetZ = getActiveOffsetZ();
		return switch (axis) {
			case PITCH -> projectModelPoint(offsetX, offsetY + cos, offsetZ + sin);
			case YAW -> projectModelPoint(offsetX + cos, offsetY, offsetZ + sin);
			case ROLL -> projectModelPoint(offsetX + cos, offsetY + sin, offsetZ);
			case NONE -> projectModelPoint(offsetX, offsetY, offsetZ);
		};
	}

	private void dragModelOffset(MoveAxis axis, double deltaX, double deltaY) {
		float offsetX = getActiveOffsetX();
		float offsetY = getActiveOffsetY();
		float offsetZ = getActiveOffsetZ();
		ScreenPoint center = projectModelPoint(offsetX, offsetY, offsetZ);
		ScreenPoint end = switch (axis) {
			case X -> projectModelPoint(offsetX + MOVE_AXIS_LENGTH, offsetY, offsetZ);
			case Y -> projectModelPoint(offsetX, offsetY + MOVE_AXIS_LENGTH, offsetZ);
			case Z -> projectModelPoint(offsetX, offsetY, offsetZ + MOVE_AXIS_LENGTH);
			case NONE -> center;
		};
		double axisX = end.x - center.x;
		double axisY = end.y - center.y;
		double axisLength = Math.sqrt(axisX * axisX + axisY * axisY);
		if (axisLength < 0.001D) {
			return;
		}
		float deltaUnits = (float) ((deltaX * axisX + deltaY * axisY) / axisLength / getPreviewPixelsPerGrid());
		switch (axis) {
			case X -> setActiveOffsetX(clamp(offsetX + deltaUnits, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX));
			case Y -> setActiveOffsetY(clamp(offsetY + deltaUnits, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX));
			case Z -> setActiveOffsetZ(clamp(offsetZ + deltaUnits, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX));
			case NONE -> {
			}
		}
	}

	private void dragModelRotation(RotationAxis axis, double mouseX, double mouseY, double deltaX, double deltaY) {
		ScreenPoint center = projectModelPoint(getActiveOffsetX(), getActiveOffsetY(), getActiveOffsetZ());
		double previousAngle = Math.atan2(mouseY - deltaY - center.y, mouseX - deltaX - center.x);
		double currentAngle = Math.atan2(mouseY - center.y, mouseX - center.x);
		float degrees = -normalizeDegrees((float) Math.toDegrees(currentAngle - previousAngle));
		switch (axis) {
			case PITCH -> setActivePitch(clamp(getActivePitch() - degrees, -180.0F, 180.0F));
			case YAW -> setActiveYaw(normalizeDegrees(getActiveYaw() + degrees));
			case ROLL -> setActiveRoll(normalizeDegrees(getActiveRoll() - degrees));
			case NONE -> {
			}
		}
	}

	private ScreenPoint projectModelPoint(float x, float y, float z) {
		float offsetX = getActiveOffsetX();
		float offsetY = getActiveOffsetY();
		float offsetZ = getActiveOffsetZ();
		float localX = x - offsetX;
		float localY = y - offsetY;
		float localZ = z - offsetZ;

		float yawRadians = (float) Math.toRadians(-getActiveYaw());
		float yawCos = (float) Math.cos(yawRadians);
		float yawSin = (float) Math.sin(yawRadians);
		float yawX = localX * yawCos + localZ * yawSin;
		float yawZ = localZ * yawCos - localX * yawSin;

		float pitchRadians = (float) Math.toRadians(getActivePitch());
		float pitchCos = (float) Math.cos(pitchRadians);
		float pitchSin = (float) Math.sin(pitchRadians);
		float pitchY = localY * pitchCos - yawZ * pitchSin;
		float pitchZ = yawZ * pitchCos + localY * pitchSin;
		float pitchX = yawX;

		float rollRadians = (float) Math.toRadians(getActiveRoll());
		float rollCos = (float) Math.cos(rollRadians);
		float rollSin = (float) Math.sin(rollRadians);
		float rollX = pitchX * rollCos - pitchY * rollSin;
		float rollY = pitchX * rollSin + pitchY * rollCos;
		float rollZ = pitchZ;

		return projectPreviewPoint(offsetX + rollX, offsetY + rollY, offsetZ + rollZ);
	}

	private double distanceToSegment(double mouseX, double mouseY, ScreenPoint start, ScreenPoint end) {
		double lineX = end.x - start.x;
		double lineY = end.y - start.y;
		double lengthSquared = lineX * lineX + lineY * lineY;
		if (lengthSquared < 0.001D) {
			double dx = mouseX - start.x;
			double dy = mouseY - start.y;
			return Math.sqrt(dx * dx + dy * dy);
		}
		double t = ((mouseX - start.x) * lineX + (mouseY - start.y) * lineY) / lengthSquared;
		t = Math.max(0.0D, Math.min(1.0D, t));
		double closestX = start.x + t * lineX;
		double closestY = start.y + t * lineY;
		double dx = mouseX - closestX;
		double dy = mouseY - closestY;
		return Math.sqrt(dx * dx + dy * dy);
	}

	private float getPreviewPixelsPerGrid() {
		int width = getPreviewRight() - getPreviewLeft() - 20;
		int height = getPreviewBottom() - getPreviewTop() - 58;
		return getPreviewBaseScale(width, height) * this.previewZoom;
	}

	private static float getPreviewBaseScale(int width, int height) {
		return Math.max(24.0F, Math.min(width, height) * PREVIEW_SCALE_FACTOR);
	}

	private float getPreviewRenderCenterX() {
		return (getPreviewLeft() + 10 + getPreviewRight() - 10) * 0.5F + this.previewPanX;
	}

	private float getPreviewRenderCenterY() {
		return (getPreviewTop() + 48 + getPreviewBottom() - 10) * 0.5F + this.previewPanY;
	}

	private void renderEditorItemPreviews(DrawContext context) {
		for (int i = 0; i < EDITOR_ITEMS.size(); i++) {
			EditorItemModel item = EDITOR_ITEMS.get(i);
			ScreenPoint point = projectPreviewPoint(item.offsetX, item.offsetY, item.offsetZ);
			int x = Math.round(point.x) - 8;
			int y = Math.round(point.y) - 8;
			context.drawItem(item.stack, x, y);
			context.drawBorder(x - 1, y - 1, 18, 18, i == this.selectedEditorItemIndex ? 0xFFFFFF55 : 0x99FFFFFF);
		}
	}

	private ScreenPoint projectPreviewPoint(float x, float y, float z) {
		float yawRadians = (float) Math.toRadians(-this.previewYaw);
		float yawCos = (float) Math.cos(yawRadians);
		float yawSin = (float) Math.sin(yawRadians);
		float yawX = x * yawCos + z * yawSin;
		float yawZ = z * yawCos - x * yawSin;
		float pitchRadians = (float) Math.toRadians(this.previewPitch);
		float pitchCos = (float) Math.cos(pitchRadians);
		float pitchSin = (float) Math.sin(pitchRadians);
		float pitchY = y * pitchCos - yawZ * pitchSin;
		float pitchX = yawX;
		float rollRadians = (float) Math.toRadians(this.previewRoll);
		float rollCos = (float) Math.cos(rollRadians);
		float rollSin = (float) Math.sin(rollRadians);
		float rollX = pitchX * rollCos - pitchY * rollSin;
		float rollY = pitchX * rollSin + pitchY * rollCos;
		return new ScreenPoint(getPreviewRenderCenterX() + rollX * getPreviewPixelsPerGrid(), getPreviewRenderCenterY() + rollY * getPreviewPixelsPerGrid());
	}

	private ScreenPoint getPlayerModelScreenOffset() {
		ScreenPoint origin = projectPreviewPoint(0.0F, 0.0F, 0.0F);
		ScreenPoint modelPoint = projectPreviewPoint(modelOffsetX, modelOffsetY, modelOffsetZ);
		return new ScreenPoint(modelPoint.x - origin.x, modelPoint.y - origin.y);
	}

	private int findPreviewItem(double mouseX, double mouseY) {
		for (int i = EDITOR_ITEMS.size() - 1; i >= 0; i--) {
			EditorItemModel item = EDITOR_ITEMS.get(i);
			ScreenPoint point = projectPreviewPoint(item.offsetX, item.offsetY, item.offsetZ);
			if (mouseX >= point.x - 10 && mouseX <= point.x + 10 && mouseY >= point.y - 10 && mouseY <= point.y + 10) {
				return i;
			}
		}
		return -1;
	}

	private void renderItemList(DrawContext context, int mouseX, int mouseY) {
		if (!this.itemListOpen) {
			return;
		}
		List<ItemStack> stacks = getAvailableItemStacks();
		clampItemListScroll(stacks.size());
		int x = getItemSelectorX();
		int y = getItemListTop();
		int width = getItemSelectorWidth();
		int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, stacks.size()));
		int height = visibleRows * ITEM_LIST_ITEM_HEIGHT + 2;
		context.fill(x, y, x + width, y + height, 0xF05E6876);
		context.drawBorder(x, y, width, height, 0xFFFFFFFF);
		if (stacks.isEmpty()) {
			context.drawTextWithShadow(this.textRenderer, "无可选物品", x + 6, y + 6, 0xFFE2E8F0);
			return;
		}
		context.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
		try {
			for (int row = 0; row < visibleRows; row++) {
				int index = this.itemListScroll + row;
				if (index >= stacks.size()) {
					break;
				}
				ItemStack stack = stacks.get(index);
				int rowTop = y + 1 + row * ITEM_LIST_ITEM_HEIGHT;
				boolean hovered = mouseX >= x + 1 && mouseX <= x + width - 1
						&& mouseY >= rowTop && mouseY < rowTop + ITEM_LIST_ITEM_HEIGHT;
				context.fill(x + 1, rowTop, x + width - 1, rowTop + ITEM_LIST_ITEM_HEIGHT, hovered ? 0xE08796AA : (row % 2 == 0 ? 0xE0727D8C : 0xE0677180));
				context.drawItem(stack, x + 3, rowTop + 1);
				context.drawTextWithShadow(this.textRenderer, stack.getName().getString(), x + 23, rowTop + 5, 0xFFE2E8F0);
			}
		} finally {
			context.disableScissor();
		}
	}

	private boolean selectItemFromList(double mouseX, double mouseY) {
		if (!isInsideItemList(mouseX, mouseY)) {
			return false;
		}
		List<ItemStack> stacks = getAvailableItemStacks();
		if (stacks.isEmpty()) {
			return true;
		}
		int row = (int) ((mouseY - getItemListTop() - 1) / ITEM_LIST_ITEM_HEIGHT);
		int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, stacks.size()));
		if (row < 0 || row >= visibleRows) {
			return true;
		}
		int index = this.itemListScroll + row;
		if (index >= stacks.size()) {
			return true;
		}
		EditorItemModel model = new EditorItemModel(stacks.get(index).copyWithCount(1));
		EDITOR_ITEMS.add(model);
		this.selectedEditorItemIndex = EDITOR_ITEMS.size() - 1;
		this.itemListOpen = false;
		refreshButtonLabels();
		return true;
	}

	private List<ItemStack> getAvailableItemStacks() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) {
			return List.of();
		}
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < client.player.getInventory().size(); i++) {
			ItemStack stack = client.player.getInventory().getStack(i);
			if (!stack.isEmpty()) {
				addItemOption(stacks, stack.copyWithCount(1));
			}
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
			if (existing.isOf(stack.getItem())) {
				return;
			}
		}
		stacks.add(stack);
	}

	private boolean isInsideItemSelector(double mouseX, double mouseY) {
		return mouseX >= getItemSelectorX()
				&& mouseX <= getItemSelectorX() + getItemSelectorWidth()
				&& mouseY >= getItemSelectorY()
				&& mouseY <= getItemSelectorY() + 20;
	}

	private boolean isInsideItemList(double mouseX, double mouseY) {
		List<ItemStack> stacks = getAvailableItemStacks();
		int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, stacks.size()));
		int height = visibleRows * ITEM_LIST_ITEM_HEIGHT + 2;
		int x = getItemSelectorX();
		int y = getItemListTop();
		return mouseX >= x && mouseX <= x + getItemSelectorWidth()
				&& mouseY >= y && mouseY <= y + height;
	}

	private int getItemSelectorX() {
		return getPreviewLeft() + 8;
	}

	private int getItemSelectorY() {
		return getPreviewTop() + 8;
	}

	private int getItemListTop() {
		return getItemSelectorY() + 22;
	}

	private int getItemSelectorWidth() {
		return Math.min(ITEM_SELECTOR_WIDTH, Math.max(80, getPreviewRight() - getPreviewLeft() - 16));
	}

	private void clampItemListScroll(int itemCount) {
		int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, itemCount));
		int maxScroll = Math.max(0, itemCount - visibleRows);
		this.itemListScroll = Math.max(0, Math.min(maxScroll, this.itemListScroll));
	}

	private boolean hasSelectedItemModel() {
		return this.selectedEditorItemIndex >= 0 && this.selectedEditorItemIndex < EDITOR_ITEMS.size();
	}

	private String getSelectedItemLabel() {
		return hasSelectedItemModel() ? EDITOR_ITEMS.get(this.selectedEditorItemIndex).stack.getName().getString() : "玩家模型";
	}

	private String getActiveModelLabel() {
		return hasSelectedItemModel() ? "Item " + (this.selectedEditorItemIndex + 1) : "Player";
	}

	private void clearSelectedItemModel() {
		if (!hasSelectedItemModel()) {
			return;
		}
		EDITOR_ITEMS.remove(this.selectedEditorItemIndex);
		this.selectedEditorItemIndex = Math.min(this.selectedEditorItemIndex, EDITOR_ITEMS.size() - 1);
		refreshButtonLabels();
	}

	private void clearAllItemModels() {
		EDITOR_ITEMS.clear();
		this.selectedEditorItemIndex = -1;
		refreshButtonLabels();
	}

	private void resetActiveTransform() {
		if (hasSelectedItemModel()) {
			EditorItemModel item = EDITOR_ITEMS.get(this.selectedEditorItemIndex);
			item.offsetX = 0.0F;
			item.offsetY = 0.0F;
			item.offsetZ = 0.0F;
			item.pitch = 0.0F;
			item.yaw = 0.0F;
			item.roll = 0.0F;
		} else {
			modelOffsetX = 0.0F;
			modelOffsetY = 0.0F;
			modelOffsetZ = 0.0F;
			modelPitch = 0.0F;
			modelYaw = 0.0F;
			modelRoll = 0.0F;
		}
	}

	private void adjustActiveOffset(MoveAxis axis, float amount) {
		switch (axis) {
			case X -> setActiveOffsetX(clamp(getActiveOffsetX() + amount, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX));
			case Y -> setActiveOffsetY(clamp(getActiveOffsetY() + amount, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX));
			case Z -> setActiveOffsetZ(clamp(getActiveOffsetZ() + amount, MODEL_OFFSET_MIN, MODEL_OFFSET_MAX));
			case NONE -> {
			}
		}
	}

	private void adjustActiveRotation(Axis axis, float amount) {
		switch (axis) {
			case PITCH -> setActivePitch(clamp(getActivePitch() + amount, -180.0F, 180.0F));
			case YAW -> setActiveYaw(normalizeDegrees(getActiveYaw() + amount));
			case ROLL -> setActiveRoll(normalizeDegrees(getActiveRoll() + amount));
		}
	}

	private static Axis toPoseAxis(RotationAxis axis) {
		return switch (axis) {
			case PITCH -> Axis.PITCH;
			case YAW -> Axis.YAW;
			case ROLL -> Axis.ROLL;
			case NONE -> Axis.YAW;
		};
	}

	private float getActiveOffsetX() {
		return hasSelectedItemModel() ? EDITOR_ITEMS.get(this.selectedEditorItemIndex).offsetX : modelOffsetX;
	}

	private float getActiveOffsetY() {
		return hasSelectedItemModel() ? EDITOR_ITEMS.get(this.selectedEditorItemIndex).offsetY : modelOffsetY;
	}

	private float getActiveOffsetZ() {
		return hasSelectedItemModel() ? EDITOR_ITEMS.get(this.selectedEditorItemIndex).offsetZ : modelOffsetZ;
	}

	private float getActivePitch() {
		return hasSelectedItemModel() ? EDITOR_ITEMS.get(this.selectedEditorItemIndex).pitch : modelPitch;
	}

	private float getActiveYaw() {
		return hasSelectedItemModel() ? EDITOR_ITEMS.get(this.selectedEditorItemIndex).yaw : modelYaw;
	}

	private float getActiveRoll() {
		return hasSelectedItemModel() ? EDITOR_ITEMS.get(this.selectedEditorItemIndex).roll : modelRoll;
	}

	private void setActiveOffsetX(float value) {
		if (hasSelectedItemModel()) {
			EDITOR_ITEMS.get(this.selectedEditorItemIndex).offsetX = value;
		} else {
			modelOffsetX = value;
		}
	}

	private void setActiveOffsetY(float value) {
		if (hasSelectedItemModel()) {
			EDITOR_ITEMS.get(this.selectedEditorItemIndex).offsetY = value;
		} else {
			modelOffsetY = value;
		}
	}

	private void setActiveOffsetZ(float value) {
		if (hasSelectedItemModel()) {
			EDITOR_ITEMS.get(this.selectedEditorItemIndex).offsetZ = value;
		} else {
			modelOffsetZ = value;
		}
	}

	private void setActivePitch(float value) {
		if (hasSelectedItemModel()) {
			EDITOR_ITEMS.get(this.selectedEditorItemIndex).pitch = value;
		} else {
			modelPitch = value;
		}
	}

	private void setActiveYaw(float value) {
		if (hasSelectedItemModel()) {
			EDITOR_ITEMS.get(this.selectedEditorItemIndex).yaw = value;
		} else {
			modelYaw = value;
		}
	}

	private void setActiveRoll(float value) {
		if (hasSelectedItemModel()) {
			EDITOR_ITEMS.get(this.selectedEditorItemIndex).roll = value;
		} else {
			modelRoll = value;
		}
	}

	private void renderPlayerList(DrawContext context, int mouseX, int mouseY) {
		if (!this.playerListOpen) {
			return;
		}
		List<PlayerListEntry> entries = getPlayerEntries();
		clampPlayerListScroll(entries.size());
		int x = getPlayerSelectorX();
		int y = getPlayerListTop();
		int visibleRows = Math.min(PLAYER_LIST_VISIBLE_ROWS, Math.max(1, entries.size()));
		int height = visibleRows * PLAYER_LIST_ITEM_HEIGHT + 2;
		context.fill(x, y, x + PLAYER_SELECTOR_WIDTH, y + height, 0xF05E6876);
		context.drawBorder(x, y, PLAYER_SELECTOR_WIDTH, height, 0xFFFFFFFF);
		if (entries.isEmpty()) {
			context.drawTextWithShadow(this.textRenderer, "当前没有玩家", x + 6, y + 6, 0xFFE2E8F0);
			return;
		}

		context.enableScissor(x + 1, y + 1, x + PLAYER_SELECTOR_WIDTH - 1, y + height - 1);
		try {
			for (int row = 0; row < visibleRows; row++) {
				int index = this.playerListScroll + row;
				if (index >= entries.size()) {
					break;
				}
				PlayerListEntry entry = entries.get(index);
				String name = getPlayerName(entry);
				int rowTop = y + 1 + row * PLAYER_LIST_ITEM_HEIGHT;
				boolean hovered = mouseX >= x + 1 && mouseX <= x + PLAYER_SELECTOR_WIDTH - 1
						&& mouseY >= rowTop && mouseY < rowTop + PLAYER_LIST_ITEM_HEIGHT;
				boolean selected = selectedSkinSource == SkinSource.PLAYER && name.equals(selectedPlayerName);
				int rowColor = selected ? 0xF04F8FDB : (row % 2 == 0 ? 0xE0727D8C : 0xE0677180);
				if (hovered && !selected) {
					rowColor = 0xE08796AA;
				}
				context.fill(x + 1, rowTop, x + PLAYER_SELECTOR_WIDTH - 1, rowTop + PLAYER_LIST_ITEM_HEIGHT, rowColor);
				context.fill(x + 1, rowTop + PLAYER_LIST_ITEM_HEIGHT - 1, x + PLAYER_SELECTOR_WIDTH - 1, rowTop + PLAYER_LIST_ITEM_HEIGHT, 0x55FFFFFF);
				int color = selected ? 0xFFFFFFFF : 0xFFE2E8F0;
				String label = (selected ? "> " : "  ") + name;
				context.drawTextWithShadow(this.textRenderer, label, x + 5, rowTop + 5, color);
			}
		} finally {
			context.disableScissor();
		}

		if (entries.size() > visibleRows) {
			int trackX = x + PLAYER_SELECTOR_WIDTH - 5;
			int trackTop = y + 3;
			int trackHeight = height - 6;
			int thumbHeight = Math.max(12, trackHeight * visibleRows / entries.size());
			int maxScroll = entries.size() - visibleRows;
			int thumbTop = trackTop + (maxScroll == 0 ? 0 : (trackHeight - thumbHeight) * this.playerListScroll / maxScroll);
			context.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, 0x55303030);
			context.fill(trackX, thumbTop, trackX + 2, thumbTop + thumbHeight, 0xCCB8B8B8);
		}
	}

	private boolean selectPlayerFromList(double mouseX, double mouseY) {
		if (!isInsidePlayerList(mouseX, mouseY)) {
			return false;
		}
		List<PlayerListEntry> entries = getPlayerEntries();
		if (entries.isEmpty()) {
			return true;
		}
		int row = (int) ((mouseY - getPlayerListTop() - 1) / PLAYER_LIST_ITEM_HEIGHT);
		int visibleRows = Math.min(PLAYER_LIST_VISIBLE_ROWS, Math.max(1, entries.size()));
		if (row < 0 || row >= visibleRows) {
			return true;
		}
		int index = this.playerListScroll + row;
		if (index >= entries.size()) {
			return true;
		}
		PlayerListEntry entry = entries.get(index);
		selectedPlayerName = getPlayerName(entry);
		selectedSkinSource = SkinSource.PLAYER;
		slimModel = entry.getSkinTextures().model() == SkinTextures.Model.SLIM;
		this.playerListOpen = false;
		refreshButtonLabels();
		return true;
	}

	private List<PlayerListEntry> getPlayerEntries() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getNetworkHandler() == null) {
			return List.of();
		}
		List<PlayerListEntry> entries = new ArrayList<>(client.getNetworkHandler().getPlayerList());
		entries.sort((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(getPlayerName(left), getPlayerName(right)));
		return entries;
	}

	private static List<String> getLocalSkins() {
		List<String> skins = new ArrayList<>();
		for (String skin : BodyModelSelectionCatalog.LOCAL_SKINS) {
			addLocalSkin(skins, skin);
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null) {
			for (Identifier id : client.getResourceManager().findResources("textures/local_skin", resourceId ->
					resourceId.getNamespace().equals("monvhua") && resourceId.getPath().endsWith(".png")).keySet()) {
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

	private PlayerListEntry getSelectedPlayerEntry() {
		if (selectedSkinSource != SkinSource.PLAYER || selectedPlayerName.isBlank()) {
			return null;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getNetworkHandler() == null) {
			return null;
		}
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

	private void clampPlayerListScroll(int entryCount) {
		int visibleRows = Math.min(PLAYER_LIST_VISIBLE_ROWS, Math.max(1, entryCount));
		int maxScroll = Math.max(0, entryCount - visibleRows);
		this.playerListScroll = Math.max(0, Math.min(maxScroll, this.playerListScroll));
	}

	private int getPlayerSelectorX() {
		return 18;
	}

	private int getPlayerSelectorY() {
		return 38;
	}

	private boolean isInsidePlayerSelector(double mouseX, double mouseY) {
		return mouseX >= getPlayerSelectorX()
				&& mouseX <= getPlayerSelectorX() + PLAYER_SELECTOR_WIDTH
				&& mouseY >= getPlayerSelectorY()
				&& mouseY <= getPlayerSelectorY() + 20;
	}

	private int getPlayerListTop() {
		return getPlayerSelectorY() + 22;
	}

	private boolean isInsidePlayerList(double mouseX, double mouseY) {
		List<PlayerListEntry> entries = getPlayerEntries();
		int visibleRows = Math.min(PLAYER_LIST_VISIBLE_ROWS, Math.max(1, entries.size()));
		int height = visibleRows * PLAYER_LIST_ITEM_HEIGHT + 2;
		int x = getPlayerSelectorX();
		int y = getPlayerListTop();
		return mouseX >= x && mouseX <= x + PLAYER_SELECTOR_WIDTH
				&& mouseY >= y && mouseY <= y + height;
	}

	private static Map<String, PartPose> createPartPoses() {
		Map<String, PartPose> poses = new HashMap<>();
		for (String part : BodyModelSelectionCatalog.PARTS) {
			if (!part.equals("all")) {
				poses.put(part, new PartPose());
			}
		}
		return poses;
	}

	private static Map<String, PartPose> getActivePoseMap() {
		return poseEditMode == PoseEditMode.SKELETAL ? SKELETAL_POSES : PART_POSES;
	}

	private static PartPose getSelectedPose() {
		return getActivePoseMap().computeIfAbsent(selectedPart, ignored -> new PartPose());
	}

	private static void adjustSelectedPose(Axis axis, float amount) {
		if (selectedPart.equals("all")) {
			return;
		}
		PartPose pose = getSelectedPose();
		switch (axis) {
			case PITCH -> pose.pitch += amount;
			case YAW -> pose.yaw += amount;
			case ROLL -> pose.roll += amount;
		}
	}

	private static void adjustSelectedBend(Axis axis, float amount) {
		if (!hasSelectedBendControls()) {
			return;
		}
		PartPose pose = getSelectedPose();
		switch (axis) {
			case PITCH -> pose.bendPitch = clamp(pose.bendPitch + amount, -180.0F, 180.0F);
			case YAW -> pose.bendYaw = normalizeDegrees(pose.bendYaw + amount);
			case ROLL -> pose.bendRoll = normalizeDegrees(pose.bendRoll + amount);
		}
	}

	private static boolean hasSelectedBendControls() {
		return poseEditMode == PoseEditMode.SKELETAL && switch (selectedPart) {
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

	private static void resetSelectedPose() {
		if (selectedPart.equals("all")) {
			return;
		}
		PartPose pose = getSelectedPose();
		pose.pitch = 0.0F;
		pose.yaw = 0.0F;
		pose.roll = 0.0F;
		pose.bendPitch = 0.0F;
		pose.bendYaw = 0.0F;
		pose.bendRoll = 0.0F;
		pose.scale = 1.0F;
	}

	private static String formatDegrees(float value) {
		return String.format("%.0f", value);
	}

	private static String formatOffset(float value) {
		return String.format("%.2f", value);
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float normalizeDegrees(float value) {
		while (value > 180.0F) {
			value -= 360.0F;
		}
		while (value < -180.0F) {
			value += 360.0F;
		}
		return value;
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
		if (pose == null) {
			return;
		}
		values[offset] = pose.pitch;
		values[offset + 1] = pose.yaw;
		values[offset + 2] = pose.roll;
		values[offset + 3] = pose.scale;
	}

	private static void writeBend(float[] values, int offset, PartPose pose) {
		if (pose == null) {
			return;
		}
		values[offset] = pose.bendPitch;
		values[offset + 1] = pose.bendYaw;
		values[offset + 2] = pose.bendRoll;
	}

	private enum Axis {
		PITCH,
		YAW,
		ROLL
	}

	private enum MoveAxis {
		NONE,
		X,
		Y,
		Z
	}

	private enum RotationAxis {
		NONE,
		PITCH,
		YAW,
		ROLL
	}

	private enum PoseEditMode {
		STATIC_PART,
		SKELETAL
	}

	public enum PreviewMode {
		FOLLOW_PLAYER,
		FIXED
	}

	private enum SkinSource {
		LOCAL,
		PLAYER
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

	private static final class EditorItemModel {
		private final ItemStack stack;
		private float offsetX;
		private float offsetY;
		private float offsetZ;
		private float pitch;
		private float yaw;
		private float roll;

		private EditorItemModel(ItemStack stack) {
			this.stack = stack;
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
