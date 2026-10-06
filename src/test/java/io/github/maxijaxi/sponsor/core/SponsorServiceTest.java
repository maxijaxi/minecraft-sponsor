package io.github.maxijaxi.sponsor.core;

import static io.github.maxijaxi.sponsor.core.SponsorGraph.SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.maxijaxi.sponsor.core.SponsorService.AddResult;
import io.github.maxijaxi.sponsor.core.SponsorService.AddStatus;
import io.github.maxijaxi.sponsor.core.SponsorService.LostAccess;
import io.github.maxijaxi.sponsor.core.SponsorService.ReconcilePlan;
import io.github.maxijaxi.sponsor.core.SponsorService.RemoveResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SponsorServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final long ENOUGH = SponsorRules.DEFAULT.minPlayTimeTicks();
    private static final SponsorRules RULES = SponsorRules.DEFAULT;

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DAVE = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID EVE = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID FRANK = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private SponsorData data;
    private SponsorService service;

    @BeforeEach
    void setUp() {
        data = new SponsorData();
        service = new SponsorService(data);
        data.rememberName(ALICE, "Alice");
        data.rememberName(BOB, "Bob");
        data.rememberName(CAROL, "Carol");
        data.rememberName(DAVE, "Dave");
        data.rememberName(EVE, "Eve");
        data.rememberName(FRANK, "Frank");
    }

    private AddResult add(UUID sponsor, UUID target) {
        return service.add(sponsor, target, ENOUGH, RULES, NOW);
    }

    // ------------------------------------------------------------------ add

    @Test
    void serverCanSponsorWithoutLimitOrPlayTime() {
        for (int i = 0; i < 10; i++) {
            AddResult result = service.add(SERVER, UUID.randomUUID(), 0, RULES, NOW);
            assertEquals(AddStatus.OK, result.status());
        }
        assertEquals(10, data.graph().sponsorshipCount(SERVER));
    }

    @Test
    void addReportsGainedAccess() {
        AddResult result = add(SERVER, ALICE);
        assertTrue(result.ok());
        assertFalse(result.targetHadAccess());
        assertEquals(List.of(ALICE), result.gainedAccess());
        assertEquals(1, result.sponsorshipCount());
    }

    @Test
    void selfSponsoringIsDenied() {
        add(SERVER, ALICE);
        assertEquals(AddStatus.SELF, add(ALICE, ALICE).status());
        assertEquals(AddStatus.SELF, add(ALICE, SERVER).status());
        assertEquals(0, data.graph().sponsorshipCount(ALICE));
    }

    @Test
    void duplicateSponsoringIsDenied() {
        add(SERVER, ALICE);
        assertTrue(add(ALICE, BOB).ok());
        AddResult duplicate = add(ALICE, BOB);
        assertEquals(AddStatus.ALREADY_SPONSORING, duplicate.status());
        assertTrue(duplicate.targetHadAccess());
        assertEquals(1, data.graph().sponsorshipCount(ALICE));
    }

    @Test
    void sponsorWithoutAccessIsDenied() {
        assertEquals(AddStatus.NO_ACCESS, add(ALICE, BOB).status());
        assertTrue(data.graph().isEmpty());
    }

    @Test
    void limitOfThreeIsEnforced() {
        add(SERVER, ALICE);
        assertTrue(add(ALICE, BOB).ok());
        assertTrue(add(ALICE, CAROL).ok());
        assertTrue(add(ALICE, DAVE).ok());
        AddResult fourth = add(ALICE, EVE);
        assertEquals(AddStatus.LIMIT_REACHED, fourth.status());
        assertEquals(3, fourth.sponsorshipCount());
        assertFalse(data.graph().isReachable(EVE));

        service.remove(ALICE, DAVE);
        assertTrue(add(ALICE, EVE).ok());
    }

    @Test
    void limitIsConfigurable() {
        SponsorRules one = new SponsorRules(1, 0);
        service.add(SERVER, ALICE, 0, one, NOW);
        assertTrue(service.add(ALICE, BOB, 0, one, NOW).ok());
        assertEquals(AddStatus.LIMIT_REACHED, service.add(ALICE, CAROL, 0, one, NOW).status());

        SponsorRules five = new SponsorRules(5, 0);
        assertTrue(service.add(ALICE, CAROL, 0, five, NOW).ok());
    }

    @Test
    void minimumPlayTimeIsEnforced() {
        add(SERVER, ALICE);
        assertEquals(AddStatus.NOT_ENOUGH_PLAYTIME, service.add(ALICE, BOB, ENOUGH - 1, RULES, NOW).status());
        assertTrue(service.add(ALICE, BOB, ENOUGH, RULES, NOW).ok());
        assertEquals(720_000L, RULES.minPlayTimeTicks());
    }

    @Test
    void addReactivatesDormantSponsorships() {
        add(SERVER, ALICE);
        add(ALICE, BOB);
        add(BOB, CAROL);
        service.remove(SERVER, ALICE);
        assertTrue(data.graph().reachable().isEmpty());

        AddResult result = add(SERVER, ALICE);
        assertEquals(List.of(ALICE, BOB, CAROL), result.gainedAccess());
    }

    // --------------------------------------------------------------- remove

    @Test
    void removingMissingEdgeDoesNothing() {
        add(SERVER, ALICE);
        RemoveResult result = service.remove(ALICE, BOB);
        assertFalse(result.removed());
        assertTrue(result.lostAccess().isEmpty());
    }

    @Test
    void cascadeRemovesEveryoneDownstream() {
        add(SERVER, ALICE);
        add(ALICE, BOB);
        add(BOB, CAROL);
        add(BOB, DAVE);
        add(SERVER, EVE);

        RemoveResult result = service.remove(SERVER, ALICE);
        assertTrue(result.removed());
        assertFalse(result.targetStillHasAccess());
        assertEquals(List.of(ALICE, BOB, CAROL, DAVE), lostPlayers(result));
        assertEquals(Set.of(EVE), data.graph().reachable());
        // Die ausgehenden Kanten der Betroffenen bleiben gespeichert.
        assertTrue(data.graph().hasEdge(ALICE, BOB));
        assertTrue(data.graph().hasEdge(BOB, CAROL));
    }

    @Test
    void cycleWithoutServerLosesAccessCompletely() {
        add(SERVER, ALICE);
        add(ALICE, BOB);
        add(BOB, CAROL);
        add(CAROL, ALICE);

        RemoveResult result = service.remove(SERVER, ALICE);
        assertEquals(List.of(ALICE, BOB, CAROL), lostPlayers(result));
        assertTrue(data.graph().reachable().isEmpty());

        LostAccess alice = result.lostAccess().get(0);
        assertEquals(List.of(CAROL), alice.remainingSponsors());
        assertEquals("Sponsoring durch SERVER entfernt, übrige Sponsoren ohne Zugang: Carol",
                SponsorService.describeLoss(alice, SERVER, ALICE, data::nameOf));
        assertEquals("Kette zu SERVER unterbrochen, Sponsoren ohne Zugang: Alice",
                SponsorService.describeLoss(result.lostAccess().get(1), SERVER, ALICE, data::nameOf));
    }

    @Test
    void playerWithMultipleSponsorsStaysAsLongAsOnePathExists() {
        add(SERVER, ALICE);
        add(SERVER, BOB);
        add(ALICE, CAROL);
        add(BOB, CAROL);
        add(CAROL, DAVE);

        RemoveResult first = service.remove(ALICE, CAROL);
        assertTrue(first.removed());
        assertTrue(first.targetStillHasAccess());
        assertTrue(first.lostAccess().isEmpty());
        assertTrue(data.graph().isReachable(DAVE));

        RemoveResult second = service.remove(BOB, CAROL);
        assertFalse(second.targetStillHasAccess());
        assertEquals(List.of(CAROL, DAVE), lostPlayers(second));
        assertEquals("Sponsoring durch Bob entfernt, keine weiteren Sponsoren",
                SponsorService.describeLoss(second.lostAccess().get(0), BOB, CAROL, data::nameOf));
    }

    @Test
    void indirectSecondPathKeepsAccess() {
        add(SERVER, ALICE);
        add(SERVER, BOB);
        add(ALICE, CAROL);
        add(CAROL, DAVE);
        add(BOB, DAVE);

        RemoveResult result = service.remove(SERVER, ALICE);
        assertEquals(List.of(ALICE, CAROL), lostPlayers(result));
        assertTrue(data.graph().isReachable(DAVE));
    }

    @Test
    void adminCanRemoveForeignEdge() {
        add(SERVER, ALICE);
        add(ALICE, BOB);
        RemoveResult result = service.remove(ALICE, BOB);
        assertTrue(result.removed());
        assertEquals(List.of(BOB), lostPlayers(result));
    }

    // ------------------------------------------------------------ reconcile

    @Test
    void firstStartAdoptsWholeWhitelistAndKicksNobody() {
        Map<UUID, String> whitelist = whitelist(ALICE, BOB, CAROL);
        ReconcilePlan plan = service.reconcile(whitelist, NOW);

        assertEquals(List.of(ALICE, BOB, CAROL), plan.adopted());
        assertTrue(plan.toWhitelist().isEmpty());
        assertTrue(plan.fromWhitelist().isEmpty());
        assertEquals(Set.of(ALICE, BOB, CAROL), data.graph().sponsoredBy(SERVER));
        assertEquals(Set.of(ALICE, BOB, CAROL), data.whitelistSnapshot());
    }

    @Test
    void reconcileIsIdempotent() {
        Map<UUID, String> whitelist = whitelist(ALICE, BOB);
        service.reconcile(whitelist, NOW);
        ReconcilePlan second = service.reconcile(whitelist, NOW);
        assertTrue(second.adopted().isEmpty());
        assertFalse(second.changesWhitelist());
    }

    @Test
    void manualWhitelistAddIsAdoptedAsServerSponsoring() {
        service.reconcile(whitelist(ALICE), NOW);

        ReconcilePlan plan = service.reconcile(whitelist(ALICE, BOB), NOW);
        assertEquals(List.of(BOB), plan.adopted());
        assertTrue(data.graph().hasEdge(SERVER, BOB));
        assertFalse(plan.changesWhitelist());
    }

    @Test
    void playersWhoLostAccessAreRemovedAndNotReadopted() {
        service.reconcile(whitelist(ALICE), NOW);
        add(ALICE, BOB);
        add(BOB, CAROL);
        ReconcilePlan afterAdd = service.reconcile(whitelist(ALICE), NOW);
        assertEquals(List.of(BOB, CAROL), afterAdd.toWhitelist());

        service.remove(ALICE, BOB);
        ReconcilePlan afterRemove = service.reconcile(whitelist(ALICE, BOB, CAROL), NOW);
        assertTrue(afterRemove.adopted().isEmpty());
        assertEquals(List.of(BOB, CAROL), afterRemove.fromWhitelist());
        assertFalse(data.graph().hasEdge(SERVER, BOB));
        assertEquals(Set.of(ALICE), data.whitelistSnapshot());
    }

    @Test
    void manualWhitelistRemovalOfSponsoredPlayerIsReverted() {
        service.reconcile(whitelist(ALICE, BOB), NOW);
        ReconcilePlan plan = service.reconcile(whitelist(ALICE), NOW);
        assertEquals(List.of(BOB), plan.toWhitelist());
        assertTrue(plan.previousSnapshot().contains(BOB));
    }

    @Test
    void manuallyReaddedFormerPlayerRestoresDormantChain() {
        service.reconcile(whitelist(ALICE), NOW);
        add(ALICE, BOB);
        add(BOB, CAROL);
        service.reconcile(whitelist(ALICE), NOW);
        service.remove(ALICE, BOB);
        service.reconcile(whitelist(ALICE, BOB, CAROL), NOW);
        assertFalse(data.graph().isReachable(CAROL));

        // Admin trägt Bob per /whitelist add wieder ein.
        ReconcilePlan plan = service.reconcile(whitelist(ALICE, BOB), NOW);
        assertEquals(List.of(BOB), plan.adopted());
        assertEquals(List.of(CAROL), plan.toWhitelist());
        assertTrue(data.graph().isReachable(CAROL));
    }

    @Test
    void reconcileAfterCrashBeforeWhitelistUpdateStillRemovesLostPlayers() {
        service.reconcile(whitelist(ALICE), NOW);
        add(ALICE, BOB);
        service.reconcile(whitelist(ALICE), NOW);
        // Kante entfernt und gespeichert, Whitelist aber noch nicht angepasst (Absturz) -> Neustart:
        service.remove(ALICE, BOB);
        ReconcilePlan plan = service.reconcile(whitelist(ALICE, BOB), NOW);
        assertTrue(plan.adopted().isEmpty());
        assertEquals(List.of(BOB), plan.fromWhitelist());
    }

    @Test
    void namesFromWhitelistAreRemembered() {
        SponsorData fresh = new SponsorData();
        SponsorService freshService = new SponsorService(fresh);
        Map<UUID, String> whitelist = new LinkedHashMap<>();
        whitelist.put(FRANK, "Frank");
        freshService.reconcile(whitelist, NOW);
        assertEquals("Frank", fresh.nameOf(FRANK));
        assertEquals("SERVER", fresh.nameOf(SERVER));
    }

    private Map<UUID, String> whitelist(UUID... players) {
        Map<UUID, String> whitelist = new LinkedHashMap<>();
        for (UUID player : players) {
            whitelist.put(player, data.nameOf(player));
        }
        return whitelist;
    }

    private static List<UUID> lostPlayers(RemoveResult result) {
        return result.lostAccess().stream().map(LostAccess::player).toList();
    }
}
