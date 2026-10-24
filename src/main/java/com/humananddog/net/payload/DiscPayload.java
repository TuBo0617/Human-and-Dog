package com.humananddog.net.payload;

import com.humananddog.HumanAndDogMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：让某个客户端在狗的位置播放一首唱片音乐。
 *
 * <p>用自定义包而不是直接 {@code level.playSound}，是因为 {@code playSound} 是"放出去就不管"的：
 * 唱片机被取下后音乐还会继续放完整首。自定义包可以让客户端自己持有 SoundInstance，
 * 一旦狗身上没有唱片机就能立刻 stop 掉。
 *
 * @param uuid    狗玩家 UUID
 * @param soundId 唱片音效 id（minecraft:music_disc.xxx 之类）
 */
public record DiscPayload(String uuid, String soundId, double x, double y, double z)
	implements CustomPacketPayload {
	public static final Identifier ID = HumanAndDogMod.id("dog_disc");
	public static final CustomPacketPayload.Type<DiscPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, DiscPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, DiscPayload::uuid,
		ByteBufCodecs.STRING_UTF8, DiscPayload::soundId,
		ByteBufCodecs.DOUBLE, DiscPayload::x,
		ByteBufCodecs.DOUBLE, DiscPayload::y,
		ByteBufCodecs.DOUBLE, DiscPayload::z,
		DiscPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
