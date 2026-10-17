package io.github.maxijaxi.sponsor.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

/**
 * Liest und schreibt {@link SponsorData} als JSON. Geschrieben wird atomar: erst in eine temporäre Datei
 * im selben Ordner (inkl. fsync), dann per Rename über die alte Datei.
 *
 * <pre>
 * {
 *   "format": 1,
 *   "players": { "&lt;uuid&gt;": "Name", ... },
 *   "edges": [ { "sponsor": "SERVER" | "&lt;uuid&gt;", "target": "&lt;uuid&gt;", "since": "2026-01-01T12:00:00Z" }, ... ],
 *   "whitelistSnapshot": [ "&lt;uuid&gt;", ... ]
 * }
 * </pre>
 */
public final class SponsorStorage {
    public static final int FORMAT = 1;
    private static final String SERVER_KEY = "SERVER";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;

    public SponsorStorage(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public boolean exists() {
        return Files.exists(file);
    }

    /** @throws IOException wenn die Datei nicht gelesen werden kann oder ungültig ist */
    public SponsorData load() throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        try {
            return fromJson(JsonParser.parseString(json).getAsJsonObject());
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException | NullPointerException
                 | UnsupportedOperationException | DateTimeParseException e) {
            throw new IOException("Invalid sponsor data in " + file + ": " + e.getMessage(), e);
        }
    }

    public void save(SponsorData data) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] bytes = (GSON.toJson(toJson(data)) + "\n").getBytes(StandardCharsets.UTF_8);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static JsonObject toJson(SponsorData data) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);

        JsonObject players = new JsonObject();
        data.names().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> players.addProperty(entry.getKey().toString(), entry.getValue()));
        root.add("players", players);

        JsonArray edges = new JsonArray();
        for (SponsorGraph.Edge edge : data.graph().edges()) {
            JsonObject object = new JsonObject();
            object.addProperty("sponsor", encode(edge.sponsor()));
            object.addProperty("target", edge.target().toString());
            object.addProperty("since", edge.since().toString());
            edges.add(object);
        }
        root.add("edges", edges);

        JsonArray snapshot = new JsonArray();
        data.whitelistSnapshot().stream().sorted().forEach(id -> snapshot.add(id.toString()));
        root.add("whitelistSnapshot", snapshot);
        return root;
    }

    static SponsorData fromJson(JsonObject root) {
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT;
        if (format > FORMAT) {
            throw new IllegalArgumentException("Unknown data format " + format + " (supported: " + FORMAT + ")");
        }
        SponsorData data = new SponsorData();
        if (root.has("players")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("players").entrySet()) {
                data.rememberName(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
            }
        }
        if (root.has("edges")) {
            for (JsonElement element : root.getAsJsonArray("edges")) {
                JsonObject edge = element.getAsJsonObject();
                UUID sponsor = decode(edge.get("sponsor").getAsString());
                UUID target = UUID.fromString(edge.get("target").getAsString());
                Instant since = edge.has("since") ? Instant.parse(edge.get("since").getAsString()) : Instant.EPOCH;
                data.graph().addEdge(sponsor, target, since);
            }
        }
        if (root.has("whitelistSnapshot")) {
            for (JsonElement element : root.getAsJsonArray("whitelistSnapshot")) {
                data.whitelistSnapshot().add(UUID.fromString(element.getAsString()));
            }
        }
        return data;
    }

    static String encode(UUID id) {
        return SponsorGraph.SERVER.equals(id) ? SERVER_KEY : id.toString();
    }

    static UUID decode(String value) {
        return SERVER_KEY.equals(value) ? SponsorGraph.SERVER : UUID.fromString(value);
    }
}
