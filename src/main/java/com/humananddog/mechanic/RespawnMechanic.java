package com.humananddog.mechanic;

import com.humananddog.session.GameSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 全体重生：倒计时结束后把所有人与狗传送到共享重生点并恢复状态。
 */
public final class RespawnMechanic {
	private RespawnMechanic() {
	}

	public static void performRespawn(GameSession session) {
		GlobalPos respawn = session.getSharedRespawn();
		for (ServerPlayer player : session.allParticipants()) {
			ServerLevel targetLevel;
			double x;
			double y;
			double z;
			if (respawn != null) {
				targetLevel = session.getServer().getLevel(respawn.dimension());
				if (targetLevel == null) {
					targetLevel = (ServerLevel) player.level();
				}
				BlockPos pos = respawn.pos();
				x = pos.getX() + 0.5;
				y = pos.getY();
				z = pos.getZ() + 0.5;
			} else {
				targetLevel = (ServerLevel) player.level();
				x = player.getX();
				y = player.getY();
				z = player.getZ();
			}

			if (player.level().dimension() != targetLevel.dimension()) {
				player.teleportTo(targetLevel, x, y, z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
			} else {
				player.teleportTo(x, y, z);
			}
			player.setDeltaMovement(Vec3.ZERO);
			// 狗恢复到原版驯服狼的 40 血，人恢复到默认 20 血
			if (session.isDog(player.getUUID())) {
				DogMechanic.setupDog(player);
				player.setHealth((float) DogMechanic.DOG_MAX_HEALTH);
			} else {
				player.setHealth(player.getMaxHealth());
				player.getFoodData().setFoodLevel(20);
				player.getFoodData().setSaturation(5.0F);
			}
			player.clearFire();
			player.setRemainingFireTicks(0);
			player.onUpdateAbilities();
		}
	}
}
