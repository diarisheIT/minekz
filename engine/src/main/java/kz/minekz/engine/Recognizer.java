package kz.minekz.engine;

import java.util.Optional;

/** Turns microphone audio into finished utterances. */
public interface Recognizer extends AutoCloseable {
	/** Feeds 16 kHz mono 16-bit little-endian PCM; returns the text once an utterance is complete. */
	Optional<String> accept(byte[] pcm, int length);

	@Override
	void close();
}
