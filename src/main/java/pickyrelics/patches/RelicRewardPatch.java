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
 * Patches to provide multiple relic choices.
 * When a relic reward is added, this creates additional relic reward options.
 */
public class RelicRewardPatch {

    private static final Logger logger = LogManager.getLogger(RelicRewardPatch.class.getName());

    // Track which relic rewards we've already processed to avoid infinite loops
    private static final ArrayList<RewardItem> processedRewards = new ArrayList<>();

    /**
     * Patch AbstractRoom.addRelicToRewards to add additional relic choices.
     */
    @SpirePatch(
            clz = AbstractRoom.class,
            method = "addRelicToRewards",
            paramtypez = {AbstractRelic.class}
    )
    public static class AddRelicToRewardsPatch {

        @SpirePostfixPatch
        public static void Postfix(AbstractRoom __instance, AbstractRelic relic) {
            if (!shouldApplyPatch(__instance)) {
                return;
            }

            int numChoices = PickyRelicsMod.numChoices;
            if (numChoices <= 1) {
                return;
            }

            logger.info("Picky Relics: Adding " + (numChoices - 1) + " additional relic choices");

            // Add additional relic rewards of the same tier
            AbstractRelic.RelicTier tier = relic.tier;
            for (int i = 1; i < numChoices; i++) {
                AbstractRelic additionalRelic = AbstractDungeon.returnRandomRelic(tier);
                __instance.rewards.add(new RewardItem(additionalRelic));
            }
        }
    }

    /**
     * Determines if the patch should apply based on the current room type.
     */
    private static boolean shouldApplyPatch(AbstractRoom room) {
        if (room == null) {
            return false;
        }

        // Apply to monster rooms (elite/regular combat rewards)
        if (room instanceof MonsterRoom || room instanceof MonsterRoomElite) {
            return true;
        }

        // Don't apply to boss rooms - they already have their own relic selection
        if (room instanceof MonsterRoomBoss) {
            return false;
        }

        // Apply to treasure rooms (chests) if enabled
        if (room instanceof TreasureRoom || room instanceof TreasureRoomBoss) {
            return PickyRelicsMod.applyToChests;
        }

        // Apply to event rooms if enabled
        if (room instanceof EventRoom) {
            return PickyRelicsMod.applyToEvents;
        }

        return false;
    }
}
