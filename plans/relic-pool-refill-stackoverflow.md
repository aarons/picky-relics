# Bug: StackOverflowError from relic-pool cycling (refill)

## Summary

With the **cycle-pools** setting on (`cyclePoolsEnabled`), generating a relic can crash
with `java.lang.StackOverflowError`. The crash is genuine infinite recursion inside
vanilla `AbstractDungeon.returnEndRandomRelicKey` (and its twin `returnRandomRelicKey`).
Our `RelicPoolRefillPatch` prefix refills an exhausted tier pool on **every** call —
including the recursive ones — which destroys the invariant vanilla relies on to
terminate its `!canSpawn()` retry-recursion. When a refilled set of "skipped" relics is
made up entirely of relics that currently fail `canSpawn()`, the retry recurses forever.

This matches the field report's hypothesis exactly: *skipped relics reappearing is what
causes the loop.*

## The crash

```
at pickyrelics.util.Log.debug(Log.java:18)
at pickyrelics.util.RelicPoolTracker.refillIfEmpty(RelicPoolTracker.java:81)
at pickyrelics.patches.RelicPoolRefillPatch.Prefix(RelicPoolRefillPatch.java:39)
at ...AbstractDungeon.returnEndRandomRelicKey(AbstractDungeon.java)
at ...AbstractDungeon.returnEndRandomRelicKey(AbstractDungeon.java:980)   ← repeats ∞
at ...AbstractDungeon.returnEndRandomRelicKey(AbstractDungeon.java:980)
```

The `Log.debug` frame at the top is incidental — it's the `"Refilled … pool"` debug line
in `refillIfEmpty`, and its log4j call happened to consume the last bytes of stack. The
real loop is the repeating `returnEndRandomRelicKey:980` frames.

## Root cause

### 1. Vanilla has a hidden retry-recursion at line 980

Decompiled `returnEndRandomRelicKey` (bytecode line table → lines 979–983):

```java
retVal = <pop last item from this tier's pool>;     // or a fallback if the pool is empty
...
if (!RelicLibrary.getRelic(retVal).canSpawn()) {     // line 979
    return returnEndRandomRelicKey(tier);             // line 980  ← recursion
}
return retVal;                                        // line 983
```

When a popped relic can't currently spawn, vanilla retries with the same tier. In vanilla
this **always terminates**: each call does `pool.remove(size()-1)`, so the pool strictly
shrinks, and when it finally hits empty the tier's `case` branch escapes to a guaranteed
result (RARE → `"Circlet"`, BOSS → `"Red Circlet"`, COMMON/SHOP → `returnRandomRelicKey`
of a lower tier). **Pool-goes-empty is the escape hatch.**

### 2. The refill prefix destroys that escape hatch

`RelicPoolRefillPatch.Prefix` (`RelicPoolRefillPatch.java:38`) runs before every call —
including each recursive line-980 call — and calls `RelicPoolTracker.refillIfEmpty(tier)`
(`RelicPoolTracker.java:64`), which repopulates an empty pool with snapshot relics not in
`everObtained` (the skipped ones). So the pool can never be observed empty by vanilla:

```
returnEndRandomRelicKey(UNCOMMON)
  └─ Prefix: pool empty → refill with skipped relics [A, B, C]   (all canSpawn()==false)
  └─ vanilla: pop C → canSpawn(C)==false → recurse (line 980)
       └─ Prefix: pool=[A,B] not empty → no-op
       └─ vanilla: pop B → false → recurse
            └─ Prefix: pool=[A] → no-op
            └─ vanilla: pop A → false → recurse
                 └─ Prefix: pool EMPTY → REFILL [A,B,C] again   ← escape hatch defeated
                 └─ vanilla: pop C → false → recurse ...  ∞  → StackOverflowError
```

### 3. The `canSpawn()` trigger

The loop only happens when an **entire** refilled set fails `canSpawn()`. If any member
could spawn, vanilla pops it and returns — recursion ends. Several base-game relics gate
on floor number:

```java
// Omamori, MoltenEgg2, FrozenEgg2, ToxicEgg2, CeramicFish, Matryoshka, …
public boolean canSpawn() {
    return Settings.isEndless || AbstractDungeon.floorNum <= 48;
}
```

So in a **non-endless run past floor 48**, those relics all return `false`. Late in a run
a tier's "skipped but never-obtained" set can narrow to only these gated relics (even one
is enough). `refillIfEmpty` re-adds them every cycle; `canSpawn()` is deterministically
false (floor number is fixed during the reward); the retry recurses forever.

### 4. Which path crashed, and why the existing guard didn't catch it

The stack is the **`End` variant**, whose only caller is the shop:
`ShopScreen.initRelics` → `returnRandomRelicEnd` → `returnEndRandomRelicKey` (confirmed:
`ShopScreen.class` invokes `returnRandomRelicEnd` ×2). Elite/combat rewards
(`AbstractRoom`) and our own `getRelicWithFallback` use `returnRandomRelic` →
`returnRandomRelicKey` (the non-`End` twin). The `"Returning UNCOMMON relic"` line is
logged by `returnRandomRelicEnd` just before it calls into the loop, so the crash was the
shop generating its uncommon relic (UNCOMMON is dense with floor-48-gated relics).

