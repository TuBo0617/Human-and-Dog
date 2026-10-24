package com.humananddog.client;

import com.humananddog.HumanAndDogMod;
import com.humananddog.net.payload.BarkPayload;
import com.humananddog.net.payload.TakeBlocksPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * 按键绑定。
 *
 * <p>MC 26.3 的 {@link KeyMapping} 分类改成了 {@code KeyMapping.Category}（带 {@link Identifier}），
 * 语言键变成 {@code key.category.<namespace>.<path>}，这里注册一个"人狗同行"分类。
 */
public final class ModKeybinds {
	/** 自定义按键分类：显示在"控制"菜单里。 */
	public static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(HumanAndDogMod.MOD_ID, "keys"));

	/** 狗叫：默认 V。 */
	public static final KeyMapping BARK = new KeyMapping("key.human-and-dog.bark", InputConstants.KEY_V, CATEGORY);

	/** 取出狗身上的挂件方块：默认 G。 */
	public static final KeyMapping TAKE_BLOCKS =
		new KeyMapping("key.human-and-dog.take_blocks", InputConstants.KEY_G, CATEGORY);

	private ModKeybinds() {
	}

	public static void register() {
		// 必须通过 Fabric API 注册，否则 MC 的 Options.keyMappings 是固定数组，
		// 自定义按键不会出现在"控制"菜单里。
		KeyMappingHelper.registerKeyMapping(BARK);
		KeyMappingHelper.registerKeyMapping(TAKE_BLOCKS);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (BARK.consumeClick()) {
				// 人 / 观战者按了无效；服务端还会再校验一次身份
				if (ClientState.localPlayerIsDog()) {
					ClientPlayNetworking.send(new BarkPayload());
				}
			}
			while (TAKE_BLOCKS.consumeClick()) {
				// 只允许主人取；服务端还会再校验一次
				if ("HUMAN".equals(ClientState.myRole)) {
					ClientPlayNetworking.send(new TakeBlocksPayload());
				}
			}
		});
	}
}
