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
     * Calculate a potentially modified tier using cascading probability.
     *
     * Cascading algorithm:
     * 1. Start at original tier
     * 2. If allowHigherTiers: repeatedly roll chance% to move up one tier
     * 3. If no upgrade AND allowLowerTiers: repeatedly roll chance% to move down
     *
     * @param originalTier The original tier of the relic reward
     * @param shouldChange Function that takes chance (0-100) and returns 1 if should change, 0 otherwise
     * @param randomIndex Function that takes list size and returns random index [0, size) - unused in cascading
     * @return The tier to use (never null - original tier is always valid)
     */
    public static AbstractRelic.RelicTier calculateModifiedTier(
            AbstractRelic.RelicTier originalTier,
            IntUnaryOperator shouldChange,
            IntUnaryOperator randomIndex) {

        int chance = PickyRelicsMod.tierChangeChance;
        if (chance <= 0) {
            return originalTier;
        }

        int currentPosition = getTierPosition(originalTier);
        boolean upgraded = false;

        // Try cascading UP if allowHigherTiers
        if (PickyRelicsMod.allowHigherTiers) {
            while (true) {
                // Roll to see if we move up
                if (shouldChange.applyAsInt(chance) == 0) {
                    break;
                }
                int nextUp = findNextEnabledTier(currentPosition, +1);
                if (nextUp == -1) {
                    break;
                }
                currentPosition = nextUp;
                upgraded = true;
            }
        }

        // Try cascading DOWN if allowLowerTiers AND no upgrade happened
        if (!upgraded && PickyRelicsMod.allowLowerTiers) {
            while (true) {
                // Roll to see if we move down
                if (shouldChange.applyAsInt(chance) == 0) {
                    break;
                }
                int nextDown = findNextEnabledTier(currentPosition, -1);
                if (nextDown == -1) {
                    break;
                }
                currentPosition = nextDown;
            }
        }

        return getTierFromPosition(currentPosition);
    }

    /**
     * Calculate probability distribution for tier outcomes starting from Common.
     * Uses pure math based on cascading algorithm - no RNG needed.
     *
     * @return Map of tier position (0-4) to probability (0.0-1.0)
     */
    public static java.util.Map<Integer, Double> calculateTierProbabilities() {
        return calculateTierProbabilities(0);
    }

    /**
     * Calculate probability distribution for tier outcomes starting from a given tier.
     * Uses pure math based on cascading algorithm - no RNG needed.
     *
     * @param startPosition Starting tier position (0=Common, 1=Uncommon, 2=Rare)
     * @return Map of tier position (0-4) to probability (0.0-1.0)
     */
    public static java.util.Map<Integer, Double> calculateTierProbabilities(int startPosition) {
        java.util.Map<Integer, Double> probabilities = new java.util.LinkedHashMap<>();

        int chance = PickyRelicsMod.tierChangeChance;
        double p = chance / 100.0;
        double q = 1.0 - p;

        // Initialize all to 0
        for (int i = 0; i <= 4; i++) {
            probabilities.put(i, 0.0);
        }

        // If chance is 0, 100% stays at starting position
        if (chance <= 0) {
            probabilities.put(startPosition, 1.0);
            return probabilities;
        }

        // Build list of enabled tier positions upward from start
        java.util.List<Integer> upwardPositions = new java.util.ArrayList<>();
        upwardPositions.add(startPosition);
        for (int pos = startPosition + 1; pos <= 4; pos++) {
            if (isTierEnabled(pos)) {
                upwardPositions.add(pos);
            }
        }

        // Build list of enabled tier positions downward from start (not including start)
        java.util.List<Integer> downwardPositions = new java.util.ArrayList<>();
        for (int pos = startPosition - 1; pos >= 0; pos--) {
            if (isTierEnabled(pos)) {
                downwardPositions.add(pos);
            }
        }

        boolean canGoUp = PickyRelicsMod.allowHigherTiers && upwardPositions.size() > 1;
        boolean canGoDown = PickyRelicsMod.allowLowerTiers && !downwardPositions.isEmpty();

        if (!canGoUp && !canGoDown) {
            // No movement possible
            probabilities.put(startPosition, 1.0);
            return probabilities;
        }

        if (canGoUp) {
            // Calculate upward probabilities
            int numUpTiers = upwardPositions.size();
            for (int i = 0; i < numUpTiers; i++) {
                int tierPosition = upwardPositions.get(i);
                int steps = i;

                if (i == numUpTiers - 1) {
                    // Top enabled tier: all rolls succeeded
                    probabilities.put(tierPosition, Math.pow(p, steps));
                } else {
                    // Intermediate tier: succeeded 'steps' times, then failed
                    probabilities.put(tierPosition, Math.pow(p, steps) * q);
                }
            }

            // If can also go down, downward cascade only happens when no upgrade occurred
            // P(no upgrade) = q (failed first roll)
            if (canGoDown) {
                double noUpgradeProb = q;
                // Redistribute the start position probability to downward cascade
                double startProb = probabilities.get(startPosition);
                probabilities.put(startPosition, 0.0);

                // From the "no upgrade" probability, calculate downward cascade
                int numDownTiers = downwardPositions.size();
                for (int i = 0; i < numDownTiers; i++) {
                    int tierPosition = downwardPositions.get(i);
                    int steps = i + 1; // +1 because first step is from start to first down position

                    if (i == numDownTiers - 1) {
                        // Bottom enabled tier: all down rolls succeeded
                        probabilities.put(tierPosition, noUpgradeProb * Math.pow(p, steps));
                    } else {
                        // Intermediate tier going down
                        probabilities.put(tierPosition, noUpgradeProb * Math.pow(p, steps) * q);
                    }
                }

                // Probability of staying at start = failed first up roll, then failed first down roll
                probabilities.put(startPosition, noUpgradeProb * q);
            }
        } else if (canGoDown) {
            // Only downward movement possible
            int numDownTiers = downwardPositions.size();

            // Start position: failed first roll
            probabilities.put(startPosition, q);

            for (int i = 0; i < numDownTiers; i++) {
                int tierPosition = downwardPositions.get(i);
                int steps = i + 1;

                if (i == numDownTiers - 1) {
                    // Bottom enabled tier: all rolls succeeded
                    probabilities.put(tierPosition, Math.pow(p, steps));
                } else {
                    // Intermediate tier
                    probabilities.put(tierPosition, Math.pow(p, steps) * q);
                }
            }
        }

        return probabilities;
    }
}
