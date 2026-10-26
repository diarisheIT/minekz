package kz.minekz.mixin;

import kz.minekz.LockedItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks treat a Locked Item as an empty hand (no composter, pot or cauldron input), but chests and doors still open. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateMixin {
	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void minekz$emptyHand(ItemStack stack, Level level, Player player, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (LockedItems.isLocked(stack, player)) {
			cir.setReturnValue(InteractionResult.TRY_WITH_EMPTY_HAND);
		}
	}
}
