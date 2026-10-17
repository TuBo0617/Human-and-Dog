package com.humananddog.mechanic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.humananddog.session.GameSession;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * 拴绳的"软"部分：人和狗距离超过 {@link #SLOW_DISTANCE}（12 格）后按"移动方向"施加不同效果。
 *
 * <ul>
 *   <li>正在<b>远离</b>对方：越远越慢（缓慢 I～III）；</li>
 *   <li>正在<b>靠近</b>对方：略微加速（速度 I）；</li>
 *   <li>距离回到 12 格以内：立即恢复正常。</li>
 * </ul>
 *
 * 这里只改移动速度，不碰视野（FOV）—— 原版缓慢/速度效果本身也不会改变视场角。
 * 15 格硬上限由 {@code ServerGamePacketListenerImplMixin}（三维钳制）和客户端预测钳制负责。
 */
public final class LeashMechanic {
	private LeashMechanic() {
	}

	/** 开始出现拉绳效果的距离（格）。 */
	public static final double SLOW_DISTANCE = 12.0;
	/** 完全锁死距离（格，三维）。 */
	public static final double MAX_DISTANCE = GameSession.MAX_BOND_DISTANCE;

	/** 每个玩家上一 tick 的位置，用于算出这一 tick 的位移方向。 */
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();

	public static void applyBondSpeedLimit(GameSession session) {
		ServerPlayer human = session.human();
		if (human == null) {
			LAST_POS.clear();
			return;
		}
		for (ServerPlayer dog : session.dogs()) {
			if (dog.level() != human.level()) {
				continue;
			}
			applyPair(human, dog);
			enforceBond(human, dog);
		}
	}

	/**
	 * 服务端兜底：任何原因（击退、传送、作弊客户端）让距离越过 15 格时，
	 * 把越界的一方拉回 15 格边界。正常游玩时客户端自己就停住了，这里基本不会触发。
	 */
	private static void enforceBond(ServerPlayer human, ServerPlayer dog) {
		// 优先把狗拉回来（狗是被拴住的一方，人被"拽着走"更符合拴绳直觉）
		if (pullBack(dog, human)) {
			return;
		}
		pullBack(human, dog);
	}

	/** 若 self 离 partner 超过硬上限，把 self 拉回边界；返回是否发生了拉回。 */
	private static boolean pullBack(ServerPlayer self, ServerPlayer partner) {
		// 乘船的位移由载具决定，硬拉会把玩家从船上拽出来，这里交给载具逻辑
		if (self.isPassenger() || partner.isPassenger()) {
			return false;
		}
		Vec3 relative = self.position().subtract(partner.position());
		double distance = relative.length();
		if (distance <= MAX_DISTANCE + 0.05) {
			return false;
		}
		Vec3 target = partner.position().add(relative.scale(MAX_DISTANCE / distance));
		self.teleportTo(target.x, target.y, target.z);
		self.setDeltaMovement(Vec3.ZERO);
		self.fallDistance = 0.0;
		return true;
	}

	private static void applyPair(ServerPlayer human, ServerPlayer dog) {
		double distance = human.distanceTo(dog);
		// 本 tick 位移每个人只需取一次（下面两边都要用）
		Vec3 humanMoved = movement(human);
		Vec3 dogMoved = movement(dog);
		if (distance <= SLOW_DISTANCE) {
			clearEffects(human);
			clearEffects(dog);
			return;
		}
		applyDirectional(human, dog, distance, humanMoved);
		applyDirectional(dog, human, distance, dogMoved);
	}

	private static void applyDirectional(ServerPlayer self, ServerPlayer partner, double distance, Vec3 moved) {
		Vec3 toPartner = partner.position().subtract(self.position());
		double horizontal = Math.hypot(toPartner.x, toPartner.z);
		if (horizontal < 1.0E-4) {
			clearEffects(self);
			return;
		}
		// 只看水平分量：掉下悬崖 / 上楼梯不算"靠近"，也不该因此被判定为远离
		double dot = (moved.x * toPartner.x + moved.z * toPartner.z) / horizontal;
		if (dot > 1.0E-3) {
			applyEffect(self, MobEffects.SPEED, speedAmplifier());
		} else if (dot < -1.0E-3) {
			applyEffect(self, MobEffects.SLOWNESS, slowAmplifier(distance));
		} else {
			// 站着不动 / 横向绕圈：不吃任何效果
			clearEffects(self);
		}
	}

	/** 12→15 格线性从 I 到 III。 */
	private static int slowAmplifier(double distance) {
		double t = (distance - SLOW_DISTANCE) / (MAX_DISTANCE - SLOW_DISTANCE);
		return Math.max(0, Math.min(2, (int) Math.floor(t * 2.0)));
	}

	/** 朝对方走时固定给速度 I。 */
	private static int speedAmplifier() {
		return 0;
	}

	private static void applyEffect(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect, int amplifier) {
		// 两种效果互斥（原版里速度与缓慢会互相抵消），先清掉另一种
		if (effect == MobEffects.SPEED) {
			player.removeEffect(MobEffects.SLOWNESS);
		} else {
			player.removeEffect(MobEffects.SPEED);
		}
		player.addEffect(new MobEffectInstance(effect, 30, amplifier, false, false, false));
	}

	private static void clearEffects(ServerPlayer player) {
		player.removeEffect(MobEffects.SLOWNESS);
		player.removeEffect(MobEffects.SPEED);
	}

	/** 这一 tick 的位移；传送 / 跨维度造成的跳变按"没有移动"处理。 */
	private static Vec3 movement(ServerPlayer player) {
		Vec3 now = player.position();
		Vec3 last = LAST_POS.put(player.getUUID(), now);
		if (last == null) {
			return Vec3.ZERO;
		}
		Vec3 delta = now.subtract(last);
		return delta.lengthSqr() > 4.0 ? Vec3.ZERO : delta;
	}
}
