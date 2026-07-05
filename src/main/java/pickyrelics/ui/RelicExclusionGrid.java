package pickyrelics.ui;

import basemod.IUIElement;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import pickyrelics.PickyRelicsMod;
import pickyrelics.util.TierUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Browsable relic grid for the Exclusions settings tab. A row of tier filter
 * buttons (plus an "Excluded only" view) selects which relics are shown; clicking
 * a relic toggles its excluded state, rendered as a dimmed icon with a red X.
 *
 * Relic lists are read lazily each frame (they're empty early in the mod
 * lifecycle and can grow as other mods register relics), following the pattern
 * in RelicChoicePreview.
 */
public class RelicExclusionGrid implements IUIElement {

    // Tier filter buttons in display order (matches the Choices tab's slider order)
    private static final AbstractRelic.RelicTier[] FILTER_TIERS = {
            AbstractRelic.RelicTier.STARTER,
            AbstractRelic.RelicTier.COMMON,
            AbstractRelic.RelicTier.UNCOMMON,
            AbstractRelic.RelicTier.RARE,
            AbstractRelic.RelicTier.SHOP,
            AbstractRelic.RelicTier.SPECIAL,
            AbstractRelic.RelicTier.BOSS,
    };
    // Index of the "Excluded only" pseudo-filter (after the tier buttons)
    private static final int EXCLUDED_ONLY = FILTER_TIERS.length;

    private static final float FILTER_WIDTH = 118.0f;
    private static final float EXCLUDED_FILTER_WIDTH = 170.0f;
    private static final float FILTER_HEIGHT = 34.0f;
    private static final float FILTER_GAP = 8.0f;
    private static final float UNDERLINE_HEIGHT = 3.0f;

    private static final int COLS = 10;
    private static final int ROWS = 4;
    private static final int PAGE_SIZE = COLS * ROWS;
    private static final float CELL_SPACING = 72.0f;
    private static final float ICON_SIZE = 64.0f;
    private static final float GRID_TOP_OFFSET = 84.0f; // filter row bottom -> first row center

    private static final float ARROW_SIZE = 48.0f;

    private static final Color ACTIVE_BG = new Color(1.0f, 0.9f, 0.5f, 0.12f);
    private static final Color INACTIVE_BG = new Color(0.0f, 0.0f, 0.0f, 0.25f);
    private static final Color HOVER_BG = new Color(1.0f, 1.0f, 1.0f, 0.18f);
    private static final Color EXCLUDED_TINT = new Color(0.3f, 0.3f, 0.35f, 1.0f);
    private static final Color X_COLOR = new Color(0.9f, 0.15f, 0.15f, 1.0f);
    private static final float X_LENGTH = 46.0f;
    private static final float X_THICKNESS = 6.0f;

    private final float x;       // left edge, virtual 1920x1080 coords
    private final float topY;    // top of the filter row
    private final String[] TEXT; // pickyrelics:Exclusions strings

    private final Hitbox[] filterHitboxes = new Hitbox[FILTER_TIERS.length + 1];
    private final Hitbox[] cellHitboxes = new Hitbox[PAGE_SIZE];
    private final Hitbox prevHitbox;
    private final Hitbox nextHitbox;

    private int selectedFilter = 1; // default to Common
    private int page = 0;

    // Computed each update(), consumed by render()
    private List<AbstractRelic> visibleRelics = new ArrayList<>();
    private int pageCount = 1;

    public RelicExclusionGrid(float x, float topY, String[] text) {
        this.x = x;
        this.topY = topY;
        this.TEXT = text;

        float filterY = (topY - FILTER_HEIGHT) * Settings.scale;
        float filterX = x;
        for (int i = 0; i < filterHitboxes.length; i++) {
            float width = (i == EXCLUDED_ONLY) ? EXCLUDED_FILTER_WIDTH : FILTER_WIDTH;
            filterHitboxes[i] = new Hitbox(filterX * Settings.scale, filterY,
                    width * Settings.scale, FILTER_HEIGHT * Settings.scale);
            filterX += width + FILTER_GAP;
        }

        for (int i = 0; i < PAGE_SIZE; i++) {
            float centerX = cellCenterX(i % COLS);
            float centerY = cellCenterY(i / COLS);
            cellHitboxes[i] = new Hitbox(
                    (centerX - ICON_SIZE / 2.0f) * Settings.scale,
                    (centerY - ICON_SIZE / 2.0f) * Settings.scale,
                    ICON_SIZE * Settings.scale, ICON_SIZE * Settings.scale);
        }

        prevHitbox = pageArrowHitbox(pageControlsX());
        nextHitbox = pageArrowHitbox(pageControlsX() + 160.0f);
    }

