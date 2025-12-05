package pickyrelics.ui;

import basemod.IUIElement;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.function.Supplier;

/**
 * Wrapper that only renders/updates an element when on the correct page.
 */
public class PagedElement implements IUIElement {
    private final IUIElement wrapped;
    private final int pageIndex;
    private final Supplier<Integer> currentPageSupplier;

    public PagedElement(IUIElement wrapped, int pageIndex, Supplier<Integer> currentPageSupplier) {
        this.wrapped = wrapped;
        this.pageIndex = pageIndex;
        this.currentPageSupplier = currentPageSupplier;
    }

    @Override
    public void render(SpriteBatch sb) {
        if (currentPageSupplier.get() == pageIndex) {
            wrapped.render(sb);
        }
    }

    @Override
    public void update() {
        if (currentPageSupplier.get() == pageIndex) {
            wrapped.update();
        }
    }

    @Override
    public int renderLayer() {
        return wrapped.renderLayer();
    }

    @Override
    public int updateOrder() {
        return wrapped.updateOrder();
    }
}
