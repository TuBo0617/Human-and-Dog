package com.humananddog.role;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 跨端的"谁是狗、是不是小狗"查询。
 *
 * <p>服务端从 {@link GameSessions} 读真实角色；客户端没有会话，由
 * {@code DogClientManager} 每 tick 把已同步的狗列表塞进来。
 * 这样 {@code main} 源集里的 mixin（碰撞箱、眼高）在客户端与服务端都能用同一套判断。
 */
public final class DogLookup {
	/** 客户端已知的狗：uuid -> 是否小狗。 */
	private static final Map<UUID, Boolean> CLIENT_DOGS = new HashMap<>();

	private DogLookup() {
	}

	/** 客户端同步狗列表（{@code DogClientManager} 每 tick 调用）。 */
	public static void setClientDogs(Map<UUID, Boolean> dogs) {
		CLIENT_DOGS.clear();
		CLIENT_DOGS.putAll(dogs);
	}

	public static boolean isDog(Entity entity) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		GameSession session = GameSessions.get();
		if (session != null) {
			return session.isDog(player.getUUID());
		}
		return CLIENT_DOGS.containsKey(player.getUUID());
	}

	/** 是否小狗（未成年）。非狗返回 false。 */
	public static boolean isBaby(Entity entity) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		GameSession session = GameSessions.get();
		if (session != null) {
			PlayerData data = session.data(player.getUUID());
			return data != null && data.role == Role.DOG && data.baby;
		}
		return Boolean.TRUE.equals(CLIENT_DOGS.get(player.getUUID()));
	}
}
