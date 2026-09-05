package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.State;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinityBorrowingTransaction;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution.Work;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.Session;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.Submission;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.SubmissionEntry;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.common.trinity.pattern.RoutedCraftingPatternDetails;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.execution.CraftingCpuHelper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Server-thread physical handoff and directed recovery. The owning CPU ledger is the only persistent truth here. */
final class TrinityReusableDispatch {

    record Result(int attempts, boolean accepted, boolean staleRule) {}

    private record Endpoint(CraftingProviderId id, ICraftingProvider provider, ReusableCraftingProviderAdapter adapter) {}

    private record Located(Session session, Endpoint endpoint, ReusableCraftingSessionView view) {}

    private final TrinityDataCoreCpuLogic owner;
    private final Object2ObjectOpenHashMap<UUID, Endpoint> locations = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<UUID, String> reported = new Object2ObjectOpenHashMap<>();
    private @Nullable UUID ledgerOwner;

    TrinityReusableDispatch(TrinityDataCoreCpuLogic owner) {
        this.owner = owner;
    }

    Result dispatch(TrinityDataCoreExecutingCraftingJob job, Work work, IPatternDetails pattern,
                    CraftingProviderPublicationIndex index, IEnergyService energy, ServerLevel level,
                    CraftingDispatchWindow window, long tick) {
        ReusableCpuSessionLedger ledger = owner.reusableLedger();
        if (ledger.hasUncertainOwnership() || !window.canCaptureProviderCapacity()) {
            return new Result(0, false, false);
        }
        IPatternDetails delegate = pattern instanceof RoutedCraftingPatternDetails routed ? routed.delegate() : pattern;
        var recipeId = delegate instanceof IMolecularAssemblerSupportedPattern nativePattern ?
                DataEnergisticsEntrypointLoader.snapshot().trinityPatternRecipes().resolve(nativePattern).map(value -> value.recipeId()) : Optional.<ResourceLocation>empty();
        TrinityReusableRecipe recipe = new TrinityReusableRecipe(pattern, work.exactBindings(), recipeId);
        var outputs = owner.reusableOutputs(job, pattern, recipe);
        if (outputs == null) {
            return new Result(0, false, false);
        }
        double power = CraftingCpuHelper.calculatePatternPower(recipe.sampleGrid());
        boolean hadTarget = false;
        boolean hadRule = false;
        for (CraftingProviderId id : index.providerIdsFor(pattern)) {
            if (window.isExhausted() || !window.canCaptureProviderCapacity()) {
                break;
            }
            ICraftingProvider provider = index.resolveLiveProvider(id);
            if (provider == null) {
                continue;
            }
            var adapter = CountedCraftingProviderAdapters.reusableAdapter(provider);
            if (adapter == null) {
                continue;
            }
            if (!window.canAttempt(provider, pattern)) continue;
            List<Target> targets;
            try (var capture = window.beginProviderCapacityCapture()) {
                targets = List.copyOf(adapter.reusableTargets(pattern, owner.cpu().actionSource(), level));
            } catch (RuntimeException failure) {
                report(work.patternIdentity().definitionEncoding(), failure);
                continue;
            }
            try (var submission = window.beginCountedSubmission(provider, pattern)) {
                for (Target target : targets) {
                    hadTarget = true;
                    if (!recipe.matches(target, owner.cpu().actionSource(), level, DataEnergisticsEntrypointLoader.snapshot().reusableInputs())) {
                        continue;
                    }
                    hadRule = true;
                    Session session = matchingSession(ledger, job, work, target);
                    if (session == null && ledger.sessions().stream().anyMatch(existing -> !existing.settled() && existing.target().equals(target))) {
                        continue;
                    }
                    ReusableCraftingSessionView view = session == null ? null : adapter.reusableSession(session.id()).orElse(null);
                    if (session != null && (view == null || view.state() != State.OPEN)) {
                        continue;
                    }
                    Int2LongOpenHashMap free = freeTools(recipe, session, view);
                    KeyCounter inventory = owner.reusableAvailability(job, work);
                    long limit = owner.reusableOfferLimit(job, work, recipe, outputs, power, energy, free);
                    TrinityReusableRecipe.Offer offer = recipe.offer(limit, inventory, tool -> free.get(tool.slot()));
                    if (offer.count() == 0L) {
                        continue;
                    }
                    UUID sessionId = session == null ? UUID.randomUUID() : session.id();
                    long sequence = session == null ? 0L : session.nextSequence();
                    var request = new ReusableCraftingRequest(sessionId, job.link.getCraftingID(), ledger.ownerIdentity(), sequence,
                            target, pattern, recipe.inputs(), offer.addedTools(), offer.count(), recipeId, owner.cpu().actionSource(), level);
                    ReusableCraftingAdmission prepared = adapter.prepareReusable(request);
                    if (prepared == null) {
                        continue;
                    }
                    if (prepared.replay() || prepared.count() <= 0L || prepared.count() > offer.count()) {
                        throw new IllegalStateException("Unexpected reusable admission count or receipt for a new sequence");
                    }
                    List<SlotStack> physical = List.copyOf(prepared.physicalInputs());
                    validatePhysical(recipe, physical, prepared.count(), offer.addedTools(), free);
                    CraftingDispatchTarget route = new CraftingDispatchTarget(target.route().stableIdentity());
                    if (!window.canAttemptCounted(provider, pattern, route)) {
                        continue;
                    }
                    if (submission.tryAcquire(route) != CraftingDispatchWindow.Acquisition.ACQUIRED) {
                        continue;
                    }
                    if (!owner.reusableWorkCurrent(job, work) || index.resolveLiveProvider(id) != provider) {
                        window.recordResult(provider, pattern, route, CraftingDispatchStatus.STALE);
                        return new Result(1, false, false);
                    }
                    boolean accepted = commit(job, work, recipe, outputs, request, prepared, physical, power, energy, tick);
                    window.recordResult(provider, pattern, route, accepted ? CraftingDispatchStatus.ACCEPTED : CraftingDispatchStatus.REJECTED);
                    if (accepted) {
                        window.recordCommittedLogicalCrafts(prepared.count());
                        locations.put(sessionId, new Endpoint(id, provider, adapter));
                    }
                    return new Result(1, accepted, false);
                }
            } catch (RuntimeException failure) {
                report(work.patternIdentity().definitionEncoding(), failure);
            }
        }
        return new Result(0, false, hadTarget && !hadRule);
    }

