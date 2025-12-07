package pickyrelics;

import basemod.BaseMod;
import basemod.IUIElement;
import basemod.ModLabel;
import basemod.ModLabeledToggleButton;
import basemod.ModPanel;
import basemod.ModMinMaxSlider;
import basemod.interfaces.PostInitializeSubscriber;
import com.badlogic.gdx.graphics.Texture;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.ui.DropdownMenu;
import pickyrelics.ui.PagedElement;
import pickyrelics.ui.PageNavigator;
import pickyrelics.ui.RelicChoicePreview;
import pickyrelics.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;

@SpireInitializer
public class PickyRelicsMod implements PostInitializeSubscriber {

    public static final String MOD_ID = "pickyrelics";
    public static final String MOD_NAME = "Picky Relics";

    // Config
    private static SpireConfig config;

    // Config keys
    private static final String CONFIG_SHOW_TIER_LABELS = "showTierLabels";
    private static final String CONFIG_STARTER_CHOICES = "starterChoices";
    private static final String CONFIG_COMMON_CHOICES = "commonChoices";
    private static final String CONFIG_UNCOMMON_CHOICES = "uncommonChoices";
    private static final String CONFIG_RARE_CHOICES = "rareChoices";
    private static final String CONFIG_BOSS_CHOICES = "bossChoices";
    private static final String CONFIG_SHOP_CHOICES = "shopChoices";
    private static final String CONFIG_SPECIAL_CHOICES = "specialChoices";
    private static final String CONFIG_TIER_CHANGE_CHANCE = "tierChangeChance";
    private static final String CONFIG_TIER_DIRECTION = "tierDirection";
    private static final String CONFIG_TIER_COMMON_ENABLED = "tierCommonEnabled";
    private static final String CONFIG_TIER_UNCOMMON_ENABLED = "tierUncommonEnabled";
    private static final String CONFIG_TIER_RARE_ENABLED = "tierRareEnabled";
    private static final String CONFIG_TIER_SHOP_ENABLED = "tierShopEnabled";
    private static final String CONFIG_TIER_BOSS_ENABLED = "tierBossEnabled";

    // Display settings
    public static boolean showTierLabels = true;

    // Per-tier choice counts (1-5, default 2)
    // 1 = original game behavior (no extra choices)
    // 2-5 = that many total options presented
    public static int starterChoices = 2;
    public static int commonChoices = 2;
    public static int uncommonChoices = 2;
    public static int rareChoices = 2;
    public static int bossChoices = 2;
    public static int shopChoices = 2;
    public static int specialChoices = 2;

    // Tier change chance: 0 to 100 (probability that tier will change)
    public static int tierChangeChance = 0;

    // Direction of tier change when it occurs
    public enum TierDirection {
        SAME_OR_BETTER,   // Can stay same or go up
        SAME_OR_WORSE,    // Can stay same or go down
        ALWAYS_BETTER,    // Must go up
        ALWAYS_WORSE,     // Must go down
        CHAOS             // Any enabled tier randomly
    }
    public static TierDirection tierDirection = TierDirection.SAME_OR_BETTER;

    // Which tiers are available for additional choices (C/U/R enabled by default)
    public static boolean tierCommonEnabled = true;
    public static boolean tierUncommonEnabled = true;
    public static boolean tierRareEnabled = true;
    public static boolean tierShopEnabled = false;
    public static boolean tierBossEnabled = false;

    // UI page tracking
    private static final int PAGE_CHOICES = 0;
    private static final int PAGE_ALGORITHMS = 1;
    private static int currentPage = PAGE_CHOICES;

    // Preview state tracking
    private static AbstractRelic.RelicTier previewTier = AbstractRelic.RelicTier.COMMON;
    private static int previewChoiceCount = 2;
    private static List<AbstractRelic> previewRelics = new ArrayList<>();
    private static final Random previewRandom = new Random();
    private static final int MAX_PREVIEW_NAME_LENGTH = 12;

    // Dropdown for tier direction selection
    private DropdownMenu tierDirectionDropdown;

    // Display names for tier direction options
    private static final String[] TIER_DIRECTION_NAMES = {
        "Same tier or better",
        "Same tier or worse",
        "Always a better tier",
        "Always a worse tier",
        "Chaos - Any tier"
    };

