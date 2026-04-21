package pickyrelics.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatches;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.util.RelicPoolTracker;

/**
 * If the live pool for {@code tier} is empty and the cycle-pools setting is on,
 * refill it with snapshot relics the player hasn't obtained before the game reads it.
 *
 * Patches both pool-key accessors in {@link AbstractDungeon}:
 * <ul>
 *     <li>{@code returnRandomRelicKey} — combat/event path; every reward helper
 *     ({@code returnRandomRelic}, {@code returnRandomNonCampfireRelic},
 *     {@code returnRandomScreenlessRelic}) and the cascade fallback funnels through it.</li>
 *     <li>{@code returnEndRandomRelicKey} — shop path, reached via
 *     {@code returnRandomRelicEnd} from {@code ShopScreen.initRelics}. Has its own
 *     empty-pool fallback that hardcodes Circlet/Red Circlet, so it needs its own hook.</li>
 * </ul>
 */
@SpirePatches({
        @SpirePatch(
                clz = AbstractDungeon.class,
                method = "returnRandomRelicKey",
                paramtypez = { AbstractRelic.RelicTier.class }
        ),
        @SpirePatch(
                clz = AbstractDungeon.class,
                method = "returnEndRandomRelicKey",
                paramtypez = { AbstractRelic.RelicTier.class }
        )
})
public class RelicPoolRefillPatch {
    @SpirePrefixPatch
    public static void Prefix(AbstractRelic.RelicTier tier) {
        RelicPoolTracker.refillIfEmpty(tier);
    }
}
