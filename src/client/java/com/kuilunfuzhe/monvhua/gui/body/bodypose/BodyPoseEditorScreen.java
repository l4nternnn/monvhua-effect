package com.kuilunfuzhe.monvhua.gui.body.bodypose;

import com.kuilunfuzhe.monvhua.features.block.body.BodyModelSelectionCatalog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePosedBodyC2SPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BodyPoseEditorScreen extends Screen {
	private static final float ROTATION_STEP_DEGREES = 5.0F;
	// Preview-only offsets keep the vanilla player model rotating around its visual center.
	private static final float PREVIEW_ROOT_Y_OFFSET = -8.0F;
	private static final float PREVIEW_Y_PIVOT = 1.601F;
	private static final int PLAYER_LIST_ITEM_HEIGHT = 18;
	private static final int PLAYER_LIST_VISIBLE_ROWS = 6;
	private static final int PLAYER_SELECTOR_WIDTH = 152;
	private static String selectedSkin = BodyModelSelectionCatalog.LOCAL_SKINS[0];
	private static String selectedPlayerName = "";
	private static SkinSource selectedSkinSource = SkinSource.LOCAL;
	private static String selectedPart = BodyModelSelectionCatalog.PARTS[0];
	private static boolean slimModel = true;
	private static final Map<String, PartPose> PART_POSES = createPartPoses();

	private final List<ButtonWidget> skinButtons = new ArrayList<>();
	private final List<ButtonWidget> partButtons = new ArrayList<>();
	private final List<ButtonWidget> poseButtons = new ArrayList<>();
	private ButtonWidget modelButton;
	private ButtonWidget runCommandButton;
	private ButtonWidget placeButton;
	private ButtonWidget showWholeButton;
	private ButtonWidget playerButton;
	private PlayerEntityModel defaultPreviewModel;
	private PlayerEntityModel slimPreviewModel;
	private float previewPitch = 8.0F;
	private float previewYaw = 0.0F;
	private float previewZoom = 1.0F;
	private boolean showWholePreview = true;
	private boolean draggingPreview;
	private boolean draggingRightPreview;
	private float previewPanX;
	private float previewPanY;
	private boolean showCoordinateAxes = true;
	private ButtonWidget coordToggleButton;
	private boolean playerListOpen;
	private int playerListScroll;

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
		if (this.showWholeButton != null) {
			this.showWholeButton.setMessage(Text.literal("整体预览 " + (this.showWholePreview ? "开启" : "关闭")));
		}
		if (this.coordToggleButton != null) {
			this.coordToggleButton.setMessage(Text.literal("坐标系 " + (this.showCoordinateAxes ? "开启" : "关闭")));
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
		ClientPlayNetworking.send(new PlacePosedBodyC2SPacket(selectedSkin, slimModel, createPoseValueArray(), selectedSkinSource == SkinSource.PLAYER, selectedPlayerName));
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
		renderPlayerPreview(context, previewLeft + 10, previewTop + 48, previewRight - 10, previewBottom - 10);

		super.render(context, mouseX, mouseY, deltaTicks);
		renderPlayerList(context, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
		if (button == 0 && this.draggingPreview) {
			this.previewYaw += (float) deltaX * 0.65F;
			this.previewPitch = clamp(this.previewPitch - (float) deltaY * 0.65F, -60.0F, 60.0F);
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
			renderGroundPlane(context, x1, y1, x2, y2, scale, panX, panY);
			context.addPlayerSkin(model, texture, scale, this.previewPitch, this.previewYaw, PREVIEW_Y_PIVOT, x1 + panX, y1 + panY, x2 + panX, renderBottom + panY);
		} finally {
			context.disableScissor();
		}
	}

	private void renderGroundPlane(DrawContext context, int x1, int y1, int x2, int y2, float scale, int panX, int panY) {
		int centerX = (x1 + x2) / 2 + panX;
		int centerY = (y1 + y2) / 2 + panY;
		int originY = Math.round(centerY + PREVIEW_Y_PIVOT * scale);
		if (originY >= y2 - 1 || originY < y1) {
			return;
		}

		float pitchRatio = (this.previewPitch + 60.0F) / 120.0F;
		float gridExtent = scale * 4.5F * Math.max(0.05F, pitchRatio);
		if (gridExtent < 2.0F) {
			return;
		}

		int gridBottomY = Math.min(y2, Math.round(originY + gridExtent));
		int gridHeight = gridBottomY - originY;
		if (gridHeight < 2) {
			return;
		}

		float vanishX = centerX + this.previewYaw * scale * 0.06F;
		float maxHalfWidth = (x2 - x1) * 0.48F;
		float gridUnit = Math.max(18.0F, scale * 0.7F);

		int minorAlpha = 0x22;
		int majorAlpha = 0x55;
		int axisAlpha = 0xD0;
		int minorColor = (minorAlpha << 24) | 0xCCCCCC;
		int majorColor = (majorAlpha << 24) | 0xFFFFFF;
		int xAxisColor = (axisAlpha << 24) | 0xFF3333;
		int zAxisColor = (axisAlpha << 24) | 0x3366FF;
		int yAxisColor = (axisAlpha << 24) | 0x33CC33;

		// === X-parallel grid lines (lines parallel to world X, drawn horizontally on screen) ===
		float xGridSpacing = gridUnit * 0.55F;
		float xPanOffset = ((panY + centerY - originY) % xGridSpacing + xGridSpacing) % xGridSpacing;
		int majorInterval = 5;
		for (float gy = originY + xPanOffset; gy < gridBottomY; gy += xGridSpacing) {
			int gpy = (int) gy;
			float progress = (gpy - originY) / (float) gridHeight;
			if (progress < 0.0F) continue;
			float halfW = maxHalfWidth * progress + 2.0F;
			int gl = Math.max(x1, Math.round(vanishX - halfW));
			int gr = Math.min(x2, Math.round(vanishX + halfW));
			if (gr <= gl) continue;

			int distFromOrigin = Math.round((gy - originY) / xGridSpacing);
			boolean isXAxis = distFromOrigin == 0;
			int lineThickness = isXAxis ? 2 : 1;
			int color = isXAxis ? xAxisColor : (Math.abs(distFromOrigin) % majorInterval == 0 ? majorColor : minorColor);
			for (int t = 0; t < lineThickness; t++) {
				context.fill(gl, gpy + t, gr, gpy + t + 1, color);
			}
		}

		// === Z-parallel grid lines (converging toward vanishX at originY) ===
		float zGridSpacing = gridUnit * 0.55F;
		float zPanOffset = ((panX + vanishX) % zGridSpacing + zGridSpacing) % zGridSpacing;
		int zLineCount = 20;
		for (int i = -zLineCount / 2; i <= zLineCount / 2; i++) {
			float worldX = vanishX + zPanOffset + i * zGridSpacing;
			boolean isMajor = Math.abs(i) % majorInterval == 0;
			boolean isZAxis = Math.abs(i * zGridSpacing + zPanOffset) < zGridSpacing * 0.5F;
			int lineAlpha = isZAxis ? axisAlpha : (isMajor ? majorAlpha : minorAlpha);
			int baseColor = isZAxis ? 0x3366FF : (isMajor ? 0xFFFFFF : 0xCCCCCC);
			int color = (lineAlpha << 24) | baseColor;
			int lineThickness = isZAxis ? 2 : 1;

			int prevX = Math.round(vanishX);
			for (int row = 0; row < gridHeight; row += 2) {
				int py = originY + row;
				float progress = (float) row / gridHeight;
				float halfW = maxHalfWidth * progress + 2.0F;
				int leftX = Math.round(vanishX - halfW);
				int rightX = Math.round(vanishX + halfW);
				float screenX = vanishX + (worldX - vanishX) * Math.max(0.0F, (float) row / gridHeight);
				int x = Math.round(screenX);
				if (x < leftX || x > rightX) continue;

				int segStart = Math.min(prevX, x);
				int segEnd = Math.max(prevX, x);
				int sx = Math.max(leftX, segStart);
				int sw = Math.min(rightX, segEnd) - sx;
				if (sw > 0) {
					for (int t = 0; t < lineThickness; t++) {
						context.fill(sx, py + t, sx + sw, py + t + 1, color);
					}
				}
				prevX = x;
			}
		}

		// === Y axis (green) — vertical from origin upward ===
		int oScreenX = Math.round(vanishX);
		int yLen = Math.round(scale * 1.8F);
		int yTop = Math.max(y1, originY - yLen);
		if (yTop < originY && oScreenX >= x1 && oScreenX < x2) {
			// Stem
			for (int py = yTop; py <= originY; py++) {
				float t = (float) (originY - py) / yLen;
				int a = (int) (axisAlpha * (1.0F - t * 0.5F));
				context.fill(oScreenX - 2, py, oScreenX + 3, py + 1, (a << 24) | 0x33CC33);
			}
			// Arrow head
			int ah = Math.min(6, originY - yTop);
			int ahTop = yTop + ah;
			context.fill(oScreenX - 3, yTop, oScreenX + 4, ahTop, yAxisColor);
		}

		// === Axis labels (small colored squares/dots for X, Y, Z) ===
		// Y label
		if (yTop > y1 + 8 && oScreenX >= x1 && oScreenX < x2) {
			context.fill(oScreenX + 4, yTop + 2, oScreenX + 12, yTop + 10, yAxisColor);
		}
	}

	private void renderCoordinateAxes(DrawContext context, int x1, int y1, int x2, int y2, float scale, int panX, int panY) {
		int centerX = (x1 + x2) / 2 + panX;
		int centerY = (y1 + y2) / 2 + panY;
		int originY = Math.round(centerY + PREVIEW_Y_PIVOT * scale);
		float pitchRatio = (this.previewPitch + 60.0F) / 120.0F;
		float gridExtent = scale * 4.5F * Math.max(0.05F, pitchRatio);
		if (gridExtent < 2.0F) return;
		int gridBottomY = Math.min(y2, Math.round(originY + gridExtent));
		int gridHeight = gridBottomY - originY;
		if (gridHeight < 4) return;

		float vanishX = centerX + this.previewYaw * scale * 0.06F;
		float maxHalfWidth = (x2 - x1) * 0.48F;
		float gridUnit = Math.max(18.0F, scale * 0.7F);

		// Place coordinate origin slightly below the horizon so axes extend into visible screen space
		int coordY = originY + Math.max(4, gridHeight / 6);
		int coordScreenX = Math.round(vanishX);
		float coordProgress = (float)(coordY - originY) / gridHeight;

		int axisAlpha = 0xE0;
		int xRed = (axisAlpha << 24) | 0xFF2222;
		int yGreen = (axisAlpha << 24) | 0x22DD22;
		int zBlue = (axisAlpha << 24) | 0x3355FF;

		// === X axis (red) — horizontal on ground, extending right ===
		float xLenPx = gridUnit * 2.0F;
		float xEnd = vanishX + xLenPx * coordProgress;
		int xEndX = Math.round(xEnd);
		if (xEndX > coordScreenX + 4 && xEndX < x2) {
			// Stem
			context.fill(coordScreenX, coordY - 1, xEndX, coordY + 2, xRed);
			// Arrow head
			int ax = xEndX;
			int ay = coordY;
			for (int d = 0; d < 5; d++) {
				int w = 5 - d;
				context.fill(ax - d, ay - w, ax - d + 1, ay + w + 1, xRed);
			}
			// X label
			int labelX = Math.min(xEndX + 4, x2 - 10);
			int labelY = coordY - 10;
			context.drawTextWithShadow(this.textRenderer, "X", labelX, labelY, 0xFFFF4444);
		}

		// === Y axis (green) — vertical upward ===
		float yLenPx = scale * 1.5F;
		int yTop = Math.max(y1, coordY - Math.round(yLenPx));
		if (yTop < coordY - 6 && coordScreenX >= x1 && coordScreenX < x2) {
			// Stem
			context.fill(coordScreenX - 1, yTop, coordScreenX + 2, coordY + 1, yGreen);
			// Arrow head
			for (int d = 0; d < 5; d++) {
				int w = 5 - d;
				context.fill(coordScreenX - w, yTop + d, coordScreenX + w + 1, yTop + d + 1, yGreen);
			}
			// Y label
			int labelX = Math.min(coordScreenX + 6, x2 - 10);
			int labelY = Math.max(y1 + 2, yTop - 2);
			context.drawTextWithShadow(this.textRenderer, "Y", labelX, labelY, 0xFF44FF44);
		}

		// === Z axis (blue) — converging line on ground toward vanishing point ===
		float zLenWorld = gridUnit * 2.0F;
		float zEndWorld = vanishX + zLenWorld * 0.15F * coordProgress;
		int zEndX = Math.round(zEndWorld);
		float zEndProgress = coordProgress - (2.0F * coordProgress) / gridHeight;
		if (zEndProgress < 0.0F) zEndProgress = 0.0F;
		float zEndScreenY = originY + zEndProgress * gridHeight;
		int zEndY = Math.round(zEndScreenY);
		if (zEndY < coordY - 4 && zEndY >= y1 && zEndX >= x1 && zEndX < x2) {
			// Stem: line from origin toward vanishing point
			float dx = (float)(zEndX - coordScreenX);
			float dy = (float)(zEndY - coordY);
			float dist = (float) Math.sqrt(dx * dx + dy * dy);
			if (dist > 2.0F) {
				float nx = dx / dist;
				float ny = dy / dist;
				for (int s = 0; s < (int) dist; s++) {
					int sx = Math.round(coordScreenX + nx * s);
					int sy = Math.round(coordY + ny * s);
					if (sx >= x1 && sx < x2 && sy >= y1 && sy < y2) {
						context.fill(sx - 1, sy, sx + 2, sy + 1, zBlue);
					}
				}
				// Arrow head
				for (int d = 0; d < 5; d++) {
					int w = 5 - d;
					int ax = Math.round(zEndX - nx * d);
					int ay = Math.round(zEndY - ny * d);
					int hx1 = Math.round(ax - ny * w);
					int hy1 = Math.round(ay + nx * w);
					int hx2 = Math.round(ax + ny * w);
					int hy2 = Math.round(ay - nx * w);
					int al = Math.min(hx1, hx2);
					int ar = Math.max(hx1, hx2);
					int at = Math.min(hy1, hy2);
					int ab = Math.max(hy1, hy2);
					al = Math.max(x1, al);
					ar = Math.min(x2, ar);
					if (ar > al && ab > at) {
						context.fill(al, at, ar, ab, zBlue);
					}
				}
				// Z label
				int labelX = Math.min(zEndX + 6, x2 - 10);
				int labelY = Math.max(y1 + 2, zEndY - 8);
				context.drawTextWithShadow(this.textRenderer, "Z", labelX, labelY, 0xFF4488FF);
			}
		}

		// === Origin point ===
		int originRadius = Math.max(3, Math.round(scale * 0.04F));
		for (int dy = -originRadius; dy <= originRadius; dy++) {
			int py = coordY + dy;
			if (py >= y1 && py < y2) {
				int dx = (int) Math.round(Math.sqrt(originRadius * originRadius - dy * dy));
				int sx = Math.max(x1, coordScreenX - dx);
				int ex = Math.min(x2, coordScreenX + dx + 1);
				if (ex > sx) context.fill(sx, py, ex, py + 1, 0xFFFFFFFF);
			}
		}
	}

	public boolean isShowingCoordinateAxes() {
		return this.showCoordinateAxes;
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
		model.getRootPart().originY = PREVIEW_ROOT_Y_OFFSET;

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
		switch (selectedPart) {
			case "head" -> movePartPair(model.head, model.hat, 0.0F, 12.0F, 0.0F);
			case "torso" -> movePartPair(model.body, model.jacket, 0.0F, 2.0F, 0.0F);
			case "left_arm" -> movePartPair(model.leftArm, model.leftSleeve, 0.0F, 4.0F, 0.0F);
			case "right_arm" -> movePartPair(model.rightArm, model.rightSleeve, 0.0F, 4.0F, 0.0F);
			case "left_leg" -> movePartPair(model.leftLeg, model.leftPants, 0.0F, 2.0F, 0.0F);
			case "right_leg" -> movePartPair(model.rightLeg, model.rightPants, 0.0F, 2.0F, 0.0F);
		}
	}

	private static void movePartPair(ModelPart base, ModelPart overlay, float x, float y, float z) {
		movePart(base, x, y, z);
		movePart(overlay, x, y, z);
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

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
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

	private enum SkinSource {
		LOCAL,
		PLAYER
	}

	private static final class PartPose {
		private float pitch;
		private float yaw;
		private float roll;
	}
}
