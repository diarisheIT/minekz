package kz.minekz.mixin;

import kz.minekz.LockedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Locked Item can't be used (place, eat, drink, throw, equip by right-click) and mines like a bare hand. */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	// entity interactions are refused earlier, in LockedItems.register
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void minekz$refuseUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (LockedItems.isLocked((ItemStack) (Object) this, player)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}

	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void minekz$refuseUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (LockedItems.isLocked((ItemStack) (Object) this, context.getPlayer())) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}

	@Inject(method = "mineBlock", at = @At("HEAD"), cancellable = true)
	private void minekz$noToolWear(Level level, BlockState state, BlockPos pos, Player player, CallbackInfo ci) {
		if (LockedItems.isLocked((ItemStack) (Object) this, player)) {
			ci.cancel();
		}
	}
}
