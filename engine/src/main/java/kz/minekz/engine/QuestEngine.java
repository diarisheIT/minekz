package kz.minekz.engine;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/** Owns every Quest rule. Driven by events; reports state. No Minecraft types. */
public class QuestEngine {
	public static final double GAP_SECONDS = 5.0; // between one Quest ending and the next appearing

	private final List<Word> words; // in a fixed order, so a seeded Random picks the same Words
	private final Random random;
	private volatile Quest current; // volatile: the HUD reads it from the render thread
	private double gapLeft = GAP_SECONDS;
	private volatile boolean paused; // volatile: set from the client thread while the server is paused
	private final Set<Word> completed = new HashSet<>(); // never asked again, so Rewards can't be farmed
	private boolean gameModeAllowsQuests = true; // false in Creative and Spectator

	public QuestEngine(Collection<Word> words, Random random) {
		this.words = List.copyOf(words);
		this.random = random;
	}

	/** {@code held} is every Word the player has an item of, so a Quest never asks for one already in hand. */
	public List<Effect> timePassed(double seconds, Set<Word> held) {
		if (paused || !gameModeAllowsQuests) {
			return List.of();
		}
		if (current != null) { // keeps running during a Challenge
			double left = current.secondsLeft() - seconds;
			if (left > 0) {
				current = new Quest(current.word(), current.byEar(), left);
				return List.of();
			}
			Word word = current.word();
			current = null;
			gapLeft = GAP_SECONDS;
			return List.of(new Effect.QuestTimedOut(word));
		}
		if ((gapLeft -= seconds) <= 0) {
			Word word = choose(held);
			if (word != null) { // otherwise Quests stop until the player lets go of something
				current = new Quest(word, random.nextBoolean(), word.tier().timeLimitSeconds);
			}
		}
		return List.of();
	}

	/** Pausing stops the Time Limit and the gap. */
	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	/** Rolls a Tier by weight among those with a Word left, then picks one of its Words evenly. */
	private Word choose(Set<Word> held) {
		Map<Tier, List<Word>> byTier = words.stream()
				.filter(w -> w.tier() != null && !held.contains(w) && !completed.contains(w))
				.collect(Collectors.groupingBy(Word::tier, () -> new EnumMap<>(Tier.class), Collectors.toList()));
		if (byTier.isEmpty()) {
			return null;
		}
		int roll = random.nextInt(byTier.keySet().stream().mapToInt(t -> t.weight).sum());
		for (var tier : byTier.entrySet()) {
			if ((roll -= tier.getKey().weight) < 0) {
				return tier.getValue().get(random.nextInt(tier.getValue().size()));
			}
		}
		throw new AssertionError("the roll is below the total weight");
	}

	/** An item of the Word entered the inventory and isn't Locked: a gain of a Learned Word, or a Challenge won. */
	public List<Effect> itemUnlocked(Word word) {
		if (current == null || !current.word().equals(word)) {
			return List.of();
		}
		current = null;
		gapLeft = GAP_SECONDS;
		completed.add(word);
		return List.of(new Effect.QuestCompleted(word, word.tier().xp, word.tier().diamonds));
	}

	/** Restores a world's saved Quest (or none: a new one follows the gap) and completed Words on joining, so rejoining can't reroll. */
	public void rejoined(Quest saved, Collection<Word> completedWords) {
		current = saved;
		completed.addAll(completedWords);
	}

	/** Creative and Spectator have no Quests; switching into them drops the current one without a timeout. */
	public void gameModeChanged(boolean allowsQuests) {
		gameModeAllowsQuests = allowsQuests;
		if (!allowsQuests && current != null) {
			current = null;
			gapLeft = GAP_SECONDS;
		}
	}

	public Optional<Quest> current() {
		return Optional.ofNullable(current);
	}

	public Set<Word> completedWords() {
		return Set.copyOf(completed);
	}
}
