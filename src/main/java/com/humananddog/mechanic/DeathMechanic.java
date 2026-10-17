package com.humananddog.mechanic;

import com.humananddog.session.GamePhase;
import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * 死亡联动：人或狗任意一方死亡，取消真实死亡并进入全体重生倒计时；
 * 倒计时期间所有参与者无敌，世界暂停，倒计时结束后在共享重生点复活。
 */
public final class DeathMechanic {
	private DeathMechanic() {
	}

	public static void register() {
		// 拦截参与者的真实死亡，改为触发联动
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}
			GameSession session = GameSessions.get();
			if (session == null || !session.isParticipant(player.getUUID())) {
				return true;
			}
			session.triggerDeathLink();
			return false;
		});

		// 倒计时期间参与者免疫所有伤害（含虚空），避免二次死亡
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}
			GameSession session = GameSessions.get();
			if (session == null || !session.isParticipant(player.getUUID())) {
				return true;
			}
			if (session.getPhase() == GamePhase.COUNTDOWN) {
				return false;
			}
			return true;
		});
	}
}
