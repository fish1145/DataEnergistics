package com.fish_dan_.data_energistics.mixin.core.menu.crafting;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.admission.TrinityPlanAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityDiagnosedCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressChannel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressMeasure;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressPhase;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityCraftingRequestContext;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningProgressContext;
import com.fish_dan_.data_energistics.common.terminal.UniversalTerminalHostAccessor;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftAmountMenuState;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;
import com.fish_dan_.data_energistics.menu.crafting.projection.TrinityCraftingPlanSummaryProjection;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.TrinityCraftingCycleSummaryProjection;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;
import com.fish_dan_.data_energistics.menu.crafting.tree.CraftingPlanTreeMenu;
import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanSessionTransfer;
import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanTreeRequest;
import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanTreeResult;
import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanTreeSession;
import com.fish_dan_.data_energistics.network.trinity.crafting.progress.TrinityCraftConfirmPlanningProgressPayload;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCyclePayload;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.core.network.clientbound.CraftConfirmPlanPacket;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.helpers.ICraftingGridMenu.AutoCraftEntry;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.PacketDistributor;

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

import java.util.List;
import java.util.concurrent.Future;

/**
 * Owns confirmation-page quantity context, diagnostics, and CPU-family filtering.
 */
@Mixin(CraftConfirmMenu.class)
public abstract class CraftConfirmMenuMixin extends AEBaseMenu implements TrinityCraftConfirmMenuState, CraftingPlanSessionTransfer {

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
    @Nullable
    private ICraftingCPU selectedCpu;
    @Shadow
    @Nullable
    private List<AutoCraftEntry> autoCraftingQueue;
    @Shadow
    @Nullable
    private List<Integer> requestedSlots;

    @GuiSync(800)
    @Unique
    public boolean dataEnergistics$hasTrinityCpu;
    @GuiSync(801)
    @Unique
    public boolean dataEnergistics$treeReady;
    @Unique
    private @Nullable CraftingPlanTreeSession dataEnergistics$treeSession;
    @Unique
    private @Nullable ICraftingCPU dataEnergistics$restoreTreeCpu;

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

    @GuiSync(802)
    @Unique
    public Component dataEnergistics$diagnosticDetail = Component.empty();

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

    @Unique
    private @Nullable TrinityPlanningProgressChannel dataEnergistics$planningProgressChannel;

    @Unique
    private @Nullable TrinityPlanningProgressSnapshot dataEnergistics$clientPlanningProgress;

    @Unique
    private long dataEnergistics$clientPlanningProgressRevision = -1L;

    @Unique
    private long dataEnergistics$clientPlanningProgressSequence = -1L;

    @Unique
    private long dataEnergistics$planningProgressSequence;

    @Unique
    private long dataEnergistics$lastPlanningProgressTick = Long.MIN_VALUE;

    @Unique
    private @Nullable TrinityPlanningProgressSnapshot dataEnergistics$lastSentPlanningProgress;

    protected CraftConfirmMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$registerPlanTreeAction(int id, Inventory inventory, ISubMenuHost host, CallbackInfo ci) {
        registerClientAction("dataEnergisticsPlanTree", Long.class, this::dataEnergistics$handlePlanTreeAction);
    }

