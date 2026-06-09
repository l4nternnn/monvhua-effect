package com.kuilunfuzhe.monvhua.network.bodypose;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PlacePoseEditorItemsC2SPacket(List<ItemPlacement> items,
											boolean fixedBase, double baseX, double baseY, double baseZ) implements CustomPayload {
	public static final Id<PlacePoseEditorItemsC2SPacket> ID = new Id<>(Identifier.of("monvhua", "place_pose_editor_items"));
	public static final PacketCodec<RegistryByteBuf, PlacePoseEditorItemsC2SPacket> CODEC = PacketCodec.of(PlacePoseEditorItemsC2SPacket::write, PlacePoseEditorItemsC2SPacket::new);
	private static final int MAX_ITEMS = 64;

	private static boolean registered = false;

	public PlacePoseEditorItemsC2SPacket(List<ItemPlacement> items) {
		this(items, false, 0.0D, 0.0D, 0.0D);
	}

	public PlacePoseEditorItemsC2SPacket(List<ItemPlacement> items, double baseX, double baseY, double baseZ) {
		this(items, true, baseX, baseY, baseZ);
	}

	public PlacePoseEditorItemsC2SPacket {
		items = List.copyOf(items.size() > MAX_ITEMS ? items.subList(0, MAX_ITEMS) : items);
	}

	private PlacePoseEditorItemsC2SPacket(RegistryByteBuf buf) {
		this(readItems(buf), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	private void write(RegistryByteBuf buf) {
		buf.writeVarInt(Math.min(this.items.size(), MAX_ITEMS));
		for (int i = 0; i < Math.min(this.items.size(), MAX_ITEMS); i++) {
			ItemPlacement item = this.items.get(i);
			buf.writeIdentifier(item.itemId());
			buf.writeFloat(item.offsetX());
			buf.writeFloat(item.offsetY());
			buf.writeFloat(item.offsetZ());
			buf.writeFloat(item.pitch());
			buf.writeFloat(item.yaw());
			buf.writeFloat(item.roll());
			buf.writeVarInt(item.displayContext().getIndex());
		}
		buf.writeBoolean(this.fixedBase);
		buf.writeDouble(this.baseX);
		buf.writeDouble(this.baseY);
		buf.writeDouble(this.baseZ);
	}

	private static List<ItemPlacement> readItems(RegistryByteBuf buf) {
		int count = Math.min(buf.readVarInt(), MAX_ITEMS);
		List<ItemPlacement> items = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			items.add(new ItemPlacement(
					buf.readIdentifier(),
					buf.readFloat(),
					buf.readFloat(),
					buf.readFloat(),
					buf.readFloat(),
					buf.readFloat(),
					buf.readFloat(),
					ItemDisplayContext.FROM_INDEX.apply(buf.readVarInt())));
		}
		return items;
	}

	public static void register() {
		if (!registered) {
			PayloadTypeRegistry.playC2S().register(ID, CODEC);
			registered = true;
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record ItemPlacement(Identifier itemId,
								float offsetX, float offsetY, float offsetZ,
								float pitch, float yaw, float roll,
								ItemDisplayContext displayContext) {
		public ItemPlacement(Identifier itemId,
							 float offsetX, float offsetY, float offsetZ,
							 float pitch, float yaw, float roll) {
			this(itemId, offsetX, offsetY, offsetZ, pitch, yaw, roll, ItemDisplayContext.FIXED);
		}

		public ItemPlacement {
			if (displayContext == null) {
				displayContext = ItemDisplayContext.FIXED;
			}
		}
	}
}