    private boolean commit(TrinityDataCoreExecutingCraftingJob job, Work work, TrinityReusableRecipe recipe,
                           ReusableCpuSessionLedger.OutputContract outputs, ReusableCraftingRequest request,
                           ReusableCraftingAdmission admission, List<SlotStack> physical, double unitPower,
                           IEnergyService energy, long tick) {
        ReusableCpuSessionLedger ledger = owner.reusableLedger();
        UUID id = request.sessionId();
        boolean opened = ledger.session(id) == null;
        owner.beginReusableMutation();
        try {
            Optional<TrinityBorrowingTransaction> borrowing = owner.borrowReusableInputs(physical);
            if (borrowing.isEmpty()) {
                return false;
            }
            TrinityBorrowingTransaction borrowed = borrowing.orElseThrow();
            TrinityDataCoreCpuLogic.EnergyCharge charge = TrinityDataCoreCpuLogic.chargeEnergy(energy, unitPower * admission.count());
            try {
                if (charge == null || !owner.reusableWorkCurrent(job, work)) {
                    return false;
                }
                if (opened) {
                    ledger.open(id, job.link.getCraftingID(), request.target(), request.pattern().getDefinition(), work.patternIdentity(), work.exactBindings());
                }
                KeyCounter[] delivery = owner.takeReusableInputs(physical, recipe.inputs().size());
                if (delivery == null) {
                    if (opened) ledger.discardUnopened(id);
                    return false;
                }
                long sequence = ledger.prepare(id, new Submission(work, admission.count(), request.requestedCount(), unitPower * admission.count(),
                        outputs, physical, false, false, false, 0L));
                if (sequence != request.sequence()) {
                    throw new IllegalStateException("Reusable CPU append sequence changed during physical extraction");
                }
                ledger.registerWaiting(id, sequence, value -> owner.registerReusableWaiting(job, value));
                boolean accepted;
                RuntimeException thrown = null;
                try {
                    accepted = admission.commit(delivery);
                } catch (RuntimeException failure) {
                    accepted = admission.hasTransferredInputOwnership();
                    thrown = failure;
                }
                if (admission.hasTransferredInputOwnership()) {
                    ledger.transferred(id, sequence);
                    charge.commit();
                    borrowed.commitConsumed(totals(physical), 1L);
                    ledger.account(id, sequence, value -> owner.accountReusable(job, work, value, physical));
                    if (thrown != null) report(id.toString(), thrown);
                    owner.cpu().markDirty();
                    return true;
                }
                if (accepted || !sameDelivery(delivery, physical)) {
                    ledger.markUncertain(id);
                    charge.commit();
                    borrowed.commitConsumed(totals(physical), 1L);
                    throw new IllegalStateException("Provider changed reusable assets without confirming ownership");
                }
                Submission value = ledger.session(id).submission(sequence);
                owner.rollbackReusableWaiting(job, value);
                owner.returnReusableInputs(ledger.reject(id, sequence));
                if (opened) ledger.discardUnopened(id);
                if (thrown != null) report(id.toString(), thrown);
                return false;
            } finally {
                if (charge != null) charge.rollback();
                borrowed.releaseUncommitted();
            }
        } finally {
            owner.endReusableMutation();
        }
    }