    @Unique
    private void dataEnergistics$handlePlanTreeAction(@Nullable Long revision) {
        if (revision != null && revision == this.dataEnergistics$planRevision) data_energistics$openPlanTree();
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void dataEnergistics$preparePlanningMetadata(CallbackInfo ci) {
        if (!this.isServerSide()) {
            return;
        }

        IGrid currentGrid = getGrid();
        this.dataEnergistics$hasTrinityCpu = currentGrid != null && currentGrid.getCraftingService().getCpus().stream()
                .anyMatch(cpu -> cpu instanceof TrinityDataCoreVirtualCpu);

        this.dataEnergistics$planReady = this.result != null;
        this.dataEnergistics$trinityOnly = false;
        this.dataEnergistics$dynamicMaterialWarning = false;
        this.dataEnergistics$hasDiagnostic = false;
        this.dataEnergistics$diagnostic = Component.empty();
        this.dataEnergistics$diagnosticDetail = Component.empty();
        this.dataEnergistics$ae2FallbackEstimate = false;
        this.dataEnergistics$planningNanos = 0L;

        if (this.result instanceof TrinityCraftingPlan plan) {
            this.dataEnergistics$quantityMode = plan.quantityMode().ordinal();
            this.dataEnergistics$trinityOnly = true;
            this.dataEnergistics$dynamicMaterialWarning = plan.stages().stream().anyMatch(TrinityPlanStage::cycleStage);
            this.dataEnergistics$planningNanos = plan.statistics().planningNanos();
            if (!plan.diagnostics().isEmpty()) {
                this.dataEnergistics$hasDiagnostic = true;
                DiagnosticText text = dataEnergistics$formatDiagnostic(plan.diagnostics().getFirst());
                this.dataEnergistics$diagnostic = text.message();
                this.dataEnergistics$diagnosticDetail = text.detail();
            }
        } else if (this.result instanceof TrinityDiagnosedCraftingPlan diagnosed) {
            this.dataEnergistics$hasDiagnostic = true;
            DiagnosticText text = dataEnergistics$formatDiagnostic(diagnosed.diagnostic());
            this.dataEnergistics$diagnostic = text.message();
            this.dataEnergistics$diagnosticDetail = text.detail();
            this.dataEnergistics$ae2FallbackEstimate = diagnosed.ae2FallbackEstimate();
            this.dataEnergistics$planningNanos = diagnosed.calculationNanos();
        }
    }

    @Unique
    private static DiagnosticText dataEnergistics$formatDiagnostic(TrinityPlanningDiagnostic diagnostic) {
        Component message = diagnostic.message();
        var exactShortage = diagnostic.inputShortage();
        if (exactShortage.isPresent()) {
            var shortage = exactShortage.orElseThrow();
            return new DiagnosticText(message, Component.translatable(
                    "gui.data_energistics.trinity_planning.diagnostic.input_shortage_detail",
                    shortage.key().getDisplayName(),
                    shortage.missing(),
                    shortage.available(),
                    shortage.required()).withStyle(dataEnergistics$diagnosticDetailColor(diagnostic)));
        }
        if (diagnostic.partialPlan().isEmpty() ||
                diagnostic.partialPlan().orElseThrow().inputRequirements().isEmpty()) {
            return new DiagnosticText(message, Component.empty());
        }
        TrinityPlanningDiagnostic.PartialPlan partial = diagnostic.partialPlan().orElseThrow();
        var first = partial.inputRequirements().entrySet().iterator().next();
        return new DiagnosticText(message, Component.translatable(
                "gui.data_energistics.trinity_planning.diagnostic.input_shortage_summary",
                partial.inputRequirements().size(),
                first.getKey().getDisplayName(),
                first.getValue().missing(),
                first.getValue().available(),
                first.getValue().required()).withStyle(dataEnergistics$diagnosticDetailColor(diagnostic)));
    }

    @Unique
    private static ChatFormatting dataEnergistics$diagnosticDetailColor(TrinityPlanningDiagnostic diagnostic) {
        return switch (diagnostic.code()) {
            case INTERNAL_ERROR, ARITHMETIC_OVERFLOW -> ChatFormatting.DARK_RED;
            case CALCULATION_CANCELLED, MIP_TIMEOUT, PLANNER_QUEUE_FULL, RUNTIME_DEADLOCK, STALE_GRAPH -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
    }

    @Unique
    private record DiagnosticText(Component message, Component detail) {}

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
            if (diagnosed.ae2FallbackEstimate()) {
                return original.call(grid, actionSource, diagnosed.delegate());
            }
            CraftingPlanSummary planSummary = TrinityCraftingPlanSummaryProjection.createDiagnostic(diagnosed);
            dataEnergistics$syncCycleSummary(TrinityCraftingCycleSummaryProjection.create(
                    diagnosed,
                    grid.getStorageService().getInventory().getAvailableStacks()));
            return planSummary;
        }
        return original.call(grid, actionSource, job);
    }

    @Unique
    private void dataEnergistics$syncCycleSummary(IGrid grid, TrinityCraftingPlan plan) {
        TrinityCraftingCycleSummary summary = TrinityCraftingCycleSummaryProjection.create(
                plan,
                grid.getStorageService().getInventory().getAvailableStacks());
        dataEnergistics$syncCycleSummary(summary);
    }

