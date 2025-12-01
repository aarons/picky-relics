# Picky Relics

A mod for Slay The Spire.

Picky Relics is like the excellent Bossy Relics mod, except it is configurable. Instead of selecting one relic from a choice of 3, this mod provides a choice of 2 by default... or more; you can configure the behavior in the config menu.

## Features

- **Configurable Choices**: Choose how many relic options you want (1-5, default: 2)
- **Separate Settings**: Configure combat rewards and treasure chests independently
- **Original Behavior**: Set to 1 to restore vanilla game behavior for either context

## Installation

### Prerequisites

1. **Slay the Spire** (Steam version recommended)
2. **ModTheSpire** - Install from [Steam Workshop](https://steamcommunity.com/sharedfiles/filedetails/?id=1605060445)
3. **BaseMod** - Install from [Steam Workshop](https://steamcommunity.com/sharedfiles/filedetails/?id=1605833019)

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

- **Combat Rewards**: Number of relic choices for combat rewards like elite fights (1-5, default: 2)
- **Treasure Chests**: Number of relic choices for treasure chest rewards (1-5, default: 2)

Set either option to 1 for original game behavior in that context.

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
   - BaseMod: Steam Workshop ID `1605833019`
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
├── scripts/
│   └── extract-api-reference.sh              # API extraction script
└── src/main/
    ├── java/pickyrelics/
    │   ├── PickyRelicsMod.java               # Main mod class, config UI
    │   └── patches/
    │       └── RelicRewardPatch.java         # Core patching logic
    └── resources/
        └── ModTheSpire.json                  # Mod metadata
```

### API Reference

The game and BaseMod don't provide source JARs, so we extract the compiled JARs for API exploration. Run the setup script after cloning:

```bash
./scripts/extract-api-reference.sh
```

This creates (gitignored):
```
api-reference/
├── slaythespire/                        # Extracted desktop-1.0.jar
│   └── com/megacrit/cardcrawl/          # Game classes
│       ├── relics/                      # AbstractRelic, relic implementations
│       ├── rewards/                     # RewardItem, RewardType
│       ├── rooms/                       # AbstractRoom, MonsterRoom, etc.
│       ├── dungeons/                    # AbstractDungeon
│       └── ...
└── basemod/                             # Extracted BaseMod.jar
    └── basemod/                         # Mod utilities
        ├── BaseMod.class                # Main mod registration
        ├── ModPanel.class               # Settings UI
        ├── ModMinMaxSlider.class        # UI components
        ├── interfaces/                  # Subscriber interfaces
        └── patches/                     # SpirePatch utilities
```

#### Browsing the API

**List class members with strings:**
```bash
strings api-reference/slaythespire/com/megacrit/cardcrawl/rewards/RewardItem.class
```

**Decompile a class with javap:**
```bash
# Show all members (including private)
javap -p api-reference/slaythespire/com/megacrit/cardcrawl/rewards/RewardItem.class

# Show method signatures with types
javap -s api-reference/slaythespire/com/megacrit/cardcrawl/relics/AbstractRelic.class
```

**Search for classes by name:**
```bash
find api-reference -name "*.class" | grep -i reward
```

**Search for method/field names across all classes:**
```bash
# Find classes that reference "relicLink"
grep -r "relicLink" api-reference --include="*.class" -l

# Search with strings for more context
for f in api-reference/slaythespire/com/megacrit/cardcrawl/rewards/*.class; do
  echo "=== $f ===" && strings "$f" | grep -i "relic"
done
```

**Find enum values:**
```bash
strings api-reference/slaythespire/com/megacrit/cardcrawl/rewards/RewardItem\$RewardType.class
```

#### Key Classes

| Class | Purpose |
|-------|---------|
| `AbstractRoom` | Base room class, has `addRelicToRewards()` |
| `RewardItem` | Individual reward entry (relic, gold, card, etc.) |
| `AbstractRelic` | Base relic class with `tier` field |
| `AbstractDungeon` | Static methods like `returnRandomRelic()` |
| `BaseMod` | Mod registration and subscriptions |
| `ModPanel` / `ModMinMaxSlider` | Settings UI components |
| `SpireConfig` | Persistent mod configuration |

### How It Works

The mod uses SpirePatch to intercept the `RewardItem` constructor when a relic reward is created. It then converts the single-relic reward into a linked relic reward (the same mechanism used for boss relic choices), adding additional random relics of the same tier.

## Credits

- Inspired by [Bossy Relics](https://steamcommunity.com/sharedfiles/filedetails/?id=2879442656) by Camputer
- Built with [ModTheSpire](https://github.com/kiooeht/ModTheSpire) and [BaseMod](https://github.com/daviscook477/BaseMod)

## License

MIT License
