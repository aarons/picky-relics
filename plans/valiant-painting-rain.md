# Performance Analysis: steamfix vs highlighting branches

## Summary

The **highlighting branch has valid performance concerns** due to per-frame object allocations, but the overhead is manageable for typical reward counts. The functionality improvement (cross-mod compatibility) may justify the cost, though an optimized hybrid approach would be ideal.

---

## Branch Comparison

### steamfix (current) - `UpdateHighlightPatch` on `RewardItem.update()`

**Implementation:**
- Patches each `RewardItem.update()` call
- Only processes items with `linkedRelics` field set (items our mod created)
- Only the LAST item in each group runs the highlighting logic
- Iterates the linked group to find hovered item, sets `redText` on others

**Performance characteristics:**
- **Memory allocation:** Zero per frame
- **CPU per frame:** O(n) where n = total reward items
  - Items without linkedRelics: ~3 ops (field get, null check, return)
  - Last item in group: 2 iterations over group (~4-8 items typically)
- **Limitation:** Only highlights relics tracked by our mod's `linkedRelics` field

---

### highlighting branch - `UnifiedHighlightPatch` on `CombatRewardScreen.update()`

**Implementation:**
- Patches `CombatRewardScreen.update()` once per frame
- Builds bidirectional adjacency graph from ALL `relicLink` references
- BFS traversal to find connected components
- Sets `redText` for each component based on hover state

**Per-frame allocations (for 16 relics with ~8 links):**
```
1x IdentityHashMap (adjacency map)
~16x HashSet (adjacency sets, 2 per linked item)
1x HashSet (visited set)
~4x LinkedHashSet (component sets)
~4x ArrayDeque (BFS queues)
≈ 26 object allocations per frame
```

**Performance characteristics:**
- **Memory allocation:** ~26 objects per frame × 60 FPS = ~1560 allocations/sec
- **CPU per frame:** O(R + E) where R = rewards, E = edges
- **Benefit:** Discovers connections from ANY mod dynamically

---

## Quantitative Impact Analysis

| Metric | steamfix | highlighting |
|--------|----------|--------------|
| Objects allocated/frame | 0 | ~26 |
| Objects allocated/sec (60 FPS) | 0 | ~1,560 |
| CPU complexity | O(n) | O(R + E) |
| Cross-mod support | No | Yes |

**Context for allocation numbers:**
- Java's garbage collector handles short-lived objects efficiently
- The reward screen is open briefly (seconds, not minutes)
- StS already allocates many objects per frame (rendering, input, etc.)
- ~1,560 objects/sec is noticeable but not severe

---

## Verdict

**The highlighting branch has a measurable performance cost but likely acceptable in practice.**

The allocations will cause minor GC pressure, but:
1. The reward screen is transient (open for seconds)
2. The objects are small and short-lived (generational GC handles well)
3. StS is not frame-critical during reward selection

**However**, the implementation can be optimized to get the best of both worlds.

---

## Recommended Optimization (if pursuing highlighting approach)

Cache the graph and only rebuild when rewards change:

```java
@SpirePatch2(clz = CombatRewardScreen.class, method = "update")
public static class UnifiedHighlightPatch {
    // Cached data - reused across frames
    private static ArrayList<RewardItem> cachedRewardsList = null;
    private static int cachedRewardsHash = 0;
    private static List<Set<RewardItem>> cachedComponents = new ArrayList<>();

    @SpirePostfixPatch
    public static void Postfix(CombatRewardScreen __instance) {
        ArrayList<RewardItem> rewards = __instance.rewards;
        if (rewards == null || rewards.isEmpty()) return;

        // Check if we need to rebuild (list changed)
        int currentHash = computeRelicLinkHash(rewards);
        if (cachedRewardsList != rewards || cachedRewardsHash != currentHash) {
            rebuildComponentCache(rewards);
            cachedRewardsList = rewards;
            cachedRewardsHash = currentHash;
        }

        // Apply highlighting using cached components (no allocation)
        applyHighlighting(cachedComponents);
    }
}
```

This approach:
- Allocates only when rewards change (not every frame)
- Still discovers connections dynamically for cross-mod support
- Zero per-frame allocation during normal hover interactions

---

