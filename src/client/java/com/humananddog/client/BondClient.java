package com.humananddog.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端预测侧的拴绳硬约束。
 *
 * <p>服务端对移动包做三维钳制（{@code ServerGamePacketListenerImplMixin}）并且每 tick 兜底拉回，
 * 但那只是"权威位置"。玩家自己的客户端是位置权威方：如果客户端不自己停住，
 * 就会出现"服务端认为你在 15 格、你自己却掉到谷底"的错位。
 *
 * <p>这里在 {@code Entity.move} 前把"继续远离"的位移分量去掉（沿 15 格球面滑动），
 * 于是掉崖时下坠分量被抵消 —— 狗会稳稳挂在空中，而不是每 tick 被服务端拽回去抖动。
 */
public final class BondClient {
	/** 与服务端一致的硬上限（格，三维）。 */
	public static final double MAX_DISTANCE = 15.0;

	private BondClient() {
	}

	/** 自己和谁被拴在一起；观战者 / 未开局时返回 null。 */
	public static Entity partnerOf(Player self) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ClientState.isSpectator()) {
			return null;
		}
		if (!"PLAYING".equals(ClientState.phase)) {
			return null;
		}
		boolean selfIsDog = ClientState.isDog(self.getUUID());
		if (selfIsDog) {
			for (AbstractClientPlayer candidate : mc.level.players()) {
				if (!ClientState.isDog(candidate.getUUID()) && !candidate.isSpectator()) {
					return candidate;
				}
			}
			return null;
		}
		for (AbstractClientPlayer candidate : mc.level.players()) {
			if (ClientState.isDog(candidate.getUUID())) {
				return candidate;
			}
		}
		return null;
	}

	/** 去掉"继续远离搭档"的位移分量，保留沿球面的切线位移。 */
	public static Vec3 clampMovement(Player self, Vec3 movement) {
		Entity partner = partnerOf(self);
		if (partner == null || self.isPassenger()) {
			return movement;
		}
		Vec3 relative = self.position().subtract(partner.position());
		double distance = relative.length();
		if (distance <= MAX_DISTANCE) {
			return movement;
		}
		Vec3 direction = relative.scale(1.0 / distance);
		double outward = movement.dot(direction);
		return outward <= 0.0 ? movement : movement.subtract(direction.scale(outward));
	}

	/** 已经超出上限（传送 / 击退 / 服务端纠正）：直接拉回 15 格边界并卸掉外冲速度。 */
	public static void snapIfNeeded(Player self) {
		Entity partner = partnerOf(self);
		if (partner == null || self.isPassenger()) {
			return;
		}
		Vec3 relative = self.position().subtract(partner.position());
		double distance = relative.length();
		if (distance <= MAX_DISTANCE + 1.0E-3) {
			return;
		}
		Vec3 direction = relative.scale(1.0 / distance);
		Vec3 target = partner.position().add(direction.scale(MAX_DISTANCE));
		self.setPos(target.x, target.y, target.z);
		Vec3 velocity = self.getDeltaMovement();
		double outward = velocity.dot(direction);
		if (outward > 0.0) {
			self.setDeltaMovement(velocity.subtract(direction.scale(outward)));
		}
		self.fallDistance = 0.0;
	}
}
