# Picky Relics

A mod for Slay The Spire.

Picky Relics is like Bossy Relics, except more flexible. Instead of selecting one relic from a choice of 3, this mod provides a choice of 2 by default... or 3, or more - you can configure the behavior in the config menu.

## Features

- **Configurable Choices**: Choose how many relic options you want (1-5, default: 2)
- **Selective Application**: Enable/disable for treasure chests and events separately
- **Combat Rewards**: Always applies to combat relic rewards (elite fights, etc.)

## Installation

### Prerequisites

1. **Slay the Spire** (Steam version recommended)
2. **ModTheSpire** - Install from [Steam Workshop](https://steamcommunity.com/sharedfiles/filedetails/?id=1605060445)
3. **BaseMod** - Install from [Steam Workshop](https://steamcommunity.com/sharedfiles/filedetails/?id=2819140563)

### Installing the Mod

1. Download `PickyRelics.jar` from the releases
2. Place it in your Slay the Spire mods folder:
   - **macOS**: `~/Library/Application Support/Steam/steamapps/common/SlayTheSpire/mods/`
   - **Windows**: `C:\Program Files (x86)\Steam\steamapps\common\SlayTheSpire\mods\`
   - **Linux**: `~/.steam/steam/steamapps/common/SlayTheSpire/mods/`
3. Launch the game via ModTheSpire
4. Enable "Picky Relics" and "BaseMod" in the mod selection screen

## Configuration

Access the mod settings through the in-game mod config menu:

- **Relic Choices**: Number of relics to choose from (1-5)
- **Apply to Chests**: Enable/disable for treasure chest rewards
- **Apply to Events**: Enable/disable for event relic rewards

## Development Setup

### Requirements

- **Java 8** (JDK 1.8) - ModTheSpire requires Java 8 specifically
- **Maven** 3.6+
- **Slay the Spire** installed via Steam
- **ModTheSpire** and **BaseMod** from Steam Workshop

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/picky-relics.git
   cd picky-relics
   ```

2. Verify dependencies exist (the pom.xml expects Workshop mods):
   - ModTheSpire: Steam Workshop ID `1605060445`
   - BaseMod: Steam Workshop ID `2819140563`
   - Slay the Spire: `desktop-1.0.jar` in game directory

3. Update `pom.xml` paths if needed:
   ```xml
   <steamapps.path>${user.home}/Library/Application Support/Steam/steamapps</steamapps.path>
   ```
   Adjust for your OS:
   - **Windows**: `C:/Program Files (x86)/Steam/steamapps`
   - **Linux**: `${user.home}/.steam/steam/steamapps`

4. Build the mod:
   ```bash
   mvn clean package
   ```

5. The JAR will be built to `target/PickyRelics.jar` and automatically copied to your mods folder.

### Testing

1. Launch Slay the Spire through ModTheSpire
2. Enable the mod in the mod selection screen
3. Start a run and defeat an elite to see the relic choice in action
4. Check the mod config menu to adjust settings

### Project Structure

```
picky-relics/
├── pom.xml                                    # Maven build config
├── readme.md
└── src/main/
    ├── java/pickyrelics/
    │   ├── PickyRelicsMod.java               # Main mod class, config UI
    │   └── patches/
    │       └── RelicRewardPatch.java         # Core patching logic
    └── resources/
        └── ModTheSpire.json                  # Mod metadata
```

### How It Works

The mod uses SpirePatch to intercept the `RewardItem` constructor when a relic reward is created. It then converts the single-relic reward into a linked relic reward (the same mechanism used for boss relic choices), adding additional random relics of the same tier.

## Credits

- Inspired by [Bossy Relics](https://steamcommunity.com/sharedfiles/filedetails/?id=2879442656) by Camputer
- Built with [ModTheSpire](https://github.com/kiooeht/ModTheSpire) and [BaseMod](https://github.com/daviscook477/BaseMod)

## License

MIT License
