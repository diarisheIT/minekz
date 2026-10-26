package kz.minekz;

import java.util.Map;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Locked Item rules for the Minecraft glue. Locks are a count per item type, so every stack of a locked type
 * is locked, and they belong to a player: the server asks that player's engine, a client asks the counts the
 * server last sent it (its own player's). The checks run on both sides so the client never mispredicts.
 */
public final class LockedItems {
	private LockedItems() {
	}

	/** This client's own player's locked counts as the server last sent them; empty on a server. */
	public static volatile Map<String, Integer> mine = Map.of();

	/** Whether {@code stack} is locked for {@code holder}; anything but a player (or none) is never locked. */
	public static boolean isLocked(ItemStack stack, @Nullable Entity holder) {
		if (stack.isEmpty() || holder == null) {
			return false;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		if (holder.level().isClientSide()) {
			return mine.getOrDefault(id, 0) > 0;
		}
		return holder instanceof ServerPlayer player && MineKz.lockedCount(player, id) > 0;
	}

	/** Client only, for screens: locked for the local player. */
	public static boolean isLockedHere(ItemStack stack) {
		return !stack.isEmpty() && mine.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), 0) > 0;
	}

	/** Locked Items may only sit in the player's own storage slots: not armor, not a container or crafting grid. */
	public static boolean canHold(Slot slot) {
		// Inventory slots 36-39 are armor and 41-42 body armor and saddle
		return slot.container instanceof Inventory
				&& (slot.getContainerSlot() < Inventory.INVENTORY_SIZE || slot.getContainerSlot() == Inventory.SLOT_OFFHAND);
	}

	static void register() {
		// shortcut: interacting with any entity (item frame, feeding, shearing) is refused outright rather than done bare-handed (so no riding or trading either); make it bare-handed if players hit this
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				isLocked(player.getItemInHand(hand), player) ? InteractionResult.FAIL : InteractionResult.PASS);
		// shortcut: a locked weapon refuses to attack (even a locked block) instead of punching bare-handed; strip its attack modifiers if players hit this
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				isLocked(player.getMainHandItem(), player) ? InteractionResult.FAIL : InteractionResult.PASS);
	}
}
