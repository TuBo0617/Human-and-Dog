package com.humananddog.mixin;

import com.humananddog.role.DogLookup;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 狗玩家的碰撞箱按原版狼来：成年 0.6×0.85，小狗 0.3×0.425。
 *
 * <p>{@code LivingEntity.getDimensions(Pose)} 是 final 且会转到
 * {@code getDefaultDimensions(Pose)}，所以改后面这个。眼高见 {@link DogEyeHeightMixin}。
 */
@Mixin(LivingEntity.class)
public abstract class DogDimensionsMixin {
	private static final EntityDimensions DOG_ADULT = EntityDimensions.scalable(0.6F, 0.85F).withEyeHeight(0.68F);
	private static final EntityDimensions DOG_BABY = EntityDimensions.scalable(0.3F, 0.425F).withEyeHeight(0.34375F);

	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
	private void humanAndDog$dogDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		Entity self = (Entity) (Object) this;
		if (DogLookup.isDog(self)) {
			cir.setReturnValue(DogLookup.isBaby(self) ? DOG_BABY : DOG_ADULT);
		}
	}
}
