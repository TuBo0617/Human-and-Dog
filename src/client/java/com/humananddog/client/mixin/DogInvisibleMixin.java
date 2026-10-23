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
 * 狗玩家本体在客户端不渲染（改由替身狼呈现），只有第一人称时允许本体可见，
 * 好让空手手臂正常渲染。
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
		// 第一人称下本体可见（用于渲染空手手臂），第三人称一律隐藏、交给替身狼
		boolean selfFirstPerson = player == Minecraft.getInstance().player
			&& Minecraft.getInstance().options.getCameraType().isFirstPerson();
		cir.setReturnValue(!selfFirstPerson);
	}

	/**
	 * 只对"信息显示 mod"放行隐形判定。
	 *
	 * <p>渲染走的是 {@code isInvisible()}（{@code EntityRenderer} 取的就是它），所以这里返回
	 * false 不会让隐身的人形露出来；但 Jade（玉）的 {@code canBeTarget} 用的是
	 * {@code isInvisibleTo(player)}：隐形 + 没穿护甲的实体会被直接过滤掉，
	 * 于是狗玩家永远显示不出血量与效果。两个方法分开覆盖即可各取所需。
	 */
	@Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
	private void humananddog$dogVisibleToInfoMods(Player viewer, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && ClientState.isDog(player.getUUID())) {
			cir.setReturnValue(false);
		}
	}
}