    @Unique
    private void dataEnergistics$syncCycleSummary(TrinityCraftingCycleSummary summary) {
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
            dataEnergistics$closePlanningProgress();
            this.dataEnergistics$planRevision = Math.incrementExact(this.dataEnergistics$planRevision);
            this.dataEnergistics$planningProgressChannel = new TrinityPlanningProgressChannel();
            this.dataEnergistics$planningProgressSequence = 0L;
            this.dataEnergistics$lastPlanningProgressTick = Long.MIN_VALUE;
            this.dataEnergistics$lastSentPlanningProgress = null;
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
        this.dataEnergistics$clientPlanningProgress = null;
        this.dataEnergistics$clientPlanningProgressRevision = -1L;
        this.dataEnergistics$clientPlanningProgressSequence = -1L;
        ((CraftConfirmMenu) (Object) this).setPlan(null);
        this.dataEnergistics$treeReady = false;
        if (this.dataEnergistics$treeSession != null) this.dataEnergistics$treeSession.closeIfOwnedBy(this);
        this.dataEnergistics$treeSession = null;
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
        IActionSource quantityContext = TrinityCraftingRequestContext.attach(original, data_energistics$quantityMode());
        TrinityPlanningProgressChannel progress = this.dataEnergistics$planningProgressChannel;
        return this.isServerSide() && progress != null ?
                TrinityPlanningProgressContext.attach(quantityContext, progress) :
                quantityContext;
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
            dataEnergistics$closePlanningProgress();
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
    public void data_energistics$receivePlanningProgress(long revision,
                                                         long sequence,
                                                         TrinityPlanningProgressSnapshot snapshot) {
        if (revision != this.dataEnergistics$planRevision ||
                this.dataEnergistics$clientPlanningProgressRevision == revision &&
                        sequence <= this.dataEnergistics$clientPlanningProgressSequence) {
            return;
        }
        this.dataEnergistics$clientPlanningProgressRevision = revision;
        this.dataEnergistics$clientPlanningProgressSequence = sequence;
        this.dataEnergistics$clientPlanningProgress = snapshot;
    }

    @Override
    public @Nullable TrinityPlanningProgressSnapshot data_energistics$planningProgress() {
        return this.dataEnergistics$clientPlanningProgressRevision == this.dataEnergistics$planRevision ?
                this.dataEnergistics$clientPlanningProgress : null;
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void dataEnergistics$syncPlanningProgress(CallbackInfo ci) {
        if (!this.isServerSide()) {
            return;
        }
        TrinityPlanningProgressChannel channel = this.dataEnergistics$planningProgressChannel;
        if (channel == null || channel.closed()) {
            return;
        }
        if (this.result != null && this.job == null && !dataEnergistics$terminalPlanningPhase(channel.latest())) {
            TrinityPlanningProgressPhase phase = this.result instanceof TrinityCraftingPlan ?
                    TrinityPlanningProgressPhase.READY :
                    this.result instanceof TrinityDiagnosedCraftingPlan ?
                            TrinityPlanningProgressPhase.DIAGNOSTIC :
                            TrinityPlanningProgressPhase.DELEGATED_TO_AE2;
            channel.publish(TrinityPlanningProgressSnapshot.withoutUnits(phase, TrinityPlanningProgressMeasure.NONE));
        }
        TrinityPlanningProgressSnapshot snapshot = channel.latest();
        if (snapshot == null || snapshot == this.dataEnergistics$lastSentPlanningProgress) {
            return;
        }
        long currentTick = this.getPlayer().level().getGameTime();
        boolean phaseChanged = this.dataEnergistics$lastSentPlanningProgress == null ||
                this.dataEnergistics$lastSentPlanningProgress.phase() != snapshot.phase();
        boolean terminal = dataEnergistics$terminalPlanningPhase(snapshot);
        if (!phaseChanged && !terminal && currentTick - this.dataEnergistics$lastPlanningProgressTick < 2L) {
            return;
        }
        this.dataEnergistics$planningProgressSequence = Math.incrementExact(this.dataEnergistics$planningProgressSequence);
        PacketDistributor.sendToPlayer((ServerPlayer) this.getPlayer(), new TrinityCraftConfirmPlanningProgressPayload(
                this.containerId,
                this.dataEnergistics$planRevision,
                this.dataEnergistics$planningProgressSequence,
                snapshot));
        this.dataEnergistics$lastSentPlanningProgress = snapshot;
        this.dataEnergistics$lastPlanningProgressTick = currentTick;
    }

    @Unique
    private static boolean dataEnergistics$terminalPlanningPhase(@Nullable TrinityPlanningProgressSnapshot snapshot) {
        return snapshot != null && snapshot.phase().terminal();
    }

    @Unique
    private void dataEnergistics$closePlanningProgress() {
        TrinityPlanningProgressChannel channel = this.dataEnergistics$planningProgressChannel;
        if (channel != null) {
            channel.close();
            this.dataEnergistics$planningProgressChannel = null;
        }
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

    @Override
    public Component data_energistics$diagnosticDetail() {
        return this.dataEnergistics$diagnosticDetail;
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void dataEnergistics$preparePlanTreeSession(CallbackInfo ci) {
        if (!isServerSide() || getPlayer().containerMenu != this) return;
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        IGrid grid = getGrid();
        ICraftingCPU restore = this.dataEnergistics$restoreTreeCpu;
        if (restore != null && grid != null) {
            // Use the cycler's public action instead of reflecting into its private index/list.
            int remaining = grid.getCraftingService().getCpus().size() + 1;
            while (this.selectedCpu != restore && remaining-- > 0) {
                self.cycleSelectedCPU(true);
                if (this.selectedCpu == null) break;
            }
            this.dataEnergistics$restoreTreeCpu = null;
        }
        if (!this.dataEnergistics$hasTrinityCpu || this.result == null || this.job != null || grid == null || self.getPlan() == null || getLocator() == null) {
            this.dataEnergistics$treeReady = false;
            return;
        }
        if (this.dataEnergistics$treeSession == null || this.dataEnergistics$treeSession.result() == null || this.dataEnergistics$treeSession.result().plan() != this.result) {
            if (this.dataEnergistics$treeSession != null) this.dataEnergistics$treeSession.closeIfOwnedBy(this);
            var request = new CraftingPlanTreeRequest(getPlayer().getUUID(),
                    this.whatToCraft == null ? this.result.finalOutput().what() : this.whatToCraft,
                    this.dataEnergistics$requestedAmount > 0 ? this.dataEnergistics$requestedAmount : this.result.finalOutput().amount(),
                    data_energistics$quantityMode(), getLocator(), self.getHost(), this.autoCraftingQueue, this.requestedSlots);
            var outcome = CraftingPlanTreeResult.create(this.result, request, grid, getActionSrc());
            this.dataEnergistics$treeSession = new CraftingPlanTreeSession(request, outcome,
                    this.dataEnergistics$planRevision, this, this.selectedCpu);
        }
        this.dataEnergistics$treeReady = true;
    }

    @Override
    public boolean data_energistics$hasTrinityCpu() {
        return this.dataEnergistics$hasTrinityCpu;
    }

    @Override
    public boolean data_energistics$isTreeReady() {
        return this.dataEnergistics$treeReady;
    }

    @Override
    public void data_energistics$openPlanTree() {
        if (isClientSide()) {
            sendClientAction("dataEnergisticsPlanTree", this.dataEnergistics$planRevision);
            return;
        }
        if (!this.dataEnergistics$treeReady || !this.dataEnergistics$hasTrinityCpu || this.dataEnergistics$treeSession == null || getPlayer().containerMenu != this || !isValidMenu()) return;
        IGrid grid = getGrid();
        if (grid == null || grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu instanceof TrinityDataCoreVirtualCpu)) return;
        this.dataEnergistics$treeSession.selectCpu(this, this.selectedCpu);
        CraftingPlanTreeMenu.open((ServerPlayer) getPlayer(), this.dataEnergistics$treeSession, this);
    }

    @Override
    public void data_energistics$adoptPlanTreeSession(CraftingPlanTreeSession session, Object handoffOwner) {
        if (!isServerSide() || !session.request().playerId().equals(getPlayer().getUUID()) || session.isPlanning()) {
            throw new IllegalArgumentException("Invalid restored plan-tree session");
        }
        CraftingPlanTreeResult outcome = session.result();
        if (outcome == null) throw new IllegalArgumentException("Cannot restore an incomplete plan-tree result");
        session.transfer(handoffOwner, this);
        this.dataEnergistics$treeSession = session;
        this.result = outcome.plan();
        this.job = null;
        this.whatToCraft = session.request().target();
        this.dataEnergistics$requestedAmount = session.request().amount();
        this.amount = (int) Math.min(Integer.MAX_VALUE, this.dataEnergistics$requestedAmount);
        this.autoCraftingQueue = session.request().queue();
        this.requestedSlots = session.request().requestedSlots();
        this.dataEnergistics$planRevision = session.revision();
        this.dataEnergistics$quantityMode = session.request().quantityMode().ordinal();
        this.dataEnergistics$restoreTreeCpu = session.selectedCpu();
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        self.setAutoStart(false);
        self.setPlan(outcome.summary());
        self.broadcastChanges();
        sendPacketToClient(new CraftConfirmPlanPacket(outcome.summary()));
        if (outcome.cycles() != null) dataEnergistics$syncCycleSummary(outcome.cycles());
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void dataEnergistics$releasePlanTreeSession(Player player, CallbackInfo ci) {
        if (this.dataEnergistics$treeSession != null) this.dataEnergistics$treeSession.closeIfOwnedBy(this);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void dataEnergistics$closePlanningProgressBeforeRemoval(Player player, CallbackInfo ci) {
        if (this.isServerSide()) {
            dataEnergistics$closePlanningProgress();
        }
    }

    @Inject(method = "goBack", at = @At("HEAD"))
    private void dataEnergistics$closePlanningProgressBeforeGoingBack(CallbackInfo ci) {
        if (this.isServerSide()) {
            dataEnergistics$closePlanningProgress();
        }
    }
}
