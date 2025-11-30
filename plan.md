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

---

# Code Cleanup


## Extract shared helper for creating linked relic groups

**Files:**
- `src/main/java/pickyrelics/patches/RelicRewardPatch.java` (lines 59-78)
- `src/main/java/pickyrelics/patches/RelicLinkPatch.java` (lines 212-228)

**Problem:** Both locations have nearly identical code for creating a linked group of relics:
1. Create an ArrayList and add the original relic
2. Find the insert index (after the original in the rewards list)
3. Loop to create additional relics of the same tier
4. Mark each new relic as non-original
5. Insert into rewards list at the correct position
6. Add to the group ArrayList
7. Call `linkRelicGroup(group)`

**Fix:** Extract a helper method in `RelicLinkPatch`:
```java
public static void createLinkedRelicGroup(ArrayList<RewardItem> rewards, RewardItem original, int numChoices)
```

Then both `RelicRewardPatch.Postfix` and `RelicLinkPatch.refreshRelicLinks` can call this helper, eliminating the duplication.

---

## Simplify isLinkTarget check in RenderLinkPatch

**File:** `src/main/java/pickyrelics/patches/RelicLinkPatch.java`
**Location:** Lines 136-145 in `RenderLinkPatch.Postfix`

**Problem:** The current code iterates through all linked items to check if any item's `relicLink` field points to the current instance:
```java
boolean isLinkTarget = false;
for (RewardItem other : linked) {
    if (other != __instance && other.relicLink == __instance) {
        isLinkTarget = true;
        break;
    }
}
```

Since `linkRelicGroup` always sets up a linear chain A→B→C (first links to second, second links to third, etc.), an item is a "link target" if and only if it is NOT the first item in the group. The chain icon renders ABOVE an item to connect it to the one above, so we want to render on items 2, 3, etc. but not item 1.

**Fix:** Replace the loop with a simple index check:
```java
boolean isLinkTarget = linked.get(0) != __instance;
```

This is clearer, more efficient, and directly expresses the intent: "render the chain icon on all items except the first."
