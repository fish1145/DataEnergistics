package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiDrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Native EMI representation of Data or DataFlow without degrading it to AE2's wrapper item.
 */
public final class DataResourceEmiStack extends EmiStack {

    private final DataResourceEmiKey key;

    public DataResourceEmiStack(DataResourceEmiKey key, long amount) {
        if (amount <= 0L) {
            throw invalid("Data resource EMI stack amount must be positive: " + amount);
        }
        this.key = key;
        this.amount = amount;
    }

    @Override
    public DataResourceEmiStack copy() {
        DataResourceEmiStack copy = new DataResourceEmiStack(key, 1L);
        copy.amount = amount;
        copy.chance = chance;
        copy.setRemainder(getRemainder().copy());
        copy.comparison = comparison;
        return copy;
    }

    @Override
    public boolean isEmpty() {
        return amount <= 0L;
    }

    @Override
    public DataComponentPatch getComponentChanges() {
        return DataComponentPatch.EMPTY;
    }

    @Override
    public DataResourceEmiKey getKey() {
        return key;
    }

    @Override
    public ResourceLocation getId() {
        return key.id();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, float delta, int flags) {
        if ((flags & RENDER_ICON) != 0) {
            CustomKeyGuiRenderer.draw(Minecraft.getInstance(), guiGraphics, x, y, key.aeKey());
        }
        if ((flags & RENDER_AMOUNT) != 0 && amount != 1L) {
            String formattedAmount = GenericStackDisplayHelper.formatCompactAmount(asGenericStack());
            EmiRenderHelper.renderAmount(
                    EmiDrawContext.wrap(guiGraphics),
                    x,
                    y,
                    Component.literal(formattedAmount));
        }
        if ((flags & RENDER_REMAINDER) != 0) {
            EmiRender.renderRemainderIcon(this, guiGraphics, x, y);
        }
    }

    @Override
    public List<Component> getTooltipText() {
        List<Component> tooltip = new ArrayList<>(AEKeyRendering.getTooltip(key.aeKey()));
        tooltip.add(GenericStackDisplayHelper.createAmountTooltip(asGenericStack()));
        return tooltip;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        List<ClientTooltipComponent> tooltip = new ArrayList<>();
        for (Component line : getTooltipText()) {
            tooltip.add(EmiTooltipComponents.of(line));
        }
        tooltip.addAll(super.getTooltip());
        return tooltip;
    }

    @Override
    public Component getName() {
        return key.aeKey().getDisplayName();
    }

    private GenericStack asGenericStack() {
        return new GenericStack(key.aeKey(), amount);
    }

    private static IllegalArgumentException invalid(String message) {
        Data_Energistics.LOGGER.error(message);
        return new IllegalArgumentException(message);
    }
}
