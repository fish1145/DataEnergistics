package com.fish_dan_.data_energistics.client.hud.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;

/** LDLib2 world HUD layer for the currently held orbital control terminal. */
@OnlyIn(Dist.CLIENT)
public final class OrbitalControlHudLayer implements ModularHudLayer {

    private static final int WIDTH = 360;
    private static final int HEIGHT = 96;
    private static final int TEXT_COLOR = 0xE8F4FF;

    private final ModularUI modularUI;
    private final Label statusLabel;
    private Component displayedStatus = Component.empty();

    private OrbitalControlHudLayer() {
        UIElement root = new UIElement();
        root.setId("orbital_control_hud_root");
        root.setAllowHitTest(false);
        root.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8)
                .top(8)
                .width(WIDTH)
                .height(HEIGHT));

        this.statusLabel = new Label();
        this.statusLabel.setId("orbital_control_hud_status");
        this.statusLabel.setAllowHitTest(false);
        this.statusLabel.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(9)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.TOP)
                .textWrap(TextWrap.WRAP)
                .textColor(TEXT_COLOR)
                .textShadow(true));
        this.statusLabel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(4)
                .top(4)
                .width(WIDTH - 8)
                .height(HEIGHT - 8));
        root.addChild(this.statusLabel);
        this.modularUI = ModularUI.of(UI.of(root));
        this.modularUI.setTickWhileRending(true);
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Data_Energistics.id("orbital_control_hud"),
                new OrbitalControlHudLayer());
    }

    @Override
    public ModularUI getModularUI() {
        Component status = OrbitalControlHudClientState.status();
        if (status == null) {
            return null;
        }
        if (!status.equals(this.displayedStatus)) {
            this.displayedStatus = status;
            this.statusLabel.setValue(status);
        }
        return this.modularUI;
    }
}
