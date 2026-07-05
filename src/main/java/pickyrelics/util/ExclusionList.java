package pickyrelics.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * (De)serialization for the user's relic exclusion list, stored in config as a
 * single comma-joined string of relic IDs.
 *
 * Relic IDs are arbitrary strings: vanilla IDs contain spaces ("Bag of Preparation")
 * and modded IDs typically contain colons ("somemod:SomeRelic"), so entries are only
 * trimmed and empty entries dropped. IDs that don't match any loaded relic are
 * preserved as-is — the relic's mod may be temporarily uninstalled, and pruning
 * would silently lose the user's setting.
 *
 * No game-class dependencies, so it stays unit-testable.
 */
public final class ExclusionList {

    private static final String SEPARATOR = ",";

    private ExclusionList() {}

    /**
     * Join relic IDs into a single comma-separated config value.
     */
    public static String serialize(Collection<String> relicIds) {
        StringBuilder sb = new StringBuilder();
        for (String id : relicIds) {
            if (id == null) continue;
            String trimmed = id.trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append(SEPARATOR);
            sb.append(trimmed);
        }
        return sb.toString();
    }

    /**
     * Parse a comma-separated config value into an ordered set of relic IDs.
     * Entries are trimmed; empty entries are dropped. Never returns null.
     */
    public static Set<String> deserialize(String serialized) {
        Set<String> result = new LinkedHashSet<>();
        if (serialized == null || serialized.isEmpty()) {
            return result;
        }
        for (String entry : serialized.split(SEPARATOR)) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
