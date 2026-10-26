package kz.minekz.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Owns every Challenge and Recall rule; a Recall takes the single active slot, so the two never overlap. Driven by events; reports state. No Minecraft or audio types. */
public class ChallengeEngine {
	public static final double WINDOW_SECONDS = 10.0;
	public static final double RECALL_MIN_GAP_SECONDS = 5 * 60, RECALL_MAX_GAP_SECONDS = 15 * 60;

	private final Map<String, Word> wordList; // item id -> Word; several items may share one Word
	private final Map<String, Word> animals; // entity type id -> Word; kept apart, as some ids are both (minecraft:chicken)
	private final Map<String, Integer> locked = new ConcurrentHashMap<>(); // items of the active and queued Words; concurrent: read from the render thread
	private final LinkedHashMap<Word, String> queued = new LinkedHashMap<>(); // waiting Word -> item or animal type that queued it, in arrival order
	private volatile ActiveChallenge active; // volatile: the HUD reads it from the render thread
	private volatile String lastHeard = "";
	private boolean challengesOff;
	private boolean gameModeAllowsChallenges = true; // false in Creative and Spectator
	private volatile boolean paused; // volatile: set from the client thread while the server is paused
	private final Set<Word> learned = new HashSet<>();
	private final Map<Word, Long> learnedAt = new ConcurrentHashMap<>(); // epoch millis; concurrent: read from the render thread; Words learned before times were saved have none
	private volatile int samplesPlaying; // volatile: the speech thread reads it through micOn()
	private boolean windowOpen; // the Challenge Window opens only once the Challenge's first sample has played
	private final Random random;
	private double recallDueIn;
	private Word lastRecalled;

	public ChallengeEngine(Map<String, Word> wordList) {
		this(wordList, new Random());
	}

	/** {@code wordList} in a fixed order, so a seeded Random picks the same Recalls. */
	public ChallengeEngine(Map<String, Word> wordList, Random random) {
		this(wordList, Map.of(), random);
	}

	/** {@code animals} have no Tier, so they are never a Recall. */
	public ChallengeEngine(Map<String, Word> wordList, Map<String, Word> animals, Random random) {
		this.wordList = wordList;
		this.animals = animals;
		this.random = random;
		recallDueIn = drawRecallGap();
	}

	private double drawRecallGap() {
		return RECALL_MIN_GAP_SECONDS + random.nextDouble() * (RECALL_MAX_GAP_SECONDS - RECALL_MIN_GAP_SECONDS);
	}

	/** Once due, starts a Recall when no Challenge is active or queued and no sample plays. It shows no Word and plays no Sample. */
	private void startRecallIfDue() {
		if (recallDueIn > 0 || active != null || !queued.isEmpty() || samplesPlaying > 0 || challengesOff || !gameModeAllowsChallenges) {
			return;
		}
		List<Word> candidates = wordList.values().stream().distinct().filter(w -> w.tier() != null && learned.contains(w)).toList();
		if (candidates.size() > 1) {
			candidates = candidates.stream().filter(w -> !w.equals(lastRecalled)).toList();
		}
		if (candidates.isEmpty()) {
			return; // stays due until a Word with a Tier is learned
		}
		Word word = candidates.get(random.nextInt(candidates.size()));
		String firstItem = wordList.entrySet().stream().filter(e -> e.getValue().equals(word)).findFirst().orElseThrow().getKey();
		lastRecalled = word;
		recallDueIn = drawRecallGap();
		active = new ActiveChallenge(word, firstItem, WINDOW_SECONDS, true, false);
		lastHeard = "";
		windowOpen = true;
	}

	public List<Effect> itemsGained(String itemId, int count) {
		Word word = wordList.get(itemId);
		if (!challengeable(word)) {
			return List.of();
		}
		enqueue(word, itemId);
		locked.merge(itemId, count, Integer::sum);
		return startNext();
	}

	private void enqueue(Word word, String source) {
		if (active == null || !active.word().equals(word)) {
			queued.putIfAbsent(word, source); // a Word already waiting keeps its place
		}
	}

