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
	/** 骑乘时替身狼相对载具原点的抬高量（船内地面大约在这个高度）。 */
	private static final double RIDING_WOLF_Y_OFFSET = 0.1;
	/** 当前替身狼所属的客户端世界；维度切换后 ClientLevel 会被整体替换。 */
	private static ClientLevel currentLevel;
	/** 上一帧本地玩家是不是狗，用于"刚变成狗"时切一次视角。 */
	private static boolean wasLocalDog;

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

		// 狗视角：刚变成狗时切到第三人称背面（能看到替身狼），之后交给原版 F5
		// 在第一人称 / 第三人称背面 / 第三人称正面之间循环，这里不再每帧覆盖。
		boolean localIsDog = ClientState.isDog(mc.player.getUUID());
		if (localIsDog && !wasLocalDog) {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		wasLocalDog = localIsDog;

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
		// 骑乘时水平坐标取玩家自己：那正是船上的座位坐标（船长在前座、乘客在后座，各自不同），
		// 之前用船体中心会把当乘客的狗顶到船头去。纵向仍取载具坐标 + 船内地面高度，
		// 因为玩家的座位比船底高一截，直接用会让狼悬在船沿上。
		boolean riding = player.isPassenger() && player.getVehicle() != null;
		net.minecraft.world.entity.Entity vehicle = riding ? player.getVehicle() : null;
		double x = player.getX();
		double z = player.getZ();
		double y = riding ? vehicle.getY() + RIDING_WOLF_Y_OFFSET : player.getY();
		wolf.setPos(x, y, z);
		// 插值的"上一帧坐标"必须和当前位置取自同一个来源，否则渲染会在两套坐标之间插值（抖动）。
		wolf.xo = player.xo;
		wolf.yo = riding ? vehicle.yo + RIDING_WOLF_Y_OFFSET : player.yo;
		wolf.zo = player.zo;
		wolf.xOld = player.xOld;
		wolf.yOld = riding ? vehicle.yOld + RIDING_WOLF_Y_OFFSET : player.yOld;
		wolf.zOld = player.zOld;
		wolf.yRotO = player.yRotO;
		wolf.xRotO = player.xRotO;
		wolf.yHeadRotO = player.yHeadRotO;
		// 狗坐在船/矿车里时不该继续摆腿：把位移清零，让摆腿速度平滑衰减到 0。
		if (riding) {
			wolf.xo = x;
			wolf.yo = y;
			wolf.zo = z;
		}
		// 走路动画：替身狼没有 AI，坐标是我们每帧"搬运"过来的。原版只在 LivingEntity.aiStep()
		// 里推进 walkAnimation，而那一刻狼的位置还没被搬运（位移恒为 0），所以四肢永远不动。
		// DummyWolf 屏蔽了原版那次调用，这里改用玩家这一 tick 的真实位移驱动。
		wolf.updateWalkAnimationFromPlayer();
		// 上岸甩水：原版 Wolf 的触发逻辑在服务端分支里，替身狼是纯客户端实体，自己驱动一次
		wolf.tickShake(player.isInWaterOrRain(), player.onGround());
		wolf.setYRot(player.getYRot());
		wolf.setXRot(player.getXRot());
		wolf.setYHeadRot(player.getYHeadRot());
		wolf.yBodyRot = player.yBodyRot;
		wolf.yBodyRotO = player.yBodyRotO;
		wolf.setDeltaMovement(0.0, 0.0, 0.0);
		// 完全无碰撞，避免替身狼与狗玩家重叠时互相推挤造成漂移
		wolf.noPhysics = true;

		// 本地狗在第一人称时隐藏自己的替身狼（否则相机会落进狼模型内部）。
		// 狗本体是否渲染由 DogInvisibleMixin 统一控制（覆盖 isInvisible），这里不再动 setInvisible，
		// 避免与服务端同步的隐形标志打架产生"蹲下闪人形"。
		boolean selfFirstPerson = player == Minecraft.getInstance().player
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

		// 客户端不会同步"其他玩家"的属性，狗的 MAX_HEALTH 在别人那里仍是 20。
		// 按狗的上限补一下，Jade（玉）之类的 mod 才会显示成 40 而不是 20。
		net.minecraft.world.entity.ai.attributes.AttributeInstance maxHealth =
			player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (maxHealth != null
			&& Math.abs(maxHealth.getBaseValue() - com.humananddog.mechanic.DogMechanic.DOG_MAX_HEALTH) > 0.01) {
			maxHealth.setBaseValue(com.humananddog.mechanic.DogMechanic.DOG_MAX_HEALTH);
		}
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
