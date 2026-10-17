package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：通知狗玩家打开"狗的类型（狼变种）"选择界面。
 */
public record OpenDogVariantScreenPayload() implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("open_dog_variant_screen");
	public static final CustomPacketPayload.Type<OpenDogVariantScreenPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenDogVariantScreenPayload> CODEC =
		StreamCodec.unit(new OpenDogVariantScreenPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
