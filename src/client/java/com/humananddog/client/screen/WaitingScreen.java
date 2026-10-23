package com.humananddog.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * 等待另一位玩家（狗加入并选择类型 / 掉线的伙伴回来）。
 * 按 ESC 打开原版暂停菜单，方便房主"对局域网开放"拉第二人；
 * 按 T 打开聊天框，可以在等待时输入 /hd 指令切换身份。
 */
public class WaitingScreen extends Screen {
	public WaitingScreen() {
		super(Component.translatable("human-and-dog.screen.waiting.title"));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		Minecraft mc = Minecraft.getInstance();
		// 等待界面是个 GUI，默认吃不到聊天键；这里手动转发，方便输入 /hd role ...
		if (mc.options.keyChat.matches(event)) {
			mc.gui.setScreen(new ChatScreen("", false));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		String title = this.title.getString();
		graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, this.height / 2 - 10, 0xFFFFFFFF);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void onClose() {
		// 不直接关闭到游戏，而是打开原版暂停菜单（含"对局域网开放"）
		Minecraft.getInstance().gui.setScreen(new PauseScreen(true));
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
