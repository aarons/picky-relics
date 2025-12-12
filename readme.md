# Picky Relics

A mod for Slay The Spire.

Picky Relics is like the excellent Bossy Relics mod, except it is configurable. Instead of receiving a single relic reward, choose from multiple options. Configure how many choices you want (1-5) for each relic tier independently.

## Features

- **Configurable Choices (1-5)**: Set the number of relic options for each tier independently:
  - Starter, Common, Uncommon, Rare, Shop, Event, and Boss tiers
  - Default: 2 choices per tier
  - Set to 1 to restore vanilla behavior for any tier

- **Tier Shifting Algorithm**: Optionally allow additional relic choices to come from different tiers:
  - **Tier Change Chance** (0-100%): Probability that a relic's tier will shift
  - **Tier Change Magnitude** (0-100%): How far the tier can drift (0 = adjacent only, 100 = maximum drift)
  - **Direction Controls**: Allow shifts toward higher tiers, lower tiers, or both
  - **Pool Controls**: Optionally include Shop and Boss relics in the tier pool

- **Tier Labels**: Optionally display the relic's tier on reward screens for quick identification

- **Live Preview**: See a sample of relic choices update in real-time as you adjust settings

- **Probability Display**: View the exact tier outcome probabilities based on your algorithm settings

- **17+ Languages**: AI-generated localizations for Simplified Chinese, Japanese, Korean, German, French, Spanish, Russian, Portuguese, Turkish, Italian, Greek, Ukrainian, Vietnamese, Polish, Indonesian, Thai, and Serbian

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

Access the mod settings through the in-game mod config menu. Settings are organized into two pages:

### Page 1: Choices Per Tier
- Sliders for each relic tier (Starter, Common, Uncommon, Rare, Shop, Event, Boss)
- Live preview showing sample relics based on current settings
- Toggle to show/hide tier labels on reward screens

### Page 2: Tier Algorithm
- Tier change chance and magnitude sliders
- Checkboxes for direction (higher/lower tiers) and pool (shop/boss relics)
- Probability table showing exact outcome chances for each starting tier

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

### Project Structure

```
picky-relics/
├── pom.xml                                    # Maven build config
├── README.md
├── CHANGELOG.md
├── scripts/
│   └── extract-api-reference.sh              # API extraction script
└── src/main/
    ├── java/pickyrelics/
    │   ├── PickyRelicsMod.java               # Main mod class, config UI
    │   ├── patches/
    │   │   └── RelicLinkPatch.java           # Linked relic rewards
    │   ├── ui/
    │   │   ├── PagedElement.java             # Paged settings support
    │   │   ├── PageNavigator.java            # Page switching UI
    │   │   ├── ProbabilityDisplay.java       # Tier probability table
    │   │   └── RelicChoicePreview.java       # Live relic preview
    │   └── util/
    │       ├── Log.java                      # Logging utilities
    │       └── TierUtils.java                # Tier calculation logic
    └── resources/
        ├── ModTheSpire.json                  # Mod metadata
        └── pickyrelicsResources/
            ├── badge.png                     # Mod badge icon
            └── localization/
                └── {eng,zhs,jpn,...}/        # Language files
                    └── UIStrings.json
```

### How It Works

The mod uses SpirePatch to intercept relic rewards. When a relic reward is created, it converts the single-relic reward into a linked relic reward (the same mechanism the game uses for boss relic choices), adding additional random relics based on your tier settings.

## Credits

- Inspired by [Bossy Relics](https://steamcommunity.com/sharedfiles/filedetails/?id=2879442656) by Camputer
- Built with [ModTheSpire](https://github.com/kiooeht/ModTheSpire) and [BaseMod](https://github.com/daviscook477/BaseMod)

## License

MIT License
