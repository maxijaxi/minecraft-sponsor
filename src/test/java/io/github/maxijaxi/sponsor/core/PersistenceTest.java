package io.github.maxijaxi.sponsor.core;

import static io.github.maxijaxi.sponsor.core.SponsorGraph.SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.maxijaxi.sponsor.core.LogEntry.Action;
import io.github.maxijaxi.sponsor.core.LogEntry.Affected;
import io.github.maxijaxi.sponsor.core.LogEntry.PlayerRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TempDir
    Path dir;

    // -------------------------------------------------------------- storage

    @Test
    void graphRoundTrip() throws IOException {
        SponsorData data = new SponsorData();
        data.rememberName(ALICE, "Alice");
        data.rememberName(BOB, "Bob");
        data.graph().addEdge(SERVER, ALICE, NOW);
        data.graph().addEdge(ALICE, BOB, NOW.plusSeconds(5));
        data.graph().addEdge(BOB, ALICE, NOW.plusSeconds(10));
        data.whitelistSnapshot().addAll(Set.of(ALICE, BOB));

        SponsorStorage storage = new SponsorStorage(dir.resolve("sponsor/graph.json"));
        assertFalse(storage.exists());
        storage.save(data);
        assertTrue(storage.exists());
        assertFalse(Files.exists(dir.resolve("sponsor/graph.json.tmp")));

        String json = Files.readString(storage.file());
        assertTrue(json.contains("\"sponsor\": \"SERVER\""), json);

        SponsorData loaded = storage.load();
        assertEquals(data.graph().edges(), loaded.graph().edges());
        assertEquals(data.names(), loaded.names());
        assertEquals(data.whitelistSnapshot(), loaded.whitelistSnapshot());
        assertEquals(Set.of(ALICE, BOB), loaded.graph().reachable());
    }

    @Test
    void saveReplacesExistingFile() throws IOException {
        SponsorStorage storage = new SponsorStorage(dir.resolve("graph.json"));
        SponsorData data = new SponsorData();
        data.graph().addEdge(SERVER, ALICE, NOW);
        storage.save(data);
        data.graph().removeEdge(SERVER, ALICE);
        storage.save(data);
        assertTrue(storage.load().graph().isEmpty());
    }

    @Test
    void corruptFileIsReportedNotSilentlyReplaced() throws IOException {
        Path file = dir.resolve("graph.json");
        Files.writeString(file, "{ \"edges\": [ { \"sponsor\": \"kaputt\" } ] }");
        SponsorStorage storage = new SponsorStorage(file);
        assertThrows(IOException.class, storage::load);

        Files.writeString(file, "das ist kein JSON");
        assertThrows(IOException.class, storage::load);
    }

    @Test
    void newerFormatIsRejected() throws IOException {
        Path file = dir.resolve("graph.json");
        Files.writeString(file, "{ \"format\": 99 }");
        assertThrows(IOException.class, () -> new SponsorStorage(file).load());
    }

    // ------------------------------------------------------------------ log

    @Test
    void logEntriesAreAppendedAsJsonLines() throws IOException {
        AuditLog log = new AuditLog(dir.resolve("sponsor/log.jsonl"));
        LogEntry entry = new LogEntry(NOW, Action.REMOVE, PlayerRef.server(), new PlayerRef(ALICE, "Alice"), new PlayerRef(BOB, "Bob"),
                List.of(new Affected(new PlayerRef(BOB, "Bob"), "Sponsoring durch Alice entfernt, keine weiteren Sponsoren")),
                List.of(), null);
        log.append(entry);
        log.append(LogEntry.of(NOW.plusSeconds(1), Action.ADD, new PlayerRef(ALICE, "Alice"), new PlayerRef(BOB, "Bob"), null));

        List<String> lines = Files.readAllLines(log.file(), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("{\"time\":\"2026-01-01T12:00:00Z\",\"action\":\"REMOVE\",\"actor\":{\"uuid\":\"SERVER\""), lines.get(0));
        assertTrue(lines.get(0).contains("\"lostAccess\":[{\"uuid\":\"22222222-2222-2222-2222-222222222222\",\"name\":\"Bob\",\"reason\":"), lines.get(0));

        List<LogEntry> read = log.readLast(10);
        assertEquals(2, read.size());
        assertEquals(entry, read.get(0));
        assertEquals(Action.ADD, read.get(1).action());
        assertNull(read.get(1).sponsor());
    }

    @Test
    void readLastReturnsNewestEntriesInChronologicalOrderAcrossChunks() throws IOException {
        // Sehr kleine Blockgröße, damit Zeilen über Blockgrenzen hinweg gelesen werden müssen.
        AuditLog log = new AuditLog(dir.resolve("log.jsonl"), 16);
        for (int i = 0; i < 25; i++) {
            log.append(LogEntry.of(NOW.plusSeconds(i), Action.ADD, PlayerRef.server(), new PlayerRef(ALICE, "Spieler" + i), "Nr. " + i));
        }
        List<LogEntry> last = log.readLast(5);
        assertEquals(5, last.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("Nr. " + (20 + i), last.get(i).detail());
        }
        assertEquals(25, log.readLast(100).size());
        assertEquals(1, log.readLast(1).size());
        assertEquals("Nr. 24", log.readLast(1).get(0).detail());
    }

    @Test
    void readLastToleratesMissingFileAndBrokenLines() throws IOException {
        AuditLog log = new AuditLog(dir.resolve("log.jsonl"));
        assertTrue(log.readLast(10).isEmpty());

        log.append(LogEntry.of(NOW, Action.ADD, PlayerRef.server(), new PlayerRef(ALICE, "Alice"), null));
        Files.writeString(log.file(), "kaputte Zeile\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        log.append(LogEntry.of(NOW, Action.ADD, PlayerRef.server(), new PlayerRef(BOB, "Bob"), null));
        List<LogEntry> read = log.readLast(10);
        assertEquals(2, read.size());
        assertEquals("Bob", read.get(1).target().name());
    }

    @Test
    void displayStringIsReadable() {
        LogEntry remove = new LogEntry(NOW, Action.REMOVE, PlayerRef.server(), new PlayerRef(ALICE, "Alice"), new PlayerRef(BOB, "Bob"),
                List.of(new Affected(new PlayerRef(BOB, "Bob"), "Sponsoring durch Alice entfernt, keine weiteren Sponsoren")),
                List.of(), null);
        assertEquals("[2026-01-01 12:00:00] SERVER entfernt Sponsoring Alice -> Bob | Zugang verloren: Bob (Sponsoring durch Alice entfernt, keine weiteren Sponsoren)",
                remove.toDisplayString(ZoneOffset.UTC, Lang.DE));

        LogEntry ownRemove = new LogEntry(NOW, Action.REMOVE, new PlayerRef(ALICE, "Alice"), new PlayerRef(ALICE, "Alice"), new PlayerRef(BOB, "Bob"),
                List.of(), List.of(), "Bob behält Zugang über andere Sponsoren");
        assertEquals("[2026-01-01 12:00:00] Alice beendet Sponsoring für Bob (Bob behält Zugang über andere Sponsoren)",
                ownRemove.toDisplayString(ZoneOffset.UTC, Lang.DE));

        assertEquals("[2026-01-01 12:00:00] SERVER removed sponsorship Alice -> Bob | lost access: Bob (Sponsoring durch Alice entfernt, keine weiteren Sponsoren)",
                remove.toDisplayString(ZoneOffset.UTC, Lang.EN));
        assertEquals("[2026-01-01 12:00:00] Alice ended the sponsorship of Bob (Bob behält Zugang über andere Sponsoren)",
                ownRemove.toDisplayString(ZoneOffset.UTC, Lang.EN));

        LogEntry add = new LogEntry(NOW, Action.ADD, new PlayerRef(ALICE, "Alice"), null, new PlayerRef(BOB, "Bob"), List.of(),
                List.of(new Affected(new PlayerRef(BOB, "Bob"), "newly sponsored")), null);
        assertEquals("[2026-01-01 12:00:00] Alice sponsors Bob | gained access: Bob (newly sponsored)", add.toDisplayString(ZoneOffset.UTC, Lang.EN));
        assertEquals("[2026-01-01 12:00:00] Alice sponsert Bob | Zugang erhalten: Bob (newly sponsored)", add.toDisplayString(ZoneOffset.UTC, Lang.DE));

        LogEntry migration = LogEntry.of(NOW, Action.MIGRATION, PlayerRef.server(), new PlayerRef(ALICE, "Alice"), null);
        assertEquals("[2026-01-01 12:00:00] Migration: Alice taken over from the whitelist (sponsor: SERVER)",
                migration.toDisplayString(ZoneOffset.UTC, Lang.EN));
        assertEquals("[2026-01-01 12:00:00] Migration: Alice von der Whitelist übernommen (Sponsor: SERVER)",
                migration.toDisplayString(ZoneOffset.UTC, Lang.DE));
    }
}
