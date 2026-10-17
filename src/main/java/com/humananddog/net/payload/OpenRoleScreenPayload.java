package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：通知房主打开"当人 / 当狗"选择界面。
 */
public record OpenRoleScreenPayload() implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("open_role_screen");
	public static final CustomPacketPayload.Type<OpenRoleScreenPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenRoleScreenPayload> CODEC =
		StreamCodec.unit(new OpenRoleScreenPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
