package io.github.maxijaxi.sponsor.core;

import static io.github.maxijaxi.sponsor.core.SponsorGraph.SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SponsorGraphTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    private final SponsorGraph graph = new SponsorGraph();

    @Test
    void emptyGraphHasNobodyReachable() {
        assertTrue(graph.reachable().isEmpty());
        assertTrue(graph.isReachable(SERVER));
        assertFalse(graph.isReachable(A));
    }

    @Test
    void chainFromServerIsReachable() {
        graph.addEdge(SERVER, A, NOW);
        graph.addEdge(A, B, NOW);
        graph.addEdge(B, C, NOW);
        assertEquals(List.of(A, B, C), List.copyOf(graph.reachable()));
    }

    @Test
    void cycleWithoutServerIsNotReachable() {
        graph.addEdge(A, B, NOW);
        graph.addEdge(B, C, NOW);
        graph.addEdge(C, A, NOW);
        assertTrue(graph.reachable().isEmpty());

        graph.addEdge(SERVER, B, NOW);
        assertEquals(Set.of(A, B, C), graph.reachable());
    }

    @Test
    void cycleLosesAccessCompletelyWhenConnectionToServerIsRemoved() {
        graph.addEdge(SERVER, A, NOW);
        graph.addEdge(A, B, NOW);
        graph.addEdge(B, C, NOW);
        graph.addEdge(C, A, NOW);
        assertEquals(Set.of(A, B, C), graph.reachable());

        assertTrue(graph.removeEdge(SERVER, A));
        assertTrue(graph.reachable().isEmpty());
        // Die Kanten des Zyklus bleiben gespeichert.
        assertTrue(graph.hasEdge(A, B));
        assertTrue(graph.hasEdge(B, C));
        assertTrue(graph.hasEdge(C, A));
    }

    @Test
    void storedEdgesOfUnreachablePlayersCountAgainWhenReconnected() {
        graph.addEdge(SERVER, A, NOW);
        graph.addEdge(A, B, NOW);
        graph.removeEdge(SERVER, A);
        assertFalse(graph.isReachable(B));

        graph.addEdge(SERVER, A, NOW);
        assertTrue(graph.isReachable(B));
    }

    @Test
    void selfSponsoringIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> graph.addEdge(A, A, NOW));
        assertThrows(IllegalArgumentException.class, () -> graph.addEdge(A, SERVER, NOW));
    }

    @Test
    void duplicateEdgesAreRejected() {
        assertTrue(graph.addEdge(A, B, NOW));
        assertFalse(graph.addEdge(A, B, NOW.plusSeconds(60)));
        assertEquals(1, graph.sponsorshipCount(A));
        assertEquals(Optional.of(NOW), graph.since(A, B));
    }

    @Test
    void removingMissingEdgeReturnsFalse() {
        assertFalse(graph.removeEdge(A, B));
        graph.addEdge(A, B, NOW);
        assertFalse(graph.removeEdge(B, A));
    }

    @Test
    void sponsorsAndSponsoredAreReported() {
        graph.addEdge(SERVER, A, NOW);
        graph.addEdge(SERVER, B, NOW);
        graph.addEdge(A, C, NOW);
        graph.addEdge(B, C, NOW);
        assertEquals(Set.of(A, B), graph.sponsorsOf(C));
        assertEquals(Set.of(C), graph.sponsoredBy(A));
        assertEquals(Set.of(A, B, C), graph.players());
        assertEquals(2, graph.sponsorshipCount(SERVER));
    }

    @Test
    void pathFromServerIsAShortestPath() {
        graph.addEdge(SERVER, A, NOW);
        graph.addEdge(A, B, NOW);
        graph.addEdge(B, C, NOW);
        graph.addEdge(C, D, NOW);
        graph.addEdge(A, D, NOW);
        assertEquals(Optional.of(List.of(SERVER, A, D)), graph.pathFromServer(D));
        assertEquals(Optional.of(List.of(SERVER, A, B, C)), graph.pathFromServer(C));
        assertEquals(Optional.of(List.of(SERVER)), graph.pathFromServer(SERVER));

        graph.removeEdge(SERVER, A);
        assertEquals(Optional.empty(), graph.pathFromServer(D));
    }

    @Test
    void shortestPathTreeContainsEveryReachablePlayerOnce() {
        graph.addEdge(SERVER, A, NOW);
        graph.addEdge(SERVER, B, NOW);
        graph.addEdge(A, C, NOW);
        graph.addEdge(B, C, NOW);
        graph.addEdge(C, A, NOW);
        var tree = graph.shortestPathTree();
        assertEquals(Set.of(A, B, C), tree.keySet());
        assertEquals(SERVER, tree.get(A));
        assertEquals(SERVER, tree.get(B));
        assertEquals(A, tree.get(C));
    }
}
