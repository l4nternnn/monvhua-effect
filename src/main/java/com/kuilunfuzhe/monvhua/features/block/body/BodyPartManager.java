package com.kuilunfuzhe.monvhua.features.block.body;

import com.kuilunfuzhe.monvhua.command.GiveBodyPartCommand;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalBodyPart;
import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalBodyPartBlockEntity;
import com.kuilunfuzhe.monvhua.item.modblock.moditems.Assembly_ModItems;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePoseEditorItemsC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePosedBodyC2SPacket;
import com.kuilunfuzhe.monvhua.screen.BodyPartScreenHandler;
import com.kuilunfuzhe.monvhua.util.ImplementedInventory;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BodyPartManager {
	// 死亡掉落肢体时每个肢体的四元数旋转
	private static final Quaternionf[] PART_ROTATIONS = {
		new Quaternionf(0.6087613f, 0.0f, 0.0f, 0.79335344f),
		new Quaternionf(0.6f, 0.0f, 0.0f, 0.79f),
		new Quaternionf(0.6733123f, 0.15081385f, -0.14311697f, 0.70952326f),
		new Quaternionf(0.7021285f, -0.12193926f, 0.12365657f, 0.69054717f),
		new Quaternionf(0.68573505f, 0.06322056f, -0.05999406f, 0.7226142f),
		new Quaternionf(0.69693834f, -0.07450287f, 0.07333822f, 0.70947003f)
	};

	private static void setLeftRotation(ItemDisplayEntity display, Quaternionf rotation) {
		display.setTransformation(new AffineTransformation(
				new Vector3f(),
				new Quaternionf(rotation),
				new Vector3f(1.0f, 1.0f, 1.0f),
				new Quaternionf()
		));
	}

	public static final Map<UUID, DefaultedList<ItemStack>> BODY_PART_DISPLAY_INVENTORIES = new ConcurrentHashMap<>();
	public static final Set<Item> BODY_PART_DISPLAY_ITEMS = Set.of(
		Assembly_ModItems.HEAD_ITEM, Assembly_ModItems.TORSO_ITEM,
		Assembly_ModItems.LEFT_ARM_ITEM, Assembly_ModItems.RIGHT_ARM_ITEM,
		Assembly_ModItems.LEFT_LEG_ITEM, Assembly_ModItems.RIGHT_LEG_ITEM
	);
	public static final Map<UUID, UUID> INTERACTION_TO_DISPLAY = new ConcurrentHashMap<>();
	public static final Map<UUID, UUID> DISPLAY_TO_INTERACTION = new ConcurrentHashMap<>();

	public static void registerEvents() {
		// 玩家死亡掉落肢体/合并身体作为可交互展示实体
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player) {
				if (!player.getCommandTags().contains("dead_body") && !player.getCommandTags().contains("dead_part")) return;
				ServerWorld world = (ServerWorld) player.getWorld();
				BlockPos deathPos = player.getBlockPos();
				ProfileComponent profile = new ProfileComponent(player.getGameProfile());
				String playerName = player.getName().getString();

				String detectedLocalSkin = null;
				for (String tag : player.getCommandTags()) {
					if (tag.equals("dead_body") || tag.equals("dead_part")) continue;
					for (String skin : GiveBodyPartCommand.LOCAL_SKINS) {
						if (skin.equals(tag)) {
							detectedLocalSkin = skin;
							break;
						}
					}
					if (detectedLocalSkin != null) break;
				}

				if (player.getCommandTags().contains("dead_part")) {
					Item[] partItems = new Item[]{
						Assembly_ModItems.HEAD_ITEM, Assembly_ModItems.TORSO_ITEM,
						Assembly_ModItems.LEFT_ARM_ITEM, Assembly_ModItems.RIGHT_ARM_ITEM,
						Assembly_ModItems.LEFT_LEG_ITEM, Assembly_ModItems.RIGHT_LEG_ITEM
					};
					String[] chineseNames = new String[]{"头部", "躯干", "左臂", "右臂", "左腿", "右腿"};
					double[] offsetsX = new double[]{0.0, 0, -0.6, 0.23, -0.3, 0.3};
					double[] offsetsY = new double[]{0.2, 0, -0.24, -0.24, -0.2, -0.2};
					double[] offsetsZ = new double[]{0.7, 0, -0.05, -0.1, -1.1, -1.1};

					for (int i = 0; i < 6; i++) {
						ItemStack stack = new ItemStack(partItems[i]);
						if (detectedLocalSkin != null) {
							NbtCompound nbt = new NbtCompound();
							nbt.putString("local_skin", detectedLocalSkin);
							nbt.putString("arm_model", "slim");
							stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
						} else {
							stack.set(DataComponentTypes.PROFILE, profile);
						}
						Text displayName = Text.literal("§6§k13§4" + playerName + "§r§6§k13§r" + "的" + chineseNames[i]);
						stack.set(DataComponentTypes.CUSTOM_NAME, displayName);

						ItemDisplayEntity display = EntityType.ITEM_DISPLAY.create(world, SpawnReason.TRIGGERED);
						if (display != null) {
							display.setItemStack(stack);
							if (i == 0) {
								float headYaw = player.getYaw();
								float headPitch = player.getPitch();
								Quaternionf headRot = new Quaternionf().rotateY((float) Math.toRadians(-headYaw)).rotateX((float) Math.toRadians(headPitch));
								setLeftRotation(display, headRot);
							} else {
								setLeftRotation(display, PART_ROTATIONS[i]);
							}
							display.setPosition(
								deathPos.getX() + 0.5 + offsetsX[i],
								deathPos.getY() + 0.3 + offsetsY[i],
								deathPos.getZ() + 0.5 + offsetsZ[i]
							);
							world.spawnEntity(display);

							InteractionEntity interaction = new InteractionEntity(EntityType.INTERACTION, world);
							interaction.setPosition(display.getPos());
							interaction.setInteractionWidth(0.75f);
							interaction.setInteractionHeight(0.75f);
							world.spawnEntity(interaction);
							INTERACTION_TO_DISPLAY.put(interaction.getUuid(), display.getUuid());
							DISPLAY_TO_INTERACTION.put(display.getUuid(), interaction.getUuid());
							if (i == 1) {
								DefaultedList<ItemStack> hotbarInv = DefaultedList.ofSize(9, ItemStack.EMPTY);
								for (int j = 0; j < 9; j++) {
									hotbarInv.set(j, player.getInventory().getStack(j));
								}
								BODY_PART_DISPLAY_INVENTORIES.putIfAbsent(display.getUuid(), hotbarInv);
							}
						}
					}
				} else if (player.getCommandTags().contains("dead_body")) {
					Vec3d pos = new Vec3d(deathPos.getX() + 0.5, deathPos.getY() + 0.3, deathPos.getZ() + 0.5);
					DefaultedList<ItemStack> hotbarInv = DefaultedList.ofSize(9, ItemStack.EMPTY);
					for (int j = 0; j < 9; j++) {
						hotbarInv.set(j, player.getInventory().getStack(j));
					}
					if (detectedLocalSkin != null) {
						createCombinedDisplay(world, pos, detectedLocalSkin, hotbarInv, null);
					} else {
						createCombinedDisplay(world, pos, playerName, hotbarInv, profile);
					}
				}
			}
		});

		// 右键交互实体：拾取或打开背包
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient) return ActionResult.PASS;
			if (!(entity instanceof InteractionEntity interaction)) return ActionResult.PASS;
			UUID displayUuid = INTERACTION_TO_DISPLAY.get(interaction.getUuid());
			if (displayUuid == null) return ActionResult.PASS;
			Entity targetDisplay = world.getEntity(displayUuid);

			if (player.isSneaking()) {
				if (targetDisplay instanceof ItemDisplayEntity display) {
					ItemStack stack = display.getItemStack().copy();
					DefaultedList<ItemStack> inv = BODY_PART_DISPLAY_INVENTORIES.get(displayUuid);
					if (inv != null) {
						NbtList itemsList = new NbtList();
						for (ItemStack s : inv) {
							NbtCompound tag = new NbtCompound();
							if (!s.isEmpty()) {
								tag = (NbtCompound) ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, s).result().orElse(new NbtCompound());
							}
							itemsList.add(tag);
						}
						NbtComponent existing = stack.get(DataComponentTypes.CUSTOM_DATA);
						NbtCompound comp = existing != null ? existing.copyNbt() : new NbtCompound();
						comp.put("body_part_inv_items", itemsList);
						stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(comp));
					}
					if (!player.getInventory().insertStack(stack)) {
						net.minecraft.entity.ItemEntity drop = new net.minecraft.entity.ItemEntity(world, display.getX(), display.getY(), display.getZ(), stack);
						world.spawnEntity(drop);
					}
					BODY_PART_DISPLAY_INVENTORIES.remove(displayUuid);
					INTERACTION_TO_DISPLAY.remove(interaction.getUuid());
					DISPLAY_TO_INTERACTION.remove(displayUuid);
					display.remove(Entity.RemovalReason.DISCARDED);
				}
				interaction.remove(Entity.RemovalReason.DISCARDED);
				return ActionResult.SUCCESS;
			}

			DefaultedList<ItemStack> inv = BODY_PART_DISPLAY_INVENTORIES.get(displayUuid);
			if (inv == null) {
				player.sendMessage(Text.literal("§c这个部位无法存放物品"), true);
				return ActionResult.PASS;
			}
			Text displayName = targetDisplay instanceof ItemDisplayEntity ide
				? ide.getItemStack().getName()
				: Text.literal("§c躯体背包");
			player.openHandledScreen(new NamedScreenHandlerFactory() {
				@Override
				public Text getDisplayName() {
					return displayName;
				}
				@Override
				public ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
					return new BodyPartScreenHandler(syncId, playerInv, new ImplementedInventory() {
						@Override
						public DefaultedList<ItemStack> getItems() {
							return inv;
						}
						@Override
						public void markDirty() {}
					});
				}
			});
			return ActionResult.SUCCESS;
		});

		// 放置肢体物品作为展示实体（右键空气）
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClient) return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity)) return ActionResult.PASS;
			ItemStack stack = player.getMainHandStack();
			if (isCombinedBodyItem(stack)) {
				if (player.isSneaking()) return ActionResult.PASS;
				BlockPos placePos = player.getBlockPos();
				placeCombinedBody(world, placePos, stack, (ServerPlayerEntity) player);
				return ActionResult.SUCCESS;
			}
			if (!BODY_PART_DISPLAY_ITEMS.contains(stack.getItem())) return ActionResult.PASS;
			if (player.isSneaking()) return ActionResult.PASS;
			BlockPos placePos = player.getBlockPos();
			ItemDisplayEntity display = EntityType.ITEM_DISPLAY.create(world, SpawnReason.TRIGGERED);
			if (display != null) {
				ItemStack placeStack = stack.copy();
				placeStack.setCount(1);
				NbtComponent customData = placeStack.get(DataComponentTypes.CUSTOM_DATA);
				if (customData != null) {
					NbtCompound root = customData.copyNbt();
					NbtElement invElem = root.get("body_part_inv_items");
					if (invElem instanceof NbtList itemsList) {
						DefaultedList<ItemStack> savedInv = DefaultedList.ofSize(9, ItemStack.EMPTY);
						for (int i = 0; i < Math.min(itemsList.size(), 9); i++) {
							NbtCompound tag = itemsList.getCompound(i).orElse(new NbtCompound());
							if (!tag.isEmpty()) {
								savedInv.set(i, ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(ItemStack.EMPTY));
							}
						}
						BODY_PART_DISPLAY_INVENTORIES.put(display.getUuid(), savedInv);
						root.remove("body_part_inv_items");
						placeStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
					}
				}
				display.setItemStack(placeStack);
				display.setPosition(placePos.getX() + 0.5, placePos.getY() + 0.3, placePos.getZ() + 0.5);
				world.spawnEntity(display);
				InteractionEntity interaction = new InteractionEntity(EntityType.INTERACTION, world);
				interaction.setPosition(display.getPos());
				interaction.setInteractionWidth(0.75f);
				interaction.setInteractionHeight(0.75f);
				world.spawnEntity(interaction);
				INTERACTION_TO_DISPLAY.put(interaction.getUuid(), display.getUuid());
				DISPLAY_TO_INTERACTION.put(display.getUuid(), interaction.getUuid());
				if (!player.isCreative()) {
					stack.decrement(1);
					if (stack.isEmpty()) {
						player.getInventory().removeOne(stack);
					}
				}
			}
			return ActionResult.SUCCESS;
		});

		// 放置肢体物品作为展示实体（右键方块）
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient) return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity)) return ActionResult.PASS;
			ItemStack stack = player.getMainHandStack();
			if (isCombinedBodyItem(stack)) {
				BlockPos placePos = hitResult.getBlockPos().offset(hitResult.getSide());
				placeCombinedBody(world, placePos, stack, (ServerPlayerEntity) player);
				return ActionResult.SUCCESS;
			}
			if (!BODY_PART_DISPLAY_ITEMS.contains(stack.getItem())) return ActionResult.PASS;
			BlockPos placePos = hitResult.getBlockPos().offset(hitResult.getSide());
			ItemDisplayEntity display = EntityType.ITEM_DISPLAY.create(world, SpawnReason.TRIGGERED);
			if (display != null) {
				ItemStack placeStack = stack.copy();
				placeStack.setCount(1);
				NbtComponent customData = placeStack.get(DataComponentTypes.CUSTOM_DATA);
				if (customData != null) {
					NbtCompound root = customData.copyNbt();
					NbtElement invElem = root.get("body_part_inv_items");
					if (invElem instanceof NbtList itemsList) {
						DefaultedList<ItemStack> savedInv = DefaultedList.ofSize(9, ItemStack.EMPTY);
						for (int i = 0; i < Math.min(itemsList.size(), 9); i++) {
							NbtCompound tag = itemsList.getCompound(i).orElse(new NbtCompound());
							if (!tag.isEmpty()) {
								savedInv.set(i, ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(ItemStack.EMPTY));
							}
						}
						BODY_PART_DISPLAY_INVENTORIES.put(display.getUuid(), savedInv);
						root.remove("body_part_inv_items");
						placeStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
					}
				}
				display.setItemStack(placeStack);
				display.setPosition(placePos.getX() + 0.5, placePos.getY(), placePos.getZ() + 0.5);
				world.spawnEntity(display);
				InteractionEntity interaction = new InteractionEntity(EntityType.INTERACTION, world);
				interaction.setPosition(display.getPos());
				interaction.setInteractionWidth(0.75f);
				interaction.setInteractionHeight(0.75f);
				world.spawnEntity(interaction);
				INTERACTION_TO_DISPLAY.put(interaction.getUuid(), display.getUuid());
				DISPLAY_TO_INTERACTION.put(display.getUuid(), interaction.getUuid());
				if (!player.isCreative()) {
					stack.decrement(1);
					if (stack.isEmpty()) {
						player.getInventory().removeOne(stack);
					}
				}
			}
			return ActionResult.SUCCESS;
		});

	}

	// ========== 核心方法 ==========

	public static String getPartType(ItemStack stack) {
		Item item = stack.getItem();
		if (item == Assembly_ModItems.HEAD_ITEM) return "head";
		if (item == Assembly_ModItems.TORSO_ITEM) return "torso";
		if (item == Assembly_ModItems.LEFT_ARM_ITEM) return "left_arm";
		if (item == Assembly_ModItems.RIGHT_ARM_ITEM) return "right_arm";
		if (item == Assembly_ModItems.LEFT_LEG_ITEM) return "left_leg";
		if (item == Assembly_ModItems.RIGHT_LEG_ITEM) return "right_leg";
		return null;
	}

	public static boolean isCombinedBodyItem(ItemStack stack) {
		Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
		return model != null && model.equals(Identifier.of("monvhua", "combined_body"));
	}

	public static void placeCombinedBody(World world, BlockPos pos, ItemStack stack, ServerPlayerEntity player) {
		ProfileComponent profile = stack.get(DataComponentTypes.PROFILE);
		String localSkin = null;
		DefaultedList<ItemStack> savedInv = null;

		NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (customData != null) {
			NbtCompound root = customData.copyNbt();
			if (root.contains("local_skin")) {
				localSkin = root.getString("local_skin").orElse(null);
			}
			NbtElement invElem = root.get("body_part_inv_items");
			if (invElem instanceof NbtList itemsList) {
				savedInv = DefaultedList.ofSize(9, ItemStack.EMPTY);
				for (int i = 0; i < Math.min(itemsList.size(), 9); i++) {
					NbtCompound tag = itemsList.getCompound(i).orElse(new NbtCompound());
					if (!tag.isEmpty()) {
						savedInv.set(i, ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(ItemStack.EMPTY));
					}
				}
			}
		}

		createCombinedDisplay((ServerWorld) world, Vec3d.of(pos).add(0.5, 0.2, 0.5),
			localSkin != null ? localSkin : "ema", savedInv, profile);

		if (!player.isCreative()) {
			stack.decrement(1);
			if (stack.isEmpty()) {
				player.getInventory().removeOne(stack);
			}
		}
	}

	public static int mergeBodyParts(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getWorld();
		Vec3d center = player.getPos();
		double radius = 5.0;

		Map<String, ItemDisplayEntity> foundParts = new HashMap<>();

		Box box = Box.of(center, radius, radius, radius);
		for (ItemDisplayEntity display : world.getEntitiesByClass(ItemDisplayEntity.class, box, entity -> true)) {
			ItemStack stack = display.getItemStack();
			if (BODY_PART_DISPLAY_ITEMS.contains(stack.getItem())) {
				String partType = getPartType(stack);
				if (partType != null) {
					foundParts.put(partType, display);
				}
			}
		}

		Set<String> required = Set.of("head", "torso", "left_arm", "right_arm", "left_leg", "right_leg");
		if (!foundParts.keySet().containsAll(required)) {
			player.sendMessage(Text.literal("§c未集齐所有肢体，缺少: " +
				required.stream().filter(p -> !foundParts.containsKey(p)).collect(Collectors.joining(", "))), false);
			return 0;
		}

		ItemDisplayEntity torsoDisplay = foundParts.get("torso");
		Vec3d mergePos = torsoDisplay.getPos();

		String skinName = "ema";
		ItemStack torsoStack = torsoDisplay.getItemStack();
		NbtComponent td = torsoStack.get(DataComponentTypes.CUSTOM_DATA);
		if (td != null && td.copyNbt().contains("local_skin")) {
			skinName = td.copyNbt().getString("local_skin").orElse("ema");
		}
		DefaultedList<ItemStack> torsoInv = BODY_PART_DISPLAY_INVENTORIES.get(torsoDisplay.getUuid());
		if (torsoInv == null) torsoInv = DefaultedList.ofSize(9, ItemStack.EMPTY);
		ProfileComponent profile = torsoStack.get(DataComponentTypes.PROFILE);

		for (ItemDisplayEntity display : foundParts.values()) {
			UUID displayUuid = display.getUuid();
			UUID interactionUuid = DISPLAY_TO_INTERACTION.get(displayUuid);
			if (interactionUuid != null) {
				Entity interaction = world.getEntity(interactionUuid);
				if (interaction != null) interaction.remove(Entity.RemovalReason.DISCARDED);
				INTERACTION_TO_DISPLAY.remove(interactionUuid);
				DISPLAY_TO_INTERACTION.remove(displayUuid);
			}
			BODY_PART_DISPLAY_INVENTORIES.remove(displayUuid);
			display.remove(Entity.RemovalReason.DISCARDED);
		}

		createCombinedDisplay(world, mergePos.add(0, 0.0, 0), skinName, torsoInv, profile);
		player.sendMessage(Text.literal("§a成功合并肢体！"), false);
		return 1;
	}

	public static int splitCombinedBody(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getWorld();
		Box box = new Box(player.getBlockPos()).expand(3);
		int count = 0;

		for (ItemDisplayEntity display : world.getEntitiesByClass(ItemDisplayEntity.class, box, e -> {
			ItemStack s = e.getItemStack();
			Identifier model = s.get(DataComponentTypes.ITEM_MODEL);
			return model != null && model.equals(Identifier.of("monvhua", "combined_body"));
		})) {
			Vec3d pos = display.getPos();
			ItemStack combinedStack = display.getItemStack();
			ProfileComponent profile = combinedStack.get(DataComponentTypes.PROFILE);
			String localSkin = null;
			DefaultedList<ItemStack> savedInv = null;

			NbtComponent customData = combinedStack.get(DataComponentTypes.CUSTOM_DATA);
			if (customData != null) {
				NbtCompound root = customData.copyNbt();
				if (root.contains("local_skin")) {
					localSkin = root.getString("local_skin").orElse(null);
				}
				NbtElement invElem = root.get("body_part_inv_items");
				if (invElem instanceof NbtList itemsList) {
					savedInv = DefaultedList.ofSize(9, ItemStack.EMPTY);
					for (int i = 0; i < Math.min(itemsList.size(), 9); i++) {
						NbtCompound tag = itemsList.getCompound(i).orElse(new NbtCompound());
						if (!tag.isEmpty()) {
							savedInv.set(i, ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(ItemStack.EMPTY));
						}
					}
				}
			}
			DefaultedList<ItemStack> mapInv = BODY_PART_DISPLAY_INVENTORIES.get(display.getUuid());
			if (mapInv != null) savedInv = mapInv;

			UUID interactionUuid = DISPLAY_TO_INTERACTION.get(display.getUuid());
			if (interactionUuid != null) {
				Entity interaction = world.getEntity(interactionUuid);
				if (interaction != null) interaction.remove(Entity.RemovalReason.DISCARDED);
				INTERACTION_TO_DISPLAY.remove(interactionUuid);
				DISPLAY_TO_INTERACTION.remove(display.getUuid());
			}
			BODY_PART_DISPLAY_INVENTORIES.remove(display.getUuid());
			display.remove(Entity.RemovalReason.DISCARDED);

			Item[] partItems = new Item[]{
				Assembly_ModItems.HEAD_ITEM, Assembly_ModItems.TORSO_ITEM,
				Assembly_ModItems.LEFT_ARM_ITEM, Assembly_ModItems.RIGHT_ARM_ITEM,
				Assembly_ModItems.LEFT_LEG_ITEM, Assembly_ModItems.RIGHT_LEG_ITEM
			};
			String[] chineseNames = new String[]{"头部", "躯干", "左臂", "右臂", "左腿", "右腿"};
			double[] offsetsX = new double[]{0.0, 0, -0.6, 0.23, -0.3, 0.3};
			double[] offsetsY = new double[]{0.2, 0, -0.24, -0.24, -0.2, -0.2};
			double[] offsetsZ = new double[]{0.7, 0, -0.05, -0.1, -1.1, -1.1};

			for (int i = 0; i < 6; i++) {
				ItemStack stack = new ItemStack(partItems[i]);
				if (localSkin != null) {
					NbtCompound nbt = new NbtCompound();
					nbt.putString("local_skin", localSkin);
					stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
				} else if (profile != null) {
					stack.set(DataComponentTypes.PROFILE, profile);
				}
				Text displayName = Text.literal("§6§k13§4" + player.getName().getString() + "§r§6§k13§r的" + chineseNames[i]);
				stack.set(DataComponentTypes.CUSTOM_NAME, displayName);

				ItemDisplayEntity partDisplay = EntityType.ITEM_DISPLAY.create(world, SpawnReason.TRIGGERED);
				if (partDisplay != null) {
					partDisplay.setItemStack(stack);
					setLeftRotation(partDisplay, PART_ROTATIONS[i]);
					partDisplay.setPosition(pos.x + offsetsX[i], pos.y + offsetsY[i], pos.z + offsetsZ[i]);
					world.spawnEntity(partDisplay);

					InteractionEntity interaction = new InteractionEntity(EntityType.INTERACTION, world);
					interaction.setPosition(partDisplay.getPos());
					interaction.setInteractionWidth(0.75f);
					interaction.setInteractionHeight(0.75f);
					world.spawnEntity(interaction);
					INTERACTION_TO_DISPLAY.put(interaction.getUuid(), partDisplay.getUuid());
					DISPLAY_TO_INTERACTION.put(partDisplay.getUuid(), interaction.getUuid());
					if (i == 1 && savedInv != null) {
						BODY_PART_DISPLAY_INVENTORIES.put(partDisplay.getUuid(), savedInv);
					}
				}
			}
			count++;
		}
		return count;
	}

	public static int clearNearbyBackpackInteractions(ServerPlayerEntity player, double radius) {
		ServerWorld world = (ServerWorld) player.getWorld();
		Box box = Box.of(player.getPos(), radius * 2.0D, radius * 2.0D, radius * 2.0D);
		int removed = 0;

		for (InteractionEntity interaction : world.getEntitiesByClass(InteractionEntity.class, box, entity -> true)) {
			UUID interactionUuid = interaction.getUuid();
			UUID displayUuid = INTERACTION_TO_DISPLAY.get(interactionUuid);
			if (displayUuid == null) {
				continue;
			}
			boolean hasBackpack = BODY_PART_DISPLAY_INVENTORIES.containsKey(displayUuid);
			boolean missingDisplay = world.getEntity(displayUuid) == null;
			if (!hasBackpack && !missingDisplay) {
				continue;
			}

			INTERACTION_TO_DISPLAY.remove(interactionUuid);
			DISPLAY_TO_INTERACTION.remove(displayUuid);
			if (missingDisplay) {
				BODY_PART_DISPLAY_INVENTORIES.remove(displayUuid);
			}
			interaction.remove(Entity.RemovalReason.DISCARDED);
			removed++;
		}

		return removed;
	}

	public static int applySkeletalPose(ServerPlayerEntity player, float[] poseValues, int radius) {
		return applySkeletalPose(player, poseValues, null, radius);
	}

	public static int applySkeletalPose(ServerPlayerEntity player, float[] poseValues, float[] bendValues, int radius) {
		if (poseValues == null || poseValues.length < PlacePosedBodyC2SPacket.ROTATION_VALUE_COUNT) {
			player.sendMessage(Text.literal("Invalid skeletal pose payload"), true);
			return 0;
		}

		ServerWorld world = (ServerWorld) player.getWorld();
		BlockPos center = player.getBlockPos();
		int stride = poseValues.length >= PlacePosedBodyC2SPacket.POSE_VALUE_COUNT
				? PlacePosedBodyC2SPacket.POSE_VALUE_STRIDE
				: 3;
		int updated = 0;

		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					BlockPos pos = center.add(x, y, z);
					if (!(world.getBlockEntity(pos) instanceof SkeletalBodyPartBlockEntity skeletal)) {
						continue;
					}
					int partIndex = getPoseIndex(skeletal.getPart());
					int offset = partIndex * stride;
					int bendOffset = partIndex * 3;
					float bendPitch = bendValues != null && bendValues.length >= bendOffset + 3 ? bendValues[bendOffset] : 0.0F;
					float bendYaw = bendValues != null && bendValues.length >= bendOffset + 3 ? bendValues[bendOffset + 1] : 0.0F;
					float bendRoll = bendValues != null && bendValues.length >= bendOffset + 3 ? bendValues[bendOffset + 2] : 0.0F;
					skeletal.setSkeletalPose(poseValues[offset], poseValues[offset + 1], poseValues[offset + 2],
							bendPitch, bendYaw, bendRoll);
					updated++;
				}
			}
		}

		player.sendMessage(Text.literal("Applied skeletal pose to " + updated + " body part block(s)"), true);
		return updated;
	}

	private static int getPoseIndex(SkeletalBodyPart part) {
		return switch (part) {
			case HEAD -> 0;
			case TORSO -> 1;
			case LEFT_ARM -> 2;
			case RIGHT_ARM -> 3;
			case LEFT_LEG -> 4;
			case RIGHT_LEG -> 5;
		};
	}

	public static void createCombinedDisplay(ServerWorld world, Vec3d pos, String skinName, DefaultedList<ItemStack> torsoInv, ProfileComponent profile) {
		createCombinedDisplay(world, pos, skinName, torsoInv, profile, false, null, null, true,
				0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
	}

	public static void createPosedCombinedDisplay(ServerPlayerEntity player, String skinName, boolean slim, float[] poseValues) {
		createPosedCombinedDisplay(player, skinName, slim, poseValues, null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
	}

	public static void createPosedCombinedDisplay(ServerPlayerEntity player, String skinName, boolean slim, float[] poseValues,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll) {
		createPosedCombinedDisplay(player, skinName, slim, poseValues, null,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, 1.0F);
	}

	public static void createPosedCombinedDisplay(ServerPlayerEntity player, String skinName, boolean slim, float[] poseValues,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale) {
		createPosedCombinedDisplay(player, skinName, slim, poseValues, null,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale);
	}

	public static void createPosedCombinedDisplay(ServerPlayerEntity player, String skinName, boolean slim, float[] poseValues, float[] bendValues,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale) {
		ServerWorld world = (ServerWorld) player.getWorld();
		Vec3d pos = getDefaultPosePlacementBase(player);
		createPosedCombinedDisplayAt(player, pos, skinName, slim, poseValues, bendValues,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale);
	}

	public static void createPosedCombinedDisplayAt(ServerPlayerEntity player, Vec3d pos, String skinName, boolean slim, float[] poseValues, float[] bendValues,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale) {
		ServerWorld world = (ServerWorld) player.getWorld();
		createCombinedDisplay(world, pos, skinName, DefaultedList.ofSize(9, ItemStack.EMPTY), null, slim, poseValues, bendValues, false,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale);
		player.sendMessage(Text.literal("Placed posed body model"), true);
	}

	public static void createPosedCombinedDisplay(ServerPlayerEntity player, ProfileComponent profile, boolean slim, float[] poseValues) {
		createPosedCombinedDisplay(player, profile, slim, poseValues, null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
	}

	public static void createPosedCombinedDisplay(ServerPlayerEntity player, ProfileComponent profile, boolean slim, float[] poseValues,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll) {
		createPosedCombinedDisplay(player, profile, slim, poseValues, null,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, 1.0F);
	}

	public static void createPosedCombinedDisplay(ServerPlayerEntity player, ProfileComponent profile, boolean slim, float[] poseValues,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale) {
		createPosedCombinedDisplay(player, profile, slim, poseValues, null,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale);
	}

	public static void createPosedCombinedDisplay(ServerPlayerEntity player, ProfileComponent profile, boolean slim, float[] poseValues, float[] bendValues,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale) {
		ServerWorld world = (ServerWorld) player.getWorld();
		Vec3d pos = getDefaultPosePlacementBase(player);
		createPosedCombinedDisplayAt(player, pos, profile, slim, poseValues, bendValues,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale);
	}

	public static void createPosedCombinedDisplayAt(ServerPlayerEntity player, Vec3d pos, ProfileComponent profile, boolean slim, float[] poseValues, float[] bendValues,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale) {
		ServerWorld world = (ServerWorld) player.getWorld();
		createCombinedDisplay(world, pos, "", DefaultedList.ofSize(9, ItemStack.EMPTY), profile, slim, poseValues, bendValues, false,
				offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale);
		player.sendMessage(Text.literal("Placed posed body model"), true);
	}

	public static void createPoseEditorItemDisplays(ServerPlayerEntity player, List<PlacePoseEditorItemsC2SPacket.ItemPlacement> placements) {
		createPoseEditorItemDisplays(player, placements, getDefaultPosePlacementBase(player));
	}

	public static void createPoseEditorItemDisplays(ServerPlayerEntity player, List<PlacePoseEditorItemsC2SPacket.ItemPlacement> placements, Vec3d basePos) {
		if (placements == null || placements.isEmpty()) {
			player.sendMessage(Text.literal("No pose editor item models to place"), true);
			return;
		}
		ServerWorld world = (ServerWorld) player.getWorld();
		int placed = 0;
		for (PlacePoseEditorItemsC2SPacket.ItemPlacement placement : placements) {
			Item item = Registries.ITEM.get(placement.itemId());
			if (item == Items.AIR) {
				continue;
			}
			ItemDisplayEntity display = EntityType.ITEM_DISPLAY.create(world, SpawnReason.TRIGGERED);
			if (display == null) {
				continue;
			}
			display.setItemStack(new ItemStack(item));
			display.setItemDisplayContext(placement.displayContext());
			display.setPosition(basePos.x + placement.offsetX(), basePos.y + placement.offsetY(), basePos.z + placement.offsetZ());
			Quaternionf rotation = new Quaternionf()
					.rotateX((float) Math.toRadians(placement.pitch()))
					.rotateY((float) Math.toRadians(-placement.yaw()))
					.rotateZ((float) Math.toRadians(placement.roll()));
			setLeftRotation(display, rotation);
			world.spawnEntity(display);
			placed++;
		}
		player.sendMessage(Text.literal("Placed " + placed + " pose editor item model(s)"), true);
	}

	private static void createCombinedDisplay(ServerWorld world, Vec3d pos, String skinName, DefaultedList<ItemStack> torsoInv, ProfileComponent profile,
			boolean slim, float[] poseValues, float[] bendValues, boolean lyingDown,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale) {
		ItemStack combinedStack = new ItemStack(Items.NETHERITE_SCRAP);
		combinedStack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("monvhua", "combined_body"));
		if (profile != null) {
			combinedStack.set(DataComponentTypes.PROFILE, profile);
			if (poseValues != null || bendValues != null || slim || hasPlacementTransform(offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale)) {
				NbtCompound nbt = new NbtCompound();
				if (slim) {
					nbt.putString("arm_model", "slim");
				}
				writePoseValues(nbt, poseValues);
				writeBendValues(nbt, bendValues);
				writePlacementTransform(nbt, offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale);
				combinedStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
			}
		} else {
			NbtCompound nbt = new NbtCompound();
			nbt.putString("local_skin", skinName);
			if (slim) {
				nbt.putString("arm_model", "slim");
			}
			writePoseValues(nbt, poseValues);
			writeBendValues(nbt, bendValues);
			writePlacementTransform(nbt, offsetX, offsetY, offsetZ, rotationPitch, rotationYaw, rotationRoll, modelScale);
			combinedStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
		}
		combinedStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§k13§4躯体§r§6§k13§r"));
		combinedStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

		ItemDisplayEntity display = EntityType.ITEM_DISPLAY.create(world, SpawnReason.TRIGGERED);
		if (display != null) {
			display.setItemStack(combinedStack);
			display.setPosition(pos);
			if (lyingDown) {
				setLeftRotation(display, new Quaternionf().rotateX((float) Math.toRadians(90)));
			}
			world.spawnEntity(display);

			InteractionEntity interaction = new InteractionEntity(EntityType.INTERACTION, world);
			interaction.setPosition(pos.x, lyingDown ? pos.y - 0.3 : pos.y - 1.5, pos.z);
			interaction.setInteractionWidth(0.9f);
			interaction.setInteractionHeight(lyingDown ? 0.8f : 1.8f);
			world.spawnEntity(interaction);
			INTERACTION_TO_DISPLAY.put(interaction.getUuid(), display.getUuid());
			DISPLAY_TO_INTERACTION.put(display.getUuid(), interaction.getUuid());
			if (torsoInv != null) {
				BODY_PART_DISPLAY_INVENTORIES.put(display.getUuid(), torsoInv);
			}
		}
	}

	private static Vec3d getHorizontalForward(ServerPlayerEntity player) {
		double rad = Math.toRadians(player.getYaw());
		return new Vec3d(-Math.sin(rad), 0, Math.cos(rad)).normalize();
	}

	private static Vec3d getDefaultPosePlacementBase(ServerPlayerEntity player) {
		Vec3d forward = getHorizontalForward(player);
		return player.getPos().add(forward.x, 1.5D, forward.z);
	}

	private static void writePoseValues(NbtCompound nbt, float[] poseValues) {
		if (poseValues == null || poseValues.length < PlacePosedBodyC2SPacket.ROTATION_VALUE_COUNT) {
			return;
		}
		boolean hasScaleValues = poseValues.length >= PlacePosedBodyC2SPacket.POSE_VALUE_COUNT;
		int stride = hasScaleValues ? PlacePosedBodyC2SPacket.POSE_VALUE_STRIDE : 3;
		writePose(nbt, poseValues, 0 * stride, "head", hasScaleValues);
		writePose(nbt, poseValues, 1 * stride, "torso", hasScaleValues);
		writePose(nbt, poseValues, 2 * stride, "left_arm", hasScaleValues);
		writePose(nbt, poseValues, 3 * stride, "right_arm", hasScaleValues);
		writePose(nbt, poseValues, 4 * stride, "left_leg", hasScaleValues);
		writePose(nbt, poseValues, 5 * stride, "right_leg", hasScaleValues);
	}

	private static void writePose(NbtCompound nbt, float[] values, int offset, String part, boolean hasScaleValue) {
		nbt.putFloat("pose_" + part + "_pitch", values[offset]);
		nbt.putFloat("pose_" + part + "_yaw", values[offset + 1]);
		nbt.putFloat("pose_" + part + "_roll", values[offset + 2]);
		if (hasScaleValue) {
			nbt.putFloat("pose_" + part + "_scale", Math.max(0.1F, values[offset + 3]));
		}
	}

	private static void writeBendValues(NbtCompound nbt, float[] bendValues) {
		if (bendValues == null || bendValues.length < PlacePosedBodyC2SPacket.BEND_VALUE_COUNT) {
			return;
		}
		writeBend(nbt, bendValues, 0, "head");
		writeBend(nbt, bendValues, 3, "torso");
		writeBend(nbt, bendValues, 6, "left_arm");
		writeBend(nbt, bendValues, 9, "right_arm");
		writeBend(nbt, bendValues, 12, "left_leg");
		writeBend(nbt, bendValues, 15, "right_leg");
	}

	private static void writeBend(NbtCompound nbt, float[] values, int offset, String part) {
		nbt.putFloat("bend_" + part + "_pitch", values[offset]);
		nbt.putFloat("bend_" + part + "_yaw", values[offset + 1]);
		nbt.putFloat("bend_" + part + "_roll", values[offset + 2]);
	}

	private static boolean hasPlacementTransform(float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll,
			float modelScale) {
		return offsetX != 0.0F || offsetY != 0.0F || offsetZ != 0.0F
				|| rotationPitch != 0.0F || rotationYaw != 0.0F || rotationRoll != 0.0F
				|| modelScale != 1.0F;
	}

	private static void writePlacementTransform(NbtCompound nbt,
			float offsetX, float offsetY, float offsetZ, float rotationPitch, float rotationYaw, float rotationRoll, float modelScale) {
		nbt.putFloat("pose_model_offset_x", offsetX);
		nbt.putFloat("pose_model_offset_y", offsetY);
		nbt.putFloat("pose_model_offset_z", offsetZ);
		nbt.putFloat("pose_model_pitch", rotationPitch);
		nbt.putFloat("pose_model_yaw", rotationYaw);
		nbt.putFloat("pose_model_roll", rotationRoll);
		nbt.putFloat("pose_model_scale", Math.max(0.1F, modelScale));
	}
}
