package pickyrelics.patches;

import basemod.ReflectionHacks;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
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
     * Add SpireFields to RewardItem to track linked relics and ownership.
     */
    @SpirePatch(clz = RewardItem.class, method = SpirePatch.CLASS)
    public static class RelicLinkFields {
        // The group of linked relics (all share the same ArrayList reference)
        public static SpireField<ArrayList<RewardItem>> linkedRelics = new SpireField<>(() -> null);
        // Whether this reward was added by Picky Relics (not the original game/other mods)
        public static SpireField<Boolean> addedByPickyRelics = new SpireField<>(() -> false);
    }

    /**
     * Link a group of relic rewards together.
     * Sets both our custom linkedRelics field (for removal logic) and
     * the game's built-in relicLink field (for visual chain icons).
     */
    public static void linkRelicGroup(ArrayList<RewardItem> relics) {
        // Set our custom field for tracking the full group
        for (RewardItem r : relics) {
            RelicLinkFields.linkedRelics.set(r, relics);
        }

        // Set the game's relicLink field in a linear chain for visual display
        // A→B→C (each item links to the next, last item has no link)
        if (relics.size() >= 2) {
            for (int i = 0; i < relics.size() - 1; i++) {
                RewardItem current = relics.get(i);
                RewardItem next = relics.get(i + 1);
                current.relicLink = next;
            }
            // Last item doesn't link to anything (no chain below it)
            relics.get(relics.size() - 1).relicLink = null;
        }
    }

    /**
     * When a relic reward is claimed, mark all other linked relics as done.
     * Using isDone=true instead of remove() avoids ConcurrentModificationException
     * since claimReward is called during the reward list iteration.
     */
    @SpirePatch2(clz = RewardItem.class, method = "claimReward")
    public static class ClaimRewardPatch {
        @SpirePostfixPatch
        public static void Postfix(RewardItem __instance, boolean __result) {
            if (!__result) return; // Reward wasn't actually claimed

            ArrayList<RewardItem> linked = RelicLinkFields.linkedRelics.get(__instance);
            if (linked == null) return;

            logger.info("Picky Relics: Relic claimed, marking " + (linked.size() - 1) + " linked relics as done");

            for (RewardItem other : linked) {
                if (other != __instance) {
                    // Mark as done - the game will remove it after iteration completes
                    other.isDone = true;
                    // Prevent the relic from being obtained
                    other.ignoreReward = true;
                }
            }
        }
    }

    /**
     * When hovering over a linked relic, highlight all other linked relics with red text.
     * Uses a postfix patch so we run AFTER the game's native relicLink handling.
     * Only the LAST item in each group performs the logic - this ensures we execute after
     * all native relicLink updates have completed (native code sets relicLink.redText = hovered,
     * which would overwrite our values if we ran earlier in the chain).
     */
    @SpirePatch2(clz = RewardItem.class, method = "update")
    public static class UpdateHighlightPatch {
        @SpirePostfixPatch
        public static void Postfix(RewardItem __instance) {
            ArrayList<RewardItem> linked = RelicLinkFields.linkedRelics.get(__instance);
            if (linked == null || linked.isEmpty()) return;

            // Only the LAST item in the group handles redText for the whole group
            // This ensures we run AFTER all native relicLink updates have occurred
            // (Native code: each item sets relicLink.redText = hovered, which can overwrite our values)
            if (linked.get(linked.size() - 1) != __instance) return;

            // Find which item (if any) is being hovered
            for (RewardItem hoveredItem : linked) {
                if (hoveredItem.hb.hovered) {
                    // Set redText on all OTHER linked items
                    for (RewardItem other : linked) {
                        other.redText = (other != hoveredItem);
                    }
                    return;
                }
            }

            // Nobody in the group is hovered, reset all redText
            for (RewardItem r : linked) {
                r.redText = false;
            }
        }
    }

    /**
     * Render the chain icon and tooltip for linked relic rewards.
     */
    @SpirePatch2(clz = RewardItem.class, method = "render")
    public static class RenderLinkPatch {
        @SpirePostfixPatch
        public static void Postfix(RewardItem __instance, SpriteBatch sb) {
            if (__instance.type != RewardItem.RewardType.RELIC) return;

            ArrayList<RewardItem> linked = RelicLinkFields.linkedRelics.get(__instance);
            if (linked == null || linked.size() < 2) return;

            // Chain renders ABOVE the item, so we render on all items except the first
            // (the first item has no chain above it connecting to a previous item)
            if (linked.get(0) != __instance) {
                ReflectionHacks.privateMethod(RewardItem.class, "renderRelicLink", SpriteBatch.class)
                        .invoke(__instance, sb);
            }

            // Render tooltip when hovering
            if (__instance.hb.hovered) {
                String title = "Linked";
                String body = "Obtaining this relic will remove the other #y" + (linked.size() - 1) + " linked relic choices.";
                TipHelper.renderGenericTip(
                        360.0F * Settings.scale,
                        InputHelper.mY + 50.0F * Settings.scale,
                        title, body);
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

        // Find all original relic rewards (those NOT added by Picky Relics)
        ArrayList<RewardItem> originalRelics = new ArrayList<>();
        for (RewardItem r : rewards) {
            if (r.type == RewardItem.RewardType.RELIC && !RelicLinkFields.addedByPickyRelics.get(r)) {
                originalRelics.add(r);
            }
        }

        // Remove all relics that were added by Picky Relics
        rewards.removeIf(r -> r.type == RewardItem.RewardType.RELIC && RelicLinkFields.addedByPickyRelics.get(r));

        // For each original relic, rebuild its group based on current numChoices
        for (RewardItem original : originalRelics) {
            if (numChoices <= 1) {
                RelicLinkFields.linkedRelics.set(original, null);
                original.relicLink = null; // Clear visual link
                continue;
            }

            ArrayList<RewardItem> group = new ArrayList<>();
            group.add(original);

            // Find the index of the original reward so we can insert after it
            int insertIndex = rewards.indexOf(original) + 1;

            AbstractRelic.RelicTier tier = original.relic.tier;
            for (int i = 1; i < numChoices; i++) {
                AbstractRelic additionalRelic = AbstractDungeon.returnRandomRelic(tier);
                RewardItem newReward = new RewardItem(additionalRelic);
                RelicLinkFields.addedByPickyRelics.set(newReward, true);
                rewards.add(insertIndex, newReward);
                insertIndex++; // Next one goes after this one
                group.add(newReward);
            }

            linkRelicGroup(group);
            logger.info("Picky Relics: Refreshed relic group with " + group.size() + " choices");
        }
    }
}
