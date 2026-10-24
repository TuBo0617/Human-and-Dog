package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.humananddog.client.ClientState;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;

/**
 * 狗玩家本体在第三人称下被设为隐形（由替身狼表现），而第一人称空手手臂的渲染要求
 * render state 非隐形，否则按 F6 进入低视角第一人称时看不到自己的手。
 * PlayerRenderState 是第一人称手渲染专用的状态（实体身体渲染走另一条路），
 * 因此仅在"狗 + F6 低视角第一人称"时把这里的隐形标记去掉，不影响第三人称的替身狼表现。
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Inject(method = "extractPlayerState", at = @At("TAIL"))
	private void humananddog$showDogFirstPersonHand(
		Camera camera, DeltaTracker deltaTracker, float worldPartialTicks, PlayerRenderState state, CallbackInfo ci
	) {
		Minecraft mc = Minecraft.getInstance();
		if (ClientState.localPlayerIsDog()
			&& mc.options.getCameraType().isFirstPerson()
			&& state.avatarRenderState != null) {
			state.avatarRenderState.isInvisible = false;
		}
	}
}
