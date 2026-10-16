package io.github.sefiraat.networks.network;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NetworkControllerConflictResolutionTest {

    @Test
    void choosesSameLoserRegardlessOfDiscoveryOrder() {
        Location first = new Location(null, -4, 70, 8);
        Location second = new Location(null, 12, 64, -20);

        assertEquals(
            new Location(null, 12, 64, -20),
            NetworkNode.chooseControllerForRemoval(first, second));
        assertEquals(
            new Location(null, 12, 64, -20),
            NetworkNode.chooseControllerForRemoval(second, first));
    }

    @Test
    void comparesYAndZWhenEarlierCoordinatesMatch() {
        Location lowY = new Location(null, 5, 60, 99);
        Location highY = new Location(null, 5, 61, -99);
        assertEquals(
            new Location(null, 5, 61, -99),
            NetworkNode.chooseControllerForRemoval(lowY, highY));

        Location lowZ = new Location(null, 5, 60, -4);
        Location highZ = new Location(null, 5, 60, 7);
        assertEquals(
            new Location(null, 5, 60, 7),
            NetworkNode.chooseControllerForRemoval(lowZ, highZ));
    }

    @Test
    void normalizesFractionalCoordinatesAndRotation() {
        Location first = new Location(null, 1.9, 64.8, 3.1, 90.0F, 45.0F);
        Location second = new Location(null, 2.2, 64.1, 3.9, -30.0F, 12.0F);

        assertEquals(
            new Location(null, 2, 64, 3),
            NetworkNode.chooseControllerForRemoval(first, second));
    }

    @Test
    void sameBlockDoesNotProduceRemovalTarget() {
        Location first = new Location(null, 3.1, 64.9, -7.2, 45.0F, 10.0F);
        Location second = new Location(null, 3.9, 64.1, -7.8, -90.0F, -5.0F);

        assertNull(NetworkNode.chooseControllerForRemoval(first, second));
    }
}
