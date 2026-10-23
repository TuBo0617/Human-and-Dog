package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.humananddog.client.ClientState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/**
 * 狗第一人称显示手；第三人称（含第三人称正面）隐藏手。
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void humananddog$hideFirstPersonHands(CallbackInfo ci) {
		if (ClientState.localPlayerIsDog()
			&& !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			ci.cancel();
		}
	}
}
