package kz.minekz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kz.minekz.LockedItems;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Locked Items move freely between the player's own slots but can't go into containers, crafting grids or armor
 * slots, and can't be thrown out of the screen. Runs on client and server, so the client never mispredicts.
 */
@Mixin(AbstractContainerMenu.class)
abstract class ContainerMenuMixin {
	@Shadow
	@Final
	public NonNullList<Slot> slots;

	@Shadow
	public abstract ItemStack getCarried();

	/** Whose menu this is: the player whose inventory it shows, as these hooks aren't handed the player. */
	@Unique
	private Player minekz$owner() {
		return slots.stream().map(slot -> slot.container).filter(Inventory.class::isInstance).map(c -> ((Inventory) c).player).findFirst().orElse(null);
	}

	@Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
	private void minekz$noThrow(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
		boolean dropCarried = slotId == -999 /* clicked outside the window */ && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)
				&& LockedItems.isLocked(getCarried(), player);
		boolean throwSlot = type == ClickType.THROW && slotId >= 0 && LockedItems.isLocked(slots.get(slotId).getItem(), player);
		if (dropCarried || throwSlot) {
			ci.cancel();
		}
	}

	// every placement click (pickup, drag, hotbar swap) asks the slot first; ArmorSlot and container slots answer no
	@WrapOperation(method = "doClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
	private boolean minekz$mayPlace(Slot slot, ItemStack stack, Operation<Boolean> original) {
		return (LockedItems.canHold(slot) || !LockedItems.isLocked(stack, minekz$owner())) && original.call(slot, stack);
	}

	// clicking onto an empty slot inserts without the doClick mayPlace check (Slot.safeInsert asks it internally)
	@WrapOperation(method = "doClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack minekz$safeInsert(Slot slot, ItemStack stack, int count, Operation<ItemStack> original) {
		return LockedItems.canHold(slot) || !LockedItems.isLocked(stack, minekz$owner()) ? original.call(slot, stack, count) : stack;
	}

	// shift-click: vanilla merges into existing stacks without asking mayPlace, so refuse the whole target range
	@Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
	private void minekz$noQuickMove(ItemStack stack, int start, int end, boolean reverse, CallbackInfoReturnable<Boolean> cir) {
		if (LockedItems.isLocked(stack, minekz$owner()) && slots.subList(start, end).stream().anyMatch(slot -> !LockedItems.canHold(slot))) {
			cir.setReturnValue(false);
		}
	}

	// bundles: clicking a Locked Item onto a bundle (or a bundle onto it) would store it
	@Inject(method = "tryItemClickBehaviourOverride", at = @At("HEAD"), cancellable = true)
	private void minekz$noBundle(Player player, ClickAction action, Slot slot, ItemStack slotItem, ItemStack carried,
			CallbackInfoReturnable<Boolean> cir) {
		if (LockedItems.isLocked(slotItem, player) || LockedItems.isLocked(carried, player)) {
			cir.setReturnValue(false);
		}
	}
}
