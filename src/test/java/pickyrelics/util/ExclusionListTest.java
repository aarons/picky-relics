package pickyrelics.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The exclusion list is stored in config as one comma-joined string of relic IDs.
 * IDs are arbitrary: vanilla IDs contain spaces ("Bag of Preparation"), modded IDs
 * contain colons ("somemod:SomeRelic"), and IDs from uninstalled mods must survive
 * load/save round-trips so the user's setting isn't silently lost.
 */
public class ExclusionListTest {

    @Test
    public void roundTripPreservesIdsWithSpacesColonsAndUnknownMods() {
        Set<String> original = new LinkedHashSet<>(Arrays.asList(
                "Bag of Preparation",          // vanilla ID with spaces
                "somemod:SomeRelic",           // modded ID with colon
                "uninstalledmod:GoneRelic"));  // not in RelicLibrary - must not be pruned

        Set<String> restored = ExclusionList.deserialize(ExclusionList.serialize(original));

        assertEquals(original, restored);
    }

    @Test
    public void deserializeOfEmptyOrNullConfigValueYieldsEmptySet() {
        assertTrue(ExclusionList.deserialize("").isEmpty());
        assertTrue(ExclusionList.deserialize(null).isEmpty());
    }

    @Test
    public void deserializeTrimsEntriesAndDropsEmptyOnes() {
        // Defensive parsing of a hand-edited config value
        Set<String> restored = ExclusionList.deserialize(" Anchor , ,, somemod:SomeRelic ,");

        assertEquals(new LinkedHashSet<>(Arrays.asList("Anchor", "somemod:SomeRelic")), restored);
    }
}
