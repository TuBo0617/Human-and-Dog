package com.humananddog.client.screen;

import com.humananddog.client.ClientState;
import com.humananddog.net.payload.SelectRolePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 进入世界后的身份选择：当人 / 当狗 / 观战者。
 * 已经被占用的身份会被置灰（人、狗各只允许一名，其余自动观战）。
 */
public class RoleSelectScreen extends Screen {
	private Button humanButton;
	private Button dogButton;
	private Button spectatorButton;

	public RoleSelectScreen() {
		super(Component.translatable("human-and-dog.screen.role.title"));
	}

	@Override
	protected void init() {
		int w = 200;
		int h = 24;
		int x = this.width / 2 - w / 2;
		int y = this.height / 2 - 44;
		this.humanButton = this.addRenderableWidget(
			Button.builder(Component.translatable("human-and-dog.button.human"), b -> choose("HUMAN"))
				.bounds(x, y, w, h).build()
		);
		this.dogButton = this.addRenderableWidget(
			Button.builder(Component.translatable("human-and-dog.button.dog"), b -> choose("DOG"))
				.bounds(x, y + 32, w, h).build()
		);
		this.spectatorButton = this.addRenderableWidget(
			Button.builder(Component.translatable("human-and-dog.button.spectator"), b -> choose("SPECTATOR"))
				.bounds(x, y + 64, w, h).build()
		);
		this.refreshButtons();
	}

	/** 身份占用情况可能在界面打开之后才同步过来，因此每帧刷新一次按钮可用状态。 */
	private void refreshButtons() {
		if (this.humanButton != null) {
			this.humanButton.active = !ClientState.humanTaken;
		}
		if (this.dogButton != null) {
			this.dogButton.active = !ClientState.dogTaken;
		}
		if (this.spectatorButton != null) {
			this.spectatorButton.active = true;
		}
	}

	private void choose(String role) {
		ClientPlayNetworking.send(new SelectRolePayload(role));
		// 立刻离开角色界面。否则后续 WAITING_* 的同步会因为"当前是角色界面"而保留它
		// （见 HumanAndDogClient 里的保护逻辑），玩家会觉得按钮没反应，还能在同一界面连点改选，
		// 结果同一个人被记成房主又是狗，单人直接开局。
		Minecraft.getInstance().gui.setScreen(new WaitingScreen());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.refreshButtons();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		String title = this.title.getString();
		graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, this.height / 2 - 84, 0xFFFFFFFF);
		Component hint = Component.translatable("human-and-dog.screen.role.hint");
		graphics.text(this.font, hint.getString(),
			this.width / 2 - this.font.width(hint.getString()) / 2, this.height / 2 + 56, 0xFFAAAAAA);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
