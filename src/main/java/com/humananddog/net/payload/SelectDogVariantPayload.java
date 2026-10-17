package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 -> 服务端：狗玩家选择狼变种（狗的类型）。
 * @param variantId 形如 "minecraft:woods" 的变种标识符
 */
public record SelectDogVariantPayload(String variantId) implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("select_dog_variant");
	public static final CustomPacketPayload.Type<SelectDogVariantPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectDogVariantPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, SelectDogVariantPayload::variantId,
		SelectDogVariantPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
