package io.github.maxijaxi.sponsor;

import io.github.maxijaxi.sponsor.core.Lang;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Alle Nachrichten an Spieler und Konsole, jeweils auf Englisch und Deutsch. Die Texte stehen in den Abschnitten
 * {@code [messages.en]} und {@code [messages.de]} der Config und können dort angepasst werden; Platzhalter in
 * {@code {geschweiften Klammern}} werden beim Senden ersetzt.
 */
public enum Msg {
    HELP("help", "",
            "Sponsor commands:\n"
                    + "/sponsor add <player> - sponsor a player (puts them on the whitelist)\n"
                    + "/sponsor remove <player> - end your sponsorship\n"
                    + "/sponsor list - who do I sponsor?\n"
                    + "/sponsor me - who sponsors me, and how am I connected to SERVER?\n"
                    + "Admins: /sponsor tree, /sponsor log [n], /sponsor remove <sponsor> <player>",
            "Sponsor-Befehle:\n"
                    + "/sponsor add <spieler> - einen Spieler sponsern (er kommt auf die Whitelist)\n"
                    + "/sponsor remove <spieler> - dein Sponsoring beenden\n"
                    + "/sponsor list - wen sponsere ich?\n"
                    + "/sponsor me - wer sponsert mich, und wie bin ich mit SERVER verbunden?\n"
                    + "Admins: /sponsor tree, /sponsor log [n], /sponsor remove <sponsor> <spieler>"),
    DISABLED("disabled", "",
            "The sponsor system is disabled because its data could not be loaded. See the server log for details.",
            "Das Sponsor-System ist deaktiviert, weil die Sponsor-Daten nicht geladen werden konnten. Details stehen im Server-Log."),
    NOT_ALLOWED("notAllowed", "",
            "Only players and the server console can use this command.",
            "Diesen Befehl können nur Spieler und die Server-Konsole verwenden."),
    PLAYER_ONLY("playerOnly", "",
            "This command is for players only.",
            "Dieser Befehl ist nur für Spieler gedacht."),
    ONE_PLAYER("onePlayer", "",
            "Please name exactly one player.",
            "Bitte genau einen Spieler angeben."),
    SELF("selfSponsor", "",
            "You cannot sponsor yourself.",
            "Du kannst dich nicht selbst sponsern."),
    ALREADY_SPONSORING("alreadySponsoring", "{player}",
            "You already sponsor {player}.",
            "Du sponserst {player} bereits."),
    ALREADY_SPONSORING_SERVER("alreadySponsoringServer", "{player}",
            "SERVER already sponsors {player}.",
            "SERVER sponsert {player} bereits."),
    NO_ACCESS("noAccess", "",
            "You are not connected to SERVER through a sponsor chain yourself, so you cannot sponsor anyone.",
            "Du bist selbst nicht über eine Sponsoren-Kette mit SERVER verbunden und kannst deshalb niemanden sponsern."),
    NOT_ENOUGH_PLAYTIME("notEnoughPlayTime", "{required}, {current}",
            "You need at least {required} of play time on this server to sponsor someone. So far: {current}.",
            "Du brauchst mindestens {required} Spielzeit auf diesem Server, um jemanden zu sponsern. Bisher: {current}."),
    LIMIT_REACHED("limitReached", "{count}, {max}",
            "You already use {count} of {max} possible sponsorships. End one first with /sponsor remove <player>.",
            "Du hast bereits {count} von {max} möglichen Sponsorings vergeben. Beende zuerst eines mit /sponsor remove <spieler>."),
    ADD_SUCCESS("addSuccess", "{player}, {count}, {max}",
            "You now sponsor {player}. ({count}/{max} sponsorships used)",
            "Du sponserst jetzt {player}. ({count}/{max} Sponsorings vergeben)"),
    ADD_SUCCESS_SERVER("addSuccessServer", "{player}",
            "SERVER now sponsors {player}.",
            "SERVER sponsert jetzt {player}."),
    ADD_NEW_ACCESS("addNewAccess", "{player}",
            "{player} is now on the whitelist and can join the server.",
            "{player} steht jetzt auf der Whitelist und kann den Server betreten."),
    ADD_ALREADY_HAD_ACCESS("addAlreadyHadAccess", "{player}",
            "{player} already had access through another sponsor.",
            "{player} hatte bereits Zugang über einen anderen Sponsor."),
    ADD_ALSO_RESTORED("addAlsoRestored", "{players}",
            "This also restored access for: {players}",
            "Dadurch haben außerdem wieder Zugang: {players}"),
    SPONSORED_NOTICE("sponsoredNotice", "{sponsor}",
            "{sponsor} now sponsors you.",
            "{sponsor} sponsert dich jetzt."),
    NOT_SPONSORING("notSponsoring", "{player}",
            "You do not sponsor {player}.",
            "Du sponserst {player} nicht."),
    NOT_SPONSORING_OTHER("notSponsoringOther", "{sponsor}, {player}",
            "{sponsor} does not sponsor {player}.",
            "{sponsor} sponsert {player} nicht."),
    REMOVE_SUCCESS("removeSuccess", "{sponsor}, {player}",
            "Sponsorship {sponsor} -> {player} ended.",
            "Sponsoring {sponsor} -> {player} beendet."),
    REMOVE_STILL_HAS_ACCESS("removeStillHasAccess", "{player}",
            "{player} keeps access through other sponsors.",
            "{player} behält den Zugang über andere Sponsoren."),
    REMOVE_CASCADE("removeCascade", "{count}, {players}",
            "As a result, {count} player(s) lost access and were removed from the whitelist: {players}",
            "Dadurch haben {count} Spieler ihren Zugang verloren und wurden von der Whitelist entfernt: {players}"),
    REMOVED_NOTICE("removedNotice", "{sponsor}",
            "{sponsor} no longer sponsors you. You still have access through other sponsors.",
            "{sponsor} sponsert dich nicht mehr. Du hast weiterhin Zugang über andere Sponsoren."),
    KICK("kick", "",
            "Your access to this server has been revoked: there is no longer a sponsor chain from SERVER to you.\n"
                    + "Ask a player who has access to sponsor you again.",
            "Dein Zugang zu diesem Server wurde entzogen: Es führt keine Sponsoren-Kette mehr von SERVER zu dir.\n"
                    + "Bitte einen Spieler mit Zugang, dich erneut zu sponsern."),
    OP_NOT_KICKED("opNotKicked", "",
            "There is no longer a sponsor chain from SERVER to you, so you were removed from the whitelist. As an operator you can keep playing.",
            "Es führt keine Sponsoren-Kette mehr von SERVER zu dir, du wurdest von der Whitelist entfernt. Als OP kannst du trotzdem weiterspielen."),
    LIST("list", "{count}, {max}, {players}",
            "You sponsor ({count}/{max}): {players}",
            "Du sponserst ({count}/{max}): {players}"),
    LIST_EMPTY("listEmpty", "{count}, {max}",
            "You do not sponsor anyone. ({count}/{max} sponsorships used)",
            "Du sponserst niemanden. ({count}/{max} Sponsorings vergeben)"),
    LIST_SERVER("listServer", "{count}, {players}",
            "SERVER sponsors {count} player(s): {players}",
            "SERVER sponsert {count} Spieler: {players}"),
    ME_SPONSORS("meSponsors", "{players}",
            "Your sponsors: {players}",
            "Deine Sponsoren: {players}"),
    ME_NO_SPONSORS("meNoSponsors", "",
            "You have no sponsors.",
            "Du hast keine Sponsoren."),
    ME_PATH("mePath", "{path}",
            "Your connection to SERVER: {path}",
            "Deine Verbindung zu SERVER: {path}"),
    ME_NO_PATH("meNoPath", "",
            "There is no sponsor chain from SERVER to you.",
            "Es führt keine Sponsoren-Kette von SERVER zu dir."),
    NO_ACCESS_MARKER("noAccessMarker", "",
            "(no access)",
            "(ohne Zugang)"),
    TREE_HEADER("treeHeader", "{count}",
            "Sponsor tree: {count} player(s) with access",
            "Sponsor-Baum: {count} Spieler mit Zugang"),
    TREE_ALSO("treeAlso", "{players}",
            "(also: {players})",
            "(auch: {players})"),
    TREE_DORMANT("treeDormant", "{players}",
            "Without access (their sponsorships are dormant): {players}",
            "Ohne Zugang (ihre Sponsorings ruhen): {players}"),
    LOG_HEADER("logHeader", "{count}",
            "Last {count} log entries:",
            "Letzte {count} Protokolleinträge:"),
    LOG_EMPTY("logEmpty", "",
            "The log is empty.",
            "Das Protokoll ist leer."),
    LOG_READ_FAILED("logReadFailed", "{error}",
            "The log could not be read: {error}",
            "Das Protokoll konnte nicht gelesen werden: {error}"),
    SAVE_FAILED("saveFailed", "",
            "Warning: the sponsor data could not be saved. Please ask an admin to check the server log.",
            "Warnung: Die Sponsor-Daten konnten nicht gespeichert werden. Bitte einen Admin, das Server-Log zu prüfen."),
    WHITELIST_ADD_HINT("whitelistAddHint", "",
            "Note: new whitelist entries are automatically taken over as sponsorships by SERVER.",
            "Hinweis: Neue Whitelist-Einträge werden automatisch als Sponsoring durch SERVER übernommen."),
    WHITELIST_REMOVE_HINT("whitelistRemoveHint", "",
            "Note: the whitelist is managed by the Sponsor mod. Sponsored players are added back automatically - use /sponsor remove.",
            "Hinweis: Die Whitelist wird vom Sponsor-Mod verwaltet. Gesponserte Spieler werden automatisch wieder eingetragen - nutze /sponsor remove.");

    private final String key;
    private final String placeholders;
    private final Map<Lang, String> defaults = new EnumMap<>(Lang.class);
    private final Map<Lang, ModConfigSpec.ConfigValue<String>> values = new EnumMap<>(Lang.class);

    Msg(String key, String placeholders, String english, String german) {
        this.key = key;
        this.placeholders = placeholders;
        defaults.put(Lang.EN, english);
        defaults.put(Lang.DE, german);
    }

    String key() {
        return key;
    }

    String placeholders() {
        return placeholders;
    }

    String defaultText(Lang lang) {
        return defaults.get(lang);
    }

    void bind(Lang lang, ModConfigSpec.ConfigValue<String> value) {
        values.put(lang, value);
    }

    /** Text in {@code lang} aus der Config (Fallback: Standardtext) mit ersetzten Platzhaltern ({@code "name", wert, ...}). */
    public String text(Lang lang, Object... placeholderValues) {
        String text = defaults.get(lang);
        ModConfigSpec.ConfigValue<String> value = values.get(lang);
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

    public MutableComponent component(Lang lang, Object... placeholderValues) {
        return Component.literal(text(lang, placeholderValues));
    }
}
