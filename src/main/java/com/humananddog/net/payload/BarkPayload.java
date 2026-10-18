package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 -> 服务端：狗玩家按下了"狗叫"按键。
 * 服务端会校验身份（只有狗才会叫），人 / 观战者按了无效。
 */
public record BarkPayload() implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("bark");
	public static final CustomPacketPayload.Type<BarkPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, BarkPayload> CODEC = StreamCodec.unit(new BarkPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