    /** Reconciles accepted history before processing any cancellation that can invalidate old work leases. */
    void synchronize(CraftingProviderPublicationIndex index, long tick) {
        ReusableCpuSessionLedger ledger = owner.reusableLedger();
        if (!ledger.owner().equals(ledgerOwner)) {
            locations.clear();
            reported.clear();
            ledgerOwner = ledger.owner();
        }
        List<Located> found = new ObjectArrayList<>();
        for (Session session : ledger.sessions()) {
            if (session.settled()) continue;
            try {
                Endpoint endpoint = locate(session, index);
                if (endpoint == null) continue;
                var view = endpoint.adapter().reusableSession(session.id()).orElseThrow();
                if (!view.jobId().equals(session.jobId()) || !view.cpuOwner().equals(ledger.ownerIdentity()) ||
                        !view.targetIdentity().equals(session.target().persistentIdentity())) {
                    throw new IllegalStateException("Reusable endpoint returned a different custody identity");
                }
                long known = session.submissions().stream().mapToLong(value -> value.submission().count()).reduce(0L, Math::addExact);
                if (known != view.accepted()) throw new IllegalStateException("CPU and reusable endpoint disagree about accepted work");
                for (SubmissionEntry entry : session.pendingSubmissions()) {
                    var receipt = endpoint.adapter().reusableReceipt(session.id(), entry.sequence()).orElseThrow();
                    if (receipt.accepted() != entry.submission().count()) throw new IllegalStateException("Reusable receipt count differs from CPU custody");
                    if (!entry.submission().transferred()) ledger.transferred(session.id(), entry.sequence());
                    if (!entry.submission().accounted()) owner.recoverReusableAccounting(session, entry);
                }
                ledger.confirmOwnership(session.id());
                found.add(new Located(session, endpoint, view));
            } catch (RuntimeException failure) {
                ledger.markUncertain(session.id());
                report(session.id().toString(), failure);
            }
        }
        for (Located located : found) {
            Session session = located.session();
            var adapter = located.endpoint().adapter();
            owner.beginReusableMutation();
            try {
                for (SubmissionEntry entry : session.pendingSubmissions()) {
                    var receipt = adapter.reusableReceipt(session.id(), entry.sequence()).orElseThrow();
                    ledger.observeCompleted(session.id(), entry.sequence(), receipt.completed(),
                            amount -> owner.completeReusableOutputs(session.jobId(), entry.submission(), amount));
                }
                for (SlotStack tool : located.view().heldTools()) owner.wakeReusableTool(tool.stack().what());
                if (session.closing() || !owner.ownsReusableJob(session.jobId()) || located.view().state() == State.FAULTED) {
                    ledger.close(session.id());
                    adapter.closeReusableSession(session.id());
                }
                adapter.settleReusableSession(session.id(), settlement -> owner.receiveReusableSettlement(session, settlement));
            } catch (RuntimeException failure) {
                ledger.markUncertain(session.id());
                report(session.id().toString(), failure);
            } finally {
                owner.endReusableMutation();
            }
        }
    }

    private @Nullable Endpoint locate(Session session, CraftingProviderPublicationIndex index) {
        Endpoint cached = locations.get(session.id());
        if (cached != null && index.resolveLiveProvider(cached.id()) == cached.provider() &&
                CountedCraftingProviderAdapters.reusableAdapter(cached.provider()) == cached.adapter())
            return cached;
        for (CraftingProviderId id : index.providerIds()) {
            ICraftingProvider provider = index.resolveLiveProvider(id);
            if (provider == null) continue;
            var adapter = CountedCraftingProviderAdapters.reusableAdapter(provider);
            if (adapter != null && adapter.reusableSession(session.id()).isPresent()) {
                Endpoint found = new Endpoint(id, provider, adapter);
                locations.put(session.id(), found);
                return found;
            }
        }
        return null;
    }

    private static @Nullable Session matchingSession(ReusableCpuSessionLedger ledger, TrinityDataCoreExecutingCraftingJob job, Work work, Target target) {
        for (Session session : ledger.sessions()) {
            if (!session.settled() && !session.closing() && session.jobId().equals(job.link.getCraftingID()) &&
                    session.target().equals(target) && session.publication().equals(work.patternIdentity()) && compatible(session, work))
                return session;
        }
        return null;
    }

