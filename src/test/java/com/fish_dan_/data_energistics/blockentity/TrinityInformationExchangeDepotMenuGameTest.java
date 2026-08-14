package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityInformationExchangeDepotBlockEntity.StorageMode;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternTerminalPartition;
import com.fish_dan_.data_energistics.menu.trinity.TrinityInformationExchangeDepotMenu;
import com.fish_dan_.data_energistics.registry.DEMenus;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.mojang.authlib.GameProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the real information-exchange-depot menu route and its three persisted storage modes. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityInformationExchangeDepotMenuGameTest {

    private TrinityInformationExchangeDepotMenuGameTest() {}

    @TestHolder("trinity_information_exchange_depot_mode_menu_is_authoritative_and_persisted")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void modeMenuIsAuthoritativeAndPersisted(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        AtomicReference<ModeTransferScenario> scenario = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> scenario.set(ModeTransferScenario.begin(helper, fixture)))
                .thenWaitUntil(() -> scenario.get().awaitInputTransfer())
                .thenExecute(() -> scenario.get().beginOutputTransfer())
                .thenWaitUntil(() -> scenario.get().awaitOutputTransfer())
                .thenExecute(() -> scenario.get().verifyInvalidMenuRoute())
                .thenSucceed();
    }

    private static void moveNear(ServerPlayer player, BlockPos position) {
        player.setPos(position.getX() + 1.5D, position.getY() + 0.5D, position.getZ() + 0.5D);
    }

    private static void moveOutOfRange(ServerPlayer player, BlockPos position) {
        player.setPos(position.getX() + 9.5D, position.getY() + 0.5D, position.getZ() + 0.5D);
    }

    private static ItemStack encodedOakPlanksPattern(GameTestHelper helper) {
        RecipeHolder<?> recipe = helper.getLevel()
                .getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace("oak_planks"))
                .orElseThrow();
        if (!(recipe.value() instanceof CraftingRecipe craftingRecipe)) {
            throw new IllegalStateException("Expected minecraft:oak_planks to be a crafting recipe");
        }
        RecipeHolder<CraftingRecipe> craftingRecipeHolder = new RecipeHolder<>(recipe.id(), craftingRecipe);
        ItemStack[] inputs = new ItemStack[9];
        inputs[0] = new ItemStack(Items.OAK_LOG);
        for (int slot = 1; slot < inputs.length; slot++) {
            inputs[slot] = ItemStack.EMPTY;
        }
        return PatternDetailsHelper.encodeCraftingPattern(
                craftingRecipeHolder,
                inputs,
                new ItemStack(Items.OAK_PLANKS, 4),
                false,
                false);
    }

    private static final class ModeTransferScenario {

        private static final AEItemKey STORAGE_KEY = AEItemKey.of(Items.DIAMOND);
        private static final AEItemKey INPUT_ITEM_KEY = AEItemKey.of(Items.IRON_INGOT);
        private static final AEFluidKey INPUT_FLUID_KEY = AEFluidKey.of(Fluids.WATER);
        private static final long STORAGE_AMOUNT = 5L;
        private static final long INPUT_ITEM_AMOUNT = 37L;
        private static final long INPUT_FLUID_AMOUNT = 250L;

        private final GameTestHelper helper;
        private final TrinityDataCoreGameTestFixture fixture;
        private final TrinityInformationExchangeDepotBlockEntity depot;
        private final TrinityInformationExchangeDepotMenu menu;
        private final MenuTestServerPlayer player;
        private final List<TrinityPatternTerminalPartition> terminalPartitions;
        private final List<IPatternDetails> publishedPatterns;
        private final TestStorage finiteSource;
        private final TestStorage outputSink;
        private final InfiniteStorage integerInfiniteSource;
        private final InfiniteStorage longInfiniteSource;
        private final MountedStorageNode storageNode;

        private ModeTransferScenario(GameTestHelper helper,
                                     TrinityDataCoreGameTestFixture fixture,
                                     TrinityInformationExchangeDepotBlockEntity depot,
                                     TrinityInformationExchangeDepotMenu menu,
                                     MenuTestServerPlayer player,
                                     List<TrinityPatternTerminalPartition> terminalPartitions,
                                     List<IPatternDetails> publishedPatterns,
                                     TestStorage finiteSource,
                                     TestStorage outputSink,
                                     InfiniteStorage integerInfiniteSource,
                                     InfiniteStorage longInfiniteSource,
                                     MountedStorageNode storageNode) {
            this.helper = helper;
            this.fixture = fixture;
            this.depot = depot;
            this.menu = menu;
            this.player = player;
            this.terminalPartitions = terminalPartitions;
            this.publishedPatterns = publishedPatterns;
            this.finiteSource = finiteSource;
            this.outputSink = outputSink;
            this.integerInfiniteSource = integerInfiniteSource;
            this.longInfiniteSource = longInfiniteSource;
            this.storageNode = storageNode;
        }

        private static ModeTransferScenario begin(GameTestHelper helper, TrinityDataCoreGameTestFixture fixture) {
            TrinityInformationExchangeDepotBlockEntity depot = fixture.accessHatches().stream()
                    .filter(fixture.host()::isLeaseOwner)
                    .findFirst()
                    .orElseThrow(() -> new GameTestAssertException(
                            "Trinity fixture has no lease-owning exchange depot"));
            MenuTestServerPlayer player = new MenuTestServerPlayer(helper);
            moveNear(player, depot.getBlockPos());

            boolean opened = MenuOpener.open(
                    DEMenus.TRINITY_INFORMATION_EXCHANGE_DEPOT.get(),
                    player,
                    MenuLocators.forBlockEntity(depot));
            helper.assertTrue(opened, "AE2 MenuOpener should resolve the placed information exchange depot");
            if (!(player.containerMenu instanceof TrinityInformationExchangeDepotMenu menu)) {
                throw new GameTestAssertException("Information exchange depot did not open its dedicated mode menu");
            }
            helper.assertValueEqual(menu.mode(), StorageMode.STORAGE,
                    "New depots should begin in storage mode");

            helper.assertTrue(fixture.host().getPatternCatalog().mountedCores().getFirst().core()
                    .trySetPattern(0, encodedOakPlanksPattern(helper)),
                    "Mode isolation probe should install a real crafting pattern");
            fixture.host().serverTick();
            fixture.refreshPatternPublication();
            List<IPatternDetails> publishedPatterns = List.copyOf(fixture.grid().getCraftingService()
                    .getCraftingFor(AEItemKey.of(Items.OAK_PLANKS)));
            helper.assertFalse(publishedPatterns.isEmpty(),
                    "Installed pattern must be published before switching storage modes");
            List<TrinityPatternTerminalPartition> terminalPartitions = List.copyOf(depot.terminalPartitions());
            helper.assertFalse(terminalPartitions.isEmpty(),
                    "Formed exchange depot must retain a concrete terminal layout during mode switches");

            helper.assertValueEqual(
                    fixture.grid().getStorageService().getInventory().insert(
                            STORAGE_KEY,
                            8L,
                            Actionable.MODULATE,
                            IActionSource.empty()),
                    8L,
                    "Storage mode should expose mounted insertion through the AE network");
            helper.assertValueEqual(networkAmount(fixture, STORAGE_KEY), 8L,
                    "Storage mode should mount Data Core contents on the AE network");
            helper.assertValueEqual(
                    fixture.grid().getStorageService().getInventory().extract(
                            STORAGE_KEY,
                            3L,
                            Actionable.MODULATE,
                            IActionSource.empty()),
                    3L,
                    "Storage mode should expose mounted extraction through the AE network");
            helper.assertValueEqual(networkAmount(fixture, STORAGE_KEY), STORAGE_AMOUNT,
                    "Mounted storage extraction should leave the exact Data Core balance");

            menu.receiveClientAction("set_information_exchange_mode", Integer.toString(StorageMode.INPUT.networkId()));
            helper.assertValueEqual(depot.informationExchangeMode(), StorageMode.INPUT,
                    "A valid menu action should select input mode");
            helper.assertTrue(StorageMode.INPUT.pullsFromNetwork(), "Input mode must actively pull from AE storage");
            helper.assertFalse(StorageMode.INPUT.mountsStorage(), "Input mode must not mount Data Core storage");
            assertDirectStorageHidden(helper, depot, STORAGE_KEY);
            helper.assertValueEqual(networkAmount(fixture, STORAGE_KEY), 0L,
                    "Input mode must unmount existing Data Core contents from AE storage");

            CompoundTag saved = depot.saveWithFullMetadata(helper.getLevel().registryAccess());
            TrinityInformationExchangeDepotBlockEntity loaded = new TrinityInformationExchangeDepotBlockEntity(
                    depot.getBlockPos(),
                    depot.getBlockState());
            loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.assertValueEqual(loaded.informationExchangeMode(), StorageMode.INPUT,
                    "The selected information exchange mode must survive NBT reload");

            TestStorage finiteSource = TestStorage.extractOnly();
            finiteSource.preload(INPUT_ITEM_KEY, INPUT_ITEM_AMOUNT);
            finiteSource.preload(INPUT_FLUID_KEY, INPUT_FLUID_AMOUNT);
            TestStorage outputSink = TestStorage.insertOnly();
            InfiniteStorage integerInfiniteSource = new InfiniteStorage(INPUT_ITEM_KEY, Integer.MAX_VALUE);
            InfiniteStorage longInfiniteSource = new InfiniteStorage(INPUT_ITEM_KEY, Long.MAX_VALUE);
            IGridNode depotNode = depot.getMainNode().getNode();
            if (depotNode == null) {
                throw new GameTestAssertException("Information exchange depot node is not available");
            }
            MountedStorageNode storageNode = new MountedStorageNode(
                    helper,
                    depotNode,
                    List.of(
                            new StorageMount(longInfiniteSource, 200),
                            new StorageMount(integerInfiniteSource, 100),
                            new StorageMount(finiteSource, 0),
                            new StorageMount(outputSink, -100)));

            ModeTransferScenario scenario = new ModeTransferScenario(
                    helper,
                    fixture,
                    depot,
                    menu,
                    player,
                    terminalPartitions,
                    publishedPatterns,
                    finiteSource,
                    outputSink,
                    integerInfiniteSource,
                    longInfiniteSource,
                    storageNode);
            scenario.assertPatternIsolation("input mode switch");
            return scenario;
        }

        private void awaitInputTransfer() {
            this.depot.serverTick();
            if (this.finiteSource.amount(INPUT_ITEM_KEY) != 0L ||
                    this.finiteSource.amount(INPUT_FLUID_KEY) != 0L ||
                    coreAmount(this.helper, this.fixture.host(), INPUT_ITEM_KEY) != INPUT_ITEM_AMOUNT ||
                    coreAmount(this.helper, this.fixture.host(), INPUT_FLUID_KEY) != INPUT_FLUID_AMOUNT) {
                throw new GameTestAssertException("Input mode has not drained all finite item and fluid sources yet");
            }
            this.helper.assertValueEqual(this.integerInfiniteSource.modulatedExtractionCalls(), 0,
                    "Integer.MAX_VALUE source must never be executed");
            this.helper.assertValueEqual(this.longInfiniteSource.modulatedExtractionCalls(), 0,
                    "Long.MAX_VALUE source must never be executed");
            this.helper.assertValueEqual(coreAmount(this.helper, this.fixture.host(), STORAGE_KEY), STORAGE_AMOUNT,
                    "Input transfer must retain pre-existing Data Core contents");
            this.helper.assertValueEqual(this.outputSink.amount(INPUT_ITEM_KEY), 0L,
                    "Input mode must not push Data Core contents into AE storage");
            assertPatternIsolation("completed input transfer");
        }

        private void beginOutputTransfer() {
            this.menu.receiveClientAction(
                    "set_information_exchange_mode",
                    Integer.toString(StorageMode.OUTPUT.networkId()));
            this.helper.assertValueEqual(this.depot.informationExchangeMode(), StorageMode.OUTPUT,
                    "A valid menu action should select output mode");
            this.helper.assertTrue(StorageMode.OUTPUT.pushesToNetwork(),
                    "Output mode must actively push into AE storage");
            this.helper.assertFalse(StorageMode.OUTPUT.mountsStorage(),
                    "Output mode must not mount Data Core storage");
            assertDirectStorageHidden(this.helper, this.depot, STORAGE_KEY);
            assertPatternIsolation("output mode switch");
        }

        private void awaitOutputTransfer() {
            this.depot.serverTick();
            if (this.outputSink.amount(INPUT_ITEM_KEY) != INPUT_ITEM_AMOUNT ||
                    this.outputSink.amount(INPUT_FLUID_KEY) != INPUT_FLUID_AMOUNT ||
                    this.outputSink.amount(STORAGE_KEY) != STORAGE_AMOUNT) {
                throw new GameTestAssertException("Output mode has not pushed the complete Data Core snapshot yet");
            }
            this.helper.assertValueEqual(coreAmount(this.helper, this.fixture.host(), INPUT_ITEM_KEY), 0L,
                    "Output mode should remove transferred item contents from the Data Core");
            this.helper.assertValueEqual(coreAmount(this.helper, this.fixture.host(), INPUT_FLUID_KEY), 0L,
                    "Output mode should remove transferred fluid contents from the Data Core");
            this.helper.assertValueEqual(coreAmount(this.helper, this.fixture.host(), STORAGE_KEY), 0L,
                    "Output mode should remove the pre-existing storage key after transfer");
            this.helper.assertValueEqual(this.integerInfiniteSource.modulatedExtractionCalls(), 0,
                    "Output processing must not extract from the Integer.MAX_VALUE source");
            this.helper.assertValueEqual(this.longInfiniteSource.modulatedExtractionCalls(), 0,
                    "Output processing must not extract from the Long.MAX_VALUE source");
            assertPatternIsolation("completed output transfer");
        }

        private void verifyInvalidMenuRoute() {
            moveOutOfRange(this.player, this.depot.getBlockPos());
            this.menu.receiveClientAction(
                    "set_information_exchange_mode",
                    Integer.toString(StorageMode.STORAGE.networkId()));
            this.helper.assertValueEqual(this.depot.informationExchangeMode(), StorageMode.OUTPUT,
                    "An invalid physical route must not change the authoritative mode");
            assertPatternIsolation("rejected out-of-range mode action");
            this.player.closeContainer();
            this.storageNode.close();
        }

        private void assertPatternIsolation(String operation) {
            List<TrinityPatternTerminalPartition> currentPartitions = this.depot.terminalPartitions();
            this.helper.assertValueEqual(currentPartitions.size(), this.terminalPartitions.size(),
                    operation + " must retain the terminal partition count");
            for (int index = 0; index < currentPartitions.size(); index++) {
                this.helper.assertTrue(currentPartitions.get(index) == this.terminalPartitions.get(index),
                        operation + " must retain terminal partition " + index);
            }
            List<IPatternDetails> currentPatterns = List.copyOf(this.fixture.grid().getCraftingService()
                    .getCraftingFor(AEItemKey.of(Items.OAK_PLANKS)));
            this.helper.assertTrue(currentPatterns.containsAll(this.publishedPatterns),
                    operation + " must retain the published crafting route");
        }
    }

    private static void assertDirectStorageHidden(GameTestHelper helper,
                                                  TrinityInformationExchangeDepotBlockEntity depot,
                                                  AEKey storedKey) {
        helper.assertValueEqual(
                depot.getInventory().insert(
                        AEItemKey.of(Items.GOLD_INGOT),
                        1L,
                        Actionable.MODULATE,
                        IActionSource.empty()),
                0L,
                "Transfer modes must reject direct insertion");
        helper.assertValueEqual(
                depot.getInventory().extract(
                        storedKey,
                        1L,
                        Actionable.MODULATE,
                        IActionSource.empty()),
                0L,
                "Transfer modes must reject direct extraction");
        KeyCounter exposed = new KeyCounter();
        depot.getInventory().getAvailableStacks(exposed);
        helper.assertTrue(exposed.isEmpty(), "Transfer modes must hide Data Core contents from direct enumeration");
    }

    private static long networkAmount(TrinityDataCoreGameTestFixture fixture, AEKey key) {
        KeyCounter contents = new KeyCounter();
        fixture.grid().getStorageService().getInventory().getAvailableStacks(contents);
        return contents.get(key);
    }

    private static long coreAmount(GameTestHelper helper, TrinityDataCoreBlockEntity host, AEKey key) {
        KeyCounter contents = new KeyCounter();
        TrinityDataCoreStorageSavedData.get(helper.getLevel().getServer())
                .addAvailableStacks(host.getStorageId(), contents);
        return contents.get(key);
    }

    private record StorageMount(MEStorage storage, int priority) {}

    private static final class MountedStorageNode implements AutoCloseable {

        private static final IGridNodeListener<MountedStorageNode> NODE_LISTENER = (owner, node) -> {};

        private final IManagedGridNode managedNode;
        private boolean closed;

        private MountedStorageNode(GameTestHelper helper, IGridNode target, List<StorageMount> mounts) {
            IStorageProvider provider = storageMounts -> {
                for (StorageMount mount : mounts) {
                    storageMounts.mount(mount.storage(), mount.priority());
                }
            };
            this.managedNode = GridHelper.createManagedNode(this, NODE_LISTENER)
                    .setInWorldNode(false)
                    .setIdlePowerUsage(0.0D)
                    .addService(IStorageProvider.class, provider);
            this.managedNode.create(helper.getLevel(), null);
            IGridNode storageNode = this.managedNode.getNode();
            if (storageNode == null) {
                this.managedNode.destroy();
                throw new IllegalStateException("Test storage node was not created");
            }
            try {
                GridHelper.createConnection(storageNode, target);
            } catch (RuntimeException exception) {
                this.managedNode.destroy();
                throw exception;
            }
            registerCleanup(helper);
        }

        private void registerCleanup(GameTestHelper helper) {
            helper.testInfo.addListener(new GameTestListener() {

                @Override
                public void testStructureLoaded(GameTestInfo testInfo) {}

                @Override
                public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
                    close();
                }

                @Override
                public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                    close();
                }

                @Override
                public void testAddedForRerun(GameTestInfo testInfo,
                                              GameTestInfo rerunTestInfo,
                                              GameTestRunner runner) {
                    close();
                }
            });
        }

        @Override
        public void close() {
            if (!this.closed) {
                this.closed = true;
                this.managedNode.destroy();
            }
        }
    }

    private static final class TestStorage implements MEStorage {

        private final Map<AEKey, Long> amounts = new HashMap<>();
        private final boolean acceptsInsert;
        private final boolean allowsExtract;

        private TestStorage(boolean acceptsInsert, boolean allowsExtract) {
            this.acceptsInsert = acceptsInsert;
            this.allowsExtract = allowsExtract;
        }

        private static TestStorage extractOnly() {
            return new TestStorage(false, true);
        }

        private static TestStorage insertOnly() {
            return new TestStorage(true, false);
        }

        private void preload(AEKey key, long amount) {
            if (amount <= 0L) {
                throw new IllegalArgumentException("Preloaded test storage amount must be positive");
            }
            this.amounts.put(key, amount);
        }

        private long amount(AEKey key) {
            return this.amounts.getOrDefault(key, 0L);
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            if (!this.acceptsInsert) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                this.amounts.merge(what, amount, Math::addExact);
            }
            return amount;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            if (!this.allowsExtract) {
                return 0L;
            }
            long extracted = Math.min(amount, amount(what));
            if (mode == Actionable.MODULATE && extracted > 0L) {
                long remaining = amount(what) - extracted;
                if (remaining == 0L) {
                    this.amounts.remove(what);
                } else {
                    this.amounts.put(what, remaining);
                }
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (Map.Entry<AEKey, Long> entry : this.amounts.entrySet()) {
                out.add(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public Component getDescription() {
            return Component.literal("Trinity mode transfer test storage");
        }
    }

    private static final class InfiniteStorage implements MEStorage {

        private final AEKey key;
        private final long reportedAmount;
        private int modulatedExtractionCalls;

        private InfiniteStorage(AEKey key, long reportedAmount) {
            this.key = key;
            this.reportedAmount = reportedAmount;
        }

        private int modulatedExtractionCalls() {
            return this.modulatedExtractionCalls;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            return 0L;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            if (!this.key.equals(what)) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                this.modulatedExtractionCalls++;
            }
            return amount;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.add(this.key, this.reportedAmount);
        }

        @Override
        public Component getDescription() {
            return Component.literal("Trinity infinite source filter probe");
        }
    }

    private static final class MenuTestServerPlayer extends ServerPlayer {

        private MenuTestServerPlayer(GameTestHelper helper) {
            super(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "trinity-exchange-menu"),
                    ClientInformation.createDefault());
            new DiscardingPacketListener(helper.getLevel().getServer(), this, getGameProfile());
        }
    }

    private static final class DiscardingPacketListener extends ServerGamePacketListenerImpl {

        private DiscardingPacketListener(MinecraftServer server, ServerPlayer player, GameProfile profile) {
            super(
                    server,
                    new Connection(PacketFlow.SERVERBOUND),
                    player,
                    new CommonListenerCookie(
                            profile,
                            0,
                            ClientInformation.createDefault(),
                            false,
                            ConnectionType.NEOFORGE));
        }

        @Override
        public void send(Packet<?> packet) {
            // The server-side menu route is under test; outbound client synchronization is intentionally discarded.
        }
    }
}
