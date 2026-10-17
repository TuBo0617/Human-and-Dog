package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：同步当前游戏阶段与倒计时。
 * @param phase 阶段名（WAITING_HUMAN / WAITING_DOG / COUNTDOWN / PLAYING）
 * @param countdown 倒计时剩余秒数（仅 COUNTDOWN 阶段使用）
 */
public record GameStatePayload(String phase, int countdown) implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("game_state");
	public static final CustomPacketPayload.Type<GameStatePayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, GameStatePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, GameStatePayload::phase,
		ByteBufCodecs.VAR_INT, GameStatePayload::countdown,
		GameStatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
