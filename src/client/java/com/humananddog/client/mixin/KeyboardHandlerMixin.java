package com.humananddog.client.mixin;

import com.humananddog.client.ClientState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F6 切换狗的低视角（贴地狼眼高度的第一人称）。
 * 仅在游戏中（无界面）且本地玩家是狗时生效。
 * 同时禁用 F3+N 的旁观者模式切换（游戏模式只能通过 /mode 修改）。
 *
 * 注意：MC 26.3 已改用 SDL3，KeyEvent.key() 是 SDL 扫描码（InputConstants.KEY_F6），
 * 不再是旧版 GLFW 键码（GLFW_KEY_F6=295），必须用 event.key() 比较。
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	private static final int PRESS = 1;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void humananddog$onF6(long handle, int action, KeyEvent event, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		// F3+N（旁观者模式快速切换）：直接吞掉，不进入原版处理
		if (event.key() == InputConstants.KEY_N && mc.options.keyDebugModifier.isDown()) {
			ci.cancel();
			return;
		}
		if (action == PRESS && event.key() == InputConstants.KEY_F6) {
			if (mc.gui.screen() == null && ClientState.localPlayerIsDog()) {
				ClientState.dogLowCamera = !ClientState.dogLowCamera;
				if (ClientState.dogLowCamera) {
					// 进入狗的第一人称：强制切到第一人称，相机由 CameraMixin 压低到狼眼高度
					mc.options.setCameraType(CameraType.FIRST_PERSON);
				} else {
					// 退回"在狗上方"的第三人称后视
					mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				}
			}
		}
	}
}