    private float cellCenterX(int col) {
        return x + CELL_SPACING / 2.0f + col * CELL_SPACING;
    }

    private float cellCenterY(int row) {
        return topY - GRID_TOP_OFFSET - row * CELL_SPACING;
    }

    /** Page controls sit to the right of the grid, level with its first row. */
    private float pageControlsX() {
        return x + COLS * CELL_SPACING + 90.0f;
    }

    private Hitbox pageArrowHitbox(float centerX) {
        float centerY = cellCenterY(0);
        return new Hitbox((centerX - ARROW_SIZE / 2.0f) * Settings.scale,
                (centerY - ARROW_SIZE / 2.0f) * Settings.scale,
                ARROW_SIZE * Settings.scale, ARROW_SIZE * Settings.scale);
    }

    /**
     * All relics for the current filter. The "Excluded only" view walks every
     * tier list, so excluded IDs whose mod isn't installed simply don't render
     * (they stay in the config untouched).
     */
    private List<AbstractRelic> relicsForFilter() {
        if (selectedFilter == EXCLUDED_ONLY) {
            List<AbstractRelic> excluded = new ArrayList<>();
            for (AbstractRelic.RelicTier tier : FILTER_TIERS) {
                for (AbstractRelic relic : PickyRelicsMod.getRelicListForTier(tier)) {
                    if (PickyRelicsMod.isExcluded(relic.relicId)) {
                        excluded.add(relic);
                    }
                }
            }
            return excluded;
        }
        return PickyRelicsMod.getRelicListForTier(FILTER_TIERS[selectedFilter]);
    }

    @Override
    public void update() {
        for (int i = 0; i < filterHitboxes.length; i++) {
            filterHitboxes[i].update();
            if (filterHitboxes[i].hovered && InputHelper.justClickedLeft) {
                if (selectedFilter != i) {
                    selectedFilter = i;
                    page = 0;
                }
            }
        }

        List<AbstractRelic> all = relicsForFilter();
        pageCount = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pageCount) {
            page = pageCount - 1; // list can shrink (e.g. un-excluding in Excluded only view)
        }

