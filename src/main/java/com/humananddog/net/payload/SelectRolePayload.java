package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 -> 服务端：房主选择角色。
 * @param role HUMAN / DOG
 */
public record SelectRolePayload(String role) implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("select_role");
	public static final CustomPacketPayload.Type<SelectRolePayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectRolePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, SelectRolePayload::role,
		SelectRolePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
