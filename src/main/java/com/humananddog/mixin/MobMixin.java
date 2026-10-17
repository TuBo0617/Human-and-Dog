package com.humananddog.mixin;

import com.humananddog.mechanic.DogMechanic;
import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 生物默认不会主动把狗玩家当目标（狗可以安安静静待在主人身边）。
 * 但如果狗自己动手打了生物，那么在一小段时间内生物可以正常记仇、反击
 * （见 {@link DogMechanic#isAggressive}），也就是"敌对/中立生物被狗打了会还手"。
 */
@Mixin(Mob.class)
public class MobMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void humanAndDog$neverTargetDog(LivingEntity target, CallbackInfo ci) {
		if (target instanceof ServerPlayer player) {
			GameSession session = GameSessions.get();
			if (session != null && session.isDog(player.getUUID()) && !DogMechanic.isAggressive(player)) {
				ci.cancel();
			}
		}
	}
}