    public static int getCurrentPage() {
        return currentPage;
    }

    public static void setCurrentPage(int page) {
        currentPage = page;
    }

    public static AbstractRelic.RelicTier getPreviewTier() {
        return previewTier;
    }

    public static int getPreviewChoiceCount() {
        return previewChoiceCount;
    }

    public static List<AbstractRelic> getPreviewRelics() {
        // Lazy init: if empty but relics are now available, populate
        if (previewRelics.isEmpty() && !RelicLibrary.commonList.isEmpty()) {
            previewRelics = selectRandomRelics(previewTier, previewChoiceCount);
        }
        return previewRelics;
    }

    private static void updatePreview(AbstractRelic.RelicTier tier, int count) {
        boolean tierChanged = !tier.equals(previewTier);
        boolean countChanged = count != previewChoiceCount;

        previewTier = tier;
        previewChoiceCount = count;

        // Only regenerate relics when tier or count actually changes
        if (tierChanged || countChanged) {
            previewRelics = selectRandomRelics(tier, count);
        }
    }

    private static ArrayList<AbstractRelic> getRelicListForTier(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case STARTER:  return RelicLibrary.starterList;
            case COMMON:   return RelicLibrary.commonList;
            case UNCOMMON: return RelicLibrary.uncommonList;
            case RARE:     return RelicLibrary.rareList;
            case SHOP:     return RelicLibrary.shopList;
            case SPECIAL:  return RelicLibrary.specialList;
            case BOSS:     return RelicLibrary.bossList;
            default:       return new ArrayList<>();
        }
    }

    private static List<AbstractRelic> selectRandomRelics(AbstractRelic.RelicTier tier, int count) {
        // Special handling for Event tier: first relic from event pool, rest from C/U/R
        if (tier == AbstractRelic.RelicTier.SPECIAL) {
            return selectRandomRelicsForEvent(count);
        }

        List<AbstractRelic> result = new ArrayList<>();

        // First relic always from original tier
        ArrayList<AbstractRelic> originalPool = getRelicListForTier(tier);
        if (originalPool.isEmpty()) {
            return result;
        }
        List<AbstractRelic> filteredOriginal = filterByNameLength(originalPool);
        Collections.shuffle(filteredOriginal, previewRandom);
        result.add(filteredOriginal.get(0));

        // Additional relics: apply tier modification algorithm
        for (int i = 1; i < count; i++) {
            AbstractRelic.RelicTier modifiedTier = calculateModifiedTier(tier, previewRandom);
            if (modifiedTier == null) {
                modifiedTier = tier;  // Fallback to original if no valid tier
            }

            ArrayList<AbstractRelic> pool = getRelicListForTier(modifiedTier);
            if (pool.isEmpty()) {
                pool = originalPool;  // Fallback to original pool
            }

            List<AbstractRelic> filtered = filterByNameLength(pool);
            Collections.shuffle(filtered, previewRandom);

            // Avoid picking same relic as previous ones if possible
            AbstractRelic candidate = filtered.get(0);
            for (int j = 0; j < filtered.size(); j++) {
                boolean duplicate = false;
                for (AbstractRelic existing : result) {
                    if (existing.relicId.equals(filtered.get(j).relicId)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    candidate = filtered.get(j);
                    break;
                }
            }
            result.add(candidate);
        }
        return result;
    }

    /**
     * Select relics for Event tier preview:
     * - First relic: random from specialList (event relics)
     * - Additional relics: random from combined Common/Uncommon/Rare pools
     */
    private static List<AbstractRelic> selectRandomRelicsForEvent(int count) {
        List<AbstractRelic> result = new ArrayList<>();

        // First relic from event pool
        ArrayList<AbstractRelic> eventPool = RelicLibrary.specialList;
        if (!eventPool.isEmpty()) {
            List<AbstractRelic> filtered = filterByNameLength(eventPool);
            Collections.shuffle(filtered, previewRandom);
            result.add(filtered.get(0));
        }

        // Additional relics from C/U/R pools
        if (count > 1) {
            List<AbstractRelic> combinedPool = new ArrayList<>();
            combinedPool.addAll(filterByNameLength(RelicLibrary.commonList));
            combinedPool.addAll(filterByNameLength(RelicLibrary.uncommonList));
            combinedPool.addAll(filterByNameLength(RelicLibrary.rareList));

            Collections.shuffle(combinedPool, previewRandom);

            for (int i = 1; i < count && (i - 1) < combinedPool.size(); i++) {
                result.add(combinedPool.get(i - 1));
            }
        }

        return result;
    }

    /**
     * Filter relics to those with short names for preview display.
     * Falls back to unfiltered list if no short names available.
     */
    private static List<AbstractRelic> filterByNameLength(ArrayList<AbstractRelic> pool) {
        List<AbstractRelic> filtered = new ArrayList<>();
        for (AbstractRelic relic : pool) {
            if (relic.name.length() <= MAX_PREVIEW_NAME_LENGTH) {
                filtered.add(relic);
            }
        }

        // Fall back to unfiltered if no short names available
        if (filtered.isEmpty()) {
            filtered = new ArrayList<>(pool);
        }
        return filtered;
    }

    // ===== Tier Calculation Utilities (shared with RelicLinkPatch) =====

    /**
     * Get hierarchy position for a tier.
     * Common=0, Uncommon=1, Rare=Shop=2, Boss=3
     */
    public static int getTierPosition(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case COMMON: return 0;
            case UNCOMMON: return 1;
            case RARE: return 2;
            case SHOP: return 2;  // Shop equivalent to Rare
            case BOSS: return 3;
            default: return 1;
        }
    }

    /**
     * Get all tiers at a given hierarchy position.
     */
    private static List<AbstractRelic.RelicTier> getTiersAtPosition(int position) {
        List<AbstractRelic.RelicTier> tiers = new ArrayList<>();
        switch (position) {
            case 0: tiers.add(AbstractRelic.RelicTier.COMMON); break;
            case 1: tiers.add(AbstractRelic.RelicTier.UNCOMMON); break;
            case 2:
                tiers.add(AbstractRelic.RelicTier.RARE);
                tiers.add(AbstractRelic.RelicTier.SHOP);
                break;
            case 3: tiers.add(AbstractRelic.RelicTier.BOSS); break;
        }
        return tiers;
    }

    /**
     * Check if a tier is enabled in settings.
     */
    public static boolean isTierEnabled(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case COMMON: return tierCommonEnabled;
            case UNCOMMON: return tierUncommonEnabled;
            case RARE: return tierRareEnabled;
            case SHOP: return tierShopEnabled;
            case BOSS: return tierBossEnabled;
            default: return false;
        }
    }

    /**
     * Pick a random enabled tier from the given position range (inclusive).
     * Returns null if no enabled tiers in range.
     */
    private static AbstractRelic.RelicTier pickRandomEnabledTierInRange(int minPos, int maxPos, Random rng) {
        List<AbstractRelic.RelicTier> candidates = new ArrayList<>();

        for (int pos = minPos; pos <= maxPos; pos++) {
            for (AbstractRelic.RelicTier tier : getTiersAtPosition(pos)) {
                if (isTierEnabled(tier)) {
                    candidates.add(tier);
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(rng.nextInt(candidates.size()));
    }

    /**
     * Calculate new tier based on direction setting.
     * Returns null if no valid tier in the allowed range.
     */
    private static AbstractRelic.RelicTier calculateNewTier(AbstractRelic.RelicTier original, TierDirection direction, Random rng) {
        int currentPos = getTierPosition(original);

        switch (direction) {
            case SAME_OR_BETTER:
                // Can stay same or go up - pick from current position or higher
                return pickRandomEnabledTierInRange(currentPos, 3, rng);

            case SAME_OR_WORSE:
                // Can stay same or go down - pick from current position or lower
                return pickRandomEnabledTierInRange(0, currentPos, rng);

            case ALWAYS_BETTER:
                // Must go up - pick from positions above current
                if (currentPos >= 3) return null;  // Already at top
                return pickRandomEnabledTierInRange(currentPos + 1, 3, rng);

            case ALWAYS_WORSE:
                // Must go down - pick from positions below current
                if (currentPos <= 0) return null;  // Already at bottom
                return pickRandomEnabledTierInRange(0, currentPos - 1, rng);

            case CHAOS:
                // Any enabled tier - pick from all positions
                return pickRandomEnabledTierInRange(0, 3, rng);

            default:
                return isTierEnabled(original) ? original : null;
        }
    }

    /**
     * Calculate a potentially modified tier for additional relic choices.
     *
     * Algorithm:
     * 1. Roll tierChangeChance% to determine if tier changes at all
     * 2. If not changing, return original tier (if enabled)
     * 3. If changing, apply direction rules to select new tier
     * 4. Filter by enabled tiers
     *
     * @param originalTier The original tier of the relic reward
     * @param rng Random number generator to use
     * @return The tier to use, or null if no valid tier available
     */
    public static AbstractRelic.RelicTier calculateModifiedTier(AbstractRelic.RelicTier originalTier, Random rng) {
        int chance = tierChangeChance;
        TierDirection direction = tierDirection;

        // Roll to see if tier changes at all
        boolean shouldChange = chance > 0 && rng.nextInt(100) < chance;

        if (!shouldChange) {
            // No change - return original tier if enabled
            if (isTierEnabled(originalTier)) {
                return originalTier;
            }
            // Original tier not enabled - try to find alternative based on direction
            return calculateNewTier(originalTier, direction, rng);
        }

        // Tier is changing - determine new tier based on direction
        return calculateNewTier(originalTier, direction, rng);
    }

    /**
     * Get the configured choice count for a given relic tier.
     * @param tier The relic tier
     * @return Number of choices (1-5) for that tier
     */
    public static int getChoicesForTier(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case STARTER:
                return starterChoices;
            case COMMON:
                return commonChoices;
            case UNCOMMON:
                return uncommonChoices;
            case RARE:
                return rareChoices;
            case BOSS:
                return bossChoices;
            case SHOP:
                return shopChoices;
            case SPECIAL:
                return specialChoices;
            default:
                return 1; // DEPRECATED or unknown - no extra choices
        }
    }

    public PickyRelicsMod() {
        Log.info("Initializing " + MOD_NAME);
        BaseMod.subscribe(this);
        loadConfig();
    }

    public static void initialize() {
        new PickyRelicsMod();
    }

    private void loadConfig() {
        try {
            Properties defaults = new Properties();
            defaults.setProperty(CONFIG_SHOW_TIER_LABELS, "true");
            defaults.setProperty(CONFIG_STARTER_CHOICES, "2");
            defaults.setProperty(CONFIG_COMMON_CHOICES, "2");
            defaults.setProperty(CONFIG_UNCOMMON_CHOICES, "2");
            defaults.setProperty(CONFIG_RARE_CHOICES, "2");
            defaults.setProperty(CONFIG_BOSS_CHOICES, "2");
            defaults.setProperty(CONFIG_SHOP_CHOICES, "2");
            defaults.setProperty(CONFIG_SPECIAL_CHOICES, "2");
            defaults.setProperty(CONFIG_TIER_CHANGE_CHANCE, "0");
            defaults.setProperty(CONFIG_TIER_DIRECTION, "0");
            defaults.setProperty(CONFIG_TIER_COMMON_ENABLED, "true");
            defaults.setProperty(CONFIG_TIER_UNCOMMON_ENABLED, "true");
            defaults.setProperty(CONFIG_TIER_RARE_ENABLED, "true");
            defaults.setProperty(CONFIG_TIER_SHOP_ENABLED, "false");
            defaults.setProperty(CONFIG_TIER_BOSS_ENABLED, "false");

            config = new SpireConfig(MOD_ID, "config", defaults);

            showTierLabels = config.getBool(CONFIG_SHOW_TIER_LABELS);
            starterChoices = clamp(config.getInt(CONFIG_STARTER_CHOICES), 1, 5);
            commonChoices = clamp(config.getInt(CONFIG_COMMON_CHOICES), 1, 5);
            uncommonChoices = clamp(config.getInt(CONFIG_UNCOMMON_CHOICES), 1, 5);
            rareChoices = clamp(config.getInt(CONFIG_RARE_CHOICES), 1, 5);
            bossChoices = clamp(config.getInt(CONFIG_BOSS_CHOICES), 1, 5);
            shopChoices = clamp(config.getInt(CONFIG_SHOP_CHOICES), 1, 5);
            specialChoices = clamp(config.getInt(CONFIG_SPECIAL_CHOICES), 1, 5);

            // Load tier change settings with migration from old format
            int rawChance = config.getInt(CONFIG_TIER_CHANGE_CHANCE);
            int rawDirection = config.getInt(CONFIG_TIER_DIRECTION);

            // Migration: old negative values meant "trend toward Common"
            if (rawChance < 0 && rawDirection == 0) {
                tierChangeChance = Math.abs(rawChance);
                tierDirection = TierDirection.ALWAYS_WORSE;
                Log.info("Migrating old negative tierChangeChance to new format");
            } else if (rawChance > 0 && rawDirection == 0 && !config.has(CONFIG_TIER_DIRECTION)) {
                // Old positive value meant "trend toward Rare"
                tierChangeChance = rawChance;
                tierDirection = TierDirection.ALWAYS_BETTER;
                Log.info("Migrating old positive tierChangeChance to new format");
            } else {
                tierChangeChance = clamp(rawChance, 0, 100);
                tierDirection = TierDirection.values()[clamp(rawDirection, 0, 4)];
            }

            tierCommonEnabled = config.getBool(CONFIG_TIER_COMMON_ENABLED);
            tierUncommonEnabled = config.getBool(CONFIG_TIER_UNCOMMON_ENABLED);
            tierRareEnabled = config.getBool(CONFIG_TIER_RARE_ENABLED);
            tierShopEnabled = config.getBool(CONFIG_TIER_SHOP_ENABLED);
            tierBossEnabled = config.getBool(CONFIG_TIER_BOSS_ENABLED);

            Log.info("Config loaded: showTierLabels=" + showTierLabels +
                    ", starter=" + starterChoices + ", common=" + commonChoices +
                    ", uncommon=" + uncommonChoices + ", rare=" + rareChoices +
                    ", boss=" + bossChoices + ", shop=" + shopChoices + ", special=" + specialChoices +
                    ", tierChangeChance=" + tierChangeChance + ", tierDirection=" + tierDirection);
        } catch (IOException e) {
            Log.error("Failed to load config", e);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void saveConfig() {
        try {
            config.setBool(CONFIG_SHOW_TIER_LABELS, showTierLabels);
            config.setInt(CONFIG_STARTER_CHOICES, starterChoices);
            config.setInt(CONFIG_COMMON_CHOICES, commonChoices);
            config.setInt(CONFIG_UNCOMMON_CHOICES, uncommonChoices);
            config.setInt(CONFIG_RARE_CHOICES, rareChoices);
            config.setInt(CONFIG_BOSS_CHOICES, bossChoices);
            config.setInt(CONFIG_SHOP_CHOICES, shopChoices);
            config.setInt(CONFIG_SPECIAL_CHOICES, specialChoices);
            config.setInt(CONFIG_TIER_CHANGE_CHANCE, tierChangeChance);
            config.setInt(CONFIG_TIER_DIRECTION, tierDirection.ordinal());
            config.setBool(CONFIG_TIER_COMMON_ENABLED, tierCommonEnabled);
            config.setBool(CONFIG_TIER_UNCOMMON_ENABLED, tierUncommonEnabled);
            config.setBool(CONFIG_TIER_RARE_ENABLED, tierRareEnabled);
            config.setBool(CONFIG_TIER_SHOP_ENABLED, tierShopEnabled);
            config.setBool(CONFIG_TIER_BOSS_ENABLED, tierBossEnabled);
            config.save();
        } catch (IOException e) {
            Log.error("Failed to save config", e);
        }
    }

    private void setTierDirection(TierDirection direction) {
        tierDirection = direction;
        saveConfig();

        // Update the dropdown selection
        if (tierDirectionDropdown != null) {
            tierDirectionDropdown.setSelectedIndex(direction.ordinal());
        }
    }

    @Override
    public void receivePostInitialize() {
        Log.info(MOD_NAME + " post-initialize");

        Texture badgeTexture = createBadgeTexture();
        ModPanel settingsPanel = new ModPanel();

        float xPos = 380.0f;
        float sliderX = xPos + 220.0f;
        float sliderYOffset = 6.0f;
        float rowHeight = 42.0f;

        // Page navigator at the top (centered)
        float navY = 820.0f;
        float navCenterX = 640.0f;
        settingsPanel.addUIElement(new PageNavigator(2, navCenterX, navY,
                PickyRelicsMod::getCurrentPage, PickyRelicsMod::setCurrentPage));

        float contentY = navY - 60.0f;

        // ===== PAGE 0: Choices Per Tier =====
        float yPos = contentY;

        // Explanation
        addPagedElement(settingsPanel, PAGE_CHOICES, new ModLabel(
                "How many choices appear in combat and chest rewards",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 40.0f;

        // Starter tier slider
        addPagedSliderRow(settingsPanel, PAGE_CHOICES, "Starter (" + RelicLibrary.starterList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, starterChoices,
                (val) -> { starterChoices = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.STARTER, val); });
        yPos -= rowHeight;

        // Common tier slider
        addPagedSliderRow(settingsPanel, PAGE_CHOICES, "Common (" + RelicLibrary.commonList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, commonChoices,
                (val) -> { commonChoices = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.COMMON, val); });
        yPos -= rowHeight;

        // Uncommon tier slider
        addPagedSliderRow(settingsPanel, PAGE_CHOICES, "Uncommon (" + RelicLibrary.uncommonList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, uncommonChoices,
                (val) -> { uncommonChoices = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.UNCOMMON, val); });
        yPos -= rowHeight;

        // Rare tier slider
        addPagedSliderRow(settingsPanel, PAGE_CHOICES, "Rare (" + RelicLibrary.rareList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, rareChoices,
                (val) -> { rareChoices = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.RARE, val); });
        yPos -= rowHeight;

        // Shop tier slider
        addPagedSliderRow(settingsPanel, PAGE_CHOICES, "Shop (" + RelicLibrary.shopList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, shopChoices,
                (val) -> { shopChoices = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.SHOP, val); });
        yPos -= rowHeight;

        // Event tier slider (Special tier in game code)
        addPagedSliderRow(settingsPanel, PAGE_CHOICES, "Event (" + RelicLibrary.specialList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, specialChoices,
                (val) -> { specialChoices = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.SPECIAL, val); });
        yPos -= rowHeight;

        // Boss tier slider
        addPagedSliderRow(settingsPanel, PAGE_CHOICES, "Boss (" + RelicLibrary.bossList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, bossChoices,
                (val) -> { bossChoices = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.BOSS, val); });
        yPos -= rowHeight;

        // Visual preview on right side
        float previewX = 1100.0f;
        float previewY = contentY - 60.0f;
        addPagedElement(settingsPanel, PAGE_CHOICES, new RelicChoicePreview(
                previewX, previewY,
                PickyRelicsMod::getPreviewTier,
                PickyRelicsMod::getPreviewChoiceCount,
                PickyRelicsMod::getPreviewRelics
        ));

        // Show tier labels checkbox
        yPos -= 30.0f;
        addPagedElement(settingsPanel, PAGE_CHOICES, new ModLabeledToggleButton(
                "Show relic tier labels on reward screen",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                showTierLabels,
                settingsPanel,
                (label) -> {},
                (toggle) -> { showTierLabels = toggle.enabled; saveConfig(); }
        ));

        // Event tier explanation text (shown when Event slider is active with count > 1)
        float eventTextX = xPos;
        float eventTextY = yPos - 50.0f;
        addPagedElement(settingsPanel, PAGE_CHOICES, new IUIElement() {
            @Override
            public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch sb) {
                if (previewTier == AbstractRelic.RelicTier.SPECIAL && previewChoiceCount > 1) {
                    float scaledX = eventTextX * Settings.scale;
                    float scaledY = eventTextY * Settings.scale;
                    float lineSpacing = 20.0f * Settings.scale;

                    FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                            "Event relics have special requirements.",
                            scaledX, scaledY, Settings.GOLD_COLOR);
                    FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                            "Additional options are from Common,",
                            scaledX, scaledY - lineSpacing, Settings.GOLD_COLOR);
                    FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                            "Uncommon, and Rare pools.",
                            scaledX, scaledY - lineSpacing * 2, Settings.GOLD_COLOR);
                }
            }

            @Override
            public void update() {}

            @Override
            public int renderLayer() { return 1; }

            @Override
            public int updateOrder() { return 1; }
        });

        // ===== PAGE 1: Algorithms =====
        yPos = contentY;

        // Title
        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabel(
                "Algorithms",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.charDescFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 50.0f;

        // Tier change chance slider (0-100%)
        addPagedSliderRow(settingsPanel, PAGE_ALGORITHMS, "Chance to change tier", xPos, sliderX + 60.0f, yPos, sliderYOffset,
                tierChangeChance, 0.0f, 100.0f, "%.0f%%",
                (val) -> { tierChangeChance = val; saveConfig(); });

        yPos -= rowHeight;

        // Explanation text
        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabel(
                "The chance for options to be a different tier",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 50.0f;

        // Tier direction section
        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabel(
                "Tier selection behavior",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 35.0f;

        // Dropdown menu for tier direction selection
        float dropdownX = xPos + 20.0f;
        tierDirectionDropdown = new DropdownMenu(
                TIER_DIRECTION_NAMES,
                tierDirection.ordinal(),
                dropdownX, yPos,
                (index) -> setTierDirection(TierDirection.values()[index])
        );
        addPagedElement(settingsPanel, PAGE_ALGORITHMS, tierDirectionDropdown);

        yPos -= 50.0f;

        // Tiers available section
        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabel(
                "Tiers that options may be selected from",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 35.0f;

        float checkboxX = xPos + 20.0f;

        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabeledToggleButton(
                "Common",
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                tierCommonEnabled,
                settingsPanel,
                (label) -> {},
                (toggle) -> { tierCommonEnabled = toggle.enabled; saveConfig(); }
        ));

        yPos -= 30.0f;

        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabeledToggleButton(
                "Uncommon",
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                tierUncommonEnabled,
                settingsPanel,
                (label) -> {},
                (toggle) -> { tierUncommonEnabled = toggle.enabled; saveConfig(); }
        ));

        yPos -= 30.0f;

        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabeledToggleButton(
                "Rare",
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                tierRareEnabled,
                settingsPanel,
                (label) -> {},
                (toggle) -> { tierRareEnabled = toggle.enabled; saveConfig(); }
        ));

        yPos -= 30.0f;

        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabeledToggleButton(
                "Shop",
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                tierShopEnabled,
                settingsPanel,
                (label) -> {},
                (toggle) -> { tierShopEnabled = toggle.enabled; saveConfig(); }
        ));

        yPos -= 30.0f;

        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabeledToggleButton(
                "Boss",
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                tierBossEnabled,
                settingsPanel,
                (label) -> {},
                (toggle) -> { tierBossEnabled = toggle.enabled; saveConfig(); }
        ));

        BaseMod.registerModBadge(
                badgeTexture,
                MOD_NAME,
                "Aaron",
                "Choose from multiple relic options per tier. Configurable (default: 2).",
                settingsPanel
        );
    }

    private void addPagedElement(ModPanel panel, int page, IUIElement element) {
        panel.addUIElement(new PagedElement(element, page, PickyRelicsMod::getCurrentPage));
    }

    private void addPagedSliderRow(ModPanel panel, int page, String label, float labelX, float sliderX,
                                   float yPos, float sliderYOffset, int currentValue,
                                   java.util.function.IntConsumer onChange) {
        addPagedSliderRow(panel, page, label, labelX, sliderX, yPos, sliderYOffset,
                currentValue, 1.0f, 5.0f, "%.0f", onChange);
    }

    private void addPagedSliderRow(ModPanel panel, int page, String label, float labelX, float sliderX,
                                   float yPos, float sliderYOffset, int currentValue,
                                   float min, float max, String format,
                                   java.util.function.IntConsumer onChange) {
        addPagedElement(panel, page, new ModLabel(
                label,
                labelX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                panel,
                (l) -> {}
        ));

        addPagedElement(panel, page, new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                min, max, (float) currentValue,
                format,
                panel,
                (slider) -> onChange.accept(Math.round(slider.getValue()))
        ));
    }

    private Texture createBadgeTexture() {
        return new Texture("pickyrelics/badge.png");
    }
}
