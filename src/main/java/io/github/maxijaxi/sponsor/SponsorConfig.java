package io.github.maxijaxi.sponsor;

import io.github.maxijaxi.sponsor.core.Lang;
import io.github.maxijaxi.sponsor.core.SponsorRules;
import java.util.Arrays;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config-Datei {@code config/sponsor-common.toml}. Bewusst COMMON und nicht SERVER: SERVER-Configs würden an
 * Clients synchronisiert, der Mod soll aber rein serverseitig bleiben. Änderungen werden zur Laufzeit übernommen.
 */
public final class SponsorConfig {
    public static final ModConfigSpec.IntValue MAX_SPONSORSHIPS;
    public static final ModConfigSpec.IntValue MIN_PLAY_TIME_TICKS;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_LANGUAGE;
    public static final ModConfigSpec.BooleanValue USE_CLIENT_LANGUAGE;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Rules for players. They do not apply to the server console/RCON (sponsor \"SERVER\").").push("rules");
        MAX_SPONSORSHIPS = builder
                .comment("Maximum number of active sponsorships per player.")
                .defineInRange("maxSponsorships", 3, 0, 1000);
        MIN_PLAY_TIME_TICKS = builder
                .comment("Play time on this server a player needs before they may sponsor someone.",
                        "Measured with the statistic minecraft:play_time in ticks (20 ticks = 1 second, 1 h = 72000, 10 h = 720000).")
                .defineInRange("minPlayTimeTicks", 720_000, 0, Integer.MAX_VALUE);
        builder.pop();

        List<String> codes = Arrays.stream(Lang.values()).map(Lang::code).toList();
        builder.comment("Language of the messages.").push("language");
        DEFAULT_LANGUAGE = builder
                .comment("Language for the console, RCON, the log file and for players whose game language is not supported: "
                        + String.join(", ", codes))
                .defineInList("default", Lang.EN.code(), codes);
        USE_CLIENT_LANGUAGE = builder
                .comment("If true, players get messages in their game language when it is supported (German for de_*);",
                        "everyone else gets the default language. If false, everyone gets the default language.")
                .define("useClientLanguage", true);
        builder.pop();

        builder.comment("Message texts per language. Placeholders in {curly braces} are replaced, \\n starts a new line,",
                "color codes with § (e.g. §e) are allowed.").push("messages");
        for (Lang lang : Lang.values()) {
            builder.push(lang.code());
            for (Msg msg : Msg.values()) {
                if (!msg.placeholders().isEmpty()) {
                    builder.comment("Placeholders: " + msg.placeholders());
                }
                msg.bind(lang, builder.define(msg.key(), msg.defaultText(lang)));
            }
            builder.pop();
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

    /** Sprache für Konsole, RCON und Protokoll. */
    public static Lang defaultLanguage() {
        try {
            return Lang.fromCode(DEFAULT_LANGUAGE.get(), Lang.EN);
        } catch (IllegalStateException notLoaded) {
            return Lang.EN;
        }
    }

    /** Sprache für einen Spieler: seine Spielsprache, falls unterstützt und erlaubt, sonst die Standardsprache. */
    public static Lang languageOf(ServerPlayer player) {
        Lang fallback = defaultLanguage();
        boolean useClient;
        try {
            useClient = USE_CLIENT_LANGUAGE.getAsBoolean();
        } catch (IllegalStateException notLoaded) {
            useClient = true;
        }
        return useClient ? Lang.fromLocale(player.clientInformation().language(), fallback) : fallback;
    }

    /** Sprache für Antworten an eine Befehlsquelle: Spieler wie {@link #languageOf(ServerPlayer)}, Konsole/RCON Standardsprache. */
    public static Lang languageOf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? languageOf(player) : defaultLanguage();
    }
}
