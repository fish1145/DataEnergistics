package com.fish_dan_.data_energistics;

import com.fish_dan_.data_energistics.bootstrap.common.CommonBootstrap;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Mod(Data_Energistics.MODID)
public class Data_Energistics {

    public static final String MODID = "data_energistics";

    private static final String[][] STARTUP_SHUTDOWN_LOG_PAIRS = {
            { "Ciallo～(∠・ω< )⌒☆", "柚子厨真恶心！" },
            { "原神启动！", "前面的区域以后再探索吧" }
    };

    private static final Logger LOGGER = LogUtils.getLogger();

    public Data_Energistics(IEventBus modEventBus, @Nullable ModContainer modContainer) {
        CommonBootstrap.init(modEventBus, modContainer);
        String[] selectedLogPair = STARTUP_SHUTDOWN_LOG_PAIRS[
                net.minecraft.util.RandomSource.create().nextInt(STARTUP_SHUTDOWN_LOG_PAIRS.length)];
        LOGGER.info(selectedLogPair[0]);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> LOGGER.info(selectedLogPair[1]),
                "data-energistics-shutdown-log"));
    }

    public static boolean isProd() {
        return FMLLoader.isProduction();
    }

    public static boolean isDev() {
        return !isProd();
    }

    public static boolean isDataGen() {
        return DatagenModLoader.isRunningDataGen();
    }

    public static MinecraftServer getMinecraftServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @SuppressWarnings("ConstantValue")
    public static boolean isClientThread() {
        return isClientSide() && Minecraft.getInstance() != null && Minecraft.getInstance().isSameThread();
    }

    public static boolean isClientSide() {
        return FMLEnvironment.dist.isClient();
    }

    public static ResourceLocation id(String path) {
        if (path.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath(MODID, "");
        }
        int i = path.indexOf(':');
        if (i > 0) {
            return ResourceLocation.tryParse(path);
        } else if (i == 0) {
            path = path.substring(i + 1);
        }
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
