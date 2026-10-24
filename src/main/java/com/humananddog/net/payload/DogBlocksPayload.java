package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：同步某只狗左右两侧挂着的方块。
 *
 * @param uuid     狗玩家 UUID
 * @param leftId   左侧方块 id，空字符串表示没有
 * @param rightId  右侧方块 id，空字符串表示没有
 * @param leftLit  左侧是否是"正在燃烧的熔炉"（客户端据此换成 LIT 状态并冒火苗）
 * @param rightLit 右侧同上
 */
public record DogBlocksPayload(String uuid, String leftId, String rightId,
							   boolean leftLit, boolean rightLit) implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("dog_blocks");
	public static final CustomPacketPayload.Type<DogBlocksPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, DogBlocksPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, DogBlocksPayload::uuid,
		ByteBufCodecs.STRING_UTF8, DogBlocksPayload::leftId,
		ByteBufCodecs.STRING_UTF8, DogBlocksPayload::rightId,
		ByteBufCodecs.BOOL, DogBlocksPayload::leftLit,
		ByteBufCodecs.BOOL, DogBlocksPayload::rightLit,
		DogBlocksPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
