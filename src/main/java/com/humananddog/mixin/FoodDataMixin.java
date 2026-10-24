package com.humananddog.mixin;

import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 狗没有饱食度 / 饱和度系统：直接跳过 {@link FoodData#tick}。
 *
 * <p>原版这一个方法里同时包含了三件事：
 * <ul>
 *   <li>饥饿自然回血（饱和回血 + 饱食度 ≥18 的慢回血）——狗不应该有；</li>
 *   <li>饱食度为 0 时的饿死伤害——狗不应该掉血；</li>
 *   <li>消耗值（exhaustion）折算成饱食度 / 饱和度——狗没有这个循环。</li>
 * </ul>
 * 跳过之后，狗的血量只能通过主人喂食（{@code DogMechanic#interactWithDog}）恢复。
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void humanAndDog$skipHungerForDog(ServerPlayer player, CallbackInfo ci) {
		GameSession session = GameSessions.get();
		if (session != null && session.isDog(player.getUUID())) {
			ci.cancel();
		}
	}
}
