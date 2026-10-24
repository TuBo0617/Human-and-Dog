package com.humananddog.save;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.humananddog.HumanAndDogMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 与存档绑定的"人狗同行"数据：每个玩家的角色、狗变种、项圈、狼铠、年龄，
 * 以及房主和共享重生点。世界保存时随存档一起写入。
 */
public class ModSaveData extends SavedData {
	/** 单个玩家的可持久化信息。 */
	public record SavedPlayer(String role, String dogVariant, boolean variantConfirmed,
							  String collarColor, boolean hasArmor,
							  boolean baby, boolean ageLocked, int growthTicks,
							  String leftBlock, String rightBlock,
							  java.util.List<net.minecraft.world.item.ItemStack> leftItems,
							  java.util.List<net.minecraft.world.item.ItemStack> rightItems) {
		public static final Codec<SavedPlayer> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.optionalFieldOf("role", "NONE").forGetter(SavedPlayer::role),
			Codec.STRING.optionalFieldOf("dogVariant", "").forGetter(SavedPlayer::dogVariant),
			Codec.BOOL.optionalFieldOf("variantConfirmed", false).forGetter(SavedPlayer::variantConfirmed),
			Codec.STRING.optionalFieldOf("collarColor", "red").forGetter(SavedPlayer::collarColor),
			Codec.BOOL.optionalFieldOf("hasArmor", false).forGetter(SavedPlayer::hasArmor),
			Codec.BOOL.optionalFieldOf("baby", false).forGetter(SavedPlayer::baby),
			Codec.BOOL.optionalFieldOf("ageLocked", false).forGetter(SavedPlayer::ageLocked),
			Codec.INT.optionalFieldOf("growthTicks", 0).forGetter(SavedPlayer::growthTicks),
			Codec.STRING.optionalFieldOf("leftBlock", "").forGetter(SavedPlayer::leftBlock),
			Codec.STRING.optionalFieldOf("rightBlock", "").forGetter(SavedPlayer::rightBlock),
			net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf()
				.optionalFieldOf("leftItems", java.util.List.of()).forGetter(SavedPlayer::leftItems),
			net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf()
				.optionalFieldOf("rightItems", java.util.List.of()).forGetter(SavedPlayer::rightItems)
		).apply(inst, SavedPlayer::new));
	}

	public Map<String, SavedPlayer> players = new HashMap<>();
	public String humanId = "";
	public boolean started = false;
	public GlobalPos respawn = null;

	public static final Codec<ModSaveData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
		Codec.unboundedMap(Codec.STRING, SavedPlayer.CODEC).fieldOf("players").forGetter((ModSaveData d) -> d.players),
		Codec.STRING.optionalFieldOf("humanId", "").forGetter((ModSaveData d) -> d.humanId),
		Codec.BOOL.optionalFieldOf("started", false).forGetter((ModSaveData d) -> d.started),
		GlobalPos.CODEC.optionalFieldOf("respawn").forGetter((ModSaveData d) -> Optional.ofNullable(d.respawn))
	).apply(inst, (players, humanId, started, respawnOpt) -> {
		ModSaveData d = new ModSaveData();
		d.players = new HashMap<>(players);
		d.humanId = humanId;
		d.started = started;
		d.respawn = respawnOpt.orElse(null);
		return d;
	}));

	public static final SavedDataType<ModSaveData> TYPE = new SavedDataType<>(
		HumanAndDogMod.id("game_data"),
		ModSaveData::new,
		CODEC,
		DataFixTypes.LEVEL
	);

	/** 从主世界数据存储加载（不存在则新建）。 */
	public static ModSaveData load(ServerLevel overworld) {
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * 开发自测：构造一份完整数据（含玩家、房主、已开始、重生点），
	 * 用 NbtOps 编码再解码，验证 Codec 往返无损。生产环境不调用。
	 */
	public static void selfTest(net.minecraft.core.RegistryAccess registries) {
		try {
			ModSaveData d = new ModSaveData();
			d.players.put(java.util.UUID.randomUUID().toString(),
				new SavedPlayer("DOG", "minecraft:pale", true, "blue", true, true, false, 0, "", "", java.util.List.of(), java.util.List.of()));
			d.players.put(java.util.UUID.randomUUID().toString(),
				new SavedPlayer("HUMAN", "", false, "red", false, false, false, 0, "", "", java.util.List.of(), java.util.List.of()));
			d.humanId = java.util.UUID.randomUUID().toString();
			d.started = true;
			d.respawn = GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD,
				net.minecraft.core.BlockPos.containing(12, 64, -7));
			com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops =
				registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
			net.minecraft.nbt.Tag encoded = CODEC.encodeStart(ops, d)
				.result().orElseThrow(() -> new IllegalStateException("编码返回空"));
			ModSaveData out = CODEC.parse(ops, encoded)
				.result().orElseThrow(() -> new IllegalStateException("解码返回空"));
			GlobalPos rp = out.respawn;
			HumanAndDogMod.LOGGER.info("[人狗同行] 存档 Codec 自测成功：玩家数={} 房主={} 已开始={} 重生点={}",
				out.players.size(), out.humanId.isEmpty() ? "空" : "有", out.started,
				rp == null ? "null" : (rp.dimension().identifier() + " @ " + rp.pos()));
		} catch (Exception e) {
			HumanAndDogMod.LOGGER.error("[人狗同行] 存档 Codec 自测失败", e);
		}
	}

	public void markChanged() {
		this.setDirty();
	}
}
