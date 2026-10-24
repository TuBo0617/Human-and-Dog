package com.humananddog.mixin;

import com.humananddog.role.DogLookup;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 狗被打时不发出"人"的受伤音效。
 *
 * <p>原版 {@code Player.getHurtSound} 返回 {@code entity.player.hurt}，狗被打会听到人声。
 * 这里对狗返回 null（{@code LivingEntity.playHurtSound} 会对 null 直接跳过），
 * 真正的狼受伤音效由 {@code DogMechanic.playHurtSound} 在 AFTER_DAMAGE 里播放。
 */
@Mixin(Player.class)
public abstract class PlayerSoundMixin {
	@Inject(method = "getHurtSound", at = @At("RETURN"), cancellable = true)
	private void humanAndDog$muteHumanHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
		if (DogLookup.isDog((Player) (Object) this)) {
			cir.setReturnValue(null);
		}
	}
}
