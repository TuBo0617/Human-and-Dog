package com.humananddog.net;

import com.humananddog.net.payload.DogVisualPayload;
import com.humananddog.net.payload.GameStatePayload;
import com.humananddog.net.payload.OpenDogVariantScreenPayload;
import com.humananddog.net.payload.OpenRoleScreenPayload;
import com.humananddog.net.payload.RoleStatePayload;
import com.humananddog.net.payload.SelectDogVariantPayload;
import com.humananddog.net.payload.SelectRolePayload;
import com.humananddog.role.Role;
import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void registerCommon() {
		// 服务端 -> 客户端
		PayloadTypeRegistry.clientboundPlay().register(OpenRoleScreenPayload.TYPE, OpenRoleScreenPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenDogVariantScreenPayload.TYPE, OpenDogVariantScreenPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GameStatePayload.TYPE, GameStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DogVisualPayload.TYPE, DogVisualPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RoleStatePayload.TYPE, RoleStatePayload.CODEC);
		// 客户端 -> 服务端
		PayloadTypeRegistry.serverboundPlay().register(SelectRolePayload.TYPE, SelectRolePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectDogVariantPayload.TYPE, SelectDogVariantPayload.CODEC);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(SelectRolePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> {
				GameSession session = GameSessions.get();
				if (session != null) {
					session.selectRole(player, Role.byName(payload.role()));
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(SelectDogVariantPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> {
				GameSession session = GameSessions.get();
				if (session != null) {
					session.selectVariant(player, payload.variantId());
				}
			});
		});
	}
}
