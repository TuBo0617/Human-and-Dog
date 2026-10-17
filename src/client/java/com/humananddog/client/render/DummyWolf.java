package com.humananddog.client.render;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;

/**
 * 纯客户端的"替身狼"，仅用于把狗玩家渲染成狼。
 * 可被射线选中（右键喂食/染色/坐下经 MultiPlayerGameModeMixin 转发到狗玩家），
 * 但不参与碰撞、推动、攻击、AI、保存。
 */
public class DummyWolf extends Wolf {
	/** 该替身狼对应的狗玩家 UUID。 */
	private UUID dogUUID;

	public DummyWolf(EntityType<? extends Wolf> type, Level level) {
		super(type, level);
		this.setNoAi(true);
		this.setNoGravity(true);
		this.setSilent(true);
		this.setPersistenceRequired();
		// 构造时就允许显示名牌，等价于命名牌命名后的狼，避免首帧被当作无名生物
		this.setCustomNameVisible(true);
	}

	public void setDogUUID(UUID uuid) {
		this.dogUUID = uuid;
	}

	public UUID getDogUUID() {
		return this.dogUUID;
	}

	@Override
	public boolean isPickable() {
		// 本地玩家就是这只狗时，替身狼不挡自己的视线/交互（可正常点船、床、方块）；
		// 其他人（人类）视角下替身狼可被命中，命中后由 mixin 转发给狗玩家。
		Minecraft mc = Minecraft.getInstance();
		if (this.dogUUID != null && mc.player != null && this.dogUUID.equals(mc.player.getUUID())) {
			return false;
		}
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	/**
	 * 替身狼的走路动画只能由 {@link DogClientManager} 在"搬运完位置"之后驱动。
	 * 原版会在 LivingEntity.aiStep() 末尾按替身狼自身的位移调用这里，而那一刻它的坐标
	 * 还没被搬运（位移恒为 0），会把摆腿速度与相位重新拉回 0 —— 表现就是四肢完全不动。
	 * 因此这里屏蔽原版自动调用，改用 {@link #updateWalkAnimationFromPlayer()}。
	 */
	@Override
	public void calculateEntityAnimation(boolean useY) {
		// 故意留空，见方法注释
	}

	/** 由客户端管理器在同步完位置后调用，按狗玩家这一 tick 的位移推进摆腿动画。 */
	public void updateWalkAnimationFromPlayer() {
		super.calculateEntityAnimation(false);
	}
}
