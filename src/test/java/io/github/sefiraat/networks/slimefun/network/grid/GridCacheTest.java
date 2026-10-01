package io.github.sefiraat.networks.slimefun.network.grid;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.github.sefiraat.networks.slimefun.network.grid.GridCache.SortOrder.*;
import static org.junit.jupiter.api.Assertions.*;

class GridCacheTest {

    @Test
    void forwardCycleIncludesTheFourthMode() {
        assertEquals(List.of(ALPHABETICAL, NUMBER, NUMBER_REVERSE, ADDON, ALPHABETICAL), cycle(4));
    }

    @Test
    void forwardCycleIncludesTheThirdModeWhenLimitedToThree() {
        assertEquals(List.of(ALPHABETICAL, NUMBER, NUMBER_REVERSE, ALPHABETICAL), cycle(3));
    }

    @Test
    void forwardCycleIncludesTheSecondModeWhenLimitedToTwo() {
        assertEquals(List.of(ALPHABETICAL, NUMBER, ALPHABETICAL), cycle(2));
    }

    @Test
    void oneModeAlwaysWrapsToItself() {
        assertSame(ALPHABETICAL, ALPHABETICAL.next(1));
        assertSame(ALPHABETICAL, ALPHABETICAL.previous(1));
    }

    @Test
    void forwardAndBackwardAreInversesWithinEverySupportedLimit() {
        var modes = GridCache.SortOrder.values();
        for (int limit = 1; limit <= modes.length; limit++) {
            for (int start = 0; start < limit; start++) {
                var mode = modes[start];
                assertSame(modes[(start + 1) % limit], mode.next(limit));
                assertSame(modes[(start + limit - 1) % limit], mode.previous(limit));
                assertSame(mode, mode.next(limit).previous(limit));
                assertSame(mode, mode.previous(limit).next(limit));
            }
        }
    }

    @Test
    void backwardNavigationMatchesHistoricalResultsForAllValidLimitsAndModes() {
        for (int limit = 1; limit <= 4; limit++) {
            for (var mode : GridCache.SortOrder.values()) {
                var historical = mode.previous().ordinal() + 1 >= limit
                        ? GridCache.SortOrder.values()[limit - 1] : mode.previous();
                assertSame(historical, mode.previous(limit));
            }
        }
    }

    @Test
    void unlimitedNavigationAndEnumIdentityRemainUnchanged() {
        assertEquals(List.of("ALPHABETICAL", "NUMBER", "NUMBER_REVERSE", "ADDON"),
                Arrays.stream(GridCache.SortOrder.values()).map(Enum::name).toList());
        for (var mode : GridCache.SortOrder.values()) {
            assertSame(mode.next(), mode.next(4));
            assertSame(mode.previous(), mode.previous(4));
            assertSame(mode, mode.next().previous());
        }
    }

    @Test
    void sortChangeInvalidatesOnlyTheDerivedEntryList() {
        var cache = new GridCache(7, 11, ALPHABETICAL);
        cache.setDisplayMode(GridCache.DisplayMode.HISTORY);
        var history = cache.getPullItemHistory();
        var entries = entries();
        cache.setEntriesCache(entries);
        cache.setSortOrder(NUMBER);
        assertNull(cache.getEntriesCache());
        assertSame(NUMBER, cache.getSortOrder());
        assertEquals(7, cache.getPage());
        assertEquals(11, cache.getMaxPages());
        assertSame(GridCache.DisplayMode.HISTORY, cache.getDisplayMode());
        assertSame(history, cache.getPullItemHistory());
        assertEquals(2, entries.size());
        assertEquals(9007199254740993L, entries.get(0).getValue().longValue());
    }

    @Test
    void filterChangeInvalidatesPreviouslyUnfilteredResultsImmediately() {
        var cache = new GridCache(0, 0, ALPHABETICAL);
        cache.setEntriesCache(entries());
        cache.setFilter("diamond");
        assertNull(cache.getEntriesCache());
        assertEquals("diamond", cache.getFilter());
    }

