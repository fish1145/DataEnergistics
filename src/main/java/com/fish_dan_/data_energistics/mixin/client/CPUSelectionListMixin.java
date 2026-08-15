package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import appeng.client.Point;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.crafting.CraftingStatusMenu;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CPUSelectionList.class)
public abstract class CPUSelectionListMixin {

    @Unique
    private static final String DATA_ENERGISTICS_TRINITY_CPU_NAME_KEY = "block.data_energistics.trinity_data_core";

    @Unique
    private static final ResourceLocation DATA_ENERGISTICS_CPU_IDLE_TEXTURE = Data_Energistics.id(
            "textures/guis/trinity_data_core/cpu_idle.png");

    @Unique
    private static final ResourceLocation DATA_ENERGISTICS_CPU_TASK_OVERLAY_TEXTURE = Data_Energistics.id(
            "textures/guis/trinity_data_core/cpu_task_overlay.png");

    @Unique
    private static final Blitter DATA_ENERGISTICS_CPU_IDLE = Blitter.texture(DATA_ENERGISTICS_CPU_IDLE_TEXTURE, 67, 22).src(0, 0, 67, 22);

    @Unique
    private static final Blitter DATA_ENERGISTICS_CPU_TASK_OVERLAY = Blitter.texture(DATA_ENERGISTICS_CPU_TASK_OVERLAY_TEXTURE, 67, 22).src(0, 0, 67, 22);

    @Shadow
    @Final
    private Blitter buttonBg;

    @Shadow
    @Final
    private CraftingStatusMenu menu;

    @Shadow
    @Final
    private Scrollbar scrollbar;

    @Shadow
    private Rect2i bounds;

    @Unique
    @Nullable
    private GuiGraphics dataEnergistics$guiGraphics;

    @Unique
    private CraftingStatusMenu.@Nullable CraftingCpuListEntry dataEnergistics$currentCpu;

    @Unique
    private int dataEnergistics$screenX;

    @Unique
    private int dataEnergistics$screenY;

    @Unique
    private int dataEnergistics$renderRow;

    @Inject(method = "drawBackgroundLayer", at = @At("HEAD"))
    private void dataEnergistics$beginDrawTrinityCpuRows(GuiGraphics guiGraphics, Rect2i bounds, Point mouse,
                                                         CallbackInfo ci) {
        this.dataEnergistics$guiGraphics = guiGraphics;
        this.dataEnergistics$screenX = bounds.getX();
        this.dataEnergistics$screenY = bounds.getY();
        this.dataEnergistics$renderRow = 0;
        this.dataEnergistics$currentCpu = null;
    }

    @ModifyArg(
               method = "drawBackgroundLayer",
               at = @At(
                        value = "INVOKE",
                        target = "Lappeng/client/gui/widgets/CPUSelectionList;getCpuName(Lappeng/menu/me/crafting/CraftingStatusMenu$CraftingCpuListEntry;)Lnet/minecraft/network/chat/Component;"))
    private CraftingStatusMenu.CraftingCpuListEntry dataEnergistics$drawTrinityCpuBackground(
                                                                                             CraftingStatusMenu.CraftingCpuListEntry cpu) {
        this.dataEnergistics$currentCpu = cpu;
        if (this.dataEnergistics$guiGraphics != null && dataEnergistics$isTrinityCpu(cpu)) {
            DATA_ENERGISTICS_CPU_IDLE.copy()
                    .dest(
                            dataEnergistics$buttonX(),
                            dataEnergistics$buttonY(this.dataEnergistics$renderRow),
                            this.buttonBg.getSrcWidth(),
                            this.buttonBg.getSrcHeight())
                    .blit(this.dataEnergistics$guiGraphics);
        }
        this.dataEnergistics$renderRow++;
        return cpu;
    }

    @Inject(method = "formatStorage", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$formatTrinityCpuStorage(CraftingStatusMenu.CraftingCpuListEntry cpu,
                                                         CallbackInfoReturnable<String> cir) {
        if (dataEnergistics$isTrinityCpu(cpu)) {
            cir.setReturnValue(TrinityAmountFormatter.format(cpu.storage()));
        }
    }

    @ModifyArg(
               method = "drawBackgroundLayer",
               at = @At(
                        value = "INVOKE",
                        target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IFII)V"),
               slice = @Slice(
                              from = @At(
                                         value = "FIELD",
                                         target = "Lappeng/client/gui/Icon;S_PROCESSOR:Lappeng/client/gui/Icon;")),
               index = 0)
    private String dataEnergistics$formatTrinityCpuCoProcessors(String formattedAmount) {
        if (this.dataEnergistics$currentCpu != null && dataEnergistics$isTrinityCpu(this.dataEnergistics$currentCpu)) {
            return TrinityAmountFormatter.format(this.dataEnergistics$currentCpu.coProcessors());
        }
        return formattedAmount;
    }

    @Inject(method = "drawBackgroundLayer", at = @At("TAIL"))
    private void dataEnergistics$drawTrinityCpuTaskOverlay(GuiGraphics guiGraphics, Rect2i bounds, Point mouse,
                                                           CallbackInfo ci) {
        int x = dataEnergistics$buttonX();
        int y = dataEnergistics$buttonY(0);
        List<CraftingStatusMenu.CraftingCpuListEntry> cpus = this.menu.cpuList.cpus();
        int start = Mth.clamp(this.scrollbar.getCurrentScroll(), 0, cpus.size());
        int end = Mth.clamp(this.scrollbar.getCurrentScroll() + 6, 0, cpus.size());

        for (int i = start; i < end; i++) {
            CraftingStatusMenu.CraftingCpuListEntry cpu = cpus.get(i);
            if (dataEnergistics$isTrinityCpu(cpu) && cpu.currentJob() != null) {
                DATA_ENERGISTICS_CPU_TASK_OVERLAY.copy()
                        .dest(x, y, this.buttonBg.getSrcWidth(), this.buttonBg.getSrcHeight())
                        .blit(guiGraphics);
            }
            y += this.buttonBg.getSrcHeight() + 1;
        }
        this.dataEnergistics$guiGraphics = null;
        this.dataEnergistics$currentCpu = null;
    }

    @Unique
    private int dataEnergistics$buttonX() {
        return this.dataEnergistics$screenX + this.bounds.getX() + 8;
    }

    @Unique
    private int dataEnergistics$buttonY(int row) {
        return this.dataEnergistics$screenY + this.bounds.getY() + 19 + row * (this.buttonBg.getSrcHeight() + 1);
    }

    @Unique
    private static boolean dataEnergistics$isTrinityCpu(CraftingStatusMenu.CraftingCpuListEntry cpu) {
        Component name = cpu.name();
        return name != null && dataEnergistics$containsTrinityNameKey(name);
    }

    @Unique
    private static boolean dataEnergistics$containsTrinityNameKey(Component component) {
        if (component.getContents() instanceof TranslatableContents contents &&
                DATA_ENERGISTICS_TRINITY_CPU_NAME_KEY.equals(contents.getKey())) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (dataEnergistics$containsTrinityNameKey(sibling)) {
                return true;
            }
        }
        return false;
    }
}
