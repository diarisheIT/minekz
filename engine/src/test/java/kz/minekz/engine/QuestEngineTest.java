package kz.minekz.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestEngineTest {
	private static final Word STONE = new Word("тас", "tas", "stone", "tas", Tier.COMMON);
	private static final Word LOG = new Word("ағаш", "agash", "wood", "aghash", Tier.COMMON);
	private static final Word LIGHT = new Word("жарық", "jaryq", "light", "zharyq", null); // Creative-only
	private static final Word BOAT = new Word("қайық", "qaiyq", "boat", "qaiyq", Tier.UNCOMMON);
	private static final Word EMERALD = new Word("зүбәржат", "zübärjat", "emerald", "zwbarzhat", Tier.RARE);
	private static final Word DIAMOND = new Word("алмас", "almas", "diamond", "almas", Tier.LEGENDARY);

	@Test
	void aQuestAppearsAfterTheGapForAWordThePlayerDoesNotHold() {
		QuestEngine quests = new QuestEngine(List.of(STONE, LOG), new Random(1));

		quests.timePassed(QuestEngine.GAP_SECONDS - 1, Set.of(STONE));
		assertTrue(quests.current().isEmpty());

		quests.timePassed(1, Set.of(STONE));
		Quest quest = quests.current().orElseThrow();
		assertEquals(LOG, quest.word());
		assertEquals(120.0, quest.secondsLeft());
	}

	@Test
	void anUnlockedGainOfTheQuestsWordCompletesItWithTheTiersReward() {
		QuestEngine quests = questFor(DIAMOND);

		assertEquals(List.of(), quests.itemUnlocked(STONE));
		assertEquals(DIAMOND, quests.current().orElseThrow().word());

		assertEquals(List.of(new Effect.QuestCompleted(DIAMOND, 100, 2)), quests.itemUnlocked(DIAMOND));
		assertTrue(quests.current().isEmpty());
	}

	@Test
	void eachTierRewardsItsOwnXp() {
		assertEquals(List.of(new Effect.QuestCompleted(STONE, 5, 0)), questFor(STONE).itemUnlocked(STONE));
		assertEquals(List.of(new Effect.QuestCompleted(BOAT, 15, 0)), questFor(BOAT).itemUnlocked(BOAT));
		assertEquals(List.of(new Effect.QuestCompleted(EMERALD, 40, 0)), questFor(EMERALD).itemUnlocked(EMERALD));
	}

	@Test
	void aCompletedWordIsNeverChosenAgainEvenWhenNotHeldAndWordsWithNoTierNeverAre() {
		QuestEngine quests = new QuestEngine(List.of(STONE, LIGHT), new Random(1));
		quests.timePassed(QuestEngine.GAP_SECONDS, Set.of());
		quests.itemUnlocked(STONE);

		for (int i = 0; i < 100; i++) {
			quests.timePassed(QuestEngine.GAP_SECONDS, Set.of());
		}
		assertTrue(quests.current().isEmpty());
	}

	@Test
	void commonWordsComeUpFarMoreOftenThanLegendaryOnes() {
		int legendary = 0;
		for (int seed = 0; seed < 2000; seed++) {
			if (firstQuest(seed, Set.of()).equals(DIAMOND)) {
				legendary++;
			}
		}
		// common 60 against legendary 3: about 95 in 2000
		assertTrue(legendary > 50 && legendary < 150, "legendary " + legendary);
	}

	@Test
	void aTierWithNothingLeftIsLeftOutOfTheRoll() {
		for (int seed = 0; seed < 50; seed++) {
			assertEquals(DIAMOND, firstQuest(seed, Set.of(STONE)));
		}
	}

	@Test
	void eachTierGetsItsTimeLimit() {
		assertEquals(240.0, questFor(BOAT).current().orElseThrow().secondsLeft());
		assertEquals(480.0, questFor(EMERALD).current().orElseThrow().secondsLeft());
		assertEquals(900.0, questFor(DIAMOND).current().orElseThrow().secondsLeft());
	}

	@Test
	void runningOutOfTimeReportsATimeoutAndTheNextQuestFollowsAfterTheGap() {
		QuestEngine quests = questFor(STONE);

		assertEquals(List.of(), quests.timePassed(119, Set.of()));
		assertEquals(1.0, quests.current().orElseThrow().secondsLeft());

		assertEquals(List.of(new Effect.QuestTimedOut(STONE)), quests.timePassed(1, Set.of()));
		assertTrue(quests.current().isEmpty());

		quests.timePassed(QuestEngine.GAP_SECONDS - 1, Set.of());
		assertTrue(quests.current().isEmpty());
		quests.timePassed(1, Set.of());
		assertEquals(STONE, quests.current().orElseThrow().word()); // a timed-out Word can come back
	}

	@Test
	void pausingStopsTheTimeLimitAndTheGap() {
		QuestEngine quests = questFor(STONE);
		quests.setPaused(true);
		quests.timePassed(200, Set.of());
		assertEquals(120.0, quests.current().orElseThrow().secondsLeft());

		quests.setPaused(false);
		quests.timePassed(120, Set.of());
		quests.setPaused(true);
		quests.timePassed(QuestEngine.GAP_SECONDS, Set.of());
		assertTrue(quests.current().isEmpty());
	}

	@Test
	void aboutHalfOfQuestsAreByEar() {
		int byEar = 0;
		for (int seed = 0; seed < 2000; seed++) {
			if (questFor(STONE, seed).current().orElseThrow().byEar()) {
				byEar++;
			}
		}
		assertTrue(byEar > 900 && byEar < 1100, "by ear " + byEar);
	}

	@Test
	void theModeLastsThroughTheQuest() {
		QuestEngine quests = questFor(STONE);
		boolean byEar = quests.current().orElseThrow().byEar();
		quests.timePassed(60, Set.of());
		assertEquals(byEar, quests.current().orElseThrow().byEar());
	}

	@Test
	void rejoiningRestoresTheSavedQuestWithItsRemainingTime() {
		QuestEngine quests = new QuestEngine(List.of(STONE, LOG), new Random(1));
		quests.rejoined(new Quest(LOG, true, 42.0), Set.of());

		assertEquals(new Quest(LOG, true, 42.0), quests.current().orElseThrow());
		quests.timePassed(2, Set.of());
		assertEquals(40.0, quests.current().orElseThrow().secondsLeft());
	}

	@Test
	void rejoiningKeepsCompletedWordsCompleted() {
		QuestEngine quests = new QuestEngine(List.of(STONE, LOG), new Random(1));
		quests.rejoined(null, Set.of(STONE));
		assertEquals(Set.of(STONE), quests.completedWords());

		quests.timePassed(QuestEngine.GAP_SECONDS, Set.of());
		assertEquals(LOG, quests.current().orElseThrow().word()); // never STONE
		quests.itemUnlocked(LOG);
		assertEquals(Set.of(STONE, LOG), quests.completedWords());
	}

	@Test
	void rejoiningWithNoSavedQuestStartsANewOneAfterTheGap() {
		QuestEngine quests = new QuestEngine(List.of(STONE), new Random(1));
		quests.rejoined(null, Set.of());
		assertTrue(quests.current().isEmpty());

		quests.timePassed(QuestEngine.GAP_SECONDS, Set.of());
		assertEquals(STONE, quests.current().orElseThrow().word());
	}

	@Test
	void creativeStopsQuestsWithoutATimeout() {
		QuestEngine quests = questFor(STONE);
		quests.gameModeChanged(false);
		assertTrue(quests.current().isEmpty());

		assertEquals(List.of(), quests.timePassed(1000, Set.of()));
		assertTrue(quests.current().isEmpty());

		quests.gameModeChanged(true);
		quests.timePassed(QuestEngine.GAP_SECONDS, Set.of());
		assertEquals(STONE, quests.current().orElseThrow().word());
	}

	private static Word firstQuest(int seed, Set<Word> held) {
		QuestEngine quests = new QuestEngine(List.of(STONE, DIAMOND), new Random(seed));
		quests.timePassed(QuestEngine.GAP_SECONDS, held);
		return quests.current().orElseThrow().word();
	}

	/** An engine whose only Quest-able Word is {@code word}, with that Quest already showing. */
	private static QuestEngine questFor(Word word) {
		return questFor(word, 1);
	}

	private static QuestEngine questFor(Word word, int seed) {
		QuestEngine quests = new QuestEngine(List.of(word), new Random(seed));
		quests.timePassed(QuestEngine.GAP_SECONDS, Set.of());
		return quests;
	}
}
