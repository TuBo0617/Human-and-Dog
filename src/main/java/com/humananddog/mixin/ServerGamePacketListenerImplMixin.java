package com.humananddog.mixin;

import com.humananddog.role.PlayerData;
import com.humananddog.session.GamePhase;
import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 暂停 / 倒计时期间禁止任何玩家移动；
 * 狗坐下时钉在原地；拴绳 15 格外禁止继续远离（允许往回靠近）。
 *
 * 关键：坐下与 15 格硬锁不再用"取消整包 + teleport 回拉"（会有橡皮筋、能看到自己走出去再弹回），
 * 而是在方法入口用 @ModifyVariable 把请求坐标直接钳制到允许的位置，
 * 让原版移动流程照常进行：服务端最终接受的坐标就是钳制后的坐标，客户端几乎无感，真正"走不动"。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Shadow
	public abstract void teleport(double x, double y, double z, float yRot, float xRot);

	/** 本次移动是否需要把水平坐标钳制到 {@link #hd$clampX} / {@link #hd$clampZ}。 */
	private boolean hd$clamp;
	private double hd$clampX;
	private double hd$clampZ;

	@Inject(method = "handlePlayerPositionChange", at = @At("HEAD"), cancellable = true)
	private void humanAndDog$freezeMovement(double requestedX, double requestedY, double requestedZ,
										   float requestedYRot, float requestedXRot, boolean isOnGround,
										   boolean horizontalCollision, CallbackInfo ci) {
		hd$clamp = false;
		GameSession session = GameSessions.get();
		if (session == null) {
			return;
		}
		// 骑乘（船 / 矿车等）时位置由载具决定，交给服务端载具逻辑，避免误锁
		if (this.player.isPassenger()) {
			return;
		}

		// 暂停 / 倒计时：禁止一切移动，强制回拉到服务端当前位置（消除死亡惯性 / 本地预测滑行）
		if (session.isPaused()) {
			ci.cancel();
			this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(),
				requestedYRot, requestedXRot);
			return;
		}

		double clampX = requestedX;
		double clampZ = requestedZ;
		boolean needClamp = false;

		// 坐下的狗：水平坐标钉在记录的坐下位置（允许原地起跳、转动视角）
		if (session.isDog(this.player.getUUID())) {
			PlayerData data = session.data(this.player);
			if (data != null && data.sitting && data.sitPosSet) {
				double dx = requestedX - data.sitX;
				double dz = requestedZ - data.sitZ;
				if (dx * dx + dz * dz > 0.0025) {
					clampX = data.sitX;
					clampZ = data.sitZ;
					needClamp = true;
				}
			}
		}

		// 拴绳：PLAYING 阶段，新位置到对方的水平距离超过 15 格时，把水平坐标钳到正好 15 格边界
		if (!needClamp && session.getPhase() == GamePhase.PLAYING) {
			ServerPlayer partner = partnerOf(session, this.player);
			if (partner != null && this.player.level() == partner.level()) {
				double ddx = requestedX - partner.getX();
				double ddz = requestedZ - partner.getZ();
				double newDistH = Math.sqrt(ddx * ddx + ddz * ddz);
				if (newDistH > GameSession.MAX_BOND_DISTANCE) {
					double k = GameSession.MAX_BOND_DISTANCE / newDistH;
					clampX = partner.getX() + ddx * k;
					clampZ = partner.getZ() + ddz * k;
					needClamp = true;
				}
			}
		}

		if (needClamp) {
			hd$clamp = true;
			hd$clampX = clampX;
			hd$clampZ = clampZ;
		}
	}

	@ModifyVariable(method = "handlePlayerPositionChange", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double humanAndDog$clampReqX(double value) {
		return hd$clamp ? hd$clampX : value;
	}

	@ModifyVariable(method = "handlePlayerPositionChange", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private double humanAndDog$clampReqZ(double value) {
		return hd$clamp ? hd$clampZ : value;
	}

	private static ServerPlayer partnerOf(GameSession session, ServerPlayer self) {
		if (session.isDog(self.getUUID())) {
			return session.human();
		}
		if (session.isHuman(self.getUUID())) {
			ServerPlayer best = null;
			double bestDist = Double.MAX_VALUE;
			for (ServerPlayer d : session.dogs()) {
				double dd = self.distanceTo(d);
				if (dd < bestDist) {
					bestDist = dd;
					best = d;
				}
			}
			return best;
		}
		return null;
	}
}
