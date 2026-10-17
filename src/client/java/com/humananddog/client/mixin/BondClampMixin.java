package com.humananddog.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.humananddog.client.BondClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端预测侧执行 15 格拴绳硬约束（详见 {@link BondClient}）。
 * 只处理本地玩家：每帧的位移先被"投影"到 15 格球面上，之后再做一次兜底位置纠正。
 */
@Mixin(Entity.class)
public abstract class BondClampMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Vec3 humananddog$clampBondMovement(Vec3 movement) {
		if ((Object) this instanceof Player player && player == Minecraft.getInstance().player) {
			return BondClient.clampMovement(player, movement);
		}
		return movement;
	}

	@Inject(method = "move", at = @At("TAIL"))
	private void humananddog$snapBondAfterMove(MoverType type, Vec3 movement, CallbackInfo ci) {
		if ((Object) this instanceof Player player && player == Minecraft.getInstance().player) {
			BondClient.snapIfNeeded(player);
		}
	}
}
