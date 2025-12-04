# Add Mod Badge Image

Replace the programmatically generated placeholder badge with a proper image file for a polished appearance in the Slay the Spire mod list.

## Context

Currently, the mod creates a simple 32x32 gold square badge at runtime using libGDX's Pixmap API (`PickyRelicsMod.java:226-237`). This works functionally but looks generic in the mod list alongside other mods that have custom artwork.

The badge appears in:
- ModTheSpire's mod selection screen
- The in-game Mods menu (accessed via the cog icon)

## Implementation Notes

**Badge Requirements:**
- Size: 32x32 pixels (standard mod badge size)
- Format: PNG with transparency support
- Location: `src/main/resources/pickyrelics/badge.png`

**Current Code to Replace:**

In `src/main/java/pickyrelics/PickyRelicsMod.java`, the `createBadgeTexture()` method (lines 226-237) generates the badge programmatically:

```java
private Texture createBadgeTexture() {
    com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(32, 32, ...);
    pixmap.setColor(Color.GOLD);
    pixmap.fill();
    // ...
    return texture;
}
```

This should be replaced with loading an image file using BaseMod's texture utilities or libGDX's standard texture loading.

**Loading Pattern:**

```java
import com.badlogic.gdx.graphics.Texture;

Texture badgeTexture = new Texture("pickyrelics/badge.png");
```

## Suggested Approach

1. Create a 32x32 badge image that visually represents "relic choice" (e.g., multiple relics with a selection indicator, or a relic with branching paths)
2. Save as `src/main/resources/pickyrelics/badge.png`
3. Replace `createBadgeTexture()` with a simple texture load
4. Remove the Pixmap-based generation code

**Design Ideas:**
- A relic icon with a "2" or "?" overlay
- Multiple small relic silhouettes
- A relic with branching arrows
- Keep the gold color scheme to match Slay the Spire's aesthetic

## Testing & Validation

1. Build the mod with `mvn clean package`
2. Load the mod in Slay the Spire via ModTheSpire
3. Verify the badge appears correctly in:
   - ModTheSpire's mod selection screen
   - The in-game Mods menu
4. Confirm no texture loading errors in the log

## Documentation

No documentation updates required. The `ModTheSpire.json` does not reference the badge path (it's loaded by convention from the mod's resources).
