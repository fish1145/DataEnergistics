package com.fish_dan_.data_energistics.client.xei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.runtime.ClientThreadHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Coalesces viewer-independent XEI layout refreshes for execution on a following client tick.
 */
public final class XeiLayoutRefreshQueue {

    private static final Map<Object, RefreshRequest> PENDING = new LinkedHashMap<>();

    private XeiLayoutRefreshQueue() {}

    /**
     * Replaces any pending refresh with the same key.
     *
     * @param key            identity of the viewer recipe or layout being refreshed
     * @param expectedScreen screen that must still be open when the request executes
     * @param validity       verifies that the recipe or composition is still current
     * @param action         refresh operation supplied by the active viewer integration
     */
    public static void enqueue(
                               Object key,
                               Screen expectedScreen,
                               BooleanSupplier validity,
                               Runnable action) {
        assertClientThread("enqueue");
        RefreshRequest request = new RefreshRequest(key, expectedScreen, validity, action);
        PENDING.put(key, request);
    }

    /**
     * Cancels the pending request associated with the supplied key.
     */
    public static void cancel(Object key) {
        assertClientThread("cancel");
        if (key == null) {
            throw new IllegalArgumentException("XEI layout refresh key cannot be null");
        }
        PENDING.remove(key);
    }

    /**
     * Removes every pending refresh request.
     */
    public static void clear() {
        assertClientThread("clear");
        PENDING.clear();
    }

    /**
     * Executes a snapshot of the currently pending requests.
     * Requests enqueued by an executing action remain pending until the next client tick.
     */
    public static void drain() {
        assertClientThread("drain");
        if (PENDING.isEmpty()) {
            return;
        }

        List<RefreshRequest> snapshot = List.copyOf(PENDING.values());
        PENDING.clear();

        Minecraft minecraft = Minecraft.getInstance();
        for (RefreshRequest request : snapshot) {
            if (minecraft.screen != request.expectedScreen()) {
                continue;
            }

            try {
                if (request.validity().getAsBoolean()) {
                    request.action().run();
                }
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to refresh an XEI layout for key {}",
                        request.key(),
                        exception);
            }
        }
    }

    private static void assertClientThread(String operation) {
        if (!ClientThreadHelper.isClientThread()) {
            throw new IllegalStateException(
                    "XEI layout refresh queue " + operation + " must run on the client thread");
        }
    }

    private record RefreshRequest(
                                  Object key,
                                  Screen expectedScreen,
                                  BooleanSupplier validity,
                                  Runnable action) {

        private RefreshRequest {
            if (key == null || expectedScreen == null || validity == null || action == null) {
                throw new IllegalArgumentException("XEI layout refresh request arguments cannot be null");
            }
        }
    }
}
