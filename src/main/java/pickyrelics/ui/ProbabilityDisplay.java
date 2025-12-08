package pickyrelics.ui;

import basemod.IUIElement;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.UIStrings;
import pickyrelics.PickyRelicsMod;
import pickyrelics.util.TierUtils;

import java.util.Map;

/**
 * UI component that displays probability distribution for tier outcomes as a table.
 * Updates in real-time based on current settings.
 */
public class ProbabilityDisplay implements IUIElement {
    private final float x;
    private final float y;

    // Layout constants
    private static final float LINE_HEIGHT = 28.0f;
    private static final float ROW_LABEL_WIDTH = 115.0f;  // Extra space for "Uncommon"
    private static final float LEFT_MARGIN_WIDTH = 70.0f;  // Shifted left
    private static final float BRACKET_GAP = 15.0f;

    // Variable column widths: Common, Uncommon, Rare, Shop, Boss
    private static final float[] COLUMN_WIDTHS = {95.0f, 115.0f, 75.0f, 75.0f, 70.0f};

    // Line rendering constants
    private static final float LINE_THICKNESS = 1.5f;
    private static final float BRACKET_CAP_LENGTH = 6.0f;

    // Colors for visual hierarchy
    private static final Color LABEL_COLOR = new Color(
            Settings.CREAM_COLOR.r,
            Settings.CREAM_COLOR.g,
            Settings.CREAM_COLOR.b,
            0.75f
    );
    private static final Color IMPOSSIBLE_COLOR = new Color(
            Settings.CREAM_COLOR.r,
            Settings.CREAM_COLOR.g,
            Settings.CREAM_COLOR.b,
            0.35f
    );
    private static final Color LINE_COLOR = new Color(
            Settings.CREAM_COLOR.r,
            Settings.CREAM_COLOR.g,
            Settings.CREAM_COLOR.b,
            0.5f
    );

    // Lazy-loaded localized strings
    private static UIStrings probabilityStrings;
    private static String[] TEXT;

    private static void ensureStringsLoaded() {
        if (probabilityStrings == null) {
            probabilityStrings = CardCrawlGame.languagePack.getUIString(PickyRelicsMod.makeID("Probability"));
            TEXT = probabilityStrings.TEXT;
        }
    }

    private static String getTierName(int tierPosition) {
        switch (tierPosition) {
            case 0: return TierUtils.getTierDisplayText(com.megacrit.cardcrawl.relics.AbstractRelic.RelicTier.COMMON);
            case 1: return TierUtils.getTierDisplayText(com.megacrit.cardcrawl.relics.AbstractRelic.RelicTier.UNCOMMON);
            case 2: return TierUtils.getTierDisplayText(com.megacrit.cardcrawl.relics.AbstractRelic.RelicTier.RARE);
            case 3: return TierUtils.getTierDisplayText(com.megacrit.cardcrawl.relics.AbstractRelic.RelicTier.SHOP);
            case 4: return TierUtils.getTierDisplayText(com.megacrit.cardcrawl.relics.AbstractRelic.RelicTier.BOSS);
            default: return "";
        }
    }

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
        ensureStringsLoaded();
        float scale = Settings.scale;
        float lineHeight = LINE_HEIGHT * scale;
        float rowLabelWidth = ROW_LABEL_WIDTH * scale;
        float leftMargin = LEFT_MARGIN_WIDTH * scale;
        float bracketGap = BRACKET_GAP * scale;
        float thickness = LINE_THICKNESS * scale;
        float capLength = BRACKET_CAP_LENGTH * scale;

