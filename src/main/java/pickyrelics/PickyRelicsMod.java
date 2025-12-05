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

    // Config keys
    private static final String CONFIG_SHOW_TIER_LABELS = "showTierLabels";
    private static final String CONFIG_STARTER_CHOICES = "starterChoices";
    private static final String CONFIG_COMMON_CHOICES = "commonChoices";
    private static final String CONFIG_UNCOMMON_CHOICES = "uncommonChoices";
    private static final String CONFIG_RARE_CHOICES = "rareChoices";
    private static final String CONFIG_BOSS_CHOICES = "bossChoices";
    private static final String CONFIG_SHOP_CHOICES = "shopChoices";
    private static final String CONFIG_SPECIAL_CHOICES = "specialChoices";

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

            config = new SpireConfig(MOD_ID, "config", defaults);

            showTierLabels = config.getBool(CONFIG_SHOW_TIER_LABELS);
            starterChoices = clamp(config.getInt(CONFIG_STARTER_CHOICES), 1, 5);
            commonChoices = clamp(config.getInt(CONFIG_COMMON_CHOICES), 1, 5);
            uncommonChoices = clamp(config.getInt(CONFIG_UNCOMMON_CHOICES), 1, 5);
            rareChoices = clamp(config.getInt(CONFIG_RARE_CHOICES), 1, 5);
            bossChoices = clamp(config.getInt(CONFIG_BOSS_CHOICES), 1, 5);
            shopChoices = clamp(config.getInt(CONFIG_SHOP_CHOICES), 1, 5);
            specialChoices = clamp(config.getInt(CONFIG_SPECIAL_CHOICES), 1, 5);

            Log.info("Config loaded: showTierLabels=" + showTierLabels +
                    ", starter=" + starterChoices + ", common=" + commonChoices +
                    ", uncommon=" + uncommonChoices + ", rare=" + rareChoices +
                    ", boss=" + bossChoices + ", shop=" + shopChoices + ", special=" + specialChoices);
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

        float yPos = 780.0f;
        float xPos = 380.0f;
        float sliderX = xPos + 180.0f;
        float sliderYOffset = 6.0f;
        float rowHeight = 42.0f;

        // Title
        settingsPanel.addUIElement(new ModLabel(
                "Picky Relics - Choices Per Tier",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.charDescFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 40.0f;

        // Hint text
        settingsPanel.addUIElement(new ModLabel(
                "1 = original game behavior (no extra choices)",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 50.0f;

        // Starter tier slider
        addSliderRow(settingsPanel, "Starter", xPos, sliderX, yPos, sliderYOffset, starterChoices,
                (val) -> { starterChoices = val; saveConfig(); });
        yPos -= rowHeight;

        // Common tier slider
        addSliderRow(settingsPanel, "Common", xPos, sliderX, yPos, sliderYOffset, commonChoices,
                (val) -> { commonChoices = val; saveConfig(); });
        yPos -= rowHeight;

        // Uncommon tier slider
        addSliderRow(settingsPanel, "Uncommon", xPos, sliderX, yPos, sliderYOffset, uncommonChoices,
                (val) -> { uncommonChoices = val; saveConfig(); });
        yPos -= rowHeight;

        // Rare tier slider
        addSliderRow(settingsPanel, "Rare", xPos, sliderX, yPos, sliderYOffset, rareChoices,
                (val) -> { rareChoices = val; saveConfig(); });
        yPos -= rowHeight;

        // Boss tier slider
        addSliderRow(settingsPanel, "Boss", xPos, sliderX, yPos, sliderYOffset, bossChoices,
                (val) -> { bossChoices = val; saveConfig(); });
        yPos -= rowHeight;

        // Shop tier slider
        addSliderRow(settingsPanel, "Shop", xPos, sliderX, yPos, sliderYOffset, shopChoices,
                (val) -> { shopChoices = val; saveConfig(); });
        yPos -= rowHeight;

        // Event tier slider (Special tier in game code)
        addSliderRow(settingsPanel, "Event", xPos, sliderX, yPos, sliderYOffset, specialChoices,
                (val) -> { specialChoices = val; saveConfig(); });
        yPos -= rowHeight;

        // Footer note
        yPos -= 10.0f;
        settingsPanel.addUIElement(new ModLabel(
                "Event tier includes unique relics from events and mods",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        ));

        // Show tier labels checkbox
        yPos -= 50.0f;
        settingsPanel.addUIElement(new ModLabeledToggleButton(
                "Show relic tier labels on reward screen",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                showTierLabels,
                settingsPanel,
                (label) -> {},
                (toggle) -> { showTierLabels = toggle.enabled; saveConfig(); }
        ));

        BaseMod.registerModBadge(
                badgeTexture,
                MOD_NAME,
                "Aaron",
                "Choose from multiple relic options per tier. Configurable (default: 2).",
                settingsPanel
        );
    }

    private void addSliderRow(ModPanel panel, String label, float labelX, float sliderX,
                              float yPos, float sliderYOffset, int currentValue,
                              java.util.function.IntConsumer onChange) {
        panel.addUIElement(new ModLabel(
                label,
                labelX, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                panel,
                (l) -> {}
        ));

        panel.addUIElement(new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) currentValue,
                "%.0f",
                panel,
                (slider) -> onChange.accept(Math.round(slider.getValue()))
        ));
    }

    private Texture createBadgeTexture() {
        return new Texture("pickyrelics/badge.png");
    }
}
