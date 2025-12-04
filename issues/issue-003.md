# Relic Filter Selection UI

Add a user-friendly UI for configuring which relics are filtered by the mod. This builds on the filter backend implemented in issue-002.

## Context

Issue-002 adds the backend for two relic filter lists:
1. **"Never add as option"** - Relics excluded from additional reward choices
2. **"Never offer options for"** - Relics that don't get additional options when awarded

This issue adds the UI to configure those filters without manually editing config files.

**Desired UI flow:**

```
Config Main Menu
├── Relic Choices slider (existing)
├── [Button] "Filter relics from reward pool" → opens filter screen
└── [Button] "Filter relics from getting options" → opens filter screen

Filter Screen (same layout for both filter types)
├── Currently filtered relics (highlighted, shown first)
├── Search box (filters by name/description)
└── Paginated grid of all relics (click to toggle filter status)
```

## Implementation Notes

### Main Menu Additions

Modify `PickyRelicsMod.receivePostInitialize()` to add two buttons below the existing controls:

```java
// Button to open "never add as option" filter screen
ModButton neverAddButton = new ModButton(xPos, yPos,
    new Texture("pickyrelics/button_filter.png"),  // or use ImageMaster texture
    settingsPanel,
    (button) -> openFilterScreen(FilterType.NEVER_ADD_AS_OPTION)
);

// Label next to button
ModLabel neverAddLabel = new ModLabel(
    "Filter relics from reward pool",
    xPos + 60.0f, yPos + 8.0f,
    Settings.CREAM_COLOR,
    FontHelper.tipHeaderFont,
    settingsPanel,
    (label) -> {}
);
```

### Filter Screen Architecture

Create a new class `RelicFilterScreen` that implements the selection UI. Two approaches:

**Option A: Custom IUIElement on ModPanel**
- Add a custom `IUIElement` to the existing `ModPanel`
- Simpler but limited to panel coordinate system
- See `RelicFilterPlus/panelUI/` for this approach

**Option B: Standalone Screen**
- Create a screen similar to the game's `RelicViewScreen`
- More flexible, can use full screen space
- See `Loadout-Mod/screens/RelicSelectScreen.java` for this approach

**Recommendation:** Option A for simplicity, since we're just toggling filters, not selecting relics to add.

### UI Components

**1. Relic Grid with Pagination**

Based on `RelicFilterPlus/panelUI/Pagination.java`:
- 10 columns × 8 rows = 80 relics per page
- Left/right arrow buttons for navigation
- Each relic is a clickable button with hover tooltip

```java
public class RelicFilterPagination implements IUIElement {
    private List<RelicFilterButton> allRelics;
    private List<RelicFilterButton> filteredRelics;  // after search
    private int page = 0;
    private int relicsPerPage = 80;
    private ImageButton nextButton, prevButton;

    public void render(SpriteBatch sb) {
        // Render current page of relics
        int start = page * relicsPerPage;
        int end = Math.min(start + relicsPerPage, filteredRelics.size());
        for (int i = start; i < end; i++) {
            filteredRelics.get(i).render(sb);
        }
        // Render nav buttons if needed
        if (page > 0) prevButton.render(sb);
        if (end < filteredRelics.size()) nextButton.render(sb);
    }
}
```

**2. Relic Button**

Based on `RelicFilterPlus/panelUI/RelicSettingsButton.java`:
- Displays relic image
- Shows outline/highlight when filtered
- Hover shows tooltip with name + description
- Click toggles filter status

```java
public class RelicFilterButton implements IUIElement {
    private AbstractRelic relic;
    private Hitbox hitbox;
    private boolean isFiltered;

    public void update() {
        hitbox.update();
        if (hitbox.hovered && InputHelper.justClickedLeft) {
            toggleFilter();
            CardCrawlGame.sound.play("UI_CLICK_1");
        }
    }

    public void render(SpriteBatch sb) {
        // Draw relic image
        relic.render(sb);
        // Draw highlight if filtered
        if (isFiltered) {
            sb.setColor(GOLD_HIGHLIGHT);
            sb.draw(highlightTexture, ...);
        }
        // Show tooltip on hover
        if (hitbox.hovered) {
            TipHelper.renderGenericTip(x, y, relic.name, relic.description);
        }
    }
}
```

**3. Search Box**

Based on `Loadout-Mod/screens/TextSearchBox.java`:
- Uses BaseMod's `TextReceiver` interface for text input
- Filters relic list as user types

```java
public class RelicSearchBox implements TextReceiver {
    private String searchText = "";
    private Hitbox hitbox;
    private boolean isTyping = false;

    public void update() {
        if (hitbox.clicked) {
            isTyping = true;
            TextInput.startTextReceiver(this);
        }
        if (isTyping && InputHelper.pressedEscape) {
            stopTyping();
        }
    }

    @Override
    public void setText(String s) {
        this.searchText = s;
        parent.updateSearchFilter(s);  // Trigger list refresh
    }
}
```

