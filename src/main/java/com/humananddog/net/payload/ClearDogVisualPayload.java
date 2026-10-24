package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：某个玩家不再是狗了，把客户端缓存的狗外观删掉。
 *
 * <p>正常情况下狗下线时客户端会跟着卸载实体，不需要这个包；但身份互换、/hd role
 * 把狗改成人的时候人还在服务器里，客户端会继续用旧缓存把他画成一只狼。
 *
 * @param uuid 不再是狗的玩家 UUID
 */
public record ClearDogVisualPayload(String uuid) implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("clear_dog_visual");
	public static final CustomPacketPayload.Type<ClearDogVisualPayload> TYPE =
		new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, ClearDogVisualPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ClearDogVisualPayload::uuid,
			ClearDogVisualPayload::new
		);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
