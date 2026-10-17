package com.humananddog.command;

import java.util.Collection;
import java.util.Collections;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;

/**
 * 新命令 /mode，功能与原版 /gamemode 完全一致。
 * 用法：/mode &lt;模式&gt; [玩家]
 */
public final class ModeCommand {
	private ModeCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("mode")
				.requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
				.then(
					Commands.argument("gamemode", GameModeArgument.gameMode())
						.executes(ModeCommand::setOwnMode)
						.then(
							Commands.argument("targets", EntityArgument.players())
								.executes(ModeCommand::setOthersMode)
						)
				)
		);
	}

	private static int setOwnMode(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		GameType mode = GameModeArgument.getGameMode(context, "gamemode");
		ServerPlayer player = context.getSource().getPlayerOrException();
		player.setGameMode(mode);
		context.getSource().sendSuccess(
			() -> Component.translatable("commands.gamemode.success.self", mode.getLongDisplayName()), true
		);
		return 1;
	}

	private static int setOthersMode(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		GameType mode = GameModeArgument.getGameMode(context, "gamemode");
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		int count = 0;
		for (ServerPlayer player : targets) {
			player.setGameMode(mode);
			count++;
		}
		if (count == 1) {
			ServerPlayer player = targets.iterator().next();
			context.getSource().sendSuccess(
				() -> Component.translatable("commands.gamemode.success.other", player.getDisplayName(), mode.getLongDisplayName()), true
			);
		} else {
			context.getSource().sendSuccess(
				() -> Component.translatable("commands.gamemode.success.multiple", targets.size(), mode.getLongDisplayName()), true
			);
		}
		return count;
	}

	public static void registerCallback() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}
}
