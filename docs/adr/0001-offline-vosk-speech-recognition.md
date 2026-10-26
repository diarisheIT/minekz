# Offline Vosk for speech recognition

The mod must stay free, so speech recognition runs on the player's own computer using Vosk and its Kazakh model, packed inside the mod. The recogniser only accepts words from the Word List (plus "unrecognised"), which keeps single-word recognition reliable and turns every other Word into a decoy.

## Considered Options

- **Cloud speech-to-text (Google `kk-KZ`, Azure, Yandex)**: rejected. It costs money per minute past the free tier, and the API key would have to ship inside the mod, where anyone could extract it.
- **Whisper (whisper.cpp)**: rejected. The small models are weak at Kazakh, it's heavier on the CPU, and it's awkward to call from Java.

## Consequences

The recogniser checks *which* word was heard, not *how well* it was pronounced. Recognition accuracy depends on the quality of the Kazakh Vosk model, so it has to be checked in a prototype before the full build.
