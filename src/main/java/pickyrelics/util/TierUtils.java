package pickyrelics.util;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.PickyRelicsMod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Shared tier utility methods used by PickyRelicsMod, RelicLinkPatch, and RelicChoicePreview.
 */
public class TierUtils {

    /**
     * Get display text for a relic tier.
     */
    public static String getTierDisplayText(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case STARTER:  return "Starter";
            case COMMON:   return "Common";
            case UNCOMMON: return "Uncommon";
            case RARE:     return "Rare";
            case BOSS:     return "Boss";
            case SHOP:     return "Shop";
            case SPECIAL:  return "Event";
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
     * Common=0, Uncommon=1, Rare=Shop=2, Boss=3
     */
    public static int getTierPosition(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case COMMON: return 0;
            case UNCOMMON: return 1;
            case RARE: return 2;
            case SHOP: return 2;  // Shop equivalent to Rare
            case BOSS: return 3;
            default: return 1;
        }
    }

    /**
     * Build list of allowed tiers based on current settings.
     * Original tier is ALWAYS included. Other tiers depend on settings.
     */
    public static List<AbstractRelic.RelicTier> getAllowedTiers(AbstractRelic.RelicTier original) {
        List<AbstractRelic.RelicTier> candidates = new ArrayList<>();
        int originalPos = getTierPosition(original);

        // Original tier is always allowed
        candidates.add(original);

        // Add lower tiers if allowed
        if (PickyRelicsMod.allowLowerTiers) {
            if (originalPos > 0) candidates.add(AbstractRelic.RelicTier.COMMON);
            if (originalPos > 1) candidates.add(AbstractRelic.RelicTier.UNCOMMON);
        }

        // Add higher tiers if allowed
        if (PickyRelicsMod.allowHigherTiers) {
            if (originalPos < 1) candidates.add(AbstractRelic.RelicTier.UNCOMMON);
            if (originalPos < 2) candidates.add(AbstractRelic.RelicTier.RARE);
            if (PickyRelicsMod.allowBossRelics && originalPos < 3) candidates.add(AbstractRelic.RelicTier.BOSS);
        }

        // Add Shop if allowed (same hierarchy position as Rare)
        if (PickyRelicsMod.allowShopRelics && original != AbstractRelic.RelicTier.SHOP) {
            if ((PickyRelicsMod.allowHigherTiers && originalPos < 2) ||
                (PickyRelicsMod.allowLowerTiers && originalPos > 2) ||
                (originalPos == 2)) {
                candidates.add(AbstractRelic.RelicTier.SHOP);
            }
        }

        return candidates;
    }

    /**
     * Calculate a potentially modified tier for additional relic choices.
     *
     * Algorithm:
     * 1. Roll tierChangeChance% to determine if tier can change
     * 2. If not changing, return original tier
     * 3. If changing, pick randomly from allowed tiers based on settings
     *
     * @param originalTier The original tier of the relic reward
     * @param shouldChange Function that takes chance (0-100) and returns true if tier should change
     * @param randomIndex Function that takes list size and returns random index [0, size)
     * @return The tier to use (never null - original tier is always valid)
     */
    public static AbstractRelic.RelicTier calculateModifiedTier(
            AbstractRelic.RelicTier originalTier,
            IntUnaryOperator shouldChange,
            IntUnaryOperator randomIndex) {

        int chance = PickyRelicsMod.tierChangeChance;

        // Roll to see if tier changes at all (returns 1 for true, 0 for false)
        if (chance <= 0 || shouldChange.applyAsInt(chance) == 0) {
            return originalTier;  // Original tier is always valid
        }

        // Tier can change - pick from allowed tiers
        List<AbstractRelic.RelicTier> candidates = getAllowedTiers(originalTier);
        return candidates.get(randomIndex.applyAsInt(candidates.size()));
    }
}
