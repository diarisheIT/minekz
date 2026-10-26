import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * Builds a Vosk grammar from a words file (one per line). Vosk logs each word it doesn't know to stderr as
 * "Ignoring word missing in vocabulary: 'x'"; gen_word_list.py reads those. Usage: VocabCheck <model dir> <words file>
 */
public class VocabCheck {
	public static void main(String[] args) throws Exception {
		List<String> quoted = new ArrayList<>();
		Files.readAllLines(Path.of(args[1])).forEach(w -> quoted.add('"' + w + '"'));
		LibVosk.setLogLevel(LogLevel.WARNINGS);
		try (Model model = new Model(args[0])) {
			new Recognizer(model, 16000f, "[" + String.join(",", quoted) + "]").close();
		}
	}
}
