package com.humananddog;

import com.humananddog.command.ModeCommand;
import com.humananddog.mechanic.DeathMechanic;
import com.humananddog.mechanic.DimensionMechanic;
import com.humananddog.mechanic.DogMechanic;
import com.humananddog.net.ModNetworking;
import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HumanAndDogMod implements ModInitializer {
	public static final String MOD_ID = "human-and-dog";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[人狗同行] 服务端初始化");

		// 网络
		ModNetworking.registerCommon();
		ModNetworking.registerServerReceivers();

		// 唯一允许的命令 /mode
		ModeCommand.registerCallback();

		// 狗的属性、限制与交互
		DogMechanic.register();
		// 死亡联动与重生倒计时
		DeathMechanic.register();
		// 维度联动
		DimensionMechanic.register();

		// 会话生命周期
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			GameSessions.start(server);
			server.getWorldData().setAllowCommands(true);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> GameSessions.stop());

		// 玩家加入
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> {
				server.getWorldData().setAllowCommands(true);
				GameSession session = GameSessions.require(server);
				session.onJoin(handler.getPlayer());
			})
		);

		// 服务器每 tick
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			GameSession session = GameSessions.get();
			if (session != null) {
				session.tick();
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
