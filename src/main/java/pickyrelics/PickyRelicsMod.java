package pickyrelics;

import basemod.BaseMod;
import basemod.IUIElement;
import basemod.ModLabel;
import basemod.ModLabeledToggleButton;
import basemod.ModPanel;
import basemod.ModMinMaxSlider;
import basemod.devcommands.ConsoleCommand;
import basemod.interfaces.EditStringsSubscriber;
import basemod.interfaces.PostBattleSubscriber;
import basemod.interfaces.PostDungeonInitializeSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.StartGameSubscriber;
import com.badlogic.gdx.graphics.Texture;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import pickyrelics.devcommands.PickyPoolCommand;
import pickyrelics.patches.RelicLinkPatch;
import pickyrelics.ui.PagedElement;
import pickyrelics.ui.ProbabilityDisplay;
import pickyrelics.ui.RelicChoicePreview;
import pickyrelics.ui.TabBar;
import pickyrelics.util.ExclusionList;
import pickyrelics.util.Log;
import pickyrelics.util.RelicPoolTracker;
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
public class PickyRelicsMod implements PostInitializeSubscriber, EditStringsSubscriber, PostBattleSubscriber,
        PostDungeonInitializeSubscriber, StartGameSubscriber {

    public static final String MOD_ID = "pickyrelics";
    public static final String MOD_NAME = "Picky Relics";

    // Localized strings (loaded in receiveEditStrings)
    private static UIStrings modInfoStrings;
    private static UIStrings settingsStrings;

    public static String makeID(String id) {
        return MOD_ID + ":" + id;
    }

    // Config
    private static SpireConfig config;

    // Config keys
    private static final String CONFIG_SHOW_TIER_LABELS = "showTierLabels";
    private static final String CONFIG_STARTER_ADDITIONAL = "starterAdditional";
    private static final String CONFIG_COMMON_ADDITIONAL = "commonAdditional";
    private static final String CONFIG_UNCOMMON_ADDITIONAL = "uncommonAdditional";
    private static final String CONFIG_RARE_ADDITIONAL = "rareAdditional";
    private static final String CONFIG_BOSS_ADDITIONAL = "bossAdditional";
    private static final String CONFIG_SHOP_ADDITIONAL = "shopAdditional";
    private static final String CONFIG_SPECIAL_ADDITIONAL = "specialAdditional";
    private static final String CONFIG_TIER_CHANGE_CHANCE = "tierChangeChance";
    private static final String CONFIG_TIER_CHANGE_MAGNITUDE = "tierChangeMagnitude";
    private static final String CONFIG_ALLOW_HIGHER_TIERS = "allowHigherTiers";
    private static final String CONFIG_ALLOW_LOWER_TIERS = "allowLowerTiers";
    private static final String CONFIG_ALLOW_SHOP_RELICS = "allowShopRelics";
    private static final String CONFIG_ALLOW_BOSS_RELICS = "allowBossRelics";
    private static final String CONFIG_CYCLE_POOLS = "cyclePoolsEnabled";
    private static final String CONFIG_EXCLUDED_RELICS = "excludedRelics";
    // Legacy config keys for migration
    private static final String CONFIG_TIER_DIRECTION = "tierDirection";
    private static final String CONFIG_TIER_SHOP_ENABLED = "tierShopEnabled";
    private static final String CONFIG_TIER_BOSS_ENABLED = "tierBossEnabled";
    // Legacy per-tier "total choices" keys (1-5); migrate to *Additional (0-4) by subtracting 1.
    private static final String LEGACY_STARTER_CHOICES = "starterChoices";
    private static final String LEGACY_COMMON_CHOICES = "commonChoices";
    private static final String LEGACY_UNCOMMON_CHOICES = "uncommonChoices";
    private static final String LEGACY_RARE_CHOICES = "rareChoices";
    private static final String LEGACY_BOSS_CHOICES = "bossChoices";
    private static final String LEGACY_SHOP_CHOICES = "shopChoices";
    private static final String LEGACY_SPECIAL_CHOICES = "specialChoices";

    // Display settings
    public static boolean showTierLabels = true;

    // Per-tier additional-choice counts (0-4, default 1)
    // 0 = original game behavior (no extra choices)
    // 1-4 = that many extra options presented alongside the original
    public static int starterAdditional = 1;
    public static int commonAdditional = 1;
    public static int uncommonAdditional = 1;
    public static int rareAdditional = 1;
    public static int bossAdditional = 1;
    public static int shopAdditional = 1;
    public static int specialAdditional = 1;

    // Tier change chance: 0 to 100 (probability that tier will change)
    public static int tierChangeChance = 0;

    // Tier change magnitude: 0 to 100 (how far tier can drift when it changes)
    // 0 = minimal (adjacent tier only), 50 = uniform, 100 = maximum (furthest tier)
    public static int tierChangeMagnitude = 0;

    // Tier direction options (which tiers can be selected when tier changes)
    public static boolean allowHigherTiers = true;   // Can tier go up (toward Rare/Boss)
    public static boolean allowLowerTiers = false;   // Can tier go down (toward Common)
    public static boolean allowShopRelics = false;   // Include Shop tier in pool
    public static boolean allowBossRelics = false;   // Include Boss tier in pool
    public static boolean cyclePoolsEnabled = false; // Refill exhausted tier pools with skipped relics

    // Relic IDs excluded from the picky-relic treatment (never offered as extra
    // choices; awarded as-is when they're the original reward). May contain IDs
    // from uninstalled mods — preserved across load/save, never pruned.
    private static Set<String> excludedRelics = new java.util.LinkedHashSet<>();

    // UI tab tracking
    private static final int TAB_CHOICES = 0;
    private static final int TAB_PROBABILITIES = 1;
    private static final int TAB_REFILLS = 2;
    private static int currentTab = TAB_CHOICES;

    // Preview state tracking
    private static AbstractRelic.RelicTier previewTier = AbstractRelic.RelicTier.COMMON;
    private static int previewChoiceCount = 2;
    private static List<AbstractRelic> previewRelics = new ArrayList<>();
    private static final Random previewRandom = new Random();
    private static final int MAX_PREVIEW_NAME_LENGTH = 12;

    public static int getCurrentTab() {
        return currentTab;
    }

    public static void setCurrentTab(int tab) {
        currentTab = tab;
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
                rng::nextDouble
        );
    }

    /**
     * Get the configured number of additional relic choices for a given tier.
     * @param tier The relic tier
     * @return Number of additional choices (0-4) for that tier
     */
    public static int getAdditionalChoicesForTier(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case STARTER:
                return starterAdditional;
            case COMMON:
                return commonAdditional;
            case UNCOMMON:
                return uncommonAdditional;
            case RARE:
                return rareAdditional;
            case BOSS:
                return bossAdditional;
            case SHOP:
                return shopAdditional;
            case SPECIAL:
                return specialAdditional;
            default:
                return 0; // DEPRECATED or unknown - no extra choices
        }
    }

    /**
     * Whether a relic is excluded from the picky-relic treatment: it is never
     * offered as an extra choice, and gets no extra choices when awarded.
     */
    public static boolean isExcluded(String relicId) {
        return excludedRelics.contains(relicId);
    }

    /**
     * Add or remove a relic from the exclusion list, saving immediately
     * (matching how every other setting saves on change).
     */
    public static void setExcluded(String relicId, boolean excluded) {
        boolean changed = excluded ? excludedRelics.add(relicId) : excludedRelics.remove(relicId);
        if (changed) {
            saveConfig();
        }
    }

    /**
     * Excluded relic IDs in insertion order, for the settings UI.
     * May contain IDs from mods that aren't currently installed.
     */
    public static Set<String> getExcludedRelicIds() {
        return Collections.unmodifiableSet(excludedRelics);
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
            defaults.setProperty(CONFIG_STARTER_ADDITIONAL, "1");
            defaults.setProperty(CONFIG_COMMON_ADDITIONAL, "1");
            defaults.setProperty(CONFIG_UNCOMMON_ADDITIONAL, "1");
            defaults.setProperty(CONFIG_RARE_ADDITIONAL, "1");
            defaults.setProperty(CONFIG_BOSS_ADDITIONAL, "1");
            defaults.setProperty(CONFIG_SHOP_ADDITIONAL, "1");
            defaults.setProperty(CONFIG_SPECIAL_ADDITIONAL, "1");
            defaults.setProperty(CONFIG_TIER_CHANGE_CHANCE, "0");
            defaults.setProperty(CONFIG_TIER_CHANGE_MAGNITUDE, "0");
            defaults.setProperty(CONFIG_ALLOW_HIGHER_TIERS, "true");
            defaults.setProperty(CONFIG_ALLOW_LOWER_TIERS, "false");
            defaults.setProperty(CONFIG_ALLOW_SHOP_RELICS, "false");
            defaults.setProperty(CONFIG_ALLOW_BOSS_RELICS, "false");
            defaults.setProperty(CONFIG_CYCLE_POOLS, "false");
            defaults.setProperty(CONFIG_EXCLUDED_RELICS, "");

            config = new SpireConfig(MOD_ID, "config", defaults);

            showTierLabels = config.getBool(CONFIG_SHOW_TIER_LABELS);
            starterAdditional = loadAdditional(CONFIG_STARTER_ADDITIONAL, LEGACY_STARTER_CHOICES);
            commonAdditional = loadAdditional(CONFIG_COMMON_ADDITIONAL, LEGACY_COMMON_CHOICES);
            uncommonAdditional = loadAdditional(CONFIG_UNCOMMON_ADDITIONAL, LEGACY_UNCOMMON_CHOICES);
            rareAdditional = loadAdditional(CONFIG_RARE_ADDITIONAL, LEGACY_RARE_CHOICES);
            bossAdditional = loadAdditional(CONFIG_BOSS_ADDITIONAL, LEGACY_BOSS_CHOICES);
            shopAdditional = loadAdditional(CONFIG_SHOP_ADDITIONAL, LEGACY_SHOP_CHOICES);
            specialAdditional = loadAdditional(CONFIG_SPECIAL_ADDITIONAL, LEGACY_SPECIAL_CHOICES);
            tierChangeChance = clamp(config.getInt(CONFIG_TIER_CHANGE_CHANCE), 0, 100);
            tierChangeMagnitude = clamp(config.getInt(CONFIG_TIER_CHANGE_MAGNITUDE), 0, 100);

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
                Log.debug("Migrated old tier direction config to new format");
            } else {
                // Load new format
                allowHigherTiers = config.getBool(CONFIG_ALLOW_HIGHER_TIERS);
                allowLowerTiers = config.getBool(CONFIG_ALLOW_LOWER_TIERS);
                allowShopRelics = config.getBool(CONFIG_ALLOW_SHOP_RELICS);
                allowBossRelics = config.getBool(CONFIG_ALLOW_BOSS_RELICS);
            }

            cyclePoolsEnabled = config.getBool(CONFIG_CYCLE_POOLS);
            excludedRelics = ExclusionList.deserialize(config.getString(CONFIG_EXCLUDED_RELICS));

            Log.debug("Config loaded: showTierLabels=" + showTierLabels +
                    ", starter=" + starterAdditional + ", common=" + commonAdditional +
                    ", uncommon=" + uncommonAdditional + ", rare=" + rareAdditional +
                    ", boss=" + bossAdditional + ", shop=" + shopAdditional + ", special=" + specialAdditional +
                    ", tierChangeChance=" + tierChangeChance + ", tierChangeMagnitude=" + tierChangeMagnitude +
                    ", allowHigher=" + allowHigherTiers + ", allowLower=" + allowLowerTiers +
                    ", allowShop=" + allowShopRelics + ", allowBoss=" + allowBossRelics +
                    ", excluded=" + excludedRelics.size());
        } catch (IOException e) {
            Log.error("Failed to load config", e);
        }
    }

    /**
     * Load an additional-choices value (0-4) from the new key, falling back to
     * migrating the legacy "total choices" key (1-5) by subtracting 1.
     */
    private static int loadAdditional(String newKey, String legacyKey) {
        if (!config.has(newKey) && config.has(legacyKey)) {
            int migrated = config.getInt(legacyKey) - 1;
            Log.debug("Migrated " + legacyKey + "=" + (migrated + 1) + " to " + newKey + "=" + migrated);
            return clamp(migrated, 0, 4);
        }
        return clamp(config.getInt(newKey), 0, 4);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void saveConfig() {
        try {
            config.setBool(CONFIG_SHOW_TIER_LABELS, showTierLabels);
            config.setInt(CONFIG_STARTER_ADDITIONAL, starterAdditional);
            config.setInt(CONFIG_COMMON_ADDITIONAL, commonAdditional);
            config.setInt(CONFIG_UNCOMMON_ADDITIONAL, uncommonAdditional);
            config.setInt(CONFIG_RARE_ADDITIONAL, rareAdditional);
            config.setInt(CONFIG_BOSS_ADDITIONAL, bossAdditional);
            config.setInt(CONFIG_SHOP_ADDITIONAL, shopAdditional);
            config.setInt(CONFIG_SPECIAL_ADDITIONAL, specialAdditional);
            config.setInt(CONFIG_TIER_CHANGE_CHANCE, tierChangeChance);
            config.setInt(CONFIG_TIER_CHANGE_MAGNITUDE, tierChangeMagnitude);
            config.setBool(CONFIG_ALLOW_HIGHER_TIERS, allowHigherTiers);
            config.setBool(CONFIG_ALLOW_LOWER_TIERS, allowLowerTiers);
            config.setBool(CONFIG_ALLOW_SHOP_RELICS, allowShopRelics);
            config.setBool(CONFIG_ALLOW_BOSS_RELICS, allowBossRelics);
            config.setBool(CONFIG_CYCLE_POOLS, cyclePoolsEnabled);
            config.setString(CONFIG_EXCLUDED_RELICS, ExclusionList.serialize(excludedRelics));
            config.save();
        } catch (IOException e) {
            Log.error("Failed to save config", e);
        }
    }

    private static final String DEFAULT_LANGUAGE = "eng";

    @Override
    public void receiveEditStrings() {
        // Always load English first as fallback
        loadLocalization(DEFAULT_LANGUAGE);

        // Then try to load current language on top (overwrites English where available)
        String lang = Settings.language.name().toLowerCase();
        if (!DEFAULT_LANGUAGE.equals(lang)) {
            try {
                loadLocalization(lang);
                Log.info("Loaded localization for language: " + lang);
            } catch (Exception e) {
                Log.info("No localization for " + lang + ", using English");
            }
        }
    }

    private void loadLocalization(String lang) {
        BaseMod.loadCustomStringsFile(UIStrings.class,
                MOD_ID + "Resources/localization/" + lang + "/UIStrings.json");
    }

    @Override
    public void receivePostBattle(AbstractRoom room) {
        if (room == null || room.rewards == null) return;
        Log.debug("[PostBattle] Processing relic rewards in AbstractRoom.rewards");
        RelicLinkPatch.processRelicRewards(room.rewards, "PostBattle");
    }

    @Override
    public void receiveStartGame() {
        RelicPoolTracker.reset();
    }

    @Override
    public void receivePostDungeonInitialize() {
        RelicPoolTracker.captureSnapshots();
    }

    @Override
    public void receivePostInitialize() {
        Log.info(MOD_NAME + " post-initialize");

        // Load localized strings
        modInfoStrings = CardCrawlGame.languagePack.getUIString(makeID("ModInfo"));
        settingsStrings = CardCrawlGame.languagePack.getUIString(makeID("Settings"));

        Texture badgeTexture = createBadgeTexture();
        ModPanel settingsPanel = new ModPanel();

        float xPos = 380.0f;
        float sliderX = xPos + 280.0f;
        float sliderYOffset = 6.0f;
        float rowHeight = 42.0f;

        // Tab bar at the top (centered)
        float tabBarY = 820.0f;
        float tabBarCenterX = 640.0f;
        UIStrings tabStrings = CardCrawlGame.languagePack.getUIString(makeID("Tabs"));
        settingsPanel.addUIElement(new TabBar(tabStrings.TEXT, tabBarCenterX, tabBarY,
                PickyRelicsMod::getCurrentTab, PickyRelicsMod::setCurrentTab));

        float contentY = tabBarY - 60.0f;

        // ===== TAB 0: Choices Per Tier =====
        float yPos = contentY;

        // Column headers
        addPagedElement(settingsPanel, TAB_CHOICES, new ModLabel(
                "Original relic's tier",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (l) -> {}
        ));
        addPagedElement(settingsPanel, TAB_CHOICES, new ModLabel(
                "Additional Choices",
                sliderX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (l) -> {}
        ));

        yPos -= 42.0f;

        // Starter tier slider
        addPagedSliderRow(settingsPanel, TAB_CHOICES, "Starter (" + RelicLibrary.starterList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, starterAdditional,
                (val) -> { starterAdditional = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.STARTER, val + 1); });
        yPos -= rowHeight;
        // Common tier slider
        addPagedSliderRow(settingsPanel, TAB_CHOICES, "Common (" + RelicLibrary.commonList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, commonAdditional,
                (val) -> { commonAdditional = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.COMMON, val + 1); });
        yPos -= rowHeight;

        // Uncommon tier slider
        addPagedSliderRow(settingsPanel, TAB_CHOICES, "Uncommon (" + RelicLibrary.uncommonList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, uncommonAdditional,
                (val) -> { uncommonAdditional = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.UNCOMMON, val + 1); });
        yPos -= rowHeight;

        // Rare tier slider
        addPagedSliderRow(settingsPanel, TAB_CHOICES, "Rare (" + RelicLibrary.rareList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, rareAdditional,
                (val) -> { rareAdditional = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.RARE, val + 1); });
        yPos -= rowHeight;

        // Shop tier slider
        addPagedSliderRow(settingsPanel, TAB_CHOICES, "Shop (" + RelicLibrary.shopList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, shopAdditional,
                (val) -> { shopAdditional = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.SHOP, val + 1); });
        yPos -= rowHeight;

        // Event tier slider (Special tier in game code)
        addPagedSliderRow(settingsPanel, TAB_CHOICES, "Event (" + RelicLibrary.specialList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, specialAdditional,
                (val) -> { specialAdditional = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.SPECIAL, val + 1); });
        yPos -= rowHeight;

        // Boss tier slider
        addPagedSliderRow(settingsPanel, TAB_CHOICES, "Boss (" + RelicLibrary.bossList.size() + ")",
                xPos, sliderX, yPos, sliderYOffset, bossAdditional,
                (val) -> { bossAdditional = val; saveConfig(); updatePreview(AbstractRelic.RelicTier.BOSS, val + 1); });
        yPos -= rowHeight;

        // Visual preview on right side
        float previewX = 1100.0f;
        float previewY = contentY - 60.0f;
        addPagedElement(settingsPanel, TAB_CHOICES, new RelicChoicePreview(
                previewX, previewY,
                PickyRelicsMod::getPreviewTier,
                PickyRelicsMod::getPreviewChoiceCount,
                PickyRelicsMod::getPreviewRelics
        ));

        // Show tier labels checkbox
        yPos -= 30.0f;
        addPagedElement(settingsPanel, TAB_CHOICES, new ModLabeledToggleButton(
                settingsStrings.TEXT[1],
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                showTierLabels,
                settingsPanel,
                (label) -> {},
                (toggle) -> { showTierLabels = toggle.enabled; saveConfig(); }
        ));

        // Event tier explanation text (shown when Event slider is active with count > 1)
        // Rendered on the right side, beneath the Loot Preview, so it doesn't collide
        // with the main explanation block on the left.
        float eventTextX = 1090.0f;
        float eventTextY = 340.0f;
        addPagedElement(settingsPanel, TAB_CHOICES, new IUIElement() {
            @Override
            public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch sb) {
                if (previewTier == AbstractRelic.RelicTier.SPECIAL && previewChoiceCount > 1) {
                    float scaledX = eventTextX * Settings.scale;
                    float scaledY = eventTextY * Settings.scale;
                    float lineSpacing = 20.0f * Settings.scale;

                    FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                            settingsStrings.TEXT[2],
                            scaledX, scaledY, Settings.GOLD_COLOR);
                    FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                            settingsStrings.TEXT[3],
                            scaledX, scaledY - lineSpacing, Settings.GOLD_COLOR);
                    FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                            settingsStrings.TEXT[4],
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

        // Bottom-of-page explanation (wraps to ~700px width)
        final float bottomExplanationX = xPos;
        final float bottomExplanationY = 380.0f;
        final float bottomExplanationWidth = 700.0f;
        addPagedElement(settingsPanel, TAB_CHOICES, new IUIElement() {
            @Override
            public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch sb) {
                FontHelper.renderSmartText(sb, FontHelper.tipBodyFont,
                        settingsStrings.TEXT[0],
                        bottomExplanationX * Settings.scale,
                        bottomExplanationY * Settings.scale,
                        bottomExplanationWidth * Settings.scale,
                        28.0f * Settings.scale,
                        Settings.GOLD_COLOR);
            }
            @Override public void update() {}
            @Override public int renderLayer() { return 1; }
            @Override public int updateOrder() { return 1; }
        });

        // ===== TAB 1: Probabilities =====
        yPos = contentY;

        // Tier change chance slider (0-100%)
        addPagedSliderRow(settingsPanel, TAB_PROBABILITIES, settingsStrings.TEXT[5], xPos, sliderX + 150.0f, yPos, sliderYOffset,
                tierChangeChance, 0.0f, 100.0f, "%.0f%%",
                (val) -> { tierChangeChance = val; saveConfig(); });

        yPos -= rowHeight;

        // Magnitude of change slider (0-100%)
        addPagedSliderRow(settingsPanel, TAB_PROBABILITIES, settingsStrings.TEXT[10], xPos, sliderX + 150.0f, yPos, sliderYOffset,
                tierChangeMagnitude, 0.0f, 100.0f, "%.0f%%",
                (val) -> { tierChangeMagnitude = val; saveConfig(); });

        yPos -= rowHeight + 30.0f;

        float checkboxX = xPos + 20.0f;

        // Tier direction checkboxes
        addPagedElement(settingsPanel, TAB_PROBABILITIES, new ModLabeledToggleButton(
                settingsStrings.TEXT[6],
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                allowHigherTiers,
                settingsPanel,
                (label) -> {},
                (toggle) -> { allowHigherTiers = toggle.enabled; saveConfig(); }
        ));

        yPos -= 35.0f;

        addPagedElement(settingsPanel, TAB_PROBABILITIES, new ModLabeledToggleButton(
                settingsStrings.TEXT[7],
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
        addPagedElement(settingsPanel, TAB_PROBABILITIES, new ModLabeledToggleButton(
                settingsStrings.TEXT[8],
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                allowShopRelics,
                settingsPanel,
                (label) -> {},
                (toggle) -> { allowShopRelics = toggle.enabled; saveConfig(); }
        ));

        yPos -= 35.0f;

        addPagedElement(settingsPanel, TAB_PROBABILITIES, new ModLabeledToggleButton(
                settingsStrings.TEXT[9],
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                allowBossRelics,
                settingsPanel,
                (label) -> {},
                (toggle) -> { allowBossRelics = toggle.enabled; saveConfig(); }
        ));

        // Probability simulator display (right side of Probabilities tab)
        addPagedElement(settingsPanel, TAB_PROBABILITIES, new ProbabilityDisplay(850.0f, contentY - 72.0f));

        // Bottom-of-page explanation for Probabilities tab (wraps to ~700px width)
        addPagedElement(settingsPanel, TAB_PROBABILITIES, new IUIElement() {
            @Override
            public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch sb) {
                FontHelper.renderSmartText(sb, FontHelper.tipBodyFont,
                        settingsStrings.TEXT[12],
                        bottomExplanationX * Settings.scale,
                        bottomExplanationY * Settings.scale,
                        bottomExplanationWidth * Settings.scale,
                        28.0f * Settings.scale,
                        Settings.GOLD_COLOR);
            }
            @Override public void update() {}
            @Override public int renderLayer() { return 1; }
            @Override public int updateOrder() { return 1; }
        });

        // ===== TAB 2: Refills =====
        // Toggle buttons render ~10px higher than labels/sliders at the same yPos,
        // so nudge down to visually align with the first row on other tabs.
        yPos = contentY - 10.0f;

        // Cycle pools: refill exhausted tiers with skipped relics
        addPagedElement(settingsPanel, TAB_REFILLS, new ModLabeledToggleButton(
                settingsStrings.TEXT[11],
                checkboxX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                cyclePoolsEnabled,
                settingsPanel,
                (label) -> {},
                (toggle) -> { cyclePoolsEnabled = toggle.enabled; saveConfig(); }
        ));

        // Dev console command for pool diagnostics and testing
        ConsoleCommand.addCommand("pickypool", PickyPoolCommand.class);

        BaseMod.registerModBadge(
                badgeTexture,
                modInfoStrings.TEXT[0],
                modInfoStrings.TEXT[1],
                modInfoStrings.TEXT[2],
                settingsPanel
        );
    }

    private void addPagedElement(ModPanel panel, int page, IUIElement element) {
        panel.addUIElement(new PagedElement(element, page, PickyRelicsMod::getCurrentTab));
    }

    private void addPagedSliderRow(ModPanel panel, int page, String label, float labelX, float sliderX,
                                   float yPos, float sliderYOffset, int currentValue,
                                   java.util.function.IntConsumer onChange) {
        addPagedSliderRow(panel, page, label, labelX, sliderX, yPos, sliderYOffset,
                currentValue, 0.0f, 4.0f, "%.0f", onChange);
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
