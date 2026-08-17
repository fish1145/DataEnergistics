package com.fish_dan_.data_energistics.mixin.core.menu.crafting;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.admission.TrinityPlanAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityDiagnosedCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityCraftingRequestContext;
import com.fish_dan_.data_energistics.common.terminal.UniversalTerminalHostAccessor;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftAmountMenuState;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;
import com.fish_dan_.data_energistics.menu.crafting.projection.TrinityCraftingPlanSummaryProjection;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.TrinityCraftingCycleSummaryProjection;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCyclePayload;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Future;

/**
 * Owns confirmation-page quantity context, diagnostics, and CPU-family filtering.
 */
@Mixin(CraftConfirmMenu.class)
public abstract class CraftConfirmMenuMixin extends AEBaseMenu implements TrinityCraftConfirmMenuState {

    @Unique
    private static final TrinityPlanAdmission DATA_ENERGISTICS_PLAN_ADMISSION = TrinityPlanAdmission.create();

    @Shadow
    @Nullable
    private ICraftingPlan result;

    @Shadow
    @Nullable
    private AEKey whatToCraft;

    @Shadow
    private int amount;

    @Shadow
    @Nullable
    private Future<ICraftingPlan> job;

    @Shadow
    private IGrid getGrid() {
        throw new AssertionError();
    }

    @Shadow
    private IActionSource getActionSrc() {
        throw new AssertionError();
    }

    @Unique
    private long dataEnergistics$requestedAmount;

    @GuiSync(791)
    @Unique
    public int dataEnergistics$quantityMode = DataEnergisticsConfiguration.INSTANCE.trinity.crafting.defaultQuantityMode.ordinal();

    @GuiSync(792)
    @Unique
    public boolean dataEnergistics$trinityOnly;

    @GuiSync(793)
    @Unique
    public boolean dataEnergistics$dynamicMaterialWarning;

    @GuiSync(794)
    @Unique
    public boolean dataEnergistics$hasDiagnostic;

    @GuiSync(795)
    @Unique
    public Component dataEnergistics$diagnostic = Component.empty();

    @GuiSync(796)
    @Unique
    public boolean dataEnergistics$ae2FallbackEstimate;

    @GuiSync(797)
    @Unique
    public boolean dataEnergistics$planReady;

    @GuiSync(798)
    @Unique
    public long dataEnergistics$planningNanos;

    @GuiSync(799)
    @Unique
    public long dataEnergistics$planRevision;

    @Unique
    private long dataEnergistics$cycleSummaryRevision = -1L;

    @Unique
    private @Nullable TrinityCraftingCycleSummary dataEnergistics$cycleSummary;

    protected CraftConfirmMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void dataEnergistics$preparePlanningMetadata(CallbackInfo ci) {
        if (!this.isServerSide()) {
            return;
        }

        this.dataEnergistics$planReady = this.result != null;
        this.dataEnergistics$trinityOnly = false;
        this.dataEnergistics$dynamicMaterialWarning = false;
        this.dataEnergistics$hasDiagnostic = false;
        this.dataEnergistics$diagnostic = Component.empty();
        this.dataEnergistics$ae2FallbackEstimate = false;
        this.dataEnergistics$planningNanos = 0L;

        if (this.result instanceof TrinityCraftingPlan plan) {
            this.dataEnergistics$quantityMode = plan.quantityMode().ordinal();
            this.dataEnergistics$trinityOnly = true;
            this.dataEnergistics$dynamicMaterialWarning = plan.stages().stream().anyMatch(TrinityPlanStage::cycleStage);
            this.dataEnergistics$planningNanos = plan.statistics().planningNanos();
            if (!plan.diagnostics().isEmpty()) {
                this.dataEnergistics$hasDiagnostic = true;
                this.dataEnergistics$diagnostic = dataEnergistics$formatDiagnostic(plan.diagnostics().getFirst());
            }
        } else if (this.result instanceof TrinityDiagnosedCraftingPlan diagnosed) {
            this.dataEnergistics$hasDiagnostic = true;
            this.dataEnergistics$diagnostic = dataEnergistics$formatDiagnostic(diagnosed.diagnostic());
            this.dataEnergistics$ae2FallbackEstimate = diagnosed.ae2FallbackEstimate();
            this.dataEnergistics$planningNanos = diagnosed.calculationNanos();
        }
    }

    @Unique
    private static Component dataEnergistics$formatDiagnostic(TrinityPlanningDiagnostic diagnostic) {
        Component message = diagnostic.message();
        return diagnostic.inputShortage()
                .<Component>map(shortage -> message.copy().append(
                        Component.translatable(
                                "gui.data_energistics.trinity_planning.diagnostic.input_shortage_detail",
                                shortage.key().getDisplayName(),
                                shortage.missing(),
                                shortage.available(),
                                shortage.required())
                                .withStyle(dataEnergistics$diagnosticDetailColor(diagnostic))))
                .orElse(message);
    }

