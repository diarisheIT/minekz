package kz.minekz.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The Vosk grammar: every Word, decoys and [unk]. Cyrillic stays unescaped (JSON unicode escapes break Vosk). */
public final class Grammar {
	private Grammar() {}

	/**
	 * {@code decoys} are words that are never Challenges (near misses like бас for тас, common words). A grammar of
	 * only the Word List forces any speech onto the nearest Word; decoys give wrong speech somewhere else to land.
	 */
	public static String json(Collection<Word> words, Collection<String> decoys) {
		List<String> quoted = new ArrayList<>();
		for (Word word : words) {
			quoted.add('"' + word.cyrillic() + '"');
		}
		for (String decoy : decoys) {
			if (words.stream().noneMatch(w -> w.cyrillic().equals(decoy))) {
				quoted.add('"' + decoy + '"');
			}
		}
		quoted.add("\"[unk]\"");
		return "[" + String.join(", ", quoted) + "]";
	}
}
