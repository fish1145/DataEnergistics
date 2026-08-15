package com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.IOptionalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import org.jspecify.annotations.Nullable;

/**
 * LDLib2 element that preserves the identity of a slot already owned and synchronized by an AE2 menu.
 *
 * <p>
 * Instances are created only by {@link AeMenuBridge}; rebinding and phantom mutation would bypass AE2's semantic
 * slot and packet protocols and are therefore rejected.
 */
public final class AeItemSlot extends ItemSlot {

    private boolean initialized;

    /**
     * Creates one immutable wrapper for an existing menu slot.
     */
    AeItemSlot(Slot slot) {
        super(slot);
        refreshSlotPresentation();
        this.initialized = true;
    }

    /**
     * Uses AE2's presentation stack so wrapped keys and hidden quantities render exactly as the original slot.
     */
    @Override
    public ItemStack getValue() {
        if (getSlot() instanceof AppEngSlot appEngSlot) {
            return appEngSlot.getDisplayStack();
        }
        return super.getValue();
    }

    /**
     * Prevents local writes to fake slots, whose mutation must remain an AEBaseScreen inventory-action packet.
     */
    @Override
    public AeItemSlot setValue(@Nullable ItemStack value, boolean notify) {
        if (getSlot() instanceof FakeSlot) {
            String message = "AeItemSlot cannot mutate an AE2 FakeSlot directly";
            Data_Energistics.LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        super.setValue(value, notify);
        return this;
    }

    /**
     * Keeps the wrapper bound to the identity validated by its bridge.
     */
    @Override
    public AeItemSlot bind(Slot slot) {
        if (this.initialized) {
            throw immutableBindingViolation();
        }
        super.bind(slot);
        return this;
    }

    /**
     * Prevents replacing the existing AE2 slot with a newly allocated item-handler slot.
     */
    @Override
    public AeItemSlot bind(IItemHandlerModifiable itemHandlerModifiable, int index) {
        if (this.initialized) {
            throw immutableBindingViolation();
        }
        super.bind(itemHandlerModifiable, index);
        return this;
    }

    /**
     * Prevents XEI ghost handlers from writing an AE2 menu slot outside AEBaseScreen's packet protocol.
     */
    @Override
    public AeItemSlot xeiPhantom() {
        String message = "AeItemSlot cannot use LDLib2 phantom mutation";
        Data_Energistics.LOGGER.error(message);
        throw new IllegalStateException(message);
    }

    /**
     * Routes ordinary, custom, and emptying tooltips through LDLib2 while retaining AE2's precedence.
     */
    @Override
    protected void onHoverTooltips(UIEvent event) {
        ModularUI modularUI = getModularUI();
        if (modularUI == null || modularUI.getMenu() == null) {
            String message = "AeItemSlot cannot resolve a tooltip before its menu UI is mounted";
            Data_Energistics.LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        event.hoverTooltips = createTooltip(modularUI.getMenu().getCarried());
    }

    /**
     * Creates one LDLib2 tooltip from the wrapped slot and an explicit carried stack.
     */
    @Nullable
    public HoverTooltips createTooltip(ItemStack carried) {
        return AeSlotTooltipResolver.resolve(getSlot(), carried, this::createOrdinaryTooltip);
    }

    /**
     * Builds LDLib2's normal ItemStack tooltip only after AE2 supplies no higher-priority tooltip.
     */
    @Nullable
    private HoverTooltips createOrdinaryTooltip() {
        ItemStack tooltipStack = getSlot().getItem();
        if (tooltipStack.isEmpty()) {
            return null;
        }
        return new HoverTooltips(
                DrawerHelper.getItemToolTip(tooltipStack),
                tooltipStack.getTooltipImage().orElse(null),
                null,
                tooltipStack);
    }

    /**
     * Refreshes active, optional-background, opacity, and hit-test state from the authoritative AE2 slot.
     */
    void refreshSlotPresentation() {
        Slot slot = getSlot();
        boolean interactionEnabled = slot.isActive();
        boolean optionalBackground = !interactionEnabled &&
                slot instanceof IOptionalSlot optionalSlot && optionalSlot.isRenderDisabled();
        float opacity = optionalBackground && slot instanceof IOptionalSlot optionalSlot &&
                !optionalSlot.isSlotEnabled() ? 0.2f : 1.0f;
        setVisible(interactionEnabled || optionalBackground);
        setAllowHitTest(interactionEnabled);
        Style.importantPipeline(getStyle(), style -> style.opacity(opacity));
    }

    /**
     * Keeps dynamically enabled, paged, and optional slots synchronized with each client screen tick.
     */
    @Override
    public void screenTick() {
        refreshSlotPresentation();
        super.screenTick();
    }

    /**
     * Prevents a stale hit-test result during the tick in which an AE2 slot becomes inactive.
     */
    @Override
    public boolean isIntersectWithPoint(double localX, double localY) {
        return getSlot().isActive() && super.isIntersectWithPoint(localX, localY);
    }

    /**
     * Draws AE2's empty icon and invalid-state overlay beneath LDLib2's item rendering.
     */
    @Override
    protected void drawSlotOverlay(GUIContext guiContext) {
        super.drawSlotOverlay(guiContext);
        if (!(getSlot() instanceof AppEngSlot appEngSlot)) {
            return;
        }
        ItemStack storedStack = appEngSlot.getItem();
        if ((appEngSlot.renderIconWithItem() || storedStack.isEmpty()) &&
                appEngSlot.isSlotEnabled() && appEngSlot.getIcon() != null) {
            appEngSlot.getIcon()
                    .getBlitter()
                    .dest(0, 0)
                    .opacity(appEngSlot.getOpacityOfIcon())
                    .blit(guiContext.graphics);
        }
        if (!appEngSlot.isValid()) {
            guiContext.graphics.fill(0, 0, 16, 16, 0x66ff6666);
        }
    }

    /**
     * Logs and creates the fail-fast exception shared by both public rebinding paths.
     */
    private static IllegalStateException immutableBindingViolation() {
        String message = "AeItemSlot cannot be rebound after construction";
        Data_Energistics.LOGGER.error(message);
        return new IllegalStateException(message);
    }
}
