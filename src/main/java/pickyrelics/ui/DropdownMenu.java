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

/**
 * A dropdown menu that expands to show all options when clicked.
 */
public class DropdownMenu implements IUIElement {
    private final String[] options;
    private int selectedIndex;
    private final float x;
    private final float y;
    private final Consumer<Integer> onSelect;

    private boolean expanded = false;
    private final Hitbox mainHitbox;
    private final Hitbox[] optionHitboxes;

    private static final float ROW_HEIGHT = 32.0f;
    private static final float PADDING_X = 16.0f;
    private static final float PADDING_Y = 8.0f;
    private static final float ARROW_SPACE = 24.0f;

    private static final Color BG_COLOR = new Color(0.1f, 0.1f, 0.1f, 0.9f);
    private static final Color HOVER_BG_COLOR = new Color(0.2f, 0.2f, 0.3f, 0.9f);
    private static final Color BORDER_COLOR = new Color(0.5f, 0.5f, 0.5f, 1.0f);
    private static final Color TEXT_COLOR = Settings.CREAM_COLOR;
    private static final Color SELECTED_TEXT_COLOR = Settings.GOLD_COLOR;
    private static final Color HOVER_TEXT_COLOR = Settings.GREEN_TEXT_COLOR;

    private float menuWidth;

    public DropdownMenu(String[] options, int initialIndex, float x, float y, Consumer<Integer> onSelect) {
        this.options = options;
        this.selectedIndex = initialIndex;
        this.x = x;
        this.y = y;
        this.onSelect = onSelect;

        // Calculate menu width based on longest option
        this.menuWidth = 0;
        for (String option : options) {
            float textWidth = FontHelper.getWidth(FontHelper.tipBodyFont, option, 1.0f);
            if (textWidth > menuWidth) {
                menuWidth = textWidth;
            }
        }
        menuWidth += PADDING_X * 2 + ARROW_SPACE;

        // Main hitbox for the collapsed dropdown
        float scaledX = x * Settings.scale;
        float scaledY = (y - ROW_HEIGHT) * Settings.scale;
        float scaledWidth = menuWidth * Settings.scale;
        float scaledHeight = ROW_HEIGHT * Settings.scale;
        this.mainHitbox = new Hitbox(scaledX, scaledY, scaledWidth, scaledHeight);

        // Option hitboxes for expanded state
        this.optionHitboxes = new Hitbox[options.length];
        for (int i = 0; i < options.length; i++) {
            float optionY = (y - ROW_HEIGHT * (i + 2)) * Settings.scale;
            optionHitboxes[i] = new Hitbox(scaledX, optionY, scaledWidth, scaledHeight);
        }
    }

    public void setSelectedIndex(int index) {
        this.selectedIndex = index;
    }

    @Override
    public void render(SpriteBatch sb) {
        float scaledX = x * Settings.scale;
        float scaledY = y * Settings.scale;
        float scaledWidth = menuWidth * Settings.scale;
        float scaledRowHeight = ROW_HEIGHT * Settings.scale;

        // Render main dropdown box
        Color mainBgColor = mainHitbox.hovered ? HOVER_BG_COLOR : BG_COLOR;
        renderBox(sb, scaledX, scaledY - scaledRowHeight, scaledWidth, scaledRowHeight, mainBgColor);

        // Render selected text with arrow
        String displayText = options[selectedIndex];
        String arrowChar = expanded ? "▲" : "▼";
        Color textColor = mainHitbox.hovered ? HOVER_TEXT_COLOR : SELECTED_TEXT_COLOR;

        FontHelper.renderFont(sb, FontHelper.tipBodyFont, displayText,
                scaledX + PADDING_X * Settings.scale,
                scaledY - PADDING_Y * Settings.scale,
                textColor);

        FontHelper.renderFont(sb, FontHelper.tipBodyFont, arrowChar,
                scaledX + scaledWidth - (PADDING_X + 10) * Settings.scale,
                scaledY - PADDING_Y * Settings.scale,
                textColor);

        // Render expanded options
        if (expanded) {
            for (int i = 0; i < options.length; i++) {
                float optionY = scaledY - scaledRowHeight * (i + 1);

                boolean isHovered = optionHitboxes[i].hovered;
                boolean isSelected = (i == selectedIndex);

                Color optionBgColor = isHovered ? HOVER_BG_COLOR : BG_COLOR;
                renderBox(sb, scaledX, optionY - scaledRowHeight, scaledWidth, scaledRowHeight, optionBgColor);

                Color optionTextColor = isHovered ? HOVER_TEXT_COLOR : (isSelected ? SELECTED_TEXT_COLOR : TEXT_COLOR);
                FontHelper.renderFont(sb, FontHelper.tipBodyFont, options[i],
                        scaledX + PADDING_X * Settings.scale,
                        optionY - PADDING_Y * Settings.scale,
                        optionTextColor);
            }
        }
    }

    private void renderBox(SpriteBatch sb, float boxX, float boxY, float width, float height, Color bgColor) {
        // Draw background using the tip texture pattern
        sb.setColor(bgColor);

        // Use a simple filled rectangle approach with the white pixel texture
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
                boxX, boxY,
                width, height);

        // Draw border
        sb.setColor(BORDER_COLOR);
        float borderThickness = 1.0f * Settings.scale;

        // Top border
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, boxX, boxY + height - borderThickness, width, borderThickness);
        // Bottom border
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, boxX, boxY, width, borderThickness);
        // Left border
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, boxX, boxY, borderThickness, height);
        // Right border
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, boxX + width - borderThickness, boxY, borderThickness, height);
    }

    @Override
    public void update() {
        mainHitbox.update();

        if (expanded) {
            for (Hitbox hb : optionHitboxes) {
                hb.update();
            }
        }

        if (InputHelper.justClickedLeft) {
            if (mainHitbox.hovered) {
                expanded = !expanded;
            } else if (expanded) {
                // Check if an option was clicked
                boolean clickedOption = false;
                for (int i = 0; i < optionHitboxes.length; i++) {
                    if (optionHitboxes[i].hovered) {
                        selectedIndex = i;
                        expanded = false;
                        onSelect.accept(i);
                        clickedOption = true;
                        break;
                    }
                }
                // Close dropdown if clicked outside
                if (!clickedOption) {
                    expanded = false;
                }
            }
        }
    }

    @Override
    public int renderLayer() {
        // Render on top of other elements when expanded
        return expanded ? 10 : 1;
    }

    @Override
    public int updateOrder() {
        // Update first when expanded to capture clicks
        return expanded ? -10 : 1;
    }
}
