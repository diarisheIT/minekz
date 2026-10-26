package kz.minekz.client;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import kz.minekz.MineKz;
import kz.minekz.engine.Word;

/**
 * Plays a Word's pronunciation sample, {@code assets/minekz/samples/<sample>.wav}, off the game threads, on the machine of the player it
 * belongs to. Always reports the sample finished, even when it can't play, because the Challenge Window waits for it.
 */
final class SamplePlayer {
	private SamplePlayer() {}

	// shortcut: plays through Java Sound, so Minecraft's volume sliders don't apply; move to the game's sound engine if players ask
	/** {@code finished} runs on the thread given by the caller's choice: here, the virtual thread, so hop to the client thread yourself. */
	static void play(Word word, Runnable finished) {
		Thread.startVirtualThread(() -> {
			try {
				playAndWait(word);
			} finally {
				finished.run();
			}
		});
	}

	private static void playAndWait(Word word) {
		String path = "/assets/minekz/samples/" + word.sample() + ".wav";
		try (InputStream in = SamplePlayer.class.getResourceAsStream(path)) {
			if (in == null) {
				MineKz.LOGGER.warn("Missing sample {}", path);
				return;
			}
			try (Clip clip = AudioSystem.getClip()) {
				CountDownLatch stopped = new CountDownLatch(1);
				clip.addLineListener(event -> {
					if (event.getType() == LineEvent.Type.STOP) {
						stopped.countDown();
					}
				});
				clip.open(AudioSystem.getAudioInputStream(new BufferedInputStream(in)));
				clip.start();
				// bounded, so a lost STOP event can't hold the Window shut forever
				stopped.await(clip.getMicrosecondLength() / 1000 + 2000, TimeUnit.MILLISECONDS);
			}
		} catch (Exception e) { // no audio device, unreadable clip
			MineKz.LOGGER.warn("Cannot play sample {}", path, e);
		}
	}
}
