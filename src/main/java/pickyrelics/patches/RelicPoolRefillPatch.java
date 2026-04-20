package pickyrelics.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.util.RelicPoolTracker;

/**
 * If the live pool for {@code tier} is empty and the cycle-pools setting is on,
 * refill it with snapshot relics the player hasn't obtained before the game reads it.
 *
 * Patches {@code returnRandomRelicKey} — the innermost pool accessor that every
 * relic-draw helper ({@code returnRandomRelic}, {@code returnRandomNonCampfireRelic},
 * {@code returnRandomScreenlessRelic}) and the cascade fallback all route through.
 */
@SpirePatch(
        clz = AbstractDungeon.class,
        method = "returnRandomRelicKey",
        paramtypez = { AbstractRelic.RelicTier.class }
)
public class RelicPoolRefillPatch {
    @SpirePrefixPatch
    public static void Prefix(AbstractRelic.RelicTier tier) {
        RelicPoolTracker.refillIfEmpty(tier);
    }
}
