package io.github.maxijaxi.sponsor.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only Protokoll im JSON-Lines-Format: eine Zeile = ein {@link LogEntry}. Bestehende Zeilen werden nie
 * verändert.
 */
public final class AuditLog {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int DEFAULT_CHUNK_SIZE = 8192;

    private final Path file;
    private final int chunkSize;

    public AuditLog(Path file) {
        this(file, DEFAULT_CHUNK_SIZE);
    }

    AuditLog(Path file, int chunkSize) {
        this.file = file;
        this.chunkSize = chunkSize;
    }

    public Path file() {
        return file;
    }

    public void append(LogEntry entry) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] line = (GSON.toJson(entry.toJson()) + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = ByteBuffer.wrap(line);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    /**
     * Liest die letzten {@code count} Einträge (älteste zuerst), ohne die ganze Datei zu laden. Kaputte Zeilen
     * werden übersprungen.
     */
    public List<LogEntry> readLast(int count) throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        for (String line : readLastLines(count)) {
            try {
                entries.add(LogEntry.fromJson(JsonParser.parseString(line).getAsJsonObject()));
            } catch (RuntimeException ignored) {
                // Unlesbare Zeile (z. B. von Hand bearbeitet) – überspringen statt den Befehl scheitern zu lassen.
            }
        }
        return entries;
    }

    /** Die letzten {@code count} nicht-leeren Zeilen (älteste zuerst). */
    List<String> readLastLines(int count) throws IOException {
        if (count <= 0 || !Files.exists(file)) {
            return List.of();
        }
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            long size = channel.size();
            long start = 0;
            long position = size;
            int newlines = 0;
            ByteBuffer buffer = ByteBuffer.allocate(chunkSize);
            search:
            while (position > 0) {
                int length = (int) Math.min(chunkSize, position);
                position -= length;
                buffer.clear().limit(length);
                channel.position(position);
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                    // weiterlesen, bis der Block voll ist
                }
                for (int i = length - 1; i >= 0; i--) {
                    long absolute = position + i;
                    if (buffer.get(i) == '\n' && absolute != size - 1) {
                        newlines++;
                        if (newlines == count) {
                            start = absolute + 1;
                            break search;
                        }
                    }
                }
            }
            ByteBuffer tail = ByteBuffer.allocate((int) (size - start));
            channel.position(start);
            while (tail.hasRemaining() && channel.read(tail) >= 0) {
                // weiterlesen
            }
            String text = new String(tail.array(), 0, tail.position(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            for (String line : text.split("\n")) {
                if (!line.isBlank()) {
                    lines.add(line.strip());
                }
            }
            return lines.size() > count ? lines.subList(lines.size() - count, lines.size()) : lines;
        }
    }
}
