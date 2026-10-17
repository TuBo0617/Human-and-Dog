package com.humananddog.dev;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 仅用于本地开发自测的开关，生产环境（正式打包的 jar）恒为 false。
 * - SOLO：单人 QuickPlay 时自动把唯一玩家设为人类并直接开局，便于在没有第二名玩家时验证逻辑。
 * - DEV_DOG：单人 QuickPlay 时把唯一玩家设为狗（自动选默认变种并开局），用于验证狗视角 / F6 / 船床 / 限制。
 */
public final class DevFlags {
	/** JVM 加 -Dhd.devsolo=true 且为开发环境时，开启单人自动开局（人类）。 */
	public static final boolean SOLO = FabricLoader.getInstance().isDevelopmentEnvironment()
		&& Boolean.parseBoolean(System.getProperty("hd.devsolo", "false"));

	/** JVM 加 -Dhd.devdog=true 时，单人开局自动成为狗。 */
	public static final boolean DEV_DOG = FabricLoader.getInstance().isDevelopmentEnvironment()
		&& Boolean.parseBoolean(System.getProperty("hd.devdog", "false"));

	private DevFlags() {
	}
}
