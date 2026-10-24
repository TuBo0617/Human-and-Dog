package com.humananddog.command;

import java.util.Locale;

import com.humananddog.HumanAndDogMod;
import com.humananddog.role.Role;
import com.humananddog.session.GameSession;
import com.humananddog.session.GameSessions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

/**
 * 人狗同行的全部指令，统一挂在 {@code /hd} 下。
 *
 * <ul>
 *   <li>{@code /hd role <human|dog|spectator>}：等待阶段修改自己的身份。
 *       目标身份已被别人占用时失败；游戏开始后不允许再改。</li>
 *   <li>{@code /hd age}：只有狗能用，重新打开"小狗 / 成年狗 → 狼变种"的选择流程。</li>
 *   <li>{@code /hd swap}：需要管理员权限，人和狗都在线时互换两者的身份。</li>
 * </ul>
 */
public final class HdCommand {
	private HdCommand() {
	}

	public static void registerCallback() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("hd")
				// 身份切换：任何玩家都能用，但只允许在开局前的等待阶段生效（服务端会再校验一次）
				.then(
					Commands.literal("role")
						.then(
							Commands.argument("role", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									builder.suggest("human");
									builder.suggest("dog");
									builder.suggest("spectator");
									return builder.buildFuture();
								})
								.executes(HdCommand::changeRole)
						)
				)
				// 狗重新选年龄 / 类型
				.then(Commands.literal("age").executes(HdCommand::dogAge))
				// 管理员互换人狗身份
				.then(
					Commands.literal("swap")
						.requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
						.executes(HdCommand::swap)
				)
		);
	}

	private static int changeRole(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		String raw = StringArgumentType.getString(context, "role").toUpperCase(Locale.ROOT);
		Role target = Role.byName(raw);
		if (target == Role.NONE) {
			context.getSource().sendFailure(Component.translatable("human-and-dog.command.role.invalid", raw));
			return 0;
		}
		GameSession session = GameSessions.get();
		if (session == null) {
			return 0;
		}
		GameSession.RoleChangeResult result = session.changeOwnRole(player, target);
		switch (result) {
			case OK -> context.getSource().sendSuccess(
				() -> Component.translatable("human-and-dog.command.role.success", roleName(target)), false);
			case LOCKED -> context.getSource().sendFailure(
				Component.translatable("human-and-dog.command.role.locked"));
			case TAKEN -> context.getSource().sendFailure(
				Component.translatable("human-and-dog.command.role.taken", roleName(target)));
			default -> {
			}
		}
		return result == GameSession.RoleChangeResult.OK ? 1 : 0;
	}

	private static int dogAge(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		GameSession session = GameSessions.get();
		if (session == null || !session.isDog(player.getUUID())) {
			context.getSource().sendFailure(Component.translatable("human-and-dog.command.age.only_dog"));
			return 0;
		}
		session.beginDogReconfigure(player);
		context.getSource().sendSuccess(
			() -> Component.translatable("human-and-dog.command.age.ok"), false);
		return 1;
	}

	private static int swap(CommandContext<CommandSourceStack> context) {
		GameSession session = GameSessions.get();
		if (session == null || !session.swapHumanAndDog()) {
			context.getSource().sendFailure(Component.translatable("human-and-dog.command.swap.need_both"));
			return 0;
		}
		HumanAndDogMod.LOGGER.info("[人狗同行] 管理员互换人狗身份");
		context.getSource().sendSuccess(
			() -> Component.translatable("human-and-dog.command.swap.done"), true);
		return 1;
	}

	private static Component roleName(Role role) {
		return Component.translatable("human-and-dog.role." + role.name().toLowerCase(Locale.ROOT));
	}
}
