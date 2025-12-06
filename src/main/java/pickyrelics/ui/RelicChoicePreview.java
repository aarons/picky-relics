package pickyrelics.ui;

import basemod.IUIElement;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.Circlet;
import pickyrelics.PickyRelicsMod;

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

    private static final float ROW_HEIGHT = 50.0f;
    private static final float CHAIN_ICON_SIZE = 64.0f;
    private static final float ICON_SIZE = 64.0f;
    private static final float PANEL_WIDTH = 300.0f;
    private static final float PANEL_HEIGHT = 48.0f;
    private static final float BACKGROUND_PADDING = 40.0f;  // Wider padding for background
    private static final float BANNER_WIDTH = 506.0f;
    private static final float BANNER_HEIGHT = 130.0f;
    private static final Color BACKGROUND_TINT = new Color(0.55f, 0.55f, 0.65f, 1.0f);  // Lighter tint
    private static final Color SILHOUETTE_COLOR = new Color(0.3f, 0.3f, 0.35f, 1.0f);  // Dark gray for relic silhouette

    private static Texture circletTexture;
    private static Texture chainTexture;
    private static Texture bannerTexture;

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
        float startY = y * Settings.scale;

        // Calculate dimensions for the background
        float bannerW = BANNER_WIDTH * Settings.scale;
        float bannerH = BANNER_HEIGHT * Settings.scale;
        float panelW = PANEL_WIDTH * Settings.scale;
        float panelH = PANEL_HEIGHT * Settings.scale;
        float padding = BACKGROUND_PADDING * Settings.scale;

        // Calculate total height needed for background
        float contentHeight = count * ROW_HEIGHT * Settings.scale;
        float bgWidth = panelW + padding * 2;
        float bgHeight = contentHeight + bannerH + padding;

        // Draw the reward screen background (lightened)
        sb.setColor(BACKGROUND_TINT);
        float bgX = scaledX - padding;
        float bgY = startY - bgHeight + bannerH / 2;
        sb.draw(ImageMaster.REWARD_SCREEN_SHEET,
                bgX, bgY,
                bgWidth, bgHeight);
        sb.setColor(Color.WHITE);

        // Draw the banner behind "Loot Preview"
        loadBannerTexture();
        float bannerYOffset = 15.0f * Settings.scale;  // Move banner up
        if (bannerTexture != null) {
            float bannerX = scaledX + (panelW - bannerW) / 2.0f;
            float bannerY = startY - bannerH / 2.0f + bannerYOffset;
            sb.draw(bannerTexture,
                    bannerX, bannerY,
                    bannerW, bannerH);
        }

        // Preview title - "Loot Preview" centered on banner (moved up)
        float previewYOffset = 28.0f * Settings.scale; // was 30.0f
        FontHelper.renderFontCentered(sb, FontHelper.tipHeaderFont,
                "Loot Preview",
                scaledX + panelW / 2.0f, startY + previewYOffset,
                Settings.GOLD_COLOR);

        float currentY = startY - 55.0f * Settings.scale;  // More separation from header

        // Get tier info for labels
        String tierName = getTierDisplayText(tier);
        Color tierColor = getTierColor(tier);

        // Calculate center X for chain icons (centered with panel)
        float panelCenterX = scaledX + panelW / 2.0f;

        // Render each choice
        for (int i = 0; i < count; i++) {
            // Draw panel background
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.REWARD_SCREEN_ITEM,
                    scaledX,
                    currentY - panelH / 2.0f,
                    panelW, panelH);

            // Chain icon between items (not above first)
            if (i > 0) {
                float chainY = currentY + (ROW_HEIGHT / 2.0f) * Settings.scale;
                renderChain(sb, panelCenterX, chainY);
            }

            // Relic silhouette
            float silhouetteX = scaledX + 40.0f * Settings.scale;
            float silhouetteY = currentY;

            renderSilhouette(sb, silhouetteX, silhouetteY);

            // Relic name (displayed as "Circlet" placeholder)
            float labelX = scaledX + 65.0f * Settings.scale;
            float labelY = currentY + 8.0f * Settings.scale;

            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                    "Circlet",
                    labelX, labelY, Settings.CREAM_COLOR);

            // Tier label in bottom-right (when enabled)
            if (PickyRelicsMod.showTierLabels) {
                renderTierLabel(sb, scaledX, currentY, panelW, tierName, tierColor);
            }

            currentY -= ROW_HEIGHT * Settings.scale;
        }
    }

    /**
     * Load the banner texture from game assets.
     */
    private static void loadBannerTexture() {
        if (bannerTexture == null) {
            bannerTexture = ImageMaster.loadImage("images/ui/selectBanner.png");
        }
    }

    /**
     * Render the chain icon (already vertical, no rotation needed).
     */
    private void renderChain(SpriteBatch sb, float x, float y) {
        // Lazy-load chain texture from our resources
        if (chainTexture == null) {
            chainTexture = ImageMaster.loadImage("pickyrelics/chain_icon.png");
        }

        float size = CHAIN_ICON_SIZE * Settings.scale;
        sb.setColor(Color.WHITE);
        sb.draw(chainTexture,
                x - size / 2,
                y - size / 2,
                size, size);
    }

    private void renderSilhouette(SpriteBatch sb, float x, float y) {
        // Lazy-load Circlet texture (meta-joke: Circlet is the game's "no relics left" placeholder)
        if (circletTexture == null) {
            circletTexture = new Circlet().img;
        }

        // Draw Circlet icon with dark gray tint
        float size = ICON_SIZE * Settings.scale;
        sb.setColor(SILHOUETTE_COLOR);
        sb.draw(circletTexture,
                x - size / 2,
                y - size / 2,
                size, size);
        sb.setColor(Color.WHITE);
    }

    /**
     * Render tier label in bottom-right corner of panel.
     * Uses cardDescFont_N for cleaner rendering without heavy drop shadows.
     */
    private void renderTierLabel(SpriteBatch sb, float panelX, float centerY,
                                 float panelW, String tierText, Color tierColor) {
        // Reduce brightness by 10% for subtler appearance
        Color dimmed = tierColor.cpy();
        dimmed.r *= 0.9f;
        dimmed.g *= 0.9f;
        dimmed.b *= 0.9f;

        // Scale font to 80%
        float originalScaleX = FontHelper.cardDescFont_N.getData().scaleX;
        float originalScaleY = FontHelper.cardDescFont_N.getData().scaleY;
        FontHelper.cardDescFont_N.getData().setScale(originalScaleX * 0.8f, originalScaleY * 0.8f);

        // Position at right edge with margin, raised by 40% of line height
        float x = panelX + panelW - 15.0f * Settings.scale;
        float lineHeight = FontHelper.cardDescFont_N.getLineHeight();
        float y = centerY - 12.0f * Settings.scale + lineHeight * 0.4f;

        // Right-align text (measure with scaled font)
        FontHelper.layout.setText(FontHelper.cardDescFont_N, tierText);
        float textX = x - FontHelper.layout.width;

        FontHelper.cardDescFont_N.setColor(dimmed);
        FontHelper.cardDescFont_N.draw(sb, tierText, textX, y);

        // Restore original scale
        FontHelper.cardDescFont_N.getData().setScale(originalScaleX, originalScaleY);
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
