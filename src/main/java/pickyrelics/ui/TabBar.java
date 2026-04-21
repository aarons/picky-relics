package pickyrelics.ui;

import basemod.IUIElement;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A horizontal tab bar. Each tab is a clickable labeled region; the active tab is
 * rendered with a gold underline and highlighted label. Uses WHITE_SQUARE_IMG for
 * fills — no custom image assets.
 */
public class TabBar implements IUIElement {
    private final String[] labels;
    private final float centerX;
    private final float y;
    private final Supplier<Integer> currentTabSupplier;
    private final Consumer<Integer> onTabChange;
    private final Hitbox[] hitboxes;

    private static final float TAB_WIDTH = 200.0f;
    private static final float TAB_HEIGHT = 44.0f;
    private static final float TAB_GAP = 8.0f;
    private static final float UNDERLINE_HEIGHT = 3.0f;

    private static final Color ACTIVE_BG = new Color(1.0f, 0.9f, 0.5f, 0.12f);
    private static final Color INACTIVE_BG = new Color(0.0f, 0.0f, 0.0f, 0.25f);
    private static final Color HOVER_BG = new Color(1.0f, 1.0f, 1.0f, 0.18f);

    public TabBar(String[] labels, float centerX, float y,
                  Supplier<Integer> currentTabSupplier, Consumer<Integer> onTabChange) {
        this.labels = labels;
        this.centerX = centerX;
        this.y = y;
        this.currentTabSupplier = currentTabSupplier;
        this.onTabChange = onTabChange;
        this.hitboxes = new Hitbox[labels.length];

        float totalWidth = labels.length * TAB_WIDTH + (labels.length - 1) * TAB_GAP;
        float startX = centerX - totalWidth / 2.0f;
        float scaledWidth = TAB_WIDTH * Settings.scale;
        float scaledHeight = TAB_HEIGHT * Settings.scale;
        float hitboxY = (y - TAB_HEIGHT / 2.0f) * Settings.scale;

        for (int i = 0; i < labels.length; i++) {
            float hitboxX = (startX + i * (TAB_WIDTH + TAB_GAP)) * Settings.scale;
            hitboxes[i] = new Hitbox(hitboxX, hitboxY, scaledWidth, scaledHeight);
        }
    }

    @Override
    public void update() {
        for (int i = 0; i < hitboxes.length; i++) {
            hitboxes[i].update();
            if (hitboxes[i].hovered && InputHelper.justClickedLeft) {
                onTabChange.accept(i);
            }
        }
    }

    @Override
    public void render(SpriteBatch sb) {
        int activeTab = currentTabSupplier.get();

        for (int i = 0; i < hitboxes.length; i++) {
            Hitbox hb = hitboxes[i];
            boolean isActive = i == activeTab;

            Color bgColor = isActive ? ACTIVE_BG : (hb.hovered ? HOVER_BG : INACTIVE_BG);
            drawRect(sb, bgColor, hb.x, hb.y, hb.width, hb.height);

            if (isActive) {
                drawRect(sb, Settings.GOLD_COLOR, hb.x,
                        hb.y - UNDERLINE_HEIGHT * Settings.scale,
                        hb.width, UNDERLINE_HEIGHT * Settings.scale);
            }

            Color labelColor = isActive ? Settings.GOLD_COLOR
                    : (hb.hovered ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR);
            FontHelper.renderFontCentered(sb, FontHelper.charDescFont, labels[i],
                    hb.x + hb.width / 2.0f, hb.y + hb.height / 2.0f, labelColor);
        }
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
