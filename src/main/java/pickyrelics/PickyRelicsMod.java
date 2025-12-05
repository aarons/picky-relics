package pickyrelics;

import basemod.BaseMod;
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
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.util.Log;

import java.io.IOException;
import java.util.Properties;

@SpireInitializer
public class PickyRelicsMod implements PostInitializeSubscriber {

    public static final String MOD_ID = "pickyrelics";
    public static final String MOD_NAME = "Picky Relics";

    // Config
    private static SpireConfig config;

    // Tab management
    private static boolean showingOptionsTab = true;
    private static java.util.List<Object> optionsTabElements = new java.util.ArrayList<>();
    private static java.util.List<Object> algorithmsTabElements = new java.util.ArrayList<>();
    private static java.util.Map<Object, Float> originalYPositions = new java.util.HashMap<>();
    private static ModLabeledToggleButton optionsTabButton;
    private static ModLabeledToggleButton algorithmsTabButton;

    // Config keys
    private static final String CONFIG_SHOW_TIER_LABELS = "showTierLabels";
    private static final String CONFIG_STARTER_CHOICES = "starterChoices";
    private static final String CONFIG_COMMON_CHOICES = "commonChoices";
    private static final String CONFIG_UNCOMMON_CHOICES = "uncommonChoices";
    private static final String CONFIG_RARE_CHOICES = "rareChoices";
    private static final String CONFIG_BOSS_CHOICES = "bossChoices";
    private static final String CONFIG_SHOP_CHOICES = "shopChoices";
    private static final String CONFIG_SPECIAL_CHOICES = "specialChoices";
    private static final String CONFIG_TIER_CHANGE_PERCENTAGE = "tierChangePercentage";

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

    // Tier change algorithm settings
    // Range: -100 to +100, default 0 (no change)
    // -100 = always lowest tier in hierarchy
    // +100 = always highest tier in hierarchy
    // 0 = no tier change (use original tier)
    public static int tierChangePercentage = 0;

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
            defaults.setProperty(CONFIG_TIER_CHANGE_PERCENTAGE, "0");

            config = new SpireConfig(MOD_ID, "config", defaults);

            showTierLabels = config.getBool(CONFIG_SHOW_TIER_LABELS);
            starterChoices = clamp(config.getInt(CONFIG_STARTER_CHOICES), 1, 5);
            commonChoices = clamp(config.getInt(CONFIG_COMMON_CHOICES), 1, 5);
            uncommonChoices = clamp(config.getInt(CONFIG_UNCOMMON_CHOICES), 1, 5);
            rareChoices = clamp(config.getInt(CONFIG_RARE_CHOICES), 1, 5);
            bossChoices = clamp(config.getInt(CONFIG_BOSS_CHOICES), 1, 5);
            shopChoices = clamp(config.getInt(CONFIG_SHOP_CHOICES), 1, 5);
            specialChoices = clamp(config.getInt(CONFIG_SPECIAL_CHOICES), 1, 5);
            tierChangePercentage = clamp(config.getInt(CONFIG_TIER_CHANGE_PERCENTAGE), -100, 100);

            Log.info("Config loaded: showTierLabels=" + showTierLabels +
                    ", starter=" + starterChoices + ", common=" + commonChoices +
                    ", uncommon=" + uncommonChoices + ", rare=" + rareChoices +
                    ", boss=" + bossChoices + ", shop=" + shopChoices + ", special=" + specialChoices +
                    ", tierChange=" + tierChangePercentage + "%");
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
            config.setInt(CONFIG_TIER_CHANGE_PERCENTAGE, tierChangePercentage);
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
        float yPos = 780.0f;

        // Create tab buttons first
        createTabButtons(settingsPanel, xPos, yPos);

        // Create content for both tabs
        createOptionsTabContent(settingsPanel, xPos, yPos - 80.0f);
        createAlgorithmsTabContent(settingsPanel, xPos, yPos - 80.0f);

        // Initialize with options tab visible
        switchToOptionsTab();

