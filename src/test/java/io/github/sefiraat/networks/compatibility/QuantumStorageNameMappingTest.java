package io.github.sefiraat.networks.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class QuantumStorageNameMappingTest {

    private static final Pattern STORAGE_NAME = Pattern.compile(
        "(?m)^  NTW_QUANTUM_STORAGE_(\\d+):\\R    name: Network Quantum Storage \\((\\d+)\\)$");

    @Test
    void visibleTierNumbersFollowCapacityProgression() throws IOException {
        String yaml;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("lang/en-US.yml")) {
            assertNotNull(stream, "Missing lang/en-US.yml");
            yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<Integer, Integer> actual = new LinkedHashMap<>();
        Matcher matcher = STORAGE_NAME.matcher(yaml);
        while (matcher.find()) {
            actual.put(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        }

        Map<Integer, Integer> expected = new LinkedHashMap<>();
        expected.put(0, 0);
        expected.put(9, 1);
        expected.put(10, 2);
        expected.put(1, 3);
        expected.put(2, 4);
        expected.put(3, 5);
        expected.put(4, 6);
        expected.put(5, 7);
        expected.put(6, 8);
        expected.put(7, 9);
        expected.put(8, 10);
        expected.put(11, 11);
        expected.put(12, 12);
        expected.put(13, 13);
        expected.put(14, 14);

        assertEquals(expected, actual,
            "Visible Quantum Storage tiers must follow capacity order without changing legacy Slimefun IDs");
    }
}
