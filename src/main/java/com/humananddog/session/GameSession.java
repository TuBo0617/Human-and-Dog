package com.humananddog.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.humananddog.HumanAndDogMod;
import com.humananddog.net.payload.ClearDogVisualPayload;
import com.humananddog.net.payload.DiscPayload;
import com.humananddog.net.payload.DogBlocksPayload;
import com.humananddog.net.payload.DogVisualPayload;
import com.humananddog.net.payload.GameStatePayload;
import com.humananddog.net.payload.OpenDogAgeScreenPayload;
import com.humananddog.net.payload.OpenDogVariantScreenPayload;
import com.humananddog.net.payload.OpenRoleScreenPayload;
import com.humananddog.net.payload.RoleStatePayload;
import com.humananddog.role.PlayerData;
import com.humananddog.role.Role;
import com.humananddog.save.ModSaveData;
import com.humananddog.save.ModSaveData.SavedPlayer;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

/**
 * 一局游戏的服务端会话状态（一个存档对应一个）。
 */
public class GameSession {
	/** 拴绳最大距离（格，三维）。 */
	public static final double MAX_BOND_DISTANCE = 15.0;
	/** 重生倒计时（秒）。 */
	public static final int RESPAWN_COUNTDOWN_SECONDS = 3;
	/** 一局最多两个人（1 人 + 1 狗），其余自动观战。 */
	public static final int MAX_PARTICIPANTS = 2;

	private final MinecraftServer server;
	private final Map<UUID, PlayerData> players = new HashMap<>();
	private GamePhase phase = GamePhase.WAITING_HUMAN;
	private UUID humanId;
	private final List<UUID> dogIds = new ArrayList<>();
	private final List<UUID> spectatorIds = new ArrayList<>();
	private GlobalPos sharedRespawn;
	/** 本次会话是否从存档恢复过（用于恢复后直接继续，而不是重新开局）。 */
	private boolean loadedFromSave = false;
	private ModSaveData saveData;
	private int countdownTicks;
	private int countdownLastSecond = -1;
	/** 最近一次广播过的阶段签名（阶段 + 倒计时），用于每 tick 自愈检查时避免重复发包。 */
	private String lastBroadcastSignature;
	/** 狗外观兜底重播的计时器。 */
	private int visualResyncTicks;
	/** 死亡倒计时期间全员定格的位置。 */
	private final Map<UUID, FrozenSpot> freezeSpots = new HashMap<>();
	/** 掉线中的参与者：这些人回来之前，掉线等待阶段不会结束。 */
	private final Set<UUID> offlineParticipants = new HashSet<>();

	private record FrozenSpot(ServerLevel level, double x, double y, double z) {
	}

	public GameSession(MinecraftServer server) {
		this.server = server;
	}

	/** 从存档恢复玩家角色、狗外观与共享重生点（服务器启动时调用）。 */
	public void loadFromSave() {
		if (com.humananddog.dev.DevFlags.SOLO) {
			// 开发自测：验证存档 Codec 往返无损
			ModSaveData.selfTest(server.registryAccess());
		}
		ServerLevel overworld = server.overworld();
		this.saveData = ModSaveData.load(overworld);

		players.clear();
		dogIds.clear();
		spectatorIds.clear();
		for (Map.Entry<String, SavedPlayer> entry : saveData.players.entrySet()) {
			UUID uuid;
			try {
				uuid = UUID.fromString(entry.getKey());
			} catch (IllegalArgumentException ex) {
				continue;
			}
			SavedPlayer sp = entry.getValue();
			PlayerData data = new PlayerData(uuid);
			try {
				data.role = Role.valueOf(sp.role());
			} catch (IllegalArgumentException ex) {
				data.role = Role.NONE;
			}
			data.dogVariant = sp.dogVariant();
			data.variantConfirmed = sp.variantConfirmed();
			data.collarColor = sp.collarColor();
			data.baby = sp.baby();
			data.ageConfirmed = true;
			data.ageLocked = sp.ageLocked();
			data.growthTicks = sp.growthTicks();
			data.leftBlock = sp.leftBlock();
			data.rightBlock = sp.rightBlock();
			data.leftItems = new ArrayList<>(sp.leftItems());
			data.rightItems = new ArrayList<>(sp.rightItems());
			if (sp.hasArmor()) {
				data.wolfArmor = new ItemStack(Items.WOLF_ARMOR);
			}
			players.put(uuid, data);
			if (data.role == Role.DOG && !dogIds.contains(uuid)) {
				dogIds.add(uuid);
			}
			if (data.role == Role.SPECTATOR && !spectatorIds.contains(uuid)) {
				spectatorIds.add(uuid);
			}
		}

		if (!saveData.humanId.isEmpty()) {
			try {
				humanId = UUID.fromString(saveData.humanId);
			} catch (IllegalArgumentException ex) {
				humanId = null;
			}
		}
		sharedRespawn = saveData.respawn;

		if (saveData.started) {
			loadedFromSave = true;
			// 已开始过的存档：先停在等待阶段，等参与者到齐后在 recheckStart 中直接恢复
			setPhase(GamePhase.WAITING_DOGS);
		}
		HumanAndDogMod.LOGGER.info("[人狗同行] 从存档恢复：玩家 {} 人，已开始={}", players.size(), loadedFromSave);
	}

