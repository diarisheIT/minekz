package kz.minekz.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WordTest {
	private static final Word STONE = new Word("Тас", "tas", "stone", "tas", Tier.COMMON, "камень");

	@Test
	void heardLettersMatchTheWordPositionByPositionIgnoringCase() {
		assertArrayEquals(new boolean[] {true, true, false}, STONE.lettersMatching("тал"));
	}

	@Test
	void extraHeardLettersDoNotMatch() {
		assertArrayEquals(new boolean[] {true, true, true, false, false}, STONE.lettersMatching("тасқа"));
	}

	@Test
	void glossIsLatinAndEnglishMeaningExceptInRussianAndKazakh() {
		assertEquals("tas · stone", STONE.gloss("en_us"));
		assertEquals("tas · stone", STONE.gloss("de_de"));
		assertEquals("камень", STONE.gloss("ru_ru"));
		assertEquals("", STONE.gloss("kk_kz"));
	}
}