	private boolean challengeable(Word word) {
		return word != null && !challengesOff && gameModeAllowsChallenges && !learned.contains(word);
	}

	/** Whether the player's hit on an animal of this type must be cancelled: its Word is unlearned and Challenges are on. */
	public boolean animalProtected(String animalType) {
		return challengeable(animals.get(animalType));
	}

	/** A cancelled hit starts the animal's Challenge, or queues it. Nothing is Locked, so its timeout Forfeits nothing. */
	public List<Effect> animalHurt(String animalType) {
		if (!animalProtected(animalType)) {
			return List.of();
		}
		enqueue(animals.get(animalType), animalType);
		return startNext();
	}

	/** Starts the first queued Challenge once none runs and no sample plays, so its Sample never overlaps another. */
	private List<Effect> startNext() {
		if (active != null || samplesPlaying > 0 || queued.isEmpty()) {
			return List.of();
		}
		Map.Entry<Word, String> next = queued.pollFirstEntry();
		Effect sample = playSample(next.getKey()); // before active is set, so the speech thread never sees the mic open
		boolean animal = next.getKey().equals(animals.get(next.getValue())); // by id and Word: minecraft:chicken is raw chicken too
		active = new ActiveChallenge(next.getKey(), next.getValue(), WINDOW_SECONDS, false, animal);
		lastHeard = "";
		windowOpen = false;
		return List.of(sample);
	}

	/** Locked Items used up some other way are no longer locked, so a Forfeit removes only what is left. */
	public void itemsRemoved(String itemId, int count) {
		locked.computeIfPresent(itemId, (id, held) -> held > count ? held - count : null);
	}

	/**
	 * Restores a world's saved progress on joining. Every locked item queues again, so each Challenge restarts in full
	 * (sample plus a whole Window) and nothing Forfeits.
	 */
	public List<Effect> rejoined(Collection<Word> learnedWords, Map<String, Integer> lockedCounts) {
		return rejoined(learnedWords, Map.of(), lockedCounts);
	}

	public List<Effect> rejoined(Collection<Word> learnedWords, Map<Word, Long> learnedTimes, Map<String, Integer> lockedCounts) {
		learned.addAll(learnedWords);
		learnedAt.putAll(learnedTimes);
		List<Effect> effects = new ArrayList<>();
		lockedCounts.forEach((itemId, count) -> effects.addAll(itemsGained(itemId, count)));
		return effects;
	}

	public Set<Word> learnedWords() {
		return Set.copyOf(learned);
	}

	/** When each Learned Word was learned, in epoch millis; some Learned Words have no time. */
	public Map<Word, Long> learnedAt() {
		return Map.copyOf(learnedAt);
	}

	/** In Challenge order, the active one first, so a rejoin restarts them in the same order. */
	public Map<String, Integer> lockedCounts() {
		List<Word> order = new ArrayList<>(queued.keySet());
		if (active != null) {
			order.addFirst(active.word());
		}
		Map<String, Integer> counts = new LinkedHashMap<>();
		order.forEach(word -> locked.forEach((itemId, count) -> {
			if (wordList.get(itemId).equals(word)) {
				counts.put(itemId, count);
			}
		}));
		return counts;
	}

	/** Unlocks every item of the Word and returns the counts it held. */
	private Map<String, Integer> release(Word word) {
		Map<String, Integer> released = new LinkedHashMap<>();
		locked.forEach((itemId, count) -> {
			if (wordList.get(itemId).equals(word)) {
				released.put(itemId, count);
			}
		});
		released.keySet().forEach(locked::remove);
		return released;
	}

	/** Hear the sample again; the Challenge Window keeps draining. Ignored while a sample plays, so a replay never overlaps a clip, and during a Recall. */
	public List<Effect> replayPressed() {
		if (active == null || active.recall() || samplesPlaying > 0) {
			return List.of();
		}
		return List.of(playSample(active.word()));
	}

