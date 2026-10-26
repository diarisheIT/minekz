package kz.minekz.client;

import com.google.gson.JsonParser;
import java.util.Optional;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;

/** Offline Kazakh recognition limited to the Word List grammar. One instance per Challenge Window. */
final class VoskRecognizer implements kz.minekz.engine.Recognizer {
	static final float SAMPLE_RATE = 16000f;

	private final org.vosk.Recognizer recognizer;

	VoskRecognizer(Model model, String grammarJson) {
		recognizer = new org.vosk.Recognizer(model, SAMPLE_RATE, grammarJson);
	}

	static Model loadModel(String path) throws java.io.IOException {
		LibVosk.setLogLevel(LogLevel.WARNINGS);
		return new Model(path);
	}

	@Override
	public Optional<String> accept(byte[] pcm, int length) {
		if (!recognizer.acceptWaveForm(pcm, length)) {
			return Optional.empty();
		}
		String text = JsonParser.parseString(recognizer.getResult()).getAsJsonObject().get("text").getAsString();
		return text.isEmpty() ? Optional.empty() : Optional.of(text);
	}

	@Override
	public void close() {
		recognizer.close();
	}
}