        // Pre-calculate scaled column widths and positions
        float[] colWidths = new float[COLUMN_WIDTHS.length];
        float[] colPositions = new float[COLUMN_WIDTHS.length];
        float totalColWidth = 0;
        for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
            colWidths[i] = COLUMN_WIDTHS[i] * scale;
            colPositions[i] = totalColWidth;
            totalColWidth += colWidths[i];
        }

        // Zone positions (left to right)
        float labelZoneX = x * scale;                              // "Starting Relic"
        float bracketLineX = labelZoneX + leftMargin;              // Vertical bracket line
        float rowLabelX = bracketLineX + bracketGap;               // Tier name labels
        float dataX = rowLabelX + rowLabelWidth;                   // Data columns start

        float currentY = y * scale;

        // 1. Column header - centered over data columns
        float headerCenterX = dataX + totalColWidth / 2.0f;
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
                TEXT[0],
                headerCenterX, currentY - lineHeight * 0.4f, Settings.CREAM_COLOR);

        currentY -= lineHeight;

        // 2. Underline - actual horizontal line
        float underlineY = currentY - lineHeight * 0.3f;
        float underlineWidth = totalColWidth - 20.0f * scale;
        drawHorizontalLine(sb, headerCenterX - underlineWidth / 2.0f, underlineY, underlineWidth, thickness);

        currentY -= lineHeight * 0.7f;

        // 3. Column tier headers
        for (int colIdx = 0; colIdx < COL_TIERS.length; colIdx++) {
            String tierName = getTierName(COL_TIERS[colIdx]);
            float colX = dataX + colPositions[colIdx];
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                    tierName, colX, currentY, Settings.CREAM_COLOR);
        }

        currentY -= lineHeight;
        float firstDataY = currentY;

        // 4. Data rows with tier labels
        for (int rowIdx = 0; rowIdx < ROW_TIERS.length; rowIdx++) {
            int startTier = ROW_TIERS[rowIdx];
            String rowLabel = getTierName(startTier);

            // Row label (tier name)
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                    rowLabel, rowLabelX, currentY, LABEL_COLOR);

            // Data cells
            Map<Integer, Double> probabilities = TierUtils.calculateTierProbabilities(startTier);
            for (int colIdx = 0; colIdx < COL_TIERS.length; colIdx++) {
                int resultTier = COL_TIERS[colIdx];
                double prob = probabilities.getOrDefault(resultTier, 0.0);

                String cellText;
                Color cellColor;
                if (prob >= 0.001) {
                    cellText = String.format("%.0f%%", prob * 100);
                    cellColor = Settings.CREAM_COLOR;
                } else {
                    cellText = "-";
                    cellColor = IMPOSSIBLE_COLOR;
                }

                float colX = dataX + colPositions[colIdx];
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                        cellText, colX, currentY, cellColor);
            }

            currentY -= lineHeight;
        }

        float lastDataY = currentY + lineHeight;

        // 5. Draw bracket lines connecting rows to axis label
        // Vertical line spans from first row to last row
        float bracketTopY = firstDataY - lineHeight * 0.35f;
        float bracketBottomY = lastDataY - lineHeight * 0.65f;
        drawVerticalLine(sb, bracketLineX, bracketBottomY, bracketTopY - bracketBottomY, thickness);

        // Top horizontal cap
        drawHorizontalLine(sb, bracketLineX, bracketTopY, capLength, thickness);

        // Bottom horizontal cap
        drawHorizontalLine(sb, bracketLineX, bracketBottomY, capLength, thickness);

        // 6. Axis label - stacked vertically, centered against data rows
        float labelCenterY = firstDataY - lineHeight * 2;
        float labelRightEdge = bracketLineX - 8.0f * scale;
        FontHelper.renderFontRightTopAligned(sb, FontHelper.tipBodyFont,
                TEXT[1], labelRightEdge, labelCenterY, LABEL_COLOR);
        FontHelper.renderFontRightTopAligned(sb, FontHelper.tipBodyFont,
                TEXT[2], labelRightEdge, labelCenterY - lineHeight, LABEL_COLOR);
    }

    /**
     * Draw a horizontal line using WHITE_SQUARE_IMG texture.
     */
    private void drawHorizontalLine(SpriteBatch sb, float x, float y, float width, float thickness) {
        sb.setColor(LINE_COLOR);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y - thickness / 2.0f, width, thickness);
        sb.setColor(Color.WHITE);
    }

    /**
     * Draw a vertical line using WHITE_SQUARE_IMG texture.
     */
    private void drawVerticalLine(SpriteBatch sb, float x, float y, float height, float thickness) {
        sb.setColor(LINE_COLOR);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - thickness / 2.0f, y, thickness, height);
        sb.setColor(Color.WHITE);
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
