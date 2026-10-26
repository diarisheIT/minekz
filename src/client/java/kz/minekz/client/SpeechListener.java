package kz.minekz.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import kz.minekz.MineKz;
import kz.minekz.engine.Grammar;
import kz.minekz.engine.Recognizer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.vosk.Model;

/** Daemon thread: the mic is open only while the server says so (a Challenge runs, no sample plays). Any failure reports speech unavailable. */
final class SpeechListener implements Runnable {
	private static final String MODEL_NAME = "vosk-model-small-kz-0.42";
	private static final AudioFormat FORMAT = new AudioFormat(VoskRecognizer.SAMPLE_RATE, 16, 1, true, false);

	/** Loudest sample in the last mic read, 0 to 1; the HUD's mic indicator follows it. */
	static volatile float level;

	static void start() {
		Thread thread = new Thread(new SpeechListener(), "minekz-speech");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void run() {
		try (Model model = VoskRecognizer.loadModel(unpackModel().toString())) {
			String grammar = Grammar.json(MineKz.words(), MineKz.decoys());
			while (true) {
				if (micOn()) {
					listenWhileChallengeRuns(model, grammar);
				} else {
					Thread.sleep(100);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable e) { // missing mic, denied permission, bad model, missing native library
			MineKz.LOGGER.error("Speech recognition unavailable", e);
			MineKzClient.speechFailed = true;
		}
	}

	/** As the server says, except in single-player's pause menu, where the paused server would only hear the words later. */
	private static boolean micOn() {
		return MineKzClient.challenge.micOn() && !Minecraft.getInstance().isPaused();
	}

	private static void listenWhileChallengeRuns(Model model, String grammar) throws Exception {
		try (Recognizer recognizer = new VoskRecognizer(model, grammar);
				TargetDataLine mic = AudioSystem.getTargetDataLine(FORMAT)) {
			mic.open(FORMAT);
			mic.start();
			byte[] buffer = new byte[3200]; // 0.1 s, so the loop notices the Challenge ending quickly
			while (micOn()) {
				int read = mic.read(buffer, 0, buffer.length);
				level = peak(buffer, read);
				recognizer.accept(buffer, read).ifPresent(MineKzClient.heard::add);
			}
		} finally {
			level = 0;
		}
	}

	private static float peak(byte[] pcm, int length) {
		int max = 0;
		for (int i = 0; i + 1 < length; i += 2) {
			max = Math.max(max, Math.abs((short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8)))); // 16-bit little-endian
		}
		return max / 32768f;
	}

	private static Path unpackModel() throws IOException {
		Path dir = FabricLoader.getInstance().getGameDir().resolve("minekz").resolve(MODEL_NAME);
		if (Files.isDirectory(dir)) {
			return dir;
		}
		Path partial = dir.resolveSibling(MODEL_NAME + ".partial");
		try (InputStream in = SpeechListener.class.getResourceAsStream("/" + MODEL_NAME + ".zip");
				ZipInputStream zip = new ZipInputStream(in)) {
			for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
				Path target = partial.resolve(entry.getName()).normalize();
				if (!target.startsWith(partial)) {
					throw new IOException("Bad model entry " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(target);
				} else {
					Files.createDirectories(target.getParent());
					Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		return Files.move(partial, dir);
	}
}
