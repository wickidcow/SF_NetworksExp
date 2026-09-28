package com.ytdd9527.networksexpansion.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextUtilTest {

    @Test
    void translatesAlternateLegacyColorCodesWithoutChangingText() {
        assertEquals("§aGreen §lBold§r Normal", TextUtil.color("&aGreen &lBold&r Normal"));
    }

    @Test
    void stripsLegacyFormattingIncludingHexSequences() {
        assertEquals(
            "Green Hex",
            TextUtil.stripColor("§aGreen §x§f§f§8§8§0§0Hex"));
    }

    @Test
    void adventureBridgePreservesLegacyFormatting() {
        String legacy = "§aGreen §lBold";
        assertEquals(legacy, TextUtil.legacy(TextUtil.component(legacy)));
    }
}
