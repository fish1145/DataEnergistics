package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import net.minecraft.network.chat.Component;

import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Compact titled previous/value/next control used by both recipe-affecting and view-only preview choices.
 */
final class PreviewStepper extends UIElement {

    private static final int TITLE_HEIGHT = 10;
    private static final int BUTTON_WIDTH = 14;

    private final Supplier<Component> valueSupplier;
    @Nullable
    private final Button valueButton;

    PreviewStepper(String id,
                   String previousId,
                   String nextId,
                   Supplier<Component> title,
                   Supplier<Component> value,
                   Runnable previous,
                   Runnable next,
                   int width) {
        this(id, previousId, nextId, title, value, previous, next, width, null, null);
    }

    PreviewStepper(String id,
                   String previousId,
                   String nextId,
                   Supplier<Component> title,
                   Supplier<Component> value,
                   Runnable previous,
                   Runnable next,
                   int width,
                   @Nullable String valueButtonId,
                   @Nullable Runnable valueAction) {
        if (id == null || id.isBlank() || previousId == null || previousId.isBlank() ||
                nextId == null || nextId.isBlank() || title == null || value == null ||
                previous == null || next == null || width <= BUTTON_WIDTH * 2) {
            throw new IllegalArgumentException("Preview stepper arguments cannot be null, blank, or too narrow");
        }
        if ((valueButtonId == null) != (valueAction == null) ||
                valueButtonId != null && valueButtonId.isBlank()) {
            throw new IllegalArgumentException("Preview stepper value action requires a non-blank id and action");
        }
        this.valueSupplier = value;
        this.valueButton = valueAction == null ? null : valueButton(valueButtonId, valueAction);

        setId(id);
        layout(layout -> layout.width(width).height(StructurePreviewPresentation.CONTROL_CONTENT_HEIGHT));
        addChildren(titleLabel(title), previousButton(previousId, previous), valueElement(), nextButton(nextId, next));
    }

    /**
     * Refreshes the optional clickable center value after its supplier-backed view state changes.
     */
    void refreshValue() {
        if (this.valueButton != null) {
            this.valueButton.setText(this.valueSupplier.get());
        }
    }

    private Label titleLabel(Supplier<Component> title) {
        Label label = new Label();
        label.bindDataSource(SupplierDataSource.of(() -> title.get().copy()));
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.0f)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(TextWrap.HOVER_ROLL)
                .textShadow(false));
        label.setOverflowVisible(false);
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .widthPercent(100)
                .height(TITLE_HEIGHT));
        return label;
    }

    private Button previousButton(String id, Runnable action) {
        return arrowButton(id, Icons.LEFT_ARROW_NO_BAR, action, false);
    }

    private Button nextButton(String id, Runnable action) {
        return arrowButton(id, Icons.RIGHT_ARROW_NO_BAR, action, true);
    }

    private Button arrowButton(String id,
                               IGuiTexture icon,
                               Runnable action,
                               boolean trailing) {
        Button button = new Button();
        button.setId(id);
        button.noText();
        button.addPreIcon(icon);
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE)
                    .top(TITLE_HEIGHT)
                    .width(BUTTON_WIDTH)
                    .height(StructurePreviewPresentation.CONTROL_CONTENT_HEIGHT - TITLE_HEIGHT);
            if (trailing) {
                layout.right(0);
            } else {
                layout.left(0);
            }
        });
        return button;
    }

    private UIElement valueElement() {
        if (this.valueButton != null) {
            this.valueButton.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(BUTTON_WIDTH)
                    .top(TITLE_HEIGHT)
                    .right(BUTTON_WIDTH)
                    .height(StructurePreviewPresentation.CONTROL_CONTENT_HEIGHT - TITLE_HEIGHT));
            return this.valueButton;
        }
        Label label = new Label();
        label.bindDataSource(SupplierDataSource.of(() -> this.valueSupplier.get().copy()));
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.0f)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(TextWrap.HOVER_ROLL)
                .textShadow(false));
        label.setOverflowVisible(false);
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(BUTTON_WIDTH)
                .top(TITLE_HEIGHT)
                .right(BUTTON_WIDTH)
                .height(StructurePreviewPresentation.CONTROL_CONTENT_HEIGHT - TITLE_HEIGHT));
        return label;
    }

    private Button valueButton(String id, Runnable action) {
        Button button = new Button();
        button.setId(id);
        button.setText(this.valueSupplier.get());
        button.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.0f)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(TextWrap.HOVER_ROLL)
                .textShadow(false));
        button.setOverflowVisible(false);
        button.setOnClick(event -> action.run());
        return button;
    }
}