	/**
	 * A Quest's Listen key: ignored during a Challenge, a Recall or a sample (so the Archive's button can't give a Recall
	 * away either), and counted like any sample, so nothing starts over it.
	 */
	public List<Effect> samplePressed(Word word) {
		if (active != null || samplesPlaying > 0) {
			return List.of();
		}
		return List.of(playSample(word));
	}

	/** An Archive card's Sample button: like {@link #samplePressed}, but only for a Learned Word, so it never gives a Challenge away. */
	public List<Effect> archiveSamplePressed(Word word) {
		return learned.contains(word) ? samplePressed(word) : List.of();
	}

	/** Every {@link Effect.PlaySample} must be answered with exactly one call, even if the clip could not play. */
	public List<Effect> sampleFinished() {
		samplesPlaying = Math.max(0, samplesPlaying - 1); // clamped: a clip from a closed world may finish in the next one
		if (samplesPlaying == 0) {
			windowOpen = true;
		}
		return startNext();
	}

	private Effect playSample(Word word) {
		samplesPlaying++;
		return new Effect.PlaySample(word);
	}

	public List<Effect> timePassed(double seconds) {
		if (paused) {
			return List.of();
		}
		if (!challengesOff && gameModeAllowsChallenges) {
			recallDueIn -= seconds; // the gap counts play time only
		}
		if (active == null) {
			startRecallIfDue();
			return List.of();
		}
		if (!windowOpen) {
			return List.of();
		}
		double left = active.secondsLeft() - seconds;
		if (left > 0) {
			active = new ActiveChallenge(active.word(), active.itemId(), left, active.recall(), active.animal());
			return List.of();
		}
		Word word = active.word();
		boolean recall = active.recall();
		active = null;
		List<Effect> effects = new ArrayList<>();
		if (recall) {
			return List.of(new Effect.RecallMissed(word), playSample(word)); // a queued Challenge waits for this sample
		}
		forfeit(word, effects);
		effects.addAll(startNext());
		return effects;
	}

	private void forfeit(Word word, List<Effect> effects) {
		release(word).forEach((itemId, count) -> effects.add(new Effect.Forfeit(itemId, count)));
	}

	/** Dying Forfeits the active Challenge at once; queued Words are unlocked without a Forfeit, so their items drop. */
	public List<Effect> died() {
		List<Effect> effects = new ArrayList<>();
		if (active != null) {
			forfeit(active.word(), effects);
			active = null;
		}
		queued.keySet().forEach(this::release);
		queued.clear();
		return effects;
	}

	/** Creative and Spectator start no Challenges; switching into them drops the active and queued ones without a Forfeit. */
	public void gameModeChanged(boolean allowsChallenges) {
		gameModeAllowsChallenges = allowsChallenges;
		if (!allowsChallenges) {
			active = null;
			queued.clear();
			locked.clear();
		}
	}

	/** Pausing stops the timer and the mic. */
	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	public List<Effect> wordHeard(String text) {
		lastHeard = text;
		if (active == null || !text.equals(active.word().cyrillic())) {
			return List.of();
		}
		Word word = active.word();
		boolean recall = active.recall();
		active = null;
		if (recall) {
			return List.of(new Effect.RecallSaid(word, word.tier().xp), playSample(word));
		}
		release(word);
		learned.add(word);
		learnedAt.put(word, System.currentTimeMillis());
		return List.of(new Effect.Learned(word), playSample(word)); // the next queued Challenge waits for this sample
	}

	/** No working mic or model: drop the active and queued Challenges without forfeiting and stop starting new ones. */
	public List<Effect> speechUnavailable() {
		if (challengesOff) {
			return List.of();
		}
		challengesOff = true;
		active = null;
		queued.clear();
		locked.clear();
		return List.of(new Effect.ChallengesOff());
	}

	/** The mic listens only during a Challenge or Recall and never while a sample plays, so the speakers can't answer. */
	public boolean micOn() {
		return active != null && samplesPlaying == 0 && !paused;
	}

	public String lastHeard() {
		return lastHeard;
	}

	public Optional<ActiveChallenge> active() {
		return Optional.ofNullable(active);
	}

	public int lockedCount(String itemId) {
		return locked.getOrDefault(itemId, 0);
	}
}
