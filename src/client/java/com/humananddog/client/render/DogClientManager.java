package com.humananddog.client.render;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.humananddog.client.ClientState;
import com.humananddog.role.DogLookup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 每个客户端维护"狗玩家 -> 替身狼"的映射并逐帧同步，
 * 由原版实体渲染管线自动把替身狼画出来，同时隐藏狗玩家本体。
 */
public final class DogClientManager {
	private static final Map<UUID, DummyWolf> WOLVES = new HashMap<>();
	private static int nextId = -900000;
	/** 当前替身狼所属的客户端世界；维度切换后 ClientLevel 会被整体替换。 */
	private static ClientLevel currentLevel;

	private DogClientManager() {
	}

	/** 根据替身狼反查它对应的狗玩家实体（客户端）。 */
	public static AbstractClientPlayer findDogPlayer(DummyWolf wolf) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || wolf.getDogUUID() == null) {
			return null;
		}
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p.getUUID().equals(wolf.getDogUUID())) {
				return p;
			}
		}
		return null;
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			clear();
			return;
		}
		ClientLevel level = mc.level;
		// 维度切换（下界/末地传送、死亡重生等）会整体替换 ClientLevel 实例。
		// 旧的替身狼还挂在旧 level 上，新维度里根本没有它的实体：本体依旧被设为隐形，
		// 但狼画不出来 —— 表现就是狗进入其他维度后彻底隐身。这里检测到 level 变化就整批重建。
		if (level != currentLevel) {
			clear();
			currentLevel = level;
		}
		// 让 main 源集的碰撞箱 / 眼高 mixin 在客户端也能认出谁是狗、谁是幼犬
		DogLookup.setClientDogs(ClientState.dogBabyFlags());

		// 狗没有物品栏，按 E 打开自己的物品栏时立即关闭
		if (ClientState.isDog(mc.player.getUUID())
			&& mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
			mc.gui.setScreen(null);
		}

		// 狗视角：默认第三人称（在狗身后上方看狼），F6 后第一人称低视角
		if (ClientState.isDog(mc.player.getUUID())) {
			CameraType desired = ClientState.dogLowCamera
				? CameraType.FIRST_PERSON
				: CameraType.THIRD_PERSON_BACK;
			if (mc.options.getCameraType() != desired) {
				mc.options.setCameraType(desired);
			}
		}

		Set<UUID> present = new HashSet<>();

		// 非狗玩家即人类（拴绳的另一端）；观战者不算
		AbstractClientPlayer human = null;
		for (AbstractClientPlayer player : level.players()) {
			if (!ClientState.isDog(player.getUUID()) && !player.isSpectator()) {
				human = player;
				break;
			}
		}

		for (AbstractClientPlayer player : level.players()) {
			UUID id = player.getUUID();
			ClientState.DogVisual visual = ClientState.getDog(id);
			if (visual == null) {
				continue;
			}
			present.add(id);
			DummyWolf wolf = WOLVES.get(id);
			if (wolf != null && wolf.level() != level) {
				// 兜底：替身狼若仍留在旧维度（level 变化没被上面的批量重建捕捉到），删除后重建
				discard(wolf);
				WOLVES.remove(id);
				wolf = null;
			}
			if (wolf == null) {
				wolf = new DummyWolf(EntityTypes.WOLF, level);
				wolf.setId(nextId--);
				wolf.setDogUUID(id);
				level.addEntity(wolf);
				WOLVES.put(id, wolf);
			}
			sync(wolf, player, visual, human);
			ensureDogDimensions(player, visual);
		}

		Iterator<Map.Entry<UUID, DummyWolf>> it = WOLVES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, DummyWolf> entry = it.next();
			if (!present.contains(entry.getKey())) {
				level.removeEntity(entry.getValue().getId(), RemovalReason.DISCARDED);
				it.remove();
			}
		}
	}

	private static void sync(DummyWolf wolf, Player player, ClientState.DogVisual visual, Player human) {
		// 位置 / 朝向（连同旧位置一起设，避免实体插值产生的滑动）
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		wolf.setPos(x, y, z);
		// 把"上一帧位置/朝向"全部对齐狗玩家：渲染插值（xOld/yOld/zOld、yRotO…）与
		// 走路动画（xo/yo/zo）都以玩家的真实位移为准，否则替身狼会比玩家慢一帧（抖动）。
		wolf.xo = player.xo;
		wolf.yo = player.yo;
		wolf.zo = player.zo;
		wolf.xOld = player.xOld;
		wolf.yOld = player.yOld;
		wolf.zOld = player.zOld;
		wolf.yRotO = player.yRotO;
		wolf.xRotO = player.xRotO;
		wolf.yHeadRotO = player.yHeadRotO;
		// 狗坐在船/矿车里时不该继续摆腿：把位移清零，让摆腿速度平滑衰减到 0。
		if (player.isPassenger()) {
			wolf.xo = x;
			wolf.yo = y;
			wolf.zo = z;
		}
		// 走路动画：替身狼没有 AI，坐标是我们每帧"搬运"过来的。原版只在 LivingEntity.aiStep()
		// 里推进 walkAnimation，而那一刻狼的位置还没被搬运（位移恒为 0），所以四肢永远不动。
		// DummyWolf 屏蔽了原版那次调用，这里改用玩家这一 tick 的真实位移驱动。
		wolf.updateWalkAnimationFromPlayer();
		wolf.setYRot(player.getYRot());
		wolf.setXRot(player.getXRot());
		wolf.setYHeadRot(player.getYHeadRot());
		wolf.yBodyRot = player.yBodyRot;
		wolf.yBodyRotO = player.yBodyRotO;
		wolf.setDeltaMovement(0.0, 0.0, 0.0);
		// 完全无碰撞，避免替身狼与狗玩家重叠时互相推挤造成漂移
		wolf.noPhysics = true;

		// 本地狗在 F6 第一人称低视角时隐藏自己的替身狼（否则相机会落进狼模型内部）。
		// 狗本体是否渲染由 DogInvisibleMixin 统一控制（覆盖 isInvisible），这里不再动 setInvisible，
		// 避免与服务端同步的隐形标志打架产生"蹲下闪人形"。
		boolean selfFirstPerson = player == Minecraft.getInstance().player
			&& ClientState.dogLowCamera
			&& Minecraft.getInstance().options.getCameraType().isFirstPerson();
		wolf.setInvisible(selfFirstPerson);

		// 名牌：用纯文本玩家名，强制常显（与命名牌命名的狼一致）；
		// 但第一人称低视角隐藏自己的替身狼时，不显示自己的名字。
		String plainName = player.getName().getString();
		Component wolfName = wolf.getCustomName() != null && wolf.getCustomName().getString().equals(plainName)
			? wolf.getCustomName()
			: Component.literal(plainName);
		wolf.setCustomName(wolfName);
		wolf.setCustomNameVisible(!selfFirstPerson);

		// 拴绳：从替身狼连到人类玩家（原版拴绳线由渲染管线自动绘制）
		if (human != null && wolf.getLeashHolder() != human) {
			wolf.setLeashedTo(human, false);
		}

		// 驯服状态
		if (!wolf.isTame()) {
			wolf.setTame(true, false);
		}

		// 变种
		Registry<WolfVariant> registry = player.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT);
		registry.get(Identifier.parse(visual.variantId())).ifPresent(
			holder -> wolf.setComponent(DataComponents.WOLF_VARIANT, holder)
		);

		// 项圈颜色
		wolf.setComponent(DataComponents.WOLF_COLLAR, DyeColor.byId(visual.collarColor()));

		// 坐姿
		wolf.setOrderedToSit(visual.sitting());
		wolf.setInSittingPose(visual.sitting());

		// 狼铠：第一人称低视角时替身狼是隐藏的，但 WolfArmorLayer 不看隐形标志、
		// 仍然会把护甲画出来，于是"狼铠糊在镜头前"。隐藏替身狼时必须把护甲槽清空。
		ItemStack armorStack = visual.hasArmor() && !selfFirstPerson
			? new ItemStack(Items.WOLF_ARMOR)
			: ItemStack.EMPTY;
		wolf.setItemSlot(EquipmentSlot.BODY, armorStack);

		// 幼犬 / 成年：切换模型与尺寸
		if (wolf.isBaby() != visual.baby()) {
			wolf.setBaby(visual.baby());
			// 本地玩家的碰撞箱由 DogDimensionsMixin 决定，尺寸缓存需要刷新才会生效
			player.refreshDimensions();
		}

		// 生命与玩家一致
		wolf.setHealth(Math.max(1.0F, Math.min(player.getHealth(), wolf.getMaxHealth())));
	}

	public static void clear() {
		for (DummyWolf wolf : WOLVES.values()) {
			discard(wolf);
		}
		WOLVES.clear();
		currentLevel = null;
	}

	/**
	 * 让狗玩家的碰撞箱跟上"成年 / 幼犬"。尺寸缓存只在 refreshDimensions() 时更新，
	 * 刚变成狗、或者刚长大时客户端不会自动刷新，这里按预期尺寸自检一次。
	 */
	private static void ensureDogDimensions(Player player, ClientState.DogVisual visual) {
		float expected = visual.baby() ? 0.425F : 0.85F;
		if (Math.abs(player.getBbHeight() - expected) > 0.01F) {
			float oldHeight = player.getBbHeight();
			player.refreshDimensions();
			com.humananddog.HumanAndDogMod.LOGGER.info("[HD] 客户端碰撞箱 {} -> {}（{}）",
				oldHeight, player.getBbHeight(), player.getName().getString());
		}
	}

	/** 从"狼自己所属的世界"里移除它（维度切换后 mc.level 已经不是它所在的世界了）。 */
	private static void discard(DummyWolf wolf) {
		if (wolf.level() instanceof ClientLevel wolfLevel) {
			wolfLevel.removeEntity(wolf.getId(), RemovalReason.DISCARDED);
		} else {
			wolf.setRemoved(RemovalReason.DISCARDED);
		}
	}
}