## Future Consideration: Caching Optimization for UnifiedHighlightPatch

### Problem Context

The `highlighting` branch introduces `UnifiedHighlightPatch` in `RelicLinkPatch.java` which provides cross-mod compatibility by dynamically discovering `relicLink` connections from any source (not just our mod). This is important for compatibility with mods like **Generosity Charm** (from strops mod) that multiply relic rewards by chaining their own `relicLink` references onto ours.

However, the current implementation rebuilds the entire connection graph **every frame**:
- Creates `IdentityHashMap`, `HashSet`, `LinkedHashSet`, `ArrayDeque` objects each frame
- ~26 object allocations per frame × 60 FPS = ~1,560 allocations/second
- Objects don't accumulate (GC clears them efficiently), but causes more frequent minor GC pauses
- A player deliberating on the reward screen for extended periods will trigger extra GC cycles

### Proposed Solution

Cache the connection graph and only rebuild when the rewards list changes. The graph only needs rebuilding when:
1. A new reward is added (another mod adds relics)
2. A reward is claimed/removed
3. A `relicLink` reference changes

**Implementation approach:**

```java
@SpirePatch2(clz = CombatRewardScreen.class, method = "update")
public static class UnifiedHighlightPatch {
    // Cached state - persists across frames
    private static ArrayList<RewardItem> cachedRewardsList = null;
    private static int cachedRelicLinkHash = 0;
    private static List<Set<RewardItem>> cachedComponents = new ArrayList<>();

    @SpirePostfixPatch
    public static void Postfix(CombatRewardScreen __instance) {
        ArrayList<RewardItem> rewards = __instance.rewards;
        if (rewards == null || rewards.isEmpty()) {
            cachedComponents.clear();
            return;
        }

        // Compute a hash of the current relicLink structure
        // This detects: list size changes, relicLink reference changes
        int currentHash = computeRelicLinkHash(rewards);

        // Only rebuild if structure changed
        if (cachedRewardsList != rewards || cachedRelicLinkHash != currentHash) {
            rebuildComponentCache(rewards);
            cachedRewardsList = rewards;
            cachedRelicLinkHash = currentHash;
        }

        // Apply highlighting using cached components (zero allocation)
        for (Set<RewardItem> component : cachedComponents) {
            RewardItem hoveredItem = null;
            for (RewardItem item : component) {
                if (item.hb != null && item.hb.hovered) {
                    hoveredItem = item;
                    break;
                }
            }
            for (RewardItem item : component) {
                item.redText = (hoveredItem != null && item != hoveredItem);
            }
        }
    }

    private static int computeRelicLinkHash(ArrayList<RewardItem> rewards) {
        int hash = rewards.size();
        for (RewardItem r : rewards) {
            if (r.type == RewardItem.RewardType.RELIC) {
                // Include identity hash of relicLink to detect reference changes
                hash = 31 * hash + System.identityHashCode(r.relicLink);
            }
        }
        return hash;
    }

    private static void rebuildComponentCache(ArrayList<RewardItem> rewards) {
        // Clear and reuse the list (avoid allocation)
        cachedComponents.clear();

        // Build adjacency map and find components using BFS
        // (Same algorithm as current implementation, but results cached)
        // ... implementation details ...
    }
}
```

### Benefits of This Approach

| Scenario | Current | With Caching |
|----------|---------|--------------|
| Per-frame during hover | ~26 allocations | 0 allocations |
| When reward added | ~26 allocations | ~26 allocations (rebuild) |
| Player deliberating 60s | ~93,600 allocations | ~26 allocations total |

### Implementation Notes

1. **Hash function must detect all changes**: Use `System.identityHashCode()` for relicLink references since we care about reference identity, not value equality

2. **Clear cache on screen close**: Consider adding a patch to `CombatRewardScreen.reopen()` or similar to clear stale cache

3. **Thread safety**: StS is single-threaded for game logic, so static fields are safe here

4. **Testing**: Verify with Generosity Charm mod that highlighting still works after optimization

### When to Implement

This optimization is **not urgent** - the current implementation is functional and the performance cost is acceptable for the brief reward screen duration. Consider implementing if:
- Users report stuttering on reward screens
- Profiling shows GC pressure from this code path
- Adding other per-frame logic to the reward screen
