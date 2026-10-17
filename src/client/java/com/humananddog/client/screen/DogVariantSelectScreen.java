package com.humananddog.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.humananddog.net.payload.SelectDogVariantPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.wolf.WolfVariant;

/**
 * 狗第一次进入时选择狗的类型（狼变种）。名称走语言文件，未知变种回退显示其 id。
 */
public class DogVariantSelectScreen extends Screen {
	public DogVariantSelectScreen() {
		super(Component.translatable("human-and-dog.screen.variant.title"));
	}

	@Override
	protected void init() {
		List<Identifier> variants = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			Registry<WolfVariant> registry = mc.level.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT);
			for (Identifier id : registry.keySet()) {
				variants.add(id);
			}
		}
		variants.sort(Comparator.comparing(Identifier::getPath));

		int columns = 2;
		int btnW = 150;
		int btnH = 20;
		int gap = 4;
		int totalW = columns * btnW + gap;
		int startX = this.width / 2 - totalW / 2;
		int startY = 44;

		for (int i = 0; i < variants.size(); i++) {
			Identifier id = variants.get(i);
			int col = i % columns;
			int row = i / columns;
			int x = startX + col * (btnW + gap);
			int y = startY + row * (btnH + gap);
			Component label = Component.translatableWithFallback(
				"human-and-dog.variant." + id.getPath(), id.getPath()
			);
			this.addRenderableWidget(
				Button.builder(label, b -> choose(id.toString()))
					.bounds(x, y, btnW, btnH).build()
			);
		}
	}

	private void choose(String variantId) {
		ClientPlayNetworking.send(new SelectDogVariantPayload(variantId));
		// 选择后进入等待（人尚未加入 / 还有其他狗未确认，或游戏立即开始）
		Minecraft.getInstance().gui.setScreen(new WaitingScreen());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		String title = this.title.getString();
		graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 22, 0xFFFFFFFF);
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
