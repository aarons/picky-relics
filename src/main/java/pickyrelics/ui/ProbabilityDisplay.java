package pickyrelics.ui;

import basemod.IUIElement;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import pickyrelics.util.TierUtils;

import java.util.Map;

/**
 * UI component that displays probability distribution for tier outcomes as a table.
 * Updates in real-time based on current settings.
 */
public class ProbabilityDisplay implements IUIElement {
    private final float x;
    private final float y;

    private static final float LINE_HEIGHT = 28.0f;
    private static final float COLUMN_WIDTH = 90.0f;
    private static final float ROW_LABEL_WIDTH = 100.0f;

    private static final String[] TIER_NAMES = {"Common", "Uncommon", "Rare", "Shop", "Boss"};

    // All starting tiers for rows (Common=0, Uncommon=1, Rare=2, Shop=3, Boss=4)
    private static final int[] ROW_TIERS = {0, 1, 2, 3, 4};

    // All result tiers for columns
    private static final int[] COL_TIERS = {0, 1, 2, 3, 4};

    public ProbabilityDisplay(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void render(SpriteBatch sb) {
        float scaledX = x * Settings.scale;
        float scaledY = y * Settings.scale;
        float lineHeight = LINE_HEIGHT * Settings.scale;
        float columnWidth = COLUMN_WIDTH * Settings.scale;
        float rowLabelWidth = ROW_LABEL_WIDTH * Settings.scale;

        float currentY = scaledY;

        // Top axis label: "2nd Relic's Probability"
        float headerX = scaledX + rowLabelWidth;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                "2nd Relic's Probability",
                headerX, currentY, Settings.CREAM_COLOR);

        currentY -= lineHeight;

        // Column headers (full tier names)
        for (int colIdx = 0; colIdx < COL_TIERS.length; colIdx++) {
            int tierPos = COL_TIERS[colIdx];
            String tierName = TIER_NAMES[tierPos];

            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                    tierName, headerX + colIdx * columnWidth, currentY, Settings.CREAM_COLOR);
        }

        currentY -= lineHeight;

        // Left axis label: "Starting Relic:" - positioned at first data row
        FontHelper.renderFontRightTopAligned(sb, FontHelper.tipBodyFont,
                "Starting Relic:",
                scaledX + rowLabelWidth - 15.0f * Settings.scale, currentY, Settings.CREAM_COLOR);

        currentY -= lineHeight;

        // Data rows
        for (int startTier : ROW_TIERS) {
            String rowLabel = TIER_NAMES[startTier];

            // Calculate probabilities for this starting tier
            Map<Integer, Double> probabilities = TierUtils.calculateTierProbabilities(startTier);

            // Render row label (right-aligned)
            FontHelper.renderFontRightTopAligned(sb, FontHelper.tipBodyFont,
                    rowLabel, scaledX + rowLabelWidth - 15.0f * Settings.scale, currentY, Settings.CREAM_COLOR);

            // Render each column value
            for (int colIdx = 0; colIdx < COL_TIERS.length; colIdx++) {
                int resultTier = COL_TIERS[colIdx];
                double prob = probabilities.getOrDefault(resultTier, 0.0);

                String cellText;
                if (prob >= 0.001) {
                    cellText = String.format("%.1f%%", prob * 100);
                } else {
                    cellText = "-";
                }

                FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                        cellText, headerX + colIdx * columnWidth, currentY, Settings.CREAM_COLOR);
            }

            currentY -= lineHeight;
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
