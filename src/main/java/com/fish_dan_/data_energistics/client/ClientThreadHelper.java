package com.fish_dan_.data_energistics.client;

import net.minecraft.client.Minecraft;

public final class ClientThreadHelper {

    private ClientThreadHelper() {}

    public static boolean isClientThread() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.isSameThread();
    }
}
