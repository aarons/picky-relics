package pickyrelics.ui;

import basemod.IUIElement;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A tab button for switching between settings pages.
 */
public class TabButton implements IUIElement {
    private final String label;
    private final int pageIndex;
    private final Supplier<Integer> currentPageSupplier;
    private final Consumer<Integer> onPageSelect;
    private final float x;
    private final float y;
    private final Hitbox hb;

    private static final float BUTTON_WIDTH = 160.0f;
    private static final float BUTTON_HEIGHT = 40.0f;
    private static final Color ACTIVE_COLOR = Settings.GOLD_COLOR;
    private static final Color INACTIVE_COLOR = Settings.CREAM_COLOR;
    private static final Color HOVER_COLOR = Settings.GREEN_TEXT_COLOR;

    public TabButton(String label, int pageIndex, float x, float y,
                     Supplier<Integer> currentPageSupplier, Consumer<Integer> onPageSelect) {
        this.label = label;
        this.pageIndex = pageIndex;
        this.x = x;
        this.y = y;
        this.currentPageSupplier = currentPageSupplier;
        this.onPageSelect = onPageSelect;
        this.hb = new Hitbox(x * Settings.scale, (y - BUTTON_HEIGHT / 2) * Settings.scale,
                BUTTON_WIDTH * Settings.scale, BUTTON_HEIGHT * Settings.scale);
    }

    @Override
    public void render(SpriteBatch sb) {
        boolean isActive = currentPageSupplier.get() == pageIndex;
        boolean isHovered = hb.hovered;

        Color textColor;
        if (isActive) {
            textColor = ACTIVE_COLOR;
        } else if (isHovered) {
            textColor = HOVER_COLOR;
        } else {
            textColor = INACTIVE_COLOR;
        }

        // Render underline for active tab
        if (isActive) {
            float lineY = (y - 15.0f) * Settings.scale;
            sb.setColor(ACTIVE_COLOR);
            // Simple underline using font rendering
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipHeaderFont,
                    "_______________", x * Settings.scale, lineY, ACTIVE_COLOR);
        }

        // Render label
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.charDescFont,
                label, x * Settings.scale, y * Settings.scale, textColor);
    }

    @Override
    public void update() {
        hb.update();
        if (hb.hovered && InputHelper.justClickedLeft) {
            onPageSelect.accept(pageIndex);
        }
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
