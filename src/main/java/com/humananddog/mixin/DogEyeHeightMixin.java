package com.humananddog.mixin;

import com.humananddog.role.DogLookup;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 狗玩家的眼睛高度按原版狼：成年 0.68，小狗 0.34375。
 *
 * <p>相机（{@code Camera}）、准星拾取（{@code LocalPlayer.raycastHitResult}）和方块射线
 * 都以 {@code getEyePosition()} 为原点，而它取的是无参 {@code getEyeHeight()}。
 * 服务端与客户端都生效（{@link DogLookup} 两端可判断），因此两端瞄准一致。
 */
@Mixin(Entity.class)
public abstract class DogEyeHeightMixin {
	private static final float DOG_ADULT_EYE = 0.68F;
	private static final float DOG_BABY_EYE = 0.34375F;

	@Inject(method = "getEyeHeight()F", at = @At("HEAD"), cancellable = true)
	private void humanAndDog$dogEyeHeight(CallbackInfoReturnable<Float> cir) {
		Entity self = (Entity) (Object) this;
		if (DogLookup.isDog(self)) {
			cir.setReturnValue(DogLookup.isBaby(self) ? DOG_BABY_EYE : DOG_ADULT_EYE);
		}
	}
}
