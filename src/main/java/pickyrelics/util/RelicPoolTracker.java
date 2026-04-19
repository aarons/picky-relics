package pickyrelics.util;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.PickyRelicsMod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks the initial contents of each relic-tier pool so an exhausted pool can be
 * refilled with relics the player has never obtained (i.e. the skipped ones).
 */
public class RelicPoolTracker {

    private static final Map<AbstractRelic.RelicTier, List<String>> snapshots =
            new EnumMap<>(AbstractRelic.RelicTier.class);
    private static final Set<String> everObtained = new HashSet<>();

    private RelicPoolTracker() {}

    /**
     * Capture the current contents of every pool we care about. Merges with any
     * existing snapshot so relics present in an earlier act aren't forgotten if
     * the game re-initializes pools across acts.
     */
    public static void captureSnapshots() {
        mergeSnapshot(AbstractRelic.RelicTier.COMMON, AbstractDungeon.commonRelicPool);
        mergeSnapshot(AbstractRelic.RelicTier.UNCOMMON, AbstractDungeon.uncommonRelicPool);
        mergeSnapshot(AbstractRelic.RelicTier.RARE, AbstractDungeon.rareRelicPool);
        mergeSnapshot(AbstractRelic.RelicTier.SHOP, AbstractDungeon.shopRelicPool);
        mergeSnapshot(AbstractRelic.RelicTier.BOSS, AbstractDungeon.bossRelicPool);

        Log.debug("Picky Relics: Captured pool snapshots - common=" + sizeOf(AbstractRelic.RelicTier.COMMON)
                + " uncommon=" + sizeOf(AbstractRelic.RelicTier.UNCOMMON)
                + " rare=" + sizeOf(AbstractRelic.RelicTier.RARE)
                + " shop=" + sizeOf(AbstractRelic.RelicTier.SHOP)
                + " boss=" + sizeOf(AbstractRelic.RelicTier.BOSS));
    }

    private static void mergeSnapshot(AbstractRelic.RelicTier tier, ArrayList<String> livePool) {
        if (livePool == null) return;
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        List<String> existing = snapshots.get(tier);
        if (existing != null) merged.addAll(existing);
        merged.addAll(livePool);
        snapshots.put(tier, new ArrayList<>(merged));
    }

    public static void recordObtained(String relicId) {
        if (relicId != null) everObtained.add(relicId);
    }

    /**
     * If cycling is enabled and the live pool for {@code tier} is empty, append
     * every snapshot id that isn't currently in the pool and hasn't been obtained.
     * @return number of ids added (0 if cycling disabled, pool non-empty, or nothing left to refill)
     */
    public static int refillIfEmpty(AbstractRelic.RelicTier tier) {
        if (!PickyRelicsMod.cyclePoolsEnabled) return 0;

        ArrayList<String> livePool = livePoolFor(tier);
        if (livePool == null || !livePool.isEmpty()) return 0;

        List<String> snapshot = snapshots.get(tier);
        if (snapshot == null || snapshot.isEmpty()) return 0;

        int added = 0;
        for (String id : snapshot) {
            if (everObtained.contains(id)) continue;
            livePool.add(id);
            added++;
        }

        if (added > 0) {
            Log.debug("Picky Relics: Refilled " + tier + " pool with " + added + " skipped relic(s)");
        }
        return added;
    }

    public static void reset() {
        snapshots.clear();
        everObtained.clear();
    }

    public static List<String> snapshotFor(AbstractRelic.RelicTier tier) {
        List<String> list = snapshots.get(tier);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public static int obtainedCount() {
        return everObtained.size();
    }

    public static ArrayList<String> livePoolFor(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case COMMON:   return AbstractDungeon.commonRelicPool;
            case UNCOMMON: return AbstractDungeon.uncommonRelicPool;
            case RARE:     return AbstractDungeon.rareRelicPool;
            case SHOP:     return AbstractDungeon.shopRelicPool;
            case BOSS:     return AbstractDungeon.bossRelicPool;
            default:       return null;
        }
    }

    private static int sizeOf(AbstractRelic.RelicTier tier) {
        List<String> list = snapshots.get(tier);
        return list == null ? 0 : list.size();
    }
}
