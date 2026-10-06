package io.github.maxijaxi.sponsor;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Alle Nachrichtentexte. Die Texte stehen im Abschnitt {@code [messages]} der Config-Datei und können dort
 * angepasst werden; Platzhalter in {@code {geschweiften Klammern}} werden beim Senden ersetzt.
 */
public enum Msg {
    HELP("help", "",
            "Sponsor-Befehle:\n"
                    + "/sponsor add <spieler> - einen Spieler sponsern (er kommt auf die Whitelist)\n"
                    + "/sponsor remove <spieler> - dein Sponsoring beenden\n"
                    + "/sponsor list - wen sponsere ich?\n"
                    + "/sponsor me - wer sponsert mich, und wie bin ich mit SERVER verbunden?\n"
                    + "Admins: /sponsor tree, /sponsor log [n], /sponsor remove <sponsor> <spieler>"),
    DISABLED("disabled", "",
            "Das Sponsor-System ist deaktiviert, weil die Sponsor-Daten nicht geladen werden konnten. Details stehen im Server-Log."),
    NOT_ALLOWED("notAllowed", "",
            "Diesen Befehl können nur Spieler und die Server-Konsole verwenden."),
    PLAYER_ONLY("playerOnly", "",
            "Dieser Befehl ist nur für Spieler gedacht."),
    ONE_PLAYER("onePlayer", "",
            "Bitte genau einen Spieler angeben."),
    SELF("selfSponsor", "",
            "Du kannst dich nicht selbst sponsern."),
    ALREADY_SPONSORING("alreadySponsoring", "{player}",
            "Du sponserst {player} bereits."),
    ALREADY_SPONSORING_SERVER("alreadySponsoringServer", "{player}",
            "SERVER sponsert {player} bereits."),
    NO_ACCESS("noAccess", "",
            "Du bist selbst nicht über eine Sponsoren-Kette mit SERVER verbunden und kannst deshalb niemanden sponsern."),
    NOT_ENOUGH_PLAYTIME("notEnoughPlayTime", "{required}, {current}",
            "Du brauchst mindestens {required} Spielzeit auf diesem Server, um jemanden zu sponsern. Bisher: {current}."),
    LIMIT_REACHED("limitReached", "{count}, {max}",
            "Du hast bereits {count} von {max} möglichen Sponsorings vergeben. Beende zuerst eines mit /sponsor remove <spieler>."),
    ADD_SUCCESS("addSuccess", "{player}, {count}, {max}",
            "Du sponserst jetzt {player}. ({count}/{max} Sponsorings vergeben)"),
    ADD_SUCCESS_SERVER("addSuccessServer", "{player}",
            "SERVER sponsert jetzt {player}."),
    ADD_NEW_ACCESS("addNewAccess", "{player}",
            "{player} steht jetzt auf der Whitelist und kann den Server betreten."),
    ADD_ALREADY_HAD_ACCESS("addAlreadyHadAccess", "{player}",
            "{player} hatte bereits Zugang über einen anderen Sponsor."),
    ADD_ALSO_RESTORED("addAlsoRestored", "{players}",
            "Dadurch haben außerdem wieder Zugang: {players}"),
    SPONSORED_NOTICE("sponsoredNotice", "{sponsor}",
            "{sponsor} sponsert dich jetzt."),
    NOT_SPONSORING("notSponsoring", "{player}",
            "Du sponserst {player} nicht."),
    NOT_SPONSORING_OTHER("notSponsoringOther", "{sponsor}, {player}",
            "{sponsor} sponsert {player} nicht."),
    REMOVE_SUCCESS("removeSuccess", "{sponsor}, {player}",
            "Sponsoring {sponsor} -> {player} beendet."),
    REMOVE_STILL_HAS_ACCESS("removeStillHasAccess", "{player}",
            "{player} behält den Zugang über andere Sponsoren."),
    REMOVE_CASCADE("removeCascade", "{count}, {players}",
            "Dadurch haben {count} Spieler ihren Zugang verloren und wurden von der Whitelist entfernt: {players}"),
    REMOVED_NOTICE("removedNotice", "{sponsor}",
            "{sponsor} sponsert dich nicht mehr. Du hast weiterhin Zugang über andere Sponsoren."),
    KICK("kick", "",
            "Dein Zugang zu diesem Server wurde entzogen: Es führt keine Sponsoren-Kette mehr von SERVER zu dir.\n"
                    + "Bitte einen Spieler mit Zugang, dich erneut zu sponsern."),
    OP_NOT_KICKED("opNotKicked", "",
            "Es führt keine Sponsoren-Kette mehr von SERVER zu dir, du wurdest von der Whitelist entfernt. Als OP kannst du trotzdem weiterspielen."),
    LIST("list", "{count}, {max}, {players}",
            "Du sponserst ({count}/{max}): {players}"),
    LIST_EMPTY("listEmpty", "{count}, {max}",
            "Du sponserst niemanden. ({count}/{max} Sponsorings vergeben)"),
    LIST_SERVER("listServer", "{count}, {players}",
            "SERVER sponsert {count} Spieler: {players}"),
    ME_SPONSORS("meSponsors", "{players}",
            "Deine Sponsoren: {players}"),
    ME_NO_SPONSORS("meNoSponsors", "",
            "Du hast keine Sponsoren."),
    ME_PATH("mePath", "{path}",
            "Deine Verbindung zu SERVER: {path}"),
    ME_NO_PATH("meNoPath", "",
            "Es führt keine Sponsoren-Kette von SERVER zu dir."),
    NO_ACCESS_MARKER("noAccessMarker", "",
            "(ohne Zugang)"),
    SAVE_FAILED("saveFailed", "",
            "Warnung: Die Sponsor-Daten konnten nicht gespeichert werden. Bitte einen Admin, das Server-Log zu prüfen."),
    WHITELIST_ADD_HINT("whitelistAddHint", "",
            "Hinweis: Neue Whitelist-Einträge werden automatisch als Sponsoring durch SERVER übernommen."),
    WHITELIST_REMOVE_HINT("whitelistRemoveHint", "",
            "Hinweis: Die Whitelist wird vom Sponsor-Mod verwaltet. Gesponserte Spieler werden automatisch wieder eingetragen - nutze /sponsor remove.");

    private final String key;
    private final String placeholders;
    private final String defaultText;
    private ModConfigSpec.ConfigValue<String> value;

    Msg(String key, String placeholders, String defaultText) {
        this.key = key;
        this.placeholders = placeholders;
        this.defaultText = defaultText;
    }

    String key() {
        return key;
    }

    String placeholders() {
        return placeholders;
    }

    String defaultText() {
        return defaultText;
    }

    void bind(ModConfigSpec.ConfigValue<String> value) {
        this.value = value;
    }

    /** Text aus der Config (Fallback: Standardtext) mit ersetzten Platzhaltern ({@code "name", wert, ...}). */
    public String text(Object... placeholderValues) {
        String text = defaultText;
        if (value != null) {
            try {
                text = value.get();
            } catch (IllegalStateException notLoaded) {
                // Config noch nicht geladen – Standardtext verwenden.
            }
        }
        for (int i = 0; i + 1 < placeholderValues.length; i += 2) {
            text = text.replace("{" + placeholderValues[i] + "}", String.valueOf(placeholderValues[i + 1]));
        }
        return text;
    }

    public MutableComponent component(Object... placeholderValues) {
        return Component.literal(text(placeholderValues));
    }
}
