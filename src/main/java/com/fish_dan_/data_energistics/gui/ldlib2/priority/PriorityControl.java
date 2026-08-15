package com.fish_dan_.data_energistics.gui.ldlib2.priority;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

/**
 * Binds the shared hosted priority editor to primitive state and server-authoritative operations.
 */
@ApiStatus.Internal
public final class PriorityControl {

    private static final int ENTER_KEY = 257;
    private static final int KEYPAD_ENTER_KEY = 335;
    private static final IGuiTexture STEP_TEXTURE = SpriteTexture.of("data_energistics:textures/guis/priority/botton.png");
    private static final IGuiTexture STEP_HIGHLIGHTED_TEXTURE = SpriteTexture.of("data_energistics:textures/guis/priority/button_highlighted.png");
    private static final IGuiTexture STEP_DISABLED_TEXTURE = SpriteTexture.of("data_energistics:textures/guis/priority/button_disabled.png");

    private final PriorityControlLayout layout;
    private final IntSupplier priority;
    private final IntSupplier modifierMask;
    private final BooleanSupplier canSubmit;
    private final BooleanSupplier pending;
    private final Submitter submitter;
    private @Nullable ModifierState displayedModifier;
    private boolean controlsInitialized;
    private boolean controlsEnabled;

    private PriorityControl(Builder builder) {
        Component title = configured(builder.title, "title");
        Component insertHint = configured(builder.insertHint, "insert hint");
        Component extractHint = configured(builder.extractHint, "extract hint");
        this.priority = configured(builder.priority, "priority supplier");
        this.modifierMask = configured(builder.modifierMask, "modifier supplier");
        this.canSubmit = configured(builder.canSubmit, "submission availability");
        this.pending = configured(builder.pending, "pending state");
        this.submitter = configured(builder.submitter, "operation submitter");
        Runnable close = configured(builder.close, "close action");
        this.layout = PriorityControlLayout.bind(builder.root, builder.idPrefix);

        this.layout.title().setText(title);
        this.layout.insertHint().setText(insertHint);
        this.layout.extractHint().setText(extractHint);
        this.layout.value().setNumbersOnlyInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
        bindStepButtons();
        bindValueField();
        this.layout.close().setOnClick(event -> close.run());
        Component closeTooltip = Component.translatable("gui.close");
        this.layout.close().text.style(style -> style.tooltips(closeTooltip));
        this.layout.close().style(style -> style.tooltips(closeTooltip));
        this.layout.root().addEventListener(UIEvents.TICK, ignored -> refresh());
        refresh();
    }

    /**
     * Starts configuring a control around one already initialized editor-authored root.
     */
    public static Builder builder(UIElement root, String idPrefix) {
        return new Builder(root, idPrefix);
    }

    private void bindStepButtons() {
        for (int index = 0; index < Step.values().length; index++) {
            Step step = Step.fromIndex(index);
            this.layout.increaseButtons().get(index).setOnClick(
                    event -> submitAdjustment(Direction.INCREASE, step));
            this.layout.decreaseButtons().get(index).setOnClick(
                    event -> submitAdjustment(Direction.DECREASE, step));
        }
    }

