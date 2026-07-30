package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCatalog;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCore;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost.PatternCoreBinding;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost.PatternCoreReleaseRequest;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost.PatternCoreReleaseResult;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternSlot;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.UUID;

/** Verifies host-authoritative release of one transient Trinity pattern-core binding. */
@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityPatternCoreReleaseGameTest {

    private TrinityPatternCoreReleaseGameTest() {}

    @TestHolder("trinity_pattern_core_release_requires_host_confirmation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void releaseRequiresHostConfirmation(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState());
        TrinityPatternCoreBlockEntity core = helper.getBlockEntity(pos);

        ConfirmingPatternHost oldHost = new ConfirmingPatternHost(core);
        PatternCoreBinding oldBinding = oldHost.bindingFor(core);
        helper.assertTrue(
                core.bindPatternHost(oldHost, oldBinding),
                "The old host should bind the placed pattern core");

        oldHost.failNextRelease();
        CompoundTag replacementState = new CompoundTag();
        core.writeToTag(replacementState, helper.getLevel().registryAccess());
        UUID replacementCoreId = UUID.randomUUID();
        replacementState.putUUID("core_id", replacementCoreId);
        core.loadTag(replacementState, helper.getLevel().registryAccess());
        helper.assertValueEqual(
                core.coreId(),
                replacementCoreId,
                "The replacement NBT state must replace the core identity before release retry");
        helper.assertTrue(
                oldHost.releaseRequestCount() == 1 && oldHost.lastReleaseRequest().core() == core &&
                        oldHost.lastReleaseRequest().binding().equals(oldBinding),
                "Identity replacement must release the captured old binding after the host callback fails");

        ConfirmingPatternHost newHost = new ConfirmingPatternHost(core);
        PatternCoreBinding newBinding = newHost.bindingFor(core);
        helper.assertFalse(
                core.bindPatternHost(newHost, newBinding),
                "A retry-required release must keep the old host binding authoritative");

        core.serverTick();
        helper.assertTrue(
                oldHost.releaseRequestCount() == 2 && oldHost.lastReleaseRequest().binding().equals(oldBinding),
                "Release retry must retain the old binding token after the core identity changed");
        helper.assertTrue(
                core.bindPatternHost(newHost, newBinding),
                "The core should accept a new host only after the old host confirms release");

        core.unbindPatternHost(oldHost, oldBinding);
        ConfirmingPatternHost unrelatedHost = new ConfirmingPatternHost(core);
        helper.assertFalse(
                core.canBindPatternHost(unrelatedHost),
                "A stale old-host callback must not clear the new binding");
        helper.assertTrue(
                newHost.isPatternCoreMounted(core, newBinding),
                "The new host must retain its exact binding after the old callback arrives");
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_unload_withdraws_catalog_once")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void unloadingCoreWithdrawsCatalogOnce(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    TrinityDataCoreBlockEntity host = fixture.host();
                    TrinityPatternCatalog.LayoutSnapshot beforeRelease = host.getPatternCatalog().layoutSnapshot();
                    TrinityPatternCoreBlockEntity core = requirePatternCore(beforeRelease);

                    helper.assertTrue(beforeRelease.active(), "The fixture should publish an active pattern catalog");
                    helper.assertTrue(
                            host.isPatternProviderAvailable(),
                            "The active catalog should be available to its AE lease");
                    core.onChunkUnloaded();

                    TrinityPatternCatalog.LayoutSnapshot withdrawn = host.getPatternCatalog().layoutSnapshot();
                    helper.assertFalse(
                            withdrawn.active(),
                            "Core unload must immediately lock and withdraw the pattern catalog");
                    helper.assertFalse(host.isPatternProviderAvailable(), "A withdrawn catalog must not remain published to AE2");
                    helper.assertFalse(
                            host.getPatternCatalog().isCoreMounted(core),
                            "The withdrawn catalog must no longer own the unloaded core");
                    long revisionAfterUnload = withdrawn.revision();

                    core.setRemoved();
                    helper.assertValueEqual(
                            host.getPatternCatalog().layoutSnapshot().revision(),
                            revisionAfterUnload,
                            "Repeated removal after unload must not mutate the catalog revision again");
                })
                .thenSucceed();
    }

    private static TrinityPatternCoreBlockEntity requirePatternCore(TrinityPatternCatalog.LayoutSnapshot layout) {
        TrinityPatternCore core = layout.ranges().getFirst().mount().core();
        if (core instanceof TrinityPatternCoreBlockEntity blockEntity) {
            return blockEntity;
        }
        throw new GameTestAssertException("Expected the fixture to mount a TrinityPatternCoreBlockEntity");
    }

    /** Minimal controlled host that exposes confirmation and stale-callback boundaries without a compatibility shim. */
    private static final class ConfirmingPatternHost implements TrinityPatternCoreHost {

        private final UUID hostId = UUID.randomUUID();
        private final TrinityPatternCore mountedCore;
        private PatternCoreBinding binding;
        private boolean available = true;
        private boolean failNextRelease;
        private int releaseRequestCount;
        private PatternCoreReleaseRequest lastReleaseRequest;

        private ConfirmingPatternHost(TrinityPatternCore mountedCore) {
            this.mountedCore = mountedCore;
        }

        private PatternCoreBinding bindingFor(TrinityPatternCoreBlockEntity core) {
            this.binding = new PatternCoreBinding(
                    this.hostId,
                    1L,
                    core.coreId(),
                    core.getBlockPos(),
                    core.patternCapacity());
            return this.binding;
        }

        private void failNextRelease() {
            this.failNextRelease = true;
        }

        private int releaseRequestCount() {
            return this.releaseRequestCount;
        }

        private PatternCoreReleaseRequest lastReleaseRequest() {
            return this.lastReleaseRequest;
        }

        @Override
        public boolean isPatternCoreMounted(TrinityPatternCore core, PatternCoreBinding binding) {
            return this.available && this.mountedCore == core && this.binding.equals(binding);
        }

        @Override
        public boolean tryRefundPatternCore(TrinityPatternCore core, PatternCoreBinding binding, Player player) {
            return false;
        }

        @Override
        public void onPatternCoreChanged(TrinityPatternCore core,
                                         PatternCoreBinding binding,
                                         TrinityPatternSlot.Change change) {}

        @Override
        public PatternCoreReleaseResult onPatternCoreUnavailable(PatternCoreReleaseRequest request) {
            this.releaseRequestCount++;
            this.lastReleaseRequest = request;
            if (this.failNextRelease) {
                this.failNextRelease = false;
                throw new IllegalStateException("Controlled host release failure");
            }
            if (!isPatternCoreMounted(request.core(), request.binding())) {
                return this.available ? PatternCoreReleaseResult.STALE_REQUEST : PatternCoreReleaseResult.ALREADY_REVOKED;
            }
            this.available = false;
            return PatternCoreReleaseResult.REVOKED;
        }
    }
}
