<div align="center">

<img src="assets/icon.png" width="112" alt="LyricsCraft icon">

# LyricsCraft

**by [iALTURKi](https://github.com/iAlturki)**

🎵 See the lyrics of the song you're playing, right inside Minecraft. Arabic included.

[![Download](https://img.shields.io/github/v/release/iAlturki/LyricsCraft?label=Download&style=for-the-badge&color=brightgreen)](https://github.com/iAlturki/LyricsCraft/releases/latest)
![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-62b47a?style=for-the-badge)

<img src="assets/demo.gif" width="720" alt="Arabic and English lyrics floating in the world, then landing word by word on a wall and the grass">

</div>

## Features

- 🎤 **Lyrics that follow the song**, on your screen or floating in the world
- 🧱 **Words that land on blocks** one by one as they're sung
- 🌍 **Arabic reads correctly**, right to left with joined letters
- 🖼️ **Now-playing card** with the album cover
- ⌨️ **Music keys in game**: play, pause, skip

## Install

1. Install [Fabric](https://fabricmc.net/use/installer/) for Minecraft **26.3** and [Fabric API](https://modrinth.com/mod/fabric-api)
2. Download LyricsCraft from [Releases](../../releases) and put it in your `mods` folder
3. Play music on your PC (Windows) and start the game

> [!TIP]
> **Optional:** play your music in [ytr-music](https://github.com/iAlturki/ytr-music) for the best results. Any music app works.

## Keys

| Key | What it does |
|:-:|---|
| <kbd>↑</kbd> | Show or hide the lyrics |
| <kbd>↓</kbd> | Settings |
| <kbd>←</kbd> <kbd>→</kbd> | Previous / next song |
| <kbd>\\</kbd> | Play / pause |
| <kbd>[</kbd> <kbd>]</kbd> | Lyrics too early or too late? Move them 5 seconds |

<details>
<summary><b>More details</b></summary>

- **Where lyrics come from:** [LRCLIB](https://lrclib.net) and YouTube Music. Only the song name and artist are sent to find them.
- **Lyrics without timing** are shown with a **≈** and spread over the song.
- **Settings** are saved in `config/lyricscraft.json`. Coming from LyricsPlus? Your settings carry over.
- **Build it yourself:** `./gradlew build` with Java 25.

</details>

## Credits

Made by **[iALTURKi](https://github.com/iAlturki)**, also the maker of [ytr-music](https://github.com/iAlturki/ytr-music). Based on [LyricsPlus](https://www.curseforge.com/minecraft/mc-mods/lyrics) 3.0.0 by FRK (MIT). Lyrics belong to their owners.

## License

MIT © 2026 iALTURKi. See [LICENSE](LICENSE), [NOTICE](NOTICE), and [AUTHORS](AUTHORS).
