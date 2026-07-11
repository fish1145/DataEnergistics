package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.widget.OutputSideActionButton;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockFailureText;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildOptions;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenuHost;
import com.fish_dan_.data_energistics.network.TrinityDataCoreAutoBuildPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.Tooltips;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class TrinityDataCoreScreen extends AEBaseScreen<TrinityDataCoreMenu> {

    private static final int LABEL_COLOR = 0x080C1B;
    private static final int VALUE_COLOR = 0x9CD3FF;
    private static final int SUCCESS_COLOR = 0x62D96B;
    private static final int WARNING_COLOR = 0xFFB347;
    private static final int ERROR_COLOR = 0xFF6B6B;
    private static final int BUSY_COLOR = 0xFFE066;
    private static final int STATUS_X = 14;
    private static final int STATUS_Y = 19;
    private static final int STATUS_WIDTH = 113;
    private static final int STATUS_HEIGHT = 100;
    private static final int LEFT_TEXT_X = 18;
    private static final int LEFT_TEXT_Y = 23;
    private static final int LEFT_TEXT_WIDTH = 105;
    private static final int RIGHT_TEXT_X = 132;
    private static final int RIGHT_TEXT_WIDTH = 105;
    private static final int CPU_TEXT_Y = 21;
    private static final int STORAGE_TEXT_X = 132;
    private static final int STORAGE_TEXT_WIDTH = 105;
    private static final int STORAGE_TYPES_TEXT_Y = 68;
    private static final int STORAGE_AMOUNT_TEXT_Y = 78;
    private static final int CRAFTING_TEXT_Y = 98;
    private static final int CRAFTING_HOVER_X = 129;
    private static final int CRAFTING_HOVER_Y = 93;
    private static final int CRAFTING_HOVER_WIDTH = 112;
    private static final int CRAFTING_HOVER_HEIGHT = 27;
    private static final int LINE_HEIGHT = 10;
    private static final BigInteger UNIT_BASE = BigInteger.valueOf(1024L);
    private static final String[] COMPACT_UNITS = { "", "K", "M", "G", "T", "P", "E" };

    private final OutputSideActionButton autoBuildOverlayButton;
    private final MultiBlockAutoBuildOverlay autoBuildOverlay;
    private final OutputSideActionButton refundAllButton;

    public TrinityDataCoreScreen(TrinityDataCoreMenu menu, Inventory playerInventory, Component title,
                                 ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.autoBuildOverlay = new MultiBlockAutoBuildOverlay(createAutoBuildDescription(
                selection -> PacketDistributor.sendToServer(
                        new TrinityDataCoreAutoBuildPayload(toTrinityAutoBuildRequest(selection)))));
        this.autoBuildOverlayButton = new OutputSideActionButton(
                button -> this.autoBuildOverlay.toggle(),
                "button.data_energistics.trinity_data_core.auto_build");
        this.autoBuildOverlayButton.setIconName("MULTIBLOCK_BUILDER_OPEN");
        this.refundAllButton = new OutputSideActionButton(
                ignored -> this.menu.sendRefundAll(),
                "button.data_energistics.trinity_data_core.refund",
                "button.data_energistics.trinity_data_core.refund.hint");
        this.refundAllButton.setIconName("TRINITY_REFUND");
        this.addToLeftToolbar(this.autoBuildOverlayButton);
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
        setTextContent("online", Component.translatable(
                this.menu.online ? "screen.data_energistics.trinity_data_core.status_online" : "screen.data_energistics.trinity_data_core.status_offline"));
        setTextContent("formed", Component.translatable(
                "screen.data_energistics.trinity_data_core.formed",
                Component.translatable(this.menu.structureFormed ? "screen.data_energistics.trinity_data_core.formed.yes" : "screen.data_energistics.trinity_data_core.formed.no")));
        setTextContent("matched_blocks", Component.translatable(
                "screen.data_energistics.trinity_data_core.matched_blocks",
                this.menu.matchedBlockCount));
        setTextContent("last_failure", Component.translatable(
                "screen.data_energistics.trinity_data_core.last_failure",
                getFailureSummary()));
        setTextContent("busy_cpus", Component.translatable(
                "screen.data_energistics.trinity_data_core.busy_cpus",
                this.menu.busyCraftingCpuCount));
        this.refundAllButton.active = this.menu.hasRefundablePatternState;
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        drawStatusText(guiGraphics);
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

    private void drawStatusText(GuiGraphics guiGraphics) {
        drawStructureStatus(guiGraphics);
        drawCpuStatus(guiGraphics);
        drawStorageStatus(guiGraphics);
        drawCraftingStatus(guiGraphics);
    }

    private void drawStructureStatus(GuiGraphics guiGraphics) {
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.status_label", Component.translatable(
                this.menu.online ? "screen.data_energistics.trinity_data_core.status_online" : "screen.data_energistics.trinity_data_core.status_offline"), LEFT_TEXT_X, LEFT_TEXT_Y, statusColor(this.menu.online), LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.main_structure_label", Component.translatable(
                this.menu.structureFormed ? "screen.data_energistics.trinity_data_core.formed.yes" : "screen.data_energistics.trinity_data_core.formed.no"), LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT, statusColor(this.menu.structureFormed), LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.matched_blocks_label", Component.literal(
                compactNumber(Integer.toString(this.menu.matchedBlockCount))), LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT * 2, VALUE_COLOR,
                LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.cpu_structure_label", structureCountStatus(
                this.menu.cpuStructureFormed,
                this.menu.cpuStructureMatchedBlockCount), LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT * 3,
                statusColor(this.menu.cpuStructureFormed), LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.crafting_structure_label", craftingStructureStatus(),
                LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT * 4,
                statusColor(this.menu.craftingStructureFormed && this.menu.craftingPatternCapacity > 0), LEFT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.last_failure_label", getFailureSummary(),
                LEFT_TEXT_X, LEFT_TEXT_Y + LINE_HEIGHT * 5, hasAnyFailure() ? ERROR_COLOR : SUCCESS_COLOR, LEFT_TEXT_WIDTH);
    }

    private Component structureCountStatus(boolean formed, int matchedBlocks) {
        if (!formed) {
            return Component.translatable("screen.data_energistics.trinity_data_core.formed.no");
        }
        return Component.literal(compactNumber(Integer.toString(matchedBlocks)));
    }

    private Component craftingStructureStatus() {
        if (!this.menu.craftingStructureFormed) {
            return Component.translatable("screen.data_energistics.trinity_data_core.formed.no");
        }
        return Component.literal(formatCapacityPair(
                Integer.toString(this.menu.craftingPatternCoreCount),
                Integer.toString(this.menu.craftingPatternCapacity)));
    }

    private void drawCpuStatus(GuiGraphics guiGraphics) {
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.cpu_label", Component.literal(
                Integer.toString(this.menu.busyCraftingCpuCount)), RIGHT_TEXT_X, CPU_TEXT_Y,
                this.menu.busyCraftingCpuCount > 0 ? BUSY_COLOR : VALUE_COLOR, RIGHT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.cpu_partitions_label", Component.literal(
                this.menu.busyCpuPartitionCount + "/" + this.menu.cpuPartitionCount), RIGHT_TEXT_X, CPU_TEXT_Y + LINE_HEIGHT,
                this.menu.busyCpuPartitionCount > 0 ? BUSY_COLOR : VALUE_COLOR, RIGHT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.cpu_storage_label", Component.literal(
                compactNumber(Long.toString(this.menu.cpuStorageBytes))), RIGHT_TEXT_X, CPU_TEXT_Y + LINE_HEIGHT * 2, VALUE_COLOR,
                RIGHT_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.cpu_coprocessors_label", Component.literal(
                compactNumber(Integer.toString(this.menu.cpuCoProcessors))), RIGHT_TEXT_X, CPU_TEXT_Y + LINE_HEIGHT * 3, VALUE_COLOR,
                RIGHT_TEXT_WIDTH);
    }

    private void drawStorageStatus(GuiGraphics guiGraphics) {
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.storage_types_label",
                Component.literal(formatCapacityPair(Integer.toString(this.menu.storedTypeCount), this.menu.storedTypeCapacityText)),
                STORAGE_TEXT_X, STORAGE_TYPES_TEXT_Y, VALUE_COLOR, STORAGE_TEXT_WIDTH);
        drawKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.storage_amount_label",
                Component.literal(formatCapacityPair(this.menu.storedAmountText, this.menu.storedAmountCapacityText)),
                STORAGE_TEXT_X, STORAGE_AMOUNT_TEXT_Y, VALUE_COLOR, STORAGE_TEXT_WIDTH);
    }

    private void drawCraftingStatus(GuiGraphics guiGraphics) {
        int statusColor = craftingUnavailable() ? ERROR_COLOR : this.menu.hasCraftingTarget() ? BUSY_COLOR : SUCCESS_COLOR;
        drawCenteredKeyValueText(guiGraphics, "screen.data_energistics.trinity_data_core.molecular_label", getMolecularStatus(),
                RIGHT_TEXT_X, CRAFTING_TEXT_Y, statusColor, RIGHT_TEXT_WIDTH);
    }

    private static void drawKeyValueText(GuiGraphics guiGraphics, String labelKey, Component value, int x, int y, int valueColor,
                                         int maxWidth) {
        Component label = Component.translatable(labelKey);
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, label, x, y, LABEL_COLOR, false);
        int valueX = x + font.width(label);
        int valueWidth = Math.max(0, maxWidth - font.width(label));
        guiGraphics.drawString(font, trimToWidth(font, value.getString(), valueWidth), valueX, y, valueColor, false);
    }

    private static void drawCenteredKeyValueText(GuiGraphics guiGraphics, String labelKey, Component value, int x, int y,
                                                 int valueColor, int maxWidth) {
        Component label = Component.translatable(labelKey);
        Font font = Minecraft.getInstance().font;
        int labelWidth = font.width(label);
        int valueWidth = Math.max(0, maxWidth - font.width(label));
        String valueText = trimToWidth(font, value.getString(), valueWidth);
        int textX = x + (maxWidth - labelWidth - font.width(valueText)) / 2;
        guiGraphics.drawString(font, label, textX, y, LABEL_COLOR, false);
        int valueX = textX + labelWidth;
        guiGraphics.drawString(font, valueText, valueX, y, valueColor, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        GenericStack target = this.menu.getCraftingTarget();
        if (this.menu.getCarried().isEmpty() && !craftingUnavailable() && target != null &&
                isMouseOverLocal(mouseX, mouseY, CRAFTING_HOVER_X, CRAFTING_HOVER_Y, CRAFTING_HOVER_WIDTH, CRAFTING_HOVER_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(target.what().getDisplayName());
            tooltip.add(GenericStackDisplayHelper.createAmountTooltip(target));
            tooltip.add(Component.translatable(
                    "screen.data_energistics.trinity_data_core.busy_cpus",
                    this.menu.busyCraftingCpuCount).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            tooltip.add(Component.translatable(
                    "screen.data_energistics.trinity_data_core.crafting_pattern_capacity",
                    this.menu.craftingPatternCapacity).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        if (this.menu.getCarried().isEmpty() && (hasFailure() || hasCpuFailure() || hasCraftingFailure()) &&
                isMouseOverLocal(mouseX, mouseY, STATUS_X, STATUS_Y, STATUS_WIDTH, STATUS_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            if (hasFailure()) {
                tooltip.add(Component.translatable(
                        "screen.data_energistics.trinity_data_core.last_failure",
                        MultiBlockFailureText.describe(this.menu.lastFailureReason)));
                if (!this.menu.lastFailurePosition.isBlank()) {
                    tooltip.add(Component.translatable(
                            "screen.data_energistics.trinity_data_core.failure_position",
                            this.menu.lastFailurePosition).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
                }
            }
            if (hasCpuFailure()) {
                tooltip.add(Component.translatable(
                        "screen.data_energistics.trinity_data_core.cpu_failure",
                        MultiBlockFailureText.describe(this.menu.cpuLastFailureReason)));
                if (!this.menu.cpuLastFailurePosition.isBlank()) {
                    tooltip.add(Component.translatable(
                            "screen.data_energistics.trinity_data_core.cpu_failure_position",
                            this.menu.cpuLastFailurePosition).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
                }
            }
            if (hasCraftingFailure()) {
                tooltip.add(Component.translatable(
                        "screen.data_energistics.trinity_data_core.crafting_failure",
                        MultiBlockFailureText.describe(this.menu.craftingLastFailureReason)));
                if (!this.menu.craftingLastFailurePosition.isBlank()) {
                    tooltip.add(Component.translatable(
                            "screen.data_energistics.trinity_data_core.crafting_failure_position",
                            this.menu.craftingLastFailurePosition).withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
                }
            }
            this.drawTooltip(guiGraphics, mouseX, mouseY, tooltip);
            return;
        }

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private Component getFailureSummary() {
        if (!hasFailure() && !hasCpuFailure() && !hasCraftingFailure()) {
            return Component.translatable("screen.data_energistics.trinity_data_core.no_failure");
        }
        if (hasFailure()) {
            return MultiBlockFailureText.describe(this.menu.lastFailureReason);
        }
        if (hasCpuFailure()) {
            return MultiBlockFailureText.describe(this.menu.cpuLastFailureReason);
        }
        return MultiBlockFailureText.describe(this.menu.craftingLastFailureReason);
    }

    private Component getMolecularStatus() {
        if (craftingUnavailable()) {
            return Component.translatable("screen.data_energistics.trinity_data_core.molecular_unavailable");
        }
        GenericStack target = this.menu.getCraftingTarget();
        if (target == null) {
            return Component.translatable("screen.data_energistics.trinity_data_core.molecular_idle");
        }
        return target.what().getDisplayName();
    }

    private static int statusColor(boolean ok) {
        return ok ? SUCCESS_COLOR : WARNING_COLOR;
    }

    private static String compactNumber(String value) {
        if (value.isBlank()) {
            return "0";
        }
        BigInteger amount = new BigInteger(value.trim());
        if (amount.signum() == 0) {
            return "0";
        }

        BigInteger absoluteAmount = amount.abs();
        BigInteger divisor = BigInteger.ONE;
        int unitIndex = 0;
        while (unitIndex < COMPACT_UNITS.length - 1 && absoluteAmount.compareTo(divisor.multiply(UNIT_BASE)) >= 0) {
            divisor = divisor.multiply(UNIT_BASE);
            unitIndex++;
        }
        if (unitIndex == 0) {
            return amount.toString();
        }

        BigInteger whole = absoluteAmount.divide(divisor);
        BigInteger fraction = absoluteAmount.remainder(divisor).multiply(BigInteger.TEN).divide(divisor);
        String sign = amount.signum() < 0 ? "-" : "";
        if (whole.compareTo(BigInteger.TEN) >= 0 || fraction.signum() == 0) {
            return sign + whole + COMPACT_UNITS[unitIndex];
        }
        return sign + whole + "." + fraction + COMPACT_UNITS[unitIndex];
    }

    private static String formatCapacityPair(String current, String capacity) {
        return compactCapacityNumber(current) + "/" + compactCapacityNumber(capacity);
    }

    private static String compactCapacityNumber(String value) {
        if (TrinityDataCoreMenuHost.UNLIMITED_STORAGE_CAPACITY.equals(value)) {
            return value;
        }
        return compactNumber(value);
    }

    private static String trimToWidth(Font font, String value, int maxWidth) {
        if (maxWidth <= 0 || value.isEmpty()) {
            return "";
        }
        if (font.width(value) <= maxWidth) {
            return value;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return font.plainSubstrByWidth(value, maxWidth);
        }
        return font.plainSubstrByWidth(value, maxWidth - ellipsisWidth) + ellipsis;
    }

    private boolean hasFailure() {
        return !this.menu.lastFailureReason.isBlank();
    }

    private boolean hasCpuFailure() {
        return !this.menu.cpuLastFailureReason.isBlank();
    }

    private boolean hasCraftingFailure() {
        return !this.menu.craftingLastFailureReason.isBlank();
    }

    private boolean hasAnyFailure() {
        return hasFailure() || hasCpuFailure() || hasCraftingFailure();
    }

    private boolean craftingUnavailable() {
        return !this.menu.craftingStructureFormed || this.menu.craftingPatternCapacity <= 0;
    }

    private boolean isMouseOverLocal(int mouseX, int mouseY, int x, int y, int width, int height) {
        int screenX = this.leftPos + x;
        int screenY = this.topPos + y;
        return mouseX >= screenX && mouseX < screenX + width && mouseY >= screenY && mouseY < screenY + height;
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
