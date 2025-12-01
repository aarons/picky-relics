package pickyrelics.patches;

import basemod.ReflectionHacks;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.*;
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
        // The original external tail (e.g., Sapphire Key) that should be preserved at end of chain
        // Stored on the original relic so we can restore it during refresh
        public static SpireField<RewardItem> originalExternalTail = new SpireField<>(() -> null);
    }

    /**
     * Create a linked group of relic rewards for the given original relic.
     * Generates additional relics of the same tier, inserts them after the original,
     * marks them as added by Picky Relics, and links them all together.
     * Preserves any existing relicLink (e.g., to a Sapphire Key) at the end of the chain.
     *
     * @param rewards    The rewards list to modify
     * @param original   The original relic reward to build a group around
     * @param numChoices Total number of relics in the group (including original)
     */
    public static void createLinkedRelicGroup(ArrayList<RewardItem> rewards, RewardItem original, int numChoices) {
        // Get the external tail to preserve at end of chain
        // First time: save original's relicLink (e.g., Sapphire Key) for future refreshes
        // On refresh: use the saved value since original.relicLink now points to our (removed) relic
        RewardItem existingTail = RelicLinkFields.originalExternalTail.get(original);
        if (existingTail == null && original.relicLink != null) {
            // First time setup - save the original's external link
            existingTail = original.relicLink;
            RelicLinkFields.originalExternalTail.set(original, existingTail);
        }

        ArrayList<RewardItem> group = new ArrayList<>();
        group.add(original);

        int insertIndex = rewards.indexOf(original) + 1;
        AbstractRelic.RelicTier tier = original.relic.tier;

        for (int i = 1; i < numChoices; i++) {
            AbstractRelic additionalRelic = AbstractDungeon.returnRandomRelic(tier);
            RewardItem newReward = new RewardItem(additionalRelic);
            RelicLinkFields.addedByPickyRelics.set(newReward, true);
            rewards.add(insertIndex, newReward);
            insertIndex++;
            group.add(newReward);
        }

        linkRelicGroup(group, existingTail);
    }

    /**
     * Link a group of relic rewards together.
     * Sets both our custom linkedRelics field (for removal logic) and
     * the game's built-in relicLink field (for visual chain icons).
     *
     * @param relics      The group of relics to link together
     * @param existingTail Optional existing relicLink to preserve at the end of the chain
     *                     (e.g., a Sapphire Key that was already linked to the original)
     */
    public static void linkRelicGroup(ArrayList<RewardItem> relics, RewardItem existingTail) {
        // Set our custom field for tracking the full group
        for (RewardItem r : relics) {
            RelicLinkFields.linkedRelics.set(r, relics);
        }

        // Set the game's relicLink field in a linear chain for visual display
        // A→B→C→existingTail (each item links to the next)
        if (relics.size() >= 2) {
            for (int i = 0; i < relics.size() - 1; i++) {
                RewardItem current = relics.get(i);
                RewardItem next = relics.get(i + 1);
                current.relicLink = next;
            }
        }
        // Last item links to the existing tail (e.g., Sapphire Key) or null
        if (!relics.isEmpty()) {
            relics.get(relics.size() - 1).relicLink = existingTail;
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
        int totalChoices = getTotalChoicesForCurrentRoom();

        // Find all original relic rewards (those NOT added by Picky Relics)
        ArrayList<RewardItem> originalRelics = new ArrayList<>();
        for (RewardItem r : rewards) {
            if (r.type == RewardItem.RewardType.RELIC && !RelicLinkFields.addedByPickyRelics.get(r)) {
                originalRelics.add(r);
            }
        }

        // Remove all relics that were added by Picky Relics
        rewards.removeIf(r -> r.type == RewardItem.RewardType.RELIC && RelicLinkFields.addedByPickyRelics.get(r));

        // For each original relic, rebuild its group based on current settings
        for (RewardItem original : originalRelics) {
            if (totalChoices <= 1) {
                RelicLinkFields.linkedRelics.set(original, null);
                // Restore original's direct link to external tail (e.g., Sapphire Key)
                RewardItem externalTail = RelicLinkFields.originalExternalTail.get(original);
                original.relicLink = externalTail;
                continue;
            }

            createLinkedRelicGroup(rewards, original, totalChoices);
            logger.info("Picky Relics: Refreshed relic group with " + totalChoices + " choices");
        }
    }

    /**
     * Returns the total number of relic choices for the current room.
     */
    private static int getTotalChoicesForCurrentRoom() {
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        if (room == null) {
            return 1;
        }

        if (room instanceof MonsterRoom || room instanceof MonsterRoomElite) {
            return PickyRelicsMod.combatChoices;
        }

        if (room instanceof TreasureRoom || room instanceof TreasureRoomBoss) {
            return PickyRelicsMod.chestChoices;
        }

        return 1;
    }
}
