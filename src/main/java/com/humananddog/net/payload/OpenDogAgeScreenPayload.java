package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：选了狗之后，先让玩家选"小狗 / 成年狗"，之后才选狼变种。
 */
public record OpenDogAgeScreenPayload() implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("open_dog_age_screen");
	public static final CustomPacketPayload.Type<OpenDogAgeScreenPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenDogAgeScreenPayload> CODEC =
		StreamCodec.unit(new OpenDogAgeScreenPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
