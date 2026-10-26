package kz.minekz;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kz.minekz.engine.ChallengeEngine;
import kz.minekz.engine.Effect;
import kz.minekz.engine.QuestEngine;
import kz.minekz.engine.Word;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Turns inventory changes into engine events. Diffing item totals each tick catches every route
 * (pickup, crafting, smelting, taking from a chest) without a hook per route.
 */
class InventoryWatcher {
	private static final double TICK_SECONDS = 0.05;

	// the first tick after joining is a silent baseline: items held on join restart only the Challenges the world saved
	private Map<String, Integer> last;

	boolean restored() {
		return last != null;
	}

	void tick(ServerPlayer player, PlayerProgress progress, List<Effect> speechEffects) {
		ChallengeEngine engine = progress.engine;
		QuestEngine quests = progress.quests;
		List<Effect> effects = new ArrayList<>(speechEffects);
		boolean survival = !player.isCreative() && !player.isSpectator();
		engine.gameModeChanged(survival);
		quests.gameModeChanged(survival);
		Map<String, Integer> now = counts(player);
		if (last == null) {
			effects.addAll(SavedProgress.of(player.level().getServer()).restore(player.getUUID(), engine, quests));
		} else {
			last.forEach((id, count) -> {
				int removed = count - now.getOrDefault(id, 0);
				if (removed > 0) {
					engine.itemsRemoved(id, removed);
				}
			});
			now.forEach((id, count) -> {
				int gained = count - last.getOrDefault(id, 0);
				if (gained > 0) {
					effects.addAll(engine.itemsGained(id, gained));
					Word word = MineKz.WORDS.get(id);
					// a Learned Word's items, or any item while speech is unavailable, are never Locked
					if (word != null && engine.lockedCount(id) == 0) {
						effects.addAll(quests.itemUnlocked(word));
					}
				}
			});
		}
		last = now;

		Set<Word> held = new HashSet<>();
		now.keySet().forEach(id -> {
			if (MineKz.WORDS.containsKey(id)) {
				held.add(MineKz.WORDS.get(id));
			}
		});
		for (Effect effect : speechEffects) {
			// a won Challenge unlocks its items, unless they were all used up while Locked
			if (effect instanceof Effect.Learned learned && held.contains(learned.word())) {
				effects.addAll(quests.itemUnlocked(learned.word()));
			}
		}
		effects.addAll(engine.timePassed(TICK_SECONDS));
		effects.addAll(quests.timePassed(TICK_SECONDS, held));
		apply(player, progress, effects);
	}

	void apply(ServerPlayer player, PlayerProgress progress, List<Effect> effects) {
		for (Effect effect : effects) {
			if (effect instanceof Effect.Forfeit forfeit) {
				int lost = forfeit(player, forfeit);
				if (lost > 0) {
					Packets.send(player, new Packets.Ending(MineKz.WORDS.get(forfeit.itemId()), false, lost));
				}
			} else if (effect instanceof Effect.Learned learned) {
				Packets.send(player, new Packets.Ending(learned.word(), true, 0));
				player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
				player.sendSystemMessage(Component.translatable("minekz.chat.learned", learned.word().cyrillic(), learned.word().latin(), learned.word().meaning(), learned.word().meaningRu()));
			} else if (effect instanceof Effect.RecallSaid said) {
				player.giveExperiencePoints(said.xp());
				player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
				Packets.send(player, new Packets.Ending(said.word(), true, 0));
			} else if (effect instanceof Effect.RecallMissed missed) {
				Packets.send(player, new Packets.Ending(missed.word(), false, 0));
			} else if (effect instanceof Effect.PlaySample sample) {
				Packets.send(player, new Packets.PlaySample(sample.word()));
			} else if (effect instanceof Effect.ChallengesOff) {
				player.sendSystemMessage(Component.translatable("minekz.chat.challenges_off"));
			} else if (effect instanceof Effect.QuestTimedOut timedOut) {
				Packets.send(player, new Packets.QuestEnding(timedOut.word(), false));
			} else if (effect instanceof Effect.QuestCompleted completed) {
				progress.rewardsDue.add(completed);
				Packets.send(player, new Packets.QuestEnding(completed.word(), true)); // after its Learned ending, so the title can see that moment coming
			}
		}
		if (!effects.isEmpty()) {
			last = counts(player);
		}
	}

	/** Hands over a Quest's Reward, as its title appears. Gains by the next tick's diff like any other, so it starts a Challenge if алмас isn't Learned. */
	static void giveReward(ServerPlayer player, Effect.QuestCompleted reward) {
		player.giveExperiencePoints(reward.xp());
		ItemStack diamonds = new ItemStack(Items.DIAMOND, reward.diamonds());
		player.getInventory().add(diamonds); // shrinks the stack by what fit
		if (!diamonds.isEmpty()) {
			player.spawnAtLocation(player.level(), diamonds); // at the player's feet
		}
	}

	/** Returns how many items it removed. */
	private static int forfeit(ServerPlayer player, Effect.Forfeit forfeit) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(forfeit.itemId()));
		int left = forfeit.count();
		for (ItemStack stack : stacks(player)) {
			if (left > 0 && stack.is(item)) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}
		int lost = forfeit.count() - left;
		if (lost == 0) {
			return 0; // the player already got rid of them
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1f, 1f);
		player.level().playSound(null, player.blockPosition(), SoundEvents.CAT_DEATH, SoundSource.PLAYERS, 1f, 1f);
		player.sendSystemMessage(Component.translatable("minekz.chat.lost", lost, item.getName()));
		return lost;
	}

	/** Inventory slots plus the stack held on the cursor, so picking an item up in a screen isn't a loss and gain. */
	private static List<ItemStack> stacks(ServerPlayer player) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			stacks.add(player.getInventory().getItem(slot));
		}
		stacks.add(player.containerMenu.getCarried());
		return stacks;
	}

	private static Map<String, Integer> counts(ServerPlayer player) {
		Map<String, Integer> counts = new HashMap<>();
		for (ItemStack stack : stacks(player)) {
			if (!stack.isEmpty()) {
				counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}
}
