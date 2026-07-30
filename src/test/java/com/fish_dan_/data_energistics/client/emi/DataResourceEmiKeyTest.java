package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.ae2.EchoKey;
import com.fish_dan_.data_energistics.ae2.ModAE2Keys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public final class DataResourceEmiKeyTest {

    @Test
    void everyCustomKeyHasOneNativeEmiIdentity() {
        assertEquals(ModAE2Keys.keys().size(), DataResourceEmiKey.values().length);
        for (var customKey : ModAE2Keys.keys()) {
            DataResourceEmiKey emiKey = DataResourceEmiKey.fromAeKey(customKey);
            assertNotNull(emiKey);
            assertSame(customKey, emiKey.aeKey());
            assertSame(emiKey, DataResourceEmiKey.fromId(customKey.getId()));
        }
        assertSame(DataResourceEmiKey.ECHO, DataResourceEmiKey.fromAeKey(EchoKey.of()));
    }
}