    private static boolean compatible(Session session, Work work) {
        if (session.bindings().size() != work.exactBindings().size()) return false;
        for (int slot = 0; slot < session.bindings().size(); slot++) {
            var old = session.bindings().get(slot);
            var next = work.exactBindings().get(slot);
            if (old.reusableRule() == null ? !old.equals(next) : next.reusableRule() == null ||
                    !old.reusableRule().id().equals(next.reusableRule().id()) || old.reusableRule().revision() != next.reusableRule().revision() ||
                    old.reusableRule().kind() != next.reusableRule().kind() ||
                    old.reusableRule().damagePerUse() != next.reusableRule().damagePerUse() ||
                    old.reusableRule().breakAtDamage() != next.reusableRule().breakAtDamage() ||
                    !old.reusableRule().transitions().equals(next.reusableRule().transitions()) ||
                    !old.reusableRule().exhaustionByproducts().equals(next.reusableRule().exhaustionByproducts()) ||
                    !old.consumedAmount().equals(next.consumedAmount()))
                return false;
        }
        return true;
    }

    private static Int2LongOpenHashMap freeTools(TrinityReusableRecipe recipe, @Nullable Session session, @Nullable ReusableCraftingSessionView view) {
        Int2LongOpenHashMap free = new Int2LongOpenHashMap();
        if (view == null) return free;
        for (var required : recipe.tools()) {
            long amount = 0L;
            for (SlotStack held : view.heldTools()) {
                if (held.slot() == required.slot() && held.stack().what().equals(required.state())) amount = Math.addExact(amount, held.stack().amount());
            }
            if (!required.unchanged()) {
                for (SubmissionEntry entry : session.pendingSubmissions()) {
                    var binding = entry.submission().work().exactBindings().get(required.slot());
                    if (binding.template().what().equals(required.state())) {
                        long reserved = Math.multiplyExact(entry.submission().count() - entry.submission().completed(), required.held());
                        amount -= Math.min(amount, reserved);
                    }
                }
            }
            free.put(required.slot(), Math.max(0L, amount));
        }
        return free;
    }

    private static void validatePhysical(TrinityReusableRecipe recipe, List<SlotStack> physical, long count,
                                         List<SlotStack> offered, Int2LongOpenHashMap resident) {
        for (int slot = 0; slot < recipe.inputs().size(); slot++) {
            Object2LongLinkedOpenHashMap<AEKey> amounts = new Object2LongLinkedOpenHashMap<>();
            for (SlotStack item : physical) {
                if (item.slot() >= recipe.inputs().size()) throw new IllegalStateException("Reusable delivery has an unknown slot");
                if (item.slot() == slot) amounts.mergeLong(item.stack().what(), item.stack().amount(), Math::addExact);
            }
            var input = recipe.inputs().get(slot);
            for (GenericStack material : input.consumedPerOperation()) {
                long left = Math.subtractExact(amounts.getLong(material.what()), Math.multiplyExact(material.amount(), count));
                if (left < 0L) throw new IllegalStateException("Reusable delivery omitted ordinary material");
                if (left == 0L) amounts.removeLong(material.what());
                else amounts.put(material.what(), left);
            }
            if (input.tool().isPresent()) {
                var tool = input.tool().orElseThrow();
                var key = tool.operationState().orElseThrow();
                long supplied = amounts.removeLong(key);
                long offeredCount = 0L;
                for (SlotStack item : offered) if (item.slot() == slot && item.stack().what().equals(key)) offeredCount += item.stack().amount();
                long needed = key.equals(tool.rule().advance(key, 1).successor()) ? tool.heldAmount() : Math.multiplyExact(tool.heldAmount(), count);
                if (supplied > offeredCount || supplied < needed - Math.min(needed, resident.get(slot))) {
                    throw new IllegalStateException("Reusable delivery contradicts supplied or resident tool quantities");
                }
            }
            if (!amounts.isEmpty()) throw new IllegalStateException("Reusable delivery contains undeclared physical assets");
        }
    }

    static List<GenericStack> totals(List<SlotStack> physical) {
        Object2LongLinkedOpenHashMap<AEKey> counts = new Object2LongLinkedOpenHashMap<>();
        physical.forEach(value -> counts.mergeLong(value.stack().what(), value.stack().amount(), Math::addExact));
        return counts.object2LongEntrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getLongValue())).toList();
    }

    private static boolean sameDelivery(KeyCounter[] counters, List<SlotStack> physical) {
        KeyCounter remaining = new KeyCounter();
        for (KeyCounter counter : counters) remaining.addAll(counter);
        List<GenericStack> expected = totals(physical);
        return remaining.size() == expected.size() && expected.stream().allMatch(stack -> remaining.get(stack.what()) == stack.amount());
    }

    private void report(String identity, RuntimeException failure) {
        UUID key = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        if (!failure.toString().equals(reported.put(key, failure.toString()))) {
            Data_Energistics.LOGGER.error("Trinity CPU {} retained reusable custody {} after a protocol or execution failure", owner.cpu().number(), identity, failure);
        }
    }
}
