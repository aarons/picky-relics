package pickyrelics;

import basemod.BaseMod;
import basemod.ModLabel;
import basemod.ModPanel;
import basemod.ModMinMaxSlider;
import basemod.ModToggleButton;
import basemod.interfaces.PostInitializeSubscriber;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Properties;

@SpireInitializer
public class PickyRelicsMod implements PostInitializeSubscriber {

    public static final String MOD_ID = "pickyrelics";
    public static final String MOD_NAME = "Picky Relics";

    private static final Logger logger = LogManager.getLogger(PickyRelicsMod.class.getName());

    // Config
    private static SpireConfig config;
    private static final String CONFIG_RELIC_CHOICES = "relicChoices";
    private static final String CONFIG_IGNORE_SPECIAL_TIER = "ignoreSpecialTier";
    // Legacy keys for migration
    private static final String CONFIG_COMBAT_CHOICES = "combatChoices";
    private static final String CONFIG_CHEST_CHOICES = "chestChoices";

    // Default: 2 total choices (1 = base game, 2-5 = mod choices)
    public static int relicChoices = 2;
    // Default: true - skip SPECIAL tier relics (improves mod compatibility)
    public static boolean ignoreSpecialTier = true;

    public PickyRelicsMod() {
        logger.info("Initializing " + MOD_NAME);
        BaseMod.subscribe(this);
        loadConfig();
    }

    public static void initialize() {
        new PickyRelicsMod();
    }

    private void loadConfig() {
        try {
            Properties defaults = new Properties();
            defaults.setProperty(CONFIG_RELIC_CHOICES, "2");
            defaults.setProperty(CONFIG_IGNORE_SPECIAL_TIER, "true");

            config = new SpireConfig(MOD_ID, "config", defaults);

            // Check for new key first, then migrate from legacy keys if needed
            if (config.has(CONFIG_RELIC_CHOICES)) {
                relicChoices = clamp(config.getInt(CONFIG_RELIC_CHOICES), 1, 5);
            } else if (config.has(CONFIG_COMBAT_CHOICES) || config.has(CONFIG_CHEST_CHOICES)) {
                // Migration: take the max of old values
                int oldCombat = config.has(CONFIG_COMBAT_CHOICES) ? config.getInt(CONFIG_COMBAT_CHOICES) : 2;
                int oldChest = config.has(CONFIG_CHEST_CHOICES) ? config.getInt(CONFIG_CHEST_CHOICES) : 2;
                relicChoices = clamp(Math.max(oldCombat, oldChest), 1, 5);
                logger.info("Migrated config from legacy keys: combatChoices=" + oldCombat +
                        ", chestChoices=" + oldChest + " -> relicChoices=" + relicChoices);
                // Save with new key
                saveConfig();
            }

            ignoreSpecialTier = config.getBool(CONFIG_IGNORE_SPECIAL_TIER);

            logger.info("Config loaded: relicChoices=" + relicChoices +
                    ", ignoreSpecialTier=" + ignoreSpecialTier);
        } catch (IOException e) {
            logger.error("Failed to load config", e);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void saveConfig() {
        try {
            config.setInt(CONFIG_RELIC_CHOICES, relicChoices);
            config.setBool(CONFIG_IGNORE_SPECIAL_TIER, ignoreSpecialTier);
            config.save();
        } catch (IOException e) {
            logger.error("Failed to save config", e);
        }
    }

    @Override
    public void receivePostInitialize() {
        logger.info(MOD_NAME + " post-initialize");

        // Create a simple colored texture for the mod badge
        Texture badgeTexture = createBadgeTexture();

        // Create settings panel
        ModPanel settingsPanel = new ModPanel();

        float yPos = 800.0f;
        float xPos = 380.0f;
        float sliderX = xPos + 250.0f;
        float sliderYOffset = 6.0f;

        // Title
        settingsPanel.addUIElement(new ModLabel(
                "Picky Relics",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.charDescFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 50.0f;

        // Subtitle
        settingsPanel.addUIElement(new ModLabel(
                "When a relic is rewarded, how many options are provided?",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 35.0f;

        // Hint text
        settingsPanel.addUIElement(new ModLabel(
                "1 = original game behavior",
                xPos, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 50.0f;

        // Relic Choices slider
        settingsPanel.addUIElement(new ModLabel(
                "Relic Choices",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (label) -> {}
        ));

        settingsPanel.addUIElement(new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) relicChoices,
                "%.0f",
                settingsPanel,
                (slider) -> {
                    relicChoices = Math.round(slider.getValue());
                    saveConfig();
                }
        ));

        yPos -= 60.0f;

        // Mod Compatibility section
        settingsPanel.addUIElement(new ModLabel(
                "Mod Compatibility",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.charDescFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 50.0f;

        float toggleX = xPos;
        float toggleYOffset = -8.0f;
        float labelXOffset = 40.0f;

        // Ignore Special Tier checkbox
        settingsPanel.addUIElement(new ModToggleButton(
                toggleX, yPos + toggleYOffset,
                ignoreSpecialTier,
                false,
                settingsPanel,
                (button) -> {
                    ignoreSpecialTier = button.enabled;
                    saveConfig();
                }
        ));

        settingsPanel.addUIElement(new ModLabel(
                "Ignore Special tier relics",
                xPos + labelXOffset, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (label) -> {}
        ));

        yPos -= 25.0f;

        settingsPanel.addUIElement(new ModLabel(
                "Prevents adding choices for unique relics from other mods",
                xPos + labelXOffset, yPos,
                Settings.GOLD_COLOR,
                FontHelper.tipBodyFont,
                settingsPanel,
                (label) -> {}
        ));

        BaseMod.registerModBadge(
                badgeTexture,
                MOD_NAME,
                "Aaron",
                "Choose from multiple relic options. Configurable choices (default: 2).",
                settingsPanel
        );
    }

    private Texture createBadgeTexture() {
        // Create a simple 32x32 texture as a placeholder (standard mod badge size)
        // In a real mod, you'd load an actual image file
        com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(32, 32, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.GOLD);
        pixmap.fill();
        pixmap.setColor(Color.DARK_GRAY);
        pixmap.drawRectangle(0, 0, 32, 32);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
