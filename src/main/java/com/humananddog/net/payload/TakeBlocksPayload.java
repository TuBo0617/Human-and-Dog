package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 -> 服务端：按下"取出挂件"快捷键，请求把附近狗身上挂的方块拿下来。
 * 服务端只允许主人（HUMAN）这么做，并把方块与容器内容一并放回主人背包。
 */
public record TakeBlocksPayload() implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("take_blocks");
	public static final CustomPacketPayload.Type<TakeBlocksPayload> TYPE =
		new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, TakeBlocksPayload> CODEC =
		StreamCodec.unit(new TakeBlocksPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
