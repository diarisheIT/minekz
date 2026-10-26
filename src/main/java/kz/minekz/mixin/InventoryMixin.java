package kz.minekz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kz.minekz.LockedItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The recipe book can't see Locked Items, so it never moves them into a crafting grid. */
@Mixin(Inventory.class)
abstract class InventoryMixin {
	@Shadow
	@Final
	public Player player;

	@WrapOperation(method = "fillStackedContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/StackedItemContents;accountSimpleStack(Lnet/minecraft/world/item/ItemStack;)V"))
	private void minekz$hideLocked(StackedItemContents contents, ItemStack stack, Operation<Void> original) {
		if (!LockedItems.isLocked(stack, player)) {
			original.call(contents, stack);
		}
	}
}
