# Loot Preview Polish - Next Steps

## Current State
The loot preview component is functional with a dark background, scroll banner header, item panels, and chain icons linking items together. However, several visual issues remain.

## Issues to Fix

### 1. Circlet Placeholder Icons Too Small/Missing
The Circlet relic icons on the left side of each item panel appear very small or not rendering properly. They should be more visible as the "placeholder" relic icons.

**Investigate:**
- Check if `ICON_SIZE` (48.0f) is appropriate (try 64.0f)
- May need to adjust the icon's position to the right a bit, to be slightly closer to the type of relic text
- May need the REWARD_SCREEN_ITEM background color to be slightly darker (or add 15% alpha transparency) for less contrast

### 2. Relic Silhuoette should be gray
The relic silhuoette should not match the tier color, but rather should be a consistent dark gray.
Look in renderSilhouette `sb.setColor(tierColor);`


## Files Modified in This Session
- `src/main/java/pickyrelics/ui/RelicChoicePreview.java` - Main preview component
- `src/main/java/pickyrelics/PickyRelicsMod.java` - Preview X position (line 263)
- `src/main/resources/pickyrelics/chain_icon.png` - Custom chain icon (needs transparency fix)

## Additional context

Key Constants can be found in RelicChoicePreview.java
