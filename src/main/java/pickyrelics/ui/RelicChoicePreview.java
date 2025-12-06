package pickyrelics.ui;

import basemod.IUIElement;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.relics.AbstractRelic;

import java.util.function.Supplier;

/**
 * Visual preview component that shows what the reward screen will look like
 * for the currently selected tier.
 */
public class RelicChoicePreview implements IUIElement {
    private final float x;
    private final float y;
    private final Supplier<AbstractRelic.RelicTier> tierSupplier;
    private final Supplier<Integer> countSupplier;

    private static final float ROW_HEIGHT = 70.0f;
    private static final float CHAIN_ICON_SIZE = 24.0f;

    public RelicChoicePreview(float x, float y,
                              Supplier<AbstractRelic.RelicTier> tierSupplier,
                              Supplier<Integer> countSupplier) {
        this.x = x;
        this.y = y;
        this.tierSupplier = tierSupplier;
        this.countSupplier = countSupplier;
    }

    @Override
    public void render(SpriteBatch sb) {
        AbstractRelic.RelicTier tier = tierSupplier.get();
        int count = countSupplier.get();

        float scaledX = x * Settings.scale;
        float currentY = y * Settings.scale;

        // Preview title
        String tierName = getTierDisplayText(tier);
        Color tierColor = getTierColor(tier);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipHeaderFont,
                "Preview: " + tierName,
                scaledX, currentY, Settings.CREAM_COLOR);

        currentY -= 35.0f * Settings.scale;

        // Render each choice
        for (int i = 0; i < count; i++) {
            // Chain icon between items (not above first)
            if (i > 0) {
                float chainY = currentY + 25.0f * Settings.scale;
                float chainX = scaledX + 20.0f * Settings.scale;

                // Draw chain icon (scaled down)
                sb.setColor(Color.WHITE);
                float chainSize = CHAIN_ICON_SIZE * Settings.scale;
                sb.draw(ImageMaster.RELIC_LINKED,
                        chainX - chainSize / 2,
                        chainY - chainSize / 2,
                        chainSize, chainSize);
            }

            // Relic silhouette (circle with ?)
            float silhouetteX = scaledX + 20.0f * Settings.scale;
            float silhouetteY = currentY;

            renderSilhouette(sb, silhouetteX, silhouetteY, tierColor);

            // Tier label
            float labelX = scaledX + 50.0f * Settings.scale;
            float labelY = currentY + 6.0f * Settings.scale;

            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                    tierName + " Relic",
                    labelX, labelY, tierColor);

            currentY -= ROW_HEIGHT * Settings.scale;
        }
    }

    private void renderSilhouette(SpriteBatch sb, float x, float y, Color tierColor) {
        // Draw "[?]" as a simple text-based silhouette
        FontHelper.renderFontCentered(sb, FontHelper.tipHeaderFont,
                "[?]", x, y, tierColor);
    }

    @Override
    public void update() {
        // No update logic needed
    }

    @Override
    public int renderLayer() {
        return 1;
    }

    @Override
    public int updateOrder() {
        return 1;
    }

    /**
     * Get display text for a relic tier.
     */
    private static String getTierDisplayText(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case STARTER:  return "Starter";
            case COMMON:   return "Common";
            case UNCOMMON: return "Uncommon";
            case RARE:     return "Rare";
            case BOSS:     return "Boss";
            case SHOP:     return "Shop";
            case SPECIAL:  return "Event";
            default:       return "";
        }
    }

    /**
     * Get color for a relic tier.
     */
    private static Color getTierColor(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case STARTER:  return Settings.PURPLE_COLOR;
            case COMMON:   return Settings.GREEN_TEXT_COLOR;
            case UNCOMMON: return Settings.BLUE_TEXT_COLOR;
            case RARE:     return Settings.GOLD_COLOR;
            case BOSS:     return Settings.RED_TEXT_COLOR;
            case SHOP:     return Settings.GOLD_COLOR;
            case SPECIAL:  return Settings.PURPLE_COLOR;
            default:       return Settings.CREAM_COLOR;
        }
    }
}
