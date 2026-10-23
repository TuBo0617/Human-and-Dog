package com.humananddog.client;

import java.util.UUID;

import com.humananddog.client.render.DogClientManager;
import com.humananddog.client.screen.CountdownScreen;
import com.humananddog.client.screen.DogAgeSelectScreen;
import com.humananddog.client.screen.DogVariantSelectScreen;
import com.humananddog.client.screen.RoleSelectScreen;
import com.humananddog.client.screen.WaitingScreen;
import com.humananddog.net.payload.ClearDogVisualPayload;
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

		// 等待阶段的界面自愈：在等待界面按 T 输入指令后，聊天框关闭会把界面置空，
		// 这里补回等待界面，避免"世界暂停但没有界面"的假死感。
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.gui.screen() != null) {
				return;
			}
			String phase = ClientState.phase;
			boolean waiting = "WAITING_HUMAN".equals(phase)
				|| "WAITING_DOGS".equals(phase)
				|| "WAITING_PARTNER".equals(phase);
			if (!waiting) {
				return;
			}
			if ("HUMAN".equals(ClientState.myRole) || "DOG".equals(ClientState.myRole)) {
				client.gui.setScreen(new WaitingScreen());
			} else if ("NONE".equals(ClientState.myRole) && "WAITING_HUMAN".equals(phase)) {
				client.gui.setScreen(new RoleSelectScreen());
			}
		});
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
						// 观战者也不该被拉进等待界面（他们本来就不参与开局）。
						if (!ClientState.isSpectator()
							&& !(current instanceof DogVariantSelectScreen)
							&& !(current instanceof DogAgeSelectScreen)
							&& !(current instanceof RoleSelectScreen)) {
							mc.gui.setScreen(new WaitingScreen());
						}
						break;
					case "WAITING_PARTNER":
						// 伙伴掉线：和开局等待用同一个界面，世界在服务端被暂停
						if (!ClientState.isSpectator()
							&& !(current instanceof DogVariantSelectScreen)
							&& !(current instanceof DogAgeSelectScreen)) {
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

		// 某个人不再是狗（/hd role 或 /hd swap）：删掉本地缓存，替身狼会在下一帧被回收
		ClientPlayNetworking.registerGlobalReceiver(ClearDogVisualPayload.TYPE, (payload, context) ->
			context.client().execute(() -> ClientState.removeDog(UUID.fromString(payload.uuid())))
		);
	}
}
