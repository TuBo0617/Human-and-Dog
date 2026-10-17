package com.humananddog.mechanic;

import java.util.Set;

import com.humananddog.session.GamePhase;
import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 维度联动：人或狗进入其他维度时，所有参与者都传送到进维度者所在的位置。
 */
public final class DimensionMechanic {
	private DimensionMechanic() {
	}

	public static void register() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			GameSession session = GameSessions.get();
			if (session == null || !session.isParticipant(player.getUUID())) {
				return;
			}
			if (session.getPhase() != GamePhase.PLAYING) {
				return;
			}
			ServerLevel dest = destination;
			double x = player.getX();
			double y = player.getY();
			double z = player.getZ();
			for (ServerPlayer other : session.allParticipants()) {
				if (other.getUUID().equals(player.getUUID())) {
					continue;
				}
				other.teleportTo(dest, x, y, z, Set.of(), other.getYRot(), other.getXRot(), false);
			}
		});
	}
}
