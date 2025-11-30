# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Picky Relics is a Slay the Spire mod that adds configurable multi-relic choice rewards (similar to Bossy Relics but with 1-5 choices). Built with ModTheSpire and BaseMod.

## Build Commands

```bash
# Build and deploy to mods folder
mvn clean package

# Extract API reference (required for exploring game/BaseMod APIs)
./scripts/extract-api-reference.sh
```

The build automatically copies the JAR to the SlayTheSpire mods folder.

## Architecture

### Core Components

- `PickyRelicsMod.java` - Main mod class with `@SpireInitializer`. Handles config loading/saving via SpireConfig and settings UI via BaseMod's ModPanel components.

- `patches/RelicRewardPatch.java` - SpirePatch that intercepts `AbstractRoom.addRelicToRewards()` to add additional relic rewards of the same tier. Uses `@SpirePostfixPatch` to add relics after the original reward is created.

### Patching Pattern

The mod uses SpirePatch annotations to modify game behavior:
- `@SpirePatch(clz, method, paramtypez)` - Declares which method to patch
- `@SpirePostfixPatch` - Runs after the original method
- First parameter `__instance` receives the patched object instance

### API Reference

Since game JARs lack source, run `./scripts/extract-api-reference.sh` to extract class files for API exploration:

```bash
# Decompile a class
javap -p api-reference/slaythespire/com/megacrit/cardcrawl/rewards/RewardItem.class

# Search for methods/fields
strings api-reference/slaythespire/com/megacrit/cardcrawl/relics/AbstractRelic.class
```

Key classes: `AbstractRoom`, `RewardItem`, `AbstractRelic`, `AbstractDungeon.returnRandomRelic(tier)`, `BaseMod`, `SpireConfig`.
