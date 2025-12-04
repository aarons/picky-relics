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
import pickyrelics.PickyRelicsMod;
import pickyrelics.util.Log;

import java.util.ArrayList;

/**
 * Patches to link relic rewards together so claiming one removes the others.
 * Follows the pattern used by Orison mod's RewardLinkPatch.
 */
public class RelicLinkPatch {

    /**
     * Add SpireFields to RewardItem to track linked relics and ownership.
     */
    @SpirePatch(clz = RewardItem.class, method = SpirePatch.CLASS)
    public static class RelicLinkFields {
        // The group of linked relics (all share the same ArrayList reference)
        public static SpireField<ArrayList<RewardItem>> linkedRelics = new SpireField<>(() -> null);
        // Whether this reward was added by Picky Relics (not the original game/other mods)
        public static SpireField<Boolean> addedByPickyRelics = new SpireField<>(() -> false);
        // The original relicLink that existed before we modified the chain (e.g., Sapphire Key)
        public static SpireField<RewardItem> originalRelicLink = new SpireField<>(() -> null);
    }

    /**
     * Create a linked group of relic rewards for the given original relic.
     * Generates additional relics of the same tier, inserts them after the original,
     * marks them as added by Picky Relics, and links them all together.
     *
     * @param rewards    The rewards list to modify
     * @param original   The original relic reward to build a group around
     * @param numChoices Total number of relics in the group (including original)
     */
    public static void createLinkedRelicGroup(ArrayList<RewardItem> rewards, RewardItem original, int numChoices) {
        // Save the original relicLink before we modify the chain (e.g., Sapphire Key)
        // Only save if we haven't already (handles refresh case)
        if (RelicLinkFields.originalRelicLink.get(original) == null && original.relicLink != null) {
            RelicLinkFields.originalRelicLink.set(original, original.relicLink);
        }
        // Use stored value (handles refresh case where original.relicLink was already modified)
        RewardItem originalLink = RelicLinkFields.originalRelicLink.get(original);

        ArrayList<RewardItem> group = new ArrayList<>();
        group.add(original);

        int insertIndex = rewards.indexOf(original) + 1;
        AbstractRelic.RelicTier tier = original.relic.tier;

        for (int i = 1; i < numChoices; i++) {
            AbstractRelic additionalRelic = AbstractDungeon.returnRandomRelic(tier);

            // Skip Circlet - it's a placeholder relic when no relics of the tier are available
            if ("Circlet".equals(additionalRelic.relicId)) {
                Log.info("Picky Relics: Skipping Circlet placeholder relic");
                continue;
            }

            RewardItem newReward = new RewardItem(additionalRelic);
            RelicLinkFields.addedByPickyRelics.set(newReward, true);
            rewards.add(insertIndex, newReward);

            // Position the new reward so it renders immediately (mirrors CombatRewardScreen.positionRewards)
            float yPos = (float)Settings.HEIGHT / 2.0F - 124.0F * Settings.scale
                         - (float)insertIndex * 100.0F * Settings.scale;
            newReward.move(yPos);

            insertIndex++;
            group.add(newReward);
        }

        linkRelicGroup(group, originalLink);
    }

