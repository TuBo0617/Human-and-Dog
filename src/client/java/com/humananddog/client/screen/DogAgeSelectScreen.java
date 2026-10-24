package com.humananddog.client.screen;

import com.humananddog.net.payload.SelectDogAgePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 选了"当狗"之后先选年龄：小狗 / 成年狗。选完才进狼变种界面。
 * 小狗可以用金蒲公英锁住年龄（永远保持幼犬）。
 */
public class DogAgeSelectScreen extends Screen {
	public DogAgeSelectScreen() {
		super(Component.translatable("human-and-dog.screen.age.title"));
	}

	@Override
	protected void init() {
		int w = 200;
		int h = 24;
		int x = this.width / 2 - w / 2;
		int y = this.height / 2 - 20;
		this.addRenderableWidget(
			Button.builder(Component.translatable("human-and-dog.button.puppy"), b -> choose(true))
				.bounds(x, y, w, h).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("human-and-dog.button.adult"), b -> choose(false))
				.bounds(x, y + 32, w, h).build()
		);
	}

	private void choose(boolean baby) {
		ClientPlayNetworking.send(new SelectDogAgePayload(baby));
		// 服务端随后会推狼变种界面；这里先离开本界面，避免卡住
		Minecraft.getInstance().gui.setScreen(new WaitingScreen());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		String title = this.title.getString();
		graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, this.height / 2 - 64, 0xFFFFFFFF);
		Component hint = Component.translatable("human-and-dog.screen.age.hint");
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
