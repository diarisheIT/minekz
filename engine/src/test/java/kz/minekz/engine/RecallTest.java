package kz.minekz.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Recalls run in the {@link ChallengeEngine}'s single active slot. */
class RecallTest {
	private static final Word STONE = new Word("тас", "tas", "stone", "tas", Tier.COMMON);
	private static final Word LOG = new Word("ағаш", "agash", "wood", "aghash", Tier.RARE);
	private static final Word BEDROCK = new Word("негіз", "negiz", "bedrock", "negiz", null);

	private final ChallengeEngine engine = new ChallengeEngine(words(), new Random(7));

	private static Map<String, Word> words() {
		Map<String, Word> words = new LinkedHashMap<>();
		words.put("minecraft:cobblestone", STONE);
		words.put("minecraft:stone", STONE);
		words.put("minecraft:oak_log", LOG);
		words.put("minecraft:bedrock", BEDROCK);
		return words;
	}

	/** Wins the Word's Challenge and lets its sample finish. */
	private void learn(String itemId, Word word) {
		engine.itemsGained(itemId, 1);
		engine.sampleFinished();
		engine.wordHeard(word.cyrillic());
		engine.sampleFinished();
	}

	@Test
	void aRecallComesOnlyAfterAGapOfFiveToFifteenMinutes() {
		learn("minecraft:cobblestone", STONE);

		engine.timePassed(299.9);
		assertTrue(engine.active().isEmpty());

		engine.timePassed(600.2);
		ActiveChallenge recall = engine.active().orElseThrow();
		assertTrue(recall.recall());
		assertEquals(STONE, recall.word());
		assertEquals("minecraft:cobblestone", recall.itemId()); // the Word's first listed item
		assertEquals(10.0, recall.secondsLeft());
	}

	@Test
	void noRecallWithoutALearnedWordThatHasATier() {
		engine.timePassed(1000);
		assertTrue(engine.active().isEmpty());

		learn("minecraft:bedrock", BEDROCK);
		engine.timePassed(1000);
		assertTrue(engine.active().isEmpty());
	}

	@Test
	void aRecallNeverRepeatsTheLastWordUnlessItIsTheOnlyOne() {
		learn("minecraft:cobblestone", STONE);
		engine.timePassed(900);
		assertEquals(STONE, engine.active().orElseThrow().word());
		missRecall();
		engine.timePassed(900);
		assertEquals(STONE, engine.active().orElseThrow().word()); // the only candidate
		missRecall();

		learn("minecraft:oak_log", LOG);
		learn("minecraft:bedrock", BEDROCK);
		Word previous = STONE;
		for (int i = 0; i < 20; i++) {
			engine.timePassed(900);
			Word word = engine.active().orElseThrow().word();
			assertNotEquals(previous, word);
			assertNotEquals(BEDROCK, word);
			previous = word;
			missRecall();
		}
	}

	private void missRecall() {
		engine.timePassed(10.0);
		engine.sampleFinished();
	}

	@Test
	void aRecallAppearsWithoutItsSampleAndReplayIsIgnored() {
		learn("minecraft:cobblestone", STONE);

		assertEquals(List.of(), engine.timePassed(900));
		assertTrue(engine.micOn()); // listening at once: there is no sample to wait for
		assertEquals(List.of(), engine.replayPressed());
	}

	@Test
	void aRecallNeverStartsOverAListenSampleSoTheSpeakersCantAnswerIt() {
		learn("minecraft:cobblestone", STONE);
		assertEquals(List.of(new Effect.PlaySample(STONE)), engine.samplePressed(STONE));

		engine.timePassed(900);
		assertTrue(engine.active().isEmpty());

		engine.sampleFinished();
		engine.timePassed(0.05);
		assertTrue(engine.active().orElseThrow().recall());
		assertEquals(List.of(), engine.samplePressed(STONE)); // nor plays during one
	}

