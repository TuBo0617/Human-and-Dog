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

	/** 狗侧边挂件数据。 */
	public record DogBlocks(String leftId, String rightId, boolean leftLit, boolean rightLit) {
		public String id(int side) {
			return side == 0 ? leftId : rightId;
		}

		public boolean lit(int side) {
			return side == 0 ? leftLit : rightLit;
		}
	}

	public static String phase = "WAITING_HUMAN";
	public static int countdown = 0;
	/** 自己的身份：NONE / HUMAN / DOG / SPECTATOR（由 RoleStatePayload 同步）。 */
	public static String myRole = "NONE";
	/** 人 / 狗身份是否已被占用（角色界面据此置灰）。 */
	public static boolean humanTaken = false;
	public static boolean dogTaken = false;
	private static final Map<UUID, DogVisual> DOGS = new HashMap<>();
	/** 每只狗左右两侧挂着的方块。 */
	private static final Map<UUID, DogBlocks> BLOCKS = new HashMap<>();

	private ClientState() {
	}

	public static void reset() {
		phase = "WAITING_HUMAN";
		countdown = 0;
		myRole = "NONE";
		humanTaken = false;
		dogTaken = false;
		DOGS.clear();
		BLOCKS.clear();
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

	/** 本地狗是否正被主人命令坐下（坐下时不允许移动）。 */
	public static boolean isLocalDogSitting() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		DogVisual visual = DOGS.get(mc.player.getUUID());
		return visual != null && visual.sitting();
	}

	/** 记录某只狗左右两侧的方块。 */
	public static void putBlocks(UUID id, String left, String right, boolean leftLit, boolean rightLit) {
		BLOCKS.put(id, new DogBlocks(left, right, leftLit, rightLit));
	}

	/** 取某只狗左右两侧的方块，没有则返回 null。 */
	public static DogBlocks getBlocks(UUID id) {
		return BLOCKS.get(id);
	}
}
