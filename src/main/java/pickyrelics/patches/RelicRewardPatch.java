package pickyrelics.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pickyrelics.PickyRelicsMod;

/**
 * Patches to provide multiple relic choices.
 * When a relic reward is added, this creates additional relic reward options
 * and links them together so picking one removes the others.
 */
public class RelicRewardPatch {

    private static final Logger logger = LogManager.getLogger(RelicRewardPatch.class.getName());

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
            int totalChoices = getTotalChoices(__instance);
            if (totalChoices <= 1) {
                return;
            }

            // Skip SPECIAL tier relics when config is enabled (mod compatibility)
            if (relic.tier == AbstractRelic.RelicTier.SPECIAL) {
                if (PickyRelicsMod.ignoreSpecialTier) {
                    logger.info("Picky Relics: Skipping SPECIAL tier relic: " + relic.relicId);
                    return;
                } else {
                    logger.info("Picky Relics: SPECIAL tier relic " + relic.relicId +
                            " - ignoreSpecialTier is disabled, adding choices");
                }
            }

            // Find the reward item that was just added for this relic
            RewardItem originalReward = null;
            for (int i = __instance.rewards.size() - 1; i >= 0; i--) {
                RewardItem r = __instance.rewards.get(i);
                if (r.type == RewardItem.RewardType.RELIC && r.relic == relic) {
                    originalReward = r;
                    break;
                }
            }
            if (originalReward == null) {
                return;
            }

            logger.info("Picky Relics: Adding " + (totalChoices - 1) + " additional relic choices");

            RelicLinkPatch.createLinkedRelicGroup(__instance.rewards, originalReward, totalChoices);
        }
    }

    /**
     * Returns the total number of relic choices for the given room type.
     * Returns 1 (base game) if the patch should not apply.
     */
    private static int getTotalChoices(AbstractRoom room) {
        if (room == null) {
            logger.info("Picky Relics: room is null, returning 1");
            return 1;
        }

        // Log room info for debugging
        String roomClass = room.getClass().getSimpleName();
        String eventInfo = room.event == null
                ? "event=null"
                : "event=" + room.event.getClass().getSimpleName() + ", noCardsInRewards=" + room.event.noCardsInRewards;
        logger.info("Picky Relics: Room detection - " + roomClass + ", " + eventInfo);

        // Treasure chests
        if (room instanceof TreasureRoom || room instanceof TreasureRoomBoss) {
            logger.info("Picky Relics: Detected as treasure room, returning chestChoices=" + PickyRelicsMod.chestChoices);
            return PickyRelicsMod.chestChoices;
        }

        // Boss rooms have their own relic selection
        if (room instanceof MonsterRoomBoss) {
            logger.info("Picky Relics: Detected as boss room, returning 1");
            return 1;
        }

        // Combat-like rooms (catches MonsterRoom, MonsterRoomElite, EventRoom, custom rooms)
        boolean afterCombat = isAfterCombat(room);
        logger.info("Picky Relics: isAfterCombat=" + afterCombat);
        if (afterCombat) {
            logger.info("Picky Relics: Detected as combat room, returning combatChoices=" + PickyRelicsMod.combatChoices);
            return PickyRelicsMod.combatChoices;
        }

        logger.info("Picky Relics: No match, returning 1 (base game behavior)");
        return 1;
    }

    /**
     * Detects if the current room is a combat-like context where relic rewards apply.
     * Uses a blacklist approach: excludes known non-combat rooms, includes everything else.
     * This catches EventRooms with combat and custom mod room types.
     */
    private static boolean isAfterCombat(AbstractRoom room) {
        return (room.event == null || !room.event.noCardsInRewards)
                && !(room instanceof TreasureRoom)
                && !(room instanceof TreasureRoomBoss)
                && !(room instanceof RestRoom)
                && !(room instanceof ShopRoom);
    }
}
