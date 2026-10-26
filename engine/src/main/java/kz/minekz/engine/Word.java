package kz.minekz.engine;

/**
 * One Word List entry: a Kazakh word that several items can share (e.g. every kind of pickaxe).
 * {@code sample} is the ASCII file name of its pronunciation clip, without extension.
 * {@code meaningRu} is the Russian meaning shown under the Word for Russian players.
 * {@code tier} is null for Words whose items only exist in Creative; those are never a Quest.
 */
public record Word(String cyrillic, String latin, String meaning, String sample, Tier tier, String meaningRu) {
	/** For tests and callers with no Russian meaning: it falls back to the English one. */
	public Word(String cyrillic, String latin, String meaning, String sample, Tier tier) {
		this(cyrillic, latin, meaning, sample, tier, meaning);
	}

	/** The line under the Word for a Minecraft language code: empty for Kazakh, which needs no explaining; English for any unknown code. */
	public String gloss(String languageCode) {
		return switch (languageCode) {
			case "ru_ru" -> meaningRu;
			case "kk_kz" -> "";
			default -> latin + " · " + meaning;
		};
	}

	/** For each letter of {@code heard}, whether the Word has the same letter (any case) at that position. */
	public boolean[] lettersMatching(String heard) {
		boolean[] matches = new boolean[heard.length()];
		for (int i = 0; i < heard.length() && i < cyrillic.length(); i++) {
			matches[i] = Character.toLowerCase(heard.charAt(i)) == Character.toLowerCase(cyrillic.charAt(i));
		}
		return matches;
	}
}
