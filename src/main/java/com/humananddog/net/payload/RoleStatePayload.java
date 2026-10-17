package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：同步"哪些身份已经被占用"以及"我自己是什么身份"。
 * 角色界面据此把已被占用的选项置灰。
 *
 * @param humanTaken 是否已经有人当人
 * @param dogTaken   是否已经有狗
 * @param myRole     收件人自己的身份（NONE / HUMAN / DOG / SPECTATOR）
 */
public record RoleStatePayload(boolean humanTaken, boolean dogTaken, String myRole) implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("role_state");
	public static final CustomPacketPayload.Type<RoleStatePayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, RoleStatePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, RoleStatePayload::humanTaken,
		ByteBufCodecs.BOOL, RoleStatePayload::dogTaken,
		ByteBufCodecs.STRING_UTF8, RoleStatePayload::myRole,
		RoleStatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
