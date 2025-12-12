package pickyrelics.patches;

import basemod.ReflectionHacks;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.screens.CombatRewardScreen;
import pickyrelics.PickyRelicsMod;
import pickyrelics.util.Log;
import pickyrelics.util.TierUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Patches to link relic rewards together so claiming one removes the others.
 * Follows the pattern used by Orison mod's RewardLinkPatch.
 */
public class RelicLinkPatch {

    // Lazy-loaded localized strings for tooltips
    private static UIStrings tooltipStrings;
    private static String[] TEXT;

    private static void ensureStringsLoaded() {
        if (tooltipStrings == null) {
            tooltipStrings = CardCrawlGame.languagePack.getUIString(PickyRelicsMod.makeID("Tooltip"));
            TEXT = tooltipStrings.TEXT;
        }
    }

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
        // Whether this reward was processed during the PostBattle phase (to avoid double-processing)
        public static SpireField<Boolean> processedInPostBattle = new SpireField<>(() -> false);
    }

    /**
     * Calculate a potentially modified tier using game's seeded RNG.
     * Wrapper around TierUtils.calculateModifiedTier with game-specific RNG.
     */
    private static AbstractRelic.RelicTier calculateModifiedTier(AbstractRelic.RelicTier originalTier) {
        return TierUtils.calculateModifiedTier(
                originalTier,
                chance -> AbstractDungeon.relicRng.randomBoolean(chance / 100.0f) ? 1 : 0,
                () -> (double) AbstractDungeon.relicRng.random()
        );
    }

    /**
     * Get the ordered list of fallback tiers for a given tier when the pool is exhausted.
     * Uses a cascade approach: try adjacent tiers first, then expand.
     *
     * @param tier The original tier that was exhausted
     * @return Array of tiers to try in order
     */
    private static AbstractRelic.RelicTier[] getFallbackTiers(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case COMMON:
                return new AbstractRelic.RelicTier[] {
                    AbstractRelic.RelicTier.UNCOMMON,
                    AbstractRelic.RelicTier.RARE
                };
            case UNCOMMON:
                return new AbstractRelic.RelicTier[] {
                    AbstractRelic.RelicTier.COMMON,
                    AbstractRelic.RelicTier.RARE
                };
            case RARE:
                return new AbstractRelic.RelicTier[] {
                    AbstractRelic.RelicTier.UNCOMMON,
                    AbstractRelic.RelicTier.COMMON
                };
            case BOSS:
            case SHOP:
            case STARTER:
            default:
                // For non-standard tiers, try Common -> Uncommon -> Rare
                return new AbstractRelic.RelicTier[] {
                    AbstractRelic.RelicTier.COMMON,
                    AbstractRelic.RelicTier.UNCOMMON,
                    AbstractRelic.RelicTier.RARE
                };
        }
    }

    /**
     * Get a random relic from the specified tier, with fallback to other tiers
     * if the pool is exhausted (returns Circlet).
     *
     * @param tier The preferred tier
     * @return A relic from the preferred tier or a fallback tier, or Circlet if all exhausted
     */
    private static AbstractRelic getRelicWithFallback(AbstractRelic.RelicTier tier) {
        // Try the requested tier first
        AbstractRelic relic = AbstractDungeon.returnRandomRelic(tier);
        if (!"Circlet".equals(relic.relicId)) {
            return relic;
        }

        Log.debug("Picky Relics: " + tier + " pool exhausted, trying fallback tiers");

        // Get fallback order based on tier
        AbstractRelic.RelicTier[] fallbacks = getFallbackTiers(tier);

        for (AbstractRelic.RelicTier fallbackTier : fallbacks) {
            relic = AbstractDungeon.returnRandomRelic(fallbackTier);
            if (!"Circlet".equals(relic.relicId)) {
                Log.debug("Picky Relics: Using fallback tier " + fallbackTier);
                return relic;
            }
        }

        // All pools exhausted
        Log.debug("Picky Relics: All fallback tiers exhausted");
        return relic; // Will be Circlet
    }

    /**
     * Check if this rewards list is AbstractRoom.rewards (vs CombatRewardScreen.rewards).
     * PostBattle adds to AbstractRoom.rewards, so BaseMod handles positioning automatically.
     * setupItemReward patch adds to CombatRewardScreen.rewards, so we need manual positioning.
     *
     * @param rewards The rewards list to check
     * @return true if this is AbstractRoom.rewards, false otherwise
     */
    private static boolean isInAbstractRoomRewards(ArrayList<RewardItem> rewards) {
        return AbstractDungeon.getCurrRoom() != null &&
               AbstractDungeon.getCurrRoom().rewards == rewards;
    }

    /**
     * Select a random relic from C/U/R pools.
     * Used for additional event relic choices since event relics have special requirements.
     *
     * @return A relic from C/U/R pools, or null if all pools exhausted
     */
    private static AbstractRelic getRandomNonEventRelic() {
        // Standard tiers to try for event relic alternatives
        List<AbstractRelic.RelicTier> tiers = new ArrayList<>();
        tiers.add(AbstractRelic.RelicTier.COMMON);
        tiers.add(AbstractRelic.RelicTier.UNCOMMON);
        tiers.add(AbstractRelic.RelicTier.RARE);

        // Randomly pick starting tier for fairness
        int startIndex = AbstractDungeon.relicRng.random(tiers.size() - 1);

        // Try each tier in random order
        for (int i = 0; i < tiers.size(); i++) {
            AbstractRelic.RelicTier tierToTry = tiers.get((startIndex + i) % tiers.size());
            AbstractRelic relic = AbstractDungeon.returnRandomRelic(tierToTry);
            if (!"Circlet".equals(relic.relicId)) {
                return relic;
            }
        }

        // All pools exhausted
        Log.debug("Picky Relics: All pools exhausted for event relic");
        return null;
    }

    /**
     * Render tier label in bottom-right corner of reward item.
     */
    private static void renderTierLabel(RewardItem reward, SpriteBatch sb) {
        if (!PickyRelicsMod.showTierLabels) return;

        String tierText = TierUtils.getTierDisplayText(reward.relic.tier);
        if (tierText.isEmpty()) return;

        Color tierColor = TierUtils.getTierColor(reward.relic.tier);
        // Reduce brightness by 10% for subtler appearance
        Color dimmedColor = tierColor.cpy();
        dimmedColor.r *= 0.9F;
        dimmedColor.g *= 0.9F;
        dimmedColor.b *= 0.9F;

        // Position at right edge of the reward hitbox (with small margin)
        float x = reward.hb.x + reward.hb.width - 15.0F * Settings.scale;
        float y = reward.hb.y + 18.0F * Settings.scale + FontHelper.tipBodyFont.getLineHeight() * 0.2F;

        // Calculate text width for right-aligned rendering
        FontHelper.layout.setText(FontHelper.tipBodyFont, tierText);
        float textX = x - FontHelper.layout.width;

        // Render text without shadow
        FontHelper.tipBodyFont.setColor(dimmedColor);
        FontHelper.tipBodyFont.draw(sb, tierText, textX, y);
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
            AbstractRelic additionalRelic;

            if (tier == AbstractRelic.RelicTier.SPECIAL) {
                // Event tier: additional choices come from enabled C/U/R pools
                additionalRelic = getRandomNonEventRelic();
                if (additionalRelic == null) {
                    Log.debug("Picky Relics: No valid tier available for event relic, skipping");
                    continue;
                }
            } else {
                // Normal tier: use tier modification
                AbstractRelic.RelicTier tierToUse = calculateModifiedTier(tier);
                if (tierToUse != tier) {
                    Log.debug("Picky Relics: Tier changed from " + tier + " to " + tierToUse);
                }
                additionalRelic = getRelicWithFallback(tierToUse);
            }

            // Skip Circlet - pool exhausted
            if ("Circlet".equals(additionalRelic.relicId)) {
                Log.debug("Picky Relics: Relic pool exhausted, skipping");
                continue;
            }

            RewardItem newReward = new RewardItem(additionalRelic);
            RelicLinkFields.addedByPickyRelics.set(newReward, true);
            rewards.add(insertIndex, newReward);

            // Only manually position if we're NOT in AbstractRoom.rewards.
            // PostBattle adds to AbstractRoom.rewards, which gets auto-positioned by setupItemReward().
            // The setupItemReward patch adds to CombatRewardScreen.rewards after animation, so needs manual positioning.
            if (!isInAbstractRoomRewards(rewards)) {
                float yPos = (float)Settings.HEIGHT / 2.0F - 124.0F * Settings.scale
                             - (float)insertIndex * 100.0F * Settings.scale;
                newReward.move(yPos);
            }

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

            Log.debug("Picky Relics: Relic claimed, marking " + (linked.size() - 1) + " linked relics as done");

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
     * Render tier label and chain icon for relic rewards.
     */
    @SpirePatch2(clz = RewardItem.class, method = "render")
    public static class RenderLinkPatch {
        @SpirePostfixPatch
        public static void Postfix(RewardItem __instance, SpriteBatch sb) {
            if (__instance.type != RewardItem.RewardType.RELIC) return;

            // Render tier label for all relic rewards
            if (__instance.relic != null) {
                renderTierLabel(__instance, sb);
            }

            // Render chain icon and tooltip for linked groups
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
                ensureStringsLoaded();
                String title = TEXT[0];
                String body = String.format(TEXT[1], linked.size() - 1);
                TipHelper.renderGenericTip(
                        360.0F * Settings.scale,
                        InputHelper.mY + 50.0F * Settings.scale,
                        title, body);
            }
        }
    }

    /**
     * Fallback hook: Process relic rewards for non-combat sources (events, chests).
     * PostBattleSubscriber handles combat rewards, but events and chests don't trigger
     * that hook, so this catches them when the reward screen is displayed.
     *
     * Relics already processed by PostBattle are skipped via the processedInPostBattle field.
     */
    @SpirePatch2(clz = CombatRewardScreen.class, method = "setupItemReward")
    public static class ProcessRelicRewardsOnSetup {
        @SpirePostfixPatch
        public static void Postfix(CombatRewardScreen __instance) {
            processRelicRewards(__instance.rewards, "SETUP");
        }
    }

    /**
     * Safety net hook: Catches relics added after setupItemReward() by other mods.
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
                Log.debug("[UPDATE] Found unlinked relic(s) added after setup, processing...");
                int sizeBefore = __instance.rewards.size();
                int processed = processRelicRewards(__instance.rewards, "UPDATE");

                // If we added relics, reposition everything
                if (__instance.rewards.size() != sizeBefore) {
                    Log.debug("[UPDATE] Repositioning rewards after adding " +
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
     *                (e.g., "PostBattle", "SETUP")
     * @return The number of relics that were processed (had linked groups created)
     */
    public static int processRelicRewards(ArrayList<RewardItem> rewards, String source) {
        boolean isPostBattle = "PostBattle".equals(source);

        // Count total relics for logging
        int totalRelicRewards = 0;
        for (RewardItem r : rewards) {
            if (r.type == RewardItem.RewardType.RELIC) {
                totalRelicRewards++;
            }
        }
        Log.debug("[" + source + "] Found " + totalRelicRewards + " relic reward(s)");

        // Find all relic rewards that don't already have linked groups
        ArrayList<RewardItem> unlinkedRelics = new ArrayList<>();
        for (RewardItem r : rewards) {
            if (r.type == RewardItem.RewardType.RELIC && r.relic != null) {
                // Skip relics already processed in PostBattle (to avoid double-processing in SETUP)
                if (!isPostBattle && RelicLinkFields.processedInPostBattle.get(r)) {
                    Log.debug("[" + source + "] Skipping " + r.relic.relicId +
                            " (already processed in PostBattle)");
                    continue;
                }

                ArrayList<RewardItem> existingGroup = RelicLinkFields.linkedRelics.get(r);
                if (existingGroup == null) {
                    // Get tier-specific choice count
                    int tierChoices = PickyRelicsMod.getChoicesForTier(r.relic.tier);
                    if (tierChoices <= 1) {
                        Log.debug("[" + source + "] Skipping " + r.relic.tier + " tier relic: " +
                                r.relic.relicId + " (choices=1)");
                        continue;
                    }
                    unlinkedRelics.add(r);
                } else {
                    Log.debug("[" + source + "] Relic " + r.relic.relicId + " already has linked group, skipping");
                }
            }
        }

        Log.debug("[" + source + "] Processing " + unlinkedRelics.size() + " unlinked relic(s)");

        // Create linked groups for each unlinked relic
        for (RewardItem original : unlinkedRelics) {
            int tierChoices = PickyRelicsMod.getChoicesForTier(original.relic.tier);
            Log.debug("[" + source + "] Creating linked group for " + original.relic.relicId +
                    " (tier: " + original.relic.tier + ") with " + tierChoices + " choices");
            createLinkedRelicGroup(rewards, original, tierChoices);

            // Mark as processed in PostBattle to prevent double-processing in SETUP
            if (isPostBattle) {
                RelicLinkFields.processedInPostBattle.set(original, true);
            }
        }

        return unlinkedRelics.size();
    }

}
