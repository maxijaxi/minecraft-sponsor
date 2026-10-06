package io.github.maxijaxi.sponsor;

import io.github.maxijaxi.sponsor.core.SponsorRules;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config-Datei {@code config/sponsor-common.toml}. Bewusst COMMON und nicht SERVER: SERVER-Configs würden an
 * Clients synchronisiert, der Mod soll aber rein serverseitig bleiben. Änderungen werden zur Laufzeit übernommen.
 */
public final class SponsorConfig {
    public static final ModConfigSpec.IntValue MAX_SPONSORSHIPS;
    public static final ModConfigSpec.IntValue MIN_PLAY_TIME_TICKS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Regeln für Spieler. Für die Server-Konsole/RCON (Sponsor \"SERVER\") gelten sie nicht.").push("rules");
        MAX_SPONSORSHIPS = builder
                .comment("Maximale Anzahl gleichzeitiger Sponsorings pro Spieler.")
                .defineInRange("maxSponsorships", 3, 0, 1000);
        MIN_PLAY_TIME_TICKS = builder
                .comment("Mindestspielzeit auf diesem Server, bevor ein Spieler sponsern darf.",
                        "Gemessen mit der Statistik minecraft:play_time in Ticks (20 Ticks = 1 Sekunde, 1 h = 72000, 10 h = 720000).")
                .defineInRange("minPlayTimeTicks", 720_000, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Nachrichtentexte. Platzhalter in {geschweiften Klammern} werden ersetzt, \\n erzeugt einen Zeilenumbruch.",
                "Farbcodes mit § (z. B. §e) sind möglich.").push("messages");
        for (Msg msg : Msg.values()) {
            if (!msg.placeholders().isEmpty()) {
                builder.comment("Platzhalter: " + msg.placeholders());
            }
            msg.bind(builder.define(msg.key(), msg.defaultText()));
        }
        builder.pop();

        SPEC = builder.build();
    }

    private SponsorConfig() {
    }

    public static SponsorRules rules() {
        try {
            return new SponsorRules(MAX_SPONSORSHIPS.getAsInt(), MIN_PLAY_TIME_TICKS.getAsInt());
        } catch (IllegalStateException notLoaded) {
            return SponsorRules.DEFAULT;
        }
    }
}
