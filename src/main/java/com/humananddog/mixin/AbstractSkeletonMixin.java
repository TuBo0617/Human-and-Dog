package com.humananddog.mixin;

import java.util.function.Predicate;

import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 骷髅（及凋灵骷髅等）像躲避狼一样躲避狗玩家。
 */
@Mixin(AbstractSkeleton.class)
public class AbstractSkeletonMixin {
	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void humanAndDog$avoidDogs(CallbackInfo ci) {
		PathfinderMob self = (PathfinderMob) (Object) this;
		Predicate<LivingEntity> isDog = target -> {
			if (target instanceof ServerPlayer player) {
				GameSession session = GameSessions.get();
				return session != null && session.isDog(player.getUUID());
			}
			return false;
		};
		((Mob) (Object) this).getGoalSelector().addGoal(
			3, new AvoidEntityGoal<>(self, Player.class, 6.0F, 1.0, 1.2, isDog)
		);
	}
}
