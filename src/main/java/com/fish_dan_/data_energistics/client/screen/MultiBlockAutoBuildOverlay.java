package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draggable client-side configuration overlay shared by MDLib-independent multiblock build screens.
 *
 * <p>
 * The overlay renders only immutable host descriptions and retains ephemeral UI selection state. It deliberately
 * contains no machine-specific build rules, block maps, or payload types; its host interprets the confirmation
 * selection through the description callback.
 * </p>
 */
public final class MultiBlockAutoBuildOverlay {

    private static final int PANEL_WIDTH = 128;
    private static final int PANEL_HEIGHT = 128;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 16;
    private static final int ICON_SIZE = 16;
    private static final int STRUCTURE_BUTTON_GAP = 4;
    private static final int STRUCTURE_BUTTON_Y = 21;
    private static final int BUILD_REQUEST_Y = 41;
    private static final int REPEAT_Y = 67;
    private static final int TIER_Y = 91;
    private static final int CONFIRM_Y = 108;
    private static final int CONFIRM_HEIGHT = 16;
    private static final int PANEL_Z = 250;
    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "textures/guis/list.png");

    private final MultiBlockAutoBuildOverlayDescription description;
    private final Map<Integer, Integer> repeatCounts = new LinkedHashMap<>();
    private final Map<Integer, Integer> selectedTierValues = new LinkedHashMap<>();
    private final Map<Integer, Boolean> buildRequests = new LinkedHashMap<>();
    private boolean visible;
    private int selectedStructureId;
    private int viewportWidth;
    private int viewportHeight;
    private int panelX;
    private int panelY;
    private boolean positionInitialized;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    /**
     * Creates an overlay from immutable host-provided structure metadata.
     *
     * @param description host description and confirmation callback
     */
    public MultiBlockAutoBuildOverlay(MultiBlockAutoBuildOverlayDescription description) {
        this.description = description;
        MultiBlockAutoBuildOverlayDescription.Structure firstStructure = description.structures().getFirst();
        this.selectedStructureId = firstStructure.id();
        for (MultiBlockAutoBuildOverlayDescription.Structure structure : description.structures()) {
            this.repeatCounts.put(structure.id(), structure.minimumRepeatCount());
            this.selectedTierValues.put(structure.id(), structure.tierOptions().getFirst().value());
            this.buildRequests.put(structure.id(), structure.buildRequestedByDefault());
        }
    }

    /**
     * Updates viewport bounds and initializes the overlay beside its host menu on first render.
     *
     * @param screenWidth  current client screen width
     * @param screenHeight current client screen height
     * @param hostBounds   current screen-space bounds of the host menu
     */
    public void updateViewport(int screenWidth, int screenHeight, Rect2i hostBounds) {
        this.viewportWidth = screenWidth;
        this.viewportHeight = screenHeight;
        if (!this.positionInitialized) {
            Rect2i defaultBounds = defaultBounds(hostBounds);
            this.panelX = defaultBounds.getX();
            this.panelY = defaultBounds.getY();
            this.positionInitialized = true;
            return;
        }
        clampPosition();
    }

    /** Toggles visibility and stops any active panel drag. */
    public void toggle() {
        this.visible = !this.visible;
        this.dragging = false;
    }

    /** Hides the overlay and stops any active panel drag. */
    public void close() {
        this.visible = false;
        this.dragging = false;
    }

    /**
     * Returns whether the overlay currently participates in rendering and input.
     *
     * @return current visibility state
     */
    public boolean isVisible() {
        return this.visible;
    }

    /**
     * Returns the current screen-space panel bounds for exclusion-zone integration.
     *
     * @return current fixed-size panel bounds
     */
    public Rect2i bounds() {
        return new Rect2i(this.panelX, this.panelY, PANEL_WIDTH, PANEL_HEIGHT);
    }

    /**
     * Renders the panel above its host screen without taking ownership of tooltips.
     *
     * @param guiGraphics render target
     * @param font        client font
     * @param mouseX      current mouse x position
     * @param mouseY      current mouse y position
     * @param partialTick render interpolation value
     */
    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, float partialTick) {
        if (!this.visible || !this.positionInitialized) {
            return;
        }

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, PANEL_Z);
        try {
            Rect2i bounds = bounds();
            Blitter.texture(PANEL_TEXTURE, 256, 256)
                    .src(0, 0, PANEL_WIDTH, PANEL_HEIGHT)
                    .dest(bounds.getX(), bounds.getY(), PANEL_WIDTH, PANEL_HEIGHT)
                    .blit(guiGraphics);
            renderHeader(guiGraphics, font, bounds, mouseX, mouseY);
            renderStructureButtons(guiGraphics, bounds, mouseX, mouseY);
            renderBuildRequestedButtons(guiGraphics, font, bounds, mouseX, mouseY);
            if (selectedStructure().repeatable()) {
                renderStepper(
                        guiGraphics,
                        font,
                        repeatBounds(bounds),
                        Component.translatable("screen.data_energistics.multiblock_auto_build.repeat", repeatCount()),
                        mouseX,
                        mouseY);
            }
            renderStepper(
                    guiGraphics,
                    font,
                    tierBounds(bounds),
                    Component.translatable(
                            "screen.data_energistics.multiblock_auto_build.tier",
                            selectedStructure().tierLabel(),
                            selectedTierOption().label()),
                    mouseX,
                    mouseY);
            renderConfirmButton(guiGraphics, font, bounds, mouseX, mouseY);
        } finally {
            pose.popPose();
        }
    }

    /**
     * Renders a tooltip for the currently hovered overlay control.
     *
     * @param guiGraphics render target
     * @param font        client font
     * @param mouseX      current mouse x position
     * @param mouseY      current mouse y position
     */
    public void renderTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!this.visible || !this.positionInitialized) {
            return;
        }

        Rect2i bounds = bounds();
        if (contains(closeBounds(bounds), mouseX, mouseY)) {
            guiGraphics.renderTooltip(font, Component.translatable("screen.data_energistics.multiblock_auto_build.close"), mouseX, mouseY);
            return;
        }
        if (contains(headerBounds(bounds), mouseX, mouseY)) {
            guiGraphics.renderTooltip(font, Component.translatable("screen.data_energistics.multiblock_auto_build.drag"), mouseX, mouseY);
            return;
        }
        List<MultiBlockAutoBuildOverlayDescription.Structure> structures = this.description.structures();
        for (int index = 0; index < structures.size(); index++) {
            if (contains(structureBounds(bounds, index), mouseX, mouseY)) {
                guiGraphics.renderTooltip(font, structures.get(index).label(), mouseX, mouseY);
                return;
            }
        }
        if (contains(buildRequestedBounds(bounds, true), mouseX, mouseY)) {
            guiGraphics.renderTooltip(
                    font,
                    Component.translatable("screen.data_energistics.multiblock_auto_build.build_requested.yes"),
                    mouseX,
                    mouseY);
            return;
        }
        if (contains(buildRequestedBounds(bounds, false), mouseX, mouseY)) {
            guiGraphics.renderTooltip(
                    font,
                    Component.translatable("screen.data_energistics.multiblock_auto_build.build_requested.no"),
                    mouseX,
                    mouseY);
            return;
        }
        if (selectedStructure().repeatable() && contains(repeatBounds(bounds), mouseX, mouseY)) {
            guiGraphics.renderTooltip(
                    font,
                    Component.translatable("screen.data_energistics.multiblock_auto_build.repeat", repeatCount()),
                    mouseX,
                    mouseY);
            return;
        }
        if (contains(tierBounds(bounds), mouseX, mouseY)) {
            guiGraphics.renderTooltip(
                    font,
                    Component.translatable(
                            "screen.data_energistics.multiblock_auto_build.tier",
                            selectedStructure().tierLabel(),
                            selectedTierOption().label()),
                    mouseX,
                    mouseY);
            return;
        }
        if (contains(confirmBounds(bounds), mouseX, mouseY)) {
            guiGraphics.renderTooltip(font, Component.translatable("screen.data_energistics.multiblock_auto_build.confirm"), mouseX, mouseY);
        }
    }

    /**
     * Offers a mouse click to the overlay before its host menu handles it.
     *
     * @param mouseX mouse x position
     * @param mouseY mouse y position
     * @param button mouse button
     * @return whether the overlay consumed the click
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.positionInitialized) {
            return false;
        }

        Rect2i bounds = bounds();
        if (!contains(bounds, mouseX, mouseY)) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        if (contains(closeBounds(bounds), mouseX, mouseY)) {
            close();
            return true;
        }
        if (contains(headerBounds(bounds), mouseX, mouseY)) {
            this.dragging = true;
            this.dragOffsetX = (int) Math.round(mouseX) - bounds.getX();
            this.dragOffsetY = (int) Math.round(mouseY) - bounds.getY();
            return true;
        }
        List<MultiBlockAutoBuildOverlayDescription.Structure> structures = this.description.structures();
        for (int index = 0; index < structures.size(); index++) {
            if (contains(structureBounds(bounds, index), mouseX, mouseY)) {
                selectStructure(structures.get(index).id());
                return true;
            }
        }
        if (contains(buildRequestedBounds(bounds, true), mouseX, mouseY)) {
            setBuildRequested(true);
            return true;
        }
        if (contains(buildRequestedBounds(bounds, false), mouseX, mouseY)) {
            setBuildRequested(false);
            return true;
        }
        if (selectedStructure().repeatable() && handleStepperClick(
                repeatBounds(bounds),
                mouseX,
                mouseY,
                this::decrementRepeat,
                this::incrementRepeat)) {
            return true;
        }
        if (handleStepperClick(tierBounds(bounds), mouseX, mouseY, this::decrementTier, this::incrementTier)) {
            return true;
        }
        if (contains(confirmBounds(bounds), mouseX, mouseY)) {
            this.description.confirmationConsumer().accept(createSelection());
            close();
        }
        return true;
    }

    /**
     * Finishes an active drag when the left mouse button is released.
     *
     * @param mouseX mouse x position
     * @param mouseY mouse y position
     * @param button mouse button
     * @return whether the overlay consumed the release
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.dragging || button != 0) {
            return false;
        }
        updateDrag(mouseX, mouseY);
        this.dragging = false;
        return true;
    }

    /**
     * Updates an active panel drag.
     *
     * @param mouseX mouse x position
     * @param mouseY mouse y position
     * @param button mouse button
     * @return whether the overlay consumed the drag
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!this.dragging || button != 0) {
            return false;
        }
        updateDrag(mouseX, mouseY);
        return true;
    }

    /**
     * Handles escape while visible without closing the host menu.
     *
     * @param keyCode GLFW key code
     * @return whether the overlay consumed the key
     */
    public boolean keyPressed(int keyCode) {
        if (!this.visible || keyCode != 256) {
            return false;
        }
        close();
        return true;
    }

    /**
     * Selects one declared structure while preserving each structure's own tier and repeat choices.
     *
     * @param structureId host-defined structure identifier
     */
    public void selectStructure(int structureId) {
        this.description.structure(structureId);
        this.selectedStructureId = structureId;
    }

    /**
     * Returns the current host-defined structure identifier.
     *
     * @return selected structure identifier
     */
    public int selectedStructureId() {
        return this.selectedStructureId;
    }

    /**
     * Returns whether confirmation will request an actual build.
     *
     * @return current build request flag
     */
    public boolean buildRequested() {
        return this.buildRequests.get(this.selectedStructureId);
    }

    /**
     * Updates whether confirmation will request an actual build.
     *
     * @param buildRequested requested build flag
     */
    public void setBuildRequested(boolean buildRequested) {
        this.buildRequests.put(this.selectedStructureId, buildRequested);
    }

    /**
     * Returns the active structure's repeat count, including its fixed minimum when repetition is unavailable.
     *
     * @return selected repeat count
     */
    public int repeatCount() {
        return this.repeatCounts.get(this.selectedStructureId);
    }

    /**
     * Updates the selected repeat count within the active structure's declared bounds.
     *
     * @param repeatCount requested repeat count
     */
    public void setRepeatCount(int repeatCount) {
        MultiBlockAutoBuildOverlayDescription.Structure structure = selectedStructure();
        if (repeatCount < structure.minimumRepeatCount() || repeatCount > structure.maximumRepeatCount()) {
            throw new IllegalArgumentException("Multiblock auto-build repeat count for structure " + structure.id() +
                    " must be between " + structure.minimumRepeatCount() + " and " + structure.maximumRepeatCount() +
                    ": " + repeatCount);
        }
        this.repeatCounts.put(structure.id(), repeatCount);
    }

    /**
     * Returns the active structure's selected host-defined tier value.
     *
     * @return selected tier value
     */
    public int selectedTierValue() {
        return this.selectedTierValues.get(this.selectedStructureId);
    }

    /**
     * Updates the active structure's selected tier using one declared host-defined value.
     *
     * @param tierValue declared tier value
     */
    public void setSelectedTierValue(int tierValue) {
        MultiBlockAutoBuildOverlayDescription.Structure structure = selectedStructure();
        structure.tier(tierValue);
        this.selectedTierValues.put(structure.id(), tierValue);
    }

    /**
     * Produces the immutable selection that confirmation will pass to the host callback.
     *
     * @return current immutable host-neutral selection
     */
    public MultiBlockAutoBuildSelection createSelection() {
        return new MultiBlockAutoBuildSelection(
                this.selectedStructureId,
                buildRequested(),
                repeatCount(),
                selectedTierValue());
    }

    private void renderHeader(GuiGraphics guiGraphics, Font font, Rect2i bounds, int mouseX, int mouseY) {
        guiGraphics.drawString(font, this.description.title(), bounds.getX() + 7, bounds.getY() + 5, 0xFFE6EDF3, false);
        Rect2i close = closeBounds(bounds);
        boolean hovered = contains(close, mouseX, mouseY);
        guiGraphics.drawString(font, "x", close.getX() + 4, close.getY() + 2, hovered ? 0xFFFF6B6B : 0xFFE6EDF3, false);
    }

    private void renderStructureButtons(GuiGraphics guiGraphics, Rect2i bounds, int mouseX, int mouseY) {
        List<MultiBlockAutoBuildOverlayDescription.Structure> structures = this.description.structures();
        for (int index = 0; index < structures.size(); index++) {
            MultiBlockAutoBuildOverlayDescription.Structure structure = structures.get(index);
            Rect2i button = structureBounds(bounds, index);
            renderIconButton(
                    guiGraphics,
                    button,
                    structureIconName(structure.iconIndex()),
                    structure.id() == this.selectedStructureId,
                    contains(button, mouseX, mouseY));
        }
    }

    private void renderBuildRequestedButtons(GuiGraphics guiGraphics, Font font, Rect2i bounds, int mouseX, int mouseY) {
        guiGraphics.drawString(
                font,
                Component.translatable("screen.data_energistics.multiblock_auto_build.build_requested"),
                bounds.getX() + 50,
                bounds.getY() + BUILD_REQUEST_Y + 4,
                0xFFE6EDF3,
                false);
        Rect2i yes = buildRequestedBounds(bounds, true);
        Rect2i no = buildRequestedBounds(bounds, false);
        boolean buildRequested = buildRequested();
        renderIconButton(guiGraphics, yes, "POWER_UNIT_YES", buildRequested, contains(yes, mouseX, mouseY));
        renderIconButton(guiGraphics, no, "POWER_UNIT_NO", !buildRequested, contains(no, mouseX, mouseY));
    }

    private void renderStepper(GuiGraphics guiGraphics,
                               Font font,
                               Rect2i bounds,
                               Component label,
                               int mouseX,
                               int mouseY) {
        Rect2i decrement = stepperDecrementBounds(bounds);
        Rect2i increment = stepperIncrementBounds(bounds);
        renderArrowButton(guiGraphics, decrement, false, contains(decrement, mouseX, mouseY));
        renderArrowButton(guiGraphics, increment, true, contains(increment, mouseX, mouseY));
        String text = font.plainSubstrByWidth(label.getString(), bounds.getWidth() - 40);
        int textX = bounds.getX() + 20 + Math.max(0, (bounds.getWidth() - 40 - font.width(text)) / 2);
        guiGraphics.drawString(font, text, textX, bounds.getY() + 4, 0xFFE6EDF3, false);
    }

    private void renderConfirmButton(GuiGraphics guiGraphics, Font font, Rect2i bounds, int mouseX, int mouseY) {
        Rect2i button = confirmBounds(bounds);
        boolean hovered = contains(button, mouseX, mouseY);
        Icon background = hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter().dest(button.getX(), button.getY(), button.getWidth(), button.getHeight()).blit(guiGraphics);
        Component label = Component.translatable("screen.data_energistics.multiblock_auto_build.confirm");
        guiGraphics.drawString(
                font,
                label,
                button.getX() + (button.getWidth() - font.width(label)) / 2,
                button.getY() + 4,
                0xFFE6EDF3,
                false);
    }

    private void renderIconButton(GuiGraphics guiGraphics,
                                  Rect2i bounds,
                                  String iconName,
                                  boolean selected,
                                  boolean hovered) {
        Icon background = hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER :
                (selected ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND);
        background.getBlitter().dest(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()).blit(guiGraphics);
        DataEnergisticsIcon.getBlitter(iconName)
                .dest(bounds.getX() + (bounds.getWidth() - ICON_SIZE) / 2, bounds.getY())
                .blit(guiGraphics);
    }

    private void renderArrowButton(GuiGraphics guiGraphics, Rect2i bounds, boolean forward, boolean hovered) {
        Icon background = hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter().dest(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()).blit(guiGraphics);
        (forward ? Icon.ARROW_RIGHT : Icon.ARROW_LEFT)
                .getBlitter()
                .dest(bounds.getX(), bounds.getY(), ICON_SIZE, ICON_SIZE)
                .blit(guiGraphics);
    }

    private boolean handleStepperClick(Rect2i bounds,
                                       double mouseX,
                                       double mouseY,
                                       Runnable decrement,
                                       Runnable increment) {
        if (contains(stepperDecrementBounds(bounds), mouseX, mouseY)) {
            decrement.run();
            return true;
        }
        if (contains(stepperIncrementBounds(bounds), mouseX, mouseY)) {
            increment.run();
            return true;
        }
        return contains(bounds, mouseX, mouseY);
    }

    private void decrementRepeat() {
        MultiBlockAutoBuildOverlayDescription.Structure structure = selectedStructure();
        if (repeatCount() > structure.minimumRepeatCount()) {
            this.repeatCounts.put(structure.id(), repeatCount() - 1);
        }
    }

    private void incrementRepeat() {
        MultiBlockAutoBuildOverlayDescription.Structure structure = selectedStructure();
        if (repeatCount() < structure.maximumRepeatCount()) {
            this.repeatCounts.put(structure.id(), repeatCount() + 1);
        }
    }

    private void decrementTier() {
        MultiBlockAutoBuildOverlayDescription.Structure structure = selectedStructure();
        int currentIndex = selectedTierIndex(structure);
        int previousIndex = currentIndex == 0 ? structure.tierOptions().size() - 1 : currentIndex - 1;
        setSelectedTierValue(structure.tierOptions().get(previousIndex).value());
    }

    private void incrementTier() {
        MultiBlockAutoBuildOverlayDescription.Structure structure = selectedStructure();
        int currentIndex = selectedTierIndex(structure);
        int nextIndex = currentIndex == structure.tierOptions().size() - 1 ? 0 : currentIndex + 1;
        setSelectedTierValue(structure.tierOptions().get(nextIndex).value());
    }

    private int selectedTierIndex(MultiBlockAutoBuildOverlayDescription.Structure structure) {
        int selectedTierValue = selectedTierValue();
        for (int index = 0; index < structure.tierOptions().size(); index++) {
            if (structure.tierOptions().get(index).value() == selectedTierValue) {
                return index;
            }
        }
        throw new IllegalStateException("Selected multiblock auto-build tier is missing from structure " + structure.id());
    }

    private void updateDrag(double mouseX, double mouseY) {
        this.panelX = (int) Math.round(mouseX) - this.dragOffsetX;
        this.panelY = (int) Math.round(mouseY) - this.dragOffsetY;
        clampPosition();
    }

    private Rect2i defaultBounds(Rect2i hostBounds) {
        int rightX = hostBounds.getX() + hostBounds.getWidth() + PANEL_MARGIN;
        int leftX = hostBounds.getX() - PANEL_WIDTH - PANEL_MARGIN;
        int preferredX = rightX + PANEL_WIDTH <= this.viewportWidth - PANEL_MARGIN ? rightX : leftX;
        int preferredY = hostBounds.getY() + 8;
        return clampedBounds(preferredX, preferredY);
    }

    private Rect2i clampedBounds(int x, int y) {
        int maxX = Math.max(PANEL_MARGIN, this.viewportWidth - PANEL_WIDTH - PANEL_MARGIN);
        int maxY = Math.max(PANEL_MARGIN, this.viewportHeight - PANEL_HEIGHT - PANEL_MARGIN);
        return new Rect2i(
                Math.max(PANEL_MARGIN, Math.min(x, maxX)),
                Math.max(PANEL_MARGIN, Math.min(y, maxY)),
                PANEL_WIDTH,
                PANEL_HEIGHT);
    }

    private void clampPosition() {
        Rect2i clamped = clampedBounds(this.panelX, this.panelY);
        this.panelX = clamped.getX();
        this.panelY = clamped.getY();
    }

    private MultiBlockAutoBuildOverlayDescription.Structure selectedStructure() {
        return this.description.structure(this.selectedStructureId);
    }

    private MultiBlockAutoBuildOverlayDescription.TierOption selectedTierOption() {
        return selectedStructure().tier(selectedTierValue());
    }

    private static String structureIconName(int iconIndex) {
        return "MULTIBLOCK_BUILDER_STRUCTURE_" + iconIndex;
    }

    private static Rect2i headerBounds(Rect2i bounds) {
        return new Rect2i(bounds.getX(), bounds.getY(), bounds.getWidth(), HEADER_HEIGHT);
    }

    private static Rect2i closeBounds(Rect2i bounds) {
        return new Rect2i(bounds.getX() + bounds.getWidth() - 18, bounds.getY(), 18, HEADER_HEIGHT);
    }

    private Rect2i structureBounds(Rect2i bounds, int structureIndex) {
        int structureCount = this.description.structures().size();
        int rowWidth = structureCount * ICON_SIZE + (structureCount - 1) * STRUCTURE_BUTTON_GAP;
        int startX = bounds.getX() + (bounds.getWidth() - rowWidth) / 2;
        return new Rect2i(
                startX + structureIndex * (ICON_SIZE + STRUCTURE_BUTTON_GAP),
                bounds.getY() + STRUCTURE_BUTTON_Y,
                ICON_SIZE,
                ICON_SIZE);
    }

    private static Rect2i buildRequestedBounds(Rect2i bounds, boolean requested) {
        return new Rect2i(bounds.getX() + (requested ? 8 : 28), bounds.getY() + BUILD_REQUEST_Y, ICON_SIZE, ICON_SIZE);
    }

    private Rect2i repeatBounds(Rect2i bounds) {
        return new Rect2i(bounds.getX() + 8, bounds.getY() + REPEAT_Y, bounds.getWidth() - 16, ICON_SIZE);
    }

    private Rect2i tierBounds(Rect2i bounds) {
        int y = selectedStructure().repeatable() ? TIER_Y : REPEAT_Y;
        return new Rect2i(bounds.getX() + 8, bounds.getY() + y, bounds.getWidth() - 16, ICON_SIZE);
    }

    private static Rect2i confirmBounds(Rect2i bounds) {
        return new Rect2i(bounds.getX() + 8, bounds.getY() + CONFIRM_Y, bounds.getWidth() - 16, CONFIRM_HEIGHT);
    }

    private static Rect2i stepperDecrementBounds(Rect2i bounds) {
        return new Rect2i(bounds.getX(), bounds.getY(), ICON_SIZE, ICON_SIZE);
    }

    private static Rect2i stepperIncrementBounds(Rect2i bounds) {
        return new Rect2i(bounds.getX() + bounds.getWidth() - ICON_SIZE, bounds.getY(), ICON_SIZE, ICON_SIZE);
    }

    private static boolean contains(Rect2i bounds, double mouseX, double mouseY) {
        return mouseX >= bounds.getX() && mouseX < bounds.getX() + bounds.getWidth() &&
                mouseY >= bounds.getY() && mouseY < bounds.getY() + bounds.getHeight();
    }
}