    /**
     * Link a group of relic rewards together.
     * Sets both our custom linkedRelics field (for removal logic) and
     * the game's built-in relicLink field (for visual chain icons).
     *
     * @param relics       The group of relics to link together
     * @param originalLink The original relicLink from the first relic (e.g., Sapphire Key), or null
     */
    public static void linkRelicGroup(ArrayList<RewardItem> relics, RewardItem originalLink) {
        // Set our custom field for tracking the full group
        for (RewardItem r : relics) {
            RelicLinkFields.linkedRelics.set(r, relics);
        }

        // Set the game's relicLink field in a linear chain for visual display
        // A→B→C→originalLink (each item links to the next, last item links to original link)
        if (relics.size() >= 2) {
            for (int i = 0; i < relics.size() - 1; i++) {
                RewardItem current = relics.get(i);
                RewardItem next = relics.get(i + 1);
                current.relicLink = next;
            }
            // Last item links to the original link (e.g., Sapphire Key) if it exists
            relics.get(relics.size() - 1).relicLink = originalLink;
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

            Log.info("Picky Relics: Relic claimed, marking " + (linked.size() - 1) + " linked relics as done");

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

            // Get the original link (e.g., Sapphire Key) from the first item in the group
            RewardItem originalLink = RelicLinkFields.originalRelicLink.get(linked.get(0));

            // Find which item (if any) is being hovered
            for (RewardItem hoveredItem : linked) {
                if (hoveredItem.hb.hovered) {
                    // Set redText on all OTHER linked items
                    for (RewardItem other : linked) {
                        other.redText = (other != hoveredItem);
                    }
                    // Also set redText on the original link (e.g., Sapphire Key)
                    if (originalLink != null) {
                        originalLink.redText = true;
                    }
                    return;
                }
            }

            // Nobody in the group is hovered, reset all redText
            for (RewardItem r : linked) {
                r.redText = false;
            }
            // Also reset redText on the original link
            if (originalLink != null) {
                originalLink.redText = false;
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
     * Primary hook: Process relic rewards at display time.
     * This runs after setupItemReward() copies rewards from room to screen,
     * catching ALL relics regardless of their source (combat, events, chests, etc.).
     */
    @SpirePatch2(clz = CombatRewardScreen.class, method = "setupItemReward")
    public static class ProcessRelicRewardsOnSetup {
        @SpirePostfixPatch
        public static void Postfix(CombatRewardScreen __instance) {
            processRelicRewards(__instance.rewards, "SETUP");
        }
    }

    /**
     * Secondary hook: Catches relics added after setupItemReward() by other mods.
     * Some mods add relics directly to combatRewardScreen.rewards after setup,
     * bypassing AbstractRoom.addRelicToRewards(). This hook ensures we process them.
     */
    @SpirePatch2(clz = CombatRewardScreen.class, method = "update")
    public static class ProcessLateRelicRewards {
        @SpirePostfixPatch
        public static void Postfix(CombatRewardScreen __instance) {
            // Quick check: any unlinked relics that should have extra choices?
            boolean hasUnlinked = false;
            for (RewardItem r : __instance.rewards) {
                if (r.type == RewardItem.RewardType.RELIC &&
                    RelicLinkFields.linkedRelics.get(r) == null) {
                    // Check if this tier should have extra choices
                    if (r.relic != null && PickyRelicsMod.getChoicesForTier(r.relic.tier) <= 1) {
                        continue;
                    }
                    hasUnlinked = true;
                    break;
                }
            }

            if (hasUnlinked) {
                Log.info("[UPDATE] Found unlinked relic(s) added after setup, processing...");
                int sizeBefore = __instance.rewards.size();
                int processed = processRelicRewards(__instance.rewards, "UPDATE");

                // If we added relics, reposition everything
                if (__instance.rewards.size() != sizeBefore) {
                    Log.info("[UPDATE] Repositioning rewards after adding " +
                            (__instance.rewards.size() - sizeBefore) + " new relic(s)");
                    __instance.positionRewards();
                }
            }
        }
    }

    /**
     * Process all relic rewards in the list, creating linked groups for any
     * that don't already have them.
     *
     * @param rewards The rewards list to process
     * @param source  Logging source to identify which hook triggered processing
     *                (e.g., "SETUP", "UPDATE")
     * @return The number of relics that were processed (had linked groups created)
     */
    public static int processRelicRewards(ArrayList<RewardItem> rewards, String source) {
        // Count total relics for logging
        int totalRelicRewards = 0;
        for (RewardItem r : rewards) {
            if (r.type == RewardItem.RewardType.RELIC) {
                totalRelicRewards++;
            }
        }
        Log.info("[" + source + "] Found " + totalRelicRewards + " relic reward(s) in screen");

        // Find all relic rewards that don't already have linked groups
        ArrayList<RewardItem> unlinkedRelics = new ArrayList<>();
        for (RewardItem r : rewards) {
            if (r.type == RewardItem.RewardType.RELIC) {
                ArrayList<RewardItem> existingGroup = RelicLinkFields.linkedRelics.get(r);
                if (existingGroup == null) {
                    // Get tier-specific choice count
                    int tierChoices = PickyRelicsMod.getChoicesForTier(r.relic.tier);
                    if (tierChoices <= 1) {
                        Log.info("[" + source + "] Skipping " + r.relic.tier + " tier relic: " +
                                r.relic.relicId + " (choices=1)");
                        continue;
                    }
                    unlinkedRelics.add(r);
                } else {
                    Log.info("[" + source + "] Relic " + r.relic.relicId + " already has linked group, skipping");
                }
            }
        }

        Log.info("[" + source + "] Processing " + unlinkedRelics.size() + " unlinked relic(s)");

        // Create linked groups for each unlinked relic
        for (RewardItem original : unlinkedRelics) {
            int tierChoices = PickyRelicsMod.getChoicesForTier(original.relic.tier);
            Log.info("[" + source + "] Creating linked group for " + original.relic.relicId +
                    " (tier: " + original.relic.tier + ") with " + tierChoices + " choices");
            createLinkedRelicGroup(rewards, original, tierChoices);
        }

        return unlinkedRelics.size();
    }

}
