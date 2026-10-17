package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.humananddog.client.ClientState;

import net.minecraft.client.gui.Hud;

/**
 * 狗没有物品栏和饱食度：隐藏快捷栏与饥饿条（保留血条）。
 */
@Mixin(Hud.class)
public class HudMixin {
	@Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
	private void humananddog$hideHotbar(CallbackInfo ci) {
		if (ClientState.localPlayerIsDog()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
	private void humananddog$hideFood(CallbackInfo ci) {
		if (ClientState.localPlayerIsDog()) {
			ci.cancel();
		}
	}
}