        BaseMod.registerModBadge(
                badgeTexture,
                MOD_NAME,
                "Aaron",
                "Choose from multiple relic options per tier. Configurable (default: 2).",
                settingsPanel
        );
    }

    private void createTabButtons(ModPanel settingsPanel, float xPos, float yPos) {
        // Options tab button
        optionsTabButton = new ModLabeledToggleButton(
                "Options",
                xPos, yPos,
                Settings.CREAM_COLOR,  // Active by default
                FontHelper.charDescFont,
                true,  // Options tab starts active
                settingsPanel,
                (label) -> {},
                (toggle) -> switchToOptionsTab()
        );
        settingsPanel.addUIElement(optionsTabButton);

        // Algorithms tab button
        algorithmsTabButton = new ModLabeledToggleButton(
                "Algorithms",
                xPos + 150.0f, yPos,
                Settings.GOLD_COLOR,  // Inactive by default
                FontHelper.charDescFont,
                false,  // Algorithms tab starts inactive
                settingsPanel,
                (label) -> {},
                (toggle) -> switchToAlgorithmsTab()
        );
        settingsPanel.addUIElement(algorithmsTabButton);
    }

    private void createOptionsTabContent(ModPanel settingsPanel, float xPos, float yPos) {
        float sliderX = xPos + 180.0f;
        float sliderYOffset = 6.0f;
        float rowHeight = 42.0f;

        // Title
        ModLabel optionsTitle = new ModLabel(
                "=== OPTIONS ===",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.charDescFont,
                settingsPanel,
                (label) -> {}
        );
        trackElement(optionsTabElements, optionsTitle);

        yPos -= 40.0f;

        ModLabel mainTitle = new ModLabel(
                "Picky Relics - Choices Per Tier",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.charDescFont,
                settingsPanel,
                (label) -> {}
        );
        settingsPanel.addUIElement(mainTitle);
        trackElement(optionsTabElements, mainTitle);

        yPos -= 40.0f;

        // Hint text
        ModLabel hintText = new ModLabel(
                "1 = original game behavior (no extra choices)",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        );
        settingsPanel.addUIElement(hintText);
        trackElement(optionsTabElements, hintText);

        yPos -= 50.0f;

        // Starter tier slider
        ModLabel starterLabel = new ModLabel(
                "Starter",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (l) -> {}
        );
        ModMinMaxSlider starterSlider = new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) starterChoices,
                "%.0f",
                settingsPanel,
                (slider) -> { starterChoices = Math.round(slider.getValue()); saveConfig(); }
        );
        settingsPanel.addUIElement(starterLabel);
        settingsPanel.addUIElement(starterSlider);
        trackElement(optionsTabElements, starterLabel);
        trackElement(optionsTabElements, starterSlider);
        yPos -= rowHeight;

        // Common tier slider
        ModLabel commonLabel = new ModLabel(
                "Common",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (l) -> {}
        );
        ModMinMaxSlider commonSlider = new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) commonChoices,
                "%.0f",
                settingsPanel,
                (slider) -> { commonChoices = Math.round(slider.getValue()); saveConfig(); }
        );
        settingsPanel.addUIElement(commonLabel);
        settingsPanel.addUIElement(commonSlider);
        trackElement(optionsTabElements, commonLabel);
        trackElement(optionsTabElements, commonSlider);
        yPos -= rowHeight;

        // Uncommon tier slider
        ModLabel uncommonLabel = new ModLabel(
                "Uncommon",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (l) -> {}
        );
        ModMinMaxSlider uncommonSlider = new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) uncommonChoices,
                "%.0f",
                settingsPanel,
                (slider) -> { uncommonChoices = Math.round(slider.getValue()); saveConfig(); }
        );
        settingsPanel.addUIElement(uncommonLabel);
        settingsPanel.addUIElement(uncommonSlider);
        trackElement(optionsTabElements, uncommonLabel);
        trackElement(optionsTabElements, uncommonSlider);
        yPos -= rowHeight;

        // Rare tier slider
        ModLabel rareLabel = new ModLabel(
                "Rare",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (l) -> {}
        );
        ModMinMaxSlider rareSlider = new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) rareChoices,
                "%.0f",
                settingsPanel,
                (slider) -> { rareChoices = Math.round(slider.getValue()); saveConfig(); }
        );
        settingsPanel.addUIElement(rareLabel);
        settingsPanel.addUIElement(rareSlider);
        trackElement(optionsTabElements, rareLabel);
        trackElement(optionsTabElements, rareSlider);
        yPos -= rowHeight;

        // Boss tier slider
        ModLabel bossLabel = new ModLabel(
                "Boss^",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (l) -> {}
        );
        ModMinMaxSlider bossSlider = new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) bossChoices,
                "%.0f",
                settingsPanel,
                (slider) -> { bossChoices = Math.round(slider.getValue()); saveConfig(); }
        );
        settingsPanel.addUIElement(bossLabel);
        settingsPanel.addUIElement(bossSlider);
        trackElement(optionsTabElements, bossLabel);
        trackElement(optionsTabElements, bossSlider);
        yPos -= rowHeight;

        // Shop tier slider
        ModLabel shopLabel = new ModLabel(
                "Shop^",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (l) -> {}
        );
        ModMinMaxSlider shopSlider = new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) shopChoices,
                "%.0f",
                settingsPanel,
                (slider) -> { shopChoices = Math.round(slider.getValue()); saveConfig(); }
        );
        settingsPanel.addUIElement(shopLabel);
        settingsPanel.addUIElement(shopSlider);
        trackElement(optionsTabElements, shopLabel);
        trackElement(optionsTabElements, shopSlider);
        yPos -= rowHeight;

        // Event tier slider (Special tier in game code)
        ModLabel eventLabel = new ModLabel(
                "Event^",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (l) -> {}
        );
        ModMinMaxSlider eventSlider = new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) specialChoices,
                "%.0f",
                settingsPanel,
                (slider) -> { specialChoices = Math.round(slider.getValue()); saveConfig(); }
        );
        settingsPanel.addUIElement(eventLabel);
        settingsPanel.addUIElement(eventSlider);
        trackElement(optionsTabElements, eventLabel);
        trackElement(optionsTabElements, eventSlider);
        yPos -= rowHeight;

        // Footer note
        yPos -= 10.0f;
        ModLabel footerNote = new ModLabel(
                "^Boss, Shop, and Event choices are only shown when those relics appear in combat or chest rewards.",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        );
        settingsPanel.addUIElement(footerNote);
        trackElement(optionsTabElements, footerNote);

        // Show tier labels checkbox
        yPos -= 50.0f;
        ModLabeledToggleButton tierLabelsToggle = new ModLabeledToggleButton(
                "Show relic tier labels on reward screen",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                showTierLabels,
                settingsPanel,
                (label) -> {},
                (toggle) -> { showTierLabels = toggle.enabled; saveConfig(); }
        );
        settingsPanel.addUIElement(tierLabelsToggle);
        trackElement(optionsTabElements, tierLabelsToggle);
    }

    private void createAlgorithmsTabContent(ModPanel settingsPanel, float xPos, float yPos) {
        float sliderX = xPos + 180.0f;
        float sliderYOffset = 6.0f;
        float rowHeight = 42.0f;

        // Title
        ModLabel algorithmsTitle = new ModLabel(
                "=== ALGORITHM ===",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.charDescFont,
                settingsPanel,
                (label) -> {}
        );
        trackElement(algorithmsTabElements, algorithmsTitle);

        yPos -= 40.0f;

        ModLabel algorithmMainTitle = new ModLabel(
                "Picky Relics - Algorithm Settings",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.charDescFont,
                settingsPanel,
                (label) -> {}
        );
        settingsPanel.addUIElement(algorithmMainTitle);
        trackElement(algorithmsTabElements, algorithmMainTitle);

        yPos -= 40.0f;

        // Tier change slider label
        ModLabel tierChangeLabel = new ModLabel(
                "Tier change chance (%)",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (label) -> {}
        );
        settingsPanel.addUIElement(tierChangeLabel);
        trackElement(algorithmsTabElements, tierChangeLabel);
        yPos -= rowHeight;

        // Tier change slider
        ModMinMaxSlider tierChangeSlider = new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                -100.0f, 100.0f, (float) tierChangePercentage,
                "%.0f",
                settingsPanel,
                (slider) -> {
                    tierChangePercentage = Math.round(slider.getValue());
                    saveConfig();
                }
        );
        settingsPanel.addUIElement(tierChangeSlider);
        trackElement(algorithmsTabElements, tierChangeSlider);

        yPos -= 50.0f;
        ModLabel rangeLabel = new ModLabel(
                "Range: -100 to +100",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        );
        settingsPanel.addUIElement(rangeLabel);
        trackElement(algorithmsTabElements, rangeLabel);

        yPos -= 30.0f;
        ModLabel negativeHundredLabel = new ModLabel(
                "-100 = always lowest tier in hierarchy",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        );
        settingsPanel.addUIElement(negativeHundredLabel);
        trackElement(algorithmsTabElements, negativeHundredLabel);

        yPos -= 25.0f;
        ModLabel zeroLabel = new ModLabel(
                "0 = no tier change (use original tier)",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        );
        settingsPanel.addUIElement(zeroLabel);
        trackElement(algorithmsTabElements, zeroLabel);

        yPos -= 25.0f;
        ModLabel positiveHundredLabel = new ModLabel(
                "+100 = always highest tier in hierarchy",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        );
        settingsPanel.addUIElement(positiveHundredLabel);
        trackElement(algorithmsTabElements, positiveHundredLabel);

        // Initially hide all algorithm elements
        hideAlgorithmTabElements();
    }

    private void trackElement(java.util.List<Object> elementList, Object element) {
        elementList.add(element);
        // Store original position for restoration using reflection since access is private
        try {
            if (element instanceof ModLabel) {
                java.lang.reflect.Field yField = ModLabel.class.getDeclaredField("y");
                yField.setAccessible(true);
                originalYPositions.put(element, yField.getFloat(element));
            } else if (element instanceof ModMinMaxSlider) {
                java.lang.reflect.Field yField = ModMinMaxSlider.class.getDeclaredField("y");
                yField.setAccessible(true);
                originalYPositions.put(element, yField.getFloat(element));
            } else if (element instanceof ModLabeledToggleButton) {
                java.lang.reflect.Field yField = ModLabeledToggleButton.class.getDeclaredField("y");
                yField.setAccessible(true);
                originalYPositions.put(element, yField.getFloat(element));
            }
        } catch (Exception e) {
            Log.error("Failed to track element position", e);
        }
    }

    private void switchToOptionsTab() {
        showingOptionsTab = true;
        
        // Show options elements
        for (Object element : optionsTabElements) {
            showElement(element);
        }
        
        // Hide algorithms elements
        hideAlgorithmTabElements();
        
        // Update tab button colors
        updateTabButtonColors();
    }

    private void switchToAlgorithmsTab() {
        showingOptionsTab = false;
        
        // Show algorithms elements
        for (Object element : algorithmsTabElements) {
            showElement(element);
        }
        
        // Hide options elements
        for (Object element : optionsTabElements) {
            hideElement(element);
        }
        
        // Update tab button colors
        updateTabButtonColors();
    }

    private void hideAlgorithmTabElements() {
        for (Object element : algorithmsTabElements) {
            hideElement(element);
        }
    }

    private void hideElement(Object element) {
        try {
            if (element instanceof ModLabel) {
                java.lang.reflect.Field yField = ModLabel.class.getDeclaredField("y");
                yField.setAccessible(true);
                yField.setFloat(element, -2000.0f);
            } else if (element instanceof ModMinMaxSlider) {
                java.lang.reflect.Field yField = ModMinMaxSlider.class.getDeclaredField("y");
                yField.setAccessible(true);
                yField.setFloat(element, -2000.0f);
            } else if (element instanceof ModLabeledToggleButton) {
                java.lang.reflect.Field yField = ModLabeledToggleButton.class.getDeclaredField("y");
                yField.setAccessible(true);
                yField.setFloat(element, -2000.0f);
            }
        } catch (Exception e) {
            Log.error("Failed to hide element", e);
        }
    }

    private void showElement(Object element) {
        Float originalY = originalYPositions.get(element);
        if (originalY != null) {
            try {
                if (element instanceof ModLabel) {
                    java.lang.reflect.Field yField = ModLabel.class.getDeclaredField("y");
                    yField.setAccessible(true);
                    yField.setFloat(element, originalY);
                } else if (element instanceof ModMinMaxSlider) {
                    java.lang.reflect.Field yField = ModMinMaxSlider.class.getDeclaredField("y");
                    yField.setAccessible(true);
                    yField.setFloat(element, originalY);
                } else if (element instanceof ModLabeledToggleButton) {
                    java.lang.reflect.Field yField = ModLabeledToggleButton.class.getDeclaredField("y");
                    yField.setAccessible(true);
                    yField.setFloat(element, originalY);
                }
            } catch (Exception e) {
                Log.error("Failed to show element", e);
            }
        }
    }

    private void updateTabButtonColors() {
        // Simplified approach - just keep the default colors since we can't easily access label
        // The active/inactive state is handled by the toggle buttons themselves
    }


    private Texture createBadgeTexture() {
        return new Texture("pickyrelics/badge.png");
    }

}
