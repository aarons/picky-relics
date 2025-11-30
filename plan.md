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

