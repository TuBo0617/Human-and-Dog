package com.humananddog.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.humananddog.HumanAndDogMod;
import com.humananddog.net.payload.DogVisualPayload;
import com.humananddog.net.payload.GameStatePayload;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
				!d.wolfArmor.isEmpty()
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

	public List<ServerPlayer> dogs() {
		List<ServerPlayer> result = new ArrayList<>();
		for (UUID id : dogIds) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				result.add(p);
			}
		}
		return result;
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
				com.humananddog.mechanic.DogMechanic.setupDog(player);
				if (!data.wolfArmor.isEmpty()) {
					com.humananddog.mechanic.DogMechanic.applyArmorModifier(player, true);
				}
				if (!data.variantConfirmed) {
					ServerPlayNetworking.send(player, new OpenDogVariantScreenPayload());
				}
			}
			syncAllDogVisualsTo(player);
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
			syncTo(player);
			ServerPlayNetworking.send(player, new OpenRoleScreenPayload());
			syncRoleState();
		} else if (isRoleAvailable(Role.HUMAN) || isRoleAvailable(Role.DOG)) {
			// 还有空位：让新玩家自己选（已被占用的身份在界面上置灰）
			syncTo(player);
			ServerPlayNetworking.send(player, new OpenRoleScreenPayload());
			syncRoleState();
		} else {
			// 人狗都已就位：后来者自动进入观战
			assignSpectator(player);
		}
	}

	public void onLeave(ServerPlayer player) {
		// 保留数据以便重连；若永久退出则在会话结束时清理
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
			case DOG -> dogIds.size() < MAX_PARTICIPANTS - 1;
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
		setPhase(GamePhase.WAITING_DOGS);
		HumanAndDogMod.LOGGER.info("玩家 {} 自动成为狗", player.getName().getString());
		// 狗首次进入需要选变种（先打开变种界面，再广播等待状态，避免被等待界面覆盖）
		if (!data.variantConfirmed) {
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

	/**
	 * 重新评估"现在能不能开局 / 继续游戏"。
	 * 该方法是幂等的，等待阶段每个 tick 都会被 {@link #tickAlways()} 调用一次。
	 */
	public void recheckStart() {
		if (phase == GamePhase.PLAYING || phase == GamePhase.COUNTDOWN) {
			return;
		}
		// 还没人当人：没有狗时停在房主选角色界面，已经有狗时等第二个玩家来当人
		if (humanId == null) {
			setPhase(dogIds.isEmpty() ? GamePhase.WAITING_HUMAN : GamePhase.WAITING_DOGS);
			syncIfChanged();
			return;
		}
		if (dogIds.isEmpty()) {
			if (com.humananddog.dev.DevFlags.SOLO && human() != null) {
				// 开发自测：单人没有狗也直接开局，用于验证开局流程与存档写盘
				startGame();
				return;
			}
			setPhase(GamePhase.WAITING_DOGS);
			syncIfChanged();
			return;
		}
		// 狗已就绪但人不在线（掉线 / 还没回来）：保持等待
		ServerPlayer human = human();
		if (human == null) {
			setPhase(GamePhase.WAITING_DOGS);
			syncIfChanged();
			return;
		}
		// 只要求"在线"的狗已确认变种：存档里已离线、再也不会回来的旧狗
		// 不应该把整局永久卡死在等待界面（原实现按 dogIds 全量判断，离线即死锁）。
		List<ServerPlayer> onlineDogs = dogs();
		if (onlineDogs.isEmpty()) {
			setPhase(GamePhase.WAITING_DOGS);
			syncIfChanged();
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
			setPhase(GamePhase.WAITING_DOGS);
			syncIfChanged();
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
				com.humananddog.mechanic.DogMechanic.tickDog(dog, d);
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
		if (phase == GamePhase.WAITING_DOGS) {
			recheckStart();
		}
	}

	private void finishRespawn() {
		// 由 RespawnMechanic 执行真正的复活与传送
		com.humananddog.mechanic.RespawnMechanic.performRespawn(this);
		setPhase(GamePhase.PLAYING);
		syncAll();
	}

	/** 无碰撞队伍名（隐藏不显示在玩家名牌上，只用于关闭人狗互相推挤）。 */
	private static final String NO_COLLISION_TEAM = "hd_nocollision";

	/**
	 * 把本局所有参与者放进同一个记分板队伍并把碰撞规则设为 NEVER：
	 * 原版会据此让他们在客户端与服务端都互不碰撞、互不推挤，
	 * 从根上消除"人狗重叠出生后松开按键仍被缓慢推开"的漂移。
	 * 该规则只影响玩家之间，不影响玩家与生物 / 方块。
	 */
	private void ensureNoCollisionTeam() {
		PlayerTeam team = server.getScoreboard().getPlayerTeam(NO_COLLISION_TEAM);
		if (team == null) {
			team = server.getScoreboard().addPlayerTeam(NO_COLLISION_TEAM);
			team.setCollisionRule(Team.CollisionRule.NEVER);
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
			!d.wolfArmor.isEmpty()
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
