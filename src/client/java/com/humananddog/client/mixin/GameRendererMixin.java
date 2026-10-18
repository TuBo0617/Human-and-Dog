package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.humananddog.client.ClientState;

import net.minecraft.client.renderer.GameRenderer;

/**
 * 狗第一人称默认不渲染手；按 F6 进入低视角后显示手（保持原有行为）。
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void humananddog$hideFirstPersonHands(CallbackInfo ci) {
		// 本地玩家是狗，且没有开 F6 低视角时才隐藏手
		if (ClientState.localPlayerIsDog() && !ClientState.dogLowCamera) {
			ci.cancel();
		}
	}
}
