package io.github.maxijaxi.sponsor;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.maxijaxi.sponsor.core.SponsorData;
import io.github.maxijaxi.sponsor.core.SponsorGraph;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Brigadier-Befehlsbaum für {@code /sponsor}. Es werden nur Vanilla-Argumenttypen verwendet, damit Clients ohne
 * den Mod den Befehlsbaum verstehen.
 */
public final class SponsorCommands {
    /** Permission-Level für Admin-Befehle ({@code /sponsor tree}, {@code /sponsor log}, fremde Kanten entfernen). */
    public static final int ADMIN_LEVEL = Commands.LEVEL_ADMINS;
    private static final int DEFAULT_LOG_ENTRIES = 10;
    private static final int MAX_LOG_ENTRIES = 100;

    private SponsorCommands() {
    }

    @FunctionalInterface
    private interface ControllerAction {
        int run(SponsorController controller) throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<SponsorController> controller) {
        dispatcher.register(Commands.literal("sponsor")
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("help")
                        .executes(ctx -> help(ctx.getSource())))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .suggests((ctx, builder) -> suggestOnlinePlayers(ctx.getSource(), builder))
                                .executes(ctx -> run(ctx.getSource(), controller,
                                        c -> c.add(ctx.getSource(), singleProfile(ctx, "player"))))))
                .then(Commands.literal("remove")
                        // Admins: Sponsoring durch SERVER entfernen, z. B. "/sponsor remove SERVER Steve".
                        .then(Commands.literal(SponsorData.SERVER_NAME)
                                .requires(source -> source.hasPermission(ADMIN_LEVEL))
                                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                        .suggests((ctx, builder) -> suggest(controller, c -> c.targetNamesOf(SponsorGraph.SERVER), builder))
                                        .executes(ctx -> run(ctx.getSource(), controller,
                                                c -> c.removeAsAdmin(ctx.getSource(), SponsorGraph.SERVER, singleProfile(ctx, "target"))))))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .suggests((ctx, builder) -> suggest(controller, c -> {
                                    List<String> names = c.ownTargetNames(ctx.getSource());
                                    return ctx.getSource().hasPermission(ADMIN_LEVEL)
                                            ? concat(names, c.sponsorNames())
                                            : names;
                                }, builder))
                                .executes(ctx -> run(ctx.getSource(), controller,
                                        c -> c.remove(ctx.getSource(), singleProfile(ctx, "player"))))
                                // Admins: "/sponsor remove <sponsor> <player>" entfernt eine fremde Kante.
                                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                        .requires(source -> source.hasPermission(ADMIN_LEVEL))
                                        .suggests((ctx, builder) -> suggest(controller, SponsorController::sponsoredNames, builder))
                                        .executes(ctx -> run(ctx.getSource(), controller,
                                                c -> c.removeAsAdmin(ctx.getSource(), singleProfile(ctx, "player").getId(), singleProfile(ctx, "target")))))))
                .then(Commands.literal("list")
                        .executes(ctx -> run(ctx.getSource(), controller, c -> c.list(ctx.getSource()))))
                .then(Commands.literal("me")
                        .executes(ctx -> run(ctx.getSource(), controller, c -> c.me(ctx.getSource()))))
                .then(Commands.literal("tree")
                        .requires(source -> source.hasPermission(ADMIN_LEVEL))
                        .executes(ctx -> run(ctx.getSource(), controller, c -> c.tree(ctx.getSource()))))
                .then(Commands.literal("log")
                        .requires(source -> source.hasPermission(ADMIN_LEVEL))
                        .executes(ctx -> run(ctx.getSource(), controller, c -> c.log(ctx.getSource(), DEFAULT_LOG_ENTRIES)))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, MAX_LOG_ENTRIES))
                                .executes(ctx -> run(ctx.getSource(), controller,
                                        c -> c.log(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))));
    }

    private static int help(CommandSourceStack source) {
        for (String line : Msg.HELP.text(SponsorConfig.languageOf(source)).split("\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int run(CommandSourceStack source, Supplier<SponsorController> supplier, ControllerAction action) throws CommandSyntaxException {
        SponsorController controller = supplier.get();
        if (controller == null) {
            source.sendFailure(Msg.DISABLED.component(SponsorConfig.languageOf(source)));
            return 0;
        }
        return action.run(controller);
    }

    /** Löst ein GameProfile-Argument wie {@code /whitelist add} auf (auch für Spieler, die nie online waren). */
    private static GameProfile singleProfile(CommandContext<CommandSourceStack> ctx, String argument) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, argument);
        if (profiles.size() != 1) {
            throw new SimpleCommandExceptionType(Msg.ONE_PLAYER.component(SponsorConfig.languageOf(ctx.getSource()))).create();
        }
        return profiles.iterator().next();
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(CommandSourceStack source, SuggestionsBuilder builder) {
        ServerPlayer self = source.getPlayer();
        return SharedSuggestionProvider.suggest(source.getServer().getPlayerList().getPlayers().stream()
                .filter(player -> player != self)
                .map(player -> player.getGameProfile().getName()), builder);
    }

    private static CompletableFuture<Suggestions> suggest(Supplier<SponsorController> supplier,
                                                          Function<SponsorController, List<String>> names,
                                                          SuggestionsBuilder builder) {
        SponsorController controller = supplier.get();
        return controller == null ? builder.buildFuture() : SharedSuggestionProvider.suggest(names.apply(controller), builder);
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).distinct().toList();
    }
}