        if (pageCount > 1) {
            prevHitbox.update();
            nextHitbox.update();
            if (prevHitbox.hovered && InputHelper.justClickedLeft && page > 0) {
                page--;
            }
            if (nextHitbox.hovered && InputHelper.justClickedLeft && page < pageCount - 1) {
                page++;
            }
        }

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, all.size());
        visibleRelics = new ArrayList<>(all.subList(start, end));

        for (int i = 0; i < visibleRelics.size(); i++) {
            cellHitboxes[i].update();
            if (cellHitboxes[i].hovered && InputHelper.justClickedLeft) {
                AbstractRelic relic = visibleRelics.get(i);
                PickyRelicsMod.setExcluded(relic.relicId, !PickyRelicsMod.isExcluded(relic.relicId));
            }
        }
    }

    @Override
    public void render(SpriteBatch sb) {
        renderFilterRow(sb);

        if (visibleRelics.isEmpty() && selectedFilter == EXCLUDED_ONLY) {
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, TEXT[3],
                    (x + COLS * CELL_SPACING / 2.0f) * Settings.scale,
                    cellCenterY(1) * Settings.scale,
                    Settings.CREAM_COLOR);
            return;
        }

        AbstractRelic hovered = null;
        for (int i = 0; i < visibleRelics.size(); i++) {
            AbstractRelic relic = visibleRelics.get(i);
            renderCell(sb, relic, cellHitboxes[i]);
            if (cellHitboxes[i].hovered) {
                hovered = relic;
            }
        }

        if (pageCount > 1) {
            renderPageControls(sb);
        }

        if (hovered != null) {
            TipHelper.renderGenericTip(
                    InputHelper.mX + 40.0f * Settings.scale,
                    InputHelper.mY + 40.0f * Settings.scale,
                    hovered.name, hovered.description);
        }
    }

    private void renderFilterRow(SpriteBatch sb) {
        int excludedCount = PickyRelicsMod.getExcludedRelicIds().size();

        for (int i = 0; i < filterHitboxes.length; i++) {
            Hitbox hb = filterHitboxes[i];
            boolean isActive = i == selectedFilter;

            Color bgColor = isActive ? ACTIVE_BG : (hb.hovered ? HOVER_BG : INACTIVE_BG);
            drawRect(sb, bgColor, hb.x, hb.y, hb.width, hb.height);
            if (isActive) {
                drawRect(sb, Settings.GOLD_COLOR, hb.x,
                        hb.y - UNDERLINE_HEIGHT * Settings.scale,
                        hb.width, UNDERLINE_HEIGHT * Settings.scale);
            }

            String label = (i == EXCLUDED_ONLY)
                    ? TEXT[1] + " (" + excludedCount + ")"
                    : TierUtils.getTierDisplayText(FILTER_TIERS[i]);
            Color labelColor = isActive ? Settings.GOLD_COLOR
                    : (hb.hovered ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR);
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, label,
                    hb.x + hb.width / 2.0f, hb.y + hb.height / 2.0f, labelColor);
        }
    }

    private void renderCell(SpriteBatch sb, AbstractRelic relic, Hitbox hb) {
        boolean excluded = PickyRelicsMod.isExcluded(relic.relicId);

        if (hb.hovered) {
            drawRect(sb, HOVER_BG, hb.x, hb.y, hb.width, hb.height);
        }

        if (relic.img != null) {
            sb.setColor(excluded ? EXCLUDED_TINT : Color.WHITE);
            sb.draw(relic.img, hb.x, hb.y, hb.width, hb.height);
            sb.setColor(Color.WHITE);
        }

        if (excluded) {
            renderRedX(sb, hb.x + hb.width / 2.0f, hb.y + hb.height / 2.0f);
        }
    }

    /** Two crossed bars over the icon center. */
    private void renderRedX(SpriteBatch sb, float centerX, float centerY) {
        Texture square = ImageMaster.WHITE_SQUARE_IMG;
        float length = X_LENGTH * Settings.scale;
        float thickness = X_THICKNESS * Settings.scale;

        sb.setColor(X_COLOR);
        for (int rotation = -45; rotation <= 45; rotation += 90) {
            sb.draw(square,
                    centerX - length / 2.0f, centerY - thickness / 2.0f,
                    length / 2.0f, thickness / 2.0f,
                    length, thickness,
                    1.0f, 1.0f, rotation,
                    0, 0, square.getWidth(), square.getHeight(),
                    false, false);
        }
        sb.setColor(Color.WHITE);
    }

    private void renderPageControls(SpriteBatch sb) {
        renderArrow(sb, ImageMaster.CF_LEFT_ARROW, prevHitbox, page > 0);
        renderArrow(sb, ImageMaster.CF_RIGHT_ARROW, nextHitbox, page < pageCount - 1);

        String pageText = String.format(TEXT[2], page + 1, pageCount);
        float textCenterX = (prevHitbox.x + nextHitbox.x + nextHitbox.width) / 2.0f;
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, pageText,
                textCenterX, prevHitbox.y + prevHitbox.height / 2.0f,
                Settings.CREAM_COLOR);
    }

    private void renderArrow(SpriteBatch sb, Texture arrow, Hitbox hb, boolean enabled) {
        sb.setColor(enabled ? (hb.hovered ? Color.WHITE : Settings.CREAM_COLOR) : Color.DARK_GRAY);
        sb.draw(arrow, hb.x, hb.y, hb.width, hb.height);
        sb.setColor(Color.WHITE);
    }

    private void drawRect(SpriteBatch sb, Color color, float x, float y, float width, float height) {
        sb.setColor(color);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, width, height);
        sb.setColor(Color.WHITE);
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
