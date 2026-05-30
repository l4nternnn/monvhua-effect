package com.kuilunfuzhe.monvhua.network.mirror;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 服务端 -> 客户端：镜子完整状态同步。
 * <p>
 * 服务端定期将两个镜子槽位的全部状态信息同步给客户端，包括：
 * <ul>
 *   <li>槽位是否激活、对应挂屏幕标位置、世界坐标锚点、渲染半径</li>
 *   <li>视口（viewport）预览的启用状态</li>
 * </ul>
 * <p>
 * 字段前缀说明：
 * <ul>
 *   <li><b>hs</b> (half-screen) — 挂屏幕标/预览画面在半屏渲染坐标系中的位置</li>
 *   <li><b>map</b> — 镜子在世界地图中锁定的目标锚点坐标</li>
 * </ul>
 * 槽位 1 和槽位 2 结构对称，各包含一组 hs/map/radius 数据。
 */
public record MirrorStateS2CPacket(
	/** 槽位 1 是否激活 */
	boolean slot1Active,
	/** 槽位 1 挂屏（hs）X 坐标 */
	double hsX1,
	/** 槽位 1 挂屏（hs）Y 坐标 */
	double hsY1,
	/** 槽位 1 挂屏（hs）Z 坐标 */
	double hsZ1,
	/** 槽位 1 世界地图锚点（map）X 坐标 */
	double mapX1,
	/** 槽位 1 世界地图锚点（map）Y 坐标 */
	double mapY1,
	/** 槽位 1 世界地图锚点（map）Z 坐标 */
	double mapZ1,
	/** 槽位 1 渲染半径 */
	double radius1,
	/** 槽位 2 是否激活 */
	boolean slot2Active,
	/** 槽位 2 挂屏（hs）X 坐标 */
	double hsX2,
	/** 槽位 2 挂屏（hs）Y 坐标 */
	double hsY2,
	/** 槽位 2 挂屏（hs）Z 坐标 */
	double hsZ2,
	/** 槽位 2 世界地图锚点（map）X 坐标 */
	double mapX2,
	/** 槽位 2 世界地图锚点（map）Y 坐标 */
	double mapY2,
	/** 槽位 2 世界地图锚点（map）Z 坐标 */
	double mapZ2,
	/** 槽位 2 渲染半径 */
	double radius2,
	/** 视口预览是否激活 */
	boolean viewportActive
) implements CustomPayload {
	public static final Id<MirrorStateS2CPacket> ID = new Id<>(Identifier.of("monvhua", "mirror_state"));
	public static final PacketCodec<PacketByteBuf, MirrorStateS2CPacket> CODEC = PacketCodec.of(
		(MirrorStateS2CPacket value, PacketByteBuf buf) -> {
			buf.writeBoolean(value.slot1Active);
			buf.writeDouble(value.hsX1); buf.writeDouble(value.hsY1); buf.writeDouble(value.hsZ1);
			buf.writeDouble(value.mapX1); buf.writeDouble(value.mapY1); buf.writeDouble(value.mapZ1);
			buf.writeDouble(value.radius1);
			buf.writeBoolean(value.slot2Active);
			buf.writeDouble(value.hsX2); buf.writeDouble(value.hsY2); buf.writeDouble(value.hsZ2);
			buf.writeDouble(value.mapX2); buf.writeDouble(value.mapY2); buf.writeDouble(value.mapZ2);
			buf.writeDouble(value.radius2);
			buf.writeBoolean(value.viewportActive);
		},
		(PacketByteBuf buf) -> {
			boolean s1a = buf.readBoolean();
			double hsx1 = buf.readDouble(), hsy1 = buf.readDouble(), hsz1 = buf.readDouble();
			double mpx1 = buf.readDouble(), mpy1 = buf.readDouble(), mpz1 = buf.readDouble();
			double r1 = buf.readDouble();
			boolean s2a = buf.readBoolean();
			double hsx2 = buf.readDouble(), hsy2 = buf.readDouble(), hsz2 = buf.readDouble();
			double mpx2 = buf.readDouble(), mpy2 = buf.readDouble(), mpz2 = buf.readDouble();
			double r2 = buf.readDouble();
			boolean va = buf.readBoolean();
			return new MirrorStateS2CPacket(
				s1a, hsx1, hsy1, hsz1, mpx1, mpy1, mpz1, r1,
				s2a, hsx2, hsy2, hsz2, mpx2, mpy2, mpz2, r2, va
			);
		}
	);

	/**
	 * 获取槽位 1 的挂屏位置向量，槽位未激活时返回 null。
	 */
	public Vec3d getHsPos1() { return slot1Active ? new Vec3d(hsX1, hsY1, hsZ1) : null; }
	/**
	 * 获取槽位 1 的世界锚点位置向量，槽位未激活时返回 null。
	 */
	public Vec3d getMapPos1() { return slot1Active ? new Vec3d(mapX1, mapY1, mapZ1) : null; }
	/**
	 * 获取槽位 2 的挂屏位置向量，槽位未激活时返回 null。
	 */
	public Vec3d getHsPos2() { return slot2Active ? new Vec3d(hsX2, hsY2, hsZ2) : null; }
	/**
	 * 获取槽位 2 的世界锚点位置向量，槽位未激活时返回 null。
	 */
	public Vec3d getMapPos2() { return slot2Active ? new Vec3d(mapX2, mapY2, mapZ2) : null; }

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static boolean registered = false;

	/**
	 * 注册此数据包到 S2C 负载类型注册表。
	 */
	public static void register() {
		if (!registered) {
			PayloadTypeRegistry.playS2C().register(ID, CODEC);
			registered = true;
		}
	}
}
