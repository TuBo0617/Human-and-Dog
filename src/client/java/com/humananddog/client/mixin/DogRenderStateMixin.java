package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.humananddog.client.ClientState;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 把狗玩家的本体藏起来——只在"渲染状态"这一层藏，实体本身保持可见。
 *
 * <p>以前是给实体设隐形（{@code isInvisible()}）。但 Jade（玉）的 {@code canBeTarget} 会跳过
 * "隐形且没穿护甲"的实体，于是狗永远显示不出血量与效果；而单纯把 {@code isInvisibleTo} 放行又会掉进
 * {@code LivingEntityRenderer.submit} 的另一个分支：
 * <pre>
 *   bodyVisible = false 且 isInvisibleToPlayer = false  →  以半透明幽灵渲染（狗身上叠个人影）
 * </pre>
 * 所以这里绕开实体标志：实体不隐形（Jade 能锁定它、能拿到服务端的效果数据），
 * 只在提取渲染状态时把两个可见性标志按"完全不可见"设好（{@code bodyVisible=false} 且
 * {@code isInvisibleToPlayer=true} → {@code getRenderType} 返回 null，模型压根不提交）。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class DogRenderStateMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL")
	)
	private void humananddog$hideDogBodyInRenderState(LivingEntity entity, LivingEntityRenderState state,
													 float partialTick, CallbackInfo ci) {
		if (entity instanceof Player player && ClientState.isDog(player.getUUID())) {
			state.isInvisible = true;
			state.isInvisibleToPlayer = true;
		}
	}
}
