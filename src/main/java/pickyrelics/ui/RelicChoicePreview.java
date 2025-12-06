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
    private static final float CHAIN_ICON_SIZE = 48.0f;
    private static final float ICON_SIZE = 48.0f;
    private static final float PANEL_WIDTH = 300.0f;
    private static final float PANEL_HEIGHT = 48.0f;
    private static final float BACKGROUND_PADDING = 40.0f;  // Wider padding for background
    private static final float BANNER_WIDTH = 440.0f;       // 35% larger banner
    private static final float BANNER_HEIGHT = 100.0f;       // 40% larger banner
    private static final Color BACKGROUND_TINT = new Color(0.55f, 0.55f, 0.65f, 1.0f);  // Lighter tint

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
        float previewYOffset = 20.0f * Settings.scale;
        FontHelper.renderFontCentered(sb, FontHelper.tipHeaderFont,
                "Loot Preview",
                scaledX + panelW / 2.0f, startY + previewYOffset,
                Settings.GOLD_COLOR);

        float currentY = startY - 55.0f * Settings.scale;  // More separation from header

        // Get tier info for labels
        String tierName = getTierDisplayText(tier);
        Color tierColor = getDimmedTierColor(tier);

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

            renderSilhouette(sb, silhouetteX, silhouetteY, tierColor);

            // Tier label
            float labelX = scaledX + 65.0f * Settings.scale;
            float labelY = currentY + 8.0f * Settings.scale;

            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                    tierName + " Relic",
                    labelX, labelY, tierColor);

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

    private void renderSilhouette(SpriteBatch sb, float x, float y, Color tierColor) {
        // Lazy-load Circlet texture (meta-joke: Circlet is the game's "no relics left" placeholder)
        if (circletTexture == null) {
            circletTexture = new Circlet().img;
        }

        // Draw scaled-down Circlet icon with tier color tint
        float size = ICON_SIZE * Settings.scale;
        sb.setColor(tierColor);
        sb.draw(circletTexture,
                x - size / 2,
                y - size / 2,
                size, size);
        sb.setColor(Color.WHITE);
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

    /**
     * Get dimmed color for a relic tier (90% brightness, matching reward screen labels).
     */
    private static Color getDimmedTierColor(AbstractRelic.RelicTier tier) {
        Color baseColor = getTierColor(tier);
        Color dimmed = baseColor.cpy();
        dimmed.r *= 0.9f;
        dimmed.g *= 0.9f;
        dimmed.b *= 0.9f;
        return dimmed;
    }
}
