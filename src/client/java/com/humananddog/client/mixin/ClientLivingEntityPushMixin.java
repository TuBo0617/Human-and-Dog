package com.humananddog.client.mixin;

import com.humananddog.client.ClientState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端预测侧同样取消人狗之间的实体推挤，否则本地预测会把人 / 狗缓慢推开，
 * 偏移位置再被服务端接受，表现为“松开按键后仍缓慢向前移动”。
 */
@Mixin(LivingEntity.class)
public class ClientLivingEntityPushMixin {
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void humanAndDog$noPushBetweenParticipants(Entity other, CallbackInfo ci) {
		if ((Object) this instanceof Player self && other instanceof Player otherPlayer) {
			if (ClientState.isDog(self.getUUID()) || ClientState.isDog(otherPlayer.getUUID())) {
				ci.cancel();
			}
		}
	}
}
