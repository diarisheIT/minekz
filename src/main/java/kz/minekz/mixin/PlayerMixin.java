package kz.minekz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kz.minekz.LockedItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A Locked tool mines like a bare hand: base speed, and no tool-only drops. */
@Mixin(Player.class)
abstract class PlayerMixin {
	@WrapOperation(method = "getDestroySpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F"))
	private float minekz$bareHandSpeed(ItemStack tool, BlockState state, Operation<Float> original) {
		return LockedItems.isLocked(tool, (Player) (Object) this) ? 1.0f : original.call(tool, state);
	}

	@WrapOperation(method = "hasCorrectToolForDrops", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean minekz$bareHandDrops(ItemStack tool, BlockState state, Operation<Boolean> original) {
		return !LockedItems.isLocked(tool, (Player) (Object) this) && original.call(tool, state);
	}
}