	/** 把当前会话状态写回存档。 */
	public void save() {
		if (saveData == null) {
			return;
		}
		Map<String, SavedPlayer> map = new HashMap<>();
		for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
			PlayerData d = entry.getValue();
			map.put(entry.getKey().toString(), new SavedPlayer(
				d.role.name(),
				d.dogVariant,
				d.variantConfirmed,
				d.collarColor,
				!d.wolfArmor.isEmpty(),
				d.baby,
				d.ageLocked,
				d.growthTicks,
				d.leftBlock,
				d.rightBlock,
				d.leftItems,
				d.rightItems
			));
		}
		saveData.players = map;
		saveData.humanId = humanId == null ? "" : humanId.toString();
		// 一旦开过局就永久记住：从存档恢复的途中会短暂回到 WAITING_DOGS，
		// 若在这里把它清成 false，下次进存档就会被当成新游戏，重新传送出生点。
		saveData.started = saveData.started
			|| phase == GamePhase.PLAYING || phase == GamePhase.COUNTDOWN;
		saveData.respawn = sharedRespawn;
		saveData.markChanged();
	}

	public MinecraftServer getServer() {
		return server;
	}

	public GamePhase getPhase() {
		return phase;
	}

	public boolean isPaused() {
		return phase.isPaused();
	}

	// ---------------------------------------------------------------------
	// 数据查询
	// ---------------------------------------------------------------------

	public PlayerData data(UUID uuid) {
		return players.get(uuid);
	}

	public PlayerData data(ServerPlayer player) {
		return players.get(player.getUUID());
	}

	public Role roleOf(UUID uuid) {
		PlayerData data = players.get(uuid);
		return data == null ? Role.NONE : data.role;
	}

	public boolean isDog(UUID uuid) {
		return roleOf(uuid) == Role.DOG;
	}

	public boolean isHuman(UUID uuid) {
		return humanId != null && humanId.equals(uuid);
	}

	public boolean isParticipant(UUID uuid) {
		return isHuman(uuid) || isDog(uuid);
	}

	public ServerPlayer human() {
		return humanId == null ? null : server.getPlayerList().getPlayer(humanId);
	}

	/**
	 * 在线的狗。
	 *
	 * <p>以"玩家角色数据"为准，而不是 {@code dogIds} 这个缓存索引：
	 * 索引一旦和真实角色不同步（读档、身份互换、旧存档里残留的 UUID 都可能造成），
	 * 这里就会永远返回空列表，{@code recheckStart} 于是认为"狗还没到"，
	 * 所有人卡在"等待其他玩家加入"——这正是读档后狗进来却开不了局的根因。
	 */
	public List<ServerPlayer> dogs() {
		List<ServerPlayer> result = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerData d = players.get(p.getUUID());
			if (d != null && d.role == Role.DOG) {
				result.add(p);
			}
		}
		return result;
	}

	/** 当前被占用的某个身份数量（含离线玩家，用于判断名额）。 */
	public int roleCount(Role role) {
		int count = 0;
		for (PlayerData d : players.values()) {
			if (d.role == role) {
				count++;
			}
		}
		return count;
	}

	public List<ServerPlayer> allParticipants() {
		List<ServerPlayer> result = new ArrayList<>();
		ServerPlayer h = human();
		if (h != null) {
			result.add(h);
		}
		result.addAll(dogs());
		return result;
	}

	public GlobalPos getSharedRespawn() {
		return sharedRespawn;
	}

	// ---------------------------------------------------------------------
	// 加入 / 退出
	// ---------------------------------------------------------------------

	public void onJoin(ServerPlayer player) {
		UUID id = player.getUUID();
		// 回来的人从"掉线名单"里移除，等待阶段据此判断能不能继续
		offlineParticipants.remove(id);
		PlayerData data = players.computeIfAbsent(id, PlayerData::new);

		// 房主 ID 必须对应一个真实的"人"：存档里 humanId 有值但那份玩家数据已丢失时清空，
		// 否则新玩家会被强制当成狗，而 human() 永远是 null → 全员卡在等待界面。
		if (humanId != null) {
			PlayerData humanData = players.get(humanId);
			if (humanData == null || humanData.role != Role.HUMAN) {
				HumanAndDogMod.LOGGER.info("房主数据丢失，清空房主 ID 重新选择");
				humanId = null;
				rebuildDogIds();
			}
		}

		// 观战者重新加入：保持旁观
		if (data.role == Role.SPECTATOR) {
			player.setGameMode(GameType.SPECTATOR);
			HumanAndDogMod.LOGGER.info("玩家 {} 以观战者身份重新加入", player.getName().getString());
			syncTo(player);
			syncRoleState();
			return;
		}

		// 已经在数据里（断线重连或从存档恢复）且已选角色，按其角色恢复
		if (data.role != Role.NONE) {
			HumanAndDogMod.LOGGER.info("玩家 {} 以角色 {} 重新加入", player.getName().getString(), data.role);
			// 人类以玩家数据里的角色为准：若存档里的 humanId 丢失或指向了别的 UUID，
			// 这里直接修正，否则 human() 永远为 null，全员会卡在"等待其他玩家加入"。
			if (data.role == Role.HUMAN) {
				if (humanId == null || !humanId.equals(id)) {
					HumanAndDogMod.LOGGER.info("修正房主 ID 为 {}", player.getName().getString());
					humanId = id;
				}
				dogIds.remove(id);
			}
			if (data.role == Role.DOG) {
				// 索引兜底：无论读档 / 重连走了哪条路径，都要保证狗在名单里，
				// 否则后面按名单判断"狗是否到齐"的旧逻辑会一直认为它没来。
				if (!dogIds.contains(id)) {
					dogIds.add(id);
				}
				com.humananddog.mechanic.DogMechanic.setupDog(player);
				if (!data.wolfArmor.isEmpty()) {
					com.humananddog.mechanic.DogMechanic.applyArmorModifier(player, true);
				}
				// 重连 / 恢复存档后同样要套用狗的小碰撞箱
				player.refreshDimensions();
				// 流程：先选年龄（小狗 / 成年狗），再选狼变种
				if (!data.ageConfirmed) {
					ServerPlayNetworking.send(player, new OpenDogAgeScreenPayload());
				} else if (!data.variantConfirmed) {
					ServerPlayNetworking.send(player, new OpenDogVariantScreenPayload());
				}
			}
			syncAllDogVisualsTo(player);
			sendAllDogBlocksTo(player);
			if (data.role == Role.DOG) {
				broadcastDogVisual(player);
			}
			recheckStart();
			syncTo(player);
			syncRoleState();
			return;
		}

		if (humanId == null) {
			if (com.humananddog.dev.DevFlags.DEV_DOG) {
				// 开发自测：单人直接当狗，自动选苍白变种并立即开局（生产环境永不进入）
				data.role = Role.DOG;
				if (!dogIds.contains(player.getUUID())) {
					dogIds.add(player.getUUID());
				}
				data.dogVariant = "minecraft:pale";
				data.variantConfirmed = true;
				data.ageConfirmed = true;
				com.humananddog.mechanic.DogMechanic.setupDog(player);
				sharedRespawn = GlobalPos.of(player.level().dimension(),
					BlockPos.containing(player.getX(), player.getY(), player.getZ()));
				setPhase(GamePhase.PLAYING);
				broadcastDogVisual(player);
				syncAll();
				syncRoleState();
				save();
				return;
			}
			if (com.humananddog.dev.DevFlags.SOLO) {
				// 开发自测：单人 QuickPlay 自动当选人类并直接开局（生产环境永不进入）
				selectRole(player, Role.HUMAN);
				return;
			}
			// 第一位玩家（房主）：弹出角色选择
			setPhase(GamePhase.WAITING_HUMAN);
			// 先同步"哪些身份已被占用"，再开界面：否则新玩家会在角色界面里
			// 看到人 / 狗两个按钮都是可点的，点下去才被服务端拒绝。
			syncRoleState();
			syncTo(player);
			ServerPlayNetworking.send(player, new OpenRoleScreenPayload());
		} else if (isRoleAvailable(Role.HUMAN) || isRoleAvailable(Role.DOG)) {
			// 还有空位：让新玩家自己选（已被占用的身份在界面上置灰）
			syncRoleState();
			syncTo(player);
			ServerPlayNetworking.send(player, new OpenRoleScreenPayload());
		} else {
			// 人狗都已就位：后来者自动进入观战
			assignSpectator(player);
		}
	}

	public void onLeave(ServerPlayer player) {
		PlayerData data = players.get(player.getUUID());
		if (data == null || !data.role.isParticipant()) {
			// 观战者 / 还没选身份的人离开，不影响正在进行的一局
			return;
		}
		if (phase != GamePhase.PLAYING) {
			// 等待阶段本来就停着；倒计时期间让倒计时自己走完，不打断
			return;
		}
		// 参与者掉线：暂停世界并显示开局同款等待界面，等他回来继续。
		// 不重新传送、不重置出生点，回来的人会被 recheckStart 直接接回 PLAYING。
		// 用显式的"掉线名单"判断，避免依赖玩家列表的移除时机（DISCONNECT 时可能还没移除）。
		offlineParticipants.add(player.getUUID());
		setPhase(GamePhase.WAITING_PARTNER);
		HumanAndDogMod.LOGGER.info("参与者 {} 离开，暂停等待其回来", player.getName().getString());
		syncAll();
		save();
	}

	/** 按玩家数据重建狗列表，清掉已经不再是狗的 UUID。 */
	private void rebuildDogIds() {
		dogIds.clear();
		spectatorIds.clear();
		for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
			if (entry.getValue().role == Role.DOG) {
				dogIds.add(entry.getKey());
			} else if (entry.getValue().role == Role.SPECTATOR) {
				spectatorIds.add(entry.getKey());
			}
		}
	}

	/** 某个身份现在还能不能选（人最多 1 个、狗最多 1 个、观战不限）。 */
	public boolean isRoleAvailable(Role role) {
		return switch (role) {
			case HUMAN -> humanId == null;
			case DOG -> roleCount(Role.DOG) < MAX_PARTICIPANTS - 1;
			case SPECTATOR -> true;
			default -> false;
		};
	}

	/** 把"身份占用情况 + 我自己的身份"推给所有在线玩家（角色界面据此置灰）。 */
	public void syncRoleState() {
		boolean humanTaken = humanId != null;
		boolean dogTaken = !dogIds.isEmpty();
		for (ServerPlayer p : PlayerLookup.all(server)) {
			PlayerData d = data(p);
			ServerPlayNetworking.send(p, new RoleStatePayload(
				humanTaken, dogTaken, d == null ? Role.NONE.name() : d.role.name()
			));
		}
	}

	/** 进入观战（旁观模式）：不参与拉绳、死亡联动、开局传送。 */
	private void assignSpectator(ServerPlayer player) {
		PlayerData data = players.computeIfAbsent(player.getUUID(), PlayerData::new);
		if (isHuman(player.getUUID())) {
			humanId = null;
		}
		data.role = Role.SPECTATOR;
		dogIds.remove(player.getUUID());
		if (!spectatorIds.contains(player.getUUID())) {
			spectatorIds.add(player.getUUID());
		}
		player.setGameMode(GameType.SPECTATOR);
		HumanAndDogMod.LOGGER.info("玩家 {} 成为观战者", player.getName().getString());
		player.sendSystemMessage(Component.translatable("human-and-dog.message.spectator"));
		syncRoleState();
		syncTo(player);
	}

	private void assignDog(ServerPlayer player, PlayerData data) {
		if (!isRoleAvailable(Role.DOG) && data.role != Role.DOG) {
			assignSpectator(player);
			return;
		}
		// 同一名玩家不能既是房主又是狗：若真的走到这里，以"改当狗"为准放弃房主身份，
		// 避免出现单人凑齐双方、跳过等待直接开局的状态。
		if (isHuman(player.getUUID())) {
			HumanAndDogMod.LOGGER.info("房主 {} 改当狗，放弃房主身份", player.getName().getString());
			humanId = null;
		}
		data.role = Role.DOG;
		spectatorIds.remove(player.getUUID());
		if (!dogIds.contains(player.getUUID())) {
			dogIds.add(player.getUUID());
		}
		com.humananddog.mechanic.DogMechanic.setupDog(player);
		if (!data.wolfArmor.isEmpty()) {
			com.humananddog.mechanic.DogMechanic.applyArmorModifier(player, true);
		}
		// 狗是 0.6×0.85 的碰撞箱（成年）/ 0.3×0.425（幼犬），需要立刻刷新尺寸缓存，
		// 否则服务端仍按 1.8 高判定，钻不进一格高的空间。
		player.refreshDimensions();
		setPhase(GamePhase.WAITING_DOGS);
		HumanAndDogMod.LOGGER.info("玩家 {} 自动成为狗", player.getName().getString());
		// 流程：先选年龄（小狗 / 成年狗），再选狼变种；先开界面再广播等待状态，避免被等待界面覆盖
		if (!data.ageConfirmed) {
			ServerPlayNetworking.send(player, new OpenDogAgeScreenPayload());
		} else if (!data.variantConfirmed) {
			ServerPlayNetworking.send(player, new OpenDogVariantScreenPayload());
		}
		syncRoleState();
		// 关键：这里必须重新评估开局条件。旧实现只 syncAll()，
		// 若这只狗不需要再选变种（variantConfirmed 已为真）就再也没有任何事件
		// 会把阶段从 WAITING_DOGS 推出去，世界保持暂停、所有人都卡在等待界面。
		recheckStart();
	}

	// ---------------------------------------------------------------------
	// 角色 / 变种选择
	// ---------------------------------------------------------------------

	public void selectRole(ServerPlayer player, Role role) {
		PlayerData data = data(player);
		if (data == null || role == Role.NONE) {
			return;
		}
		// 角色选定后不允许在同一个界面里改选。旧实现里"人再点当狗"会把同一个人同时记成
		// 房主和狗：既满足 human() != null 又满足狗已确认，于是单人凑齐双方直接开局
		// （表现就是没有等待界面、时间也不暂停）。这里直接拒掉并把真实状态同步回去。
		if (data.role != Role.NONE && data.role != role) {
			HumanAndDogMod.LOGGER.info("玩家 {} 已选择 {}，忽略改选为 {}",
				player.getName().getString(), data.role, role);
			syncTo(player);
			syncRoleState();
			return;
		}
		if (role == Role.SPECTATOR) {
			assignSpectator(player);
			return;
		}
		// 人最多 1 个、狗最多 1 个：被别人占了就只同步状态（界面上的按钮也已置灰）
		if (!isRoleAvailable(role)) {
			HumanAndDogMod.LOGGER.info("{} 身份已被占用，忽略 {} 的选择",
				role, player.getName().getString());
			syncTo(player);
			syncRoleState();
			return;
		}

		if (role == Role.HUMAN) {
			data.role = Role.HUMAN;
			humanId = player.getUUID();
			// 若该玩家之前被标记成狗，移出狗列表
			dogIds.remove(player.getUUID());
			spectatorIds.remove(player.getUUID());
			setPhase(GamePhase.WAITING_DOGS);
			HumanAndDogMod.LOGGER.info("房主 {} 选择当人", player.getName().getString());
			// 通知房主进入等待界面（保持暂停）
			syncAll();
			ServerPlayNetworking.send(player, new GameStatePayload(GamePhase.WAITING_DOGS.name(), 0));
		} else if (role == Role.DOG) {
			assignDog(player, data);
		}
		syncRoleState();
		recheckStart();
		save();
	}

	/** 狗选择"小狗 / 成年狗"；选完立刻接着弹狼变种界面。 */
	public void selectDogAge(ServerPlayer player, boolean baby) {
		PlayerData data = data(player);
		if (data == null || data.role != Role.DOG || data.ageConfirmed) {
			return;
		}
		data.baby = baby;
		data.ageConfirmed = true;
		data.ageLocked = false;
		data.growthTicks = 0;
		HumanAndDogMod.LOGGER.info("狗 {} 选择了{}", player.getName().getString(), baby ? "小狗" : "成年狗");
		// 尺寸 / 眼高随年龄变化，必须刷新
		player.refreshDimensions();
		broadcastDogVisual(player);
		ServerPlayNetworking.send(player, new OpenDogVariantScreenPayload());
		save();
	}

	public void selectVariant(ServerPlayer player, String variantId) {
		PlayerData data = data(player);
		if (data == null || data.role != Role.DOG) {
			return;
		}
		data.dogVariant = variantId;
		data.variantConfirmed = true;
		HumanAndDogMod.LOGGER.info("狗 {} 选择了类型 {}", player.getName().getString(), variantId);
		broadcastDogVisual(player);
		recheckStart();
		// 若开局已经在进行中（第三个玩家中途加入当狗），recheckStart 会直接返回，
		// 这里补一次单发同步，让这只狗知道自己已经可以正常行动。
		syncTo(player);
		save();
	}

	// ---------------------------------------------------------------------
	// 身份变更（/hd role、/hd swap、/hd age）
	// ---------------------------------------------------------------------

	/** 身份切换的结果。 */
	public enum RoleChangeResult {
		/** 切换成功。 */
		OK,
		/** 游戏已经开始，不允许再改。 */
		LOCKED,
		/** 目标身份已经被别人占了。 */
		TAKEN
	}

	/**
	 * 等待阶段修改自己的身份（{@code /hd role}）。
	 * 人和狗各只有一个名额：目标身份被别人占用时返回 {@link RoleChangeResult#TAKEN}；
	 * 开局之后（PLAYING / COUNTDOWN）返回 {@link RoleChangeResult#LOCKED}。
	 */
	public RoleChangeResult changeOwnRole(ServerPlayer player, Role target) {
		if (phase == GamePhase.PLAYING || phase == GamePhase.COUNTDOWN) {
			return RoleChangeResult.LOCKED;
		}
		PlayerData data = data(player);
		if (data == null) {
			return RoleChangeResult.LOCKED;
		}
		UUID id = player.getUUID();
		if (data.role == target) {
			syncTo(player);
			syncRoleState();
			return RoleChangeResult.OK;
		}
		switch (target) {
			case HUMAN -> {
				if (humanId != null && !humanId.equals(id)) {
					return RoleChangeResult.TAKEN;
				}
				leaveDogRole(player, data);
				spectatorIds.remove(id);
				data.role = Role.HUMAN;
				humanId = id;
				player.setGameMode(GameType.SURVIVAL);
				applyHumanBody(player);
				setPhase(GamePhase.WAITING_DOGS);
				HumanAndDogMod.LOGGER.info("{} 通过指令改为当人", player.getName().getString());
			}
			case DOG -> {
				if (roleCount(Role.DOG) > 0 && !isDog(id)) {
					return RoleChangeResult.TAKEN;
				}
				if (isHuman(id)) {
					humanId = null;
				}
				spectatorIds.remove(id);
				data.role = Role.DOG;
				if (!dogIds.contains(id)) {
					dogIds.add(id);
				}
				player.setGameMode(GameType.SURVIVAL);
				com.humananddog.mechanic.DogMechanic.setupDog(player);
				if (!data.wolfArmor.isEmpty()) {
					com.humananddog.mechanic.DogMechanic.applyArmorModifier(player, true);
				}
				player.refreshDimensions();
				if (!data.ageConfirmed) {
					ServerPlayNetworking.send(player, new OpenDogAgeScreenPayload());
				} else if (!data.variantConfirmed) {
					ServerPlayNetworking.send(player, new OpenDogVariantScreenPayload());
				} else {
					broadcastDogVisual(player);
				}
				setPhase(GamePhase.WAITING_DOGS);
				HumanAndDogMod.LOGGER.info("{} 通过指令改为当狗", player.getName().getString());
			}
			case SPECTATOR -> {
				if (isHuman(id)) {
					humanId = null;
				}
				leaveDogRole(player, data);
				data.role = Role.SPECTATOR;
				if (!spectatorIds.contains(id)) {
					spectatorIds.add(id);
				}
				player.setGameMode(GameType.SPECTATOR);
				player.sendSystemMessage(Component.translatable("human-and-dog.message.spectator"));
				setPhase(humanId == null && dogIds.isEmpty() ? GamePhase.WAITING_HUMAN : GamePhase.WAITING_DOGS);
				HumanAndDogMod.LOGGER.info("{} 通过指令改为观战者", player.getName().getString());
			}
			default -> {
				return RoleChangeResult.LOCKED;
			}
		}
		syncRoleState();
		syncAll();
		// 身份都变了，之前"等某个人回来"的前提不再成立：清掉掉线名单，按新阵容重新评估
		offlineParticipants.clear();
		recheckStart();
		save();
		return RoleChangeResult.OK;
	}

	/** {@code /hd age}：让狗重新走一遍"年龄 → 类型"的选择流程。 */
	public void beginDogReconfigure(ServerPlayer dog) {
		PlayerData data = data(dog);
		if (data == null || data.role != Role.DOG) {
			return;
		}
		// 把两个"已确认"标记清掉，selectDogAge / selectVariant 才会接受这次重选
		data.ageConfirmed = false;
		data.variantConfirmed = false;
		save();
		ServerPlayNetworking.send(dog, new OpenDogAgeScreenPayload());
	}

	/**
	 * {@code /hd swap}：人和狗都在线时互换身份。
	 * 狗的外观（变种、项圈、狼铠、年龄）整套搬给新狗，原来的人恢复成 20 血的普通人。
	 */
	public boolean swapHumanAndDog() {
		ServerPlayer human = human();
		List<ServerPlayer> onlineDogs = dogs();
		if (human == null || onlineDogs.isEmpty()) {
			return false;
		}
		ServerPlayer dog = onlineDogs.get(0);
		UUID humanUuid = human.getUUID();
		UUID dogUuid = dog.getUUID();
		PlayerData humanData = players.get(humanUuid);
		PlayerData dogData = players.get(dogUuid);
		if (humanData == null || dogData == null) {
			return false;
		}
		// 先把狗的整套数据搬给原来的人，再把角色对调
		copyDogFields(dogData, humanData);
		humanData.role = Role.DOG;
		dogData.role = Role.HUMAN;
		humanId = dogUuid;
		dogIds.clear();
		dogIds.add(humanUuid);
		spectatorIds.remove(humanUuid);
		spectatorIds.remove(dogUuid);

		// 新狗
		com.humananddog.mechanic.DogMechanic.setupDog(human);
		if (!humanData.wolfArmor.isEmpty()) {
			com.humananddog.mechanic.DogMechanic.applyArmorModifier(human, true);
		}
		human.refreshDimensions();
		human.setGameMode(GameType.SURVIVAL);
		// 新人
		clearDogFields(dogData);
		applyHumanBody(dog);
		dog.setGameMode(GameType.SURVIVAL);

		// 原来那只狗现在是人了：先让客户端把它的狼外观删掉
		broadcastClearDogVisual(dogUuid);
		broadcastDogVisual(human);
		offlineParticipants.clear();
		syncRoleState();
		syncAll();
		save();
		return true;
	}

	/** 退出狗的身份：收回狼铠加成、清掉坐姿，并通知客户端别再把这个人画成狼。 */
	private void leaveDogRole(ServerPlayer player, PlayerData data) {
		if (dogIds.remove(player.getUUID())) {
			com.humananddog.mechanic.DogMechanic.applyArmorModifier(player, false);
			broadcastClearDogVisual(player.getUUID());
		}
		if (data.role == Role.DOG) {
			data.sitting = false;
			data.sitPosSet = false;
		}
	}

	/** 告诉所有客户端：这个 UUID 已经不是狗了。 */
	private void broadcastClearDogVisual(UUID id) {
		ClearDogVisualPayload payload = new ClearDogVisualPayload(id.toString());
		for (ServerPlayer p : PlayerLookup.all(server)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** 把玩家身体恢复成"人"：20 血上限、去掉狼铠护甲、碰撞箱回到玩家尺寸。 */
	private void applyHumanBody(ServerPlayer player) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(20.0);
		}
		if (player.getHealth() > 20.0F) {
			player.setHealth(20.0F);
		}
		com.humananddog.mechanic.DogMechanic.applyArmorModifier(player, false);
		player.refreshDimensions();
	}

	/** 复制狗相关字段（互换身份时用）。 */
	private static void copyDogFields(PlayerData from, PlayerData to) {
		to.dogVariant = from.dogVariant;
		to.variantConfirmed = from.variantConfirmed;
		to.collarColor = from.collarColor;
		to.wolfArmor = from.wolfArmor.isEmpty() ? ItemStack.EMPTY : from.wolfArmor.copy();
		to.sitting = false;
		to.sitPosSet = false;
		to.baby = from.baby;
		to.ageConfirmed = from.ageConfirmed;
		to.ageLocked = from.ageLocked;
		to.growthTicks = from.growthTicks;
		to.loveTicks = 0;
		to.breedTicks = 0;
		to.breedCooldown = from.breedCooldown;
	}

	/** 清空狗相关字段（变成人之后，下次当狗需要重新选年龄和类型）。 */
	private static void clearDogFields(PlayerData data) {
		data.dogVariant = "";
		data.variantConfirmed = false;
		data.collarColor = "red";
		data.wolfArmor = ItemStack.EMPTY;
		data.sitting = false;
		data.sitPosSet = false;
		data.baby = false;
		data.ageConfirmed = false;
		data.ageLocked = false;
		data.growthTicks = 0;
		data.loveTicks = 0;
		data.breedTicks = 0;
		data.breedCooldown = 0;
	}

	/**
	 * 重新评估"现在能不能开局 / 继续游戏"。
	 * 该方法是幂等的，等待阶段每个 tick 都会被 {@link #tickAlways()} 调用一次。
	 */
	public void recheckStart() {
		if (phase == GamePhase.PLAYING || phase == GamePhase.COUNTDOWN) {
			return;
		}
		// 掉线等待：人和狗都回来了就继续（不重新开局、不传送）
		if (phase == GamePhase.WAITING_PARTNER) {
			if (offlineParticipants.isEmpty() && human() != null && !dogs().isEmpty()) {
				setPhase(GamePhase.PLAYING);
				HumanAndDogMod.LOGGER.info("人狗同行：伙伴已回来，继续游戏");
				syncAll();
				save();
			} else {
				syncIfChanged();
			}
			return;
		}
		// 还没人当人：没有狗时停在房主选角色界面，已经有狗时等第二个玩家来当人
		if (humanId == null) {
			stayWaiting(roleCount(Role.DOG) == 0 ? GamePhase.WAITING_HUMAN : GamePhase.WAITING_DOGS, "还没有人");
			return;
		}
		if (roleCount(Role.DOG) == 0) {
			if (com.humananddog.dev.DevFlags.SOLO && human() != null) {
				// 开发自测：单人没有狗也直接开局，用于验证开局流程与存档写盘
				startGame();
				return;
			}
			stayWaiting(GamePhase.WAITING_DOGS, "还没有狗");
			return;
		}
		// 狗已就绪但人不在线（掉线 / 还没回来）：保持等待
		ServerPlayer human = human();
		if (human == null) {
			stayWaiting(GamePhase.WAITING_DOGS, "人不在线");
			return;
		}
		// 只要求"在线"的狗已确认变种：存档里已离线、再也不会回来的旧狗
		// 不应该把整局永久卡死在等待界面（原实现按 dogIds 全量判断，离线即死锁）。
		List<ServerPlayer> onlineDogs = dogs();
		if (onlineDogs.isEmpty()) {
			stayWaiting(GamePhase.WAITING_DOGS, "狗不在线");
			return;
		}
		boolean allConfirmed = true;
		for (ServerPlayer dog : onlineDogs) {
			PlayerData d = data(dog);
			if (d == null || !d.variantConfirmed) {
				allConfirmed = false;
				break;
			}
		}
		if (!allConfirmed) {
			stayWaiting(GamePhase.WAITING_DOGS, "狗还没确认类型");
			return;
		}
		if (loadedFromSave) {
			// 从存档恢复：人狗到齐后直接继续游戏，不重新传送 / 不重置出生点
			loadedFromSave = false;
			setPhase(GamePhase.PLAYING);
			HumanAndDogMod.LOGGER.info("人狗同行：从存档恢复，继续游戏");
			syncAll();
			save();
			return;
		}
		startGame();
	}

	// ---------------------------------------------------------------------
	// 狗身侧挂方块
	// ---------------------------------------------------------------------

	/** 每只狗左右两侧的虚拟容器（箱子一类才有内容），按需创建。 */
	private final Map<UUID, net.minecraft.world.Container[]> blockContainers = new HashMap<>();

	/** 方块 id 能否作为"可挂载方块"（有物品形态、不是空气）。 */
	public static boolean isAttachable(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
		return block != net.minecraft.world.level.block.Blocks.AIR;
	}

	/**
	 * 人拿着方块右键狗：挂到左侧，左侧有就挂右侧，两侧都有就替换左侧（旧方块还给玩家）。
	 *
	 * @return 是否真的挂上了
	 */
	public boolean attachBlock(ServerPlayer dog, ServerPlayer human, ItemStack stack) {
		return attachBlock(dog, human, stack, null);
	}

	/**
	 * 同上，但可以指定优先挂哪一侧（shift+右键时按"人站在狗的哪一边"决定）。
	 *
	 * @param preferredLeft 优先挂左侧；该侧已占且另一侧空着时优先挂空的那侧
	 */
	public boolean attachBlock(ServerPlayer dog, ServerPlayer human, ItemStack stack, Boolean preferredLeft) {
		PlayerData data = data(dog);
		if (data == null || !isAttachable(stack)) {
			return false;
		}
		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
			net.minecraft.world.level.block.Block.byItem(stack.getItem())).toString();
		boolean left;
		if (preferredLeft == null) {
			// 老行为：左 → 右 → 替换左
			left = data.leftBlock.isEmpty() || !data.rightBlock.isEmpty();
		} else if (preferredLeft) {
			if (data.leftBlock.isEmpty()) {
				left = true;                     // 优先侧空 → 用优先侧
			} else if (data.rightBlock.isEmpty()) {
				left = false;                    // 优先侧占用、另一侧空 → 用另一侧
			} else {
				left = true;                     // 两侧都满 → 替换优先侧
			}
		} else {
			if (data.rightBlock.isEmpty()) {
				left = false;
			} else if (data.leftBlock.isEmpty()) {
				left = true;
			} else {
				left = false;
			}
		}
		String old = left ? data.leftBlock : data.rightBlock;
		if (!old.isEmpty()) {
			// 替换：把旧方块还给玩家
			net.minecraft.world.level.block.Block oldBlock =
				net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse(old));
			if (oldBlock != null && oldBlock != net.minecraft.world.level.block.Blocks.AIR) {
				ItemStack back = new ItemStack(oldBlock);
				if (!human.getInventory().add(back)) {
					human.drop(back, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			// 旧容器的东西一并还回去
			net.minecraft.world.Container oldContainer = containerFor(data, left);
			for (int i = 0; i < oldContainer.getContainerSize(); i++) {
				ItemStack s = oldContainer.getItem(i);
				if (!s.isEmpty() && !human.getInventory().add(s)) {
					human.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			oldContainer.clearContent();
			if (left) {
				data.leftItems.clear();
			} else {
				data.rightItems.clear();
			}
		}
		if (left) {
			data.leftBlock = id;
		} else {
			data.rightBlock = id;
		}
		stack.consume(1, human);
		// 立刻建好容器：熔炉要从挂上那一刻就开始烧，而不是等第一次打开界面
		containerFor(data, left);
		broadcastDogBlocks(dog);
		save();
		HumanAndDogMod.LOGGER.info("狗 {} 挂上了方块 {}（{}）", dog.getName().getString(), id, left ? "左" : "右");
		return true;
	}

	/**
	 * 主人按下"取出挂件"快捷键：把附近（8 格内最近的）狗身上挂的方块全部取下，
	 * 方块本身与容器里的东西一并放回主人背包，背包满了就掉在脚边。
	 */
	public void takeBlocksFromNearbyDog(ServerPlayer human) {
		if (!isHuman(human.getUUID())) {
			human.sendSystemMessage(Component.translatable("human-and-dog.message.take_blocks.not_human"));
			return;
		}
		ServerPlayer target = null;
		double best = 8.0 * 8.0;
		for (ServerPlayer dog : dogs()) {
			if (dog.level() != human.level()) {
				continue;
			}
			double d = human.distanceToSqr(dog);
			if (d <= best) {
				best = d;
				target = dog;
			}
		}
		if (target == null) {
			human.sendSystemMessage(Component.translatable("human-and-dog.message.take_blocks.no_dog"));
			return;
		}
		PlayerData data = data(target);
		if (data == null || (data.leftBlock.isEmpty() && data.rightBlock.isEmpty())) {
			human.sendSystemMessage(Component.translatable("human-and-dog.message.take_blocks.empty"));
			return;
		}
		for (int side = 0; side < 2; side++) {
			boolean left = side == 0;
			String blockId = left ? data.leftBlock : data.rightBlock;
			if (blockId.isEmpty()) {
				continue;
			}
			// 先把容器里的东西还给主人，再还方块本体
			net.minecraft.world.Container container = containerFor(data, left);
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (!stack.isEmpty()) {
					giveOrDrop(human, stack.copy());
				}
			}
			container.clearContent();
			net.minecraft.world.level.block.Block block =
				net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
			if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
				giveOrDrop(human, new ItemStack(block));
			}
			if (left) {
				data.leftBlock = "";
				data.leftItems.clear();
			} else {
				data.rightBlock = "";
				data.rightItems.clear();
			}
			blockContainers.getOrDefault(data.uuid, new net.minecraft.world.Container[2])[side] = null;
		}
		broadcastDogBlocks(target);
		save();
		human.sendSystemMessage(Component.translatable("human-and-dog.message.take_blocks.done",
			target.getName()));
	}

	/** 塞进背包，塞不下就掉在玩家脚边。 */
	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/**
	 * 人站在狗的哪一侧（真 = 左侧）。渲染挂件与"shift+右键挂方块"共用这一套方位判断。
	 */
	public boolean isHumanOnDogLeft(ServerPlayer human, ServerPlayer dog) {
		net.minecraft.world.phys.Vec3 look = dog.getLookAngle();
		net.minecraft.world.phys.Vec3 right = new net.minecraft.world.phys.Vec3(look.z, 0, -look.x);
		net.minecraft.world.phys.Vec3 toHuman =
			new net.minecraft.world.phys.Vec3(human.getX() - dog.getX(), 0, human.getZ() - dog.getZ());
		return toHuman.dot(right) < 0;
	}

	/**
	 * shift+右键狗：打开它身上方块的界面。
	 * 两侧都有时，按"人站在狗的哪一侧"来选。
	 *
	 * @return 是否打开了界面
	 */
	public boolean openDogBlockMenu(ServerPlayer human, ServerPlayer dog) {
		PlayerData data = data(dog);
		if (data == null) {
			return false;
		}
		boolean left;
		if (data.leftBlock.isEmpty() && data.rightBlock.isEmpty()) {
			return false;
		} else if (data.rightBlock.isEmpty()) {
			left = true;
		} else if (data.leftBlock.isEmpty()) {
			left = false;
		} else {
			left = isHumanOnDogLeft(human, dog);
		}
		String id = left ? data.leftBlock : data.rightBlock;
		net.minecraft.world.level.block.Block block =
			net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
		if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
			return false;
		}
		if (block instanceof net.minecraft.world.level.block.CraftingTableBlock) {
			human.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.CraftingMenu(
					cid, inv, net.minecraft.world.inventory.ContainerLevelAccess.NULL),
				Component.translatable("container.crafting")));
			return true;
		}
		// 熔炉 / 高炉 / 烟熏炉：真正的炉子，能烧、能看进度
		if (block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock) {
			if (containerFor(data, left) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace) {
				human.openMenu(new net.minecraft.world.SimpleMenuProvider(
					(cid, inv, p) -> new net.minecraft.world.inventory.FurnaceMenu(
						cid, inv, furnace,
						((com.humananddog.mixin.FurnaceAccessor) furnace).humananddog$dataAccess()),
					block.getName()));
				return true;
			}
		}
		if (isStorageBlock(block)) {
			net.minecraft.world.Container container = containerFor(data, left);
			human.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(cid, inv, container),
				block.getName()));
			return true;
		}
		// 末影箱：直接打开主人自己的末影箱库存（和原版一样，谁开看谁的）
		if (block instanceof net.minecraft.world.level.block.EnderChestBlock) {
			human.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.ChestMenu(
					net.minecraft.world.inventory.MenuType.GENERIC_9x3, cid, inv,
					p.getEnderChestInventory(), 3),
				block.getName()));
			return true;
		}
		// 酿造台：5 格容器（花哨的酿造进度没实现，但东西能放、能拿）
		if (block instanceof net.minecraft.world.level.block.BrewingStandBlock) {
			net.minecraft.world.Container container = containerFor(data, left);
			net.minecraft.world.inventory.ContainerData brewingData =
				new net.minecraft.world.inventory.SimpleContainerData(2);
			human.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.BrewingStandMenu(
					cid, inv, container, brewingData),
				block.getName()));
			return true;
		}
		// 讲台
		if (block instanceof net.minecraft.world.level.block.LecternBlock) {
			net.minecraft.world.Container container = containerFor(data, left);
			human.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.LecternMenu(
					cid, container, new net.minecraft.world.inventory.SimpleContainerData(1)),
				block.getName()));
			return true;
		}
		// 信标
		if (block instanceof net.minecraft.world.level.block.BeaconBlock) {
			human.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.BeaconMenu(
					cid, inv, new net.minecraft.world.inventory.SimpleContainerData(3),
					net.minecraft.world.inventory.ContainerLevelAccess.NULL),
				block.getName()));
			return true;
		}
		// 其它"点开就有一个原版界面"的方块
		if (openUtilityMenu(human, block)) {
			return true;
		}
		human.sendSystemMessage(Component.translatable("human-and-dog.message.block_unsupported", block.getName()));
		return true;
	}

	/**
	 * 一堆"原版自带界面"的方块：切石机、砂轮、织布机、锻造台、铁砧、附魔台、制图台。
	 * 都使用原版的两参构造（ContainerLevelAccess.NULL），界面能正常用。
	 */
	private static boolean openUtilityMenu(ServerPlayer human, net.minecraft.world.level.block.Block block) {
		net.minecraft.world.SimpleMenuProvider provider = null;
		if (block instanceof net.minecraft.world.level.block.StonecutterBlock) {
			provider = new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.StonecutterMenu(cid, inv), block.getName());
		} else if (block instanceof net.minecraft.world.level.block.GrindstoneBlock) {
			provider = new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.GrindstoneMenu(cid, inv), block.getName());
		} else if (block instanceof net.minecraft.world.level.block.LoomBlock) {
			provider = new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.LoomMenu(cid, inv), block.getName());
		} else if (block instanceof net.minecraft.world.level.block.SmithingTableBlock) {
			provider = new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.SmithingMenu(cid, inv), block.getName());
		} else if (block instanceof net.minecraft.world.level.block.AnvilBlock) {
			provider = new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.AnvilMenu(cid, inv), block.getName());
		} else if (block instanceof net.minecraft.world.level.block.EnchantingTableBlock) {
			provider = new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.EnchantmentMenu(cid, inv), block.getName());
		} else if (block instanceof net.minecraft.world.level.block.CartographyTableBlock) {
			provider = new net.minecraft.world.SimpleMenuProvider(
				(cid, inv, p) -> new net.minecraft.world.inventory.CartographyTableMenu(cid, inv), block.getName());
		}
		if (provider == null) {
			return false;
		}
		human.openMenu(provider);
		return true;
	}

	/** 目前能打开界面的存储类方块。 */
	private static boolean isStorageBlock(net.minecraft.world.level.block.Block block) {
		return block instanceof net.minecraft.world.level.block.ChestBlock
			|| block instanceof net.minecraft.world.level.block.BarrelBlock
			|| block instanceof net.minecraft.world.level.block.ShulkerBoxBlock
			|| block instanceof net.minecraft.world.level.block.HopperBlock
			|| block instanceof net.minecraft.world.level.block.DispenserBlock
			|| block instanceof net.minecraft.world.level.block.DropperBlock
			|| block instanceof net.minecraft.world.level.block.CrafterBlock;
	}

	/** 挂件容器格数：炉子 3、酿造台 5、讲台 1，其余（箱子一类）27。 */
	private static int containerSizeFor(net.minecraft.world.level.block.Block block) {
		if (block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock) {
			return 3;
		}
		if (block instanceof net.minecraft.world.level.block.BrewingStandBlock) {
			return 5;
		}
		if (block instanceof net.minecraft.world.level.block.LecternBlock) {
			return 1;
		}
		return 27;
	}

	/** 取（并按需创建）某只狗某一侧的挂件容器：普通方块 27 格，熔炉类为真正的炉子实体。 */
	private net.minecraft.world.Container containerFor(PlayerData data, boolean left) {
		String blockId = left ? data.leftBlock : data.rightBlock;
		net.minecraft.world.level.block.Block block = blockId.isEmpty()
			? net.minecraft.world.level.block.Blocks.AIR
			: net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
		boolean furnaceType = block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock;
		int size = containerSizeFor(block);

		net.minecraft.world.Container[] pair =
			blockContainers.computeIfAbsent(data.uuid, k -> new net.minecraft.world.Container[2]);
		int idx = left ? 0 : 1;
		net.minecraft.world.Container existing = pair[idx];
		boolean reusable = existing != null
			&& existing.getContainerSize() == size
			&& (existing instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) == furnaceType;
		if (reusable) {
			return existing;
		}

		net.minecraft.world.Container container;
		if (furnaceType) {
			container = createFurnace(block, server.overworld());
		} else {
			container = new net.minecraft.world.SimpleContainer(size);
		}
		java.util.List<ItemStack> src = left ? data.leftItems : data.rightItems;
		for (int i = 0; i < Math.min(size, src.size()); i++) {
			container.setItem(i, src.get(i));
		}
		pair[idx] = container;
		return container;
	}

	/**
	 * 造一个"虚拟熔炉"方块实体：它不在世界里，只用来借原版的烧炼逻辑与界面。
	 *
	 * <p>位置故意放在世界最低点以下：{@code AbstractFurnaceBlockEntity.serverTick}
	 * 在"点燃状态变化"时会调用 {@code level.setBlockAndUpdate(pos, state)}，
	 * 而该坐标超出建造高度，原版会直接返回 false，绝不会真的在世界里放一个熔炉。
	 *
	 * <p>另外必须覆盖 {@code stillValid}：原版 {@code BaseContainerBlockEntity.stillValid}
	 * 会校验"玩家离该方块 4 格内且该坐标仍是这个方块实体"，虚拟炉子两条都不成立，
	 * 于是菜单会在打开后的下一 tick 被服务端判定失效、瞬间关闭。
	 */
	private net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity createFurnace(
		net.minecraft.world.level.block.Block block, net.minecraft.server.level.ServerLevel level) {
		net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(0, level.getMinY() - 16, 0);
		net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
		net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace;
		if (block instanceof net.minecraft.world.level.block.BlastFurnaceBlock) {
			furnace = new net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity(pos, state) {
				@Override
				public boolean stillValid(net.minecraft.world.entity.player.Player player) {
					return true;
				}
			};
		} else if (block instanceof net.minecraft.world.level.block.SmokerBlock) {
			furnace = new net.minecraft.world.level.block.entity.SmokerBlockEntity(pos, state) {
				@Override
				public boolean stillValid(net.minecraft.world.entity.player.Player player) {
					return true;
				}
			};
		} else {
			furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(pos, state) {
				@Override
				public boolean stillValid(net.minecraft.world.entity.player.Player player) {
					return true;
				}
			};
		}
		furnace.setLevel(level);
		return furnace;
	}

	/** 每侧上一次的燃烧状态，用来只在变化时广播（避免每 tick 发包）。 */
	private final Map<UUID, boolean[]> lastLit = new HashMap<>();

	/** 每 tick 驱动狗背上的虚拟熔炉（真实烧炼 + 燃烧状态 / 火苗粒子）。 */
	private void tickDogFurnaces() {
		for (Map.Entry<UUID, net.minecraft.world.Container[]> entry : blockContainers.entrySet()) {
			PlayerData data = players.get(entry.getKey());
			ServerPlayer dog = findDog(data);
			if (dog == null || !(dog.level() instanceof net.minecraft.server.level.ServerLevel level)) {
				continue;
			}
			net.minecraft.world.Container[] pair = entry.getValue();
			boolean[] wasLit = lastLit.computeIfAbsent(data.uuid, k -> new boolean[2]);
			boolean litChanged = false;
			for (int side = 0; side < 2; side++) {
				if (!(pair[side] instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)) {
					continue;
				}
				String blockId = side == 0 ? data.leftBlock : data.rightBlock;
				net.minecraft.world.level.block.Block block =
					net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
				if (!(block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock)) {
					continue;
				}
				furnace.setLevel(level);
				net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(0, level.getMinY() - 16, 0);
				try {
					net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(
						level, pos, block.defaultBlockState(), furnace);
				} catch (RuntimeException e) {
					HumanAndDogMod.LOGGER.warn("[人狗同行] 虚拟熔炉 tick 失败：{}", e.toString());
				}
				// 燃烧中：挂件换成 LIT 外观（客户端处理），并从狗的身侧冒火苗 / 烟
				boolean lit = ((com.humananddog.mixin.FurnaceAccessor) furnace)
					.humananddog$dataAccess().get(0) > 0;
				if (lit != wasLit[side]) {
					wasLit[side] = lit;
					litChanged = true;
				}
				if (lit) {
					spawnFurnaceParticles(level, dog, side);
				}
			}
			if (litChanged) {
				broadcastDogBlocks(dog);
			}
		}
	}

	/** 在狗某一侧挂件的位置冒火苗与烟（原版熔炉点燃时的观感）。 */
	private void spawnFurnaceParticles(net.minecraft.server.level.ServerLevel level, ServerPlayer dog, int side) {
		long time = level.getGameTime();
		if (time % 3L != 0L) {
			return;
		}
		double yaw = Math.toRadians(dog.yBodyRot);
		double offset = side == 0 ? -0.4 : 0.4;
		double x = dog.getX() + Math.cos(yaw) * offset;
		double y = dog.getY() + 0.42;
		double z = dog.getZ() + Math.sin(yaw) * offset;
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
			x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
		if (time % 9L == 0L) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
				x, y + 0.15, z, 1, 0.02, 0.02, 0.02, 0.005);
		}
	}

	/** 按 UUID 找在线的狗玩家。 */
	private ServerPlayer findDog(PlayerData data) {
		if (data == null || !data.isDog()) {
			return null;
		}
		for (ServerPlayer p : dogs()) {
			if (p.getUUID().equals(data.uuid)) {
				return p;
			}
		}
		return null;
	}

	/** 把狗身上挂了什么同步给所有客户端（客户端据此渲染方块）。 */
	public void broadcastDogBlocks(ServerPlayer dog) {
		PlayerData data = data(dog);
		if (data == null) {
			return;
		}
		DogBlocksPayload payload = new DogBlocksPayload(
			dog.getUUID().toString(), data.leftBlock, data.rightBlock,
			isSideLit(data, true), isSideLit(data, false));
		for (ServerPlayer p : PlayerLookup.all(server)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** 某一侧挂件当前是否"正在燃烧"（只有炉子类会）。 */
	private boolean isSideLit(PlayerData data, boolean left) {
		net.minecraft.world.Container[] pair = blockContainers.get(data.uuid);
		if (pair == null) {
			return false;
		}
		net.minecraft.world.Container container = pair[left ? 0 : 1];
		if (container instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace) {
			return ((com.humananddog.mixin.FurnaceAccessor) furnace).humananddog$dataAccess().get(0) > 0;
		}
		return false;
	}

	/** 狗身上是否挂着指定方块。 */
	public boolean hasBlock(ServerPlayer dog, net.minecraft.world.level.block.Block block) {
		PlayerData data = data(dog);
		if (data == null) {
			return false;
		}
		for (String id : new String[]{data.leftBlock, data.rightBlock}) {
			if (id.isEmpty()) {
				continue;
			}
			net.minecraft.world.level.block.Block attached =
				net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
			if (attached == block) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 狗身上挂着唱片机时，主人拿唱片 shift+右键：按原版唱片机的音量与音源播放这首歌。
	 *
	 * @return 是否成功播了歌
	 */
	public boolean playDisc(ServerPlayer dog, ServerPlayer human, ItemStack disc) {
		net.minecraft.world.item.JukeboxPlayable playable = disc.get(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);
		if (playable == null || !hasBlock(dog, net.minecraft.world.level.block.Blocks.JUKEBOX)) {
			return false;
		}
		net.minecraft.world.item.JukeboxSong song = playable.song().value();
		net.minecraft.sounds.SoundEvent event = song.soundEvent().value();
		Identifier soundId = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(event);
		if (soundId != null) {
			// 交给客户端自己播放并持有 SoundInstance：这样把唱片机取下来时能立刻停掉。
			// 直接用 level.playSound 是"放出去就不管"的，取下唱片机音乐也不会停。
			DiscPayload payload = new DiscPayload(dog.getUUID().toString(), soundId.toString(),
				dog.getX(), dog.getY(), dog.getZ());
			for (ServerPlayer p : PlayerLookup.all(server)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
		if (dog.level() instanceof net.minecraft.server.level.ServerLevel level) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
				dog.getX(), dog.getY() + 1.2, dog.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
		}
		human.sendSystemMessage(Component.translatable("human-and-dog.message.disc.playing",
			song.description()));
		return true;
	}


	/** 每 tick 把虚拟容器的内容写回玩家数据（菜单里改动后存档才拿得到）。 */
	private void syncBlockContainers() {
		for (Map.Entry<UUID, net.minecraft.world.Container[]> entry : blockContainers.entrySet()) {
			PlayerData data = players.get(entry.getKey());
			if (data == null) {
				continue;
			}
			net.minecraft.world.Container[] pair = entry.getValue();
			for (int side = 0; side < 2; side++) {
				net.minecraft.world.Container container = pair[side];
				if (container == null) {
					continue;
				}
				java.util.List<ItemStack> dst = side == 0 ? data.leftItems : data.rightItems;
				boolean changed = dst.size() != container.getContainerSize();
				for (int i = 0; i < container.getContainerSize(); i++) {
					ItemStack now = container.getItem(i);
					if (i >= dst.size() || !ItemStack.matches(dst.get(i), now)) {
						changed = true;
					}
					if (i >= dst.size()) {
						dst.add(now.copy());
					} else {
						dst.set(i, now.copy());
					}
				}
				if (changed) {
					save();
				}
			}
		}
	}

	/** 把所有狗身上的方块情况发给某个玩家（进服 / 重连时用）。 */
	public void sendAllDogBlocksTo(ServerPlayer player) {
		for (ServerPlayer dog : dogs()) {
			PlayerData data = data(dog);
			if (data != null) {
				ServerPlayNetworking.send(player, new DogBlocksPayload(
					dog.getUUID().toString(), data.leftBlock, data.rightBlock,
					isSideLit(data, true), isSideLit(data, false)));
			}
		}
		if (isDog(player.getUUID())) {
			// 自己就是狗：也要把"自己身上挂了什么"推给自己（第三人称要看得到）
			broadcastDogBlocks(player);
		}
	}

	/** 上一次停在等待的原因，避免暂停期间每个 tick 刷同一条日志。 */
	private String lastWaitReason;

	/**
	 * 停在等待阶段，并把"为什么还没开局"记一条日志（只在原因变化时输出）。
	 * 读档后卡在等待界面时，看这条日志就能知道是缺人、缺狗，还是狗没确认类型。
	 */
	private void stayWaiting(GamePhase next, String reason) {
		if (!reason.equals(lastWaitReason)) {
			lastWaitReason = reason;
			HumanAndDogMod.LOGGER.info("[人狗同行] 停在等待：{}｜人={} 狗名单={} 在线狗={} 读档恢复={}",
				reason,
				humanId == null ? "无" : (human() != null ? "在线" : "离线"),
				dogIds.size(), dogs().size(), loadedFromSave);
		}
		setPhase(next);
		syncIfChanged();
	}

	/** 切换阶段（只在真正变化时记录日志）。 */
	private void setPhase(GamePhase next) {
		if (phase != next) {
			HumanAndDogMod.LOGGER.info("[人狗同行] 阶段 {} -> {}", phase, next);
			phase = next;
		}
	}

	private void startGame() {
		ServerPlayer human = human();
		if (human == null) {
			return;
		}
		// 统一出生点：所有狗传送到人的位置，且无任何偏差
		ServerLevel level = (ServerLevel) human.level();
		double x = human.getX();
		double y = human.getY();
		double z = human.getZ();
		float yRot = human.getYRot();
		float xRot = human.getXRot();
		for (ServerPlayer dog : dogs()) {
			dog.teleportTo(level, x, y, z, java.util.Set.of(), yRot, xRot, false);
		}
		sharedRespawn = GlobalPos.of(level.dimension(), BlockPos.containing(x, y, z));
		setPhase(GamePhase.PLAYING);
		HumanAndDogMod.LOGGER.info("人狗同行：游戏开始，共同出生点 {}", sharedRespawn);
		syncAll();
		// 观战者一起跟到人狗所在维度，方便看
		for (UUID spectatorId : spectatorIds) {
			ServerPlayer spectator = server.getPlayerList().getPlayer(spectatorId);
			if (spectator != null) {
				spectator.teleportTo(level, x, y, z, java.util.Set.of(), spectator.getYRot(), spectator.getXRot(), false);
			}
		}
		// 人和狗都就位：全服广播欢迎语（文案走语言文件，随客户端语言切换）
		if (human() != null && !dogs().isEmpty()) {
			Component welcome = Component.translatable("human-and-dog.message.welcome");
			for (ServerPlayer p : PlayerLookup.all(server)) {
				p.sendSystemMessage(welcome);
			}
		}
		syncRoleState();
		save();
	}

	// ---------------------------------------------------------------------
	// 共享重生点
	// ---------------------------------------------------------------------

	public void setSharedRespawn(GlobalPos pos) {
		this.sharedRespawn = pos;
		save();
	}

	// ---------------------------------------------------------------------
	// 死亡联动 / 重生倒计时
	// ---------------------------------------------------------------------

	public void triggerDeathLink() {
		if (phase == GamePhase.COUNTDOWN || phase == GamePhase.WAITING_HUMAN || phase == GamePhase.WAITING_DOGS) {
			return;
		}
		setPhase(GamePhase.COUNTDOWN);
		countdownTicks = RESPAWN_COUNTDOWN_SECONDS * 20;
		countdownLastSecond = RESPAWN_COUNTDOWN_SECONDS;
		// 立即定格全员并把血量抬到 1，避免 0 血触发死亡屏，也防止虚空继续坠落
		freezeSpots.clear();
		for (ServerPlayer p : allParticipants()) {
			freezeSpots.put(p.getUUID(), new FrozenSpot((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ()));
			p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			p.clearFire();
			p.setRemainingFireTicks(0);
			if (p.getHealth() < 1.0F) {
				p.setHealth(1.0F);
			}
		}
		HumanAndDogMod.LOGGER.info("触发死亡联动，开始 {} 秒倒计时", RESPAWN_COUNTDOWN_SECONDS);
		syncAll();
	}

	// ---------------------------------------------------------------------
	// 每 tick
	// ---------------------------------------------------------------------

	public void tick() {
		syncBlockContainers();
		// 狗背上的熔炉要真的会烧：每 tick 用原版烧炼逻辑驱动一次
		tickDogFurnaces();
		// 挂件同步自愈：每 20 tick 全量重发一次。重连 / 丢包时客户端也能补上
		// （之前只在本 tick 的 JOIN 里发一次，客户端还没就绪就会漏掉，表现为"重新进世界看不到挂件"）。
		if (server.getTickCount() % 20 == 0) {
			for (ServerPlayer p : PlayerLookup.all(server)) {
				sendAllDogBlocksTo(p);
			}
		}
		// 指令始终强制开启且无法关闭
		server.getWorldData().setAllowCommands(true);

		// 人狗同坐标出生，必须彼此无碰撞推挤（用原版队伍碰撞规则，双端生效）
		ensureNoCollisionTeam();

		// 阶段自愈（暂停时由 IntegratedServerMixin 直接调用，见 tickAlways）
		tickAlways();

		if (phase == GamePhase.COUNTDOWN) {
			// 每 tick 把全员钉在死亡位置，防止重力 / 虚空 / 火焰影响倒计时
			for (ServerPlayer p : allParticipants()) {
				FrozenSpot spot = freezeSpots.get(p.getUUID());
				if (spot != null) {
					p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
					p.clearFire();
					if (p.level() == spot.level()) {
						p.setPos(spot.x(), spot.y(), spot.z());
					}
					if (p.getHealth() < 1.0F) {
						p.setHealth(1.0F);
					}
				}
			}
			countdownTicks--;
			int second = Math.max(0, (countdownTicks + 19) / 20);
			if (second != countdownLastSecond) {
				countdownLastSecond = second;
				GameStatePayload payload = new GameStatePayload(GamePhase.COUNTDOWN.name(), second);
				lastBroadcastSignature = stateSignature();
				for (ServerPlayer p : PlayerLookup.all(server)) {
					ServerPlayNetworking.send(p, payload);
				}
			}
			if (countdownTicks <= 0) {
				finishRespawn();
			}
		}
		if (phase == GamePhase.PLAYING) {
			// 拴绳：10 格外软减速，15 格外锁死远离
			com.humananddog.mechanic.LeashMechanic.applyBondSpeedLimit(this);
			// 外观兜底：加入时序异常时客户端可能漏收某只狗的外观（表现为看不到替身狼 / 点不到狗），
			// 每 5 秒静默重播一次（不写存档）。
			if (++visualResyncTicks >= 100) {
				visualResyncTicks = 0;
				resyncDogVisuals();
			}
		}
		// 每 tick 锁定狗的饥饿
		for (ServerPlayer dog : dogs()) {
			PlayerData d = data(dog);
			if (d != null) {
				com.humananddog.mechanic.DogMechanic.tickDog(this, dog, d);
			}
		}
	}

	/**
	 * 与"世界是否暂停"无关的每 tick 维护：等待阶段持续重新评估开局条件。
	 *
	 * <p>关键：等待阶段集成服务器会强制暂停世界（见 IntegratedServerMixin），
	 * 此时 MinecraftServer.tickServer 不会执行，END_SERVER_TICK 事件也就不会触发。
	 * 因此本方法额外由 IntegratedServerMixin 在 tickServer 头部直接调用，
	 * 否则阶段一旦停在等待（旧实现里 assignDog / 存档恢复都可能停在那里）就永远出不来。
	 */
	public void tickAlways() {
		if (phase == GamePhase.WAITING_DOGS || phase == GamePhase.WAITING_PARTNER) {
			recheckStart();
		}
	}

	private void finishRespawn() {
		// 由 RespawnMechanic 执行真正的复活与传送
		com.humananddog.mechanic.RespawnMechanic.performRespawn(this);
		setPhase(GamePhase.PLAYING);
		syncAll();
	}

	/** 队伍名（隐藏不显示在玩家名牌上；只用来关掉"队友可见隐形"）。 */
	private static final String NO_COLLISION_TEAM = "hd_nocollision";

	/**
	 * 把本局参与者放进同一个记分板队伍，只为了关掉"队友可见隐形"
	 * （否则客户端把狗本体设为隐身后，队友仍能看到半透明人形）。
	 *
	 * <p>碰撞规则必须是 {@code ALWAYS}：原版 {@code EntitySelector.pushableBy()} 里，
	 * 实体自己队伍的规则为 {@code NEVER} 时"任何人都推不动它"（直接 alwaysFalse），
	 * 之前设成 NEVER 就是为了防人狗互推，结果连生物都挤不动狗了。
	 * 现在改为由 {@code LivingEntityPushMixin} 只取消"人↔狗"之间的互推，
	 * 其他玩家与生物都能正常挤压狗。
	 */
	private void ensureNoCollisionTeam() {
		PlayerTeam team = server.getScoreboard().getPlayerTeam(NO_COLLISION_TEAM);
		if (team == null) {
			team = server.getScoreboard().addPlayerTeam(NO_COLLISION_TEAM);
		}
		if (team.getCollisionRule() != Team.CollisionRule.ALWAYS) {
			team.setCollisionRule(Team.CollisionRule.ALWAYS);
		}
		// 队伍默认 seeFriendlyInvisibles=true，而原版 Entity.isInvisibleTo() 的规则是
		// "同一队伍且队友可见隐形 → 隐形对队友无效"。客户端把狗玩家本体设为隐身后，
		// 由于人狗被放进了同一个队伍，本体对彼此仍然"可见"，于是被 LivingEntityRenderer
		// 用半透明材质画出来，表现就是狼身上叠着一个人影。这里把该规则关掉。
		if (team.canSeeFriendlyInvisibles()) {
			team.setSeeFriendlyInvisibles(false);
		}
		for (ServerPlayer p : allParticipants()) {
			if (server.getScoreboard().getPlayersTeam(p.getScoreboardName()) != team) {
				server.getScoreboard().addPlayerToTeam(p.getScoreboardName(), team);
			}
		}
	}

	// ---------------------------------------------------------------------
	// 网络同步
	// ---------------------------------------------------------------------

	/** 当前要同步给客户端的阶段签名（阶段 + 倒计时秒数）。 */
	private String stateSignature() {
		return phase.name() + "/" + (phase == GamePhase.COUNTDOWN ? countdownLastSecond : 0);
	}

	public void syncAll() {
		GameStatePayload payload = new GameStatePayload(phase.name(), phase == GamePhase.COUNTDOWN ? countdownLastSecond : 0);
		lastBroadcastSignature = stateSignature();
		for (ServerPlayer p : PlayerLookup.all(server)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** 阶段确实变化时才广播（供每 tick 的自愈检查使用，避免每 tick 刷包）。 */
	private void syncIfChanged() {
		if (!stateSignature().equals(lastBroadcastSignature)) {
			syncAll();
		}
	}

	public void syncTo(ServerPlayer player) {
		ServerPlayNetworking.send(player, new GameStatePayload(phase.name(), phase == GamePhase.COUNTDOWN ? countdownLastSecond : 0));
	}

	/** 把某只狗当前的外观数据发给指定观众（不写存档）。 */
	private void sendDogVisualTo(ServerPlayer dog, ServerPlayer viewer) {
		PlayerData d = data(dog);
		if (d == null || d.role != Role.DOG) {
			return;
		}
		DyeColor color = DyeColor.byName(d.collarColor, DyeColor.RED);
		ServerPlayNetworking.send(viewer, new DogVisualPayload(
			dog.getUUID().toString(),
			d.dogVariant,
			color.getId(),
			d.sitting,
			!d.wolfArmor.isEmpty(),
			d.baby
		));
	}

	/** 广播某只狗的外观数据给所有客户端。 */
	public void broadcastDogVisual(ServerPlayer dog) {
		PlayerData d = data(dog);
		if (d == null || d.role != Role.DOG) {
			return;
		}
		java.util.List<ServerPlayer> allViewers = new java.util.ArrayList<>(PlayerLookup.all(server));
		// JOIN 事件 server.execute 时机可能早于玩家正式进入 PlayerList，需确保狗自己也能收到自己的外观
		if (!allViewers.contains(dog)) {
			allViewers.add(dog);
		}
		for (ServerPlayer p : allViewers) {
			sendDogVisualTo(dog, p);
		}
		// 项圈颜色 / 狼铠 / 坐姿等变化一并写回存档
		save();
	}

	/** 让新加入的玩家收到所有已存在狗的外观。 */
	public void syncAllDogVisualsTo(ServerPlayer viewer) {
		for (ServerPlayer dog : dogs()) {
			sendDogVisualTo(dog, viewer);
		}
	}

	/** 静默重播所有狗的外观（不写存档），用于兜底修复客户端漏收的情况。 */
	public void resyncDogVisuals() {
		for (ServerPlayer viewer : PlayerLookup.all(server)) {
			for (ServerPlayer dog : dogs()) {
				sendDogVisualTo(dog, viewer);
			}
		}
	}
}