    private void bindValueField() {
        this.layout.value().addEventListener(UIEvents.BLUR, ignored -> submitAbsoluteValue());
        this.layout.value().addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == ENTER_KEY || event.keyCode == KEYPAD_ENTER_KEY) {
                submitAbsoluteValue();
                event.stopPropagation();
            }
        });
    }

    private void submitAdjustment(Direction direction, Step step) {
        if (!submissionEnabled()) {
            refresh();
            return;
        }
        ModifierState modifier = ModifierState.fromMask(this.modifierMask.getAsInt());
        this.submitter.submit(new Adjust(direction, step, modifier));
        refresh();
    }

    private void submitAbsoluteValue() {
        if (!submissionEnabled() || this.layout.value().isError()) {
            restoreAuthoritativeValue();
            return;
        }
        int requested;
        try {
            requested = Integer.parseInt(this.layout.value().getRawText());
        } catch (NumberFormatException ignored) {
            restoreAuthoritativeValue();
            return;
        }
        if (requested == this.priority.getAsInt()) {
            restoreAuthoritativeValue();
            return;
        }
        if (!this.submitter.submit(new SetValue(requested))) {
            restoreAuthoritativeValue();
        }
        refresh();
    }

    private void refresh() {
        ModifierState modifier = ModifierState.fromMask(this.modifierMask.getAsInt());
        if (modifier != this.displayedModifier) {
            this.displayedModifier = modifier;
            refreshStepLabels(modifier);
        }
        boolean enabled = submissionEnabled();
        if (!this.controlsInitialized || enabled != this.controlsEnabled) {
            this.controlsInitialized = true;
            this.controlsEnabled = enabled;
            refreshControlState(enabled);
        }
        if (!this.layout.value().isFocused() || !enabled) {
            restoreAuthoritativeValue();
        }
    }

    private void refreshStepLabels(ModifierState modifier) {
        for (int index = 0; index < Step.values().length; index++) {
            Step step = Step.fromIndex(index);
            int magnitude = step.amount() * modifier.multiplier();
            this.layout.increaseButtons().get(index).setText(Component.literal("+" + magnitude));
            this.layout.decreaseButtons().get(index).setText(Component.literal("-" + magnitude));
        }
    }

    private void refreshControlState(boolean enabled) {
        for (Button button : allStepButtons()) {
            button.setActive(enabled);
            button.buttonStyle(style -> {
                if (enabled) {
                    style.baseTexture(STEP_TEXTURE)
                            .hoverTexture(STEP_HIGHLIGHTED_TEXTURE)
                            .pressedTexture(STEP_HIGHLIGHTED_TEXTURE);
                } else {
                    style.baseTexture(STEP_DISABLED_TEXTURE)
                            .hoverTexture(STEP_DISABLED_TEXTURE)
                            .pressedTexture(STEP_DISABLED_TEXTURE);
                }
            });
        }
        this.layout.value().setActive(enabled);
    }

    private List<Button> allStepButtons() {
        return Stream.concat(
                this.layout.increaseButtons().stream(),
                this.layout.decreaseButtons().stream())
                .toList();
    }

    private boolean submissionEnabled() {
        return this.canSubmit.getAsBoolean() && !this.pending.getAsBoolean();
    }

    private void restoreAuthoritativeValue() {
        String authoritative = Integer.toString(this.priority.getAsInt());
        if (!authoritative.equals(this.layout.value().getRawText())) {
            this.layout.value().setText(authoritative, false);
        }
    }

    private static <T> T configured(@Nullable T value, String role) {
        if (value == null) {
            throw new IllegalStateException("Priority control is missing its " + role);
        }
        return value;
    }

    /**
     * Builds one fully bound control without exposing editor tree details to its owning window.
     */
    public static final class Builder {

        private final UIElement root;
        private final String idPrefix;
        private @Nullable Component title;
        private @Nullable Component insertHint;
        private @Nullable Component extractHint;
        private @Nullable IntSupplier priority;
        private @Nullable IntSupplier modifierMask;
        private @Nullable BooleanSupplier canSubmit;
        private @Nullable BooleanSupplier pending;
        private @Nullable Submitter submitter;
        private @Nullable Runnable close;

        private Builder(UIElement root, String idPrefix) {
            if (idPrefix.isBlank()) {
                throw new IllegalArgumentException("Priority control id prefix must not be blank");
            }
            this.root = root;
            this.idPrefix = idPrefix;
        }

        /**
         * Sets localized text owned by the calling feature.
         */
        public Builder labels(Component title, Component insertHint, Component extractHint) {
            this.title = title;
            this.insertHint = insertHint;
            this.extractHint = extractHint;
            return this;
        }

        /**
         * Supplies the current primitive value, client modifier state and submission availability.
         */
        public Builder state(
                             IntSupplier priority,
                             IntSupplier modifierMask,
                             BooleanSupplier canSubmit,
                             BooleanSupplier pending) {
            this.priority = priority;
            this.modifierMask = modifierMask;
            this.canSubmit = canSubmit;
            this.pending = pending;
            return this;
        }

        /**
         * Binds the authoritative operation sender and owning window close action.
         */
        public Builder actions(Submitter submitter, Runnable close) {
            this.submitter = submitter;
            this.close = close;
            return this;
        }

        /**
         * Validates the configuration and installs all control behavior.
         */
        public PriorityControl build() {
            return new PriorityControl(this);
        }
    }

    /**
     * Sends one validated operation through the owning feature's protocol.
     */
    @FunctionalInterface
    public interface Submitter {

        /**
         * Returns whether the operation was accepted for sending.
         */
        boolean submit(Operation operation);
    }

    /**
     * Represents the two server-authoritative ways to modify an integer priority.
     */
    public sealed interface Operation permits Adjust, SetValue {

        /**
         * Applies this validated operation to the current server value.
         */
        int apply(int current);
    }

    /**
     * Applies one direction, authored step and explicit modifier state.
     */
    public record Adjust(Direction direction, Step step, ModifierState modifier) implements Operation {

        @Override
        public int apply(int current) {
            long delta = (long) direction.sign() * step.amount() * modifier.multiplier();
            long candidate = (long) current + delta;
            return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, candidate));
        }
    }

    /**
     * Replaces the priority with one complete signed integer entered by the user.
     */
    public record SetValue(int value) implements Operation {

        @Override
        public int apply(int current) {
            return this.value;
        }
    }

    /**
     * Identifies whether an authored step increases or decreases the current value.
     */
    public enum Direction {

        INCREASE(1),
        DECREASE(-1);

        private final int sign;

        Direction(int sign) {
            this.sign = sign;
        }

        /**
         * Returns the exact arithmetic sign used by the server.
         */
        public int sign() {
            return this.sign;
        }
    }

    /**
     * Identifies one of the four authored base buttons without relying on enum ordinals in network payloads.
     */
    public enum Step {

        ONE(0, 1),
        TEN(1, 10),
        HUNDRED(2, 100),
        THOUSAND(3, 1000);

        private final int index;
        private final int amount;

        Step(int index, int amount) {
            this.index = index;
            this.amount = amount;
        }

        /**
         * Returns the explicit bounded protocol index.
         */
        public int index() {
            return this.index;
        }

        /**
         * Returns the unmodified authored step amount.
         */
        public int amount() {
            return this.amount;
        }

        /**
         * Resolves the bounded protocol index and rejects malformed requests.
         */
        public static Step fromIndex(int index) {
            return switch (index) {
                case 0 -> ONE;
                case 1 -> TEN;
                case 2 -> HUNDRED;
                case 3 -> THOUSAND;
                default -> throw new IllegalArgumentException("Unknown priority step index " + index);
            };
        }
    }

    /**
     * Captures the four modifier combinations explicitly so mouse events never depend on LDLib2 modifier fields.
     */
    public enum ModifierState {

        NONE(0, 1),
        SHIFT(1, 10),
        CTRL(2, 100),
        SHIFT_CTRL(3, 1000);

        private final int mask;
        private final int multiplier;

        ModifierState(int mask, int multiplier) {
            this.mask = mask;
            this.multiplier = multiplier;
        }

        /**
         * Returns the explicit two-bit protocol value.
         */
        public int mask() {
            return this.mask;
        }

        /**
         * Returns the exact arithmetic multiplier.
         */
        public int multiplier() {
            return this.multiplier;
        }

        /**
         * Resolves Shift/Ctrl bits and rejects any unrecognized bit.
         */
        public static ModifierState fromMask(int mask) {
            return switch (mask) {
                case 0 -> NONE;
                case 1 -> SHIFT;
                case 2 -> CTRL;
                case 3 -> SHIFT_CTRL;
                default -> throw new IllegalArgumentException("Unknown priority modifier mask " + mask);
            };
        }
    }
}
