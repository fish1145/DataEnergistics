package com.fish_dan_.data_energistics.client.screen;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.me.common.ClientDisplaySlot;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.TabButton;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.function.Consumer;

public class SetIntProcessingPatternAmountScreen
                                                 extends AESubScreen<PatternEncodingTermMenu, PatternEncodingTermScreen<PatternEncodingTermMenu>> {

    private final NumberEntryWidget amount;
    private final GenericStack currentStack;
    private final Consumer<GenericStack> setter;

    public SetIntProcessingPatternAmountScreen(
                                               PatternEncodingTermScreen<PatternEncodingTermMenu> parentScreen,
                                               GenericStack currentStack,
                                               Consumer<GenericStack> setter) {
        super(parentScreen, "/screens/set_processing_pattern_amount.json");
        this.currentStack = currentStack;
        this.setter = setter;

        this.widgets.addButton("save", GuiText.Set.text(), this::confirm);

        ItemStack icon = this.getMenu().getHost().getMainMenuIcon();
        TabButton button = new TabButton(Icon.BACK, icon.getHoverName(), btn -> this.returnToParent());
        this.widgets.add("back", button);

        this.amount = this.widgets.addNumberEntryWidget("amountToStock", NumberEntryType.of(currentStack.what()));
        this.amount.setMaxValue(getMaxAmount());
        this.amount.setLongValue(currentStack.amount());
        this.amount.setTextFieldStyle(this.style.getWidget("amountToStockInput"));
        this.amount.setMinValue(0L);
        this.amount.setHideValidationIcon(true);
        this.amount.setOnConfirm(this::confirm);
        this.addClientSideSlot(new ClientDisplaySlot(currentStack), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    protected void init() {
        super.init();
        this.setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void confirm() {
        this.amount.getLongValue().ifPresent(newAmount -> {
            long clamped = Longs.constrainToRange(newAmount, 0L, getMaxAmount());
            if (clamped <= 0L) {
                this.setter.accept(null);
            } else {
                this.setter.accept(new GenericStack(this.currentStack.what(), clamped));
            }
            this.returnToParent();
        });
    }

    private long getMaxAmount() {
        return Ints.constrainToRange(Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
    }
}
