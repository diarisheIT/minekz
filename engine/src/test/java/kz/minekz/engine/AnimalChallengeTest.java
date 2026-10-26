package kz.minekz.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AnimalChallengeTest {
	private static final Word STONE = new Word("тас", "tas", "stone", "tas", Tier.COMMON);
	private static final Word COW = new Word("сиыр", "siır", "cow", "siir", null);

	private final ChallengeEngine engine = new ChallengeEngine(Map.of("minecraft:cobblestone", STONE), Map.of("minecraft:cow", COW), new Random(1));

	@Test
	void hurtingAnAnimalWithItsWordUnlearnedIsCancelledAndStartsItsChallenge() {
		assertTrue(engine.animalProtected("minecraft:cow"));
		assertEquals(List.of(new Effect.PlaySample(COW)), engine.animalHurt("minecraft:cow"));

		ActiveChallenge active = engine.active().orElseThrow();
		assertEquals(COW, active.word());
		assertEquals("minecraft:cow", active.itemId());
		assertTrue(engine.animalProtected("minecraft:cow"));
	}

	@Test
	void theChallengeKnowsWhetherAnAnimalStartedIt() {
		Word meat = new Word("ет", "et", "meat", "et", Tier.COMMON);
		Word chicken = new Word("тауық", "tawıq", "chicken", "tawiq", null);
		ChallengeEngine bird = new ChallengeEngine(Map.of("minecraft:chicken", meat), Map.of("minecraft:chicken", chicken), new Random(1));
		ChallengeEngine raw = new ChallengeEngine(Map.of("minecraft:chicken", meat), Map.of("minecraft:chicken", chicken), new Random(1));

		bird.animalHurt("minecraft:chicken");
		raw.itemsGained("minecraft:chicken", 1);

		assertTrue(bird.active().orElseThrow().animal());
		assertFalse(raw.active().orElseThrow().animal()); // the same id as an item: raw chicken, not the bird
	}

	@Test
	void animalsWithoutAWordAreNeverProtected() {
		assertFalse(engine.animalProtected("minecraft:zombie"));
	}

	@Test
	void aHitDuringAnotherChallengeQueuesTheAnimalsChallengeAndTheAnimalStaysProtected() {
		engine.itemsGained("minecraft:cobblestone", 1);
		engine.sampleFinished();

		assertTrue(engine.animalHurt("minecraft:cow").isEmpty());
		assertTrue(engine.animalHurt("minecraft:cow").isEmpty());
		assertTrue(engine.animalProtected("minecraft:cow"));

		engine.wordHeard("тас");
		assertEquals(List.of(new Effect.PlaySample(COW)), engine.sampleFinished());
		assertEquals(COW, engine.active().orElseThrow().word());
	}

	@Test
	void sayingTheWordLearnsIt() {
		engine.animalHurt("minecraft:cow");
		engine.sampleFinished();

		assertEquals(List.of(new Effect.Learned(COW), new Effect.PlaySample(COW)), engine.wordHeard("сиыр"));
		assertFalse(engine.animalProtected("minecraft:cow"));
		assertTrue(engine.learnedWords().contains(COW));
	}

	@Test
	void aLearnedAnimalWordStaysLearnedAfterRejoining() {
		engine.rejoined(List.of(COW), Map.of());

		assertFalse(engine.animalProtected("minecraft:cow"));
		assertTrue(engine.animalHurt("minecraft:cow").isEmpty());
	}

	@Test
	void aTimeoutLosesNothingAndTheNextHitStartsANewChallenge() {
		engine.animalHurt("minecraft:cow");
		engine.sampleFinished();

		assertTrue(engine.timePassed(10.0).isEmpty());
		assertTrue(engine.active().isEmpty());
		assertTrue(engine.animalProtected("minecraft:cow"));
		assertTrue(engine.lockedCounts().isEmpty());

		assertEquals(List.of(new Effect.PlaySample(COW)), engine.animalHurt("minecraft:cow"));
	}

	@Test
	void creativeAndSpeechOffLeaveAnimalsUnprotected() {
		engine.gameModeChanged(false);
		assertFalse(engine.animalProtected("minecraft:cow"));

		engine.gameModeChanged(true);
		engine.speechUnavailable();
		assertFalse(engine.animalProtected("minecraft:cow"));
	}

	@Test
	void aRecallNeverPicksAnAnimalWord() {
		engine.rejoined(List.of(COW), Map.of());

		engine.timePassed(ChallengeEngine.RECALL_MAX_GAP_SECONDS);
		assertTrue(engine.active().isEmpty());
	}
}
