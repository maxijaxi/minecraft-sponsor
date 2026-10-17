package io.github.maxijaxi.sponsor.core;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Gerichteter Sponsor-Graph, unabhängig von Minecraft.
 *
 * <p>Knoten sind Spieler-UUIDs plus der Wurzelknoten {@link #SERVER}. Eine Kante {@code sponsor -> target}
 * bedeutet "sponsor sponsert target". Zyklen sind erlaubt. Ein Spieler hat Zugang, wenn er über eine Kette
 * von Kanten von {@link #SERVER} aus erreichbar ist. Kanten von nicht erreichbaren Spielern bleiben
 * gespeichert, zählen aber erst wieder, wenn ihr Sponsor erneut erreichbar ist.
 */
public final class SponsorGraph {
    /** Wurzelknoten. Die Null-UUID kann bei online-mode=true keinem echten Spieler gehören. */
    public static final UUID SERVER = new UUID(0L, 0L);

    public record Edge(UUID sponsor, UUID target, Instant since) {
    }

    /** sponsor -> (target -> seit), jeweils in Einfügereihenfolge, damit alle Ausgaben deterministisch sind. */
    private final Map<UUID, Map<UUID, Instant>> outgoing = new LinkedHashMap<>();

    /**
     * Fügt die Kante {@code sponsor -> target} hinzu.
     *
     * @return {@code false}, wenn die Kante bereits existiert
     * @throws IllegalArgumentException bei Selbst-Sponsoring oder wenn {@link #SERVER} gesponsert werden soll
     */
    public boolean addEdge(UUID sponsor, UUID target, Instant since) {
        Objects.requireNonNull(sponsor, "sponsor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(since, "since");
        if (sponsor.equals(target)) {
            throw new IllegalArgumentException("Self-sponsoring is not allowed: " + sponsor);
        }
        if (SERVER.equals(target)) {
            throw new IllegalArgumentException("SERVER cannot be sponsored");
        }
        Map<UUID, Instant> targets = outgoing.computeIfAbsent(sponsor, key -> new LinkedHashMap<>());
        if (targets.containsKey(target)) {
            return false;
        }
        targets.put(target, since);
        return true;
    }

    /** @return {@code false}, wenn es die Kante nicht gab */
    public boolean removeEdge(UUID sponsor, UUID target) {
        Map<UUID, Instant> targets = outgoing.get(sponsor);
        if (targets == null || targets.remove(target) == null) {
            return false;
        }
        if (targets.isEmpty()) {
            outgoing.remove(sponsor);
        }
        return true;
    }

    public boolean hasEdge(UUID sponsor, UUID target) {
        Map<UUID, Instant> targets = outgoing.get(sponsor);
        return targets != null && targets.containsKey(target);
    }

    public Optional<Instant> since(UUID sponsor, UUID target) {
        Map<UUID, Instant> targets = outgoing.get(sponsor);
        return targets == null ? Optional.empty() : Optional.ofNullable(targets.get(target));
    }

    /** Alle Spieler, die {@code sponsor} sponsert (unabhängig davon, ob die Kanten gerade zählen). */
    public Set<UUID> sponsoredBy(UUID sponsor) {
        Map<UUID, Instant> targets = outgoing.get(sponsor);
        return targets == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(targets.keySet()));
    }

    /** Alle Sponsoren von {@code target} (inklusive {@link #SERVER} und nicht erreichbarer Sponsoren). */
    public Set<UUID> sponsorsOf(UUID target) {
        Set<UUID> sponsors = new LinkedHashSet<>();
        for (Map.Entry<UUID, Map<UUID, Instant>> entry : outgoing.entrySet()) {
            if (entry.getValue().containsKey(target)) {
                sponsors.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(sponsors);
    }

    /** Anzahl der gespeicherten ausgehenden Sponsorings von {@code sponsor}. */
    public int sponsorshipCount(UUID sponsor) {
        Map<UUID, Instant> targets = outgoing.get(sponsor);
        return targets == null ? 0 : targets.size();
    }

    public List<Edge> edges() {
        List<Edge> edges = new ArrayList<>();
        outgoing.forEach((sponsor, targets) -> targets.forEach((target, since) -> edges.add(new Edge(sponsor, target, since))));
        return Collections.unmodifiableList(edges);
    }

    /** Alle Spieler, die in irgendeiner Kante vorkommen (ohne {@link #SERVER}). */
    public Set<UUID> players() {
        Set<UUID> players = new LinkedHashSet<>();
        outgoing.forEach((sponsor, targets) -> {
            players.add(sponsor);
            players.addAll(targets.keySet());
        });
        players.remove(SERVER);
        return Collections.unmodifiableSet(players);
    }

    public boolean isEmpty() {
        return outgoing.isEmpty();
    }

    /** Alle von {@link #SERVER} aus erreichbaren Spieler in BFS-Reihenfolge (ohne SERVER selbst). */
    public Set<UUID> reachable() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(bfsParents().keySet()));
    }

    public boolean isReachable(UUID player) {
        return SERVER.equals(player) || bfsParents().containsKey(player);
    }

    /**
     * Kürzeste-Wege-Baum ab {@link #SERVER}: für jeden erreichbaren Spieler der Vorgänger auf einem kürzesten
     * Pfad. Die Iterationsreihenfolge ist die BFS-Reihenfolge.
     */
    public Map<UUID, UUID> shortestPathTree() {
        return Collections.unmodifiableMap(bfsParents());
    }

    /** Ein kürzester Pfad {@code [SERVER, ..., player]} oder leer, wenn {@code player} nicht erreichbar ist. */
    public Optional<List<UUID>> pathFromServer(UUID player) {
        if (SERVER.equals(player)) {
            return Optional.of(List.of(SERVER));
        }
        Map<UUID, UUID> parents = bfsParents();
        if (!parents.containsKey(player)) {
            return Optional.empty();
        }
        List<UUID> path = new ArrayList<>();
        UUID current = player;
        while (!SERVER.equals(current)) {
            path.add(current);
            current = parents.get(current);
        }
        path.add(SERVER);
        Collections.reverse(path);
        return Optional.of(List.copyOf(path));
    }

    private LinkedHashMap<UUID, UUID> bfsParents() {
        LinkedHashMap<UUID, UUID> parents = new LinkedHashMap<>();
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        visited.add(SERVER);
        queue.add(SERVER);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            Map<UUID, Instant> targets = outgoing.get(current);
            if (targets == null) {
                continue;
            }
            for (UUID next : targets.keySet()) {
                if (visited.add(next)) {
                    parents.put(next, current);
                    queue.add(next);
                }
            }
        }
        return parents;
    }
}
