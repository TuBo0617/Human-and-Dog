package com.humananddog.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;

/**
 * 客户端持有的游戏状态（由服务端网络包同步）。
 */
public final class ClientState {
	/** 一只狗玩家的外观数据。 */
	public record DogVisual(String variantId, int collarColor, boolean sitting, boolean hasArmor, boolean baby) {
	}

	public static String phase = "WAITING_HUMAN";
	public static int countdown = 0;
	/** 狗玩家按 F6 切换：是否使用低视角（贴地狼眼高度的第一人称）。 */
	public static boolean dogLowCamera = false;
	/** 自己的身份：NONE / HUMAN / DOG / SPECTATOR（由 RoleStatePayload 同步）。 */
	public static String myRole = "NONE";
	/** 人 / 狗身份是否已被占用（角色界面据此置灰）。 */
	public static boolean humanTaken = false;
	public static boolean dogTaken = false;
	private static final Map<UUID, DogVisual> DOGS = new HashMap<>();

	private ClientState() {
	}

	public static void reset() {
		phase = "WAITING_HUMAN";
		countdown = 0;
		dogLowCamera = false;
		myRole = "NONE";
		humanTaken = false;
		dogTaken = false;
		DOGS.clear();
	}

	public static void putDog(UUID id, DogVisual visual) {
		DOGS.put(id, visual);
	}

	public static void removeDog(UUID id) {
		DOGS.remove(id);
	}

	public static DogVisual getDog(UUID id) {
		return DOGS.get(id);
	}

	public static boolean isDog(UUID id) {
		return DOGS.containsKey(id);
	}

	/** uuid -> 是否小狗，供 {@code DogLookup} 在客户端判断碰撞箱 / 眼高。 */
	public static Map<UUID, Boolean> dogBabyFlags() {
		Map<UUID, Boolean> map = new HashMap<>();
		DOGS.forEach((id, visual) -> map.put(id, visual.baby()));
		return map;
	}

	public static boolean isSpectator() {
		return "SPECTATOR".equals(myRole);
	}

	/** 当前本地玩家是否为狗。 */
	public static boolean localPlayerIsDog() {
		return Minecraft.getInstance().player != null
			&& isDog(Minecraft.getInstance().player.getUUID());
	}
}
