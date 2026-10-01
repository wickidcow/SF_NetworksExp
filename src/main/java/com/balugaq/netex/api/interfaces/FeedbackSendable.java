package com.balugaq.netex.api.interfaces;

import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.utils.Lang;
import com.balugaq.netex.utils.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public interface FeedbackSendable {
    /**
     * The authoritative public subscription map. Existing integrations can mutate this map and
     * its location sets directly, so feedback must not depend on a separate unsynchronized index.
     */
    Map<UUID, Set<Location>> SUBSCRIBED_LOCATIONS = new ConcurrentHashMap<>();

    static void subscribe(@NotNull Player player, @NotNull Location location) {
        final UUID key = player.getUniqueId();
        SUBSCRIBED_LOCATIONS.compute(key, (ignored, locations) -> {
            if (locations == null) {
                locations = ConcurrentHashMap.newKeySet();
            }
            locations.add(location);
            return locations;
        });
    }

    static void unsubscribe(@NotNull Player player, @NotNull Location location) {
        final UUID key = player.getUniqueId();
        SUBSCRIBED_LOCATIONS.computeIfPresent(key, (ignored, locations) -> {
            locations.remove(location);
            return locations.isEmpty() ? null : locations;
        });
    }

    static boolean hasSubscribed(@NotNull Player player, @NotNull Location location) {
        final Set<Location> locations = SUBSCRIBED_LOCATIONS.get(player.getUniqueId());
        return locations != null && locations.contains(location);
    }

    static void sendFeedback0(@NotNull Location location, @NotNull FeedbackType type) {
        if (SUBSCRIBED_LOCATIONS.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Set<Location>> entry : SUBSCRIBED_LOCATIONS.entrySet()) {
            if (!entry.getValue().contains(location)) {
                continue;
            }
            Player player = Bukkit.getServer().getPlayer(entry.getKey());
            if (player != null) {
                sendFeedback0(player, location, type.getMessage());
            }
        }
    }

    static void sendFeedback0(@NotNull Player player, @NotNull Location location, String message) {
        player.sendMessage(String.format(
            Lang.getString("messages.debug.status_view"), LocationUtil.humanizeBlock(location), message));
    }

    default void sendFeedback(@NotNull Location location, @NotNull FeedbackType type) {
        if (SUBSCRIBED_LOCATIONS.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Set<Location>> entry : SUBSCRIBED_LOCATIONS.entrySet()) {
            if (!entry.getValue().contains(location)) {
                continue;
            }
            Player player = Bukkit.getServer().getPlayer(entry.getKey());
            if (player != null) {
                sendFeedback(player, location, type.getMessage());
            }
        }
    }

    default void sendFeedback(@NotNull Player player, @NotNull Location location, String message) {
        player.sendMessage(String.format(
            Lang.getString("messages.debug.status_view"), LocationUtil.humanizeBlock(location), message));
    }
}
