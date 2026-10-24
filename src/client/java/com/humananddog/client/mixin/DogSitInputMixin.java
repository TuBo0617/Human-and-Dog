package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.humananddog.client.ClientState;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

/**
 * 被主人命令坐下后，狗不能再走动。
 *
 * <p>服务端本来就会把坐下狗的移动包钳回原地（见 {@code ServerGamePacketListenerImplMixin}），
 * 但那只改服务端的记录，客户端依旧按自己的预测一路走出去 ——
 * 表现就是"别人看狗乖乖坐着，狗自己却能到处跑"。
 * 这里直接在客户端把移动输入清零，从源头不再产生位移。
 */
@Mixin(ClientInput.class)
public abstract class DogSitInputMixin {
	@Inject(method = "getMoveVector", at = @At("HEAD"), cancellable = true)
	private void humananddog$noMoveWhileSitting(CallbackInfoReturnable<Vec2> cir) {
		if (ClientState.localPlayerIsDog() && ClientState.isLocalDogSitting()) {
			cir.setReturnValue(Vec2.ZERO);
		}
	}
}
