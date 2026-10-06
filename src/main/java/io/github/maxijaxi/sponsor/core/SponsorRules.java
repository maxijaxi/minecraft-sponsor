package io.github.maxijaxi.sponsor.core;

/**
 * Regeln für Spieler-Sponsorings. Für {@link SponsorGraph#SERVER} (Konsole/RCON) gelten sie nicht.
 *
 * @param maxSponsorships  maximale Anzahl gleichzeitiger Sponsorings pro Spieler
 * @param minPlayTimeTicks benötigte Spielzeit (Statistik minecraft:play_time) in Ticks, bevor ein Spieler sponsern darf
 */
public record SponsorRules(int maxSponsorships, long minPlayTimeTicks) {
    public static final long TICKS_PER_HOUR = 20L * 60L * 60L;
    public static final SponsorRules DEFAULT = new SponsorRules(3, 10L * TICKS_PER_HOUR);

    public SponsorRules {
        if (maxSponsorships < 0) {
            throw new IllegalArgumentException("maxSponsorships < 0");
        }
        if (minPlayTimeTicks < 0) {
            throw new IllegalArgumentException("minPlayTimeTicks < 0");
        }
    }
}
