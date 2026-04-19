package pickyrelics.devcommands;

import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.util.RelicPoolTracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Developer command: {@code pickypool show|empty <tier>}.
 * <ul>
 *     <li>{@code show} dumps the live pool, snapshot size, and ever-obtained count for a tier.</li>
 *     <li>{@code empty} clears the live pool so the next draw exercises the cycle refill.</li>
 * </ul>
 */
public class PickyPoolCommand extends ConsoleCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList("show", "empty");
    private static final List<String> TIER_NAMES = Arrays.asList(
            "common", "uncommon", "rare", "shop", "boss");

    public PickyPoolCommand() {
        this.requiresPlayer = true;
        this.minExtraTokens = 2;
        this.maxExtraTokens = 2;
    }

    @Override
    protected void execute(String[] tokens, int depth) {
        String subcommand = tokens[1].toLowerCase(Locale.ROOT);
        AbstractRelic.RelicTier tier = parseTier(tokens[2]);
        if (tier == null) {
            DevConsole.log("Unknown tier: " + tokens[2] + " (expected common/uncommon/rare/shop/boss)");
            return;
        }

        ArrayList<String> livePool = RelicPoolTracker.livePoolFor(tier);
        if (livePool == null) {
            DevConsole.log("Tier " + tier + " has no pool");
            return;
        }

        switch (subcommand) {
            case "show":
                DevConsole.log(tier + " live pool (" + livePool.size() + "): " + livePool);
                DevConsole.log(tier + " snapshot size: " + RelicPoolTracker.snapshotFor(tier).size()
                        + ", everObtained total: " + RelicPoolTracker.obtainedCount());
                break;
            case "empty":
                int cleared = livePool.size();
                livePool.clear();
                DevConsole.log(tier + " live pool cleared (" + cleared + " removed)");
                break;
            default:
                DevConsole.log("Unknown subcommand: " + subcommand + " (expected show or empty)");
        }
    }

    @Override
    protected ArrayList<String> extraOptions(String[] tokens, int depth) {
        if (depth == 1) return new ArrayList<>(SUB_COMMANDS);
        if (depth == 2) return new ArrayList<>(TIER_NAMES);
        return new ArrayList<>();
    }

    @Override
    public void errorMsg() {
        DevConsole.log("Usage: pickypool show|empty <common|uncommon|rare|shop|boss>");
    }

    private static AbstractRelic.RelicTier parseTier(String token) {
        switch (token.toLowerCase(Locale.ROOT)) {
            case "common":   return AbstractRelic.RelicTier.COMMON;
            case "uncommon": return AbstractRelic.RelicTier.UNCOMMON;
            case "rare":     return AbstractRelic.RelicTier.RARE;
            case "shop":     return AbstractRelic.RelicTier.SHOP;
            case "boss":     return AbstractRelic.RelicTier.BOSS;
            default:         return null;
        }
    }
}
