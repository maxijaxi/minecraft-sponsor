package io.github.maxijaxi.sponsor.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ein Eintrag im append-only Protokoll {@code world/sponsor/log.jsonl}.
 *
 * @param time         Zeitstempel
 * @param action       Aktion
 * @param actor        Ausführender ({@link SponsorGraph#SERVER} für Konsole/RCON und automatische Abgleiche)
 * @param sponsor      betroffener Sponsor der Kante, falls abweichend vom Ausführenden relevant (sonst {@code null})
 * @param target       Ziel
 * @param lostAccess   bei Kaskaden: alle Spieler, die dadurch ihren Zugang verloren haben, mit Grund
 * @param gainedAccess alle Spieler, die dadurch Zugang erhalten haben, mit Grund
 * @param detail       optionaler Freitext
 */
public record LogEntry(Instant time, Action action, PlayerRef actor, PlayerRef sponsor, PlayerRef target,
                       List<Affected> lostAccess, List<Affected> gainedAccess, String detail) {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum Action {
        /** Neues Sponsoring. */
        ADD,
        /** Sponsoring entfernt (ggf. mit Kaskade). */
        REMOVE,
        /** Erster Start: bestehender Whitelist-Eintrag als SERVER-Sponsoring übernommen. */
        MIGRATION,
        /** Manueller Whitelist-Eintrag als SERVER-Sponsoring übernommen. */
        WHITELIST_ADOPT,
        /** Gesponserter Spieler fehlte auf der Whitelist und wurde wieder eingetragen. */
        WHITELIST_READD,
        /** Spieler durch den Abgleich auf die Whitelist gesetzt. */
        WHITELIST_ADD,
        /** Spieler durch den Abgleich von der Whitelist entfernt. */
        WHITELIST_REMOVE
    }

    public record PlayerRef(UUID uuid, String name) {
        public PlayerRef {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(name, "name");
        }

        public static PlayerRef server() {
            return new PlayerRef(SponsorGraph.SERVER, SponsorData.SERVER_NAME);
        }

        public static PlayerRef of(UUID uuid, SponsorData data) {
            return new PlayerRef(uuid, data.nameOf(uuid));
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", SponsorStorage.encode(uuid));
            object.addProperty("name", name);
            return object;
        }

        static PlayerRef fromJson(JsonElement element) {
            if (element == null || element.isJsonNull()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            return new PlayerRef(SponsorStorage.decode(object.get("uuid").getAsString()), object.get("name").getAsString());
        }
    }

    public record Affected(PlayerRef player, String reason) {
        JsonObject toJson() {
            JsonObject object = player.toJson();
            object.addProperty("reason", reason);
            return object;
        }

        static Affected fromJson(JsonElement element) {
            JsonObject object = element.getAsJsonObject();
            return new Affected(PlayerRef.fromJson(object), object.has("reason") ? object.get("reason").getAsString() : "");
        }
    }

    public LogEntry {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actor, "actor");
        lostAccess = lostAccess == null ? List.of() : List.copyOf(lostAccess);
        gainedAccess = gainedAccess == null ? List.of() : List.copyOf(gainedAccess);
    }

    public static LogEntry of(Instant time, Action action, PlayerRef actor, PlayerRef target, String detail) {
        return new LogEntry(time, action, actor, null, target, List.of(), List.of(), detail);
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("time", time.toString());
        object.addProperty("action", action.name());
        object.add("actor", actor.toJson());
        if (sponsor != null) {
            object.add("sponsor", sponsor.toJson());
        }
        if (target != null) {
            object.add("target", target.toJson());
        }
        if (!lostAccess.isEmpty()) {
            JsonArray array = new JsonArray();
            lostAccess.forEach(affected -> array.add(affected.toJson()));
            object.add("lostAccess", array);
        }
        if (!gainedAccess.isEmpty()) {
            JsonArray array = new JsonArray();
            gainedAccess.forEach(affected -> array.add(affected.toJson()));
            object.add("gainedAccess", array);
        }
        if (detail != null && !detail.isEmpty()) {
            object.addProperty("detail", detail);
        }
        return object;
    }

    public static LogEntry fromJson(JsonObject object) {
        return new LogEntry(
                Instant.parse(object.get("time").getAsString()),
                Action.valueOf(object.get("action").getAsString()),
                PlayerRef.fromJson(object.get("actor")),
                PlayerRef.fromJson(object.get("sponsor")),
                PlayerRef.fromJson(object.get("target")),
                affectedList(object.get("lostAccess")),
                affectedList(object.get("gainedAccess")),
                object.has("detail") ? object.get("detail").getAsString() : null);
    }

    private static List<Affected> affectedList(JsonElement element) {
        List<Affected> list = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> list.add(Affected.fromJson(item)));
        }
        return list;
    }

    /** Einzeilige, lesbare Darstellung (deutsch) für {@code /sponsor log}. */
    public String toDisplayString(ZoneId zone) {
        StringBuilder line = new StringBuilder();
        line.append('[').append(DISPLAY_TIME.format(time.atZone(zone))).append("] ");
        String targetName = target == null ? "?" : target.name();
        switch (action) {
            case ADD -> line.append(actor.name()).append(" sponsert ").append(targetName);
            case REMOVE -> {
                PlayerRef edgeSponsor = sponsor == null ? actor : sponsor;
                if (edgeSponsor.uuid().equals(actor.uuid())) {
                    line.append(actor.name()).append(" beendet Sponsoring für ").append(targetName);
                } else {
                    line.append(actor.name()).append(" entfernt Sponsoring ").append(edgeSponsor.name()).append(" -> ").append(targetName);
                }
            }
            case MIGRATION -> line.append("Migration: ").append(targetName).append(" von der Whitelist übernommen (Sponsor: SERVER)");
            case WHITELIST_ADOPT -> line.append("Manueller Whitelist-Eintrag ").append(targetName).append(" übernommen (Sponsor: SERVER)");
            case WHITELIST_READD -> line.append(targetName).append(" wieder auf die Whitelist gesetzt");
            case WHITELIST_ADD -> line.append(targetName).append(" auf die Whitelist gesetzt");
            case WHITELIST_REMOVE -> line.append(targetName).append(" von der Whitelist entfernt");
        }
        if (detail != null && !detail.isEmpty()) {
            line.append(" (").append(detail).append(')');
        }
        if (!lostAccess.isEmpty()) {
            line.append(" | Zugang verloren: ").append(describe(lostAccess));
        }
        if (!gainedAccess.isEmpty()) {
            line.append(" | Zugang erhalten: ").append(describe(gainedAccess));
        }
        return line.toString();
    }

    private static String describe(List<Affected> affected) {
        return affected.stream()
                .map(a -> a.reason() == null || a.reason().isEmpty() ? a.player().name() : a.player().name() + " (" + a.reason() + ")")
                .collect(Collectors.joining("; "));
    }
}
