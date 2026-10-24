package com.humananddog.mixin;

import com.humananddog.mechanic.DogMechanic;
import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 生物默认不会主动把狗玩家当目标（狗可以安安静静待在主人身边）。
 * 两种例外：
 * <ul>
 *   <li>狗自己动手打了生物：一段时间内生物可以正常记仇、反击
 *       （见 {@link DogMechanic#isAggressive}）；</li>
 *   <li><b>末影人</b>：原版只有"被玩家直视眼睛"和"被打"才会仇恨玩家，
 *       两者本身都是玩家主动挑衅，所以对末影人放行。否则狗盯着末影人看
 *       永远不会激怒它（原版这个判定走的是
 *       {@code EndermanLookForPlayerGoal} → {@code setTarget}）。</li>
 * </ul>
 */
@Mixin(Mob.class)
public class MobMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void humanAndDog$neverTargetDog(LivingEntity target, CallbackInfo ci) {
		if (target instanceof ServerPlayer player) {
			GameSession session = GameSessions.get();
			if (session == null || !session.isDog(player.getUUID())) {
				return;
			}
			// 末影人的仇恨来源只有"被注视"与"被打"，均由玩家主动触发，直接放行
			if ((Object) this instanceof Enderman) {
				return;
			}
			if (!DogMechanic.isAggressive(player)) {
				ci.cancel();
			}
		}
	}
}
