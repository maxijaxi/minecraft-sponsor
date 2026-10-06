package io.github.maxijaxi.sponsor.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fachlogik des Sponsor-Systems ohne Minecraft-Abhängigkeit: Regeln prüfen, Kanten setzen/entfernen,
 * Kaskaden bestimmen und den Abgleich mit der Whitelist planen. Das Anwenden auf die echte Whitelist,
 * Kicks, Speichern und Protokollieren übernimmt die Mod-Schicht.
 */
public final class SponsorService {
    private final SponsorData data;

    public SponsorService(SponsorData data) {
        this.data = data;
    }

    public SponsorData data() {
        return data;
    }

    public SponsorGraph graph() {
        return data.graph();
    }

    // ---------------------------------------------------------------- add

    public enum AddStatus {
        OK,
        /** Selbst-Sponsoring. */
        SELF,
        /** Die Kante existiert bereits. */
        ALREADY_SPONSORING,
        /** Der Sponsor ist selbst nicht von SERVER aus erreichbar. */
        NO_ACCESS,
        NOT_ENOUGH_PLAYTIME,
        LIMIT_REACHED
    }

    /**
     * @param sponsorshipCount Anzahl der Sponsorings des Sponsors nach dem Versuch
     * @param targetHadAccess  ob das Ziel schon vorher erreichbar war
     * @param gainedAccess     alle Spieler, die dadurch Zugang erhalten haben (BFS-Reihenfolge; neben dem Ziel
     *                         auch Spieler, deren ruhende Sponsorings dadurch wieder zählen)
     */
    public record AddResult(AddStatus status, int sponsorshipCount, boolean targetHadAccess, List<UUID> gainedAccess) {
        public boolean ok() {
            return status == AddStatus.OK;
        }
    }

    /**
     * Versucht, die Kante {@code sponsor -> target} anzulegen.
     *
     * @param sponsorPlayTimeTicks Spielzeit des Sponsors in Ticks; für {@link SponsorGraph#SERVER} ignoriert
     */
    public AddResult add(UUID sponsor, UUID target, long sponsorPlayTimeTicks, SponsorRules rules, Instant now) {
        SponsorGraph graph = data.graph();
        int count = graph.sponsorshipCount(sponsor);
        if (sponsor.equals(target) || SponsorGraph.SERVER.equals(target)) {
            return denied(AddStatus.SELF, count, graph.isReachable(target));
        }
        if (graph.hasEdge(sponsor, target)) {
            return denied(AddStatus.ALREADY_SPONSORING, count, graph.isReachable(target));
        }
        if (!SponsorGraph.SERVER.equals(sponsor)) {
            if (!graph.isReachable(sponsor)) {
                return denied(AddStatus.NO_ACCESS, count, graph.isReachable(target));
            }
            if (sponsorPlayTimeTicks < rules.minPlayTimeTicks()) {
                return denied(AddStatus.NOT_ENOUGH_PLAYTIME, count, graph.isReachable(target));
            }
            if (count >= rules.maxSponsorships()) {
                return denied(AddStatus.LIMIT_REACHED, count, graph.isReachable(target));
            }
        }
        Set<UUID> before = graph.reachable();
        graph.addEdge(sponsor, target, now);
        List<UUID> gained = new ArrayList<>();
        for (UUID player : graph.reachable()) {
            if (!before.contains(player)) {
                gained.add(player);
            }
        }
        return new AddResult(AddStatus.OK, count + 1, before.contains(target), List.copyOf(gained));
    }

    private static AddResult denied(AddStatus status, int count, boolean targetHasAccess) {
        return new AddResult(status, count, targetHasAccess, List.of());
    }

    // ------------------------------------------------------------- remove

    /**
     * Ein Spieler, der durch eine Änderung seinen Zugang verloren hat.
     *
     * @param remainingSponsors seine verbliebenen Sponsoren (alle selbst ohne Zugang), sortiert nach UUID
     */
    public record LostAccess(UUID player, List<UUID> remainingSponsors) {
    }

    /**
     * @param removed              ob die Kante existierte und entfernt wurde
     * @param targetStillHasAccess ob das Ziel weiterhin (über andere Sponsoren) erreichbar ist
     * @param lostAccess           alle Spieler, die dadurch ihren Zugang verloren haben (Kaskade, BFS-Reihenfolge)
     */
    public record RemoveResult(boolean removed, boolean targetStillHasAccess, List<LostAccess> lostAccess) {
    }

    /** Entfernt die Kante {@code sponsor -> target} und berechnet die Kaskade. */
    public RemoveResult remove(UUID sponsor, UUID target) {
        SponsorGraph graph = data.graph();
        if (!graph.hasEdge(sponsor, target)) {
            return new RemoveResult(false, graph.isReachable(target), List.of());
        }
        Set<UUID> before = graph.reachable();
        graph.removeEdge(sponsor, target);
        Set<UUID> after = graph.reachable();
        List<LostAccess> lost = new ArrayList<>();
        for (UUID player : before) {
            if (!after.contains(player)) {
                List<UUID> remaining = graph.sponsorsOf(player).stream().sorted().toList();
                lost.add(new LostAccess(player, remaining));
            }
        }
        return new RemoveResult(true, after.contains(target), List.copyOf(lost));
    }

