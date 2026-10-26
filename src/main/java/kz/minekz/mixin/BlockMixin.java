package kz.minekz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kz.minekz.LockedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Drops are rolled as if bare-handed: a locked tool gives no silk touch, fortune or shears drops. */
@Mixin(Block.class)
abstract class BlockMixin {
	@WrapOperation(method = "playerDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"))
	private void minekz$bareHandLoot(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity entity,
			ItemStack tool, Operation<Void> original) {
		original.call(state, level, pos, blockEntity, entity, LockedItems.isLocked(tool, entity) ? ItemStack.EMPTY : tool);
	}
}
