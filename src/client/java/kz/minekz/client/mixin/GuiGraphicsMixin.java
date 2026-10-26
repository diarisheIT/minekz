package kz.minekz.client.mixin;

import kz.minekz.LockedItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inventory and hotbar slots both draw decorations here: grey out a Locked Item and add a padlock. */
@Mixin(GuiGraphics.class)
abstract class GuiGraphicsMixin {
	@Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
	private void minekz$padlock(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
		if (!LockedItems.isLockedHere(stack)) {
			return;
		}
		GuiGraphics g = (GuiGraphics) (Object) this;
		g.fill(x, y, x + 16, y + 16, 0xA0303030);
		g.fill(x + 2, y + 1, x + 7, y + 2, 0xFFC0C0C0); // shackle
		g.fill(x + 2, y + 2, x + 3, y + 5, 0xFFC0C0C0);
		g.fill(x + 6, y + 2, x + 7, y + 5, 0xFFC0C0C0);
		g.fill(x + 1, y + 5, x + 8, y + 10, 0xFFFFD040); // body
		g.fill(x + 4, y + 6, x + 5, y + 9, 0xFF000000); // keyhole
	}
}
