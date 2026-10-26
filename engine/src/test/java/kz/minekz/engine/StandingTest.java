package kz.minekz.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StandingTest {
	private static final Word STONE = new Word("тас", "tas", "stone", "tas", Tier.COMMON);
	private static final Word LOG = new Word("бөрене", "börene", "log", "borene", Tier.COMMON);
	private static final Word COW = new Word("сиыр", "siyr", "cow", "siyr", Tier.COMMON);
	private static final Set<Word> ANIMALS = Set.of(COW);

	private static List<String> ranked(Standing... standings) {
		return List.of(standings).stream().sorted(Standing.ORDER).map(Standing::name).toList();
	}

	@Test
	void countsLearnedItemsAndAnimalsApart() {
		Standing standing = Standing.of(UUID.randomUUID(), "Aru", Set.of(STONE, LOG, COW), Map.of(), ANIMALS, true);
		assertEquals(2, standing.items());
		assertEquals(1, standing.animals());
	}

	@Test
	void moreLearnedWordsRanksHigher() {
		assertEquals(List.of("Aru", "Bek"), ranked(
				Standing.of(UUID.randomUUID(), "Bek", Set.of(STONE), Map.of(), ANIMALS, true),
				Standing.of(UUID.randomUUID(), "Aru", Set.of(STONE, COW), Map.of(), ANIMALS, false)));
	}

	@Test
	void aTieGoesToWhoeverReachedTheCountFirst() {
		assertEquals(List.of("Bek", "Aru"), ranked(
				Standing.of(UUID.randomUUID(), "Aru", Set.of(STONE, LOG), Map.of(STONE, 100L, LOG, 500L), ANIMALS, true),
				Standing.of(UUID.randomUUID(), "Bek", Set.of(STONE, COW), Map.of(STONE, 300L, COW, 400L), ANIMALS, true)));
	}

	@Test
	void wordsWithoutTimesCameFromOlderSavesSoTheyReachedItEarlier() {
		assertEquals(List.of("Aru", "Bek"), ranked(
				Standing.of(UUID.randomUUID(), "Bek", Set.of(STONE), Map.of(STONE, 100L), ANIMALS, true),
				Standing.of(UUID.randomUUID(), "Aru", Set.of(LOG), Map.of(), ANIMALS, true)));
	}
}
