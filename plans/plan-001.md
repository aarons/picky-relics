# Settings UI Improvements: Header Text & Visual Preview

## Summary

Improve the settings UI clarity by:
1. Updating the header text to a label + explanation format
2. Adding a visual preview that shows what the reward screen will look like

---

## Files to Modify

- `src/main/java/pickyrelics/PickyRelicsMod.java` - Header text, state tracking, preview integration

## Files to Create

- `src/main/java/pickyrelics/ui/RelicChoicePreview.java` - Visual preview component

---

## Implementation Steps

### Step 1: Add State Tracking to PickyRelicsMod.java

Add fields to track which tier is being previewed:

```java
// Preview state tracking
private static AbstractRelic.RelicTier previewTier = AbstractRelic.RelicTier.COMMON;
private static int previewChoiceCount = 2;

public static AbstractRelic.RelicTier getPreviewTier() { return previewTier; }
public static int getPreviewChoiceCount() { return previewChoiceCount; }

private static void updatePreview(AbstractRelic.RelicTier tier, int count) {
    previewTier = tier;
    previewChoiceCount = count;
}
```

### Step 2: Update Header Text

Replace the current single label with a title + explanation:

Current:

**Title:** "Relic Choices"
**Explanation line 1:** "Choose 1 from multiple options when relics drop from combat or chests."
**Explanation line 2:** "Adjust how many options appear for each tier. (Does not add extra relics.)"

Issues:
- a title is a little odd to have in the settings menu, it's not necessary
- the explanation lines are very long yet a bit unclear

Option A:
**Title:** "Relic Choices"
**Explanation line 1:** "For each type of relic, how many options should you get to choose from?"
**Explanation line 2:** "This only affects relics found in combat rewards or chests."

Option B:
"When you get a relic after combat or in a chest, how many options do you want to have?"

Option C:
"How many relic choices should be provided in combat and chest rewards?"



- Title uses `FontHelper.charDescFont` in `Settings.CREAM_COLOR`
- Explanation uses `FontHelper.tipBodyFont` in `Settings.GOLD_COLOR`

### Step 3: Update Slider Callbacks

Each slider's onChange callback should also call `updatePreview()`:

```java
// Example for Common tier
(val) -> {
    commonChoices = val;
    saveConfig();
    updatePreview(AbstractRelic.RelicTier.COMMON, val);
}
```

### Step 4: Create RelicChoicePreview.java

New `IUIElement` implementation that renders a preview on the right side of the panel.

**Structure:**
- Position: `x = 920.0f`, `y = contentY` (right side of panel)
- Uses `Supplier<RelicTier>` and `Supplier<Integer>` to get current state
- Renders:
  - Preview title: "Preview: {TierName}"
  - For each choice (1 to N):
    - Chain icon (`ImageMaster.RELIC_LINKED`) between items (not above first)
    - Generic relic silhouette (simple "?" text in tier color)
    - Tier label (e.g., "Common Relic")

**Key rendering details:**
- Use tier colors from `RelicLinkPatch.getTierColor()` pattern (already exists at lines 58-69)
- Use tier names from `RelicLinkPatch.getTierDisplayText()` pattern (lines 42-52)
- Chain icon: `ImageMaster.RELIC_LINKED` texture, scaled to ~50% for preview
- All positions scaled with `Settings.scale`

**Silhouette approach:** Render "?" text in a colored circle using tier color. Simple, no additional assets needed.

### Step 5: Integrate Preview into Settings Panel

```java
// After creating sliders, add preview on right side
float previewX = 920.0f;
float previewY = contentY;

RelicChoicePreview preview = new RelicChoicePreview(
    previewX, previewY,
    PickyRelicsMod::getPreviewTier,
    PickyRelicsMod::getPreviewChoiceCount
);

addPagedElement(settingsPanel, PAGE_CHOICES, preview);
```

---

## Visual Layout

```
┌──────────────────────────────────────────────────────────────────┐
│                    << page 1 of 2 >>                             │
│                                                                  │
│  Relic Choices                                                   │
│  Choose 1 from multiple options when relics                      │
│  drop from combat or chests.                                     │
│  Adjust how many options appear for each tier.                   │
│  (Does not add extra relics.)                                    │
│                                                                  │
│  Starter (X)     [====2====]              Preview: Common        │
│  Common (X)      [====3====]                 [?] Common Relic    │
│  Uncommon (X)    [====2====]                      ⛓              │
│  Rare (X)        [====2====]                 [?] Common Relic    │
│  Shop (X)        [====2====]                      ⛓              │
│  Event (X)       [====2====]                 [?] Common Relic    │
│  Boss (X)        [====2====]                                     │
│                                                                  │
│  A value of 1 has the same behavior as the base game.            │
│                                                                  │
│  [ ] Show relic tier labels on reward screen                     │
└──────────────────────────────────────────────────────────────────┘
```

---

## Reference Code

**PageNavigator.java** (lines 50-66) - Pattern for `render()` with `SpriteBatch` and `Settings.scale`

**RelicLinkPatch.java**:
- Lines 42-52: `getTierDisplayText()` - tier name strings
- Lines 58-69: `getTierColor()` - tier colors
- Line 328: Chain icon rendering via `renderRelicLink`

---

## Testing

1. Build with `mvn clean package`
2. Verify header text displays correctly
3. Adjust each tier slider and confirm preview updates
4. Verify chain icons appear between items (not above first)
5. Test on different resolutions to verify scaling
