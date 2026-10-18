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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 人看到的是"替身狼"，右键 / 左键命中替身狼时，把动作转发给它对应的狗玩家。
 *
 * <p>左键以前是直接取消（防误伤），现在改成把目标换成狗玩家：
 * {@code MultiPlayerGameMode.attack(player, entity)} 会用这个 entity 发包、播放挥手与打击音效，
 * 所以只要把参数换掉，玩家就能像打普通玩家一样打到狗（伤害走原版流程，会触发死亡联动）。
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

	/** 把"左键打替身狼"的目标换成狗玩家本体。 */
	@ModifyVariable(method = "attack", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Entity humananddog$redirectWolfAttackTarget(Entity target) {
		if (target instanceof DummyWolf wolf) {
			AbstractClientPlayer dog = DogClientManager.findDogPlayer(wolf);
			if (dog != null) {
				return dog;
			}
		}
		return target;
	}
}
