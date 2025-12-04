# Relic Filter Logic and Config Backend

Add configurable relic filter lists that control which relics the mod processes. This provides the data layer and filter logic that will later be exposed through a UI (see issue-003).

## Context

The mod currently uses a single toggle (`ignoreSpecialTier`) to skip all SPECIAL tier relics when adding additional reward options. This is heavy-handed and filters out many relics unnecessarily.

**Current behavior (`RelicLinkPatch.java:305-311`):**
```java
if (PickyRelicsMod.ignoreSpecialTier &&
        r.relic != null &&
        r.relic.tier == AbstractRelic.RelicTier.SPECIAL) {
    Log.info("[" + source + "] Skipping SPECIAL tier relic: " + r.relic.relicId);
    continue;
}
```

**Desired behavior:** Two separate filter lists stored in config:

1. **"Never add as option" filter** - Relics that should never be added as additional choices in the reward pool (e.g., relics that break when picked from reward screens)

2. **"Never offer options for" filter** - Relics that should never have additional options generated when they are awarded (e.g., relics that players earn frequently like Fragments of Corruption)

## Implementation Notes

### Config Storage

The mod uses `SpireConfig` for persistence (`PickyRelicsMod.java:49-78`). Add two new config keys:

```java
private static final String CONFIG_RELICS_NEVER_ADD_AS_OPTION = "relicsNeverAddAsOption";
private static final String CONFIG_RELICS_NEVER_OFFER_OPTIONS = "relicsNeverOfferOptions";
```

Store as comma-separated relic IDs:
```
relicsNeverAddAsOption=CircusTroupe,SomeOtherRelic
relicsNeverOfferOptions=FragmentOfCorruption
```

Use `HashSet<String>` in memory for O(1) lookups:
```java
public static HashSet<String> relicsNeverAddAsOption = new HashSet<>();
public static HashSet<String> relicsNeverOfferOptions = new HashSet<>();
```

### Public API

Add methods to `PickyRelicsMod` for checking and modifying filters:

```java
// Check if a relic is filtered
public static boolean shouldNeverAddAsOption(String relicId)
public static boolean shouldNeverOfferOptionsFor(String relicId)

// Modify filters (for UI to call)
public static void addToNeverAddAsOption(String relicId)
public static void removeFromNeverAddAsOption(String relicId)
public static void addToNeverOfferOptions(String relicId)
public static void removeFromNeverOfferOptions(String relicId)

// Get all filtered relics (for UI to display)
public static Set<String> getNeverAddAsOptionRelics()
public static Set<String> getNeverOfferOptionsRelics()
```

### Filter Application

Modify `RelicLinkPatch.processRelicRewards()` to use the new filters:

**"Never offer options for" filter** - Check before creating a linked group for a relic reward:
```java
// Around line 305, replace the SPECIAL tier check with:
if (PickyRelicsMod.shouldNeverOfferOptionsFor(r.relic.relicId)) {
    Log.info("[" + source + "] Skipping filtered relic: " + r.relic.relicId);
    continue;
}
```

**"Never add as option" filter** - Check when generating additional relics in `createLinkedRelicGroup()`:
```java
// Around line 62, after getting additionalRelic:
AbstractRelic additionalRelic = AbstractDungeon.returnRandomRelic(tier);

// Skip filtered relics (retry up to N times to avoid infinite loop)
int retries = 0;
while (PickyRelicsMod.shouldNeverAddAsOption(additionalRelic.relicId) && retries < 10) {
    additionalRelic = AbstractDungeon.returnRandomRelic(tier);
    retries++;
}
if (PickyRelicsMod.shouldNeverAddAsOption(additionalRelic.relicId)) {
    Log.info("Picky Relics: Could not find unfiltered relic after " + retries + " attempts");
    continue;
}
```

### Migration from ignoreSpecialTier

Keep the `ignoreSpecialTier` config for backwards compatibility, but treat it as a convenience toggle:
- When `ignoreSpecialTier` is true AND a SPECIAL relic is encountered, apply the "never offer options" behavior
- This preserves existing user settings while allowing granular overrides

Alternatively, deprecate `ignoreSpecialTier` entirely and migrate users:
- On first load with old config, if `ignoreSpecialTier=true`, add all SPECIAL tier relics to the "never offer options" filter
- Remove the old toggle from the UI

## Suggested Approach

1. Add config keys and in-memory HashSets to `PickyRelicsMod`
2. Implement load/save logic for the filter lists (comma-separated strings)
3. Add public API methods for checking and modifying filters
4. Modify `RelicLinkPatch.processRelicRewards()` to check "never offer options" filter
5. Modify `RelicLinkPatch.createLinkedRelicGroup()` to check "never add as option" filter
6. Decide on migration strategy for `ignoreSpecialTier`
7. Add a few sensible defaults (if any known problematic relics exist)

## Testing & Validation

Since there's no UI yet, test by manually editing the config file:

1. **Config persistence:**
   - Manually add relic IDs to `~/.prefs/pickyrelics/config.txt`:
     ```
     relicsNeverAddAsOption=Burning Blood
     relicsNeverOfferOptions=Circlet
     ```
   - Start the game, verify filters load correctly (check logs)
   - Restart the game, verify filters persist

2. **"Never add as option" filter:**
   - Add a common relic (e.g., "Burning Blood") to the filter
   - Start a run, get multiple relic rewards
   - Verify that relic never appears as an additional choice (may appear as original reward)

3. **"Never offer options for" filter:**
   - Add a relic to the filter
   - Use console command: `relic add <relicId>` to trigger a reward with that relic
   - Verify no additional options are generated for that specific reward

4. **Edge cases:**
   - Empty filter lists (should work normally)
   - Invalid relic IDs in config (should be ignored gracefully)
   - All relics of a tier filtered from "add as option" (should gracefully skip after retries)

## Documentation

- Add code comments explaining the filter system
- Update README.md with a note that manual config editing is possible (until UI is available)
