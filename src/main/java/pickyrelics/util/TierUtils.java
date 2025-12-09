package pickyrelics.util;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.PickyRelicsMod;

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
     * Calculate weight for a candidate tier based on distance, magnitude, and max distance.
     *
     * Uses steep exponential with hard cutoffs at extremes:
     * - magnitude 0%: only adjacent (d=1) gets weight
     * - magnitude 50%: uniform (all get equal weight)
     * - magnitude 100%: only furthest (d=maxDistance) gets weight
     *
     * @param distance Distance from original tier (1, 2, 3, or 4)
     * @param magnitude Magnitude of change (0 to 100)
     * @param maxDistance Maximum distance in this direction
     * @return Weight for this candidate
     */
    public static double calculateWeight(int distance, int magnitude, int maxDistance) {
        if (distance <= 0) return 0;
        if (maxDistance <= 0) return 0;

        // Hard cutoff at magnitude 0: only adjacent tier
        if (magnitude <= 0) {
            return distance == 1 ? 1.0 : 0.0;
        }

        // Hard cutoff at magnitude 100: only furthest tier
        if (magnitude >= 100) {
            return distance == maxDistance ? 1.0 : 0.0;
        }

        // Smooth transition using steep exponential: weight = d^(k * (mag/50 - 1))
        // k=4 gives good steepness: at mag=25, d=1 gets 4x weight of d=2
        double k = 4.0;
        double exponent = k * (magnitude / 50.0 - 1.0);
        return Math.pow(distance, exponent);
    }

    /**
     * Build list of candidate tiers in a given direction.
     *
     * @param referencePosition Starting tier position
     * @param direction +1 for up (toward Boss), -1 for down (toward Common)
     * @return List of [position, distance] pairs
     */
    private static java.util.List<int[]> buildCandidatesInDirection(int referencePosition, int direction) {
        java.util.List<int[]> candidates = new java.util.ArrayList<>();

        int pos = referencePosition + direction;
        while (pos >= 0 && pos <= 4) {
            if (isTierEnabled(pos)) {
                int distance = Math.abs(pos - referencePosition);
                candidates.add(new int[]{pos, distance});
            }
            pos += direction;
        }

        return candidates;
    }

    /**
     * Get maximum distance from a list of candidates.
     */
    private static int getMaxDistance(java.util.List<int[]> candidates) {
        int max = 0;
        for (int[] c : candidates) {
            if (c[1] > max) max = c[1];
        }
        return max;
    }

    /**
     * Select a tier from candidates using weighted random based on magnitude.
     *
     * @param candidates List of [position, distance] pairs
     * @param magnitude Magnitude of change (0-100)
     * @param randomDouble Random number supplier
     * @return Selected tier position
     */
    private static int selectFromCandidates(java.util.List<int[]> candidates, int magnitude,
                                            java.util.function.DoubleSupplier randomDouble) {
        if (candidates.isEmpty()) return -1;
        if (candidates.size() == 1) return candidates.get(0)[0];

        int maxDistance = getMaxDistance(candidates);

        // Calculate weights
        double totalWeight = 0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = calculateWeight(candidates.get(i)[1], magnitude, maxDistance);
            totalWeight += weights[i];
        }

        // If all weights are 0 (shouldn't happen), fall back to uniform
        if (totalWeight <= 0) {
            return candidates.get((int)(randomDouble.getAsDouble() * candidates.size()))[0];
        }

        // Weighted random selection
        double roll = randomDouble.getAsDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return candidates.get(i)[0];
            }
        }

        return candidates.get(candidates.size() - 1)[0];
    }

    /**
     * Calculate a potentially modified tier using direction-first algorithm.
     *
     * Stage 1: Roll against varianceChance
     *   - If fail → return original tier
     *   - If succeed → proceed to Stage 2
     *
     * Stage 2: Direction selection
     *   - If both directions enabled: randomly pick one (50/50)
     *   - Otherwise: use the enabled direction
     *
     * Stage 3: Magnitude selection within chosen direction
     *   - magnitude 0%: only adjacent tier
     *   - magnitude 100%: only furthest tier
     *   - in between: weighted selection
     *
     * @param originalTier The original tier of the relic reward
     * @param shouldChange Function that takes chance (0-100) and returns 1 if should change, 0 otherwise
     * @param randomDouble Function that returns a random double in [0, 1)
     * @return The tier to use (never null - original tier is always valid)
     */
    public static AbstractRelic.RelicTier calculateModifiedTier(
            AbstractRelic.RelicTier originalTier,
            IntUnaryOperator shouldChange,
            java.util.function.DoubleSupplier randomDouble) {

        int chance = PickyRelicsMod.tierChangeChance;
        int magnitude = PickyRelicsMod.tierChangeMagnitude;

        // Special handling for Event tier: force 100% variance, use Uncommon as reference
        boolean isEventTier = (originalTier == AbstractRelic.RelicTier.SPECIAL);
        int referencePosition = isEventTier ? 1 : getTierPosition(originalTier);

        // Stage 1: Check if variance occurs (Event tier always forces variance)
        if (!isEventTier) {
            // If both direction toggles are off, no variance possible
            if (!PickyRelicsMod.allowHigherTiers && !PickyRelicsMod.allowLowerTiers) {
                return originalTier;
            }

            // Roll for variance
            if (chance <= 0 || shouldChange.applyAsInt(chance) == 0) {
                return originalTier;
            }
        }

        // Stage 2: Build candidates for each direction
        java.util.List<int[]> upCandidates = PickyRelicsMod.allowHigherTiers ?
                buildCandidatesInDirection(referencePosition, +1) : new java.util.ArrayList<>();
        java.util.List<int[]> downCandidates = PickyRelicsMod.allowLowerTiers ?
                buildCandidatesInDirection(referencePosition, -1) : new java.util.ArrayList<>();

        boolean hasUp = !upCandidates.isEmpty();
        boolean hasDown = !downCandidates.isEmpty();

        // Edge case: no valid candidates in either direction
        if (!hasUp && !hasDown) {
            return originalTier;
        }

        // Select direction: if both available, random 50/50
        java.util.List<int[]> candidates;
        if (hasUp && hasDown) {
            candidates = randomDouble.getAsDouble() < 0.5 ? downCandidates : upCandidates;
        } else if (hasUp) {
            candidates = upCandidates;
        } else {
            candidates = downCandidates;
        }

        // Stage 3: Select tier within chosen direction based on magnitude
        int selectedPosition = selectFromCandidates(candidates, magnitude, randomDouble);
        if (selectedPosition < 0) {
            return originalTier;
        }

        return getTierFromPosition(selectedPosition);
    }

    /**
     * Calculate probability distribution for tier outcomes starting from Common.
     * Uses pure math based on direction-first weighted algorithm - no RNG needed.
     *
     * @return Map of tier position (0-4) to probability (0.0-1.0)
     */
    public static java.util.Map<Integer, Double> calculateTierProbabilities() {
        return calculateTierProbabilities(0);
    }

    /**
     * Calculate probability distribution for a list of candidates in one direction.
     *
     * @param candidates List of [position, distance] pairs
     * @param magnitude Magnitude of change (0-100)
     * @return Map of position to probability within this direction (sums to 1.0)
     */
    private static java.util.Map<Integer, Double> calculateDirectionProbabilities(
            java.util.List<int[]> candidates, int magnitude) {
        java.util.Map<Integer, Double> probs = new java.util.LinkedHashMap<>();

        if (candidates.isEmpty()) return probs;

        int maxDistance = getMaxDistance(candidates);

        // Calculate weights
        double totalWeight = 0;
        for (int[] c : candidates) {
            double w = calculateWeight(c[1], magnitude, maxDistance);
            totalWeight += w;
        }

        // Convert to probabilities
        for (int[] c : candidates) {
            double w = calculateWeight(c[1], magnitude, maxDistance);
            probs.put(c[0], totalWeight > 0 ? w / totalWeight : 1.0 / candidates.size());
        }

        return probs;
    }

    /**
     * Calculate probability distribution for tier outcomes starting from a given tier.
     * Uses pure math based on direction-first weighted algorithm - no RNG needed.
     *
     * Algorithm:
     * 1. If variance doesn't occur: stay at original
     * 2. If both directions enabled: 50% chance each direction
     * 3. Within each direction: weighted by magnitude
     *
     * @param startPosition Starting tier position (0=Common, 1=Uncommon, 2=Rare, 3=Shop, 4=Boss)
     * @return Map of tier position (0-4) to probability (0.0-1.0)
     */
    public static java.util.Map<Integer, Double> calculateTierProbabilities(int startPosition) {
        java.util.Map<Integer, Double> probabilities = new java.util.LinkedHashMap<>();

        int chance = PickyRelicsMod.tierChangeChance;
        int magnitude = PickyRelicsMod.tierChangeMagnitude;
        double varianceProb = chance / 100.0;
        double noVarianceProb = 1.0 - varianceProb;

        // Initialize all to 0
        for (int i = 0; i <= 4; i++) {
            probabilities.put(i, 0.0);
        }

        // Check if variance is possible
        boolean canGoUp = PickyRelicsMod.allowHigherTiers;
        boolean canGoDown = PickyRelicsMod.allowLowerTiers;

        if (!canGoUp && !canGoDown) {
            // No variance possible - 100% stays at original
            probabilities.put(startPosition, 1.0);
            return probabilities;
        }

        // Build candidates for each direction
        java.util.List<int[]> upCandidates = canGoUp ?
                buildCandidatesInDirection(startPosition, +1) : new java.util.ArrayList<>();
        java.util.List<int[]> downCandidates = canGoDown ?
                buildCandidatesInDirection(startPosition, -1) : new java.util.ArrayList<>();

        boolean hasUp = !upCandidates.isEmpty();
        boolean hasDown = !downCandidates.isEmpty();

        // Edge case: no valid candidates in either direction
        if (!hasUp && !hasDown) {
            probabilities.put(startPosition, 1.0);
            return probabilities;
        }

        // Probability of staying at original (variance didn't occur)
        probabilities.put(startPosition, noVarianceProb);

        // Calculate direction probabilities
        double upDirProb, downDirProb;
        if (hasUp && hasDown) {
            // Both directions: 50/50 split
            upDirProb = 0.5;
            downDirProb = 0.5;
        } else if (hasUp) {
            upDirProb = 1.0;
            downDirProb = 0.0;
        } else {
            upDirProb = 0.0;
            downDirProb = 1.0;
        }

        // Calculate probabilities within each direction
        java.util.Map<Integer, Double> upProbs = calculateDirectionProbabilities(upCandidates, magnitude);
        java.util.Map<Integer, Double> downProbs = calculateDirectionProbabilities(downCandidates, magnitude);

        // Combine: P(tier) = P(variance) * P(direction) * P(tier | direction)
        for (java.util.Map.Entry<Integer, Double> e : upProbs.entrySet()) {
            int pos = e.getKey();
            double tierProb = varianceProb * upDirProb * e.getValue();
            probabilities.put(pos, probabilities.get(pos) + tierProb);
        }

        for (java.util.Map.Entry<Integer, Double> e : downProbs.entrySet()) {
            int pos = e.getKey();
            double tierProb = varianceProb * downDirProb * e.getValue();
            probabilities.put(pos, probabilities.get(pos) + tierProb);
        }

        return probabilities;
    }
}
