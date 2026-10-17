package com.humananddog.mixin;

import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 任意参与者设置重生点后，把该重生点同步给其余所有参与者。
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
	private static boolean humanAndDog$syncing = false;

	@Inject(method = "setRespawnPosition(Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;Z)V", at = @At("TAIL"))
	private void humanAndDog$shareRespawn(ServerPlayer.RespawnConfig config, boolean showMessage, CallbackInfo ci) {
		if (humanAndDog$syncing || config == null || config.respawnData() == null) {
			return;
		}
		ServerPlayer self = (ServerPlayer) (Object) this;
		GameSession session = GameSessions.get();
		if (session == null || !session.isParticipant(self.getUUID())) {
			return;
		}
		session.setSharedRespawn(config.respawnData().globalPos());
		humanAndDog$syncing = true;
		try {
			for (ServerPlayer other : session.allParticipants()) {
				if (!other.getUUID().equals(self.getUUID())) {
					other.setRespawnPosition(config, false);
				}
			}
		} finally {
			humanAndDog$syncing = false;
		}
	}
}
