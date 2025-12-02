# CLAUDE.md

## Project Overview

Picky Relics is a Slay the Spire mod that adds configurable multi-relic choice rewards (similar to Bossy Relics but with 1-5 choices). Built with ModTheSpire and BaseMod.

## Build

```bash
mvn clean package
```

## Architecture

### Core Components

- `PickyRelicsMod.java` - Main mod class with `@SpireInitializer`. Handles config loading/saving via SpireConfig and settings UI via BaseMod's ModPanel components.

- `patches/RelicRewardPatch.java` - SpirePatch that intercepts `AbstractRoom.addRelicToRewards()` to add additional relic rewards of the same tier. Uses `@SpirePostfixPatch` to add relics after the original reward is created.

### Patching Pattern

The mod uses SpirePatch annotations to modify game behavior:
- `@SpirePatch(clz, method, paramtypez)` - Declares which method to patch
- `@SpirePostfixPatch` - Runs after the original method
- First parameter `__instance` receives the patched object instance

### Reference Materials

Reference materials for exploring game APIs are in `references/`:

- **slaythespire/** - Extracted class files from the game JAR. Use `javap -p <class>` to inspect methods/fields. Key classes: `AbstractRoom`, `RewardItem`, `AbstractRelic`, `AbstractDungeon`.

- **basemod/** - Extracted class files from BaseMod. Key classes: `BaseMod`, `SpireConfig`, and various subscriber interfaces.
