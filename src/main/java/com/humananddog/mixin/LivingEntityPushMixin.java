package com.humananddog.mixin;

import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 人狗同坐标出生，必须互不发生实体推挤，否则会出现静止时被对方缓慢推开（漂移）。
 * 这里仅在双方都是本局参与者时取消互相推挤，对其他生物、怪物的推挤行为无影响。
 */
@Mixin(LivingEntity.class)
public class LivingEntityPushMixin {
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void humanAndDog$noPushBetweenParticipants(Entity other, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer self && other instanceof ServerPlayer otherPlayer) {
			GameSession session = GameSessions.get();
			if (session != null
				&& session.isParticipant(self.getUUID())
				&& session.isParticipant(otherPlayer.getUUID())) {
				ci.cancel();
			}
		}
	}
}
