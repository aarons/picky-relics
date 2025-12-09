package pickyrelics.util;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.PickyRelicsMod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Shared tier utility methods used by PickyRelicsMod, RelicLinkPatch, and RelicChoicePreview.
 */
public class TierUtils {

    // Lazy-loaded localized tier names
    private static UIStrings tierNamesStrings;
    private static String[] TIER_NAMES;

    private static void ensureTierNamesLoaded() {
        if (tierNamesStrings == null) {
            tierNamesStrings = CardCrawlGame.languagePack.getUIString(PickyRelicsMod.makeID("TierNames"));
            TIER_NAMES = tierNamesStrings.TEXT;
        }
    }

    /**
     * Get display text for a relic tier.
     */
    public static String getTierDisplayText(AbstractRelic.RelicTier tier) {
        ensureTierNamesLoaded();
        switch (tier) {
            case STARTER:  return TIER_NAMES[0];
            case COMMON:   return TIER_NAMES[1];
            case UNCOMMON: return TIER_NAMES[2];
            case RARE:     return TIER_NAMES[3];
            case BOSS:     return TIER_NAMES[4];
            case SHOP:     return TIER_NAMES[5];
            case SPECIAL:  return TIER_NAMES[6];
            default:       return "";
        }
    }

    /**
     * Get color for a relic tier.
     */
    public static Color getTierColor(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case STARTER:  return Settings.PURPLE_COLOR;
            case COMMON:   return Settings.GREEN_TEXT_COLOR;
            case UNCOMMON: return Settings.BLUE_TEXT_COLOR;
            case RARE:     return Settings.GOLD_COLOR;
            case BOSS:     return Settings.RED_TEXT_COLOR;
            case SHOP:     return Settings.GOLD_COLOR;
            case SPECIAL:  return Settings.PURPLE_COLOR;
            default:       return Settings.CREAM_COLOR;
        }
    }

    /**
     * Get hierarchy position for a tier.
     * Common=0, Uncommon=1, Rare=2, Shop=3, Boss=4
     */
    public static int getTierPosition(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case COMMON: return 0;
            case UNCOMMON: return 1;
            case RARE: return 2;
            case SHOP: return 3;
            case BOSS: return 4;
            default: return 1;
        }
    }

    /**
     * Get tier from hierarchy position.
     */
    public static AbstractRelic.RelicTier getTierFromPosition(int position) {
        switch (position) {
            case 0: return AbstractRelic.RelicTier.COMMON;
            case 1: return AbstractRelic.RelicTier.UNCOMMON;
            case 2: return AbstractRelic.RelicTier.RARE;
            case 3: return AbstractRelic.RelicTier.SHOP;
            case 4: return AbstractRelic.RelicTier.BOSS;
            default: return AbstractRelic.RelicTier.COMMON;
        }
    }

    /**
     * Check if a tier position is enabled for cascading.
     * Common, Uncommon, Rare are always enabled.
     * Shop requires allowShopRelics, Boss requires allowBossRelics.
     */
    public static boolean isTierEnabled(int position) {
        switch (position) {
            case 0: // Common
            case 1: // Uncommon
            case 2: // Rare
                return true;
            case 3: // Shop
                return PickyRelicsMod.allowShopRelics;
            case 4: // Boss
                return PickyRelicsMod.allowBossRelics;
            default:
                return false;
        }
    }

    /**
     * Find the next enabled tier position in the given direction.
     * @param currentPosition Starting position
     * @param direction +1 for up (toward Boss), -1 for down (toward Common)
     * @return Next enabled position, or -1 if none exists
     */
    public static int findNextEnabledTier(int currentPosition, int direction) {
        int next = currentPosition + direction;
        while (next >= 0 && next <= 4) {
            if (isTierEnabled(next)) {
                return next;
            }
            next += direction;
        }
        return -1;
    }

    /**
     * Build the pool of available tier positions based on current settings.
     * Pool is ordered from lowest to highest tier position.
     *
     * @param originalTier The original tier of the relic reward
     * @return List of tier positions that can be selected
     */
    public static List<Integer> buildTierPool(AbstractRelic.RelicTier originalTier) {
        List<Integer> pool = new ArrayList<>();
        int originalPosition = getTierPosition(originalTier);

        // Add lower tiers first (so pool is ordered low-to-high)
        if (PickyRelicsMod.allowLowerTiers) {
            for (int pos = 0; pos < originalPosition; pos++) {
                if (isTierEnabled(pos)) {
                    pool.add(pos);
                }
            }
        }

        // Always include the original tier
        pool.add(originalPosition);

        // Add higher tiers
        if (PickyRelicsMod.allowHigherTiers) {
            for (int pos = originalPosition + 1; pos <= 4; pos++) {
                if (isTierEnabled(pos)) {
                    pool.add(pos);
                }
            }
        }

        return pool;
    }

    /**
     * Build pool for a given starting position (used by probability calculations).
     */
    private static List<Integer> buildTierPoolForPosition(int startPosition) {
        return buildTierPool(getTierFromPosition(startPosition));
    }

    // Concentration factor for exponential decay distribution.
    // Higher values = more peaked around target tier.
    // k=2.0 gives ~50% chance for target tier, ~28% for adjacent tiers combined.
    private static final double CONCENTRATION_K = 2.0;

    /**
     * Calculate a potentially modified tier using exponential decay distribution.
     *
     * Algorithm:
     * 1. Build pool of available tiers based on direction toggles
     * 2. Map slider (0-100%) to target position in pool
     * 3. Assign weights using exponential decay from target: weight = exp(-k * |i - target|)
     * 4. Select tier via weighted random
     *
     * @param originalTier The original tier of the relic reward
     * @param shouldChange Unused in new algorithm (kept for API compatibility)
     * @param randomIndex Function that takes max value and returns random int [0, max)
     * @return The tier to use (never null - original tier is always valid)
     */
    public static AbstractRelic.RelicTier calculateModifiedTier(
            AbstractRelic.RelicTier originalTier,
            IntUnaryOperator shouldChange,
            IntUnaryOperator randomIndex) {

        // Build available tier pool
        List<Integer> pool = buildTierPool(originalTier);
        if (pool.size() <= 1) {
            return originalTier;
        }

        // Map slider to target position in pool
        double sliderPercent = PickyRelicsMod.targetTierPercent / 100.0;
        double target = sliderPercent * (pool.size() - 1);

        // Calculate weights using exponential decay
        double[] weights = new double[pool.size()];
        double totalWeight = 0;
        for (int i = 0; i < pool.size(); i++) {
            weights[i] = Math.exp(-CONCENTRATION_K * Math.abs(i - target));
            totalWeight += weights[i];
        }

        // Weighted random selection
        // Use randomIndex to get a value 0-9999, then scale to 0-1 for precision
        double roll = randomIndex.applyAsInt(10000) / 10000.0;
        double cumulative = 0;
        for (int i = 0; i < pool.size(); i++) {
            cumulative += weights[i] / totalWeight;
            if (roll < cumulative) {
                return getTierFromPosition(pool.get(i));
            }
        }

        // Fallback (shouldn't happen due to floating point, but just in case)
        return getTierFromPosition(pool.get(pool.size() - 1));
    }

    /**
     * Calculate probability distribution for tier outcomes starting from Common.
     * Uses exponential decay formula matching the runtime algorithm.
     *
     * @return Map of tier position (0-4) to probability (0.0-1.0)
     */
    public static java.util.Map<Integer, Double> calculateTierProbabilities() {
        return calculateTierProbabilities(0);
    }

    /**
     * Calculate probability distribution for tier outcomes starting from a given tier.
     * Uses exponential decay formula matching the runtime algorithm.
     *
     * @param startPosition Starting tier position (0=Common, 1=Uncommon, 2=Rare)
     * @return Map of tier position (0-4) to probability (0.0-1.0)
     */
    public static java.util.Map<Integer, Double> calculateTierProbabilities(int startPosition) {
        java.util.Map<Integer, Double> probabilities = new java.util.LinkedHashMap<>();

        // Initialize all to 0
        for (int i = 0; i <= 4; i++) {
            probabilities.put(i, 0.0);
        }

        // Build pool from perspective of this starting tier
        List<Integer> pool = buildTierPoolForPosition(startPosition);
        if (pool.isEmpty()) {
            probabilities.put(startPosition, 1.0);
            return probabilities;
        }

        // If only one tier in pool, 100% for that tier
        if (pool.size() == 1) {
            probabilities.put(pool.get(0), 1.0);
            return probabilities;
        }

        // Calculate weights using same algorithm as runtime
        double sliderPercent = PickyRelicsMod.targetTierPercent / 100.0;
        double target = sliderPercent * (pool.size() - 1);

        double[] weights = new double[pool.size()];
        double totalWeight = 0;
        for (int i = 0; i < pool.size(); i++) {
            weights[i] = Math.exp(-CONCENTRATION_K * Math.abs(i - target));
            totalWeight += weights[i];
        }

        // Convert to probabilities
        for (int i = 0; i < pool.size(); i++) {
            probabilities.put(pool.get(i), weights[i] / totalWeight);
        }

        return probabilities;
    }
}
