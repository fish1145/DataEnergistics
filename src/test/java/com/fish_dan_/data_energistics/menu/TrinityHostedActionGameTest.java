package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildDraft;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUi;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiRoot;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiOperation;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiRequest;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiResponse;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityDataCoreHostUiKeys;
import com.fish_dan_.data_energistics.network.TrinityHostedActionPayloadHandler;
import com.fish_dan_.data_energistics.network.TrinityHostedActionResponsePayload;
import com.fish_dan_.data_energistics.network.TrinityHostedAutoBuildPayload;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityHostedActionGameTest {

    private TrinityHostedActionGameTest() {}

    @TestHolder("trinity_hosted_action_client_tracks_exact_pending_ack")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void clientTracksExactPendingAck(GameTestHelper helper) {
        Fixture fixture = fixture(helper, new BlockPos(1, 1, 1), 80);
        TrinityAutoBuildSubmission submission = validSubmission();
        open(fixture.menu(), TrinityDataCoreHostUiKeys.AUTO_BUILD, 1L);

        assertTrue(fixture.menu().sendHostedAutoBuild(1L, submission));
        assertTrue(fixture.menu().isHostedActionPending(TrinityDataCoreHostUiKeys.AUTO_BUILD, 1L));
        assertEquals(1, fixture.outbound().size());
        TrinityHostedAutoBuildPayload firstPayload = requireAutoBuild(fixture.outbound().getFirst());
        assertEquals(1L, firstPayload.actionSequence());
        assertEquals(submission, firstPayload.submission());

        TrinityHostedActionResult wrongSequence = result(
                TrinityDataCoreHostUiKeys.AUTO_BUILD,
                1L,
                2L,
                TrinityHostedActionStatus.COMPLETED);
        assertFalse(fixture.menu().handleHostedActionResponse(wrongSequence));
        assertTrue(fixture.menu().isHostedActionPending(TrinityDataCoreHostUiKeys.AUTO_BUILD, 1L));

        TrinityHostedActionResult exact = result(
                TrinityDataCoreHostUiKeys.AUTO_BUILD,
                1L,
                1L,
                TrinityHostedActionStatus.REJECTED);
        assertTrue(fixture.menu().handleHostedActionResponse(exact));
        assertFalse(fixture.menu().isHostedActionPending(TrinityDataCoreHostUiKeys.AUTO_BUILD, 1L));
        assertEquals(exact, fixture.menu().consumeHostedActionResult(TrinityDataCoreHostUiKeys.AUTO_BUILD, 1L));
        assertNull(fixture.menu().consumeHostedActionResult(TrinityDataCoreHostUiKeys.AUTO_BUILD, 1L));
        assertFalse(fixture.menu().handleHostedActionResponse(exact));

        assertTrue(fixture.menu().sendHostedAutoBuild(1L, submission));
        assertEquals(2L, requireAutoBuild(fixture.outbound().getLast()).actionSequence());
        fixture.menu().handleHostedActionResponse(result(
                TrinityDataCoreHostUiKeys.AUTO_BUILD,
                1L,
                2L,
                TrinityHostedActionStatus.COMPLETED));
        close(fixture.menu(), TrinityDataCoreHostUiKeys.AUTO_BUILD, 2L);
        open(fixture.menu(), TrinityDataCoreHostUiKeys.AUTO_BUILD, 3L);
        assertFalse(fixture.menu().sendHostedAutoBuild(1L, submission));
        assertTrue(fixture.menu().sendHostedAutoBuild(3L, submission));
        assertEquals(1L, requireAutoBuild(fixture.outbound().getLast()).actionSequence());
        fixture.close();
        helper.succeed();
    }

    @TestHolder("trinity_hosted_auto_build_rejects_forged_projection_before_business")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void autoBuildRejectsForgedProjectionBeforeBusiness(GameTestHelper helper) {
        Fixture fixture = fixture(helper, new BlockPos(2, 1, 2), 82);
        open(fixture.menu(), TrinityDataCoreHostUiKeys.AUTO_BUILD, 1L);
        List<TrinityHostedActionResponsePayload> responses = new ArrayList<>();
        MultiblockPreviewSpec spec = ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                .require(ModVerticalMultiBlocks.trinityDataCoreId());
        ProjectionFingerprint validFingerprint = TrinityAutoBuildDraft.initial(spec)
                .submission()
                .projectionFingerprint();
        long sequence = 1L;

        sequence = rejectAutoBuild(fixture, responses, sequence, withRevision(
                validFingerprint,
                validFingerprint.definitionRevision() + 1L));
        sequence = rejectAutoBuild(fixture, responses, sequence, withStructure(
                validFingerprint,
                new JsonMultiBlockStructureKey(validFingerprint.controllerId(), "unknown")));
        sequence = rejectAutoBuild(fixture, responses, sequence, withVariant(validFingerprint, 1));
        sequence = rejectAutoBuild(fixture, responses, sequence, withTiers(
                validFingerprint,
                Map.of("wrong", 1)));
        List<Integer> invalidRepeats = new ArrayList<>(validFingerprint.repeatCounts());
        invalidRepeats.set(0, 2);
        sequence = rejectAutoBuild(fixture, responses, sequence, withRepeats(validFingerprint, invalidRepeats));
        sequence = rejectAutoBuild(fixture, responses, sequence, withCandidates(
                validFingerprint,
                Map.of(new PreviewPredicateKey(0, 0, 0), 0)));
        assertEquals(0, fixture.executor().autoBuildCount);

        TrinityHostedAutoBuildPayload valid = new TrinityHostedAutoBuildPayload(
                82,
                1L,
                sequence,
                new TrinityAutoBuildSubmission(validFingerprint, true));
        TrinityHostedActionPayloadHandler.handleAutoBuild(valid, fixture.player(), responses::add);
        assertEquals(1, fixture.executor().autoBuildCount);
        assertTrue(fixture.executor().lastAutoBuild.options().buildRequested());
        assertStatus(responses.getLast(), TrinityHostedActionStatus.COMPLETED);
        TrinityHostedActionPayloadHandler.handleAutoBuild(valid, fixture.player(), responses::add);
        assertEquals(1, fixture.executor().autoBuildCount);
        assertStatus(responses.getLast(), TrinityHostedActionStatus.REJECTED);

        TrinityHostedAutoBuildPayload noBuild = new TrinityHostedAutoBuildPayload(
                82,
                1L,
                sequence + 1L,
                new TrinityAutoBuildSubmission(validFingerprint, false));
        TrinityHostedActionPayloadHandler.handleAutoBuild(noBuild, fixture.player(), responses::add);
        assertEquals(2, fixture.executor().autoBuildCount);
        assertFalse(fixture.executor().lastAutoBuild.options().buildRequested());
        assertStatus(responses.getLast(), TrinityHostedActionStatus.COMPLETED);
        fixture.close();
        helper.succeed();
    }

    private static long rejectAutoBuild(Fixture fixture,
                                        List<TrinityHostedActionResponsePayload> responses,
                                        long sequence,
                                        ProjectionFingerprint fingerprint) {
        TrinityHostedActionPayloadHandler.handleAutoBuild(
                new TrinityHostedAutoBuildPayload(
                        fixture.menu().containerId,
                        1L,
                        sequence,
                        new TrinityAutoBuildSubmission(fingerprint, true)),
                fixture.player(),
                responses::add);
        assertEquals(0, fixture.executor().autoBuildCount);
        assertStatus(responses.getLast(), TrinityHostedActionStatus.REJECTED);
        return sequence + 1L;
    }

    private static Fixture fixture(GameTestHelper helper, BlockPos position, int containerId) {
        helper.setBlock(position, ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        TrinityDataCoreBlockEntity host = helper.getBlockEntity(position);
        UUID playerId = UUID.randomUUID();
        TestServerPlayer serverPlayer = new TestServerPlayer(
                helper,
                new GameProfile(playerId, "hosted-" + playerId.toString().substring(0, 8)));
        BlockPos hostPosition = host.getBlockPos();
        serverPlayer.setPos(
                hostPosition.getX() + 0.5D,
                hostPosition.getY() + 0.5D,
                hostPosition.getZ() + 0.5D);
        CountingExecutor executor = new CountingExecutor();
        List<CustomPacketPayload> outbound = new ArrayList<>();
        TrinityDataCoreMenu menu = new TrinityDataCoreMenu(
                containerId,
                serverPlayer.getInventory(),
                host,
                outbound::add,
                executor,
                TrinityHostedActionGameTest::registerProviders);
        serverPlayer.containerMenu = menu;
        assertTrue(menu.isHostUiAvailable(serverPlayer));
        return new Fixture(serverPlayer, menu, executor, outbound);
    }

    private static void registerProviders(HostUiExtension hostUi) {
        if (!hostUi.registeredKeys().isEmpty()) {
            assertEquals(TrinityDataCoreHostUiKeys.registrationOrder(), hostUi.registeredKeys());
            return;
        }
        for (HostUiKey key : TrinityDataCoreHostUiKeys.registrationOrder()) {
            hostUi.register(new TestProvider(key));
        }
    }

    private static void open(TrinityDataCoreMenu menu, HostUiKey key, long lifecycleSequence) {
        HostUiResponse response = menu.getHostUiCoordinator().handleRequest(
                new HostUiRequest(HostUiOperation.OPEN, key, lifecycleSequence),
                true);
        assertTrue(response.accepted());
        assertTrue(menu.getHostUiExtension().isOpen(key, lifecycleSequence));
    }

    private static void close(TrinityDataCoreMenu menu, HostUiKey key, long lifecycleSequence) {
        HostUiResponse response = menu.getHostUiCoordinator().handleRequest(
                new HostUiRequest(HostUiOperation.CLOSE, key, lifecycleSequence),
                true);
        assertTrue(response.accepted());
        assertFalse(menu.getHostUiExtension().isOpen(key));
    }

    private static TrinityHostedAutoBuildPayload requireAutoBuild(CustomPacketPayload payload) {
        if (payload instanceof TrinityHostedAutoBuildPayload autoBuild) {
            return autoBuild;
        }
        throw new GameTestAssertException("Expected Trinity hosted auto-build payload, got " + payload);
    }

    private static TrinityAutoBuildSubmission validSubmission() {
        MultiblockPreviewSpec spec = ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                .require(ModVerticalMultiBlocks.trinityDataCoreId());
        return TrinityAutoBuildDraft.initial(spec).submission();
    }

    private static void assertStatus(TrinityHostedActionResponsePayload response,
                                     TrinityHostedActionStatus status) {
        assertEquals(status, response.result().status());
    }

    private static TrinityHostedActionResult result(HostUiKey key,
                                                    long generation,
                                                    long sequence,
                                                    TrinityHostedActionStatus status) {
        return new TrinityHostedActionResult(key, generation, sequence, status);
    }

    private static ProjectionFingerprint withRevision(ProjectionFingerprint source, long revision) {
        return fingerprint(
                source,
                revision,
                source.structureKey(),
                source.variantIndex(),
                source.repeatCounts(),
                source.tierSelections(),
                source.candidateSelections());
    }

    private static ProjectionFingerprint withStructure(ProjectionFingerprint source,
                                                       JsonMultiBlockStructureKey structureKey) {
        return fingerprint(
                source,
                source.definitionRevision(),
                structureKey,
                source.variantIndex(),
                source.repeatCounts(),
                source.tierSelections(),
                source.candidateSelections());
    }

    private static ProjectionFingerprint withVariant(ProjectionFingerprint source, int variant) {
        return fingerprint(
                source,
                source.definitionRevision(),
                source.structureKey(),
                variant,
                source.repeatCounts(),
                source.tierSelections(),
                source.candidateSelections());
    }

    private static ProjectionFingerprint withRepeats(ProjectionFingerprint source, List<Integer> repeats) {
        return fingerprint(
                source,
                source.definitionRevision(),
                source.structureKey(),
                source.variantIndex(),
                repeats,
                source.tierSelections(),
                source.candidateSelections());
    }

    private static ProjectionFingerprint withTiers(ProjectionFingerprint source, Map<String, Integer> tiers) {
        return fingerprint(
                source,
                source.definitionRevision(),
                source.structureKey(),
                source.variantIndex(),
                source.repeatCounts(),
                tiers,
                source.candidateSelections());
    }

    private static ProjectionFingerprint withCandidates(ProjectionFingerprint source,
                                                        Map<PreviewPredicateKey, Integer> candidates) {
        return fingerprint(
                source,
                source.definitionRevision(),
                source.structureKey(),
                source.variantIndex(),
                source.repeatCounts(),
                source.tierSelections(),
                candidates);
    }

    private static ProjectionFingerprint fingerprint(ProjectionFingerprint source,
                                                     long revision,
                                                     JsonMultiBlockStructureKey structureKey,
                                                     int variant,
                                                     List<Integer> repeats,
                                                     Map<String, Integer> tiers,
                                                     Map<PreviewPredicateKey, Integer> candidates) {
        return new ProjectionFingerprint(
                source.controllerId(),
                revision,
                structureKey,
                variant,
                repeats,
                new LinkedHashMap<>(tiers),
                new LinkedHashMap<>(candidates));
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new GameTestAssertException("Expected condition to be true");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new GameTestAssertException("Expected condition to be false");
        }
    }

    private static void assertNull(Object value) {
        if (value != null) {
            throw new GameTestAssertException("Expected null, got " + value);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private record Fixture(TestServerPlayer player,
                           TrinityDataCoreMenu menu,
                           CountingExecutor executor,
                           List<CustomPacketPayload> outbound) {

        private void close() {
            if (this.player.containerMenu == this.menu) {
                this.player.doCloseContainer();
            }
            this.player.closeTestConnection();
        }
    }

    private static final class TestServerPlayer extends ServerPlayer {

        private final EmbeddedChannel testChannel;

        private TestServerPlayer(GameTestHelper helper, GameProfile profile) {
            super(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    profile,
                    ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            this.testChannel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            this.connection = new ServerGamePacketListenerImpl(
                    helper.getLevel().getServer(),
                    connection,
                    this,
                    CommonListenerCookie.createInitial(profile, false));
        }

        private void closeTestConnection() {
            this.testChannel.finishAndReleaseAll();
        }
    }

    private record TestProvider(HostUiKey key) implements HostSubUiProvider {

        @Override
        public HostSubUi create(HostSubUiContext context) {
            HostSubUiRoot root = context.createRoot();
            UIElement dragHandle = new UIElement();
            root.addChild(dragHandle);
            return new HostSubUi(root, dragHandle);
        }
    }

    private static final class CountingExecutor implements TrinityDataCoreMenu.TrinityHostedActionExecutor {

        private int autoBuildCount;
        private TrinityAutoBuildRequest lastAutoBuild;

        @Override
        public void autoBuild(Player player, TrinityAutoBuildRequest request) {
            this.autoBuildCount++;
            this.lastAutoBuild = request;
        }
    }
}
