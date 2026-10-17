package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.humananddog.client.ClientState;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 狗玩家在客户端按"狼的眼高"看世界与瞄准。
 *
 * <p>玩家实体的眼高是 1.62，而替身狼只有 0.85 高（{@code EntityTypes.WOLF} 的 eyeHeight = 0.68）。
 * 相机（{@code Camera} 用 {@code getEyePosition}）、准星拾取（{@code LocalPlayer.raycastHitResult}
 * 用 {@code cameraEntity.getEyePosition}）和方块射线都以"眼睛"为原点，因此眼高一不一致就会出现
 * 狗瞄准了却打空、或者打点明显偏移（射线从头顶 1.6 格高射出去，越过矮生物、或者先打到地面）。
 *
 * <p>这里只覆盖无参的 {@code getEyeHeight()}（相机 / 瞄准 / 射线都走它），
 * 不动缓存的 {@code eyeHeight} 字段，所以 {@code getEyeY()} 仍然返回原值：
 * 液体判定、溺水计时、水下遮罩保持原样，不会让狗在浅水里溺水。
 */
@Mixin(Entity.class)
public abstract class DogEyeHeightMixin {
	/** 狼（成年）眼睛高度，取自 EntityTypes.WOLF 的 eyeHeight。 */
	private static final float WOLF_EYE_HEIGHT = 0.68F;
	/** 蹲下时按人形比例压低，避免蹲下后瞄准高度不变。 */
	private static final float WOLF_EYE_HEIGHT_CROUCHING = 0.5F;

	@Inject(method = "getEyeHeight()F", at = @At("HEAD"), cancellable = true)
	private void humananddog$useWolfEyeHeight(CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof Player player && ClientState.isDog(player.getUUID())) {
			cir.setReturnValue(player.isCrouching() ? WOLF_EYE_HEIGHT_CROUCHING : WOLF_EYE_HEIGHT);
		}
	}
}
