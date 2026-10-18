package com.humananddog.client;

import java.util.UUID;

import com.humananddog.client.render.DogClientManager;
import com.humananddog.client.screen.CountdownScreen;
import com.humananddog.client.screen.DogAgeSelectScreen;
import com.humananddog.client.screen.DogVariantSelectScreen;
import com.humananddog.client.screen.RoleSelectScreen;
import com.humananddog.client.screen.WaitingScreen;
import com.humananddog.net.payload.DogVisualPayload;
import com.humananddog.net.payload.GameStatePayload;
import com.humananddog.net.payload.OpenDogAgeScreenPayload;
import com.humananddog.net.payload.OpenDogVariantScreenPayload;
import com.humananddog.net.payload.OpenRoleScreenPayload;
import com.humananddog.net.payload.RoleStatePayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class HumanAndDogClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 按键绑定（狗叫）
		ModKeybinds.register();
		// 每帧维护"狗玩家 -> 替身狼"
		ClientTickEvents.END_CLIENT_TICK.register(DogClientManager::tick);
		// 断开时清理替身
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			DogClientManager.clear();
			ClientState.reset();
		});

		// 房主选择角色
		ClientPlayNetworking.registerGlobalReceiver(OpenRoleScreenPayload.TYPE, (payload, context) ->
			context.client().execute(() -> context.client().gui.setScreen(new RoleSelectScreen()))
		);

		// 选了狗之后先选年龄（小狗 / 成年狗）
		ClientPlayNetworking.registerGlobalReceiver(OpenDogAgeScreenPayload.TYPE, (payload, context) ->
			context.client().execute(() -> context.client().gui.setScreen(new DogAgeSelectScreen()))
		);

		// 狗选择类型
		ClientPlayNetworking.registerGlobalReceiver(OpenDogVariantScreenPayload.TYPE, (payload, context) ->
			context.client().execute(() -> context.client().gui.setScreen(new DogVariantSelectScreen()))
		);

		// 游戏阶段 / 倒计时
		ClientPlayNetworking.registerGlobalReceiver(GameStatePayload.TYPE, (payload, context) -> {
			ClientState.phase = payload.phase();
			ClientState.countdown = payload.countdown();
			context.client().execute(() -> {
				Minecraft mc = context.client();
				Screen current = mc.gui.screen();
				switch (payload.phase()) {
					case "PLAYING":
						if (current instanceof RoleSelectScreen
							|| current instanceof WaitingScreen
							|| current instanceof DogAgeSelectScreen
							|| current instanceof DogVariantSelectScreen
							|| current instanceof CountdownScreen) {
							mc.gui.setScreen(null);
						}
						break;
					case "COUNTDOWN":
						mc.gui.setScreen(new CountdownScreen());
						break;
					case "WAITING_DOGS":
						// 正在选角色 / 选年龄 / 选狗类型的人不要被等待界面顶掉：
						// 否则"狗先确认、人还没选"时人会被永久困在等待界面（再也选不了当人）。
						if (!(current instanceof DogVariantSelectScreen)
							&& !(current instanceof DogAgeSelectScreen)
							&& !(current instanceof RoleSelectScreen)) {
							mc.gui.setScreen(new WaitingScreen());
						}
						break;
					case "WAITING_HUMAN":
						// 已经选了身份（观战者）的玩家不再弹角色界面
						if (!(current instanceof RoleSelectScreen) && "NONE".equals(ClientState.myRole)) {
							mc.gui.setScreen(new RoleSelectScreen());
						}
						break;
					default:
						break;
				}
			});
		});

		// 身份占用情况 + 自己的身份（角色界面据此置灰）
		ClientPlayNetworking.registerGlobalReceiver(RoleStatePayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				ClientState.humanTaken = payload.humanTaken();
				ClientState.dogTaken = payload.dogTaken();
				ClientState.myRole = payload.myRole();
				Minecraft mc = context.client();
				// 观战者不该停在"等待其他玩家加入"这类参与者界面里
				if ("SPECTATOR".equals(payload.myRole())
					&& (mc.gui.screen() instanceof WaitingScreen
					|| mc.gui.screen() instanceof RoleSelectScreen)) {
					mc.gui.setScreen(null);
				}
			})
		);

		// 狗外观数据
		ClientPlayNetworking.registerGlobalReceiver(DogVisualPayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				UUID id = UUID.fromString(payload.uuid());
				ClientState.putDog(id, new ClientState.DogVisual(
					payload.variantId(), payload.collarColor(), payload.sitting(), payload.hasArmor(), payload.baby()
				));
			})
		);
	}
}
