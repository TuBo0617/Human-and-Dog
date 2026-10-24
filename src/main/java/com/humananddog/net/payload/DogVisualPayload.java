package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：同步某只狗玩家的外观数据（用于渲染成狼）。
 *
 * @param baby 是否小狗（渲染成幼年狼，尺寸也随之变小）
 */
public record DogVisualPayload(String uuid, String variantId, int collarColor, boolean sitting, boolean hasArmor,
							   boolean baby) implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("dog_visual");
	public static final CustomPacketPayload.Type<DogVisualPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, DogVisualPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, DogVisualPayload::uuid,
		ByteBufCodecs.STRING_UTF8, DogVisualPayload::variantId,
		ByteBufCodecs.VAR_INT, DogVisualPayload::collarColor,
		ByteBufCodecs.BOOL, DogVisualPayload::sitting,
		ByteBufCodecs.BOOL, DogVisualPayload::hasArmor,
		ByteBufCodecs.BOOL, DogVisualPayload::baby,
		DogVisualPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
