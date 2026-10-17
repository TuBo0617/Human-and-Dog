package com.humananddog.mixin;

import java.util.Locale;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 只允许 /mode 命令，其余所有命令一律不执行。
 */
@Mixin(Commands.class)
public class CommandsMixin {
	@Inject(method = "performPrefixedCommand", at = @At("HEAD"), cancellable = true)
	private void humanAndDog$blockOtherCommands(CommandSourceStack sender, String command, CallbackInfo ci) {
		String trimmed = command.startsWith("/") ? command.substring(1) : command;
		String trimmedNoSpace = trimmed.trim();
		if (trimmedNoSpace.isEmpty()) {
			ci.cancel();
			return;
		}
		String root = trimmedNoSpace.split("\\s+")[0].toLowerCase(Locale.ROOT);
		if (!root.equals("mode")) {
			ci.cancel();
		}
	}
}
