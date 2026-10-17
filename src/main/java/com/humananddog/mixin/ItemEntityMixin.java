package com.humananddog.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 狗没有物品栏，不能拾取掉落物。
 */
@Mixin(ItemEntity.class)
public class ItemEntityMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void humananddog$cancelDogPickup(Player player, CallbackInfo ci) {
		if (player.level().isClientSide()) {
			return;
		}
		GameSession session = GameSessions.get();
		if (session != null && session.isDog(player.getUUID())) {
			ci.cancel();
		}
	}
}