	@Test
	void theArchivePlaysOnlyALearnedWordsSampleSoItNeverSpoilsAChallenge() {
		assertEquals(List.of(), engine.archiveSamplePressed(STONE));

		learn("minecraft:cobblestone", STONE);
		assertEquals(List.of(new Effect.PlaySample(STONE)), engine.archiveSamplePressed(STONE));
	}

	@Test
	void sayingTheWordPaysItsTiersExperienceAndPlaysItsSampleOnce() {
		learn("minecraft:oak_log", LOG);
		engine.timePassed(900);

		assertEquals(List.of(new Effect.RecallSaid(LOG, Tier.RARE.xp), new Effect.PlaySample(LOG)), engine.wordHeard("ағаш"));
		assertTrue(engine.active().isEmpty());
	}

	@Test
	void runningOutShowsTheWordAndPlaysItsSampleAndTheWordStaysLearned() {
		learn("minecraft:cobblestone", STONE);
		engine.timePassed(900);
		engine.timePassed(9.0);
		engine.wordHeard("тал"); // a wrong try is free

		assertEquals(List.of(new Effect.RecallMissed(STONE), new Effect.PlaySample(STONE)), engine.timePassed(1.0));
		assertTrue(engine.active().isEmpty());
		assertTrue(engine.learnedWords().contains(STONE));
	}

	@Test
	void aChallengeGainedDuringARecallWaitsForItToEnd() {
		learn("minecraft:cobblestone", STONE);
		engine.timePassed(900);

		assertEquals(List.of(), engine.itemsGained("minecraft:oak_log", 2));
		assertTrue(engine.active().orElseThrow().recall());
		assertEquals(2, engine.lockedCount("minecraft:oak_log"));

		engine.wordHeard("тас");
		assertEquals(List.of(new Effect.PlaySample(LOG)), engine.sampleFinished());
		assertEquals(LOG, engine.active().orElseThrow().word());
	}

	@Test
	void theGapDoesNotAdvanceInCreativeOrSpectator() {
		learn("minecraft:cobblestone", STONE);
		engine.gameModeChanged(false);
		engine.timePassed(1000);
		engine.gameModeChanged(true);

		engine.timePassed(299.9);
		assertTrue(engine.active().isEmpty());
		engine.timePassed(600.2);
		assertTrue(engine.active().orElseThrow().recall());
	}

	@Test
	void theGapDoesNotAdvanceWhilePaused() {
		learn("minecraft:cobblestone", STONE);
		engine.setPaused(true);
		engine.timePassed(1000);
		engine.setPaused(false);

		engine.timePassed(299.9);
		assertTrue(engine.active().isEmpty());
	}

	@Test
	void switchingToCreativeMidRecallDropsItWithNoResult() {
		learn("minecraft:cobblestone", STONE);
		engine.timePassed(900);

		engine.gameModeChanged(false);
		assertTrue(engine.active().isEmpty());
		assertEquals(List.of(), engine.wordHeard("тас"));
		assertEquals(List.of(), engine.timePassed(10.0));
	}

	@Test
	void losingSpeechMidRecallDropsItAndNoMoreRecallsCome() {
		learn("minecraft:cobblestone", STONE);
		engine.timePassed(900);

		assertEquals(List.of(new Effect.ChallengesOff()), engine.speechUnavailable());
		assertTrue(engine.active().isEmpty());
		assertEquals(List.of(), engine.wordHeard("тас"));
		engine.timePassed(10_000);
		assertTrue(engine.active().isEmpty());
	}

	@Test
	void aRecallDueDuringAChallengeWaitsForItToEnd() {
		learn("minecraft:cobblestone", STONE);
		engine.itemsGained("minecraft:oak_log", 1); // its sample still plays, so its Window never drains

		engine.timePassed(1000);
		assertEquals(LOG, engine.active().orElseThrow().word());

		engine.sampleFinished();
		engine.wordHeard("ағаш");
		engine.timePassed(1.0);
		assertTrue(engine.active().isEmpty()); // still waits for the success sample

		engine.sampleFinished();
		engine.timePassed(0.05);
		assertTrue(engine.active().orElseThrow().recall());
	}
}
