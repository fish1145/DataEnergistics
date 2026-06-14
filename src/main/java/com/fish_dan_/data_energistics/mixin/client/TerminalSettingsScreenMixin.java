package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.menu.common.PatternEncodingSourceAware;

import net.minecraft.network.chat.Component;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.TerminalSettingsScreen;
import appeng.client.gui.widgets.AECheckbox;
import appeng.menu.me.common.MEStorageMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TerminalSettingsScreen.class)
public abstract class TerminalSettingsScreenMixin
        extends AESubScreen<MEStorageMenu, MEStorageScreen<MEStorageMenu>> {

    @Unique
    private static final int DATA_ENERGISTICS_UPLOAD_CHECKBOX_X = 10;
    @Unique
    private static final int DATA_ENERGISTICS_UPLOAD_CHECKBOX_Y = 96;
    @Unique
    private static final int DATA_ENERGISTICS_UPLOAD_CHECKBOX_WIDTH = 180;
    @Unique
    private static final int DATA_ENERGISTICS_CLEAR_GRID_OFFSET_Y = -10;

    @Unique
    private AECheckbox dataEnergistics$uploadEnabledCheckbox;
    @Unique
    private AECheckbox dataEnergistics$clearGridOnCloseCheckbox;
    @Unique
    private int dataEnergistics$clearGridOnCloseOriginalY = Integer.MIN_VALUE;

    protected TerminalSettingsScreenMixin(MEStorageScreen<MEStorageMenu> parent, String stylePath) {
        super(parent, stylePath);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void dataEnergistics$initUploadEnabledCheckbox(CallbackInfo ci) {
        this.dataEnergistics$uploadEnabledCheckbox = new AECheckbox(
                this.leftPos + DATA_ENERGISTICS_UPLOAD_CHECKBOX_X,
                this.topPos + DATA_ENERGISTICS_UPLOAD_CHECKBOX_Y,
                DATA_ENERGISTICS_UPLOAD_CHECKBOX_WIDTH,
                AECheckbox.SIZE,
                this.getStyle(),
                Component.translatable("gui.data_energistics.terminal_settings.enable_upload"));
        this.dataEnergistics$uploadEnabledCheckbox.setChangeListener(this::dataEnergistics$saveUploadEnabled);
        this.addRenderableWidget(this.dataEnergistics$uploadEnabledCheckbox);
        this.dataEnergistics$clearGridOnCloseCheckbox = dataEnergistics$findCheckboxAbove(this.dataEnergistics$uploadEnabledCheckbox.getY());
        if (this.dataEnergistics$clearGridOnCloseCheckbox != null) {
            this.dataEnergistics$clearGridOnCloseOriginalY = this.dataEnergistics$clearGridOnCloseCheckbox.getY();
        }
        dataEnergistics$repositionTerminalSettings();
        dataEnergistics$syncUploadEnabledCheckbox();
    }

    @Unique
    private void dataEnergistics$saveUploadEnabled() {
        if (this.dataEnergistics$uploadEnabledCheckbox == null) {
            return;
        }

        if (this.menu instanceof PatternEncodingSourceAware sourceAware) {
            sourceAware.data_energistics$setUploadEnabled(this.dataEnergistics$uploadEnabledCheckbox.isSelected());
        }

        dataEnergistics$syncUploadEnabledCheckbox();
    }

    @Unique
    private void dataEnergistics$syncUploadEnabledCheckbox() {
        if (this.dataEnergistics$uploadEnabledCheckbox == null) {
            return;
        }

        dataEnergistics$repositionTerminalSettings();
        this.dataEnergistics$uploadEnabledCheckbox.setX(this.leftPos + DATA_ENERGISTICS_UPLOAD_CHECKBOX_X);
        this.dataEnergistics$uploadEnabledCheckbox.setY(this.topPos + DATA_ENERGISTICS_UPLOAD_CHECKBOX_Y);
        this.dataEnergistics$uploadEnabledCheckbox.setWidth(DATA_ENERGISTICS_UPLOAD_CHECKBOX_WIDTH);

        boolean visible = this.menu instanceof PatternEncodingSourceAware;
        this.dataEnergistics$uploadEnabledCheckbox.visible = visible;
        this.dataEnergistics$uploadEnabledCheckbox.active = visible;

        if (visible && this.menu instanceof PatternEncodingSourceAware sourceAware) {
            this.dataEnergistics$uploadEnabledCheckbox.setSelected(sourceAware.data_energistics$isUploadEnabled());
        }
    }

    @Unique
    private void dataEnergistics$repositionTerminalSettings() {
        if (this.dataEnergistics$clearGridOnCloseCheckbox != null
                && this.dataEnergistics$clearGridOnCloseOriginalY != Integer.MIN_VALUE) {
            this.dataEnergistics$clearGridOnCloseCheckbox
                    .setY(this.dataEnergistics$clearGridOnCloseOriginalY + DATA_ENERGISTICS_CLEAR_GRID_OFFSET_Y);
        }
    }

    @Unique
    private AECheckbox dataEnergistics$findCheckboxAbove(int maxY) {
        AECheckbox candidate = null;
        for (var renderable : this.renderables) {
            if (renderable instanceof AECheckbox checkbox
                    && checkbox != this.dataEnergistics$uploadEnabledCheckbox
                    && checkbox.getY() < maxY) {
                if (candidate == null || checkbox.getY() > candidate.getY()) {
                    candidate = checkbox;
                }
            }
        }
        return candidate;
    }
}
