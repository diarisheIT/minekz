package kz.minekz.engine;

/**
 * The Quest showing now; {@code secondsLeft} counts down from its Tier's Time Limit. A Quest {@code byEar} shows only a
 * speaker, and its Sample plays when the player presses Listen.
 */
public record Quest(Word word, boolean byEar, double secondsLeft) {}
