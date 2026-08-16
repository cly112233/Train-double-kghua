package xiao.hua.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class LensViewScreen extends AbstractContainerScreen<LensViewScreenHandler> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/generic_54.png");

	private static final int BACKGROUND_WIDTH = 176;

	public LensViewScreen(LensViewScreenHandler handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 89;
		this.inventoryLabelY = 6;
		this.titleLabelX = this.imageWidth - 94;
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = -9999;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.renderTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		int x = (this.width - this.imageWidth) / 2;
		int y = (this.height - this.imageHeight) / 2;
		context.blit(TEXTURE, x, y, 0, 0, this.imageWidth, 17);
		for (int row = 0; row < 4; row++)
			context.blit(TEXTURE, x, y + 17 + row * 18, 0, 17, this.imageWidth, 18);
		context.blit(TEXTURE, x, y + 17 + 72, 0, 215, this.imageWidth, 7);
	}

	@Override
	protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
		context.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
		Component readOnlyText = Component.translatable("screen.huarolemods.lens_view.read_only");
		int textWidth = this.font.width(readOnlyText);
		context.drawString(this.font, readOnlyText, this.imageWidth - textWidth - 8, this.inventoryLabelY, 8421504, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}