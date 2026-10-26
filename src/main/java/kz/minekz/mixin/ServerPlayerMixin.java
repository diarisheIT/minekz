package kz.minekz.mixin;

import kz.minekz.LockedItems;
import kz.minekz.MineKz;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The drop key does nothing on a Locked Item, and dying Forfeits before the inventory drops. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
	private void minekz$noDrop(boolean fullStack, CallbackInfo ci) {
		if (LockedItems.isLocked(((ServerPlayer) (Object) this).getMainHandItem(), (ServerPlayer) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "die", at = @At("HEAD"))
	private void minekz$forfeitOnDeath(DamageSource source, CallbackInfo ci) {
		MineKz.playerDied((ServerPlayer) (Object) this);
	}
}
