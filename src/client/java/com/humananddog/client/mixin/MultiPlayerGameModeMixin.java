package com.humananddog.client.mixin;

import com.humananddog.client.render.DogClientManager;
import com.humananddog.client.render.DummyWolf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 人看到的是"替身狼"，右键/左键命中替身狼时，把交互转发给它对应的狗玩家。
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void humananddog$redirectWolfInteract(Player player, Entity entity, EntityHitResult hitResult,
												 InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (entity instanceof DummyWolf wolf) {
			AbstractClientPlayer dog = DogClientManager.findDogPlayer(wolf);
			if (dog != null) {
				Vec3 location = hitResult.getLocation().subtract(dog.getX(), dog.getY(), dog.getZ());
				Minecraft.getInstance().getConnection().send(
					new ServerboundInteractPacket(dog.getId(), hand, location, player.isShiftKeyDown())
				);
				cir.setReturnValue(InteractionResult.SUCCESS);
			}
		}
	}

	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void humananddog$redirectWolfAttack(Player player, Entity entity, CallbackInfo ci) {
		if (entity instanceof DummyWolf) {
			// 替身狼是队友，禁止对它发起攻击（避免误伤狗）
			ci.cancel();
		}
	}
}
