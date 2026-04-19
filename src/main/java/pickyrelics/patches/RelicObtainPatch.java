package pickyrelics.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.util.RelicPoolTracker;

/**
 * Record every relic the player picks up so cycling never re-offers a relic that
 * was ever obtained — even if it was later consumed, swapped, or transformed.
 */
public class RelicObtainPatch {

    @SpirePatch(clz = AbstractRelic.class, method = "instantObtain", paramtypez = {})
    public static class NoArgs {
        @SpirePostfixPatch
        public static void Postfix(AbstractRelic __instance) {
            RelicPoolTracker.recordObtained(__instance.relicId);
        }
    }

    @SpirePatch(
            clz = AbstractRelic.class,
            method = "instantObtain",
            paramtypez = { AbstractPlayer.class, int.class, boolean.class }
    )
    public static class ThreeArgs {
        @SpirePostfixPatch
        public static void Postfix(AbstractRelic __instance, AbstractPlayer p, int slot, boolean callOnEquip) {
            RelicPoolTracker.recordObtained(__instance.relicId);
        }
    }
}
