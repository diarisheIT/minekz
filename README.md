# MineKZ

**Beta release.** Expect bugs.

A Fabric mod for Minecraft 1.21.11 that teaches Kazakh. Go let your bratishkas play it.

## Download and install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **Minecraft 1.21.11** (Java 21 or newer).
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11 and put it in your `mods` folder.
3. Download `minekz-1.0.0-beta.1.jar` from the [latest release](../../releases/latest) and put it in the same `mods` folder.
4. Start the game and allow the microphone when asked.

The jar is about 94 MB because it bundles the offline Kazakh speech model. The first launch unpacks it to the game folder.

## Build from source

Needs JDK 21 and the Vosk Kazakh model:

1. Download `vosk-model-small-kz-0.42` from <https://alphacephei.com/vosk/models> and unzip it to `prototypes/vosk-piper-PROTOTYPE/cache/vosk-model-small-kz-0.42`.
2. Run `./gradlew build`. The jar is in `build/libs/minekz-1.0.0-beta.1.jar`.

Run in development with `./gradlew runClient`.

## Credits

- Speech recognition: [Vosk](https://alphacephei.com/vosk/) (Apache 2.0).
- Pronunciation samples: synthetic voice, Azure Speech `kk-KZ-DauletNeural`.
- Minecraft is a trademark of Mojang. This project is not affiliated with Mojang or Microsoft.

Licensed under the [MIT License](LICENSE).
