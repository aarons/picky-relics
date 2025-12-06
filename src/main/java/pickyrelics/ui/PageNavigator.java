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
 * A page navigator component showing "<< page X of Y >>" with clickable arrows.
 */
public class PageNavigator implements IUIElement {
    private final int totalPages;
    private final Supplier<Integer> currentPageSupplier;
    private final Consumer<Integer> onPageChange;
    private final float centerX;
    private final float y;
    private final Hitbox leftArrowHb;
    private final Hitbox rightArrowHb;

    private static final float ARROW_WIDTH = 50.0f;
    private static final float ARROW_HEIGHT = 40.0f;
    private static final float ARROW_OFFSET = 120.0f;
    private static final Color ARROW_COLOR = Settings.GOLD_COLOR;
    private static final Color TEXT_COLOR = Settings.CREAM_COLOR;
    private static final Color HOVER_COLOR = Settings.GREEN_TEXT_COLOR;

    public PageNavigator(int totalPages, float centerX, float y,
                         Supplier<Integer> currentPageSupplier, Consumer<Integer> onPageChange) {
        this.totalPages = totalPages;
        this.centerX = centerX;
        this.y = y;
        this.currentPageSupplier = currentPageSupplier;
        this.onPageChange = onPageChange;

        float leftArrowX = (centerX - ARROW_OFFSET - ARROW_WIDTH / 2) * Settings.scale;
        float rightArrowX = (centerX + ARROW_OFFSET - ARROW_WIDTH / 2) * Settings.scale;
        float arrowY = (y - ARROW_HEIGHT / 2) * Settings.scale;

        this.leftArrowHb = new Hitbox(leftArrowX, arrowY, ARROW_WIDTH * Settings.scale, ARROW_HEIGHT * Settings.scale);
        this.rightArrowHb = new Hitbox(rightArrowX, arrowY, ARROW_WIDTH * Settings.scale, ARROW_HEIGHT * Settings.scale);
    }

    @Override
    public void render(SpriteBatch sb) {
        int currentPage = currentPageSupplier.get();

        // Render left arrow
        Color leftColor = leftArrowHb.hovered ? HOVER_COLOR : ARROW_COLOR;
        FontHelper.renderFontCentered(sb, FontHelper.charDescFont, "<<",
                (centerX - ARROW_OFFSET) * Settings.scale, y * Settings.scale, leftColor);

        // Render page indicator
        String pageText = "page " + (currentPage + 1) + " of " + totalPages;
        FontHelper.renderFontCentered(sb, FontHelper.charDescFont, pageText,
                centerX * Settings.scale, y * Settings.scale, TEXT_COLOR);

        // Render right arrow
        Color rightColor = rightArrowHb.hovered ? HOVER_COLOR : ARROW_COLOR;
        FontHelper.renderFontCentered(sb, FontHelper.charDescFont, ">>",
                (centerX + ARROW_OFFSET) * Settings.scale, y * Settings.scale, rightColor);
    }

    @Override
    public void update() {
        leftArrowHb.update();
        rightArrowHb.update();

        if (leftArrowHb.hovered && InputHelper.justClickedLeft) {
            int currentPage = currentPageSupplier.get();
            int newPage = (currentPage - 1 + totalPages) % totalPages;
            onPageChange.accept(newPage);
        }

        if (rightArrowHb.hovered && InputHelper.justClickedLeft) {
            int currentPage = currentPageSupplier.get();
            int newPage = (currentPage + 1) % totalPages;
            onPageChange.accept(newPage);
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
