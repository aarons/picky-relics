# Known Issues

## Potential mod conflict in RelicLinkPatch

**File:** `src/main/java/pickyrelics/patches/RelicLinkPatch.java`

**Problem:** The current implementation uses an `isOriginal` SpireField that defaults to `true`. This could cause issues with other mods:

1. Relics added by other mods are treated as "original" and may get linked choices added to them unexpectedly
2. The `removeIf` call in `refreshRelicLinks` removes all non-original relics on every `open()`, which could interfere with other mods if patch ordering is unfavorable
3. Patch execution order on `CombatRewardScreen.open()` is load-order dependent

**Proposed fix:** Replace the generic `isOriginal` boolean with a more specific `addedByPickyRelics` field:

```java
public static SpireField<Boolean> addedByPickyRelics = new SpireField<>(() -> false);
```

Then only remove/rebuild relics where `addedByPickyRelics` is `true`. This ensures we only touch rewards we explicitly created and won't interfere with other mods.
