package pickyrelics.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.screens.CombatRewardScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pickyrelics.PickyRelicsMod;

import java.util.ArrayList;

/**
 * Patches to link relic rewards together so claiming one removes the others.
 * Follows the pattern used by Orison mod's RewardLinkPatch.
 */
public class RelicLinkPatch {

    private static final Logger logger = LogManager.getLogger(RelicLinkPatch.class.getName());

    /**
     * Add SpireFields to RewardItem to track linked relics and original status.
     */
    @SpirePatch(clz = RewardItem.class, method = SpirePatch.CLASS)
    public static class RelicLinkFields {
        // The group of linked relics (all share the same ArrayList reference)
        public static SpireField<ArrayList<RewardItem>> linkedRelics = new SpireField<>(() -> null);
        // Whether this is the original relic (true) or added by our patch (false)
        public static SpireField<Boolean> isOriginal = new SpireField<>(() -> true);
    }

    /**
     * Link a group of relic rewards together.
     */
    public static void linkRelicGroup(ArrayList<RewardItem> relics) {
        for (RewardItem r : relics) {
            RelicLinkFields.linkedRelics.set(r, relics);
        }
    }

    /**
     * When a relic reward is claimed, remove all other linked relics.
     */
    @SpirePatch2(clz = RewardItem.class, method = "claimReward")
    public static class ClaimRewardPatch {
        @SpirePostfixPatch
        public static void Postfix(RewardItem __instance, boolean __result) {
            if (!__result) return; // Reward wasn't actually claimed

            ArrayList<RewardItem> linked = RelicLinkFields.linkedRelics.get(__instance);
            if (linked == null) return;

            logger.info("Picky Relics: Relic claimed, removing " + (linked.size() - 1) + " linked relics");

            for (RewardItem other : linked) {
                if (other != __instance) {
                    AbstractDungeon.combatRewardScreen.rewards.remove(other);
                }
            }
        }
    }

    /**
     * When the combat reward screen opens, rebuild linked groups based on current config.
     * This handles the case where player exits to menu, changes numChoices, and returns.
     */
    @SpirePatch2(clz = CombatRewardScreen.class, method = "open", paramtypez = {})
    public static class RefreshLinksOnOpen {
        @SpirePostfixPatch
        public static void Postfix(CombatRewardScreen __instance) {
            refreshRelicLinks(__instance.rewards);
        }
    }

    /**
     * Also patch the String overload of open() for completeness.
     */
    @SpirePatch2(clz = CombatRewardScreen.class, method = "open", paramtypez = {String.class})
    public static class RefreshLinksOnOpenString {
        @SpirePostfixPatch
        public static void Postfix(CombatRewardScreen __instance) {
            refreshRelicLinks(__instance.rewards);
        }
    }

    /**
     * Rebuild linked relic groups based on current config settings.
     */
    public static void refreshRelicLinks(ArrayList<RewardItem> rewards) {
        int numChoices = PickyRelicsMod.numChoices;

        // Find all original relic rewards
        ArrayList<RewardItem> originalRelics = new ArrayList<>();
        for (RewardItem r : rewards) {
            if (r.type == RewardItem.RewardType.RELIC && RelicLinkFields.isOriginal.get(r)) {
                originalRelics.add(r);
            }
        }

        // Remove all non-original (added) relics first
        rewards.removeIf(r -> r.type == RewardItem.RewardType.RELIC && !RelicLinkFields.isOriginal.get(r));

        // For each original relic, rebuild its group based on current numChoices
        for (RewardItem original : originalRelics) {
            if (numChoices <= 1) {
                RelicLinkFields.linkedRelics.set(original, null);
                continue;
            }

            ArrayList<RewardItem> group = new ArrayList<>();
            group.add(original);

            AbstractRelic.RelicTier tier = original.relic.tier;
            for (int i = 1; i < numChoices; i++) {
                AbstractRelic additionalRelic = AbstractDungeon.returnRandomRelic(tier);
                RewardItem newReward = new RewardItem(additionalRelic);
                RelicLinkFields.isOriginal.set(newReward, false);
                rewards.add(newReward);
                group.add(newReward);
            }

            linkRelicGroup(group);
            logger.info("Picky Relics: Refreshed relic group with " + group.size() + " choices");
        }
    }
}
