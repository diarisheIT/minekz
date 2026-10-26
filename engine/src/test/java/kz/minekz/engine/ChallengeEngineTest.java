package kz.minekz.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChallengeEngineTest {
	private static final Word STONE = new Word("тас", "tas", "stone", "tas", Tier.COMMON);

	private static final Word LOG = new Word("ағаш", "agash", "wood", "aghash", Tier.COMMON);

	private final ChallengeEngine engine = new ChallengeEngine(Map.of("minecraft:cobblestone", STONE, "minecraft:oak_log", LOG));

	@Test
	void gainingAListedItemStartsAChallengeAndLocksIt() {
		start("minecraft:cobblestone", 3);

		ActiveChallenge active = engine.active().orElseThrow();
		assertEquals(STONE, active.word());
		assertEquals(10.0, active.secondsLeft());
		assertEquals(3, engine.lockedCount("minecraft:cobblestone"));
	}

	/** Starts a Challenge and lets its sample finish, so the Window is running. */
	private void start(String itemId, int count) {
		engine.itemsGained(itemId, count);
		engine.sampleFinished();
	}

	@Test
	void aChallengeStartsWithItsSampleAndTheWindowWaitsForTheSampleToFinish() {
		assertEquals(List.of(new Effect.PlaySample(STONE)), engine.itemsGained("minecraft:cobblestone", 3));

		assertTrue(engine.timePassed(20.0).isEmpty());
		assertEquals(10.0, engine.active().orElseThrow().secondsLeft());

		engine.sampleFinished();
		engine.timePassed(4.0);
		assertEquals(6.0, engine.active().orElseThrow().secondsLeft());
	}

	@Test
	void theMicIsOnOnlyWhileAChallengeRunsAndNoSampleIsPlaying() {
		assertFalse(engine.micOn());

		engine.itemsGained("minecraft:cobblestone", 3);
		assertFalse(engine.micOn());

		engine.sampleFinished();
		assertTrue(engine.micOn());
	}

	@Test
	void aReplayPlaysTheSampleWithTheMicOffWhileTheChallengeWindowKeepsDraining() {
		start("minecraft:cobblestone", 3);

		assertEquals(List.of(new Effect.PlaySample(STONE)), engine.replayPressed());
		assertFalse(engine.micOn());
		engine.timePassed(4.0);
		assertEquals(6.0, engine.active().orElseThrow().secondsLeft());

		engine.sampleFinished();
		assertTrue(engine.micOn());
	}

	@Test
	void replayDoesNothingWithoutAChallengeOrWhileASampleIsAlreadyPlaying() {
		assertTrue(engine.replayPressed().isEmpty());

		engine.itemsGained("minecraft:cobblestone", 3);
		assertTrue(engine.replayPressed().isEmpty());

		engine.sampleFinished();
		assertTrue(engine.micOn());
	}

	@Test
	void successPlaysTheSampleOnceMoreAndANewChallengeWaitsForEverySampleToFinish() {
		start("minecraft:cobblestone", 3);
		assertEquals(List.of(new Effect.Learned(STONE), new Effect.PlaySample(STONE)), engine.wordHeard("тас"));

		engine.itemsGained("minecraft:oak_log", 1);
		engine.sampleFinished();
		assertFalse(engine.micOn());
		assertTrue(engine.timePassed(20.0).isEmpty());

		engine.sampleFinished();
		assertTrue(engine.micOn());
		engine.timePassed(4.0);
		assertEquals(6.0, engine.active().orElseThrow().secondsLeft());
	}

	@Test
	void timePassingDrainsTheChallengeWindow() {
		start("minecraft:cobblestone", 3);

		engine.timePassed(4.0);

		assertEquals(6.0, engine.active().orElseThrow().secondsLeft());
	}

	@Test
	void whenTheWindowEndsTheChallengeForfeitsAndReportsWhatToRemove() {
		start("minecraft:cobblestone", 3);

		List<Effect> effects = engine.timePassed(10.0);

		assertEquals(List.of(new Effect.Forfeit("minecraft:cobblestone", 3)), effects);
		assertTrue(engine.active().isEmpty());
		assertEquals(0, engine.lockedCount("minecraft:cobblestone"));
	}

	@Test
	void timeBeforeTheWindowEndsCausesNoEffects() {
		start("minecraft:cobblestone", 3);

		assertTrue(engine.timePassed(9.9).isEmpty());
		assertEquals(3, engine.lockedCount("minecraft:cobblestone"));
	}

	@Test
	void itemsNotOnTheWordListNeverStartAChallenge() {
		engine.itemsGained("minecraft:dirt", 64);

		assertTrue(engine.active().isEmpty());
		assertEquals(0, engine.lockedCount("minecraft:dirt"));
		assertTrue(engine.timePassed(100.0).isEmpty());
	}

	@Test
	void aForfeitedWordStaysUnlearnedSoTheNextGainStartsAnotherChallenge() {
		start("minecraft:cobblestone", 3);
		engine.timePassed(10.0);

		engine.itemsGained("minecraft:cobblestone", 1);

		assertEquals(10.0, engine.active().orElseThrow().secondsLeft());
		assertEquals(1, engine.lockedCount("minecraft:cobblestone"));
	}

	@Test
	void moreOfTheActiveItemJoinsTheRunningChallengeWithoutRestartingTheClock() {
		start("minecraft:cobblestone", 3);
		engine.timePassed(5.0);

		engine.itemsGained("minecraft:cobblestone", 2);

		assertEquals(5.0, engine.active().orElseThrow().secondsLeft());
		assertEquals(5, engine.lockedCount("minecraft:cobblestone"));
		assertEquals(List.of(new Effect.Forfeit("minecraft:cobblestone", 5)), engine.timePassed(5.0));
	}

	@Test
	void anotherListedItemQueuesLockedAndItsClockStartsOnlyWhenItsTurnComes() {
		start("minecraft:cobblestone", 3);

		assertTrue(engine.itemsGained("minecraft:oak_log", 1).isEmpty());
		assertEquals(STONE, engine.active().orElseThrow().word());
		assertEquals(1, engine.lockedCount("minecraft:oak_log"));

		engine.timePassed(5.0);
		assertEquals(List.of(new Effect.Forfeit("minecraft:cobblestone", 3), new Effect.PlaySample(LOG)), engine.timePassed(5.0));
		assertEquals(1, engine.lockedCount("minecraft:oak_log"));
		assertEquals(LOG, engine.active().orElseThrow().word());
		assertEquals(10.0, engine.active().orElseThrow().secondsLeft());
	}

	@Test
	void queuedChallengesRunOneAtATimeInOrderAndMorePickupsJoinTheQueuedOne() {
		Word sand = new Word("құм", "qum", "sand", "qum", Tier.COMMON);
		var engine = new ChallengeEngine(Map.of("minecraft:cobblestone", STONE, "minecraft:oak_log", LOG, "minecraft:sand", sand));
		engine.itemsGained("minecraft:cobblestone", 1);
		engine.sampleFinished();
		engine.itemsGained("minecraft:oak_log", 1);
		engine.itemsGained("minecraft:sand", 4);
		engine.itemsGained("minecraft:oak_log", 2);

		engine.wordHeard("тас");
		assertTrue(engine.active().isEmpty()); // the next Challenge waits for the success sample
		assertEquals(List.of(new Effect.PlaySample(LOG)), engine.sampleFinished());
		engine.sampleFinished();
		assertEquals(LOG, engine.active().orElseThrow().word());
		assertEquals(4, engine.lockedCount("minecraft:sand"));

		assertEquals(List.of(new Effect.Forfeit("minecraft:oak_log", 3), new Effect.PlaySample(sand)), engine.timePassed(10.0));
		assertEquals(sand, engine.active().orElseThrow().word());
	}

	@Test
	void sayingTheActiveWordLearnsItAndUnlocksTheItems() {
		start("minecraft:cobblestone", 3);

		assertEquals(List.of(new Effect.Learned(STONE), new Effect.PlaySample(STONE)), engine.wordHeard("тас"));

		assertTrue(engine.active().isEmpty());
		assertEquals(0, engine.lockedCount("minecraft:cobblestone"));
		assertTrue(engine.timePassed(10.0).isEmpty());
	}

	@Test
	void sayingAnotherWordOrNoiseIsWrongAndKeepsTheChallengeRunning() {
		start("minecraft:cobblestone", 3);

		assertTrue(engine.wordHeard("ағаш").isEmpty());
		assertTrue(engine.wordHeard("[unk]").isEmpty());

		assertEquals("[unk]", engine.lastHeard());
		assertEquals(3, engine.lockedCount("minecraft:cobblestone"));
		assertEquals(List.of(new Effect.Forfeit("minecraft:cobblestone", 3)), engine.timePassed(10.0));
	}

	@Test
	void speechUnavailableSwitchesChallengesOffOnceAndNeverForfeits() {
		start("minecraft:cobblestone", 3);
		engine.itemsGained("minecraft:oak_log", 1);

		assertEquals(List.of(new Effect.ChallengesOff()), engine.speechUnavailable());
		assertEquals(0, engine.lockedCount("minecraft:oak_log"));
		assertTrue(engine.speechUnavailable().isEmpty());

		assertTrue(engine.active().isEmpty());
		assertTrue(engine.timePassed(100.0).isEmpty());
		engine.itemsGained("minecraft:oak_log", 1);
		assertTrue(engine.active().isEmpty());
	}

	@Test
	void grammarListsEveryWordThenDecoysThatAreNotWordsThenUnkWithCyrillicUnescaped() {
		assertEquals("[\"тас\", \"ағаш\", \"бас\", \"[unk]\"]", Grammar.json(List.of(STONE, LOG), List.of("бас", "тас")));
	}

	@Test
	void itemsSharingAWordShareOneChallengeAndAreUnlockedOrForfeitedTogether() {
		Word pickaxe = new Word("кетпен", "ketpen", "pickaxe", "ketpen", Tier.COMMON);
		var engine = new ChallengeEngine(Map.of("minecraft:diamond_pickaxe", pickaxe, "minecraft:golden_pickaxe", pickaxe));
		engine.itemsGained("minecraft:diamond_pickaxe", 1);
		engine.sampleFinished();
		engine.itemsGained("minecraft:golden_pickaxe", 1);

		assertEquals("minecraft:diamond_pickaxe", engine.active().orElseThrow().itemId());
		assertEquals(1, engine.lockedCount("minecraft:golden_pickaxe"));
		assertEquals(2, engine.timePassed(10.0).size());

		engine.itemsGained("minecraft:golden_pickaxe", 1);
		assertEquals(List.of(new Effect.Learned(pickaxe), new Effect.PlaySample(pickaxe)), engine.wordHeard("кетпен"));
		assertEquals(0, engine.lockedCount("minecraft:golden_pickaxe"));
	}

	@Test
	void aLearnedWordNeverStartsAnotherChallenge() {
		start("minecraft:cobblestone", 3);
		engine.wordHeard("тас");

		engine.itemsGained("minecraft:cobblestone", 5);

		assertTrue(engine.active().isEmpty());
		assertEquals(0, engine.lockedCount("minecraft:cobblestone"));
	}

	@Test
	void pauseStopsTheTimerAndTheMicUntilResumed() {
		start("minecraft:cobblestone", 3);

		engine.setPaused(true);
		assertTrue(engine.timePassed(20.0).isEmpty());
		assertEquals(10.0, engine.active().orElseThrow().secondsLeft());
		assertFalse(engine.micOn());

		engine.setPaused(false);
		assertTrue(engine.micOn());
		engine.timePassed(4.0);
		assertEquals(6.0, engine.active().orElseThrow().secondsLeft());
	}

	@Test
	void dyingForfeitsTheActiveChallengeAtOnceAndUnlocksQueuedOnesSoTheyDrop() {
		start("minecraft:cobblestone", 3);
		engine.itemsGained("minecraft:oak_log", 2);

		assertEquals(List.of(new Effect.Forfeit("minecraft:cobblestone", 3)), engine.died());
		assertTrue(engine.active().isEmpty());
		assertEquals(0, engine.lockedCount("minecraft:oak_log"));

		engine.sampleFinished();
		assertTrue(engine.active().isEmpty()); // the queue is gone; picking the logs up again queues them anew
	}

	@Test
	void creativeOrSpectatorStartsNoChallenges() {
		engine.gameModeChanged(false);

		assertTrue(engine.itemsGained("minecraft:cobblestone", 3).isEmpty());
		assertTrue(engine.active().isEmpty());
		assertEquals(0, engine.lockedCount("minecraft:cobblestone"));
	}

	@Test
	void switchingToCreativeMidChallengeDropsItWithoutForfeitingAndSurvivalWorksAgain() {
		start("minecraft:cobblestone", 3);
		engine.itemsGained("minecraft:oak_log", 2);

		engine.gameModeChanged(false);
		assertTrue(engine.active().isEmpty());
		assertEquals(0, engine.lockedCount("minecraft:cobblestone"));
		assertTrue(engine.timePassed(30.0).isEmpty());

		engine.gameModeChanged(true);
		start("minecraft:oak_log", 1);
		assertEquals(LOG, engine.active().orElseThrow().word());
	}

	@Test
	void lockedItemsUsedUpElsewhereReduceTheForfeit() {
		start("minecraft:cobblestone", 3);

		engine.itemsRemoved("minecraft:cobblestone", 2);
		assertEquals(1, engine.lockedCount("minecraft:cobblestone"));

		assertEquals(List.of(new Effect.Forfeit("minecraft:cobblestone", 1)), engine.timePassed(10.0));
	}

	@Test
	void whenEveryLockedItemIsUsedUpTheForfeitRemovesNothing() {
		start("minecraft:cobblestone", 3);

		engine.itemsRemoved("minecraft:cobblestone", 5);

		assertTrue(engine.timePassed(10.0).isEmpty());
	}

	@Test
	void rejoiningRestoresLearnedWordsSoTheyNeverTriggerAgain() {
		ChallengeEngine rejoined = new ChallengeEngine(Map.of("minecraft:cobblestone", STONE, "minecraft:oak_log", LOG));

		assertTrue(rejoined.rejoined(Set.of(STONE), Map.of()).isEmpty());

		assertTrue(rejoined.itemsGained("minecraft:cobblestone", 3).isEmpty());
		assertEquals(Set.of(STONE), rejoined.learnedWords());
	}

	@Test
	void rejoiningRestartsEveryLockedChallengeInFullAndNeverForfeits() {
		start("minecraft:cobblestone", 3);
		engine.timePassed(5.0);
		engine.itemsGained("minecraft:oak_log", 2);
		Map<String, Integer> saved = engine.lockedCounts();
		assertEquals(Map.of("minecraft:cobblestone", 3, "minecraft:oak_log", 2), saved);
		assertEquals(List.of("minecraft:cobblestone", "minecraft:oak_log"), List.copyOf(saved.keySet())); // active first

		ChallengeEngine rejoined = new ChallengeEngine(Map.of("minecraft:cobblestone", STONE, "minecraft:oak_log", LOG));
		List<Effect> effects = rejoined.rejoined(Set.of(), saved);

		assertEquals(1, effects.size());
		assertTrue(effects.getFirst() instanceof Effect.PlaySample);
		assertEquals(10.0, rejoined.active().orElseThrow().secondsLeft());
		assertEquals(3, rejoined.lockedCount("minecraft:cobblestone"));
		assertEquals(2, rejoined.lockedCount("minecraft:oak_log"));

		rejoined.sampleFinished();
		List<Effect> forfeit = rejoined.timePassed(10.0);
		assertEquals(1, forfeit.stream().filter(Effect.Forfeit.class::isInstance).count()); // only the first, after its full Window
		assertEquals(2, rejoined.lockedCount("minecraft:oak_log"));
	}

	@Test
	void rejoiningIgnoresItemsNoLongerOnTheWordList() {
		assertTrue(engine.rejoined(Set.of(), Map.of("minecraft:dirt", 4)).isEmpty());
		assertEquals(0, engine.lockedCount("minecraft:dirt"));
	}

	@Test
	void learningAWordRecordsTheTimeItWasLearned() {
		long before = System.currentTimeMillis();
		start("minecraft:cobblestone", 3);
		engine.wordHeard("тас");

		long at = engine.learnedAt().get(STONE);
		assertTrue(at >= before && at <= System.currentTimeMillis());
	}

	@Test
	void rejoiningRestoresLearnedTimesAndWordsWithoutOneHaveNone() {
		ChallengeEngine rejoined = new ChallengeEngine(Map.of("minecraft:cobblestone", STONE, "minecraft:oak_log", LOG));

		rejoined.rejoined(List.of(STONE, LOG), Map.of(STONE, 1234L), Map.of());

		assertEquals(Set.of(STONE, LOG), rejoined.learnedWords());
		assertEquals(Map.of(STONE, 1234L), rejoined.learnedAt());
	}
}
