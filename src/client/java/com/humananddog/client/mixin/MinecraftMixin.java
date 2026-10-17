package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

/**
 * 禁用 F3+F4 的游戏模式切换界面。
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "canSwitchGameMode", at = @At("HEAD"), cancellable = true)
	private void humananddog$disableGameModeSwitcher(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
