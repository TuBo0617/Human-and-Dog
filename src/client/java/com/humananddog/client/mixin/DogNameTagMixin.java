package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.humananddog.client.ClientState;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 隐藏狗玩家本体头顶的名字。
 *
 * <p>名字由替身狼显示（纯文本玩家名、常显），本体是隐形的、本来不该有名字。
 * 但 {@code DogInvisibleMixin} 为了让 Jade 之类的信息 mod 能锁定狗玩家，把
 * {@code isInvisibleTo} 放行了，而原版 {@code shouldShowName} 正是靠它判断要不要画名字——
 * 结果本体头顶会多出一个和狼重名的标签。这里只对狗玩家本体关掉名字渲染。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class DogNameTagMixin {
	@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
	private void humananddog$hideDogBodyNameTag(LivingEntity entity, double distance,
											   CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player && ClientState.isDog(player.getUUID())) {
			cir.setReturnValue(false);
		}
	}
}
