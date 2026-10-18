package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.humananddog.client.ClientState;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 狗玩家本体在客户端不渲染（改由替身狼呈现），只有"本地狗 + F6 低视角第一人称"时
 * 允许本体可见，好让空手手臂正常渲染。
 *
 * <p>旧实现靠每 tick 调 {@code player.setInvisible(...)}，但服务端
 * {@code LivingEntity.updateInvisibilityStatus()} 会在药水效果变化时
 * （例如拉绳施加/移除缓慢）把隐形标志重新同步，客户端再补一次，
 * 于是蹲下/起身这类同步帧会闪一下人形。这里改成覆盖 {@code isInvisible()}：
 * 判断条件完全来自客户端本地状态，不会被服务端同步改写，因此不会再闪。
 */
@Mixin(Entity.class)
public abstract class DogInvisibleMixin {
	@Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
	private void humananddog$dogBodyHidden(CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof Player player) || !ClientState.isDog(player.getUUID())) {
			return;
		}
		// 与历史行为保持一致：F6 低视角第一人称下本体可见（用于渲染空手手臂）
		boolean selfFirstPerson = player == Minecraft.getInstance().player
			&& ClientState.dogLowCamera
			&& Minecraft.getInstance().options.getCameraType().isFirstPerson();
		cir.setReturnValue(!selfFirstPerson);
	}
}
