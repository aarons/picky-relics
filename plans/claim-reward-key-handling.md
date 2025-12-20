# Potential Issue: ClaimRewardPatch key handling

## Context

After adding the key (e.g., Sapphire Key) to the `linkedRelics` ArrayList to fix the highlighting bug, there may be an issue with `ClaimRewardPatch`.

## Potential Problem

When a relic is claimed, `ClaimRewardPatch.Postfix()` marks all other items in `linkedRelics` as `isDone = true`:

```java
for (RewardItem other : linked) {
    if (other != __instance) {
        other.isDone = true;
        other.ignoreReward = true;
    }
}
```

If the key is now in `linkedRelics`, this would mark the key as done, which might interfere with the native key consumption logic.

## Potential Fix

Skip the key when marking items as done:

```java
for (RewardItem other : linked) {
    if (other != __instance) {
        // Don't mark the originalLink (key) as done - native logic handles it
        if (other == RelicLinkFields.originalRelicLink.get(__instance)) {
            continue;
        }
        other.isDone = true;
        other.ignoreReward = true;
    }
}
```

## Status

- [ ] Test if this is actually an issue after the highlighting fix
- [ ] Implement fix if needed
