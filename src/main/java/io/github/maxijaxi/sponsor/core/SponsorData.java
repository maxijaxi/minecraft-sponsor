package io.github.maxijaxi.sponsor.core;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Alles, was in {@code world/sponsor/graph.json} gespeichert wird: der Graph, die zuletzt bekannten Namen
 * (nur zur Anzeige, maßgeblich sind immer die UUIDs) und der Whitelist-Stand nach dem letzten Abgleich.
 */
public final class SponsorData {
    public static final String SERVER_NAME = "SERVER";

    private final SponsorGraph graph = new SponsorGraph();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> whitelistSnapshot = new LinkedHashSet<>();

    public SponsorGraph graph() {
        return graph;
    }

    /** Zuletzt bekannte Spielernamen (veränderbar). */
    public Map<UUID, String> names() {
        return names;
    }

    /**
     * Die Spieler, die nach dem letzten Abgleich auf der Whitelist standen (veränderbar). Damit lassen sich
     * manuelle {@code /whitelist add}-Einträge von Spielern unterscheiden, die gerade ihren Zugang verloren haben.
     */
    public Set<UUID> whitelistSnapshot() {
        return whitelistSnapshot;
    }

    public String nameOf(UUID id) {
        if (SponsorGraph.SERVER.equals(id)) {
            return SERVER_NAME;
        }
        String name = names.get(id);
        return name != null ? name : id.toString();
    }

    /** @return {@code true}, wenn sich der gespeicherte Name dadurch geändert hat */
    public boolean rememberName(UUID id, String name) {
        if (SponsorGraph.SERVER.equals(id) || name == null || name.isBlank()) {
            return false;
        }
        return !Objects.equals(names.put(id, name), name);
    }
}
