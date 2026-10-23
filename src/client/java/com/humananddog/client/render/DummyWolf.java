package com.humananddog.client.render;

import java.util.UUID;

import net.minecraft.sounds.SoundEvents;
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
	/** 甩水动画总时长（tick）：原版每 tick 累加 0.05，到 2.0 结束。 */
	private static final int SHAKE_TICKS = 40;
	/** 出水后仍认为"身上是湿的"的宽限（tick），用于覆盖掉下岸边再落地的情况。 */
	private static final int WET_GRACE_TICKS = 60;

	/** 该替身狼对应的狗玩家 UUID。 */
	private UUID dogUUID;
	/** 甩水动画计时，-1 表示当前没在甩。 */
	private int shakeTicks = -1;
	/** 身上是否还湿着（进水过、还没甩干净）。 */
	private boolean wet = false;
	/** 出水后的宽限计时。 */
	private int dryTicks = 0;

	public DummyWolf(EntityType<? extends Wolf> type, Level level) {
		super(type, level);
		this.setNoAi(true);
		this.setNoGravity(true);
		this.setSilent(true);
		this.setPersistenceRequired();
		// 构造时就允许显示名牌，等价于命名牌命名后的狼，避免首帧被当作无名生物
		this.setCustomNameVisible(true);
	}

	/**
	 * 由 {@link DogClientManager} 每 tick 调用，驱动"上岸甩水"。
	 *
	 * <p>原版 {@code Wolf.aiStep()} 里那段触发甩水的逻辑只在服务端分支执行，
	 * 而替身狼是纯客户端实体，所以这里按同样的条件自己判定：
	 * 身上湿着（最近进过水 / 淋过雨）+ 已经离开水 + 站在地面 → 开始甩。
	 */
	public void tickShake(boolean inWater, boolean onGround) {
		if (inWater) {
			this.wet = true;
			this.dryTicks = 0;
		} else if (this.wet) {
			this.dryTicks++;
			if (this.dryTicks > WET_GRACE_TICKS) {
				this.wet = false;
			}
		}
		if (this.shakeTicks < 0 && this.wet && !inWater && onGround) {
			this.shakeTicks = 0;
			this.playSound(SoundEvents.WOLF_SHAKE, 1.0F, 1.0F);
		}
		if (this.shakeTicks >= 0) {
			this.shakeTicks++;
			if (this.shakeTicks > SHAKE_TICKS) {
				this.shakeTicks = -1;
				this.wet = false;
				this.dryTicks = 0;
			}
		}
	}

	/** 供渲染层取用：0 → 2.0 的甩水进度，与原版 Wolf 的取值范围一致。 */
	@Override
	public float getShakeAnim(float partialTick) {
		if (this.shakeTicks < 0) {
			return 0.0F;
		}
		return Math.min(2.0F, (this.shakeTicks + partialTick) * 0.05F);
	}

	public void setDogUUID(UUID uuid) {
		this.dogUUID = uuid;
	}

	public UUID getDogUUID() {
		return this.dogUUID;
	}

	@Override
	public boolean isPickable() {
		// 替身狼只是"皮"，永远不作为准星目标。
		// 狗玩家本体和它占据同一个碰撞箱，准星命中的是玩家实体本身：
		// 这样 Jade（玉）之类读取实体血量和药水效果的 mod 才能拿到真实数据
		// （替身狼是纯客户端实体，血量/效果都是假的）。
		// 右键喂食 / 左键攻击依旧走原版流程，服务端 UseEntityCallback 会把它们路由到狗的处理逻辑。
		return false;
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