    @Test
    void clearingFilterInvalidatesTheOldFilteredResults() {
        var cache = new GridCache(0, 0, ALPHABETICAL);
        cache.setFilter("diamond");
        cache.setEntriesCache(entries());
        cache.setFilter(null);
        assertNull(cache.getEntriesCache());
        assertNull(cache.getFilter());
    }

    @Test
    void equivalentFilterDoesNotDiscardUsableResults() {
        var cache = new GridCache(0, 0, ALPHABETICAL);
        cache.setFilter("diamond");
        var entries = entries();
        cache.setEntriesCache(entries);
        cache.setFilter(new String("diamond"));
        assertSame(entries, cache.getEntriesCache());
    }

    @Test
    void repeatedNullFilterAndSortDoNotTriggerUnnecessaryRebuilds() {
        var cache = new GridCache(0, 0, ALPHABETICAL);
        var entries = entries();
        cache.setEntriesCache(entries);
        for (int i = 0; i < 1000; i++) {
            cache.setFilter(null);
            cache.setSortOrder(ALPHABETICAL);
            assertSame(entries, cache.getEntriesCache());
        }
    }

    @Test
    void pagingDoesNotDiscardTheSharedSortedView() {
        var cache = new GridCache(0, 3, ALPHABETICAL);
        var entries = entries();
        cache.setEntriesCache(entries);
        cache.setPage(2);
        cache.setMaxPages(4);
        assertSame(entries, cache.getEntriesCache());
    }

    @Test
    void changingOneGridDoesNotInvalidateAnotherGrid() {
        var first = new GridCache(0, 0, ALPHABETICAL);
        var second = new GridCache(0, 0, ALPHABETICAL);
        var entries = entries();
        first.setEntriesCache(entries);
        second.setEntriesCache(entries);
        first.setFilter("gold");
        assertNull(first.getEntriesCache());
        assertSame(entries, second.getEntriesCache());
        assertNull(second.getFilter());
    }

    @Test
    void newResultsCanBeInstalledAfterSelectionChanges() {
        var cache = new GridCache(0, 0, ALPHABETICAL);
        cache.setEntriesCache(entries());
        cache.setSortOrder(ADDON);
        var newEntries = entries();
        cache.setEntriesCache(newEntries);
        cache.setSortOrder(ADDON);
        assertSame(newEntries, cache.getEntriesCache());
    }

    @Test
    void invalidationDoesNotMutateCallerOwnedEntriesOrTheirAmounts() {
        var cache = new GridCache(0, 0, ALPHABETICAL);
        var entries = entries();
        var snapshot = new ArrayList<>(entries);
        cache.setEntriesCache(entries);
        cache.setFilter("");
        assertNull(cache.getEntriesCache());
        assertEquals(snapshot, entries);
        assertEquals("", cache.getFilter());
    }

    @Test
    void nullSortIsRejectedWithoutDestroyingCachedResults() {
        var cache = new GridCache(0, 0, ALPHABETICAL);
        var entries = entries();
        cache.setEntriesCache(entries);
        assertThrows(NullPointerException.class, () -> cache.setSortOrder(null));
        assertSame(ALPHABETICAL, cache.getSortOrder());
        assertSame(entries, cache.getEntriesCache());
    }

    private static List<GridCache.SortOrder> cycle(int limit) {
        var result = new ArrayList<GridCache.SortOrder>();
        var mode = ALPHABETICAL;
        result.add(mode);
        for (int i = 0; i < limit; i++) {
            mode = mode.next(limit);
            result.add(mode);
        }
        return result;
    }

    private static List<Map.Entry<ItemStack, Long>> entries() {
        // The cache stores opaque references; no item construction or server mock is needed.
        var entries = new ArrayList<Map.Entry<ItemStack, Long>>();
        entries.add(new AbstractMap.SimpleImmutableEntry<>(null, 9007199254740993L));
        entries.add(new AbstractMap.SimpleImmutableEntry<>(null, 7L));
        return entries;
    }
}
