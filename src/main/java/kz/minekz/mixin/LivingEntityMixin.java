package kz.minekz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kz.minekz.LockedItems;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A locked totem of undying doesn't save the player. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@WrapOperation(method = "checkTotemDeathProtection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private Object minekz$lockedTotem(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
		return LockedItems.isLocked(stack, (LivingEntity) (Object) this) ? null : original.call(stack, type);
	}
}
