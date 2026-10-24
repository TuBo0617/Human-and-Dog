package com.humananddog.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.humananddog.net.payload.DiscPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 狗身上唱片机的客户端播放器。
 *
 * <p>服务端只发一个 {@link DiscPayload}，音乐由客户端自己创建 {@link SimpleSoundInstance} 播放，
 * 这样一旦狗身上没有唱片机了（取下 / 被替换 / 狗离开），可以立刻 {@code SoundManager.stop} 掉。
 * 直接用 {@code level.playSound} 是停不下来的。
 */
public final class DogDiscPlayer {
	/** 每只狗当前在放的唱片音效 id。 */
	private static final Map<UUID, Identifier> PLAYING = new HashMap<>();

	private DogDiscPlayer() {
	}

	public static void play(DiscPayload payload) {
		UUID id = UUID.fromString(payload.uuid());
		Identifier soundId = Identifier.tryParse(payload.soundId());
		if (soundId == null) {
			return;
		}
		SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(soundId);
		if (event == null) {
			return;
		}
		// 同一只狗先停掉上一首
		stop(id);
		SimpleSoundInstance instance = SimpleSoundInstance.forJukeboxSong(
			event, new Vec3(payload.x(), payload.y(), payload.z()));
		Minecraft.getInstance().getSoundManager().play(instance);
		PLAYING.put(id, soundId);
	}

	/** 停掉某只狗正在放的唱片。 */
	public static void stop(UUID id) {
		Identifier soundId = PLAYING.remove(id);
		if (soundId == null) {
			return;
		}
		Minecraft.getInstance().getSoundManager().stop(soundId, SoundSource.RECORDS);
	}

	public static void clear() {
		for (UUID id : new java.util.ArrayList<>(PLAYING.keySet())) {
			stop(id);
		}
	}
}
