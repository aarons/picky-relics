package pickyrelics.ui;

import basemod.IUIElement;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import pickyrelics.util.TierUtils;

import java.util.Map;

/**
 * UI component that displays probability distribution for tier outcomes.
 * Updates in real-time based on current settings.
 */
public class ProbabilityDisplay implements IUIElement {
    private final float x;
    private final float y;

    private static final float LINE_HEIGHT = 28.0f;
    private static final String[] TIER_NAMES = {"Common", "Uncommon", "Rare", "Shop", "Boss"};
    private static final Color[] TIER_COLORS = {
            Settings.GREEN_TEXT_COLOR,   // Common
            Settings.BLUE_TEXT_COLOR,    // Uncommon
            Settings.GOLD_COLOR,         // Rare
            Settings.GOLD_COLOR,         // Shop
            Settings.RED_TEXT_COLOR      // Boss
    };

    public ProbabilityDisplay(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void render(SpriteBatch sb) {
        float scaledX = x * Settings.scale;
        float scaledY = y * Settings.scale;
        float lineHeight = LINE_HEIGHT * Settings.scale;

        // Title
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                "Simulated odds from Common:",
                scaledX, scaledY, Settings.GOLD_COLOR);

        float currentY = scaledY - lineHeight * 1.3f;

        // Calculate probabilities
        Map<Integer, Double> probabilities = TierUtils.calculateTierProbabilities();

        // Display each tier with non-zero probability
        for (int position = 0; position <= 4; position++) {
            double prob = probabilities.getOrDefault(position, 0.0);

            // Show tiers with >= 0.1% probability
            if (prob >= 0.001) {
                String tierName = TIER_NAMES[position];
                String probText = String.format("  %-9s %5.1f%%", tierName, prob * 100);
                Color tierColor = TIER_COLORS[position];

                FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                        probText, scaledX, currentY, tierColor);

                currentY -= lineHeight;
            }
        }
    }

    @Override
    public void update() {
        // Probabilities are recalculated each render based on current settings
    }

    @Override
    public int renderLayer() {
        return 1;
    }

    @Override
    public int updateOrder() {
        return 1;
    }
}