Notes:
- The `MAX_ATTEMPTS = 10` guard in `getRelicWithFallback` (`RelicLinkPatch.java:113`) can't
  help: it wraps the *outer* `returnRandomRelic` calls, but the loop is *inside* a single
  key-lookup call that never returns. The shop path doesn't use that helper at all.
- **Both** patched methods are affected — `returnRandomRelicKey` has the identical
  line-980-style `!canSpawn()` recursion. Any fix must cover both (the prefix patches both,
  so a fix in `refillIfEmpty` or a shared guard covers both automatically).

## Design question: does filtering by `canSpawn()` make refill a no-op?

Short answer: **No, not in general** — but the intuition points at the correct behavior.

- `canSpawn()` returns `true` for the large majority of relics. It's `false` only in
  special cases — chiefly the floor-48-gated relics, and only once you're past floor 48 in
  a non-endless run. So filtering by `canSpawn()` during refill still re-adds all the
  *normal* skipped relics; the feature keeps working. It excludes only the relics that
  literally cannot appear right now — which are exactly the ones causing the loop.
- The one case where the filter "does nothing" is when **every** remaining skipped relic is
  currently un-spawnable. In that case adding nothing is the *correct, intended* outcome:
  there is genuinely nothing valid to offer, so the pool stays empty and vanilla falls
  through to its normal fallback (lower tier / Circlet). That's the non-looping behavior we
  want, not a defect.
- Soundness: `canSpawn()` reads game state (floor number, current room, owned relics) that
  does not change between our prefix and vanilla's immediate pop within one synchronous
  resolution, so a relic that passes the refill filter will also pass vanilla's line-979
  check → get returned. And line 980 is the *only* self-recursion in the method, gated
  solely by `!canSpawn()` — so canSpawn filtering is a **complete** fix for this loop, not a
  partial one.

So your worry ("skipped relics fail canSpawn, so refill does nothing") only holds for the
rare all-unspawnable set, where doing nothing is exactly right.

## Solution options (high level)

1. **Filter by `canSpawn()` during refill** (content fix).
   In `refillIfEmpty`, skip ids whose `RelicLibrary.getRelic(id).canSpawn()` is false, in
   addition to the existing `everObtained` check. Removes the loop-causing relics from the
   refill set; also fixes a latent issue where cycling could otherwise offer un-spawnable
   relics. Small, localized change; mirrors what `getRelicWithFallback` already does.
   Caveat: relies on `canSpawn()` being the only recursion trigger (verified true here).

2. **Idempotent / re-entrancy guard** (structural fix — looks most robust).
   Allow a refill only on the *outermost* entry to a key-resolution, not on the recursive
   line-980 re-entries. E.g. add a paired prefix/postfix that tracks recursion depth (or a
   "tier already refilled this resolution" flag), and only refill when depth goes 0→1. The
   first refill still happens; if the pool drains again during the same recursive chain,
   refill is suppressed → pool stays empty → vanilla escapes. Doesn't depend on predicting
   `canSpawn()`; robust to any reason vanilla might recurse. Main design point is scoping
   "one resolution" cleanly (postfix to decrement/clear the counter).

3. **One-shot refill per tier until something is obtained** (coarser structural fix).
   Track which tiers have already been refilled; don't refill again until `recordObtained`
   fires (or a new floor/act resets it). Breaks the loop (second empty → no refill →
   escape) and is simple, but changes feature cadence (a tier refills once, then waits).

4. **Move cycling out of the recursive hot path** (refactor).
   Instead of mutating pool state inside the per-key recursion, refill at a higher level
   (e.g. a prefix on `ShopScreen.initRelics`, and around reward/event relic generation) so
   vanilla's internal recursion runs on a stable, pre-filled pool and terminates normally.
   Cleanest design, but the biggest change and must cover every entry point the current
   prefix covers.

5. **Combine 1 + 2** (belt-and-suspenders).
   Filter `canSpawn()` so we never add known-bad relics, *and* guard re-entrancy so even an
   unforeseen un-spawnable case can't loop. Most defensive; small incremental cost over
   doing either alone.

6. **Recursion-depth backstop** (safety net, not a real fix).
   Detect runaway depth in the prefix and stop refilling past a threshold. Only worth it as
   a cheap last-resort guard layered under a real fix.

## Recommendation (tentative — for discussion)

Lean toward **1 + 2 combined (option 5)**: option 1 makes the feature only ever offer
relics that can actually spawn (correctness win on its own), and option 2 guarantees no
recursion interaction can ever loop regardless of `canSpawn()` behavior. Both are small and
localized to `RelicPoolTracker` / `RelicPoolRefillPatch`, and both automatically cover the
`returnRandomRelicKey` twin. Option 4 is the "right" long-term shape if we want to stop
mutating vanilla pool state mid-recursion, but it's a larger change.
