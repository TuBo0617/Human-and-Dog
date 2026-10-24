package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.humananddog.client.ClientState;

import net.minecraft.world.entity.player.Player;

/**
 * 狗不需要饱食度也能无限疾跑。
 *
 * <p>原版 {@code LocalPlayer.isSprintingPossible()} 会调用
 * {@link Player#hasEnoughFoodToDoExhaustiveManoeuvres()}，而它等价于
 * {@code foodData.hasEnoughFood()}（饱食度 &gt; 6）或飞行能力。
 * 狗的饱食度恒为 0，所以这里对狗直接放行，疾跑不再受饥饿限制。
 */
@Mixin(Player.class)
public abstract class DogSprintMixin {
	@Inject(method = "hasEnoughFoodToDoExhaustiveManoeuvres", at = @At("HEAD"), cancellable = true)
	private void humanAndDog$dogSprintsWithoutFood(CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (ClientState.isDog(self.getUUID())) {
			cir.setReturnValue(true);
		}
	}
}