    @Unique
    private static ChatFormatting dataEnergistics$diagnosticDetailColor(TrinityPlanningDiagnostic diagnostic) {
        return switch (diagnostic.code()) {
            case INTERNAL_ERROR, ARITHMETIC_OVERFLOW -> ChatFormatting.DARK_RED;
            case CALCULATION_CANCELLED, MIP_TIMEOUT, PLANNER_QUEUE_FULL, RUNTIME_DEADLOCK, STALE_GRAPH -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
    }

    @WrapOperation(
                   method = "broadcastChanges",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/menu/me/crafting/CraftingPlanSummary;fromJob(Lappeng/api/networking/IGrid;Lappeng/api/networking/security/IActionSource;Lappeng/api/networking/crafting/ICraftingPlan;)Lappeng/menu/me/crafting/CraftingPlanSummary;"))
    private CraftingPlanSummary dataEnergistics$projectTrinityPlan(IGrid grid,
                                                                   IActionSource actionSource,
                                                                   ICraftingPlan job,
                                                                   Operation<CraftingPlanSummary> original) {
        if (job instanceof TrinityCraftingPlan trinityPlan) {
            CraftingPlanSummary planSummary = TrinityCraftingPlanSummaryProjection.create(trinityPlan);
            dataEnergistics$syncCycleSummary(grid, trinityPlan);
            return planSummary;
        }
        if (job instanceof TrinityDiagnosedCraftingPlan diagnosed) {
            return diagnosed.ae2FallbackEstimate() ?
                    original.call(grid, actionSource, diagnosed.delegate()) :
                    TrinityCraftingPlanSummaryProjection.createDiagnostic(diagnosed);
        }
        return original.call(grid, actionSource, job);
    }

    @Unique
    private void dataEnergistics$syncCycleSummary(IGrid grid, TrinityCraftingPlan plan) {
        TrinityCraftingCycleSummary summary = TrinityCraftingCycleSummaryProjection.create(
                plan,
                grid.getStorageService().getInventory().getAvailableStacks());
        ServerPlayer player = (ServerPlayer) this.getPlayer();
        for (TrinityCraftConfirmCyclePayload payload : TrinityCraftConfirmCyclePayload.batches(
                this.containerId,
                this.dataEnergistics$planRevision,
                summary)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Inject(method = "planJob", at = @At("HEAD"))
    private void dataEnergistics$beginPlanning(AEKey what,
                                               int amount,
                                               CalculationStrategy strategy,
                                               CallbackInfoReturnable<Boolean> cir) {
        this.dataEnergistics$requestedAmount = amount;
        dataEnergistics$beginPlanRevision();
    }

    @Inject(method = "replan", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$beginReplanning(CallbackInfo ci) {
        if (!this.isServerSide()) {
            dataEnergistics$clearPlanReadiness();
            return;
        }

        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        if (this.whatToCraft == null || !data_energistics$planJob(
                this.whatToCraft,
                this.dataEnergistics$requestedAmount,
                CalculationStrategy.CRAFT_LESS)) {
            self.goBack();
        }
        ci.cancel();
    }

    @Unique
    private void dataEnergistics$beginPlanRevision() {
        if (this.isServerSide()) {
            this.dataEnergistics$planRevision = Math.incrementExact(this.dataEnergistics$planRevision);
        }
        dataEnergistics$clearPlanReadiness();
    }

    @Inject(method = "startJob", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$rejectEarlyManualStart(CallbackInfo ci) {
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        if (!this.isServerSide()) {
            if (!this.dataEnergistics$planReady) {
                ci.cancel();
            }
            return;
        }
        if (this.result != null && (this.dataEnergistics$planReady || self.isAutoStart())) {
            return;
        }
        Data_Energistics.LOGGER.debug(
                "Rejected an early crafting confirmation while its current plan was not ready; resultPresent={}, autoStart={}",
                this.result != null,
                self.isAutoStart());
        self.submitError = new CraftConfirmMenu.SyncableSubmitResult(
                CraftingSubmitResult.simpleError(CraftingSubmitErrorCode.INCOMPLETE_PLAN));
        ci.cancel();
    }

    @Unique
    private void dataEnergistics$clearPlanReadiness() {
        this.dataEnergistics$planReady = false;
        this.dataEnergistics$planningNanos = 0L;
        this.dataEnergistics$cycleSummaryRevision = -1L;
        this.dataEnergistics$cycleSummary = null;
        ((CraftConfirmMenu) (Object) this).setPlan(null);
    }

    @Inject(method = "cpuMatches", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void dataEnergistics$filterCpuByPlanFamily(
                                                       ICraftingCPU c,
                                                       CallbackInfoReturnable<Boolean> cir) {
        TrinityPlanAdmission.CpuFamily family = c instanceof TrinityDataCoreVirtualCpu ?
                TrinityPlanAdmission.CpuFamily.TRINITY :
                TrinityPlanAdmission.CpuFamily.NON_TRINITY;
        if (this.result != null && !DATA_ENERGISTICS_PLAN_ADMISSION.isCompatibleWith(this.result, family)) {
            cir.setReturnValue(false);
            return;
        }
        if (c instanceof TrinityDataCoreVirtualCpu trinityCpu && !trinityCpu.canAcceptJob()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyReturnValue(method = "getActionSrc", at = @At("RETURN"))
    private IActionSource dataEnergistics$attachQuantityContext(IActionSource original) {
        return TrinityCraftingRequestContext.attach(original, data_energistics$quantityMode());
    }

    @WrapOperation(
                   method = "goBack",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/menu/me/crafting/CraftAmountMenu;open(Lnet/minecraft/server/level/ServerPlayer;Lappeng/menu/locator/MenuHostLocator;Lappeng/api/stacks/AEKey;I)V"))
    private void dataEnergistics$restoreQuantityMode(
                                                     ServerPlayer player,
                                                     MenuHostLocator locator,
                                                     AEKey whatToCraft,
                                                     int initialAmount,
                                                     Operation<Void> original) {
        original.call(player, locator, whatToCraft, initialAmount);
        if (player.containerMenu instanceof TrinityCraftAmountMenuState amountMenu) {
            amountMenu.data_energistics$setQuantityMode(data_energistics$quantityMode());
            amountMenu.data_energistics$setInitialAmount(this.dataEnergistics$requestedAmount);
        }
    }

    @Inject(
            method = "startJob",
            at = @At(
                     value = "INVOKE",
                     target = "Lappeng/api/storage/ISubMenuHost;returnToMainMenu(Lnet/minecraft/world/entity/player/Player;Lappeng/menu/ISubMenu;)V",
                     shift = At.Shift.AFTER),
            remap = false)
    private void dataEnergistics$returnUniversalTerminalAfterSubmit(CallbackInfo ci) {
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        Player player = self.getPlayer();
        if (player.level().isClientSide) {
            return;
        }

        Object target = self.getTarget();
        if (target instanceof UniversalTerminalPart part) {
            part.returnToMainMenu(player, self);
            return;
        }

        if (target instanceof UniversalTerminalHostAccessor accessor) {
            accessor.getUniversalTerminalPart().returnToMainMenu(player, self);
        }
    }

    @Override
    public CraftingQuantityMode data_energistics$quantityMode() {
        return CraftingQuantityMode.fromOrdinal(this.dataEnergistics$quantityMode);
    }

    @Override
    public void data_energistics$setQuantityMode(CraftingQuantityMode quantityMode) {
        this.dataEnergistics$quantityMode = quantityMode.ordinal();
    }

    @Override
    public boolean data_energistics$planJob(AEKey what, long amount, CalculationStrategy strategy) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("A crafting request amount must be positive");
        }
        if (this.job != null) {
            this.job.cancel(true);
        }

        this.result = null;
        ((CraftConfirmMenu) (Object) this).clearError();
        dataEnergistics$beginPlanRevision();
        this.whatToCraft = what;
        this.dataEnergistics$requestedAmount = amount;

        // AE2 retains this private int only for its native go-back/replan path. Both paths are intercepted above and
        // use requestedAmount, so this mirror is never used to calculate or submit the request.
        this.amount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;

        IGrid grid = getGrid();
        if (grid == null) {
            return false;
        }
        this.job = grid.getCraftingService().beginCraftingCalculation(
                this.getPlayer().level(),
                this::getActionSrc,
                what,
                amount,
                strategy);
        return true;
    }

    @Override
    public boolean data_energistics$isPlanReady() {
        return this.dataEnergistics$planReady;
    }

    @Override
    public long data_energistics$planRevision() {
        return this.dataEnergistics$planRevision;
    }

    @Override
    public void data_energistics$receiveCycleSummary(long revision, TrinityCraftingCycleSummary summary) {
        if (revision != this.dataEnergistics$planRevision) {
            throw new IllegalArgumentException("Trinity crafting cycle summary revision does not match this menu");
        }
        this.dataEnergistics$cycleSummaryRevision = revision;
        this.dataEnergistics$cycleSummary = summary;
    }

    @Override
    public @Nullable TrinityCraftingCycleSummary data_energistics$cycleSummary() {
        return this.dataEnergistics$cycleSummaryRevision == this.dataEnergistics$planRevision ? this.dataEnergistics$cycleSummary : null;
    }

    @Override
    public long data_energistics$planningNanos() {
        return this.dataEnergistics$planningNanos;
    }

    @Override
    public boolean data_energistics$isTrinityOnly() {
        return this.dataEnergistics$trinityOnly;
    }

    @Override
    public boolean data_energistics$hasDynamicMaterialWarning() {
        return this.dataEnergistics$dynamicMaterialWarning;
    }

    @Override
    public boolean data_energistics$hasDiagnostic() {
        return this.dataEnergistics$hasDiagnostic;
    }

    @Override
    public boolean data_energistics$isAe2FallbackEstimate() {
        return this.dataEnergistics$ae2FallbackEstimate;
    }

    @Override
    public Component data_energistics$diagnostic() {
        return this.dataEnergistics$diagnostic;
    }
}
