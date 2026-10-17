package io.github.maxijaxi.sponsor.core;

/**
 * Fest eingebaute Texte für Protokoll und Admin-Ausgaben (Englisch/Deutsch). Die Nachrichten an Spieler stehen
 * dagegen in der Config. Platzhalter in {@code {geschweiften Klammern}}.
 */
public enum CoreText {
    // Gründe für einen Zugangsverlust
    LOSS_DIRECT_NO_SPONSORS(
            "sponsorship by {sponsor} removed, no other sponsors",
            "Sponsoring durch {sponsor} entfernt, keine weiteren Sponsoren"),
    LOSS_DIRECT_SPONSORS_WITHOUT_ACCESS(
            "sponsorship by {sponsor} removed, remaining sponsors have no access: {sponsors}",
            "Sponsoring durch {sponsor} entfernt, übrige Sponsoren ohne Zugang: {sponsors}"),
    LOSS_INDIRECT_NO_SPONSORS(
            "no sponsors left",
            "keine Sponsoren mehr"),
    LOSS_INDIRECT(
            "chain to SERVER broken, sponsors without access: {sponsors}",
            "Kette zu SERVER unterbrochen, Sponsoren ohne Zugang: {sponsors}"),

    // Zusatzangaben im Protokoll
    DETAIL_NEWLY_SPONSORED(
            "newly sponsored",
            "neu gesponsert"),
    DETAIL_DORMANT_REACTIVATED(
            "dormant sponsorship active again",
            "ruhendes Sponsoring wieder wirksam"),
    DETAIL_KEEPS_ACCESS(
            "{player} keeps access through other sponsors",
            "{player} behält Zugang über andere Sponsoren"),
    DETAIL_MISSING_FROM_WHITELIST(
            "sponsored, but missing from the whitelist",
            "gesponsert, fehlte aber auf der Whitelist"),
    DETAIL_RECONNECTED(
            "connected to SERVER through a sponsor chain again",
            "wieder über eine Sponsoren-Kette mit SERVER verbunden"),
    DETAIL_NO_CHAIN(
            "no sponsor chain from SERVER",
            "keine Sponsoren-Kette von SERVER"),

    // Lesbare Darstellung der Protokolleinträge (/sponsor log)
    LOG_ADD(
            "{actor} sponsors {target}",
            "{actor} sponsert {target}"),
    LOG_REMOVE_OWN(
            "{actor} ended the sponsorship of {target}",
            "{actor} beendet Sponsoring für {target}"),
    LOG_REMOVE_OTHER(
            "{actor} removed sponsorship {sponsor} -> {target}",
            "{actor} entfernt Sponsoring {sponsor} -> {target}"),
    LOG_MIGRATION(
            "Migration: {target} taken over from the whitelist (sponsor: SERVER)",
            "Migration: {target} von der Whitelist übernommen (Sponsor: SERVER)"),
    LOG_WHITELIST_ADOPT(
            "Manual whitelist entry {target} taken over (sponsor: SERVER)",
            "Manueller Whitelist-Eintrag {target} übernommen (Sponsor: SERVER)"),
    LOG_WHITELIST_READD(
            "{target} put back on the whitelist",
            "{target} wieder auf die Whitelist gesetzt"),
    LOG_WHITELIST_ADD(
            "{target} added to the whitelist",
            "{target} auf die Whitelist gesetzt"),
    LOG_WHITELIST_REMOVE(
            "{target} removed from the whitelist",
            "{target} von der Whitelist entfernt"),
    LOG_LOST_ACCESS(
            " | lost access: {players}",
            " | Zugang verloren: {players}"),
    LOG_GAINED_ACCESS(
            " | gained access: {players}",
            " | Zugang erhalten: {players}");

    private final String en;
    private final String de;

    CoreText(String en, String de) {
        this.en = en;
        this.de = de;
    }

    /** Text in {@code lang} mit ersetzten Platzhaltern ({@code "name", wert, ...}). */
    public String format(Lang lang, Object... placeholderValues) {
        String text = lang == Lang.DE ? de : en;
        for (int i = 0; i + 1 < placeholderValues.length; i += 2) {
            text = text.replace("{" + placeholderValues[i] + "}", String.valueOf(placeholderValues[i + 1]));
        }
        return text;
    }
}
