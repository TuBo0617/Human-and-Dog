package com.humananddog.session;

import net.minecraft.server.MinecraftServer;

/**
 * 会话持有者。集成服务器单存档，因此使用单例。
 */
public final class GameSessions {
	private static GameSession current;

	private GameSessions() {
	}

	public static GameSession start(MinecraftServer server) {
		current = new GameSession(server);
		current.loadFromSave();
		return current;
	}

	public static void stop() {
		current = null;
	}

	public static GameSession get() {
		return current;
	}

	public static GameSession require(MinecraftServer server) {
		if (current == null) {
			current = new GameSession(server);
		}
		return current;
	}
}
