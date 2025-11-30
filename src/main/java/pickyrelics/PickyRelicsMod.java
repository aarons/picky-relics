package pickyrelics;

import basemod.BaseMod;
import basemod.ModLabeledToggleButton;
import basemod.ModPanel;
import basemod.ModSlider;
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
    private static final String CONFIG_NUM_CHOICES = "numChoices";
    private static final String CONFIG_APPLY_TO_CHESTS = "applyToChests";
    private static final String CONFIG_APPLY_TO_EVENTS = "applyToEvents";

    // Default: 2 choices (like elite fights give less than boss fights)
    public static int numChoices = 2;
    public static boolean applyToChests = true;
    public static boolean applyToEvents = true;

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
            defaults.setProperty(CONFIG_NUM_CHOICES, "2");
            defaults.setProperty(CONFIG_APPLY_TO_CHESTS, "true");
            defaults.setProperty(CONFIG_APPLY_TO_EVENTS, "true");

            config = new SpireConfig(MOD_ID, "config", defaults);

            numChoices = config.getInt(CONFIG_NUM_CHOICES);
            applyToChests = config.getBool(CONFIG_APPLY_TO_CHESTS);
            applyToEvents = config.getBool(CONFIG_APPLY_TO_EVENTS);

            // Clamp numChoices to valid range
            if (numChoices < 1) numChoices = 1;
            if (numChoices > 5) numChoices = 5;

            logger.info("Config loaded: numChoices=" + numChoices +
                    ", applyToChests=" + applyToChests +
                    ", applyToEvents=" + applyToEvents);
        } catch (IOException e) {
            logger.error("Failed to load config", e);
        }
    }

    public static void saveConfig() {
        try {
            config.setInt(CONFIG_NUM_CHOICES, numChoices);
            config.setBool(CONFIG_APPLY_TO_CHESTS, applyToChests);
            config.setBool(CONFIG_APPLY_TO_EVENTS, applyToEvents);
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

        // Title
        float yPos = 750.0f;
        float xPos = 350.0f;

        // Number of choices slider
        settingsPanel.addUIElement(new ModSlider(
                "Relic Choices",
                xPos, yPos,
                1.0f, 5.0f, numChoices,
                "%d",
                settingsPanel,
                (slider) -> {
                    numChoices = Math.round(slider.getValue());
                    saveConfig();
                }
        ));

        yPos -= 80.0f;

        // Apply to chests toggle
        settingsPanel.addUIElement(new ModLabeledToggleButton(
                "Apply to treasure chests",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.charDescFont,
                applyToChests,
                settingsPanel,
                (label) -> {},
                (button) -> {
                    applyToChests = button.enabled;
                    saveConfig();
                }
        ));

        yPos -= 50.0f;

        // Apply to events toggle
        settingsPanel.addUIElement(new ModLabeledToggleButton(
                "Apply to event rewards",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.charDescFont,
                applyToEvents,
                settingsPanel,
                (label) -> {},
                (button) -> {
                    applyToEvents = button.enabled;
                    saveConfig();
                }
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
        // Create a simple 64x64 texture as a placeholder
        // In a real mod, you'd load an actual image file
        com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(64, 64, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.GOLD);
        pixmap.fill();
        pixmap.setColor(Color.DARK_GRAY);
        pixmap.drawRectangle(0, 0, 64, 64);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
