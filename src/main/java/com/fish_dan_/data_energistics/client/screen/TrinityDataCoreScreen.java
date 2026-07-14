package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.widget.OutputSideActionButton;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildOptions;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.network.TrinityDataCoreAutoBuildPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class TrinityDataCoreScreen extends AEBaseScreen<TrinityDataCoreMenu> {

    private final MultiBlockAutoBuildOverlay autoBuildOverlay;
    private final OutputSideActionButton refundAllButton;

    public TrinityDataCoreScreen(TrinityDataCoreMenu menu, Inventory playerInventory, Component title,
                                 ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.autoBuildOverlay = new MultiBlockAutoBuildOverlay(createAutoBuildDescription(
                selection -> PacketDistributor.sendToServer(
                        new TrinityDataCoreAutoBuildPayload(toTrinityAutoBuildRequest(selection)))));
        OutputSideActionButton autoBuildOverlayButton = new OutputSideActionButton(
                button -> this.autoBuildOverlay.toggle(),
                "button.data_energistics.trinity_data_core.auto_build");
        autoBuildOverlayButton.setIconName("MULTIBLOCK_BUILDER_OPEN");
        this.refundAllButton = new OutputSideActionButton(
                ignored -> this.menu.sendRefundAll(),
                "button.data_energistics.trinity_data_core.refund",
                "button.data_energistics.trinity_data_core.refund.hint");
        this.refundAllButton.setIconName("TRINITY_REFUND");
        this.addToLeftToolbar(autoBuildOverlayButton);
        this.addToLeftToolbar(this.refundAllButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.autoBuildOverlay.updateViewport(
                this.width,
                this.height,
                new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight));

        setTextContent("dialog_title", Component.translatable("block.data_energistics.trinity_data_core"));
        this.refundAllButton.active = this.menu.hasRefundablePatternState;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.autoBuildOverlay.render(guiGraphics, this.font, mouseX, mouseY, partialTicks);
        this.autoBuildOverlay.renderTooltip(guiGraphics, this.font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.autoBuildOverlay.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.autoBuildOverlay.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double dragX, double dragY) {
        if (this.autoBuildOverlay.mouseDragged(mouseX, mouseY, mouseButton)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, mouseButton, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.menu.getHostUiExtension().handleKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.autoBuildOverlay.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public List<Rect2i> getExclusionZones() {
        List<Rect2i> zones = new ArrayList<>(super.getExclusionZones());
        if (this.autoBuildOverlay.isVisible()) {
            zones.add(this.autoBuildOverlay.bounds());
        }
        return zones;
    }

    static MultiBlockAutoBuildOverlayDescription createAutoBuildDescription(
                                                                            Consumer<MultiBlockAutoBuildSelection> confirmationConsumer) {
        return new MultiBlockAutoBuildOverlayDescription(
                Component.translatable("screen.data_energistics.trinity_data_core.auto_build.title"),
                List.of(
                        trinityStructure(
                                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX,
                                0,
                                "screen.data_energistics.trinity_data_core.auto_build.structure.main",
                                "screen.data_energistics.trinity_data_core.auto_build.storage_tier",
                                TrinityAutoBuildOptions.MIN_REPEAT_COUNT,
                                TrinityAutoBuildOptions.MIN_REPEAT_COUNT,
                                true),
                        trinityStructure(
                                TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX,
                                1,
                                "screen.data_energistics.trinity_data_core.auto_build.structure.cpu",
                                "screen.data_energistics.trinity_data_core.auto_build.cpu_tier",
                                TrinityAutoBuildOptions.MIN_REPEAT_COUNT,
                                TrinityAutoBuildOptions.MAX_REPEAT_COUNT,
                                false),
                        trinityStructure(
                                TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX,
                                2,
                                "screen.data_energistics.trinity_data_core.auto_build.structure.crafting",
                                "screen.data_energistics.trinity_data_core.auto_build.pattern_tier",
                                TrinityAutoBuildOptions.MIN_REPEAT_COUNT,
                                TrinityAutoBuildOptions.MAX_REPEAT_COUNT,
                                false)),
                confirmationConsumer);
    }

    static TrinityAutoBuildRequest toTrinityAutoBuildRequest(MultiBlockAutoBuildSelection selection) {
        return new TrinityAutoBuildRequest(
                selection.structureId(),
                new TrinityAutoBuildOptions(
                        selection.buildRequested(),
                        selection.repeatCount(),
                        Map.of(
                                TrinityAutoBuildBlockMap.categoryForStructure(selection.structureId()),
                                selection.tierValue())));
    }

    private static MultiBlockAutoBuildOverlayDescription.Structure trinityStructure(
                                                                                    int structureId,
                                                                                    int iconIndex,
                                                                                    String structureLabelKey,
                                                                                    String tierLabelKey,
                                                                                    int minimumRepeatCount,
                                                                                    int maximumRepeatCount,
                                                                                    boolean buildRequestedByDefault) {
        String category = TrinityAutoBuildBlockMap.categoryForStructure(structureId);
        List<ResourceLocation> tierIds = TrinityAutoBuildBlockMap.categories().get(category);
        if (tierIds == null || tierIds.isEmpty()) {
            throw new IllegalStateException("Missing Trinity auto-build tiers for " + category);
        }

        List<MultiBlockAutoBuildOverlayDescription.TierOption> tierOptions = new ArrayList<>(tierIds.size());
        for (int tierIndex = 1; tierIndex <= tierIds.size(); tierIndex++) {
            tierOptions.add(new MultiBlockAutoBuildOverlayDescription.TierOption(
                    tierIndex,
                    Component.literal(trinityTierLabel(category, tierIndex, tierIds.get(tierIndex - 1)))));
        }
        return new MultiBlockAutoBuildOverlayDescription.Structure(
                structureId,
                iconIndex,
                Component.translatable(structureLabelKey),
                Component.translatable(tierLabelKey),
                minimumRepeatCount,
                maximumRepeatCount,
                tierOptions,
                buildRequestedByDefault);
    }

    private static String trinityTierLabel(String category, int tierIndex, ResourceLocation tierId) {
        if (TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE.equals(category)) {
            return switch (tierIndex) {
                case 1 -> "64";
                case 2 -> "128";
                case 3 -> "512";
                default -> throw new IllegalStateException("Unsupported Trinity pattern core tier: " + tierIndex);
            };
        }

        String path = tierId.getPath();
        int separator = path.lastIndexOf('_');
        if (separator < 0 || separator == path.length() - 1) {
            throw new IllegalStateException("Cannot render Trinity auto-build tier label for " + path);
        }
        return path.substring(separator + 1).toUpperCase(Locale.ROOT);
    }
}
