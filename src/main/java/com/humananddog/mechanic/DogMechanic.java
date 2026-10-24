package com.humananddog.mechanic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.humananddog.HumanAndDogMod;
import com.humananddog.role.PlayerData;
import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 狗的属性、限制与主人交互：
 * 驯服狗的 40 血与空手 4 点伤害、无饱食度、无背包/物品使用、
 * 喂食回血、染料改项圈、穿狼铠、空手切换坐下；
 * 但狗可以上船、可以睡觉（度过夜晚）。
 */
public final class DogMechanic {
	private DogMechanic() {
	}

	/** 驯服狗的最大生命。 */
	public static final double DOG_MAX_HEALTH = 40.0;
	/** 狗（原版驯服狼）空手攻击伤害。 */
	public static final double DOG_ATTACK_DAMAGE = 4.0;
	/** 狼铠提供的护甲值。 */
	public static final double WOLF_ARMOR_ARMOR = 11.0;
	private static final Identifier ARMOR_MODIFIER_ID = HumanAndDogMod.id("wolf_armor");

	public static void register() {
		// 狗不能使用物品（吃食物、喝药水、使用工具等）
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (isDog(player)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		// 狗不能对方块使用物品 / 放置方块，但允许睡觉（右键床）
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!isDog(player)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.BEDS)) {
				return InteractionResult.PASS;
			}
			return InteractionResult.FAIL;
		});

		// 狗不能破坏方块
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, blockEntity, state) -> !isDog(player));

		// 狗打了生物 → 记一次"惹事"时间戳，之后一段时间内生物可以正常把狗当目标（记仇）；
		// 狗自己被打 → 播放原版狼的受伤音效（人形受伤音效已在 PlayerSoundMixin 里关掉）
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			GameSession session = GameSessions.get();
			if (session == null) {
				return;
			}
			if (entity instanceof ServerPlayer dogPlayer && session.isDog(dogPlayer.getUUID())
				&& damageTaken > 0.0F && dogPlayer.level() instanceof ServerLevel dogLevel) {
				playHurtSound(dogLevel, dogPlayer, session.data(dogPlayer));
			}
			if (entity instanceof Mob && source.getEntity() instanceof ServerPlayer attacker
				&& session.isDog(attacker.getUUID())) {
				recordAggression(attacker);
			}
		});

		UseEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			GameSession session = GameSessions.get();
			if (session == null) {
				return InteractionResult.PASS;
			}
			// 狗的交互：允许上船（原版上船逻辑），其余实体交互一律禁止
			if (session.isDog(sp.getUUID())) {
				if (target instanceof AbstractBoat) {
					return InteractionResult.PASS;
				}
				return InteractionResult.FAIL;
			}
			// 主人对狗玩家右键
			if (target instanceof ServerPlayer dogTarget && session.isDog(dogTarget.getUUID())) {
				return interactWithDog(session, sp, dogTarget, hand);
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean isDog(net.minecraft.world.entity.player.Player player) {
		if (!(player instanceof ServerPlayer sp)) {
			return false;
		}
		GameSession session = GameSessions.get();
		return session != null && session.isDog(sp.getUUID());
	}

	/** 主人对狗玩家的交互（喂食/染色/狼铠/坐下）。 */
	public static InteractionResult interactWithDog(GameSession session, ServerPlayer human, ServerPlayer dog,
													InteractionHand hand) {
		// 只处理主手，避免副手（空手）再次触发空手坐下切换
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		PlayerData data = session.data(dog);
		if (data == null) {
			return InteractionResult.PASS;
		}
		ItemStack stack = human.getItemInHand(hand);

		// 唱片机 + 唱片：播放原版音乐（不消耗唱片，方便反复听）
		if (!stack.isEmpty() && session.hasBlock(dog, net.minecraft.world.level.block.Blocks.JUKEBOX)
			&& stack.get(DataComponents.JUKEBOX_PLAYABLE) != null) {
			if (session.playDisc(dog, human, stack)) {
				return InteractionResult.SUCCESS;
			}
		}

		// 方块挂件：
		//  - 手持可挂方块 + shift：挂到"人站的这一侧"（不再被"打开界面"抢走）
		//  - 手持可挂方块（不按 shift）：左 → 右 → 替换左
		//  - shift + 非方块/空手：打开已挂方块的界面（工作台 / 箱子 / 熔炉…）
		boolean attachable = GameSession.isAttachable(stack);
		if (attachable && human.isShiftKeyDown()) {
			if (session.attachBlock(dog, human, stack, session.isHumanOnDogLeft(human, dog))) {
				return InteractionResult.SUCCESS;
			}
		}
		if (!attachable && human.isShiftKeyDown() && session.openDogBlockMenu(human, dog)) {
			return InteractionResult.SUCCESS;
		}
		if (attachable && session.attachBlock(dog, human, stack)) {
			return InteractionResult.SUCCESS;
		}

		// 0. 剪刀取下狼铠（原版行为：对着穿狼铠的狗用剪刀即可取下）
		if (stack.is(Items.SHEARS) && !data.wolfArmor.isEmpty()) {
			ItemStack armor = data.wolfArmor.copy();
			data.wolfArmor = ItemStack.EMPTY;
			applyArmorModifier(dog, false);
			if (!human.getInventory().add(armor)) {
				human.drop(armor, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			stack.hurtAndBreak(1, human, EquipmentSlot.MAINHAND);
			ServerLevel dogLevel = (ServerLevel) dog.level();
			dogLevel.playSound(null, dog.blockPosition(), SoundEvents.WOLF_ARMOR_CRACK,
				SoundSource.PLAYERS, 1.0F, 1.0F);
			session.broadcastDogVisual(dog);
			HumanAndDogMod.LOGGER.info("从狗 {} 身上取下了狼铠", dog.getName().getString());
			return InteractionResult.SUCCESS;
		}

		// 0.5 金蒲公英：给小狗切换"年龄锁定"，锁住后永远是幼犬（与原版一致）
		if (stack.is(Items.GOLDEN_DANDELION) && data.baby) {
			data.ageLocked = !data.ageLocked;
			data.growthTicks = 0;
			stack.consume(1, human);
			ServerLevel dogLevel = (ServerLevel) dog.level();
			dogLevel.playSound(null, dog.blockPosition(),
				data.ageLocked ? SoundEvents.GOLDEN_DANDELION_USE : SoundEvents.GOLDEN_DANDELION_UNUSE,
				SoundSource.PLAYERS, 1.0F, 1.0F);
			dogLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
				dog.getX(), dog.getY() + 0.5, dog.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
			HumanAndDogMod.LOGGER.info("狗 {} 年龄锁定={}", dog.getName().getString(), data.ageLocked);
			return InteractionResult.SUCCESS;
		}

		// 1. 食物：狼食物回血；金苹果 / 附魔金苹果 / 谜之炖菜额外给予对应药水效果
		boolean specialTreat = stack.is(Items.GOLDEN_APPLE)
			|| stack.is(Items.ENCHANTED_GOLDEN_APPLE)
			|| stack.is(Items.SUSPICIOUS_STEW);
		if (stack.is(ItemTags.WOLF_FOOD) || specialTreat) {
			// 喂食前确保最大血量为 40（防止属性同步瞬间把上限重置为 20）
			ensureDogMaxHealth(dog);
			ServerLevel dogLevel = (ServerLevel) dog.level();
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (dog.getHealth() < dog.getMaxHealth()) {
				// 原版 feed：回血量 = 2 × 食物营养值
				float heal = food != null ? 2.0F * food.nutrition() : 2.0F;
				dog.heal(heal);
				dog.removeEffect(MobEffects.HUNGER);
				stack.consume(1, human);
				dogLevel.playSound(null, dog.getX(), dog.getY() + 0.5, dog.getZ(),
					SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 1.0F + dog.getRandom().nextFloat() * 0.2F);
				dogLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
					dog.getX(), dog.getY() + 1.0, dog.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
				applyConsumeEffects(dogLevel, dog, stack, specialTreat);
				HumanAndDogMod.LOGGER.info("[HD] 喂食 {} 回 {} 血，现 {}/{}",
					dog.getName().getString(), heal, dog.getHealth(), dog.getMaxHealth());
			} else {
				// 满血：进入寻爱（原版喂满血动物会冒爱心），并给出友好反馈
				applyConsumeEffects(dogLevel, dog, stack, specialTreat);
				dogLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
					dog.getX(), dog.getY() + 1.0, dog.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
				stack.consume(1, human);
				startLove(session, dog, data);
			}
			return InteractionResult.SUCCESS;
		}

		// 2. 染料改项圈颜色
		if (stack.is(ItemTags.WOLF_COLLAR_DYES) && stack.getItem() instanceof DyeItem) {
			DyeColor color = stack.get(DataComponents.DYE);
			if (color != null && !color.getName().equals(data.collarColor)) {
				data.collarColor = color.getName();
				stack.consume(1, human);
				session.broadcastDogVisual(dog);
			}
			return InteractionResult.SUCCESS;
		}

		// 3. 穿狼铠：不能用 isEquippableInSlot——它要求 Equippable.allowedEntities 匹配实体类型，
		//    而狼铠只允许 WOLF，狗玩家是 PLAYER 会被判为不可穿戴。这里只校验"可装备到 BODY 槽"。
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.slot() == EquipmentSlot.BODY && data.wolfArmor.isEmpty()) {
			data.wolfArmor = stack.copyWithCount(1);
			stack.consume(1, human);
			applyArmorModifier(dog, true);
			session.broadcastDogVisual(dog);
			return InteractionResult.SUCCESS;
		}

		// 4. 空手切换坐下
		if (stack.isEmpty()) {
			data.sitting = !data.sitting;
			if (data.sitting) {
				// 记录坐下位置，移动拦截据此把狗钉在原地
				data.sitX = dog.getX();
				data.sitY = dog.getY();
				data.sitZ = dog.getZ();
				data.sitPosSet = true;
			} else {
				data.sitPosSet = false;
			}
			session.broadcastDogVisual(dog);
			HumanAndDogMod.LOGGER.info("狗 {} 切换坐下={}", dog.getName().getString(), data.sitting);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	/** 确保狗的最大血量为 40（原版驯服狼血量）。 */
	public static void ensureDogMaxHealth(ServerPlayer dog) {
		AttributeInstance health = dog.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && health.getBaseValue() != DOG_MAX_HEALTH) {
			health.setBaseValue(DOG_MAX_HEALTH);
		}
	}

	// ---------------------------------------------------------------------
	// 原版狼音效（狗叫 / 受伤）
	// ---------------------------------------------------------------------

	/** 取原版"经典"狼音效集（成年 / 幼犬各一套，数据驱动）。 */
	private static WolfSoundVariant.WolfSoundSet wolfSounds(ServerLevel level, boolean baby) {
		return level.registryAccess()
			.lookupOrThrow(Registries.WOLF_SOUND_VARIANT)
			.get(WolfSoundVariants.CLASSIC)
			.map(holder -> baby ? holder.value().babySounds() : holder.value().adultSounds())
			.orElse(null);
	}

	/** 狗叫（按键触发）：播放原版狼的 ambient 音效。 */
	public static void playBark(ServerLevel level, ServerPlayer dog, PlayerData data) {
		WolfSoundVariant.WolfSoundSet set = wolfSounds(level, data != null && data.baby);
		if (set == null) {
			return;
		}
		level.playSound(null, dog.getX(), dog.getY(), dog.getZ(), set.ambientSound().value(),
			SoundSource.NEUTRAL, 1.0F, 1.0F + (dog.getRandom().nextFloat() - 0.5F) * 0.2F);
	}

	/** 狗被打：播放原版狼的受伤音效。 */
	public static void playHurtSound(ServerLevel level, ServerPlayer dog, PlayerData data) {
		WolfSoundVariant.WolfSoundSet set = wolfSounds(level, data != null && data.baby);
		if (set == null) {
			return;
		}
		level.playSound(null, dog.getX(), dog.getY(), dog.getZ(), set.hurtSound().value(),
			SoundSource.NEUTRAL, 1.0F, 1.0F + (dog.getRandom().nextFloat() - 0.5F) * 0.2F);
	}

	// ---------------------------------------------------------------------
	// 被仇恨：狗打了生物才会被记仇（见 MobMixin）
	// ---------------------------------------------------------------------

	/** 狗最近一次对生物造成伤害时的游戏刻。 */
	private static final Map<UUID, Long> LAST_AGGRO_TICK = new HashMap<>();
	/** 记仇窗口（tick）：这段时间内生物可以正常把狗当目标。 */
	private static final long AGGRO_WINDOW_TICKS = 100L;

	/** 记录狗刚刚攻击过生物。 */
	public static void recordAggression(ServerPlayer dog) {
		LAST_AGGRO_TICK.put(dog.getUUID(), dog.level().getGameTime());
	}

	/** 狗是否"刚打过架"：只有这种状态下生物才会把狗当目标。 */
	public static boolean isAggressive(ServerPlayer dog) {
		Long last = LAST_AGGRO_TICK.get(dog.getUUID());
		return last != null && dog.level().getGameTime() - last <= AGGRO_WINDOW_TICKS;
	}

	/** 狗被指定时初始化属性。 */
	public static void setupDog(ServerPlayer dog) {
		ensureDogMaxHealth(dog);
		dog.setHealth((float) DOG_MAX_HEALTH);
		AttributeInstance attack = dog.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.setBaseValue(DOG_ATTACK_DAMAGE);
		}
	}

	/** 每 tick：锁定狗的饥饿、推进成长与寻爱繁殖，并持续确保 40 血上限。 */
	public static void tickDog(GameSession session, ServerPlayer dog, PlayerData data) {
		ensureDogMaxHealth(dog);
		// 碰撞箱自愈：狗应该是原版狼尺寸（成年 0.6×0.85 / 幼犬 0.3×0.425）。
		// 尺寸缓存只在 refreshDimensions() 时更新，漏掉一次就会保持 1.8 高、
		// 钻不进一格高的空间，所以每 tick 比对一次。
		ensureDogDimensions(dog, data);
		// 狼铠的护甲加成是瞬时属性修饰符，被同步覆盖后需要补回
		if (!data.wolfArmor.isEmpty()) {
			applyArmorModifier(dog, true);
		}
		// 狗没有饱食度 / 饱和度系统：两者恒为 0。
		// 真正的机制停用在 FoodDataMixin（跳过 FoodData.tick），这里只负责把值钉死，
		// 防止和平难度等原版逻辑把它填回去。血量因此只能靠主人喂食恢复。
		FoodData food = dog.getFoodData();
		if (food.getFoodLevel() != 0) {
			food.setFoodLevel(0);
		}
		if (food.getSaturationLevel() != 0.0F) {
			food.setSaturation(0.0F);
		}
		tickGrowth(session, dog, data);
		tickLove(session, dog, data);
	}

	// ---------------------------------------------------------------------
	// 成长（小狗 → 成年狗，金蒲公英可锁住年龄）
	// ---------------------------------------------------------------------

	/** 原版幼崽成长为成年所需 tick（20 分钟）。 */
	public static final int BABY_GROW_TICKS = 24000;

	private static void tickGrowth(GameSession session, ServerPlayer dog, PlayerData data) {
		if (!data.baby || data.ageLocked) {
			return;
		}
		if (++data.growthTicks < BABY_GROW_TICKS) {
			return;
		}
		data.baby = false;
		data.growthTicks = 0;
		// 尺寸与眼高随之恢复成年体型
		dog.refreshDimensions();
		ServerLevel level = (ServerLevel) dog.level();
		level.playSound(null, dog.blockPosition(), SoundEvents.WOLF_AMBIENT_BABY.value(), SoundSource.NEUTRAL, 0.6F, 1.4F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
			dog.getX(), dog.getY() + 0.6, dog.getZ(), 12, 0.3, 0.4, 0.3, 0.0);
		session.broadcastDogVisual(dog);
		HumanAndDogMod.LOGGER.info("狗 {} 长大了", dog.getName().getString());
	}

	// ---------------------------------------------------------------------
	// 寻爱 / 繁殖
	// ---------------------------------------------------------------------

	/** 寻爱持续时间（原版动物 600 tick）。 */
	public static final int LOVE_TICKS = 600;
	/** 靠近伴侣后要等待多久才生小狗（tick）。 */
	private static final int BREED_DELAY_TICKS = 40;
	/** 繁殖冷却（tick），避免刷狗。 */
	private static final int BREED_COOLDOWN_TICKS = 6000;

	/** 满血被喂食：进入寻爱状态，头顶冒爱心。 */
	public static void startLove(GameSession session, ServerPlayer dog, PlayerData data) {
		if (data.loveTicks > 0 || data.breedCooldown > 0) {
			return;
		}
		data.loveTicks = LOVE_TICKS;
		data.breedTicks = 0;
		spawnHearts(dog, 3);
		HumanAndDogMod.LOGGER.info("狗 {} 进入寻爱状态", dog.getName().getString());
	}

	private static void spawnHearts(ServerPlayer dog, int count) {
		ServerLevel level = (ServerLevel) dog.level();
		level.sendParticles(ParticleTypes.HEART,
			dog.getX(), dog.getY() + dog.getBbHeight() + 0.3, dog.getZ(),
			count, 0.3, 0.3, 0.3, 0.0);
	}

	/** 每 tick 处理寻爱：冒爱心、寻找附近同样在寻爱的驯服狼、靠近后生小狗。 */
	private static void tickLove(GameSession session, ServerPlayer dog, PlayerData data) {
		if (data.breedCooldown > 0) {
			data.breedCooldown--;
		}
		if (data.loveTicks <= 0) {
			return;
		}
		data.loveTicks--;
		if (data.loveTicks % 10 == 0) {
			spawnHearts(dog, 1);
		}

		Wolf partner = findLovePartner(dog);
		if (partner == null) {
			data.breedTicks = 0;
			return;
		}
		// 像原版繁殖一样朝对方走
		partner.getNavigation().moveTo(dog, 1.0);
		partner.getLookControl().setLookAt(dog, 10.0F, 10.0F);

		if (partner.distanceTo(dog) > 2.5) {
			data.breedTicks = 0;
			return;
		}
		if (++data.breedTicks < BREED_DELAY_TICKS) {
			return;
		}
		// 由那只非玩家狗生出小狗，归属于玩家狗
		if (spawnPuppy(partner, dog)) {
			partner.setInLoveTime(0);
			partner.setAge(0);
			data.loveTicks = 0;
			data.breedTicks = 0;
			data.breedCooldown = BREED_COOLDOWN_TICKS;
			spawnHearts(dog, 5);
			spawnHeartsEntity(partner, 5);
			saveWithSession(session);
		}
	}

	private static void spawnHeartsEntity(net.minecraft.world.entity.Entity entity, int count) {
		if (entity.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HEART,
				entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(),
				count, 0.3, 0.3, 0.3, 0.0);
		}
	}

	/** 找一只附近"正在寻爱"的、已被驯服的狼。 */
	private static Wolf findLovePartner(ServerPlayer dog) {
		if (!(dog.level() instanceof ServerLevel level)) {
			return null;
		}
		Wolf best = null;
		double bestDist = Double.MAX_VALUE;
		for (Wolf wolf : level.getEntitiesOfClass(Wolf.class, dog.getBoundingBox().inflate(10.0))) {
			if (!wolf.isTame() || !wolf.isInLove() || !wolf.isAlive()) {
				continue;
			}
			double d = wolf.distanceToSqr(dog);
			if (d < bestDist) {
				bestDist = d;
				best = wolf;
			}
		}
		return best;
	}

	/** 让 partner 生下一只归属于 dog 的小狗。 */
	private static boolean spawnPuppy(Wolf partner, ServerPlayer owner) {
		if (!(partner.level() instanceof ServerLevel level)) {
			return false;
		}
		Wolf puppy = EntityTypes.WOLF.create(level, EntitySpawnReason.BREEDING);
		if (puppy == null) {
			return false;
		}
		puppy.setBaby(true);
		puppy.setTame(true, false);
		puppy.setOwner(owner);
		puppy.snapTo(partner.getX(), partner.getY(), partner.getZ(), partner.getYRot(), 0.0F);
		level.addFreshEntityWithPassengers(puppy);
		level.broadcastEntityEvent(partner, (byte) 18); // 原版繁殖爱心事件
		return true;
	}

	private static void saveWithSession(GameSession session) {
		if (session != null) {
			session.save();
		}
	}

	/** 金苹果 / 附魔金苹果 / 谜之炖菜：把原版食用效果套到狗身上。 */
	private static void applyConsumeEffects(ServerLevel level, ServerPlayer dog, ItemStack stack, boolean special) {
		if (!special) {
			return;
		}
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		if (consumable == null) {
			return;
		}
		// 用副本走原版 Consumable 流程：应用 on_consume_effects（金苹果）与
		// ConsumableListener（谜之炖菜的药水效果），且不会动主人手上的物品。
		ItemStack copy = stack.copyWithCount(1);
		consumable.onConsume(level, dog, copy);
	}

	/** 碰撞箱与"成年 / 幼犬"不符时立刻刷新（服务端尺寸缓存自愈）。 */
	private static void ensureDogDimensions(ServerPlayer dog, PlayerData data) {
		float expectedHeight = data.baby ? 0.425F : 0.85F;
		float expectedWidth = data.baby ? 0.3F : 0.6F;
		if (Math.abs(dog.getBbHeight() - expectedHeight) > 0.01F
			|| Math.abs(dog.getBbWidth() - expectedWidth) > 0.01F) {
			float oldWidth = dog.getBbWidth();
			float oldHeight = dog.getBbHeight();
			dog.refreshDimensions();
			HumanAndDogMod.LOGGER.info("[HD] 狗 {} 碰撞箱 {}x{} -> {}x{}",
				dog.getName().getString(), oldWidth, oldHeight, dog.getBbWidth(), dog.getBbHeight());
		}
		// 诊断用：每 10 秒打一次当前碰撞箱，方便确认服务端实际尺寸
		if (dog.tickCount % 200 == 0) {
			HumanAndDogMod.LOGGER.info("[HD] 狗 {} 当前碰撞箱 {}x{}，眼高 {}",
				dog.getName().getString(), dog.getBbWidth(), dog.getBbHeight(), dog.getEyeHeight());
		}
	}

	public static void applyArmorModifier(ServerPlayer dog, boolean add) {
		AttributeInstance armor = dog.getAttribute(Attributes.ARMOR);
		if (armor == null) {
			return;
		}
		armor.removeModifier(ARMOR_MODIFIER_ID);
		if (add) {
			armor.addOrUpdateTransientModifier(
				new AttributeModifier(ARMOR_MODIFIER_ID, WOLF_ARMOR_ARMOR, AttributeModifier.Operation.ADD_VALUE)
			);
		}
	}
}
