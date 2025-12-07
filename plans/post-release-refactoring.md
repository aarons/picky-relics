# Post-Release Refactoring Plan

Items deferred from v1.0 release for future consideration.

## Architectural Improvements

### 1. Split PickyRelicsMod.java
**Current:** 670+ lines handling config, UI, tier calculation, relic selection, preview state.

**Proposed split:**
- `PickyRelicsMod.java` - Main mod class, config loading/saving only
- `ConfigManager.java` - Configuration handling and persistence
- `SettingsUI.java` - ModPanel setup and UI components
- `PreviewManager.java` - Preview relic selection and state

### 2. Restructure Static Configuration State
**Current:** 15+ public static fields for configuration.

**Issue:** Makes testing difficult, creates global mutable state.

**Options:**
- Create a `ModConfig` object that encapsulates all settings
- Use dependency injection pattern
- Keep static fields but make private with getters

### 3. Add Texture Disposal
**File:** `RelicChoicePreview.java`

**Issue:** Static textures (`chainTexture`, `bannerTexture`) are loaded once and never disposed.

**Impact:** Minor memory leak if mod is reloaded during development.

**Fix:** Implement disposal hooks or use BaseMod's resource management.

### 4. Remove Legacy Migration Code
**File:** `PickyRelicsMod.java` (lines ~389-422)

**Description:** Migration code for old enum-based tier direction config.

**Timeline:** Remove after 2-3 releases when most users have migrated.

## Code Quality

### 5. Add Unit Tests
**Priority:** Low

**Coverage needed:**
- Tier calculation logic (TierUtils)
- Config migration
- Relic selection algorithms

### 6. Extract More UI Constants
**File:** `PickyRelicsMod.java`

Remaining magic numbers in UI code that could be named constants:
- Preview position offsets
- Checkbox spacing values
- Event text line spacing

## Notes

These items are non-critical for v1.0. The codebase is functional and maintainable.
Prioritize based on user feedback and bug reports post-release.
