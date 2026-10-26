package kz.minekz.client.mixin;

import kz.minekz.LockedItems;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Matches the server refusing the drop key, so the client doesn't predict the Locked Item vanishing. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
	@Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
	private void minekz$noDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
		if (LockedItems.isLocked(((LocalPlayer) (Object) this).getMainHandItem(), (LocalPlayer) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
