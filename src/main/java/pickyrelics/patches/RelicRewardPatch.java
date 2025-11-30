package pickyrelics.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.*;
import javassist.CtBehavior;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pickyrelics.PickyRelicsMod;

import java.util.ArrayList;

/**
 * Patches the RewardItem constructor for relics to provide multiple choices
 * similar to boss relic rewards.
 */
public class RelicRewardPatch {

    private static final Logger logger = LogManager.getLogger(RelicRewardPatch.class.getName());

    /**
     * Patch the RewardItem constructor that takes a single AbstractRelic.
     * This is called when creating relic rewards from chests, events, and combat.
     */
    @SpirePatch(
            clz = RewardItem.class,
            method = SpirePatch.CONSTRUCTOR,
            paramtypez = {AbstractRelic.class}
    )
    public static class SingleRelicRewardPatch {

        @SpirePostfixPatch
        public static void Postfix(RewardItem __instance, AbstractRelic relic) {
            if (!shouldApplyPatch()) {
                return;
            }

            int numChoices = PickyRelicsMod.numChoices;
            if (numChoices <= 1) {
                return; // No choices needed, just the original relic
            }

            logger.info("Picky Relics: Creating " + numChoices + " relic choices");

            // Convert to a linked relic reward (like boss relics)
            __instance.relicLink = new RewardItem.RewardLinkedList();

            // The original relic is already set in __instance.relic
            // We need to add additional choices
            for (int i = 1; i < numChoices; i++) {
                AbstractRelic additionalRelic = AbstractDungeon.returnRandomRelic(
                        getRelicTier(relic)
                );
                __instance.relicLink.add(additionalRelic);
            }
        }

        private static AbstractRelic.RelicTier getRelicTier(AbstractRelic relic) {
            // Return the same tier as the original relic
            return relic.tier;
        }
    }

    /**
     * Determines if the patch should apply based on the current room type
     * and config settings.
     */
    private static boolean shouldApplyPatch() {
        if (AbstractDungeon.getCurrRoom() == null) {
            return false;
        }

        AbstractRoom room = AbstractDungeon.getCurrRoom();

        // Always apply to monster rooms (elite/regular combat rewards)
        if (room instanceof MonsterRoom || room instanceof MonsterRoomElite || room instanceof MonsterRoomBoss) {
            // Don't apply to boss rooms - they already have 3 choices for boss relics
            if (room instanceof MonsterRoomBoss) {
                return false;
            }
            return true;
        }

        // Apply to treasure rooms (chests) if enabled
        if (room instanceof TreasureRoom || room instanceof TreasureRoomBoss) {
            return PickyRelicsMod.applyToChests;
        }

        // Apply to event rooms if enabled
        if (room instanceof EventRoom) {
            return PickyRelicsMod.applyToEvents;
        }

        // Apply to shop rooms for relic purchases? Probably not by default
        // if (room instanceof ShopRoom) { return false; }

        return false;
    }
}
