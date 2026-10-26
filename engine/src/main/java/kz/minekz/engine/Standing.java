package kz.minekz.engine;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * One player's place on the Leaderboard: counts only, never which Words. {@code reachedAt} is when they Learned the last of
 * their Words (epoch millis), or {@link Long#MIN_VALUE} if none of theirs has a time, as those came from older saves.
 */
public record Standing(UUID id, String name, int items, int animals, long reachedAt, boolean online) {
	/** Most Learned Words first; a tie goes to whoever reached that count first. */
	public static final Comparator<Standing> ORDER = Comparator.comparingInt((Standing s) -> s.items + s.animals).reversed()
			.thenComparingLong(Standing::reachedAt).thenComparing(Standing::name);

	public static Standing of(UUID id, String name, Collection<Word> learned, Map<Word, Long> learnedAt, Collection<Word> animalWords, boolean online) {
		int animals = (int) learned.stream().filter(animalWords::contains).count();
		long reachedAt = learned.stream().filter(learnedAt::containsKey).mapToLong(learnedAt::get).max().orElse(Long.MIN_VALUE);
		return new Standing(id, name, learned.size() - animals, animals, reachedAt, online);
	}
}
