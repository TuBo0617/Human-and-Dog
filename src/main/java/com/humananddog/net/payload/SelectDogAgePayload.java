package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 -> 服务端：狗选定"小狗 / 成年狗"。
 *
 * @param baby true = 小狗，false = 成年狗
 */
public record SelectDogAgePayload(boolean baby) implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("select_dog_age");
	public static final CustomPacketPayload.Type<SelectDogAgePayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectDogAgePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, SelectDogAgePayload::baby,
		SelectDogAgePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