    /** Verständlicher Grund (deutsch) für einen Zugangsverlust, z. B. für das Protokoll. */
    public static String describeLoss(LostAccess loss, UUID removedSponsor, UUID removedTarget, Function<UUID, String> names) {
        String remaining = loss.remainingSponsors().stream().map(names).collect(Collectors.joining(", "));
        if (loss.player().equals(removedTarget)) {
            String base = "Sponsoring durch " + names.apply(removedSponsor) + " entfernt";
            return loss.remainingSponsors().isEmpty()
                    ? base + ", keine weiteren Sponsoren"
                    : base + ", übrige Sponsoren ohne Zugang: " + remaining;
        }
        return loss.remainingSponsors().isEmpty()
                ? "keine Sponsoren mehr"
                : "Kette zu SERVER unterbrochen, Sponsoren ohne Zugang: " + remaining;
    }

    // ----------------------------------------------------------- reconcile

    /**
     * Ergebnis eines Abgleichs.
     *
     * @param adopted          Whitelist-Einträge, die neu als Sponsoring durch SERVER übernommen wurden
     * @param toWhitelist      erreichbare Spieler, die auf die Whitelist gesetzt werden müssen
     * @param fromWhitelist    nicht erreichbare Spieler, die von der Whitelist entfernt (und ggf. gekickt) werden müssen
     * @param previousSnapshot Whitelist-Stand nach dem vorherigen Abgleich
     */
    public record ReconcilePlan(List<UUID> adopted, List<UUID> toWhitelist, List<UUID> fromWhitelist, Set<UUID> previousSnapshot) {
        public boolean changesWhitelist() {
            return !toWhitelist.isEmpty() || !fromWhitelist.isEmpty();
        }
    }

    /**
     * Gleicht den Graphen mit der aktuellen Whitelist ab.
     *
     * <ul>
     *   <li>Whitelist-Einträge, die beim letzten Abgleich nicht auf der Whitelist standen und nicht erreichbar
     *       sind, wurden manuell hinzugefügt (oder es ist der erste Start): Sie werden als Sponsoring durch
     *       SERVER übernommen. Dadurch werden ggf. auch ihre ruhenden Sponsorings wieder wirksam.</li>
     *   <li>Danach gilt: Whitelist = alle erreichbaren Spieler. Fehlende werden ergänzt, nicht erreichbare
     *       entfernt.</li>
     * </ul>
     *
     * Der Graph und der Whitelist-Stand in {@link SponsorData} werden dabei aktualisiert; die echte Whitelist
     * muss der Aufrufer gemäß Plan anpassen.
     *
     * @param whitelist aktuelle Whitelist (UUID -> Name)
     */
    public ReconcilePlan reconcile(Map<UUID, String> whitelist, Instant now) {
        SponsorGraph graph = data.graph();
        whitelist.forEach((id, name) -> {
            if (name != null && !name.isBlank()) {
                data.names().putIfAbsent(id, name);
            }
        });

        Set<UUID> previous = Set.copyOf(data.whitelistSnapshot());
        Set<UUID> reachable = graph.reachable();
        List<UUID> adopted = new ArrayList<>();
        for (UUID id : sortedByName(whitelist.keySet())) {
            if (!SponsorGraph.SERVER.equals(id) && !previous.contains(id) && !reachable.contains(id)) {
                graph.addEdge(SponsorGraph.SERVER, id, now);
                adopted.add(id);
            }
        }
        if (!adopted.isEmpty()) {
            reachable = graph.reachable();
        }

        List<UUID> toWhitelist = new ArrayList<>();
        for (UUID id : reachable) {
            if (!whitelist.containsKey(id)) {
                toWhitelist.add(id);
            }
        }
        List<UUID> fromWhitelist = new ArrayList<>();
        for (UUID id : sortedByName(whitelist.keySet())) {
            if (!reachable.contains(id)) {
                fromWhitelist.add(id);
            }
        }

        data.whitelistSnapshot().clear();
        data.whitelistSnapshot().addAll(reachable);
        return new ReconcilePlan(List.copyOf(adopted), List.copyOf(toWhitelist), List.copyOf(fromWhitelist), previous);
    }

    private List<UUID> sortedByName(Set<UUID> ids) {
        List<UUID> sorted = new ArrayList<>(new LinkedHashSet<>(ids));
        sorted.sort(Comparator.comparing((UUID id) -> data.nameOf(id).toLowerCase(Locale.ROOT)).thenComparing(Comparator.naturalOrder()));
        return sorted;
    }
}
