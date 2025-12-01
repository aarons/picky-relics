package pickyrelics;

import basemod.BaseMod;
import basemod.ModLabel;
import basemod.ModPanel;
import basemod.ModMinMaxSlider;
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
    private static final String CONFIG_COMBAT_CHOICES = "combatChoices";
    private static final String CONFIG_CHEST_CHOICES = "chestChoices";

    // Default: 2 total choices (1 = base game, 2-5 = mod choices)
    public static int combatChoices = 2;
    public static int chestChoices = 2;

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
            defaults.setProperty(CONFIG_COMBAT_CHOICES, "2");
            defaults.setProperty(CONFIG_CHEST_CHOICES, "2");

            config = new SpireConfig(MOD_ID, "config", defaults);

            combatChoices = clamp(config.getInt(CONFIG_COMBAT_CHOICES), 1, 5);
            chestChoices = clamp(config.getInt(CONFIG_CHEST_CHOICES), 1, 5);

            logger.info("Config loaded: combatChoices=" + combatChoices +
                    ", chestChoices=" + chestChoices);
        } catch (IOException e) {
            logger.error("Failed to load config", e);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void saveConfig() {
        try {
            config.setInt(CONFIG_COMBAT_CHOICES, combatChoices);
            config.setInt(CONFIG_CHEST_CHOICES, chestChoices);
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

        // Combat Rewards slider
        settingsPanel.addUIElement(new ModLabel(
                "Combat Rewards",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (label) -> {}
        ));

        settingsPanel.addUIElement(new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) combatChoices,
                "%.0f",
                settingsPanel,
                (slider) -> {
                    combatChoices = Math.round(slider.getValue());
                    saveConfig();
                }
        ));

        yPos -= 60.0f;

        // Treasure Chests slider
        settingsPanel.addUIElement(new ModLabel(
                "Treasure Chests",
                xPos, yPos,
                Settings.CREAM_COLOR,
                FontHelper.tipHeaderFont,
                settingsPanel,
                (label) -> {}
        ));

        settingsPanel.addUIElement(new ModMinMaxSlider(
                "",
                sliderX, yPos + sliderYOffset,
                1.0f, 5.0f, (float) chestChoices,
                "%.0f",
                settingsPanel,
                (slider) -> {
                    chestChoices = Math.round(slider.getValue());
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