**4. Search Filtering Logic**

From `Loadout-Mod/screens/RelicSelectScreen.java:179-183`:

```java
private boolean matchesSearch(AbstractRelic relic, String searchText) {
    if (searchText.isEmpty()) return true;
    String lower = searchText.toLowerCase();
    if (relic.name != null && relic.name.toLowerCase().contains(lower)) return true;
    if (relic.description != null && relic.description.toLowerCase().contains(lower)) return true;
    return false;
}
```

### Sorting and Grouping

Display relics in this order:
1. **Currently filtered relics first** (highlighted)
2. **Then by tier**: Starter → Common → Uncommon → Rare → Boss → Shop → Special
3. **Alphabetically within each tier**

Add tier headers between groups (like RelicViewScreen does).

### Getting All Relics

Use `RelicLibrary` to get all available relics:

```java
ArrayList<AbstractRelic> allRelics = new ArrayList<>();
// Add all relics from each tier
allRelics.addAll(RelicLibrary.starterList);
allRelics.addAll(RelicLibrary.commonList);
allRelics.addAll(RelicLibrary.uncommonList);
allRelics.addAll(RelicLibrary.rareList);
allRelics.addAll(RelicLibrary.bossList);
allRelics.addAll(RelicLibrary.shopList);
allRelics.addAll(RelicLibrary.specialList);
```

### Connecting to Backend

The filter screen calls the API from issue-002:

```java
// When user clicks a relic to toggle
private void toggleFilter(String relicId) {
    if (filterType == FilterType.NEVER_ADD_AS_OPTION) {
        if (PickyRelicsMod.shouldNeverAddAsOption(relicId)) {
            PickyRelicsMod.removeFromNeverAddAsOption(relicId);
        } else {
            PickyRelicsMod.addToNeverAddAsOption(relicId);
        }
    } else {
        // Similar for NEVER_OFFER_OPTIONS
    }
    PickyRelicsMod.saveConfig();
}
```

## Reference Implementations

**RelicFilterPlus (`references/RelicFilterPlus/`):**
- `RelicFilterModMenu.java` - Menu structure, pagination setup
- `panelUI/Pagination.java` - Page navigation logic
- `panelUI/RelicSettingsButton.java` - Clickable relic with tooltip, highlight
- `panelUI/ImageButton.java` - Simple image button for nav arrows

**Loadout-Mod (`references/Loadout-Mod/`):**
- `screens/TextSearchBox.java` - Text input using `TextReceiver` interface
- `screens/RelicSelectScreen.java` - Full relic selection with search (lines 179-206)
- `screens/AbstractSelectScreen.java` - Scrolling, pagination base class
- `helper/RelicNameComparator.java` - Alphabetical sorting

## Suggested Approach

1. **Create RelicFilterButton class**
   - Render relic image with highlight for filtered state
   - Handle click to toggle, hover for tooltip

2. **Create RelicFilterPagination class**
   - Grid layout for relic buttons
   - Page navigation

3. **Create RelicFilterScreen class**
   - Combines pagination + search box + close button
   - Loads relics from RelicLibrary
   - Sorts: filtered first, then by tier, then alphabetically

4. **Add search box**
   - Implement TextReceiver interface
   - Filter displayed relics based on search text

5. **Wire up to main menu**
   - Add two buttons to ModPanel
   - Each opens RelicFilterScreen with appropriate filter type

6. **Polish**
   - Tier headers between groups
   - "Clear all" / "Reset to defaults" button
   - Visual feedback when toggling (flash effect)

## Testing & Validation

1. **Navigation:**
   - Open filter screen from main menu
   - Page through relics with arrow buttons
   - Close screen and verify return to main menu

2. **Filtering:**
   - Click relics to toggle filter status
   - Verify highlight appears/disappears
   - Close and reopen screen, verify state persists

3. **Search:**
   - Type in search box
   - Verify list updates to show matching relics
   - Clear search, verify full list returns
   - Search by partial name and description text

4. **Sorting:**
   - Verify filtered relics appear first
   - Verify tier grouping is correct
   - Verify alphabetical order within tiers

5. **Both filter types:**
   - Test "never add as option" filter screen
   - Test "never offer options" filter screen
   - Verify they maintain separate filter lists

6. **Edge cases:**
   - Empty search results
   - All relics filtered
   - Very long relic names (text truncation)
   - Modded relics appear in list

## Documentation

- Update README.md with screenshots of the filter UI
- Add tooltips explaining what each filter does
