package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.humananddog.client.ClientState;

import net.minecraft.world.entity.player.Player;

/**
 * 人类视角下，狗的"本体玩家"被替身狼取代。但隐形的玩家本体仍然能被准星射线选中
 * （拾取判定只看 isPickable，不看隐形），而且玩家比狼高，准星往往命中玩家本体而非替身狼，
 * 导致右键交互走到了错误的路径（喂食 / 坐下偶发失败）。
 *
 * 这里让狗玩家本体在客户端完全不可被拾取：人类的准星只会命中替身狼，
 * 再由 MultiPlayerGameModeMixin 统一把交互重定向到狗玩家。
 * 本地狗自己就是相机实体，本就不会被拾取，不受影响。
 */
@Mixin(Player.class)
public abstract class ClientPlayerPickableMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void humananddog$dogBodyNotPickable(CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (ClientState.isDog(self.getUUID())) {
			cir.setReturnValue(false);
		}
	}
}
