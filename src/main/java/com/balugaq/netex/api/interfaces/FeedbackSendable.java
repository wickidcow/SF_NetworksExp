package com.balugaq.netex.api.interfaces;

import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.utils.Lang;
import com.balugaq.netex.utils.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public interface FeedbackSendable {
    /**
     * Preserved public subscription map for source/binary compatibility.
     *
     * <p>Use {@link #subscribe(Player, Location)} and {@link #unsubscribe(Player, Location)} so the
     * reverse hot-path index stays synchronized.</p>
     */
    Map<UUID, Set<Location>> SUBSCRIBED_LOCATIONS = new ConcurrentHashMap<>();

    static void subscribe(@NotNull Player player, @NotNull Location location) {
        final UUID key = player.getUniqueId();
        SUBSCRIBED_LOCATIONS
            .computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet())
            .add(location);
        FeedbackSubscriptionIndex.subscribe(key, location);
    }

    static void unsubscribe(@NotNull Player player, @NotNull Location location) {
        final UUID key = player.getUniqueId();
        SUBSCRIBED_LOCATIONS.computeIfPresent(key, (ignored, locations) -> {
            locations.remove(location);
            return locations.isEmpty() ? null : locations;
        });
        FeedbackSubscriptionIndex.unsubscribe(key, location);
    }

    static boolean hasSubscribed(@NotNull Player player, @NotNull Location location) {
        final Set<Location> locations = SUBSCRIBED_LOCATIONS.get(player.getUniqueId());
        return locations != null && locations.contains(location);
    }

    static void sendFeedback0(@NotNull Location location, @NotNull FeedbackType type) {
        final Set<UUID> subscribers = FeedbackSubscriptionIndex.getSubscribers(location);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        for (UUID uuid : subscribers) {
            Player player = Bukkit.getServer().getPlayer(uuid);
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
        final Set<UUID> subscribers = FeedbackSubscriptionIndex.getSubscribers(location);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        for (UUID uuid : subscribers) {
            Player player = Bukkit.getServer().getPlayer(uuid);
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

/**
 * Reverse feedback index used by machine ticker hot paths.
 *
 * <p>The historical public UUID -> locations map remains available above. This companion index turns
 * ordinary machine feedback into one location lookup, so watching one debug location does not make
 * every loaded Networks machine scan every subscribed player on every ticker pass.</p>
 */
final class FeedbackSubscriptionIndex {
    private static final Map<Location, Set<UUID>> SUBSCRIBERS_BY_LOCATION = new ConcurrentHashMap<>();

    private FeedbackSubscriptionIndex() {
    }

    static void subscribe(@NotNull UUID playerId, @NotNull Location location) {
        SUBSCRIBERS_BY_LOCATION
            .computeIfAbsent(location.clone(), ignored -> ConcurrentHashMap.newKeySet())
            .add(playerId);
    }

    static void unsubscribe(@NotNull UUID playerId, @NotNull Location location) {
        SUBSCRIBERS_BY_LOCATION.computeIfPresent(location, (ignored, subscribers) -> {
            subscribers.remove(playerId);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }

    static @Nullable Set<UUID> getSubscribers(@NotNull Location location) {
        return SUBSCRIBERS_BY_LOCATION.get(location);
    }
}
