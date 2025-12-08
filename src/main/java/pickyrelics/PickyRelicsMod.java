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
import pickyrelics.ui.PagedElement;
import pickyrelics.ui.PageNavigator;
import pickyrelics.ui.ProbabilityDisplay;
import pickyrelics.ui.RelicChoicePreview;
import pickyrelics.util.Log;
import pickyrelics.util.TierUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

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
    private static final String CONFIG_ALLOW_HIGHER_TIERS = "allowHigherTiers";
    private static final String CONFIG_ALLOW_LOWER_TIERS = "allowLowerTiers";
    private static final String CONFIG_ALLOW_SHOP_RELICS = "allowShopRelics";
    private static final String CONFIG_ALLOW_BOSS_RELICS = "allowBossRelics";
    // Legacy config keys for migration
    private static final String CONFIG_TIER_DIRECTION = "tierDirection";
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

    // Tier direction options (which tiers can be selected when tier changes)
    public static boolean allowHigherTiers = false;  // Can tier go up (toward Rare/Boss)
    public static boolean allowLowerTiers = false;   // Can tier go down (toward Common)
    public static boolean allowShopRelics = false;   // Include Shop tier in pool
    public static boolean allowBossRelics = false;   // Include Boss tier in pool

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
        Set<String> usedIds = new HashSet<>();  // O(1) duplicate detection

        // First relic always from original tier
        ArrayList<AbstractRelic> originalPool = getRelicListForTier(tier);
        if (originalPool.isEmpty()) {
            return result;
        }
        List<AbstractRelic> filteredOriginal = filterByNameLength(originalPool);
        Collections.shuffle(filteredOriginal, previewRandom);
        AbstractRelic firstRelic = filteredOriginal.get(0);
        result.add(firstRelic);
        usedIds.add(firstRelic.relicId);

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
            for (AbstractRelic relic : filtered) {
                if (!usedIds.contains(relic.relicId)) {
                    candidate = relic;
                    break;
                }
            }
            result.add(candidate);
            usedIds.add(candidate.relicId);
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

    // ===== Tier Calculation Utilities (delegating to TierUtils) =====

    /**
     * Get hierarchy position for a tier.
     * Delegates to TierUtils for shared implementation.
     */
    public static int getTierPosition(AbstractRelic.RelicTier tier) {
        return TierUtils.getTierPosition(tier);
    }

    /**
     * Calculate a potentially modified tier for additional relic choices.
     * Wrapper around TierUtils.calculateModifiedTier using Java's Random.
     *
     * @param originalTier The original tier of the relic reward
     * @param rng Random number generator to use
     * @return The tier to use (never null - original tier is always valid)
     */
    public static AbstractRelic.RelicTier calculateModifiedTier(AbstractRelic.RelicTier originalTier, Random rng) {
        return TierUtils.calculateModifiedTier(
                originalTier,
                chance -> rng.nextInt(100) < chance ? 1 : 0,
                rng::nextInt
        );
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
            defaults.setProperty(CONFIG_ALLOW_HIGHER_TIERS, "false");
            defaults.setProperty(CONFIG_ALLOW_LOWER_TIERS, "false");
            defaults.setProperty(CONFIG_ALLOW_SHOP_RELICS, "false");
            defaults.setProperty(CONFIG_ALLOW_BOSS_RELICS, "false");

            config = new SpireConfig(MOD_ID, "config", defaults);

            showTierLabels = config.getBool(CONFIG_SHOW_TIER_LABELS);
            starterChoices = clamp(config.getInt(CONFIG_STARTER_CHOICES), 1, 5);
            commonChoices = clamp(config.getInt(CONFIG_COMMON_CHOICES), 1, 5);
            uncommonChoices = clamp(config.getInt(CONFIG_UNCOMMON_CHOICES), 1, 5);
            rareChoices = clamp(config.getInt(CONFIG_RARE_CHOICES), 1, 5);
            bossChoices = clamp(config.getInt(CONFIG_BOSS_CHOICES), 1, 5);
            shopChoices = clamp(config.getInt(CONFIG_SHOP_CHOICES), 1, 5);
            specialChoices = clamp(config.getInt(CONFIG_SPECIAL_CHOICES), 1, 5);
            tierChangeChance = clamp(config.getInt(CONFIG_TIER_CHANGE_CHANCE), 0, 100);

            // Check for migration from old format
            if (config.has(CONFIG_TIER_DIRECTION) && !config.has(CONFIG_ALLOW_HIGHER_TIERS)) {
                // Migrate from old TierDirection enum
                int oldDirection = config.getInt(CONFIG_TIER_DIRECTION);
                switch (oldDirection) {
                    case 0: // SAME_OR_BETTER
                    case 2: // ALWAYS_BETTER
                        allowHigherTiers = true;
                        allowLowerTiers = false;
                        break;
                    case 1: // SAME_OR_WORSE
                    case 3: // ALWAYS_WORSE
                        allowHigherTiers = false;
                        allowLowerTiers = true;
                        break;
                    case 4: // CHAOS
                        allowHigherTiers = true;
                        allowLowerTiers = true;
                        break;
                    default:
                        allowHigherTiers = false;
                        allowLowerTiers = false;
                }
                // Migrate shop/boss from old keys
                allowShopRelics = config.getBool(CONFIG_TIER_SHOP_ENABLED);
                allowBossRelics = config.getBool(CONFIG_TIER_BOSS_ENABLED);
                Log.info("Migrated old tier direction config to new format");
            } else {
                // Load new format
                allowHigherTiers = config.getBool(CONFIG_ALLOW_HIGHER_TIERS);
                allowLowerTiers = config.getBool(CONFIG_ALLOW_LOWER_TIERS);
                allowShopRelics = config.getBool(CONFIG_ALLOW_SHOP_RELICS);
                allowBossRelics = config.getBool(CONFIG_ALLOW_BOSS_RELICS);
            }

            Log.info("Config loaded: showTierLabels=" + showTierLabels +
                    ", starter=" + starterChoices + ", common=" + commonChoices +
                    ", uncommon=" + uncommonChoices + ", rare=" + rareChoices +
                    ", boss=" + bossChoices + ", shop=" + shopChoices + ", special=" + specialChoices +
                    ", tierChangeChance=" + tierChangeChance +
                    ", allowHigher=" + allowHigherTiers + ", allowLower=" + allowLowerTiers +
                    ", allowShop=" + allowShopRelics + ", allowBoss=" + allowBossRelics);
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
            config.setBool(CONFIG_ALLOW_HIGHER_TIERS, allowHigherTiers);
            config.setBool(CONFIG_ALLOW_LOWER_TIERS, allowLowerTiers);
            config.setBool(CONFIG_ALLOW_SHOP_RELICS, allowShopRelics);
            config.setBool(CONFIG_ALLOW_BOSS_RELICS, allowBossRelics);
            config.save();
        } catch (IOException e) {
            Log.error("Failed to save config", e);
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

        // Tier change chance slider (0-100%)
        addPagedSliderRow(settingsPanel, PAGE_ALGORITHMS, "Chance for options to be a different tier", xPos, sliderX + 210.0f, yPos, sliderYOffset,
                tierChangeChance, 0.0f, 100.0f, "%.0f%%",
                (val) -> { tierChangeChance = val; saveConfig(); });

        yPos -= rowHeight + 30.0f;

        float checkboxX = xPos + 20.0f;

        // Tier direction checkboxes
        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabeledToggleButton(
                "Allow options from higher tiers",
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                allowHigherTiers,
                settingsPanel,
                (label) -> {},
                (toggle) -> { allowHigherTiers = toggle.enabled; saveConfig(); }
        ));

        yPos -= 35.0f;

        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabeledToggleButton(
                "Allow options from lower tiers",
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                allowLowerTiers,
                settingsPanel,
                (label) -> {},
                (toggle) -> { allowLowerTiers = toggle.enabled; saveConfig(); }
        ));

        yPos -= 50.0f;

        // Shop/Boss relic section
        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabeledToggleButton(
                "Include shop relics as options",
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                allowShopRelics,
                settingsPanel,
                (label) -> {},
                (toggle) -> { allowShopRelics = toggle.enabled; saveConfig(); }
        ));

        yPos -= 35.0f;

        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ModLabeledToggleButton(
                "Include boss relics as options",
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                allowBossRelics,
                settingsPanel,
                (label) -> {},
                (toggle) -> { allowBossRelics = toggle.enabled; saveConfig(); }
        ));

        // Probability simulator display (right side of Algorithms page)
        addPagedElement(settingsPanel, PAGE_ALGORITHMS, new ProbabilityDisplay(850.0f, contentY - 72.0f));

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
