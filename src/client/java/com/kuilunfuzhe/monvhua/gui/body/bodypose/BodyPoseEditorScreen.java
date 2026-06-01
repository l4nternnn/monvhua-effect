package com.kuilunfuzhe.monvhua.gui.body.bodypose;

import com.kuilunfuzhe.monvhua.features.block.body.BodyModelSelectionCatalog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePoseEditorItemsC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePosedBodyC2SPacket;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BodyPoseEditorScreen extends Screen {
	private static final float ROTATION_STEP_DEGREES = 5.0F;
	private static final float PREVIEW_CHEST_PIVOT_Y = 6.0F;
	private static final float PREVIEW_Y_PIVOT = 1.601F;
	private static final float MODEL_PART_UNITS_PER_GRID = 16.0F;
	private static final float MODEL_OFFSET_MIN = -10.0F;
	private static final float MODEL_OFFSET_MAX = 10.0F;
	private static final float MOVE_AXIS_LENGTH = 2.0F / 3.0F;
	private static final float MOVE_AXIS_HIT_RADIUS = 3.4F;
	private static final float ROTATION_RING_RADIUS = 2.45F / 3.0F;
	private static final float ROTATION_RING_HIT_RADIUS = 3.0F;
	private static final int ROTATION_RING_SEGMENTS = 48;
	private static final int PLAYER_LIST_ITEM_HEIGHT = 18;
	private static final int PLAYER_LIST_VISIBLE_ROWS = 6;
	private static final int PLAYER_SELECTOR_WIDTH = 152;
	private static final int ITEM_LIST_ITEM_HEIGHT = 18;
	private static final int ITEM_LIST_VISIBLE_ROWS = 8;
	private static final int ITEM_SELECTOR_WIDTH = 150;
	private static String selectedSkin = BodyModelSelectionCatalog.LOCAL_SKINS[0];
	private static String selectedPlayerName = "";
	private static SkinSource selectedSkinSource = SkinSource.LOCAL;
	private static String selectedPart = BodyModelSelectionCatalog.PARTS[0];
	private static boolean slimModel = true;
	private static float modelOffsetX;
	private static float modelOffsetY;
	private static float modelOffsetZ;
	private static final List<EditorItemModel> EDITOR_ITEMS = new ArrayList<>();
	private static final Map<String, PartPose> PART_POSES = createPartPoses();

	private final List<ButtonWidget> skinButtons = new ArrayList<>();
	private final List<ButtonWidget> partButtons = new ArrayList<>();
	private final List<ButtonWidget> poseButtons = new ArrayList<>();
	private ButtonWidget modelButton;
	private ButtonWidget runCommandButton;
	private ButtonWidget placeButton;
	private ButtonWidget showWholeButton;
	private ButtonWidget playerButton;
	private ButtonWidget itemButton;
	private ButtonWidget placeItemsButton;
	private ButtonWidget clearSelectedItemButton;
	private ButtonWidget clearAllItemsButton;
	private ButtonWidget resetTransformButton;
	private PlayerEntityModel defaultPreviewModel;
	private PlayerEntityModel slimPreviewModel;
	private float previewPitch = 24.0F;
	private float previewYaw = 0.0F;
	private float previewRoll = 0.0F;
	private float previewZoom = 1.0F;
	private boolean showWholePreview = true;
	private boolean draggingPreview;
	private boolean draggingRightPreview;
	private float previewPanX;
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

		int rightX = this.width - 178;
		this.modelButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> {
			slimModel = !slimModel;
			refreshButtonLabels();
		}).size(150, 20).position(rightX, panelTop).build());

		int partY = panelTop + 34;
		for (int i = 0; i < BodyModelSelectionCatalog.PARTS.length; i++) {
			String part = BodyModelSelectionCatalog.PARTS[i];
			ButtonWidget button = ButtonWidget.builder(Text.literal(part), pressed -> {
				selectedPart = part;
				refreshButtonLabels();
			}).size(150, 18).position(rightX, partY + i * 22).build();
			this.partButtons.add(this.addDrawableChild(button));
		}

		int poseY = getPoseControlsY(panelTop);
		addPoseButton(rightX + 58, poseY, "-俯仰", () -> adjustSelectedPose(Axis.PITCH, -ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 104, poseY, "+俯仰", () -> adjustSelectedPose(Axis.PITCH, ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 58, poseY + 24, "-偏转", () -> adjustSelectedPose(Axis.YAW, -ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 104, poseY + 24, "+偏转", () -> adjustSelectedPose(Axis.YAW, ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 58, poseY + 48, "-R", () -> adjustSelectedPose(Axis.ROLL, -ROTATION_STEP_DEGREES));
		addPoseButton(rightX + 104, poseY + 48, "+R", () -> adjustSelectedPose(Axis.ROLL, ROTATION_STEP_DEGREES));
		addPoseButton(rightX, poseY + 76, "重置", BodyPoseEditorScreen::resetSelectedPose, 72);

		this.runCommandButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> runGiveCommand())
				.size(72, 18)
				.position(rightX + 78, poseY + 76)
				.build());

		this.placeButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> placePosedBody())
				.size(150, 20)
				.position(rightX, poseY + 102)
				.build());

		this.itemButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> {
					this.itemListOpen = !this.itemListOpen;
					clampItemListScroll(getAvailableItemStacks().size());
					refreshButtonLabels();
				})
				.size(ITEM_SELECTOR_WIDTH, 20)
				.position(rightX, poseY + 126)
				.build());

		this.placeItemsButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> placeEditorItems())
				.size(150, 18)
				.position(rightX, poseY + 150)
				.build());

		this.clearSelectedItemButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> clearSelectedItemModel())
				.size(72, 18)
				.position(rightX, poseY + 172)
				.build());

		this.clearAllItemsButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> clearAllItemModels())
				.size(74, 18)
				.position(rightX + 76, poseY + 172)
				.build());

		this.resetTransformButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), pressed -> resetActiveTransform())
				.size(150, 18)
				.position(rightX, poseY + 194)
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
			this.modelButton.setMessage(Text.literal("Model: " + (slimModel ? "slim" : "default-默认")));
		}
		if (this.runCommandButton != null) {
			this.runCommandButton.setMessage(Text.literal("给予肢体"));
		}
		if (this.placeButton != null) {
			this.placeButton.setMessage(Text.literal("放置模型"));
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
		if (selectedSkinSource == SkinSource.PLAYER && !selectedPlayerName.isBlank()) {
			command = "clairvoyance-肢体|获取 @s " + selectedPlayerName + " " + selectedPart;
		} else {
			command = "clairvoyance-肢体|获取 @s localskin " + selectedSkin + " " + selectedPart;
		}
		if (slimModel && (selectedSkinSource != SkinSource.PLAYER || !selectedPart.equals("head"))) {
			command += " slim";
		}
		this.client.player.networkHandler.sendChatCommand(command);
	}

	private void placePosedBody() {
		ClientPlayNetworking.send(new PlacePosedBodyC2SPacket(selectedSkin, slimModel, createPoseValueArray(),
				selectedSkinSource == SkinSource.PLAYER, selectedPlayerName,
				modelOffsetX, modelOffsetY, modelOffsetZ,
				this.previewPitch, this.previewYaw, this.previewRoll));
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
		context.drawTextWithShadow(this.textRenderer, "Selection", this.width - 178, 26, 0xD8D8D8);
		renderPoseReadout(context, this.width - 178, getPoseControlsY(38));

		context.fill(previewLeft, previewTop, previewRight, previewBottom, 0x66000000);
		context.drawBorder(previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop, 0x88FFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, getSelectedSkinLabel() + " / " + (slimModel ? "slim" : "default"), (previewLeft + previewRight) / 2, previewTop + 16, 0xFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, selectedPart, (previewLeft + previewRight) / 2, previewTop + 30, 0xB8B8B8);
		this.hoveredMoveAxis = this.draggingMoveAxis == MoveAxis.NONE ? findMoveAxis(mouseX, mouseY) : this.draggingMoveAxis;
		this.hoveredRotationAxis = this.draggingRotationAxis == RotationAxis.NONE ? findRotationRing(mouseX, mouseY) : this.draggingRotationAxis;
		renderModelOffsetReadout(context, previewLeft + 8, previewTop + 8);
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
				this.draggingRotationAxis = rotationAxis;
				this.hoveredRotationAxis = rotationAxis;
				return true;
			}
			MoveAxis moveAxis = findMoveAxis(mouseX, mouseY);
			if (moveAxis != MoveAxis.NONE) {
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
		if (button == 0 && this.draggingPreview) {
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
			this.previewZoom = clamp(this.previewZoom + (float) verticalAmount * 0.1F, 0.45F, 2.25F);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_DELETE) {
			clearSelectedItemModel();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
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
	}

	private void renderPlayerPreview(DrawContext context, int x1, int y1, int x2, int y2) {
		PlayerEntityModel model = getPreviewModel();
		if (model == null) {
			return;
		}

		preparePreviewModel(model);
		int width = x2 - x1;
		int height = y2 - y1;
		float scale = Math.max(24.0F, Math.min(width, height) * 0.42F) * this.previewZoom;
		int renderBottom = Math.max(y1 + 1, Math.round((y1 + y2) * 0.5F + scale * PREVIEW_Y_PIVOT));
		int panX = (int) this.previewPanX;
		int panY = (int) this.previewPanY;
		Identifier texture = getPreviewTexture();
		context.enableScissor(x1, y1, x2, y2);
		try {
			context.addPlayerSkin(model, texture, scale, 0.0F, 0.0F, PREVIEW_Y_PIVOT, x1 + panX, y1 + panY, x2 + panX, renderBottom + panY);
		} finally {
			context.disableScissor();
		}
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

	private PlayerEntityModel getPreviewModel() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return null;
		}
		if (slimModel) {
			if (this.slimPreviewModel == null) {
				this.slimPreviewModel = new PlayerEntityModel(client.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER_SLIM), true);
			}
			return this.slimPreviewModel;
		}
		if (this.defaultPreviewModel == null) {
			this.defaultPreviewModel = new PlayerEntityModel(client.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
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
		root.originY = 0.0F;
		root.pitch = (float) Math.toRadians(this.previewPitch);
		root.yaw = (float) Math.toRadians(-this.previewYaw);
		root.roll = (float) Math.toRadians(this.previewRoll);

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

		model.rightArm.pitch = -0.08F;
		model.rightArm.roll = 0.08F;
		model.leftArm.pitch = -0.08F;
		model.leftArm.roll = -0.08F;
		model.rightLeg.pitch = 0.04F;
		model.leftLeg.pitch = -0.04F;

		applyPose(model.head, PART_POSES.get("head"));
		applyPose(model.body, PART_POSES.get("torso"));
		applyPose(model.leftArm, PART_POSES.get("left_arm"));
		applyPose(model.rightArm, PART_POSES.get("right_arm"));
		applyPose(model.leftLeg, PART_POSES.get("left_leg"));
		applyPose(model.rightLeg, PART_POSES.get("right_leg"));
	}

	private static void centerSelectedPart(PlayerEntityModel model) {
		float offsetX = modelOffsetX * MODEL_PART_UNITS_PER_GRID;
		float offsetY = modelOffsetY * MODEL_PART_UNITS_PER_GRID;
		float offsetZ = modelOffsetZ * MODEL_PART_UNITS_PER_GRID;
		switch (selectedPart) {
			case "head" -> movePart(model.head, offsetX, 12.0F + offsetY, offsetZ);
			case "torso" -> movePart(model.body, offsetX, 2.0F + offsetY, offsetZ);
			case "left_arm" -> movePart(model.leftArm, offsetX, 4.0F + offsetY, offsetZ);
			case "right_arm" -> movePart(model.rightArm, offsetX, 4.0F + offsetY, offsetZ);
			case "left_leg" -> movePart(model.leftLeg, offsetX, 2.0F + offsetY, offsetZ);
			case "right_leg" -> movePart(model.rightLeg, offsetX, 2.0F + offsetY, offsetZ);
		}
	}

	private static void moveWholeModelToChestPivot(PlayerEntityModel model) {
		float offsetX = modelOffsetX * MODEL_PART_UNITS_PER_GRID;
		float offsetY = modelOffsetY * MODEL_PART_UNITS_PER_GRID;
		float offsetZ = modelOffsetZ * MODEL_PART_UNITS_PER_GRID;
		movePart(model.head, model.head.originX + offsetX, model.head.originY - PREVIEW_CHEST_PIVOT_Y + offsetY, model.head.originZ + offsetZ);
		movePart(model.body, model.body.originX + offsetX, model.body.originY - PREVIEW_CHEST_PIVOT_Y + offsetY, model.body.originZ + offsetZ);
		movePart(model.leftArm, model.leftArm.originX + offsetX, model.leftArm.originY - PREVIEW_CHEST_PIVOT_Y + offsetY, model.leftArm.originZ + offsetZ);
		movePart(model.rightArm, model.rightArm.originX + offsetX, model.rightArm.originY - PREVIEW_CHEST_PIVOT_Y + offsetY, model.rightArm.originZ + offsetZ);
		movePart(model.leftLeg, model.leftLeg.originX + offsetX, model.leftLeg.originY - PREVIEW_CHEST_PIVOT_Y + offsetY, model.leftLeg.originZ + offsetZ);
		movePart(model.rightLeg, model.rightLeg.originX + offsetX, model.rightLeg.originY - PREVIEW_CHEST_PIVOT_Y + offsetY, model.rightLeg.originZ + offsetZ);
	}

	private static void movePart(ModelPart part, float x, float y, float z) {
		part.originX = x;
		part.originY = y;
		part.originZ = z;
	}

	private static void applyPose(ModelPart part, PartPose pose) {
		if (pose == null) {
			return;
		}
		float degreesToRadians = (float) (Math.PI / 180.0);
		part.pitch += pose.pitch * degreesToRadians;
		part.yaw += pose.yaw * degreesToRadians;
		part.roll += pose.roll * degreesToRadians;
	}

	private static int getPoseControlsY(int panelTop) {
		return panelTop + 34 + BodyModelSelectionCatalog.PARTS.length * 22 + 16;
	}

	private int getPreviewLeft() {
		return 190;
	}

	private int getPreviewRight() {
		return Math.max(getPreviewLeft() + 140, this.width - 198);
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
		float degrees = normalizeDegrees((float) Math.toDegrees(currentAngle - previousAngle));
		switch (axis) {
			case PITCH -> setActivePitch(clamp(getActivePitch() + degrees, -180.0F, 180.0F));
			case YAW -> setActiveYaw(normalizeDegrees(getActiveYaw() + degrees));
			case ROLL -> setActiveRoll(normalizeDegrees(getActiveRoll() + degrees));
			case NONE -> {
			}
		}
	}

	private ScreenPoint projectModelPoint(float x, float y, float z) {
		float yawRadians = (float) Math.toRadians(-getActiveYaw());
		float yawCos = (float) Math.cos(yawRadians);
		float yawSin = (float) Math.sin(yawRadians);
		float yawX = x * yawCos + z * yawSin;
		float yawZ = z * yawCos - x * yawSin;

		float pitchRadians = (float) Math.toRadians(getActivePitch());
		float pitchCos = (float) Math.cos(pitchRadians);
		float pitchSin = (float) Math.sin(pitchRadians);
		float pitchY = y * pitchCos - yawZ * pitchSin;
		float pitchX = yawX;

		float rollRadians = (float) Math.toRadians(getActiveRoll());
		float rollCos = (float) Math.cos(rollRadians);
		float rollSin = (float) Math.sin(rollRadians);
		float rollX = pitchX * rollCos - pitchY * rollSin;
		float rollY = pitchX * rollSin + pitchY * rollCos;

		return new ScreenPoint(getPreviewRenderCenterX() + rollX * getPreviewPixelsPerGrid(), getPreviewRenderCenterY() + rollY * getPreviewPixelsPerGrid());
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
		return Math.max(24.0F, Math.min(width, height) * 0.42F) * this.previewZoom;
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
		int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, stacks.size()));
		int height = visibleRows * ITEM_LIST_ITEM_HEIGHT + 2;
		context.fill(x, y, x + ITEM_SELECTOR_WIDTH, y + height, 0xF05E6876);
		context.drawBorder(x, y, ITEM_SELECTOR_WIDTH, height, 0xFFFFFFFF);
		if (stacks.isEmpty()) {
			context.drawTextWithShadow(this.textRenderer, "无可选物品", x + 6, y + 6, 0xFFE2E8F0);
			return;
		}
		context.enableScissor(x + 1, y + 1, x + ITEM_SELECTOR_WIDTH - 1, y + height - 1);
		try {
			for (int row = 0; row < visibleRows; row++) {
				int index = this.itemListScroll + row;
				if (index >= stacks.size()) {
					break;
				}
				ItemStack stack = stacks.get(index);
				int rowTop = y + 1 + row * ITEM_LIST_ITEM_HEIGHT;
				boolean hovered = mouseX >= x + 1 && mouseX <= x + ITEM_SELECTOR_WIDTH - 1
						&& mouseY >= rowTop && mouseY < rowTop + ITEM_LIST_ITEM_HEIGHT;
				context.fill(x + 1, rowTop, x + ITEM_SELECTOR_WIDTH - 1, rowTop + ITEM_LIST_ITEM_HEIGHT, hovered ? 0xE08796AA : (row % 2 == 0 ? 0xE0727D8C : 0xE0677180));
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
				&& mouseX <= getItemSelectorX() + ITEM_SELECTOR_WIDTH
				&& mouseY >= getItemSelectorY()
				&& mouseY <= getItemSelectorY() + 20;
	}

	private boolean isInsideItemList(double mouseX, double mouseY) {
		List<ItemStack> stacks = getAvailableItemStacks();
		int visibleRows = Math.min(ITEM_LIST_VISIBLE_ROWS, Math.max(1, stacks.size()));
		int height = visibleRows * ITEM_LIST_ITEM_HEIGHT + 2;
		int x = getItemSelectorX();
		int y = getItemListTop();
		return mouseX >= x && mouseX <= x + ITEM_SELECTOR_WIDTH
				&& mouseY >= y && mouseY <= y + height;
	}

	private int getItemSelectorX() {
		return this.width - 178;
	}

	private int getItemSelectorY() {
		return getPoseControlsY(38) + 126;
	}

	private int getItemListTop() {
		return getItemSelectorY() + 22;
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
			this.previewPitch = 0.0F;
			this.previewYaw = 0.0F;
			this.previewRoll = 0.0F;
		}
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
		return hasSelectedItemModel() ? EDITOR_ITEMS.get(this.selectedEditorItemIndex).pitch : this.previewPitch;
	}

	private float getActiveYaw() {
		return hasSelectedItemModel() ? EDITOR_ITEMS.get(this.selectedEditorItemIndex).yaw : this.previewYaw;
	}

	private float getActiveRoll() {
		return hasSelectedItemModel() ? EDITOR_ITEMS.get(this.selectedEditorItemIndex).roll : this.previewRoll;
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
			this.previewPitch = value;
		}
	}

	private void setActiveYaw(float value) {
		if (hasSelectedItemModel()) {
			EDITOR_ITEMS.get(this.selectedEditorItemIndex).yaw = value;
		} else {
			this.previewYaw = value;
		}
	}

	private void setActiveRoll(float value) {
		if (hasSelectedItemModel()) {
			EDITOR_ITEMS.get(this.selectedEditorItemIndex).roll = value;
		} else {
			this.previewRoll = value;
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

	private static PartPose getSelectedPose() {
		return PART_POSES.computeIfAbsent(selectedPart, ignored -> new PartPose());
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

	private static void resetSelectedPose() {
		if (selectedPart.equals("all")) {
			return;
		}
		PartPose pose = getSelectedPose();
		pose.pitch = 0.0F;
		pose.yaw = 0.0F;
		pose.roll = 0.0F;
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

	private static float[] createPoseValueArray() {
		float[] values = new float[PlacePosedBodyC2SPacket.POSE_VALUE_COUNT];
		writePose(values, 0, PART_POSES.get("head"));
		writePose(values, 3, PART_POSES.get("torso"));
		writePose(values, 6, PART_POSES.get("left_arm"));
		writePose(values, 9, PART_POSES.get("right_arm"));
		writePose(values, 12, PART_POSES.get("left_leg"));
		writePose(values, 15, PART_POSES.get("right_leg"));
		return values;
	}

	private static void writePose(float[] values, int offset, PartPose pose) {
		if (pose == null) {
			return;
		}
		values[offset] = pose.pitch;
		values[offset + 1] = pose.yaw;
		values[offset + 2] = pose.roll;
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

	private enum SkinSource {
		LOCAL,
		PLAYER
	}

	private static final class PartPose {
		private float pitch;
		private float yaw;
		private float roll;
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
