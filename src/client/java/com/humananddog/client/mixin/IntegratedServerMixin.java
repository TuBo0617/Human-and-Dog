package com.humananddog.client.mixin;

import java.util.function.BooleanSupplier;

import com.humananddog.dev.DevFlags;
import com.humananddog.session.GamePhase;
import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 等待房主选角色 / 狗选类型期间，即使对局域网开放（联机）也让世界真正暂停：
 * 复用原版 IntegratedServer 的暂停分支（只处理网络连接，不 tick 世界、时间与怪物），
 * 但玩家点击"当人 / 当狗 / 狗类型"的网络包仍会被正常处理。
 * 倒计时（COUNTDOWN）阶段不走这里，需要服务器 tick 推进倒计时。
 *
 * <p>注意：进入暂停分支后 MinecraftServer.tickServer 不再执行，ServerTickEvents.END_SERVER_TICK
 * 也就不会触发。所以阶段自愈检查（tickAlways）必须在这里的 tickServer 头部直接驱动，
 * 否则一旦停在等待阶段，就再没有任何 tick 能推进它（狗重进后一直显示"等待其他玩家加入"）。
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
	/** 每 tick（含世界暂停时）驱动一次会话自愈检查。 */
	@Inject(method = "tickServer", at = @At("HEAD"))
	private void humananddog$tickSessionEvenWhenPaused(BooleanSupplier haveTime, CallbackInfo ci) {
		GameSession session = GameSessions.get();
		if (session != null) {
			session.tickAlways();
		}
	}

	@Redirect(
		method = "tickServer",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isPaused()Z")
	)
	private boolean humananddog$pauseWhileWaiting(Minecraft mc) {
		boolean vanilla = mc.isPaused();
		// 开发自测单人自动开局依赖 tick 推进，不强制暂停
		if (DevFlags.SOLO) {
			return vanilla;
		}
		GameSession session = GameSessions.get();
		if (session != null) {
			GamePhase phase = session.getPhase();
			if (phase == GamePhase.WAITING_HUMAN
				|| phase == GamePhase.WAITING_DOGS
				|| phase == GamePhase.WAITING_PARTNER) {
				return true;
			}
		}
		return vanilla;
	}
}
