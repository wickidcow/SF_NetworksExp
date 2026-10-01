package io.github.sefiraat.networks.slimefun.network.grid;

import com.balugaq.netex.utils.Lang;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@Setter
public class GridCache {

    @NotNull
    private final List<ItemStack> pullItemHistory = new ArrayList<>();

    @Setter
    @Getter
    private int page;

    @Setter
    @Getter
    private int maxPages;

    @NotNull
    private DisplayMode displayMode;

    @NotNull
    @Setter(AccessLevel.NONE)
    private SortOrder sortOrder;

    @Nullable
    @Setter(AccessLevel.NONE)
    private String filter;

    @Nullable
    private List<Map.Entry<ItemStack, Long>> entriesCache;

    public GridCache(int page, int maxPages, @NotNull SortOrder sortOrder) {
        this.page = page;
        this.maxPages = maxPages;
        this.sortOrder = sortOrder;
        this.displayMode = DisplayMode.DISPLAY;
    }

    public void setSortOrder(@NotNull SortOrder sortOrder) {
        Objects.requireNonNull(sortOrder, "sortOrder is marked non-null but is null");
        if (this.sortOrder != sortOrder) {
            this.sortOrder = sortOrder;
            this.entriesCache = null;
        }
    }

    public void setFilter(@Nullable String filter) {
        if (!Objects.equals(this.filter, filter)) {
            this.filter = filter;
            this.entriesCache = null;
        }
    }

    public void addPullItemHistory(@Nullable ItemStack itemStack) {
        if (itemStack != null) {
            getPullItemHistory().remove(itemStack);

            getPullItemHistory().add(0, itemStack);
        }
    }

    public void toggleDisplayMode() {
        if (this.displayMode == DisplayMode.DISPLAY) {
            this.displayMode = DisplayMode.HISTORY;
        } else {
            this.displayMode = DisplayMode.DISPLAY;
        }
    }

    public enum SortOrder {
        ALPHABETICAL,
        NUMBER,
        NUMBER_REVERSE,
        ADDON;

        private static final SortOrder[] ORDERS = values();

        public @NotNull SortOrder next(@Range(from = 1, to = 4) int limit) {
            SortOrder candidate = this.next();
            return candidate.ordinal() >= limit ? ALPHABETICAL : candidate;
        }

        public @NotNull SortOrder next() {
            return switch (this) {
                case ALPHABETICAL -> NUMBER;
                case NUMBER -> NUMBER_REVERSE;
                case NUMBER_REVERSE -> ADDON;
                case ADDON -> ALPHABETICAL;
            };
        }

        public @NotNull SortOrder previous(@Range(from = 1, to = 4) int limit) {
            SortOrder candidate = this.previous();
            return candidate.ordinal() >= limit ? ORDERS[limit - 1] : candidate;
        }

        public @NotNull SortOrder previous() {
            return switch (this) {
                case ALPHABETICAL -> ADDON;
                case NUMBER -> ALPHABETICAL;
                case NUMBER_REVERSE -> NUMBER;
                case ADDON -> NUMBER_REVERSE;
            };
        }

        public String getTranslationName() {
            return Lang.getString("messages.completed-operation.grid.sort_orders." + name().toLowerCase());
        }
    }

    public enum DisplayMode {
        DISPLAY,
        HISTORY
    }
}
