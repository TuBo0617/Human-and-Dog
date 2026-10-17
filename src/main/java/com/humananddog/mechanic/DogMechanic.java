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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
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

		// 狗打了生物 → 记一次"惹事"时间戳，之后一段时间内生物可以正常把狗当目标（记仇）
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof Mob)) {
				return;
			}
			if (source.getEntity() instanceof ServerPlayer attacker) {
				GameSession session = GameSessions.get();
				if (session != null && session.isDog(attacker.getUUID())) {
					recordAggression(attacker);
				}
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

		// 1. 食物回血（完全对齐原版驯服狼 Wolf.mobInteract：isFood 且 getHealth<getMaxHealth 才回血）
		if (stack.is(ItemTags.WOLF_FOOD)) {
			// 喂食前确保最大血量为 40（防止属性同步瞬间把上限重置为 20）
			ensureDogMaxHealth(dog);
			ServerLevel dogLevel = (ServerLevel) dog.level();
			if (dog.getHealth() < dog.getMaxHealth()) {
				// 原版 feed：回血量 = 2 × 食物营养值
				FoodProperties food = stack.get(DataComponents.FOOD);
				float heal = food != null ? 2.0F * food.nutrition() : 2.0F;
				dog.heal(heal);
				dog.removeEffect(MobEffects.HUNGER);
				stack.consume(1, human);
				dogLevel.playSound(null, dog.getX(), dog.getY() + 0.5, dog.getZ(),
					SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 1.0F + dog.getRandom().nextFloat() * 0.2F);
				dogLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
					dog.getX(), dog.getY() + 1.0, dog.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
				HumanAndDogMod.LOGGER.info("[HD] 喂食 {} 回 {} 血，现 {}/{}",
					dog.getName().getString(), heal, dog.getHealth(), dog.getMaxHealth());
			} else {
				// 满血：给友好反馈（爱心），但不消耗食物、不回血，避免被误判为"喂食无效"
				dogLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
					dog.getX(), dog.getY() + 1.0, dog.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
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

		// 3. 穿狼铠（可装备到身体槽的动物护甲）
		if (dog.isEquippableInSlot(stack, EquipmentSlot.BODY) && data.wolfArmor.isEmpty()) {
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

	/** 每 tick：锁定狗的饥饿，并持续确保 40 血上限（防止属性被同步重置）。 */
	public static void tickDog(ServerPlayer dog, PlayerData data) {
		ensureDogMaxHealth(dog);
		// 狗没有饱食度系统：饥饿值与饱和度始终保持在满值
		FoodData food = dog.getFoodData();
		if (food.getFoodLevel() != 20) {
			food.setFoodLevel(20);
		}
		if (food.getSaturationLevel() < 20.0F) {
			food.setSaturation(20.0F);
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
