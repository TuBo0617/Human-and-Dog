package com.humananddog.client.screen;

import com.humananddog.client.ClientState;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 重生倒计时：3 秒内世界暂停，任何玩家无法移动。
 */
public class CountdownScreen extends Screen {
	public CountdownScreen() {
		super(Component.translatable("human-and-dog.screen.countdown.title"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		// 半透明遮罩
		graphics.fill(0, 0, this.width, this.height, 0x80000000);
		String title = this.title.getString();
		String number = String.valueOf(Math.max(1, ClientState.countdown));
		graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, this.height / 2 - 50, 0xFFFFFFFF);
		graphics.text(this.font, number, this.width / 2 - this.font.width(number) / 2, this.height / 2 - 20, 0xFFFF5555);
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
